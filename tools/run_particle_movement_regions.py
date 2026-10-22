#!/usr/bin/env python3
"""Session 25: particle movement region attribution (Java 25).

Reuses session 24's option set, snapshot and launch checks. Every launch passes every render option
explicitly, refuses reused labels/logs and overlapping clients, verifies the actual launcher and
checks the source tree against a manifest frozen before the first measured launch.

Kinds:
  regions  census plus session 25 region marks (intrusive counts and times)
  jfr      no census/detail hooks; high-rate execution sampling (archive .jfc) and DebugNonSafepoints
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time

import run_particle_movement as movement
import run_particle_movement_attribution as base

ROOT = movement.ROOT
ARCHIVE = ROOT / 'docs/benchmarks/2026-10-21-particle-movement-regions'
JDK = base.JDK
TIME_SCALE = {'regions': '1', 'jfr': '4'}
TRACKED = ('tools/run_particle_movement_regions.py', 'tools/run_particle_movement_attribution.py',
           'tools/run_particle_movement.py',
           'docs/benchmarks/2026-10-20-particle-light-confirmation/analyze.py',
           'docs/benchmarks/2026-10-20-particle-light-confirmation/stream_jfr.py',
           'docs/benchmarks/2026-10-20-particle-light-confirmation/JfrPassAttribution.java')
ARCHIVE_TRACKED = ('protocol.md', 'analyze.py', 'JfrMovementRegions.java', 'stream_regions.py', 'line_table.py',
                   'movement-1ms.jfc')


def explicit_options(kind):
    props = base.explicit_options('census' if kind == 'regions' else 'absent')
    props['profiler.particleMovementRegions'] = str(kind == 'regions').lower()
    props['benchmark.timeScale'] = TIME_SCALE[kind]
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


def run(label, scale, kind, frozen):
    record_path = ARCHIVE / 'runs' / (label + '.record.json')
    assert not record_path.exists(), 'record already exists: ' + label
    assert not list(movement.BENCH.glob('*-' + label + '-*')), 'label already used'
    log = ROOT / 'build' / (label + '.log')
    assert not log.exists(), 'log already exists'
    jfr = ROOT / 'run' / (label + '.jfr')
    assert not jfr.exists(), 'jfr already exists'
    assert clients() == [], 'another client is running'
    props = {'benchmark': 'true', 'benchmark.label': label, 'benchmark.particles': 'true',
             'benchmark.particleScale': str(scale), 'benchmark.vbo': 'false',
             'benchmark.toggleReverse': 'false', 'profiler.particleDrawDiagnostics': 'false'}
    props.update(explicit_options(kind))
    args = [str(ROOT / 'gradlew.bat'), 'runClient', '--console=plain', '-Pwindow=1920x1080', '-Pxmx=2G']
    settings = ARCHIVE / 'movement-1ms.jfc'
    if kind == 'jfr':
        args += ['-Pjfr=' + label, '-PjfrSettings=' + settings.as_posix(), '-PjfrDebugNonSafepoints=true']
    args += ['-Palacer.' + k + '=' + v for k, v in props.items()]
    hashes = manifest_check(frozen)
    snapshot = movement.snapshot(hashes)
    shared = ROOT / 'run/config/alacer.properties'
    before = movement.sha(shared)
    base_world = world_hashes()
    assert base_world == frozen['base_world_sha256'], 'base world differs from manifest'
    assert before == '694ff84d8e5672cd7a2ec0ee1b5a3e4eadbe09552d5d65eef93e16f61ceacd31'
    attempt_path = ARCHIVE / 'attempts' / (label + '.json')
    assert not attempt_path.exists(), 'attempt already recorded: ' + label
    attempt_path.parent.mkdir(parents=True, exist_ok=True)
    attempt = {'label': label, 'kind': kind, 'scale': scale, 'command': args, 'source_snapshot': snapshot,
               'shared_config_before_sha256': before, 'base_world_sha256': base_world, 'status': 'running'}
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
            if time.monotonic() - start > 1200:
                process.kill()
                raise TimeoutError(label)
            time.sleep(1)
    attempt.update({'exit_code': process.returncode, 'log': str(log.relative_to(ROOT)), 'log_sha256': movement.sha(log),
                    'shared_config_after_sha256': movement.sha(shared), 'launcher': launcher,
                    'status': 'exited' if process.returncode == 0 else 'failed'})
    attempt_path.write_text(json.dumps(attempt, indent=2) + '\n')
    assert launcher is not None, 'launcher not captured'
    command = launcher['CommandLine']
    if kind == 'jfr':
        assert '-XX:+DebugNonSafepoints' in command and '-XX:+UnlockDiagnosticVMOptions' in command, 'missing JIT debug flags'
        assert 'settings=' + settings.as_posix() in command, 'missing JFR settings'
    else:
        assert 'StartFlightRecording' not in command and 'DebugNonSafepoints' not in command
    assert '-Dalacer.profiler.particleMovementRegions=' + str(kind == 'regions').lower() in command
    assert process.returncode == 0, 'launch failed: ' + str(log)
    assert manifest_check(frozen) == hashes, 'sources changed during launch'
    assert movement.sha(shared) == before, 'shared config changed'
    assert world_hashes() == base_world, 'base world changed'
    text = log.read_text(encoding='utf-8', errors='replace')
    assert 'BUILD SUCCESSFUL' in text
    for bad in ('GL ERROR', 'OpenGLException', 'Particle vertex mismatch', 'Benchmark aborted', 'Benchmark timed out',
                'Mixin apply failed', 'Exception in thread', 'particle vertex format has been changed',
                'Particle light entry mismatch', 'Particle light entry position mismatch', 'Particle light mismatch',
                'Particle light reuse mismatch', 'InvalidInjectionException', 'InjectionError'):
        assert bad not in text, (label, bad)
    folders = list(movement.BENCH.glob('*-' + label + '-*'))
    assert len(folders) == 1
    folder = folders[0]
    summary = json.loads((folder / 'summary.json').read_text())
    census_path = folder / 'particle-movement-census.json'
    if kind == 'regions':
        census = json.loads(census_path.read_text())
        assert census.get('regions_enabled') is True and census.get('regions_schema') == 2
        for k in ('dropped_phase_activations', 'move_depth_overflows', 'incomplete_moves_at_boundary',
                  'unclosed_queries', 'unmatched_move_returns', 'unmatched_query_returns'):
            assert census[k] == 0, (label, k)
        assert census['excluded_particle_moves_seen_on_owner'] > 0
        assert set(census['phases']) == {'static', 'pan'}
        for phase in census['phases'].values():
            c = phase['total']
            assert c['moves_started'] == c['moves_returned'] == c['timed_moves'] > 0
            assert c['world_query_calls'] == c['world_query_returns'] > 0
            assert c['unknown_world_list_results'] == c['unknown_site_list_results'] == 0
            assert c['nested_moves'] == 0
            assert c['ordinary_query_returns'] == c['moves_started'] - c['no_clip_at_entry']
            assert c['region_order_anomalies'] == 0, label
            assert c['region_moves'] == c['ordinary_query_returns'], label
            assert c['region_no_clip_moves'] == c['no_clip_at_entry'], label
            assert c['unknown_entity_query_lists'] == 0
            assert c['gather_block_boxes'] + c['gather_entity_boxes'] == c['ordinary_result_size_sum'], label
    else:
        assert not census_path.exists()
    entity = re.search(r'Particle entity query final: (\d+) compared, (\d+) mismatched, (\d+) scalar queries', text)
    assert entity and all(int(entity[i]) == 0 for i in (1, 2, 3)), label
    env_out = summary['environment']
    assert env_out['java.version'].startswith('25.')
    assert env_out['alacer.configSource'] == 'build-defaults-and-jvm-overrides'
    assert env_out['benchmark.generationRun'] == 'false'
    assert env_out['window'] == '1920x1080' and env_out['vboActive'] == 'false'
    assert env_out['options.renderDistance'] == '12' and env_out['options.vsync'] == 'false'
    assert env_out['benchmark.particles'] == 'true' and env_out['benchmark.particleScale'] == str(scale)
    assert env_out['benchmark.mobs'] == '0' and env_out['benchmark.chests'] == '0'
    assert env_out['profiler.allocationAvailable'] == 'true'
    assert env_out['benchmark.toggle'] == '' and env_out['benchmark.toggleReverse'] == 'false'
    assert env_out['profiler.particleDrawDiagnostics'] == 'false'
    assert env_out['profiler.particleMovementCensus'] == str(kind == 'regions').lower()
    assert env_out['profiler.particleMovementRegions'] == str(kind == 'regions').lower()
    assert env_out['benchmark.movementCensusInactive'] == 'false'
    configs = {}
    for name, phase in summary['phases'].items():
        assert phase['frames'] > 0 and phase['dropped_frames'] == 0 and phase['discarded_setup_frames'] == 1
        assert abs(phase['target_seconds'] - {'static': 10, 'pan': 15}[name] * float(TIME_SCALE[kind])) < 1e-9
        config = json.loads((folder / (name + '.json')).read_text())['config']
        for key in base.ON:
            assert config['render.' + key] == 'true', (label, name, key)
        for key in base.OFF:
            assert config['render.' + key] == 'false', (label, name, key)
        for key, value in config.items():
            if key.endswith('.verify'):
                assert value == 'false', (label, name, key)
        for key in base.PROFILER_OFF:
            assert config['profiler.' + key] == 'false', (label, name, key)
        assert config['render.reuseParticleLight'] == 'false'
        assert config['options.particles'] == '0'
        configs[name] = config
    assert set(configs) == {'static', 'pan'}
    entry = re.search(r'Particle light entry final: (\d+) chunk checks compared \((\d+) not loaded\), (\d+) mismatched; '
                      r'(\d+) positions compared, (\d+) mismatched', text)
    assert entry and int(entry[1]) == 0 and int(entry[4]) == 0, label
    final = re.search(r'Particle light reuse final: (\d+) passes, (\d+) reused, (\d+) computed \((\d+) not stored\); '
                      r'(\d+) queries compared \((\d+) reused\), (\d+) mismatched', text)
    assert final and all(int(final[i]) == 0 for i in (1, 2, 5, 7)), label
    light = re.search(r'Particle light queries final: (\d+) queries compared, (\d+) mismatched', text)
    assert light and int(light[2]) == 0, label
    record = {'label': label, 'scale': scale, 'kind': kind, 'command': args, 'java_home': str(JDK),
              'launcher': launcher, 'base_world_sha256': base_world, 'time_scale': TIME_SCALE[kind],
              'elapsed_seconds': time.monotonic() - start, 'source': str(folder.relative_to(ROOT)),
              'source_snapshot': snapshot, 'frozen_manifest': True,
              'shared_config_before_sha256': before, 'shared_config_after_sha256': movement.sha(shared),
              'files_sha256': {p.name: movement.sha(p) for p in folder.iterdir() if p.is_file()},
              'log': str(log.relative_to(ROOT)), 'log_sha256': movement.sha(log),
              'verification_lines': [line for line in text.splitlines()
                                     if 'Particle light reuse' in line or 'Particle light entry final' in line
                                     or 'Particle light queries final' in line or 'Particle vertices final' in line
                                     or 'Particle removal final' in line or 'Particle entity query final' in line],
              'phase_configs': configs}
    if kind == 'jfr':
        assert jfr.exists(), 'jfr missing'
        record['jfr'] = str(jfr.relative_to(ROOT))
        record['jfr_sha256'] = movement.sha(jfr)
        record['jfr_settings_sha256'] = movement.sha(settings)
    else:
        assert not jfr.exists()
    record_path.parent.mkdir(parents=True, exist_ok=True)
    record_path.write_text(json.dumps(record, indent=2) + '\n')
    (record_path.parent / (label + '.summary.json')).write_text(json.dumps(summary, indent=2) + '\n')
    print('Passed:', label, flush=True)


def jobs_for(group, prefix):
    if group == 'regions':
        return [(f'{prefix}-regions-{scale}', scale, 'regions') for scale in (1, 2)]
    # Scale order alternates between the two repetitions.
    return [(f'{prefix}-jfr-{scale}-r{n}', scale, 'jfr') for n in (1, 2) for scale in ((1, 2) if n == 1 else (2, 1))]


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('group', choices=('regions', 'profiles', 'freeze'))
    parser.add_argument('--only')
    parser.add_argument('--prefix', default='mr25')
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
    assert frozen is not None, 'freeze the manifest before measured launches'
    jobs = jobs_for(opts.group, opts.prefix)
    if opts.only:
        assert opts.only in {job[0] for job in jobs}
    for job in jobs:
        if not opts.only or opts.only == job[0]:
            run(*job, frozen)
