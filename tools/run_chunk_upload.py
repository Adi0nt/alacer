#!/usr/bin/env python3
"""Session 27 fresh-flight upload attribution. Freeze before measuring; serial clients only."""
import argparse
import datetime
import json
import os
from pathlib import Path
import re
import subprocess
import time
import traceback
import zipfile
import run_whole_client_attribution as prior

ROOT = prior.ROOT
ARCHIVE = ROOT / 'docs/benchmarks/2026-10-21-chunk-upload'
JDK = prior.JDK
BENCH = prior.movement.BENCH
sha = prior.movement.sha
clients = prior.clients
world_hashes = prior.world_hashes


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(value, indent=2) + '\n').encode())


def source_hashes():
    hashes = prior.movement.sources()
    for name in ('tools/run_chunk_upload.py', 'tools/run_whole_client_attribution.py', 'tools/run_particle_movement.py',
                 'settings.gradle', 'gradle/gradle-daemon-jvm.properties', 'gradle/wrapper/gradle-wrapper.properties'):
        hashes[name] = sha(ROOT / name)
    for name in ('protocol.md', 'analyze.py', 'runtime-bytecode.txt', 'runtime-identity.json'):
        hashes[str((ARCHIVE / name).relative_to(ROOT))] = sha(ARCHIVE / name)
    assert all(hashes.values())
    return hashes


def artifacts():
    return {str(p.relative_to(ROOT)): sha(p) for folder in ('build/classes/java/main', 'build/resources/main')
            for p in sorted((ROOT / folder).rglob('*')) if p.is_file()}


def check(manifest):
    assert source_hashes() == manifest['source_sha256'], 'frozen sources/tooling changed'
    assert artifacts() == manifest['runtime_artifacts_sha256'], 'runtime artifacts changed'
    assert world_hashes() == manifest['base_world_sha256'], 'base world changed'
    assert sha(ROOT / 'run/config/alacer.properties') == prior.SHARED_SHA, 'shared configuration changed'
    assert sha(ROOT / manifest['source_snapshot']['path']) == manifest['source_snapshot']['sha256']


def freeze():
    assert clients() == [], 'client running during freeze'
    assert not (ARCHIVE / 'manifest.json').exists(), 'already frozen'
    h = source_hashes()
    prior.movement.ARCHIVE = ARCHIVE
    snap = prior.movement.snapshot(h)
    assert world_hashes() and artifacts()
    m = {'session': 27, 'frozen_at_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
         'source_sha256': h, 'source_snapshot': snap, 'base_world_sha256': world_hashes(),
         'runtime_artifacts_sha256': artifacts(), 'shared_config_sha256': prior.SHARED_SHA,
         'java_home': str(JDK), 'jobs': jobs()}
    check(m)
    write(ARCHIVE / 'manifest.json', m)
    print('Frozen:', snap, flush=True)


def jobs():
    # Reverse scene order in alternate blocks; diagnostics never score as controls.
    out = []
    for rep, order in ((1, ('near', 'high')), (2, ('high', 'near')), (3, ('near', 'high'))):
        out.extend({'label': f'cu27-{s}-control-r{rep}', 'scene': s, 'detail': False, 'scale': '1'} for s in order)
    for rep, order in ((1, ('high', 'near')), (2, ('near', 'high'))):
        out.extend({'label': f'cu27-{s}-detail-r{rep}', 'scene': s, 'detail': True, 'scale': '1'} for s in order)
    return out


def options(job):
    props = prior.explicit_options('d0', job['label'])
    props.update({'benchmark.chunkFlight': job['scene'], 'benchmark.timeScale': job['scale'],
                  'benchmark.timeout': '600', 'benchmark.chunkUploadGpu': str(job['detail']).lower(),
                  'profiler.chunkDetail': str(job['detail']).lower(),
                  'benchmark.screenshots': str(job['detail']).lower(), 'benchmark.exit': 'true'})
    return props


def validate(folder, job, log_text, props):
    summary = json.loads((folder / 'summary.json').read_bytes())
    env = summary['environment']
    expected = {'java.version': '25.0.4.1', 'alacer.configSource': 'build-defaults-and-jvm-overrides',
                'benchmark.generationRun': 'false', 'benchmark.anchor': '-200.5 65.0 267.5',
                'window': '1920x1080', 'options.renderDistance': '12', 'options.vsync': 'false',
                'options.fpsLimit': '260', 'options.fancyGraphics': 'true', 'options.smoothLighting': '2',
                'options.mipmapLevels': '4', 'options.clouds': '2', 'options.useVbo': 'false', 'vboActive': 'false',
                'benchmark.mobs': '0', 'benchmark.chests': '0', 'benchmark.backendScenario': 'false',
                'benchmark.particles': 'false', 'benchmark.chunkFlight': job['scene'],
                'benchmark.chunkUploadGpu': str(job['detail']).lower(), 'profiler.allocationAvailable': 'true',
                'benchmark.toggle': '', 'benchmark.toggleReverse': 'false'}
    for k, v in expected.items():
        assert env[k] == v, (job['label'], k, env.get(k), v)
    assert 'fresh chunk-flight world copy verified byte-identical' in log_text
    assert 'BUILD SUCCESSFUL' in log_text and 'Benchmark: results written' in log_text
    for bad in prior.BAD + ('Chunk render task was', 'OpenGL Error', 'FAILED', 'Chunk GPU context changed'):
        assert bad not in log_text, (job['label'], bad)
    # Gradle is just the launcher. No compilation/test/analysis occurs in the game process window.
    assert '> Task :compileJava UP-TO-DATE' in log_text
    assert '> Task :processResources UP-TO-DATE' in log_text
    phases = {'warmup-pan': 10, 'still': 10, 'fresh-flight': 40}
    assert set(summary['phases']) == set(phases)
    configs = {}
    for name, seconds in phases.items():
        p = summary['phases'][name]
        assert p['frames'] > 0 and p['dropped_frames'] == 0 and p['discarded_setup_frames'] == 1
        assert p['target_seconds'] == seconds * float(job['scale'])
        assert abs(p['recorded_wall_seconds'] - p['target_seconds']) < 1
        assert p['alloc_mean_bytes'] > 0 and p['client_thread_cpu_ms_per_frame'] > 0 and p['process_cpu_cores'] > 0
        c = json.loads((folder / (name + '.json')).read_bytes())['config']
        assert c == p['config']
        for k in prior.ON:
            assert c['render.'+k] == 'true', k
        for k in prior.OFF:
            assert c['render.'+k] == 'false', k
        for k, v in c.items():
            if k.endswith('.verify'):
                assert v == 'false', k
        for k in prior.PROFILER_OFF:
            if 'profiler.'+k in c:
                assert c['profiler.'+k] == str(job['detail'] and k == 'chunkDetail').lower(), k
        assert c['backend.renderChunkClass'] == 'net.minecraft.client.render.world.GlListRenderChunk'
        configs[name] = c
    if job['detail']:
        gpu = json.loads((folder / 'chunk-upload-gpu.json').read_bytes())
        assert gpu['supported'] and gpu['timestamp_bits'] > 0 and gpu['issued'] > 0
        assert gpu['completed'] + gpu['outstanding'] == gpu['issued']
    else:
        assert not (folder / 'chunk-upload-gpu.json').exists()
    return summary, configs


def run(job, manifest, smoke=False):
    label = job['label']
    log = ROOT / 'build' / (label + '.log')
    attempt_path = ARCHIVE / 'attempts' / (label + '.json')
    assert not attempt_path.exists() and not log.exists() and not list(BENCH.glob('*-' + label + '-*')), 'reused label'
    assert not (ROOT / 'run' / (label + '.jfr')).exists(), 'existing JFR'
    attempt = dict(job, smoke=smoke, status='preflight', frozen_manifest=manifest is not None)
    write(attempt_path, attempt)  # Every attempt, including preflight failures, is retained.
    launcher = None
    process = None
    started = time.monotonic()
    try:
        assert clients() == [], 'another KnotClient/devlaunchinjector java.exe/javaw.exe client exists'
        if manifest:
            check(manifest)
        before = source_hashes()
        runtime_before = artifacts()
        base_before = world_hashes()
        assert base_before and sha(ROOT / 'run/config/alacer.properties') == prior.SHARED_SHA
        props = options(job)
        command = [str(ROOT / 'gradlew.bat'), 'runClient', '--offline', '--console=plain',
                   '-Pwindow=1920x1080', '-Pxmx=2G'] + ['-Palacer.'+k+'='+v for k, v in props.items()]
        attempt.update(command=command, status='running', prelaunch_clients=[], base_world_sha256=base_before,
                       source_sha256=before, java_home=str(JDK))
        write(attempt_path, attempt)
        print('Starting:', label, flush=True)
        with log.open('x', encoding='utf-8') as out:
            process = subprocess.Popen(command, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT,
                                       env=dict(os.environ, JAVA_HOME=str(JDK)))
            while process.poll() is None:
                found = clients()
                if found:
                    assert len(found) == 1, 'overlapping clients'
                    if launcher is None:
                        launcher = found[0]
                        attempt['launcher'] = launcher
                        write(attempt_path, attempt)
                        assert Path(launcher['ExecutablePath']).resolve() == (JDK / 'bin/java.exe').resolve()
                        cmd = launcher['CommandLine']
                        assert '--enable-native-access=ALL-UNNAMED' in cmd and '-Xmx2G' in cmd
                        assert 'StartFlightRecording' not in cmd and 'DebugNonSafepoints' not in cmd
                        for k, v in props.items():
                            assert '-Dalacer.'+k+'='+v in cmd, ('missing launcher flag', k)
                assert time.monotonic() - started < 900, 'launch timed out'
                time.sleep(1)
        assert process.returncode == 0 and launcher, 'client failed or launcher not observed'
        assert clients() == [], 'client left running'
        assert before == source_hashes(), 'sources/tooling changed during launch'
        assert runtime_before == artifacts(), 'compiled artifacts changed during launch'
        assert base_before == world_hashes(), 'base world changed'
        assert sha(ROOT / 'run/config/alacer.properties') == prior.SHARED_SHA
        if manifest:
            check(manifest)
        folders = list(BENCH.glob('*-' + label + '-*'))
        assert len(folders) == 1
        folder = folders[0]
        summary, configs = validate(folder, job, log.read_text(encoding='utf-8', errors='replace'), props)
        record = dict(job, smoke=smoke, frozen_manifest=manifest is not None, source=str(folder.relative_to(ROOT)),
                      launcher=launcher, command=command, java_home=str(JDK), base_world_sha256=base_before,
                      source_sha256=before, runtime_artifacts_sha256=runtime_before,
                      source_snapshot=manifest['source_snapshot'] if manifest else None,
                      shared_config_before_sha256=prior.SHARED_SHA, shared_config_after_sha256=prior.SHARED_SHA,
                      files_sha256={p.name: sha(p) for p in folder.iterdir() if p.is_file()}, phase_configs=configs)
        if not smoke:
            write(ARCHIVE / 'runs' / (label + '.record.json'), record)
            write(ARCHIVE / 'runs' / (label + '.summary.json'), summary)
        attempt.update(status='passed-smoke' if smoke else 'passed', record=record)
        print('Passed:', label, flush=True)
    except BaseException:
        attempt.update(status='failed', error=traceback.format_exc())
        if process is not None and process.poll() is None:
            subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], capture_output=True)
            process.wait(timeout=30)
        raise
    finally:
        attempt.update(elapsed_seconds=time.monotonic()-started, log=str(log.relative_to(ROOT)), log_sha256=sha(log),
                       exit_code=process.returncode if process else None, launcher=launcher)
        write(attempt_path, attempt)


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('group', choices=('freeze', 'smoke', 'measure'))
    p.add_argument('--only')
    args = p.parse_args()
    if args.group == 'freeze':
        freeze()
    elif args.group == 'smoke':
        assert not (ARCHIVE / 'manifest.json').exists(), 'smoke must precede freeze'
        run({'label': 'cu27-smoke-near', 'scene': 'near', 'detail': True, 'scale': '0.1'}, None, True)
    else:
        m = json.loads((ARCHIVE / 'manifest.json').read_bytes())
        planned = jobs()
        if args.only:
            assert args.only in {j['label'] for j in planned}
            planned = [j for j in planned if j['label'] == args.only]
        for job in planned:
            run(job, m)
