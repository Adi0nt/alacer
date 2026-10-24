#!/usr/bin/env python3
"""Windows Java 25 scheduling series. Clients run serially; plan never launches a game."""
import argparse
import datetime as dt
import hashlib
import json
import math
import os
from pathlib import Path
import re
import statistics
import subprocess
import time
import traceback
import zipfile
import aa_platform

ROOT = Path(__file__).resolve().parents[1]
PROTOCOL = ROOT / 'docs/benchmarks/2026-10-24-chunk-scheduling-reference/protocol.md'
DEFAULT_JDK = Path('C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot')
BENCH = ROOT / 'run/alacer/benchmarks'
BASE = ROOT / 'run/saves/alacer-benchmark'
ORDER = 'ABBABAAB'
OPTIONS = ('render.chunkTimeFloor', 'render.feedChunkWorkers')
PHASES = {
    'edit': {'warmup-pan': 10, 'still': 10, 'edit': 30},
    'near': {'warmup-pan': 10, 'still': 10, 'fresh-flight': 40},
    'high': {'warmup-pan': 10, 'still': 10, 'fresh-flight': 40},
    'default': {'static': 10, 'pan': 15, 'fly': 20},
    'terrain': {'warmup-pan': 15, 'warmup-sort': 16, 'steady': 10, 'pan': 15, 'sort': 16, 'fly': 20},
}
BAD = re.compile(r'InvalidInjectionException|InjectionError|MixinApplyError|Mixin apply failed|'
                 r'Exception in thread|Benchmark aborted|Benchmark timed out|GL ERROR|OpenGL Error|'
                 r'OpenGLException|verification mismatch|Chunk visibility mismatch|BUILD FAILED', re.I)


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None


def write_new(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('x', encoding='utf-8') as out:
        json.dump(value, out, indent=2, allow_nan=False)
        out.write('\n')


def hashes(folder):
    return {p.relative_to(folder).as_posix(): sha(p) for p in sorted(folder.rglob('*'))
            if p.is_file() and p.name != 'session.lock'}


def source_hashes():
    paths = [p for p in (ROOT / 'src').rglob('*') if p.is_file()]
    paths += [ROOT / p for p in ('AGENTS.md', 'build.gradle', 'settings.gradle', 'gradle.properties',
                                'gradlew.bat', 'gradle/gradle-daemon-jvm.properties',
                                'gradle/wrapper/gradle-wrapper.properties', 'gradle/wrapper/gradle-wrapper.jar')]
    paths += list((ROOT / 'tools').rglob('*.py'))
    paths += [PROTOCOL, ROOT / 'tools/reference_java25.init.gradle']
    require(all(p.is_file() for p in paths), 'missing source/protocol file')
    return {p.relative_to(ROOT).as_posix(): sha(p) for p in sorted(set(paths))}


def artifacts():
    out = {}
    for name in ('build/classes/java/main', 'build/resources/main'):
        out.update({name + '/' + p: h for p, h in hashes(ROOT / name).items()})
    require(out and any(p.endswith('.class') for p in out), 'build classes first')
    return out


def shared_hashes():
    return {p: sha(ROOT / p) for p in ('run/config/alacer.properties', 'run/options.txt')}


def clients():
    require(os.name == 'nt', 'live execution requires Windows')
    platform = aa_platform.WindowsPlatform()
    try:
        return [dict(ProcessId=p['pid'], ExecutablePath=p['exe'], CommandLine=p['cmdline'])
                for p in platform.find_clients()]
    finally:
        platform.close()


def jobs(suite):
    pairs = [('edit', OPTIONS[0]), ('near', OPTIONS[0])]
    if suite == 'full':
        pairs += [(s, OPTIONS[0]) for s in ('high', 'default', 'terrain')]
        pairs += [(s, OPTIONS[1]) for s in PHASES]
    out = []
    for scene, option in pairs:
        short = 'floor' if option == OPTIONS[0] else 'feed'
        for i, arm in enumerate(ORDER, 1):
            out.append(dict(label=f'csr-{short}-{scene}-{i}{arm.lower()}', scene=scene,
                            option=option, arm=arm, scale=1.0))
    return out


def properties(job, baseline=None):
    p = {k: v for k, v in (baseline or {}).items()
         if k.startswith(('render.', 'quality.', 'advanced.', 'profiler.'))}
    p.update({k: 'false' for k in p if k.endswith('.verify')})
    p.update({'benchmark': 'true', 'benchmark.label': job['label'], 'benchmark.exit': 'true',
              'benchmark.vbo': 'false', 'benchmark.viewDistance': '12', 'benchmark.entities': '0',
              'benchmark.chests': '0', 'benchmark.particles': 'false', 'benchmark.toggle': '',
              'benchmark.cloudSweep': 'false', 'benchmark.screenshots': 'false',
              'benchmark.chunkUploadGpu': 'false', 'benchmark.timeout': '600',
              'benchmark.timeScale': str(job['scale']), 'benchmark.backendScenario': 'false',
              'benchmark.chunkEdit': 'false', 'benchmark.chunkFlight': '',
              'profiler.enabled': 'true', 'profiler.chunkLatency': 'true',
              'profiler.chunkDetail': 'false', 'profiler.entityDetail': 'false',
              'profiler.particleDetail': 'false', 'profiler.shadowTiming': 'false',
              'profiler.frustumTiming': 'false', 'profiler.hud': 'false',
              OPTIONS[0]: 'false', OPTIONS[1]: 'false'})
    p[job['option']] = str(job['arm'] == 'B').lower()
    if job['scene'] == 'edit':
        p['benchmark.chunkEdit'] = 'true'
    elif job['scene'] in ('near', 'high'):
        p['benchmark.chunkFlight'] = job['scene']
    elif job['scene'] == 'terrain':
        p['benchmark.backendScenario'] = 'true'
    return p


def command(job, jdk, baseline=None):
    # Gradle toolchain discovery is restricted to the selected installation as well as JAVA_HOME.
    return [str(ROOT / 'gradlew.bat'), 'runClient', '--offline', '--console=plain',
            '-I', str(ROOT / 'tools/reference_java25.init.gradle'),
            '-Porg.gradle.java.installations.auto-detect=false',
            '-Porg.gradle.java.installations.paths=' + str(jdk), '-Pwindow=1920x1080', '-Pxmx=2G',
            *['-Palacer.' + k + '=' + v for k, v in properties(job, baseline).items()]]


def validate(summary, job, log, baseline=None):
    require(not BAD.search(log), 'client error in log')
    require('BUILD SUCCESSFUL' in log and 'Benchmark: results written' in log, 'incomplete launch')
    require('> Task :compileJava UP-TO-DATE' in log and '> Task :processResources UP-TO-DATE' in log,
            'compiled during client launch; prepare the build before smoke/freeze')
    e = summary['environment']
    expected = {'alacer.configSource': 'build-defaults-and-jvm-overrides',
                'benchmark.generationRun': 'false', 'window': '1920x1080',
                'options.renderDistance': '12', 'options.vsync': 'false',
                'options.fpsLimit': '260', 'options.fancyGraphics': 'true',
                'options.smoothLighting': '2', 'options.mipmapLevels': '4', 'options.clouds': '2',
                'options.useVbo': 'false', 'vboActive': 'false', 'benchmark.mobs': '0',
                'benchmark.chests': '0', 'benchmark.particles': 'false',
                'benchmark.chunkEdit': str(job['scene'] == 'edit').lower(),
                'benchmark.chunkFlight': job['scene'] if job['scene'] in ('near', 'high') else '',
                'benchmark.backendScenario': str(job['scene'] == 'terrain').lower(),
                'benchmark.chunkUploadGpu': 'false', 'profiler.allocationAvailable': 'true',
                'benchmark.toggle': '', 'benchmark.screenshots': 'false'}
    for key, value in expected.items():
        require(e.get(key) == value, f'environment {key}: {e.get(key)!r} != {value!r}')
    require(e.get('java.version', '').startswith('25.'), 'not Java 25')
    require('RTX 3060 Ti' in e.get('gl.renderer', ''), 'not reference GPU')
    if job['scene'] in ('edit', 'near', 'high'):
        require('fresh chunk-flight world copy verified byte-identical' in log, 'no fresh-copy receipt')
    require(set(summary['phases']) == set(PHASES[job['scene']]), 'phase list changed')
    configs = []
    requested = properties(job, baseline)
    for name, seconds in PHASES[job['scene']].items():
        phase = summary['phases'][name]
        require(phase['frames'] > 0 and phase['dropped_frames'] == 0, f'{name}: missing/dropped frames')
        require(phase.get('update_latency_overflow') == 0, f'{name}: missing/overflowed latency')
        require(phase['discarded_setup_frames'] == 1, f'{name}: setup frame not excluded')
        require(phase['target_seconds'] == seconds * job['scale'], f'{name}: duration changed')
        require(abs(phase['recorded_wall_seconds'] - phase['target_seconds']) < 1, f'{name}: duration missed')
        require(phase['client_thread_cpu_ms_per_frame'] > 0 and phase['process_cpu_cores'] > 0
                and phase['alloc_mean_bytes'] > 0,
                f'{name}: CPU/allocation counters unavailable')
        c = phase['config']
        for k, v in requested.items():
            if k in (baseline or {}) or k.startswith(('render.', 'profiler.')):
                require(c.get(k) == v, f'{name}: config {k}: {c.get(k)!r} != {v!r}')
        require(all(v == 'false' for k, v in c.items() if k.endswith('.verify')), 'verification in score')
        require(c.get('backend.renderChunkClass') == 'net.minecraft.client.render.world.GlListRenderChunk',
                'actual display-list backend changed')
        configs.append(c)
    require(all(c == configs[0] for c in configs), 'configuration changed across phases')
    if job['scene'] in ('edit', 'near', 'high'):
        active = summary['phases']['edit' if job['scene'] == 'edit' else 'fresh-flight']
        require(active.get('update_latency_count', 0) > 0, 'no updates published')
    if job['scene'] == 'edit' and job['scale'] == 1:
        require(e.get('benchmark.chunkEditBursts') == '28', 'edit workload changed')
    return configs[0]


def state(jdk):
    require((jdk / 'bin/java.exe').is_file(), 'selected Java installation missing')
    require(BASE.is_dir() and hashes(BASE), 'existing base world required; do not score generation')
    return dict(sources=source_hashes(), artifacts=artifacts(), world=hashes(BASE), shared=shared_hashes(),
                java_exe=sha(jdk / 'bin/java.exe'), java_release=sha(jdk / 'release'))


def run_one(archive, job, jdk, frozen=None):
    label = job['label']
    log_path = archive / 'logs' / (label + '.log')
    attempt_path = archive / 'attempts' / (label + '.json')
    require(not log_path.exists() and not attempt_path.exists(), 'attempt label already used')
    baseline = frozen['baseline'] if frozen else None
    cmd = command(job, jdk, baseline)
    attempt = dict(job=job, command=cmd, java_home=str(jdk), started_utc=dt.datetime.now(dt.UTC).isoformat())
    process = None
    launcher = None
    started = time.monotonic()
    # An exclusive reservation survives interruption; attempts and logs are never overwritten.
    write_new(attempt_path, {'status': 'reserved', **attempt})
    try:
        require(not clients(), 'another Minecraft client is running')
        before = state(jdk)
        if frozen:
            require(before == frozen['state'], 'frozen inputs changed')
            require(sha(archive / 'sources.zip') == frozen['snapshot_sha256'], 'source snapshot changed')
        require(not list(BENCH.glob('*-' + label + '*')), 'result label already used')
        log_path.parent.mkdir(parents=True, exist_ok=True)
        env = dict(os.environ, JAVA_HOME=str(jdk), PATH=str(jdk / 'bin') + os.pathsep + os.environ['PATH'])
        require(not any(env.get(k) for k in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS',
                                           'GRADLE_OPTS')), 'ambient JVM options must be cleared explicitly')
        with log_path.open('x', encoding='utf-8') as out:
            process = subprocess.Popen(cmd, cwd=ROOT, env=env, stdout=out, stderr=subprocess.STDOUT)
            while process.poll() is None:
                live = clients()
                require(len(live) <= 1, 'overlapping clients')
                if live:
                    current = live[0]
                    if launcher is None:
                        launcher = current
                        require(Path(current['ExecutablePath']).resolve() == (jdk / 'bin/java.exe').resolve(),
                                'launcher uses a different JDK')
                        require('--add-modules=jdk.incubator.vector' in current['CommandLine'], 'Vector module missing')
                        require('StartFlightRecording' not in current['CommandLine'], 'JFR in score')
                    require(current['ProcessId'] == launcher['ProcessId'], 'client process changed')
                require(time.monotonic() - started < 900, 'client timeout')
                time.sleep(1)
        require(process.returncode == 0 and launcher is not None, 'client failed or launcher not observed')
        require(not clients(), 'client left running')
        require(before == state(jdk), 'source, artifacts, base world, JDK or shared files changed')
        matches = list(BENCH.glob('*-' + label + '*'))
        require(len(matches) == 1, 'expected exactly one result folder')
        folder = matches[0]
        summary = json.loads((folder / 'summary.json').read_bytes())
        baseline = validate(summary, job, log_path.read_text(encoding='utf-8'), baseline)
        if frozen:
            for key in ('java.version', 'java.vendor', 'gl.vendor', 'gl.renderer', 'gl.version',
                        'cpu', 'window', 'options.fancyGraphics', 'options.smoothLighting',
                        'options.mipmapLevels', 'options.clouds'):
                require(summary['environment'].get(key) == frozen['environment'].get(key),
                        f'frozen environment changed: {key}')
        for phase in summary['phases']:
            phase_file = json.loads((folder / (phase + '.json')).read_bytes())
            require(phase_file['config'] == summary['phases'][phase]['config'], 'phase receipt differs')
        record = dict(status='passed', job=job, summary=summary, baseline=baseline,
                      environment=summary['environment'], state=before, launcher=launcher,
                      raw_folder=str(folder.relative_to(ROOT)), raw_sha256=hashes(folder))
        write_new(archive / 'records' / (label + '.json'), record)
        attempt['status'] = 'passed'
    except BaseException:
        attempt.update(status='failed', error=traceback.format_exc())
        if process is not None and process.poll() is None:
            # Kill only this runner's launcher tree, never a pre-existing/unrelated Java process.
            subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], capture_output=True)
            process.wait(timeout=30)
        if launcher is not None and '-Dalacer.benchmark.label=' + label in launcher['CommandLine']:
            # The long-lived Gradle daemon can parent the game outside the .bat process tree.
            for live in clients():
                if (live['ProcessId'] == launcher['ProcessId'] and
                        '-Dalacer.benchmark.label=' + label in live['CommandLine']):
                    subprocess.run(['taskkill', '/PID', str(live['ProcessId']), '/T', '/F'], capture_output=True)
        raise
    finally:
        attempt.update(elapsed_seconds=time.monotonic() - started, launcher=launcher,
                       exit_code=process.returncode if process else None, log_sha256=sha(log_path))
        write_new(archive / 'attempts' / (label + '.final.json'), attempt)


def freeze(archive, suite, jdk):
    receipts = list((archive / 'records').glob('csr-smoke-*.json'))
    require(len(receipts) == 1, 'exactly one passing smoke required')
    smoke = json.loads(receipts[0].read_bytes())
    require(smoke['status'] == 'passed' and not clients(), 'passing smoke and no running clients required')
    current = state(jdk)
    require(current == smoke['state'], 'inputs changed since smoke; use a new archive/smoke')
    planned = jobs(suite)
    prefix = hashlib.sha256(str(archive).encode()).hexdigest()[:8]
    for job in planned:
        job['label'] = prefix + '-' + job['label']
    with zipfile.ZipFile(archive / 'sources.zip', 'x', zipfile.ZIP_DEFLATED) as out:
        for name in current['sources']:
            out.write(ROOT / name, name)
    write_new(archive / 'manifest.json', dict(suite=suite, state=current, jobs=planned,
              java_home=str(jdk), baseline=smoke['baseline'], environment=smoke['environment'],
              snapshot_sha256=sha(archive / 'sources.zip'), frozen_utc=dt.datetime.now(dt.UTC).isoformat()))


def change(a, b):
    require(math.isfinite(a) and math.isfinite(b) and a >= 0 and b >= 0, 'invalid metric')
    return b / a - 1 if a else (0 if b == 0 else None)


def evaluate(archive, frozen):
    records = {}
    for job in frozen['jobs']:
        r = json.loads((archive / 'records' / (job['label'] + '.json')).read_bytes())
        require(r['status'] == 'passed' and r['job'] == job and r['state'] == frozen['state'], 'invalid record')
        require(hashes(ROOT / r['raw_folder']) == r['raw_sha256'], 'raw results changed')
        require(json.loads((ROOT / r['raw_folder'] / 'summary.json').read_bytes()) == r['summary'],
                'recorded summary differs from raw summary')
        records[job['label']] = r['summary']
    metrics = {}
    for scene, option in dict.fromkeys((j['scene'], j['option']) for j in frozen['jobs']):
        group = [j for j in frozen['jobs'] if j['scene'] == scene and j['option'] == option]
        for phase in PHASES[scene]:
            if phase.startswith('warmup'):
                continue
            for metric in ('frame_mean_ms', 'frame_p99_ms', 'frame_slow1_mean_ms',
                           'client_thread_cpu_ms_per_frame', 'chunk_visible_uncompiled_mean',
                           'update_latency_p90_ms'):
                deltas = []
                for i in range(0, 8, 2):
                    pair = {j['arm']: records[j['label']]['phases'][phase] for j in group[i:i+2]}
                    vals = [1000 / pair[arm]['avg_fps'] if metric == 'frame_mean_ms'
                            else 1000 / pair[arm]['one_percent_low_fps'] if metric == 'frame_slow1_mean_ms'
                            else pair[arm][metric] for arm in 'AB']
                    deltas.append(change(*vals))
                median = statistics.median(deltas) if all(v is not None for v in deltas) else None
                metrics[f'{option}/{scene}/{phase}/{metric}'] = dict(changes=deltas, median=median)
    gates = {}
    def metric(option, scene, phase, name):
        return metrics[f'{option}/{scene}/{phase}/{name}']
    def below(m, threshold):
        return m['median'] is not None and m['median'] <= threshold
    for option in dict.fromkeys(j['option'] for j in frozen['jobs']):
        for scene in dict.fromkeys(j['scene'] for j in frozen['jobs'] if j['option'] == option):
            active = 'edit' if scene == 'edit' else 'fresh-flight'
            if scene in ('edit', 'near', 'high'):
                latency = metric(option, scene, active, 'update_latency_p90_ms')
                needed, limit = (4, -0.25) if scene == 'edit' else (3, -0.10)
                gates[f'{option}/{scene}/responsiveness'] = (below(latency, limit) and
                    sum(v is not None and v < 0 for v in latency['changes']) >= needed)
                gates[f'{option}/{scene}/holes'] = below(metric(option, scene, active, 'chunk_visible_uncompiled_mean'), 0.05)
                still = metric(option, scene, 'still', 'frame_mean_ms')['median']
                gates[f'{option}/{scene}/still'] = still is not None and abs(still) <= 0.05
            for phase in PHASES[scene]:
                if phase.startswith('warmup') or phase == 'still':
                    continue
                for name, limit in (('frame_mean_ms', 0.01), ('frame_p99_ms', 0.02),
                                    ('frame_slow1_mean_ms', 0.02), ('client_thread_cpu_ms_per_frame', 0.0)):
                    gates[f'{option}/{scene}/{phase}/{name}'] = below(metric(option, scene, phase, name), limit)
    return dict(completed=len(records), metrics=metrics, gates=gates, passed=all(gates.values()),
                selection='Not promoted. Core lacks supplementary controls; full still requires separate visual gates.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('plan', 'smoke', 'freeze', 'measure', 'evaluate'))
    parser.add_argument('--archive', type=Path)
    parser.add_argument('--suite', choices=('core', 'full'), default='core')
    parser.add_argument('--jdk', type=Path, default=DEFAULT_JDK)
    args = parser.parse_args()
    if args.mode == 'plan':
        print(json.dumps(jobs(args.suite), indent=2))
        return
    require(args.archive is not None, '--archive is required')
    archive = args.archive.resolve()
    require(archive != PROTOCOL.parent and archive != ROOT, 'use a new, dedicated result archive')
    if args.mode == 'smoke':
        require(not archive.exists(), 'smoke requires a new archive')
        prefix = hashlib.sha256(str(archive).encode()).hexdigest()[:8]
        run_one(archive, dict(label='csr-smoke-' + prefix, scene='near', option=OPTIONS[0], arm='A', scale=0.1), args.jdk)
    elif args.mode == 'freeze':
        freeze(archive, args.suite, args.jdk)
    else:
        frozen = json.loads((archive / 'manifest.json').read_bytes())
        if args.mode == 'measure':
            require(state(args.jdk) == frozen['state'], 'frozen inputs changed')
            for job in frozen['jobs']:
                print('Starting', job['label'], flush=True)
                run_one(archive, job, args.jdk, frozen)
        else:
            result = evaluate(archive, frozen)
            write_new(archive / 'evaluation.json', result)
            print(json.dumps(result, indent=2, allow_nan=False))


if __name__ == '__main__':
    main()
