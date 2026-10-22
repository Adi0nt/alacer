#!/usr/bin/env python3
"""Session 20: particle vertex writing diagnostics, A/B/B/A controls and startup-absent controls (Java 25).

Every launch passes every render option explicitly, refuses reused labels/logs and overlapping
clients, and checks the source tree against a manifest frozen before the first measured launch.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time

import run_particle_movement as movement

ROOT = movement.ROOT
OPTION = 'render.fastParticleVertices'
ARCHIVE = ROOT / 'docs/benchmarks/2026-10-20-particle-vertices'
JDK = Path('C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot')
ON = ('fastChunkVisibility', 'fastEntityChunkLookup', 'throttleGlErrorChecks', 'avoidMatrixReads', 'cacheClouds',
      'fastEntityShadows', 'fastParticleLight', 'fastParticleRemoval')
OFF = ('fastChunkFrustum', 'skipZeroTranslates', 'batchModelLists', 'fastParticleMovement', 'reuseParticleBoxes',
       'scalarParticleEntityQuery')
PROFILER_OFF = ('entityDetail', 'particleDetail', 'chunkDetail', 'shadowTiming', 'frustumTiming', 'hud')


def explicit_options(kind):
    props = {'render.' + key: 'true' for key in ON}
    props.update({'render.' + key: 'false' for key in OFF})
    for key in ON + OFF + ('fastParticleVertices',):
        props['render.' + key + '.verify'] = 'false'
    props.update({'profiler.' + key: 'false' for key in PROFILER_OFF})
    # Profiles record the candidate's remaining costs (attribution only, JFR on).
    props[OPTION] = 'true' if kind in ('diagnostic', 'profile', 'on') else 'false'
    if kind == 'diagnostic':
        props[OPTION + '.verify'] = 'true'
    return props


def manifest_check(frozen):
    current = movement.sources()
    if frozen is not None:
        assert current == frozen['source_sha256'], 'source tree differs from the frozen manifest'
    return current


def run(label, scale, kind, frozen):
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
             'benchmark.particleScale': str(scale), 'benchmark.vbo': 'false'}
    props.update(explicit_options(kind))
    if kind == 'diagnostic':
        props.update({'benchmark.particleDiagnostic': 'true', 'benchmark.particleRemovalDiagnostic': 'true'})
    elif kind == 'control':
        props['benchmark.toggle'] = OPTION
    args = [str(ROOT / 'gradlew.bat'), 'runClient', '--console=plain', '-Pwindow=1920x1080', '-Pxmx=2G']
    if kind == 'profile':
        assert not (ROOT / 'run' / (label + '.jfr')).exists()
        args.append('-Pjfr=' + label)
    args += ['-Palacer.' + k + '=' + v for k, v in props.items()]
    hashes = manifest_check(frozen)
    snapshot = movement.snapshot(hashes)
    shared = ROOT / 'run/config/alacer.properties'
    before = movement.sha(shared)
    attempt_path = ARCHIVE / 'attempts' / (label + '.json')
    attempt_path.parent.mkdir(parents=True, exist_ok=True)
    attempt = {'label': label, 'kind': kind, 'scale': scale, 'command': args, 'source_snapshot': snapshot,
               'shared_config_before_sha256': before, 'status': 'running'}
    attempt_path.write_text(json.dumps(attempt, indent=2) + '\n')
    start = time.monotonic()
    print('Starting:', label, flush=True)
    env = dict(os.environ, JAVA_HOME=str(JDK))
    with log.open('w', encoding='utf-8') as out:
        result = subprocess.run(args, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT, timeout=900, env=env)
    attempt.update({'exit_code': result.returncode, 'log': str(log.relative_to(ROOT)), 'log_sha256': movement.sha(log),
                    'shared_config_after_sha256': movement.sha(shared),
                    'status': 'exited' if result.returncode == 0 else 'failed'})
    attempt_path.write_text(json.dumps(attempt, indent=2) + '\n')
    assert result.returncode == 0, 'launch failed: ' + str(log)
    assert manifest_check(frozen) == hashes, 'sources changed during launch'
    assert movement.sha(shared) == before, 'shared config changed'
    text = log.read_text(encoding='utf-8', errors='replace')
    assert 'BUILD SUCCESSFUL' in text
    for bad in ('GL ERROR', 'OpenGLException', 'Particle vertex mismatch', 'Benchmark aborted', 'Benchmark timed out',
                'Mixin apply failed', 'Exception in thread', 'particle vertex format has been changed'):
        assert bad not in text, (label, bad)
    folders = list(movement.BENCH.glob('*-' + label + '-*'))
    assert len(folders) == 1
    folder = folders[0]
    summary = json.loads((folder / 'summary.json').read_text())
    env_out = summary['environment']
    assert env_out['java.version'].startswith('25.')
    assert env_out['alacer.configSource'] == 'build-defaults-and-jvm-overrides'
    assert env_out['benchmark.generationRun'] == 'false'
    assert env_out['window'] == '1920x1080' and env_out['vboActive'] == 'false'
    assert env_out['options.renderDistance'] == '12' and env_out['options.vsync'] == 'false'
    assert env_out['benchmark.particles'] == 'true' and env_out['benchmark.particleScale'] == str(scale)
    assert env_out['benchmark.mobs'] == '0' and env_out['benchmark.chests'] == '0'
    assert env_out['profiler.allocationAvailable'] == 'true'
    assert env_out['benchmark.toggle'] == (OPTION + ' (a=false, b=true)' if kind == 'control' else '')
    configs = {}
    for name, phase in summary['phases'].items():
        assert phase['frames'] > 0 and phase['dropped_frames'] == 0 and phase['discarded_setup_frames'] == 1
        config = json.loads((folder / (name + '.json')).read_text())['config']
        for key in ON:
            assert config['render.' + key] == 'true', (label, name, key)
        for key in OFF:
            assert config['render.' + key] == 'false', (label, name, key)
        for key, value in config.items():
            if key.endswith('.verify'):
                assert value == str(kind == 'diagnostic' and key == OPTION + '.verify').lower(), (label, name, key)
        for key in PROFILER_OFF:
            assert config['profiler.' + key] == 'false', (label, name, key)
        variant = kind in ('diagnostic', 'profile', 'on') or (kind == 'control' and name.endswith(('-b', '-b1', '-b2')))
        assert config[OPTION] == str(variant).lower(), (label, name)
        assert config['options.particles'] == '0'
        configs[name] = config
    if kind == 'control':
        expected = {f'{phase}-{segment}' for phase in ('static', 'pan') for segment in ('a1', 'b1', 'b2', 'a2')}
        assert expected.issubset(configs)
        normalized = [{k: v for k, v in c.items() if k != OPTION} for c in configs.values()]
        assert all(c == normalized[0] for c in normalized), label
    final = re.search(r'Particle vertices final: (\d+) elements compared \((\d+) complete vertices, (\d+) incomplete, '
                      r'(\d+) vanilla vertices, (\d+)/(\d+) passes with another layout\), (\d+) mismatched', text)
    assert final, label
    if kind == 'diagnostic':
        assert int(final[1]) >= 10_000_000 and int(final[3]) == 0 and int(final[5]) == 0 and int(final[7]) == 0, final[0]
        assert int(final[1]) == 4 * int(final[2]), final[0]
    record = {'label': label, 'scale': scale, 'kind': kind, 'command': args, 'java_home': str(JDK),
              'elapsed_seconds': time.monotonic() - start, 'source': str(folder.relative_to(ROOT)),
              'source_snapshot': snapshot, 'frozen_manifest': frozen is not None,
              'shared_config_before_sha256': before, 'shared_config_after_sha256': movement.sha(shared),
              'files_sha256': {p.name: movement.sha(p) for p in folder.iterdir() if p.is_file()},
              'log': str(log.relative_to(ROOT)), 'log_sha256': movement.sha(log),
              'verification_lines': [line for line in text.splitlines()
                                     if 'Particle vert' in line or 'Particle light queries final' in line
                                     or 'Particle removal final' in line],
              'phase_configs': configs}
    jfr = ROOT / 'run' / (label + '.jfr')
    if jfr.exists():
        record['jfr'] = str(jfr.relative_to(ROOT))
        record['jfr_sha256'] = movement.sha(jfr)
    record_path.parent.mkdir(parents=True, exist_ok=True)
    record_path.write_text(json.dumps(record, indent=2) + '\n')
    (record_path.parent / (label + '.summary.json')).write_text(json.dumps(summary, indent=2) + '\n')
    print('Passed:', label, flush=True)


def jobs_for(group, prefix):
    if group == 'controls':
        # Four launches per scale, alternating scale order by round; one startup-absent control per scale
        # after rounds 2 and 4.
        jobs = []
        for repeat in range(1, 5):
            for scale in ((1, 2) if repeat % 2 else (2, 1)):
                jobs.append((f'{prefix}-ab-{scale}-r{repeat}', scale, 'control'))
            if repeat == 2:
                jobs.append((f'{prefix}-absent-1', 1, 'absent'))
            if repeat == 4:
                jobs.append((f'{prefix}-absent-2', 2, 'absent'))
        return jobs
    if group == 'startup':
        # Addendum: option on at startup versus hooks absent, both without a toggle.
        jobs = []
        for scale in (1, 2, 2, 1):
            n = sum(1 for j in jobs if j[1] == scale) // 2 + 1
            order = ('absent', 'on') if n == 1 else ('on', 'absent')
            for kind in order:
                jobs.append((f'{prefix}-startup-{kind}-{scale}-r{n}', scale, kind))
        return jobs
    if group == 'diagnostics':
        return [(prefix + '-check-2', 2, 'diagnostic')]
    return [(prefix + '-profile-' + str(scale), scale, 'profile') for scale in (1, 2)]


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('group', choices=('diagnostics', 'controls', 'startup', 'profiles', 'freeze'))
    parser.add_argument('--only')
    parser.add_argument('--prefix', default='pv20')
    parser.add_argument('--archive', type=Path, default=ARCHIVE)
    opts = parser.parse_args()
    assert re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*', opts.prefix)
    ARCHIVE = (ROOT / opts.archive).resolve()
    assert ARCHIVE.is_relative_to(ROOT)
    movement.ARCHIVE = ARCHIVE
    movement.OPTION = OPTION
    manifest_path = ARCHIVE / 'manifest.json'
    if opts.group == 'freeze':
        assert not manifest_path.exists(), 'manifest already frozen'
        hashes = movement.sources()
        manifest = {'frozen_at': time.strftime('%Y-%m-%dT%H:%M:%S%z'), 'source_sha256': hashes,
                    'source_snapshot': movement.snapshot(hashes)}
        manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
        print('Frozen:', manifest['source_snapshot'])
        raise SystemExit(0)
    frozen = json.loads(manifest_path.read_text()) if manifest_path.exists() else None
    assert frozen is not None, 'freeze the manifest before measured launches'
    jobs = jobs_for(opts.group, opts.prefix)
    if opts.only:
        assert opts.only in {job[0] for job in jobs}
    for job in jobs:
        if not opts.only or opts.only == job[0]:
            run(*job, frozen)
