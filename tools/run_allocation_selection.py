#!/usr/bin/env python3
"""Session 28: allocation-based selection (Java 25, Linux cloud container, Mesa llvmpipe).

Profiles group (hooks-absent allocation attribution of the shipped defaults):
  d0       default static/pan/fly path, 0 mobs
  d200     default static/pan/fly path, 200 mobs
  terrain  terrain backend scenario (display lists; its two warmups are recorded but never ranked)
Time scale 2, 854x480, 2 GiB heap, JFR with the archive's allocation.jfc (JDK 25 profile.jfc with the
allocation sample throttle raised from 300/s to 10000/s) and a fixed 16 KiB TLAB (-PjfrTlab=16k).

Every launch passes every render, verification, profiler-detail and benchmark-diagnostic option explicitly,
refuses reused labels/logs/JFR files and overlapping clients, verifies the actual launcher and its JVM flags
from /proc, and refuses to run unless the source tree and tooling equal the frozen git commit named in the
archive's freeze.json. Smoke launches (before the freeze) are attempts, not evidence.

Usage:
  python3 tools/run_allocation_selection.py smoke|profiles [--only LABEL] --jdk <jdk25> --init <mirror.gradle>
  python3 tools/run_allocation_selection.py freeze      (records HEAD after the protocol commit)
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import threading
import time

ROOT = Path(__file__).resolve().parent.parent
ARCHIVE = ROOT / 'docs/benchmarks/2026-10-22-allocation-selection'
BENCH = ROOT / 'run/alacer/benchmarks'
JFC = ARCHIVE / 'allocation.jfc'
FROZEN_PATHS = ('src', 'build.gradle', 'gradle.properties', 'settings.gradle', 'tools/run_allocation_selection.py',
                'docs/benchmarks/2026-10-22-allocation-selection/protocol.md',
                'docs/benchmarks/2026-10-22-allocation-selection/allocation.jfc',
                'docs/benchmarks/2026-10-22-allocation-selection/AllocSamples.java',
                'docs/benchmarks/2026-10-22-allocation-selection/classify.py',
                'docs/benchmarks/2026-10-22-allocation-selection/decision.py')
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
    'd0': {'phases': {'static': 10, 'pan': 15, 'fly': 20},
           'props': {'benchmark.entities': '0', 'benchmark.backendScenario': 'false', 'benchmark.particles': 'false'}},
    'd200': {'phases': {'static': 10, 'pan': 15, 'fly': 20},
             'props': {'benchmark.entities': '200', 'benchmark.backendScenario': 'false', 'benchmark.particles': 'false'}},
    'terrain': {'phases': {'warmup-pan': 15, 'warmup-sort': 16, 'steady': 10, 'pan': 15, 'sort': 16, 'fly': 20},
                'props': {'benchmark.entities': '0', 'benchmark.backendScenario': 'true', 'benchmark.particles': 'false'}},
}
TIME_SCALE = '2'
WINDOW = '854x480'
TLAB = '16k'
BAD = ('GL ERROR', 'OpenGLException', 'Benchmark aborted', 'Benchmark timed out', 'Mixin apply failed',
       'Exception in thread', 'InvalidInjectionException', 'InjectionError')


def sha(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for block in iter(lambda: f.read(1 << 20), b''):
            h.update(block)
    return h.hexdigest()


def git(*args):
    return subprocess.run(['git', *args], cwd=ROOT, capture_output=True, text=True, check=True).stdout.strip()


def tree_state():
    """HEAD plus whether any frozen path differs from it (tracked changes or untracked files)."""
    head = git('rev-parse', 'HEAD')
    dirty = git('status', '--porcelain', '--', *FROZEN_PATHS)
    return head, dirty


def explicit_options(scene, label):
    props = {'benchmark': 'true', 'benchmark.label': label, 'benchmark.vbo': 'false', 'benchmark.chests': '0',
             'benchmark.viewDistance': '12', 'benchmark.timeout': '1500', 'benchmark.timeScale': TIME_SCALE,
             'profiler.enabled': 'true'}
    props.update(SCENES[scene]['props'])
    props.update({'render.' + k: 'true' for k in ON})
    props.update({'render.' + k: 'false' for k in OFF})
    props.update({'render.' + k + '.verify': 'false' for k in ON + OFF})
    props.update({'profiler.' + k: 'false' for k in PROFILER_OFF})
    props.update({'benchmark.' + k: 'false' for k in BENCH_OFF})
    return props


def clients():
    found = []
    for proc in Path('/proc').iterdir():
        if not proc.name.isdigit():
            continue
        try:
            cmd = (proc / 'cmdline').read_bytes().split(b'\0')
            exe = os.readlink(proc / 'exe')
        except OSError:
            continue
        text = ' '.join(part.decode('utf-8', 'replace') for part in cmd if part)
        if 'KnotClient' in text and 'java' in exe and 'GradleDaemon' not in text and 'GradleWrapperMain' not in text:
            found.append({'pid': int(proc.name), 'exe': exe, 'cmdline': [p.decode('utf-8', 'replace') for p in cmd if p]})
    return found


def world_hashes():
    base_dir = ROOT / 'run/saves/alacer-benchmark'
    return {str(p.relative_to(base_dir)): sha(p) for p in sorted(base_dir.rglob('*')) if p.is_file()}


def run(label, scene, smoke, jdk, init):
    record_path = ARCHIVE / 'runs' / (label + '.record.json')
    attempt_path = ARCHIVE / 'attempts' / (label + '.json')
    assert not record_path.exists() and not attempt_path.exists(), 'label already recorded: ' + label
    assert not list(BENCH.glob('*-' + label + '-*')), 'label already used'
    log = ROOT / 'build' / (label + '.log')
    jfr = ROOT / 'run' / (label + '.jfr')
    assert not log.exists() and not jfr.exists(), 'log or jfr already exists'
    assert clients() == [], 'another client is running'
    head, dirty = tree_state()
    freeze_path = ARCHIVE / 'freeze.json'
    if smoke:
        assert not freeze_path.exists(), 'smoke launches precede the freeze'
    else:
        frozen = json.loads(freeze_path.read_text())
        assert dirty == '', 'frozen paths differ from HEAD:\n' + dirty
        assert git('merge-base', '--is-ancestor', frozen['commit'], 'HEAD') == ''
        assert git('diff', '--name-only', frozen['commit'], 'HEAD', '--', *FROZEN_PATHS) == '', 'frozen paths changed since the freeze'
        assert world_hashes() == frozen['base_world_sha256'], 'base world differs from the freeze'
    props = explicit_options(scene, label)
    if smoke:
        props['benchmark.timeScale'] = '0.5'
    args = ['xvfb-run', '-a', '-s', '-screen 0 1280x720x24 +extension GLX +extension RANDR',
            'sh', './gradlew', 'runClient', '--console=plain', '-I', str(init),
            '-Porg.gradle.java.installations.paths=' + str(jdk), '-Pwindow=' + WINDOW, '-Pxmx=2G',
            '-Pjfr=' + label, '-PjfrSettings=' + str(JFC), '-PjfrTlab=' + TLAB]
    args += ['-Palacer.' + k + '=' + v for k, v in props.items()]
    base_world = world_hashes()
    attempt_path.parent.mkdir(parents=True, exist_ok=True)
    attempt = {'label': label, 'scene': scene, 'smoke': smoke, 'command': args, 'head': head, 'dirty': dirty,
               'base_world_sha256': base_world, 'status': 'running'}
    attempt_path.write_text(json.dumps(attempt, indent=2) + '\n')
    start = time.monotonic()
    print('Starting:', label, flush=True)
    env = dict(os.environ, JAVA_HOME=str(jdk))
    launchers = []

    def watch(process):
        while process.poll() is None:
            for found in clients():
                if found['pid'] not in {l['pid'] for l in launchers}:
                    launchers.append(found)
            time.sleep(1)

    with log.open('w', encoding='utf-8') as out:
        process = subprocess.Popen(args, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT, env=env)
        watcher = threading.Thread(target=watch, args=(process,), daemon=True)
        watcher.start()
        try:
            process.wait(timeout=2400)
        except subprocess.TimeoutExpired:
            process.kill()
            raise
        watcher.join()
    elapsed = time.monotonic() - start
    attempt.update({'exit_code': process.returncode, 'log': str(log.relative_to(ROOT)), 'log_sha256': sha(log),
                    'launchers': launchers, 'elapsed_seconds': elapsed,
                    'status': 'exited' if process.returncode == 0 else 'failed'})
    attempt_path.write_text(json.dumps(attempt, indent=2) + '\n')

    def fail(reason):
        attempt['status'] = 'rejected'
        attempt['reason'] = reason
        attempt_path.write_text(json.dumps(attempt, indent=2) + '\n')
        raise SystemExit('Rejected ' + label + ': ' + reason)

    if len(launchers) != 1:
        fail('expected exactly one client launcher, saw %d' % len(launchers))
    launcher = launchers[0]
    if Path(launcher['exe']).resolve() != (Path(jdk) / 'bin/java').resolve():
        fail('unexpected launcher ' + launcher['exe'])
    command = launcher['cmdline']
    if not any(a.startswith('-XX:StartFlightRecording=settings=' + str(JFC)) for a in command):
        fail('missing JFR settings')
    if '-Xmx2G' not in command:
        fail('missing heap size')
    if '-XX:TLABSize=' + TLAB not in command or '-XX:-ResizeTLAB' not in command:
        fail('missing fixed TLAB size')
    for k, v in props.items():
        if '-Dalacer.' + k + '=' + v not in command:
            fail('missing launcher property ' + k)
    if process.returncode != 0:
        fail('launch failed')
    if tree_state() != (head, dirty):
        fail('sources changed during launch')
    if world_hashes() != base_world:
        fail('base world changed')
    text = log.read_text(encoding='utf-8', errors='replace')
    if 'BUILD SUCCESSFUL' not in text:
        fail('build not successful')
    for bad in BAD:
        if bad in text:
            fail('log contains ' + bad)
    for line in text.splitlines():
        if 'ismatch' in line:
            # Exit summaries ("... final: ..., 0 mismatched") are fine; any nonzero count or other mismatch line is not.
            counts = re.findall(r'(\d+) mismatch', line)
            if 'final:' not in line or not counts or any(int(c) != 0 for c in counts):
                fail('mismatch line: ' + line.strip())
    folders = list(BENCH.glob('*-' + label + '-*'))
    if len(folders) != 1:
        fail('result folder not unique')
    folder = folders[0]
    summary = json.loads((folder / 'summary.json').read_text())
    e = summary['environment']
    checks = {
        'java': e['java.runtime.version'].startswith('25.0.4.1') and e['java.vendor'] == 'Eclipse Adoptium',
        'config source': e['alacer.configSource'] == 'build-defaults-and-jvm-overrides',
        'generation': e['benchmark.generationRun'] == 'false',
        'window': e['window'] == WINDOW and e['vboActive'] == 'false',
        'view/vsync': e['options.renderDistance'] == '12' and e['options.vsync'] == 'false',
        'mobs': e['benchmark.mobs'] == SCENES[scene]['props']['benchmark.entities'] and e['benchmark.chests'] == '0',
        'scenario': e['benchmark.backendScenario'] == SCENES[scene]['props']['benchmark.backendScenario']
                    and e['benchmark.particles'] == 'false',
        'allocation': e['profiler.allocationAvailable'] == 'true',
        'toggle': e['benchmark.toggle'] == '' and e['benchmark.toggleReverse'] == 'false',
        'diagnostics': all(e.get(k, 'false') == 'false' for k in (
            'profiler.particleMovementCensus', 'profiler.particleMovementRegions', 'profiler.particleDrawDiagnostics',
            'benchmark.movementCensusInactive', 'benchmark.particleDiagnostic', 'benchmark.particleRemovalDiagnostic')),
        'renderer': e['gl.renderer'].startswith('llvmpipe'),
    }
    for name, ok in checks.items():
        if not ok:
            fail('environment check failed: ' + name)
    scale = float(props['benchmark.timeScale'])
    if set(summary['phases']) != set(SCENES[scene]['phases']):
        fail('unexpected phases')
    configs = {}
    for name, phase in summary['phases'].items():
        if not (phase['frames'] > 0 and phase['dropped_frames'] == 0 and phase['discarded_setup_frames'] == 1):
            fail('frame checks failed in ' + name)
        if abs(phase['target_seconds'] - SCENES[scene]['phases'][name] * scale) > 1e-9:
            fail('phase duration differs in ' + name)
        if not (phase['alloc_mean_bytes'] > 0 and phase['client_thread_cpu_ms_per_frame'] > 0):
            fail('allocation/CPU not measured in ' + name)
        config = json.loads((folder / (name + '.json')).read_text())['config']
        for key in ON:
            if config['render.' + key] != 'true':
                fail('option not on: ' + key)
        for key in OFF:
            if config['render.' + key] != 'false':
                fail('option not off: ' + key)
        for key, value in config.items():
            if key.endswith('.verify') and value != 'false':
                fail('verification on: ' + key)
        for key in PROFILER_OFF:
            if config.get('profiler.' + key, 'false') != 'false':
                fail('profiler detail on: ' + key)
        configs[name] = config
    if not jfr.exists():
        fail('jfr missing')
    record = {'label': label, 'scene': scene, 'smoke': smoke, 'command': args, 'java_home': str(jdk), 'head': head,
              'launcher': launcher, 'base_world_sha256_count': len(base_world), 'time_scale': props['benchmark.timeScale'],
              'elapsed_seconds': elapsed, 'source': str(folder.relative_to(ROOT)),
              'files_sha256': {p.name: sha(p) for p in folder.iterdir() if p.is_file()},
              'log': str(log.relative_to(ROOT)), 'log_sha256': sha(log),
              'jfr': str(jfr.relative_to(ROOT)), 'jfr_sha256': sha(jfr), 'jfr_settings_sha256': sha(JFC),
              'phase_configs': configs}
    if smoke:
        attempt['record'] = record
        attempt['status'] = 'passed-smoke'
    else:
        record_path.parent.mkdir(parents=True, exist_ok=True)
        record_path.write_text(json.dumps(record, indent=2) + '\n')
        (record_path.parent / (label + '.summary.json')).write_text(json.dumps(summary, indent=2) + '\n')
        attempt['status'] = 'passed'
    attempt_path.write_text(json.dumps(attempt, indent=2) + '\n')
    print('Passed:', label, flush=True)


def jobs_for(group):
    if group == 'smoke':
        # alloc28-smoke-d200 was rejected (tooling edited during it) and showed too few client samples; retained.
        return [('alloc28-smoke-d200', 'd200', True), ('alloc28-smoke-d200-2', 'd200', True),
                ('alloc28-smoke-terrain', 'terrain', True)]
    order = ('d0', 'd200', 'terrain')
    # The second repetition reverses the scene order.
    return [(f'alloc28-{scene}-r{n}', scene, False) for n in (1, 2) for scene in (order if n == 1 else order[::-1])]


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('group', choices=('smoke', 'profiles', 'freeze'))
    parser.add_argument('--only')
    parser.add_argument('--jdk', type=Path)
    parser.add_argument('--init', type=Path)
    opts = parser.parse_args()
    if opts.group == 'freeze':
        path = ARCHIVE / 'freeze.json'
        assert not path.exists(), 'already frozen'
        head, dirty = tree_state()
        assert dirty == '', 'commit the protocol and tooling first:\n' + dirty
        freeze = {'commit': head, 'frozen_at': time.strftime('%Y-%m-%dT%H:%M:%S%z'),
                  'frozen_paths': FROZEN_PATHS, 'base_world_sha256': world_hashes()}
        path.write_text(json.dumps(freeze, indent=2) + '\n')
        print('Frozen at', head)
        raise SystemExit(0)
    assert opts.jdk and opts.init, '--jdk and --init are required'
    jobs = jobs_for(opts.group)
    if opts.only:
        assert opts.only in {job[0] for job in jobs}
    for label, scene, smoke in jobs:
        if not opts.only or opts.only == label:
            run(label, scene, smoke, opts.jdk.resolve(), opts.init.resolve())
