#!/usr/bin/env python3
"""Session 18: isolated Java 25 particle Box diagnostics and A/B/B/A measurements."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time

import run_particle_movement as movement

ROOT = movement.ROOT
OPTION = 'render.reuseParticleBoxes'
ARCHIVE = ROOT / 'docs/benchmarks/2026-10-20-particle-boxes'
JDK = Path('C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot')


def run(label, scale, kind):
    record_path = ARCHIVE / 'runs' / (label + '.record.json')
    if record_path.exists():
        print('Already collected:', label, flush=True)
        return
    assert not list(movement.BENCH.glob('*-' + label + '-*')), 'label already used'
    log = ROOT / 'build' / (label + '.log')
    assert not log.exists(), 'log already exists'
    probe = subprocess.run(['powershell', '-NoProfile', '-Command',
        "@(Get-CimInstance Win32_Process -Filter \"name = 'java.exe'\" | Where-Object { $_.CommandLine -match 'devlaunchinjector.Main|KnotClient' }).Count"],
        capture_output=True, text=True, check=True)
    assert probe.stdout.strip() == '0', 'another client is running'
    props = {'benchmark': 'true', 'benchmark.label': label, 'benchmark.particles': 'true',
             'benchmark.particleScale': str(scale), 'benchmark.vbo': 'false',
             'render.fastParticleMovement': 'false'}
    if kind == 'diagnostic':
        props.update({OPTION: 'true', OPTION + '.verify': 'true',
                      'benchmark.particleDiagnostic': 'true', 'benchmark.particleBoxDiagnostic': 'true'})
    elif kind == 'control':
        props['benchmark.toggle'] = OPTION
    else:
        props['profiler.particleDetail'] = 'true'
        props[OPTION] = str(kind == 'candidate-profile').lower()
    args = [str(ROOT / 'gradlew.bat'), 'runClient', '--console=plain', '-Pwindow=1920x1080', '-Pxmx=2G']
    if kind.endswith('profile'):
        assert not (ROOT / 'run' / (label + '.jfr')).exists()
        args.append('-Pjfr=' + label)
    args += ['-Palacer.' + k + '=' + v for k, v in props.items()]
    hashes = movement.sources()
    snapshot = movement.snapshot(hashes)
    shared = ROOT / 'run/config/alacer.properties'
    before = movement.sha(shared)
    attempt_path = ARCHIVE / 'attempts' / (label + '.json')
    attempt_path.parent.mkdir(parents=True, exist_ok=True)
    attempt = {'label': label, 'kind': kind, 'command': args, 'source_sha256': hashes,
               'source_snapshot': snapshot, 'shared_config_before_sha256': before, 'status': 'running'}
    attempt_path.write_text(json.dumps(attempt, indent=2) + '\n')
    start = time.monotonic()
    print('Starting:', label, flush=True)
    import os
    env = dict(os.environ, JAVA_HOME=str(JDK))
    with log.open('w', encoding='utf-8') as out:
        result = subprocess.run(args, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT, timeout=720, env=env)
    attempt.update({'exit_code': result.returncode, 'log': str(log.relative_to(ROOT)),
                    'log_sha256': movement.sha(log), 'shared_config_after_sha256': movement.sha(shared),
                    'status': 'exited' if result.returncode == 0 else 'failed'})
    attempt_path.write_text(json.dumps(attempt, indent=2) + '\n')
    assert result.returncode == 0, 'launch failed: ' + str(log)
    assert movement.sources() == hashes, 'sources changed during launch'
    assert movement.sha(shared) == before, 'shared config changed'
    text = log.read_text(encoding='utf-8', errors='replace')
    assert 'BUILD SUCCESSFUL' in text
    for bad in ('GL ERROR', 'OpenGLException', 'Particle box mismatch', 'Benchmark aborted',
                'Benchmark timed out', 'Mixin apply failed', 'Exception in thread'):
        assert bad not in text, (label, bad)
    folders = list(movement.BENCH.glob('*-' + label + '-*'))
    assert len(folders) == 1
    folder = folders[0]
    if not kind.endswith('profile'):
        summary, configs = movement.validate(folder, label, scale, kind == 'diagnostic')
    else:
        summary = json.loads((folder / 'summary.json').read_text())
        configs = {name: json.loads((folder / (name + '.json')).read_text())['config'] for name in summary['phases']}
    env = summary['environment']
    assert env['java.version'].startswith('25.')
    assert env['alacer.configSource'] == 'build-defaults-and-jvm-overrides'
    assert env['window'] == '1920x1080' and env['vboActive'] == 'false'
    for name, config in configs.items():
        assert config['render.fastParticleMovement'] == 'false'
        if kind.endswith('profile'):
            assert config['profiler.particleDetail'] == 'true'
    if kind == 'diagnostic':
        match = re.search(r'Particle boxes final: (\d+) compared, (\d+) reused, (\d+) mismatched', text)
        assert match and int(match[1]) > 0 and int(match[2]) > 0 and int(match[3]) == 0
        assert 'Particle box probes:' in text and 'movement pairs matched' in text
    record = {'label': label, 'scale': scale, 'kind': kind, 'diagnostic': kind != 'control',
              'command': args, 'java_home': str(JDK), 'elapsed_seconds': time.monotonic() - start,
              'source': str(folder.relative_to(ROOT)), 'source_sha256': hashes, 'source_snapshot': snapshot,
              'shared_config_before_sha256': before, 'shared_config_after_sha256': movement.sha(shared),
              'files_sha256': {p.name: movement.sha(p) for p in folder.iterdir() if p.is_file()},
              'log': str(log.relative_to(ROOT)), 'log_sha256': movement.sha(log),
              'verification_lines': [line for line in text.splitlines() if 'Particle box' in line],
              'phase_configs': configs}
    jfr = ROOT / 'run' / (label + '.jfr')
    if jfr.exists():
        record['jfr'] = str(jfr.relative_to(ROOT))
        record['jfr_sha256'] = movement.sha(jfr)
    record_path.parent.mkdir(parents=True, exist_ok=True)
    record_path.write_text(json.dumps(record, indent=2) + '\n')
    (record_path.parent / (label + '.summary.json')).write_text(json.dumps(summary, indent=2) + '\n')
    print('Passed:', label, flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('group', choices=('profile', 'candidate-profile', 'diagnostics', 'controls'))
    parser.add_argument('--only')
    parser.add_argument('--prefix', default='pb18')
    parser.add_argument('--archive', type=Path, default=ARCHIVE)
    opts = parser.parse_args()
    assert re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*', opts.prefix)
    ARCHIVE = (ROOT / opts.archive).resolve()
    assert ARCHIVE.is_relative_to(ROOT)
    movement.ARCHIVE = ARCHIVE
    movement.OPTION = OPTION
    if opts.group == 'controls':
        jobs = [(f'{opts.prefix}-ab-{scale}-r{repeat}', scale, 'control') for repeat in range(1, 5)
                for scale in ((1, 2) if repeat % 2 else (2, 1))]
    elif opts.group == 'diagnostics':
        jobs = [(opts.prefix + '-check-2', 2, 'diagnostic')]
    else:
        jobs = [(opts.prefix + '-' + opts.group, 1, opts.group)]
    if opts.only:
        assert opts.only in {job[0] for job in jobs}
    for job in jobs:
        if not opts.only or opts.only == job[0]:
            run(*job)
