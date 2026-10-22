#!/usr/bin/env python3
"""Serial Java 25 movement experiment; never modify shared config or historical archives."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ARCHIVE = ROOT / 'docs/benchmarks/2026-10-20-particle-movement'
BENCH = ROOT / 'run/alacer/benchmarks'
OPTION = 'render.fastParticleMovement'
SHIPPED = ('fastChunkVisibility', 'fastEntityChunkLookup', 'throttleGlErrorChecks',
           'avoidMatrixReads', 'cacheClouds', 'fastEntityShadows', 'fastParticleLight', 'fastParticleRemoval')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest() if path.exists() else None


def sources():
    paths = sorted((ROOT / 'src').rglob('*')) + [ROOT / 'build.gradle', ROOT / 'gradle.properties']
    return {str(p.relative_to(ROOT)): sha(p) for p in paths if p.is_file()}


def snapshot(source_hashes):
    identity = hashlib.sha256(json.dumps(source_hashes, sort_keys=True).encode()).hexdigest()
    path = ARCHIVE / ('sources-' + identity[:16] + '.zip')
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists():
        with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as out:
            for name in source_hashes:
                out.write(ROOT / name, name.replace('\\', '/'))
    with zipfile.ZipFile(path) as stored:
        for name, expected in source_hashes.items():
            assert hashlib.sha256(stored.read(name.replace('\\', '/'))).hexdigest() == expected
    return {'path': str(path.relative_to(ROOT)), 'sha256': sha(path)}


def validate(folder, label, scale, diagnostic):
    summary = json.loads((folder / 'summary.json').read_text())
    env = summary['environment']
    assert env['java.version'].startswith('25.')
    assert env['alacer.configSource'] == 'build-defaults-and-jvm-overrides'
    assert env['benchmark.generationRun'] == 'false'
    assert env['window'] == '1920x1080' and env['vboActive'] == 'false'
    assert env['benchmark.particles'] == 'true' and env['benchmark.particleScale'] == str(scale)
    assert env['benchmark.mobs'] == '0' and env['benchmark.chests'] == '0'
    assert env['profiler.allocationAvailable'] == 'true'
    expected_toggle = '' if diagnostic else OPTION + ' (a=false, b=true)'
    assert env['benchmark.toggle'] == expected_toggle
    phase_configs = {}
    for name, phase in summary['phases'].items():
        assert phase['frames'] > 0 and phase['dropped_frames'] == 0
        assert phase['discarded_setup_frames'] == 1
        doc = json.loads((folder / (name + '.json')).read_text())
        config = doc['config']
        for key in SHIPPED:
            assert config['render.' + key] == 'true', (label, name, key)
        for key in ('fastChunkFrustum', 'skipZeroTranslates', 'batchModelLists'):
            assert config['render.' + key] == 'false', (label, name, key)
        for key, value in config.items():
            if key.endswith('.verify'):
                assert value == str(diagnostic and key == OPTION + '.verify').lower(), (label, name, key)
        for key in ('entityDetail', 'particleDetail', 'chunkDetail', 'shadowTiming', 'frustumTiming', 'hud'):
            assert config['profiler.' + key] == 'false', (label, name, key)
        variant = diagnostic or name.endswith(('-b', '-b1', '-b2'))
        assert config[OPTION] == str(variant).lower(), (label, name)
        assert config['options.particles'] == '0'
        phase_configs[name] = config
    if not diagnostic:
        expected = {f'{phase}-{segment}' for phase in ('static', 'pan') for segment in ('a1', 'b1', 'b2', 'a2')}
        assert expected.issubset(phase_configs)
        normalized = [{k: v for k, v in c.items() if k != OPTION} for c in phase_configs.values()]
        assert all(c == normalized[0] for c in normalized), label
    return summary, phase_configs


def run(label, scale, diagnostic):
    record_path = ARCHIVE / 'runs' / (label + '.record.json')
    if record_path.exists():
        print('Already collected:', label, flush=True)
        return
    assert not list(BENCH.glob('*-' + label + '-*')), 'label already used: ' + label
    # Do not overlap the copied-world benchmark with any other game client.
    probe = subprocess.run(['powershell', '-NoProfile', '-Command',
        "@(Get-CimInstance Win32_Process -Filter \"name = 'java.exe'\" | Where-Object { $_.CommandLine -match 'devlaunchinjector.Main|KnotClient' }).Count"],
        capture_output=True, text=True, check=True)
    assert probe.stdout.strip() == '0', 'another game client is running'
    props = {'benchmark': 'true', 'benchmark.label': label, 'benchmark.particles': 'true',
             'benchmark.particleScale': str(scale), 'benchmark.vbo': 'false'}
    if diagnostic:
        props.update({OPTION: 'true', OPTION + '.verify': 'true',
                      'benchmark.particleDiagnostic': 'true', 'benchmark.particleMovementDiagnostic': 'true'})
    else:
        props['benchmark.toggle'] = OPTION
    args = [str(ROOT / 'gradlew.bat'), 'runClient', '--console=plain', '-Pwindow=1920x1080', '-Pxmx=2G']
    args += ['-Palacer.' + k + '=' + v for k, v in props.items()]
    log = ROOT / 'build' / (label + '.log')
    assert not log.exists(), 'log already exists: ' + str(log)
    source_hashes = sources()
    source_snapshot = snapshot(source_hashes)
    shared = ROOT / 'run/config/alacer.properties'
    before = sha(shared)
    start = time.monotonic()
    print('Starting:', label, flush=True)
    with log.open('w', encoding='utf-8') as out:
        result = subprocess.run(args, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT, timeout=720)
    assert result.returncode == 0, 'launch failed: ' + str(log)
    assert sources() == source_hashes, 'sources changed during launch'
    assert sha(shared) == before, 'shared config changed'
    text = log.read_text(encoding='utf-8', errors='replace')
    assert 'BUILD SUCCESSFUL' in text
    for bad in ('GL ERROR', 'OpenGLException', 'Particle movement mismatch', 'Benchmark aborted',
                'Benchmark timed out', 'Mixin apply failed', 'movement probe collision'):
        assert bad not in text, (label, bad)
    folders = list(BENCH.glob('*-' + label + '-*'))
    assert len(folders) == 1, label
    folder = folders[0]
    summary, configs = validate(folder, label, scale, diagnostic)
    match = re.search(r'Particle movement final: (\d+) block reads compared, (\d+) mismatched', text)
    assert match and int(match[2]) == 0 and (not diagnostic or int(match[1]) > 0)
    if diagnostic:
        assert 'ordered collision lists and border flags matched' in text
    record = {'label': label, 'scale': scale, 'diagnostic': diagnostic, 'command': args,
              'elapsed_seconds': time.monotonic() - start, 'source': str(folder.relative_to(ROOT)),
              'source_sha256': source_hashes, 'shared_config_before_sha256': before,
              'source_snapshot': source_snapshot,
              'shared_config_after_sha256': sha(shared),
              'files_sha256': {p.name: sha(p) for p in folder.iterdir() if p.is_file()},
              'log': str(log.relative_to(ROOT)), 'log_sha256': sha(log),
              'verification_lines': [line for line in text.splitlines() if 'Particle movement' in line],
              'phase_configs': configs}
    record_path.parent.mkdir(parents=True, exist_ok=True)
    record_path.write_text(json.dumps(record, indent=2) + '\n')
    (record_path.parent / (label + '.summary.json')).write_text(json.dumps(summary, indent=2) + '\n')
    print('Passed:', label, flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('group', choices=('diagnostics', 'controls'))
    parser.add_argument('--only')
    parser.add_argument('--prefix', default='pm', help='Fresh labels for an independent replay')
    parser.add_argument('--archive', type=Path, default=ARCHIVE, help='Separate archive within this checkout')
    opts = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*', opts.prefix):
        parser.error('--prefix must be a filename-safe label')
    ARCHIVE = (ROOT / opts.archive).resolve()
    if not ARCHIVE.is_relative_to(ROOT):
        parser.error('--archive must be within this checkout')
    jobs = [(f'{opts.prefix}-check-2', 2, True)] if opts.group == 'diagnostics' else [
        (f'{opts.prefix}-ab-{scale}-r{repeat}', scale, False) for repeat in range(1, 5)
        for scale in ((1, 2) if repeat % 2 else (2, 1))]
    if opts.only and opts.only not in {job[0] for job in jobs}:
        parser.error('--only does not match a label in this group/prefix')
    for label, scale, diagnostic in jobs:
        if opts.only is None or opts.only == label:
            run(label, scale, diagnostic)
