#!/usr/bin/env python3
"""Session 23: per-pass particle light reuse diagnostics, A/B/B/A controls, startup controls and profiles (Java 25).

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

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[4] / "tools"))
import run_particle_movement as movement

ROOT = movement.ROOT
OPTION = 'render.reuseParticleLight'
ARCHIVE = ROOT / 'docs/benchmarks/2026-10-20-particle-light-confirmation'
JDK = Path('C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot')
ON = ('fastChunkVisibility', 'fastEntityChunkLookup', 'throttleGlErrorChecks', 'avoidMatrixReads', 'cacheClouds',
      'fastEntityShadows', 'fastParticleLight', 'fastParticleRemoval', 'fastParticleVertices')
OFF = ('fastChunkFrustum', 'skipZeroTranslates', 'batchModelLists', 'fastParticleMovement', 'reuseParticleBoxes',
       'scalarParticleEntityQuery', 'fastParticleLightEntry')
PROFILER_OFF = ('entityDetail', 'particleDetail', 'particleLightRepeats', 'chunkDetail', 'shadowTiming', 'frustumTiming', 'hud')


def explicit_options(kind):
    props = {'render.' + key: 'true' for key in ON}
    props.update({'render.' + key: 'false' for key in OFF})
    for key in ON + OFF + ('reuseParticleLight',):
        props['render.' + key + '.verify'] = 'false'
    props.update({'profiler.' + key: 'false' for key in PROFILER_OFF})
    # Profiles record the candidate's remaining costs (attribution only, JFR on).
    props[OPTION] = 'true' if kind in ('diagnostic', 'profile', 'on') else 'false'
    if kind == 'diagnostic':
        props[OPTION + '.verify'] = 'true'
    return props


def manifest_check(frozen):
    current = movement.sources()
    for path in (Path(__file__), Path(__file__).with_name('protocol.md')):
        current[str(path.resolve().relative_to(ROOT))] = movement.sha(path)
    for name in ('tools/run_particle_light_confirmation.py', 'tools/run_particle_movement.py',
                 'docs/benchmarks/2026-10-20-particle-light-confirmation/protocol.md',
                 'docs/benchmarks/2026-10-20-particle-light-confirmation/analyze.py'):
        current[name] = movement.sha(ROOT / name)
    if frozen is not None:
        assert current == frozen['source_sha256'], 'source tree differs from the frozen manifest'
    return current


def run(label, scale, kind, frozen):
    record_path = ARCHIVE / 'runs' / (label + '.record.json')
    if record_path.exists():
        raise AssertionError('record already exists: ' + label)
    assert not list(movement.BENCH.glob('*-' + label + '-*')), 'label already used'
    log = ROOT / 'build' / (label + '.log')
    assert not log.exists(), 'log already exists'
    probe = subprocess.run(['powershell', '-NoProfile', '-Command',
        "@(Get-CimInstance Win32_Process -Filter \"name = 'java.exe' OR name = 'javaw.exe'\" | Where-Object { $_.CommandLine -match 'devlaunchinjector.Main|KnotClient' }).Count"],
        capture_output=True, text=True, check=True)
    assert probe.stdout.strip() == '0', 'another client is running'
    props = {'benchmark': 'true', 'benchmark.label': label, 'benchmark.particles': 'true',
             'benchmark.particleScale': str(scale), 'benchmark.vbo': 'false'}
    effective_kind = 'control' if kind == 'draw' else 'profile' if kind.startswith('profile-') else kind
    props.update(explicit_options(effective_kind))
    reverse = kind in ('control', 'draw') and int(label.rsplit('r', 1)[1]) % 2 == 0
    props['benchmark.toggleReverse'] = str(reverse).lower()
    props['profiler.particleThreadCycles'] = str(kind == 'draw').lower()
    props['profiler.particleDrawDiagnostics'] = str(kind == 'draw').lower()
    if kind == 'profile-off':
        props[OPTION] = 'false'
    if kind == 'diagnostic':
        props.update({'benchmark.particleDiagnostic': 'true', 'benchmark.particleRemovalDiagnostic': 'true', 'benchmark.screenshots': 'true'})
    elif kind in ('control', 'draw'):
        props['benchmark.toggle'] = OPTION
    args = [str(ROOT / 'gradlew.bat'), 'runClient', '--console=plain', '-Pwindow=1920x1080', '-Pxmx=2G']
    if kind.startswith('profile-'):
        assert not (ROOT / 'run' / (label + '.jfr')).exists()
        args.append('-Pjfr=' + label)
    args += ['-Palacer.' + k + '=' + v for k, v in props.items()]
    hashes = manifest_check(frozen)
    snapshot = movement.snapshot(hashes)
    shared = ROOT / 'run/config/alacer.properties'
    before = movement.sha(shared)
    base_world = {str(p.relative_to(ROOT / 'run/saves/alacer-benchmark')): movement.sha(p)
                  for p in sorted((ROOT / 'run/saves/alacer-benchmark').rglob('*')) if p.is_file()}
    assert base_world == frozen['base_world_sha256'], 'base world differs from manifest'
    assert before == '694ff84d8e5672cd7a2ec0ee1b5a3e4eadbe09552d5d65eef93e16f61ceacd31'
    attempt_path = ARCHIVE / 'attempts' / (label + '.json')
    attempt_path.parent.mkdir(parents=True, exist_ok=True)
    attempt = {'label': label, 'kind': kind, 'scale': scale, 'command': args, 'source_snapshot': snapshot,
               'shared_config_before_sha256': before, 'base_world_sha256': base_world, 'status': 'running'}
    attempt_path.write_text(json.dumps(attempt, indent=2) + '\n')
    start = time.monotonic()
    print('Starting:', label, flush=True)
    env = dict(os.environ, JAVA_HOME=str(JDK))
    with log.open('w', encoding='utf-8') as out:
        process = subprocess.Popen(args, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT, env=env)
        launcher = None
        while process.poll() is None:
            if launcher is None:
                probe = subprocess.run(['powershell', '-NoProfile', '-Command',
                    "Get-CimInstance Win32_Process -Filter \"name = 'java.exe' OR name = 'javaw.exe'\" | Where-Object { $_.CommandLine -match 'devlaunchinjector.Main|KnotClient' } | Select-Object ProcessId,ExecutablePath,CommandLine | ConvertTo-Json -Compress"], capture_output=True, text=True, check=True)
                if probe.stdout.strip():
                    launcher = json.loads(probe.stdout)
                    assert isinstance(launcher, dict), 'overlapping clients'
                    assert Path(launcher['ExecutablePath']).resolve() == (JDK / 'bin/java.exe').resolve(), launcher
            if time.monotonic() - start > 900:
                raise TimeoutError(label)
            time.sleep(1)
        result = process
    attempt.update({'exit_code': result.returncode, 'log': str(log.relative_to(ROOT)), 'log_sha256': movement.sha(log),
                    'shared_config_after_sha256': movement.sha(shared),
                    'status': 'exited' if result.returncode == 0 else 'failed'})
    attempt_path.write_text(json.dumps(attempt, indent=2) + '\n')
    assert launcher is not None, 'launcher not captured'
    assert result.returncode == 0, 'launch failed: ' + str(log)
    assert manifest_check(frozen) == hashes, 'sources changed during launch'
    assert movement.sha(shared) == before, 'shared config changed'
    assert base_world == {str(p.relative_to(ROOT / 'run/saves/alacer-benchmark')): movement.sha(p)
                         for p in sorted((ROOT / 'run/saves/alacer-benchmark').rglob('*')) if p.is_file()}, 'base world changed'
    text = log.read_text(encoding='utf-8', errors='replace')
    assert 'BUILD SUCCESSFUL' in text
    for bad in ('GL ERROR', 'OpenGLException', 'Particle vertex mismatch', 'Benchmark aborted', 'Benchmark timed out',
                'Mixin apply failed', 'Exception in thread', 'particle vertex format has been changed',
                'Particle light entry mismatch', 'Particle light entry position mismatch', 'Particle light mismatch',
                'Particle light reuse mismatch'):
        assert bad not in text, (label, bad)
    folders = list(movement.BENCH.glob('*-' + label + '-*'))
    assert len(folders) == 1
    folder = folders[0]
    summary = json.loads((folder / 'summary.json').read_text())
    if kind == 'draw':
        diagnostic = json.loads((folder / 'particle-draw-diagnostics.json').read_text())
        assert diagnostic['thread_cycles_enabled'] and diagnostic['thread_cycles_supported'] and diagnostic['thread_cycles_available']
        assert diagnostic['attribution_only'] and diagnostic['cpu_supported'] and diagnostic['allocation_supported']
        for phase, totals in diagnostic['phases'].items():
            assert totals['draw_thread_cycle_samples'] == totals['draws']
            assert totals['pass_thread_cycle_samples'] == totals['passes']
            assert totals['passes'] > 0 and totals['draws'] > 0
            assert totals['draw_cpu_samples'] == totals['draws']
            assert totals['pass_allocation_samples'] == totals['passes']
            assert totals['issued'] == totals['completed'] + totals['outstanding']
            assert totals['draws'] == totals['issued'] + totals['dropped']
            if diagnostic['gpu_supported']:
                assert totals['completed'] > 0
    env_out = summary['environment']
    assert env_out['java.version'].startswith('25.')
    assert env_out['alacer.configSource'] == 'build-defaults-and-jvm-overrides'
    assert env_out['benchmark.generationRun'] == 'false'
    assert env_out['window'] == '1920x1080' and env_out['vboActive'] == 'false'
    assert env_out['options.renderDistance'] == '12' and env_out['options.vsync'] == 'false'
    assert env_out['benchmark.particles'] == 'true' and env_out['benchmark.particleScale'] == str(scale)
    assert env_out['benchmark.mobs'] == '0' and env_out['benchmark.chests'] == '0'
    assert env_out['profiler.allocationAvailable'] == 'true'
    assert env_out['benchmark.toggle'] == (OPTION + ' (a=false, b=true)' if kind in ('control', 'draw') else '')
    assert env_out['benchmark.toggleReverse'] == str(reverse).lower()
    assert env_out['profiler.particleDrawDiagnostics'] == str(kind == 'draw').lower()
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
        variant = kind in ('diagnostic', 'profile-on', 'on') or (kind in ('control', 'draw') and name.endswith(('-b', '-b1', '-b2')))
        assert config[OPTION] == str(variant).lower(), (label, name)
        assert config['options.particles'] == '0'
        configs[name] = config
    if kind in ('control', 'draw'):
        expected = {f'{phase}-{segment}' for phase in ('static', 'pan') for segment in ('a1', 'b1', 'b2', 'a2')}
        assert expected.issubset(configs)
        normalized = [{k: v for k, v in c.items() if k != OPTION} for c in configs.values()]
        assert all(c == normalized[0] for c in normalized), label
    entry = re.search(r'Particle light entry final: (\d+) chunk checks compared \((\d+) not loaded\), (\d+) mismatched; '
                      r'(\d+) positions compared, (\d+) mismatched', text)
    assert entry and int(entry[1]) == 0 and int(entry[4]) == 0, label
    final = re.search(r'Particle light reuse final: (\d+) passes, (\d+) reused, (\d+) computed \((\d+) not stored\); '
                      r'(\d+) queries compared \((\d+) reused\), (\d+) mismatched', text)
    assert final, label
    if kind == 'diagnostic':
        assert int(final[5]) >= 10_000_000 and int(final[6]) >= 5_000_000 and int(final[7]) == 0, final[0]
    else:
        assert int(final[5]) == 0, final[0]
    if kind in ('absent', 'profile-off'):
        assert int(final[1]) == 0 and int(final[2]) == 0, final[0]
    else:
        assert int(final[1]) > 0 and int(final[2]) > 0, final[0]
    light = re.search(r'Particle light queries final: (\d+) queries compared, (\d+) mismatched', text)
    assert light and int(light[2]) == 0, label
    record = {'label': label, 'scale': scale, 'kind': kind, 'command': args, 'java_home': str(JDK),
              'launcher': launcher, 'base_world_sha256': base_world, 'reverse': reverse,
              'elapsed_seconds': time.monotonic() - start, 'source': str(folder.relative_to(ROOT)),
              'source_snapshot': snapshot, 'frozen_manifest': frozen is not None,
              'shared_config_before_sha256': before, 'shared_config_after_sha256': movement.sha(shared),
              'files_sha256': {p.name: movement.sha(p) for p in folder.iterdir() if p.is_file()},
              'log': str(log.relative_to(ROOT)), 'log_sha256': movement.sha(log),
              'verification_lines': [line for line in text.splitlines()
                                     if 'Particle light reuse' in line or 'Particle light entry final' in line
                                     or 'Particle light queries final' in line
                                     or 'Particle vertices final' in line
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
        return [(f'{prefix}-ab-1-r{repeat}', 1, 'control') for repeat in range(1, 9)]
    if group == 'startup':
        return [(f'{prefix}-startup-{kind}-1-r{repeat}', 1, kind) for repeat in range(1, 5)
                for kind in (('absent', 'on') if repeat % 2 else ('on', 'absent'))]
    if group == 'diagnostics':
        return [(prefix + '-check-2', 2, 'diagnostic'), (prefix + '-check-1', 1, 'diagnostic')]
    if group == 'draws':
        return [(f'{prefix}-draw-1-r{n}', 1, 'draw') for n in (1, 2)]
    return [(f'{prefix}-profile-{kind}-1-r{n}', 1, 'profile-' + kind) for n in (1, 2)
            for kind in (('off', 'on') if n == 1 else ('on', 'off'))]


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('group', choices=('diagnostics', 'controls', 'startup', 'profiles', 'draws', 'freeze'))
    parser.add_argument('--only')
    parser.add_argument('--prefix', default='lc23')
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
        hashes = manifest_check(None)
        manifest = {'frozen_at': time.strftime('%Y-%m-%dT%H:%M:%S%z'), 'source_sha256': hashes,
                    'source_snapshot': movement.snapshot(hashes),
                    'base_world_sha256': {str(p.relative_to(ROOT / 'run/saves/alacer-benchmark')): movement.sha(p)
                                         for p in sorted((ROOT / 'run/saves/alacer-benchmark').rglob('*')) if p.is_file()}}
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
