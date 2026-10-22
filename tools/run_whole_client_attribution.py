#!/usr/bin/env python3
"""Session 26: hooks-absent whole-client JFR attribution of the nine shipped defaults (Java 25).

Scenes (benchmark.vbo=false, the game's option default; Alacer forces no backend):
  d0         default static/pan/fly path, 0 mobs
  d200       default static/pan/fly path, 200 mobs
  terrain    terrain backend scenario (stained glass); its two warmups are recorded but never ranked
  particles  particle scenario, particleScale 1 (reference)
All scenes use time scale 4 (session 25's), so a 10 s phase records 40 s.

Every launch passes every render, verification, profiler-detail and benchmark-diagnostic option explicitly,
records 1 ms JFR with session 25's movement-1ms.jfc and DebugNonSafepoints, refuses reused labels/logs/JFR files
and overlapping clients, verifies the actual launcher and its JVM flags, and checks the source tree against a
manifest frozen before the first measured launch. Smoke launches (before the freeze) are attempts, not evidence.
Reuses session 24/25's helpers (run_particle_movement.py snapshot/sha) without changing them.
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
ARCHIVE = ROOT / 'docs/benchmarks/2026-10-21-whole-client-attribution'
JDK = Path('C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot')
JFC = ROOT / 'docs/benchmarks/2026-10-21-particle-movement-regions/movement-1ms.jfc'
SHARED_SHA = '694ff84d8e5672cd7a2ec0ee1b5a3e4eadbe09552d5d65eef93e16f61ceacd31'
ON = ('fastChunkVisibility', 'fastEntityChunkLookup', 'throttleGlErrorChecks', 'avoidMatrixReads', 'cacheClouds',
      'fastEntityShadows', 'fastParticleLight', 'fastParticleRemoval', 'fastParticleVertices')
OFF = ('fastChunkFrustum', 'skipZeroTranslates', 'batchModelLists', 'fastParticleMovement', 'reuseParticleBoxes',
       'scalarParticleEntityQuery', 'fastParticleLightEntry', 'reuseParticleLight')
PROFILER_OFF = ('entityDetail', 'entityStages', 'particleDetail', 'particleLightRepeats', 'chunkDetail', 'shadowTiming',
                'frustumTiming', 'hud', 'particleMovementCensus', 'particleMovementRegions', 'particleThreadCycles',
                'particleDrawDiagnostics')
BENCH_OFF = ('screenshots', 'shadowDiagnostic', 'cloudSweep', 'particleDiagnostic', 'particleRemovalDiagnostic',
             'particleEntityQueryDiagnostic', 'particleBoxDiagnostic', 'particleMovementDiagnostic',
             'movementCensusInactive', 'toggleReverse')
SCENES = {
    'd0': {'time_scale': '4', 'phases': {'static': 10, 'pan': 15, 'fly': 20},
           'props': {'benchmark.entities': '0', 'benchmark.backendScenario': 'false', 'benchmark.particles': 'false'}},
    'd200': {'time_scale': '4', 'phases': {'static': 10, 'pan': 15, 'fly': 20},
             'props': {'benchmark.entities': '200', 'benchmark.backendScenario': 'false', 'benchmark.particles': 'false'}},
    'terrain': {'time_scale': '4',
                'phases': {'warmup-pan': 15, 'warmup-sort': 16, 'steady': 10, 'pan': 15, 'sort': 16, 'fly': 20},
                'props': {'benchmark.entities': '0', 'benchmark.backendScenario': 'true', 'benchmark.particles': 'false'}},
    'particles': {'time_scale': '4', 'phases': {'static': 10, 'pan': 15},
                  'props': {'benchmark.entities': '0', 'benchmark.backendScenario': 'false', 'benchmark.particles': 'true',
                            'benchmark.particleScale': '1'}},
}
TRACKED = ('tools/run_whole_client_attribution.py', 'tools/run_particle_movement.py',
           'docs/benchmarks/2026-10-21-particle-movement-regions/movement-1ms.jfc',
           'docs/benchmarks/2026-10-21-particle-movement-regions/JfrMovementRegions.java',
           'docs/benchmarks/2026-10-21-particle-movement-regions/stream_regions.py',
           'docs/benchmarks/2026-10-21-particle-movement-regions/line_table.py',
           'docs/benchmarks/2026-10-20-particle-light-confirmation/stream_jfr.py',
           'docs/benchmarks/2026-10-20-particle-light-confirmation/JfrPassAttribution.java')
ARCHIVE_TRACKED = ('protocol.md', 'JfrWholeFrame.java', 'stream_frames.py', 'line_table.py', 'analyze.py')
BAD = ('GL ERROR', 'OpenGLException', 'Particle vertex mismatch', 'Benchmark aborted', 'Benchmark timed out',
       'Mixin apply failed', 'Exception in thread', 'particle vertex format has been changed',
       'Particle light entry mismatch', 'Particle light entry position mismatch', 'Particle light mismatch',
       'Particle light reuse mismatch', 'InvalidInjectionException', 'InjectionError', 'Chunk visibility mismatch',
       'Entity chunk lookup mismatch', 'Matrix read mismatch', 'Cloud cache verification: tile')


def explicit_options(scene, label):
    spec = SCENES[scene]
    props = {'benchmark': 'true', 'benchmark.label': label, 'benchmark.vbo': 'false', 'benchmark.chests': '0',
             'benchmark.viewDistance': '12', 'benchmark.timeout': '1500', 'benchmark.timeScale': spec['time_scale'],
             'profiler.enabled': 'true'}
    props.update(spec['props'])
    props.update({'render.' + k: 'true' for k in ON})
    props.update({'render.' + k: 'false' for k in OFF})
    props.update({'render.' + k + '.verify': 'false' for k in ON + OFF})
    props.update({'profiler.' + k: 'false' for k in PROFILER_OFF})
    props.update({'benchmark.' + k: 'false' for k in BENCH_OFF})
    return props


def manifest_check(frozen):
    current = movement.sources()
    for name in TRACKED:
        current[name] = movement.sha(ROOT / name)
    for name in ARCHIVE_TRACKED:
        path = ARCHIVE / name
        current[str(path.relative_to(ROOT)).replace('\\', '/')] = movement.sha(path)
    if frozen is not None:
        assert current == frozen['source_sha256'], 'source tree differs from the frozen manifest'
    return current


def clients():
    probe = subprocess.run(['powershell', '-NoProfile', '-Command',
        "Get-CimInstance Win32_Process -Filter \"name = 'java.exe' OR name = 'javaw.exe'\" | Where-Object { $_.CommandLine -match 'devlaunchinjector.Main|KnotClient' } | Select-Object ProcessId,ExecutablePath,CommandLine | ConvertTo-Json -Compress"],
        capture_output=True, text=True, check=True)
    text = probe.stdout.strip()
    if not text:
        return []
    found = json.loads(text)
    return found if isinstance(found, list) else [found]


def world_hashes():
    base_dir = ROOT / 'run/saves/alacer-benchmark'
    return {str(p.relative_to(base_dir)): movement.sha(p) for p in sorted(base_dir.rglob('*')) if p.is_file()}


def run(label, scene, frozen, smoke=False):
    record_path = ARCHIVE / 'runs' / (label + '.record.json')
    attempt_path = ARCHIVE / 'attempts' / (label + '.json')
    assert not record_path.exists(), 'record already exists: ' + label
    assert not attempt_path.exists(), 'attempt already recorded: ' + label
    assert not list(movement.BENCH.glob('*-' + label + '-*')), 'label already used'
    log = ROOT / 'build' / (label + '.log')
    assert not log.exists(), 'log already exists'
    jfr = ROOT / 'run' / (label + '.jfr')
    assert not jfr.exists(), 'jfr already exists'
    assert clients() == [], 'another client is running'
    spec = SCENES[scene]
    props = explicit_options(scene, label)
    if smoke:
        props['benchmark.timeScale'] = '0.3'
    args = [str(ROOT / 'gradlew.bat'), 'runClient', '--console=plain', '-Pwindow=1920x1080', '-Pxmx=2G',
            '-Pjfr=' + label, '-PjfrSettings=' + JFC.as_posix(), '-PjfrDebugNonSafepoints=true']
    args += ['-Palacer.' + k + '=' + v for k, v in props.items()]
    hashes = manifest_check(frozen)
    if smoke:
        # Pre-freeze tooling may be incomplete; smoke snapshots stay out of the archive.
        archive, movement.ARCHIVE = movement.ARCHIVE, ROOT / 'build/wc26-smoke-snapshots'
        snapshot = movement.snapshot({k: v for k, v in hashes.items() if v is not None})
        movement.ARCHIVE = archive
    else:
        snapshot = movement.snapshot(hashes)
    shared = ROOT / 'run/config/alacer.properties'
    before = movement.sha(shared)
    base_world = world_hashes()
    assert before == SHARED_SHA
    if frozen is not None:
        assert base_world == frozen['base_world_sha256'], 'base world differs from manifest'
    attempt_path.parent.mkdir(parents=True, exist_ok=True)
    attempt = {'label': label, 'scene': scene, 'smoke': smoke, 'command': args, 'source_snapshot': snapshot,
               'frozen_manifest': frozen is not None, 'shared_config_before_sha256': before,
               'base_world_sha256': base_world, 'status': 'running'}
    attempt_path.write_text(json.dumps(attempt, indent=2) + '\n')
    start = time.monotonic()
    print('Starting:', label, flush=True)
    env = dict(os.environ, JAVA_HOME=str(JDK))
    launcher = None
    with log.open('w', encoding='utf-8') as out:
        process = subprocess.Popen(args, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT, env=env)
        while process.poll() is None:
            if launcher is None:
                found = clients()
                if found:
                    assert len(found) == 1, 'overlapping clients'
                    launcher = found[0]
                    assert Path(launcher['ExecutablePath']).resolve() == (JDK / 'bin/java.exe').resolve(), launcher
            if time.monotonic() - start > 1800:
                process.kill()
                raise TimeoutError(label)
            time.sleep(1)
    attempt.update({'exit_code': process.returncode, 'log': str(log.relative_to(ROOT)), 'log_sha256': movement.sha(log),
                    'shared_config_after_sha256': movement.sha(shared), 'launcher': launcher,
                    'elapsed_seconds': time.monotonic() - start,
                    'status': 'exited' if process.returncode == 0 else 'failed'})
    attempt_path.write_text(json.dumps(attempt, indent=2) + '\n')
    assert launcher is not None, 'launcher not captured'
    command = launcher['CommandLine']
    assert '-XX:+DebugNonSafepoints' in command and '-XX:+UnlockDiagnosticVMOptions' in command, 'missing JIT debug flags'
    assert 'settings=' + JFC.as_posix() in command, 'missing JFR settings'
    for k, v in props.items():
        assert '-Dalacer.' + k + '=' + v in command, ('missing launcher property', k)
    assert process.returncode == 0, 'launch failed: ' + str(log)
    assert manifest_check(frozen) == hashes, 'sources changed during launch'
    assert movement.sha(shared) == before, 'shared config changed'
    assert world_hashes() == base_world, 'base world changed'
    text = log.read_text(encoding='utf-8', errors='replace')
    assert 'BUILD SUCCESSFUL' in text
    for bad in BAD:
        assert bad not in text, (label, bad)
    folders = list(movement.BENCH.glob('*-' + label + '-*'))
    assert len(folders) == 1
    folder = folders[0]
    summary = json.loads((folder / 'summary.json').read_text())
    assert not (folder / 'particle-movement-census.json').exists()
    env_out = summary['environment']
    assert env_out['java.version'].startswith('25.')
    assert env_out['alacer.configSource'] == 'build-defaults-and-jvm-overrides'
    assert env_out['benchmark.generationRun'] == 'false'
    assert env_out['window'] == '1920x1080' and env_out['vboActive'] == 'false'
    assert env_out['options.renderDistance'] == '12' and env_out['options.vsync'] == 'false'
    assert env_out['benchmark.mobs'] == spec['props']['benchmark.entities']
    assert env_out['benchmark.chests'] == '0'
    assert env_out['benchmark.backendScenario'] == spec['props']['benchmark.backendScenario']
    assert env_out['benchmark.particles'] == spec['props']['benchmark.particles']
    if scene == 'particles':
        assert env_out['benchmark.particleScale'] == '1'
    assert env_out['profiler.allocationAvailable'] == 'true'
    assert env_out['benchmark.toggle'] == '' and env_out['benchmark.toggleReverse'] == 'false'
    assert env_out.get('profiler.particleMovementCensus', 'false') == 'false'
    assert env_out.get('profiler.particleMovementRegions', 'false') == 'false'
    assert env_out.get('profiler.particleDrawDiagnostics', 'false') == 'false'
    assert env_out.get('benchmark.movementCensusInactive', 'false') == 'false'
    assert env_out['benchmark.particleDiagnostic'] == 'false' and env_out['benchmark.particleRemovalDiagnostic'] == 'false'
    scale = float(props['benchmark.timeScale'])
    configs = {}
    assert set(summary['phases']) == set(spec['phases']), sorted(summary['phases'])
    for name, phase in summary['phases'].items():
        assert phase['frames'] > 0 and phase['dropped_frames'] == 0 and phase['discarded_setup_frames'] == 1
        assert abs(phase['target_seconds'] - spec['phases'][name] * scale) < 1e-9
        assert phase['alloc_mean_bytes'] > 0 and phase['client_thread_cpu_ms_per_frame'] > 0 and phase['process_cpu_cores'] > 0
        config = json.loads((folder / (name + '.json')).read_text())['config']
        for key in ON:
            assert config['render.' + key] == 'true', (label, name, key)
        for key in OFF:
            assert config['render.' + key] == 'false', (label, name, key)
        for key, value in config.items():
            if key.endswith('.verify'):
                assert value == 'false', (label, name, key)
        for key in PROFILER_OFF:
            if 'profiler.' + key in config:
                assert config['profiler.' + key] == 'false', (label, name, key)
        assert config['profiler.entityDetail'] == config['profiler.chunkDetail'] == config['profiler.particleDetail'] == 'false'
        configs[name] = config
    # Exit summaries the defaults print: when present they must report no mismatch.
    for pattern, groups in ((r'Particle light queries final: (\d+) queries compared, (\d+) mismatched', (2,)),
                            (r'Particle entity query final: (\d+) compared, (\d+) mismatched, (\d+) scalar queries', (1, 2, 3)),
                            (r'Particle light reuse final: (\d+) passes, (\d+) reused, (\d+) computed \((\d+) not stored\); '
                             r'(\d+) queries compared \((\d+) reused\), (\d+) mismatched', (1, 2, 5, 7))):
        found = re.search(pattern, text)
        if scene == 'particles':
            assert found, (label, pattern)
        if found:
            assert all(int(found[i]) == 0 for i in groups), (label, pattern)
    assert jfr.exists(), 'jfr missing'
    record = {'label': label, 'scene': scene, 'smoke': smoke, 'command': args, 'java_home': str(JDK),
              'launcher': launcher, 'base_world_sha256': base_world, 'time_scale': props['benchmark.timeScale'],
              'elapsed_seconds': time.monotonic() - start, 'source': str(folder.relative_to(ROOT)),
              'source_snapshot': snapshot, 'frozen_manifest': frozen is not None,
              'shared_config_before_sha256': before, 'shared_config_after_sha256': movement.sha(shared),
              'files_sha256': {p.name: movement.sha(p) for p in folder.iterdir() if p.is_file()},
              'log': str(log.relative_to(ROOT)), 'log_sha256': movement.sha(log),
              'jfr': str(jfr.relative_to(ROOT)), 'jfr_sha256': movement.sha(jfr), 'jfr_settings_sha256': movement.sha(JFC),
              'verification_lines': [line for line in text.splitlines()
                                     if re.search(r'final:|verification|Benchmark: (recording|settled|loading)', line)],
              'phase_configs': configs}
    if smoke:
        attempt['record'] = record
        attempt['status'] = 'passed-smoke'
        attempt_path.write_text(json.dumps(attempt, indent=2) + '\n')
    else:
        record_path.parent.mkdir(parents=True, exist_ok=True)
        record_path.write_text(json.dumps(record, indent=2) + '\n')
        (record_path.parent / (label + '.summary.json')).write_text(json.dumps(summary, indent=2) + '\n')
        attempt['status'] = 'passed'
        attempt_path.write_text(json.dumps(attempt, indent=2) + '\n')
    print('Passed:', label, flush=True)


def jobs_for(group, prefix):
    if group == 'smoke':
        # smoke-terrain completed but failed the unchanged-sources check (tooling was edited during it); retained.
        return [(f'{prefix}-smoke-d200', 'd200', True), (f'{prefix}-smoke-terrain', 'terrain', True),
                (f'{prefix}-smoke-terrain-2', 'terrain', True)]
    order = ('d0', 'd200', 'terrain', 'particles')
    # The second repetition reverses the scene order.
    return [(f'{prefix}-{scene}-r{n}', scene, False) for n in (1, 2) for scene in (order if n == 1 else order[::-1])]


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('group', choices=('smoke', 'profiles', 'freeze'))
    parser.add_argument('--only')
    parser.add_argument('--prefix', default='wc26')
    parser.add_argument('--archive', type=Path, default=ARCHIVE)
    opts = parser.parse_args()
    assert re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*', opts.prefix)
    ARCHIVE = (ROOT / opts.archive).resolve()
    assert ARCHIVE.is_relative_to(ROOT)
    movement.ARCHIVE = ARCHIVE
    manifest_path = ARCHIVE / 'manifest.json'
    if opts.group == 'freeze':
        assert not manifest_path.exists(), 'manifest already frozen'
        hashes = manifest_check(None)
        manifest = {'frozen_at': time.strftime('%Y-%m-%dT%H:%M:%S%z'), 'source_sha256': hashes,
                    'source_snapshot': movement.snapshot(hashes), 'base_world_sha256': world_hashes()}
        manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
        print('Frozen:', manifest['source_snapshot'])
        raise SystemExit(0)
    frozen = json.loads(manifest_path.read_text()) if manifest_path.exists() else None
    if opts.group == 'smoke':
        assert frozen is None, 'smoke launches precede the freeze'
    else:
        assert frozen is not None, 'freeze the manifest before measured launches'
    jobs = jobs_for(opts.group, opts.prefix)
    if opts.only:
        assert opts.only in {job[0] for job in jobs}
    for label, scene, smoke in jobs:
        if not opts.only or opts.only == label:
            run(label, scene, frozen, smoke)
