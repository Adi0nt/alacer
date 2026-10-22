#!/usr/bin/env python3
"""Run Java 25 client gates serially; retain raw CSVs locally and archive identities.

Usage: python tools/run_java25_validation.py [diagnostics|controls|all] [--only LABEL]
Completed labels are skipped. Remove their new archive record explicitly to rerun.
No Java 8 archive, game config or optimization default is rewritten.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
ARCHIVE = ROOT / 'docs/benchmarks/2026-10-20-java25'
DEFAULTS = {
    'profiler.enabled': 'true', 'profiler.entityDetail': 'false',
    'profiler.entityStages': 'true', 'profiler.chunkDetail': 'false',
    'profiler.frustumTiming': 'false', 'profiler.shadowTiming': 'false',
    'profiler.hud': 'false',
    **{f'render.{name}': 'true' for name in (
        'fastChunkVisibility', 'fastEntityChunkLookup', 'throttleGlErrorChecks',
        'avoidMatrixReads', 'cacheClouds', 'fastEntityShadows')},
    **{f'render.{name}': 'false' for name in (
        'fastChunkFrustum', 'skipZeroTranslates', 'batchModelLists')},
}
for key in list(DEFAULTS):
    if key.startswith('render.'):
        DEFAULTS[key + '.verify'] = 'false'


def plan():
    verify = {k: 'true' for k in DEFAULTS if k.endswith('.verify')}
    verify.pop('render.throttleGlErrorChecks.verify')
    jobs = []
    for backend in ('dl', 'vbo'):
        jobs.append(('diagnostics', f'j25-verify-{backend}', {
            **verify, 'benchmark.entities': '200', 'benchmark.timeScale': '0.5',
            'benchmark.vbo': str(backend == 'vbo').lower(),
            'benchmark.screenshots': 'true'}))
    jobs.extend([
        ('diagnostics', 'j25-frustum-traversal', {
            'render.fastChunkFrustum': 'true', 'render.fastChunkVisibility.verify': 'true',
            'benchmark.timeScale': '0.5'}),
        ('diagnostics', 'j25-gl-error', {
            'render.throttleGlErrorChecks.verify': 'true', 'benchmark.timeScale': '0.2'}),
        ('diagnostics', 'j25-cloud-sweep', {
            'render.cacheClouds.verify': 'true', 'benchmark.cloudSweep': 'true',
            'benchmark.timeScale': '0.3', 'benchmark.screenshots': 'true'}),
        ('diagnostics', 'j25-shadow-diagnostic', {
            'render.fastEntityShadows.verify': 'true', 'benchmark.shadowDiagnostic': 'true',
            'benchmark.entities': '200', 'benchmark.timeScale': '0.5'}),
        ('diagnostics', 'j25-jfr-detail', {
            'profiler.chunkDetail': 'true', 'profiler.entityDetail': 'true',
            'profiler.shadowTiming': 'true', 'benchmark.entities': '200',
            'benchmark.backendScenario': 'false', 'benchmark.timeScale': '1'}),
    ])
    # Each density repeats the complete warmed A/B/B/A protocol in fresh launches.
    for repeat, populations in ((1, (0, 200, 1000)), (2, (1000, 200, 0))):
        for population in populations:
            jobs.append(('controls', f'j25-defaults-e{population}-r{repeat}', {
                'benchmark.toggle': 'render.defaults', 'benchmark.entities': str(population)}))
    # Balanced separate-launch backend controls: DL, VBO, VBO, DL.
    for backend, repeat in (('dl', 1), ('vbo', 1), ('vbo', 2), ('dl', 2)):
        jobs.append(('controls', f'j25-backend-{backend}-r{repeat}', {
            'benchmark.backendScenario': 'true',
            'benchmark.vbo': str(backend == 'vbo').lower()}))
    return jobs


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(group, label, overrides, collect_only=False):
    record = ARCHIVE / 'runs' / f'{label}.record.json'
    if record.exists():
        print('Already complete:', label, flush=True)
        return
    props = {**DEFAULTS, 'benchmark': 'true', 'benchmark.label': label,
             'benchmark.entities': '0', 'benchmark.chests': '0',
             'benchmark.viewDistance': '12', 'benchmark.vbo': 'false',
             'benchmark.timeout': '600', **overrides}
    args = [str(ROOT / 'gradlew.bat'), 'runClient', '-Pwindow=1920x1080', '-Pxmx=2G']
    args += [f'-Palacer.{k}={v}' for k, v in props.items()]
    if label == 'j25-jfr-detail':
        args.append('-Pjfr=java25-detail')
    log = ARCHIVE / 'logs' / f'{label}.log'
    log.parent.mkdir(parents=True, exist_ok=True)
    record.parent.mkdir(parents=True, exist_ok=True)
    print('Starting:', label, flush=True)
    start = time.monotonic()
    if not collect_only:
        with log.open('w', encoding='utf-8') as out:
            result = subprocess.run(args, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT, timeout=720)
        assert result.returncode == 0, f'{label}: client failed; see {log}'
    text = log.read_text(encoding='utf-8', errors='replace')
    assert 'BUILD SUCCESSFUL' in text, f'{label}: client failed; see {log}'
    folders = list((ROOT / 'run/alacer/benchmarks').glob(f'*-{label}-vis_*'))
    assert len(folders) == 1, f'{label}: ambiguous/missing benchmark output'
    folder = folders[0]
    summary = json.loads((folder / 'summary.json').read_text())
    env = summary['environment']
    assert env['java.version'].startswith('25.')
    assert env['profiler.allocationAvailable'] == 'true'
    assert env['benchmark.generationRun'] == 'false'
    assert env['window'] == '1920x1080'
    assert env['vboActive'] == props['benchmark.vbo']
    assert env['benchmark.mobs'] == props['benchmark.entities']
    for phase in summary['phases'].values():
        assert phase['frames'] > 0 and phase['dropped_frames'] == 0
        assert phase['discarded_setup_frames'] == 1
        assert phase['alloc_mean_bytes'] > 0
        assert phase['client_thread_cpu_ms_per_frame'] > 0
        assert phase['process_cpu_cores'] > 0
    failures = [r'Chunk visibility mismatch with unchanged', r'Chunk frustum test mismatch',
                r'Entity chunk lookup mismatch', r'Entity shadow \w+ mismatch at',
                r'Cloud cache verification: tile', r'Model list feedback differs',
                r'injected error was NOT reported', r'Matrix read mismatch',
                r'Zero translation changed the matrix', r'Tracked .* differs from OpenGL',
                r'Benchmark aborted', r'OpenGLException', r'Mixin apply .* failed']
    for failure in failures:
        assert not re.search(failure, text), f'{label}: {failure}'
    if label != 'j25-gl-error':
        assert '########## GL ERROR' not in text
    shutil.copyfile(folder / 'summary.json', ARCHIVE / 'runs' / f'{label}.summary.json')
    data = {'group': group, 'label': label, 'command': args, 'properties': props,
            'elapsed_seconds': None if collect_only else time.monotonic() - start,
            'source': str(folder.relative_to(ROOT)),
            'files_sha256': {p.name: sha(p) for p in folder.iterdir() if p.is_file()},
            'log_sha256': sha(log),
            'verification_lines': [line for line in text.splitlines()
                                   if re.search(r'verification|queries final|feedback|diagnostic toggled', line, re.I)]}
    record.write_text(json.dumps(data, indent=2) + '\n')
    print('Passed:', label, flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('group', choices=('diagnostics', 'controls', 'all'), nargs='?', default='all')
    parser.add_argument('--only')
    parser.add_argument('--collect-only', action='store_true', help='Validate/archive an already completed launch')
    opts = parser.parse_args()
    for group, label, overrides in plan():
        if opts.group not in (group, 'all') or (opts.only and label != opts.only):
            continue
        run(group, label, overrides, opts.collect_only)
