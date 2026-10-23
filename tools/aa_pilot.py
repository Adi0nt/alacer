#!/usr/bin/env python3
"""Session 31: automated A/A benchmark pilot with environmental monitoring (protocol: the archive's README.md).

Both sides of every pair are the same build, JVM, options and machine settings; only the label differs. Nothing is
toggled to manufacture a difference. Stages are separate commands, run in this order:

  python tools/aa_pilot.py plan       --protocol P      print the schedule, labels and expected time (no launches)
  python tools/aa_pilot.py smoke      --protocol P      one launch per pilot scene; shapes the tooling; not evidence
  python tools/aa_pilot.py freeze     --protocol P      hash manifest + sources ZIP; refuses if already frozen
  python tools/aa_pilot.py calibrate  --protocol P      calibration launches -> calibration.json (written once)
  python tools/aa_pilot.py pilot      --protocol P [--max-pairs N]   the A/A pairs; resumable
  python tools/aa_pilot.py overhead   --protocol P      optional, exploratory: monitor on/off launches
  python tools/aa_pilot.py evaluate   --protocol P      pilot.json + pilot.md from the archive only
  python tools/aa_pilot.py status     --protocol P      progress, from the attempt files

Every attempt (accepted, rejected or interrupted) keeps its file under attempts/. Labels, logs, result folders and
records are never reused or overwritten; a rejected pair is repeated whole under new labels (-r2, -r3, ...). After an
interruption, `pilot` resumes: decided pairs are kept, an unfinished pair is rejected and rescheduled, never completed
or reused. On Linux (a development container) the client runs under xvfb-run with software GL; such launches judge
nothing and the protocol for them must say so.
"""
import argparse
import datetime
import json
import math
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import time
import traceback

sys.path.insert(0, str(Path(__file__).resolve().parent))
import aa_monitor as mon  # noqa: E402
import aa_platform as plat  # noqa: E402
import aa_rules as rules  # noqa: E402
import run_allocation_selection as s28  # noqa: E402  (option lists and log failure markers, unchanged)
import run_particle_movement as movement  # noqa: E402  (sha, sources and the sources ZIP, unchanged)

ROOT = Path(__file__).resolve().parent.parent
BENCH = ROOT / 'run/alacer/benchmarks'
RAW = ROOT / 'run/alacer/stability'
LATER_OFF = ('reuseShadowPositions', 'fastCameraSectionFaces')  # Experiments added after session 28's OFF list.
BAD = s28.BAD + ('Chunk render task was', 'OpenGL Error', 'Chunk GPU context changed')
TOOLING = ('tools/aa_pilot.py', 'tools/aa_rules.py', 'tools/aa_monitor.py', 'tools/aa_platform.py', 'tools/aa_stats.py',
           'tools/aa_report.py', 'tools/aa_pilot_windows.bat', 'tools/aa_foreground_windows.mjs',
           'tools/run_allocation_selection.py', 'tools/run_particle_movement.py', 'settings.gradle',
           'gradle/gradle-daemon-jvm.properties', 'gradle/wrapper/gradle-wrapper.properties')


class CheckFailed(Exception):
    """A runner check (harness, configuration or correctness) failed: the launch is not a valid measurement."""


def check(condition, message):
    if not condition:
        raise CheckFailed(message)


def write_json(path, value, exclusive=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    data = (json.dumps(value, indent=1, default=str) + '\n').encode()
    if exclusive:
        with open(path, 'xb') as f:
            f.write(data)
    else:
        tmp = path.with_suffix(path.suffix + '.tmp')
        tmp.write_bytes(data)
        for attempt in range(6):
            try:
                os.replace(tmp, path)
                break
            except PermissionError:
                if not plat.WINDOWS or attempt == 5:
                    raise  # Never report a successful write when the target stayed unchanged.
                time.sleep(0.05 * (attempt + 1))  # Windows readers/scanners can briefly deny replacement.


def now_utc():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


# ---------------------------------------------------------------------------------------------------------------

def load_protocol(path):
    protocol = json.loads(Path(path).read_text(encoding='utf-8'))
    for key in ('series', 'archive', 'launch', 'stability', 'scenes', 'calibration', 'pilot', 'monitor', 'affinity',
                'limits', 'gate', 'power', 'validity', 'expect'):
        if key not in protocol:
            raise SystemExit(f'protocol {path} lacks "{key}"')
    rules.check_label(protocol['series'])
    for scene in protocol['pilot']['pairs']:
        rules.expected_phases(protocol, scene)
    return protocol


class Archive:
    """The series' evidence folder. Refuses to write into any folder that holds a different protocol, which also
    protects every historical archive (none of them has this series' protocol.json)."""

    def __init__(self, protocol, protocol_path):
        self.root = (ROOT / protocol['archive']).resolve()
        if not self.root.is_relative_to(ROOT):
            raise SystemExit('archive must be inside the checkout')
        self.protocol_file = self.root / 'protocol.json'
        source = Path(protocol_path).resolve()
        if self.root.exists() and any(self.root.iterdir()):
            if not self.protocol_file.exists():
                raise SystemExit(f'{self.root} exists without protocol.json: refusing to write into it')
            if source != self.protocol_file and self.protocol_file.read_bytes() != source.read_bytes():
                raise SystemExit(f'{self.protocol_file} differs from {source}: a frozen protocol is never replaced')
        self.attempts = self.root / 'attempts'
        self.pairs = self.root / 'pairs'
        self.records = self.root / 'records'
        self.manifest = self.root / 'manifest.json'
        self.calibration = self.root / 'calibration.json'
        self.lock = self.root / 'runner.lock'
        self.raw = RAW / protocol['series']
        self.source = source

    def ensure(self):
        self.root.mkdir(parents=True, exist_ok=True)
        if not self.protocol_file.exists():
            self.protocol_file.write_bytes(self.source.read_bytes())

    def load(self, folder):
        if not folder.exists():
            return {}
        return {p.stem: json.loads(p.read_text(encoding='utf-8')) for p in sorted(folder.glob('*.json'))
                if not p.name.endswith('.summary.json')}


# ---------------------------------------------------------------------------------------------------------------

def explicit_props(protocol, scene, label):
    """Every render, verification, profiler and benchmark option explicit, as sessions 28-30 launched."""
    launch = protocol['launch']
    flight = scene in rules.FLIGHTS
    base = 'd0' if flight else scene
    props = {'benchmark': 'true', 'benchmark.label': label, 'benchmark.vbo': launch['vbo'], 'benchmark.chests': '0',
             'benchmark.viewDistance': '12', 'benchmark.timeout': str(launch['timeout_s']), 'benchmark.exit': 'true',
             'benchmark.timeScale': launch['time_scale_flight'] if flight else launch['time_scale'],
             'profiler.enabled': 'true'}
    props.update(s28.SCENES[base]['props'])
    props.update({'render.' + k: 'true' for k in s28.ON})
    props.update({'render.' + k: 'false' for k in s28.OFF + LATER_OFF})
    props.update({'render.' + k + '.verify': 'false' for k in s28.ON + s28.OFF + LATER_OFF})
    props.update({'profiler.' + k: 'false' for k in s28.PROFILER_OFF})
    props.update({'benchmark.' + k: 'false' for k in s28.BENCH_OFF})
    props['benchmark.screenshotEndPose'] = 'false'
    if flight:
        props['benchmark.chunkFlight'] = scene
    stab = protocol['stability']
    spec = protocol['scenes'][scene]
    if stab.get('reference'):
        props['benchmark.reference'] = str(stab['reference'])
        props['benchmark.referencePost'] = 'true' if stab.get('reference_post') else 'false'
    if spec.get('phase_seconds'):
        props['benchmark.phaseSeconds'] = ','.join(f'{k}:{v}' for k, v in spec['phase_seconds'].items())
    if spec.get('repeat_segments'):
        props['benchmark.repeatSegments'] = str(spec['repeat_segments'])
        props['benchmark.repeatSegmentSeconds'] = str(spec['repeat_segment_seconds'])
    props['benchmark.processorSampling'] = 'true' if stab.get('processor_sampling') else 'false'
    props['benchmark.mobPoseCheck'] = 'true' if stab.get('mob_pose_check') else 'false'
    props['benchmark.freezeMobs'] = 'true' if stab.get('freeze_mobs') else 'false'
    return props


def props_identity(props):
    return rules.canonical_sha({k: v for k, v in props.items() if k != 'benchmark.label'})


def launch_command(protocol, props, jdk, init):
    launch = protocol['launch']
    args = ['-Pwindow=' + launch['window'], '-Pxmx=' + launch['xmx']] + ['-Palacer.' + k + '=' + v for k, v in props.items()]
    if plat.WINDOWS:
        return [str(ROOT / 'gradlew.bat'), 'runClient', '--offline', '--console=plain'] + args
    command = ['xvfb-run', '-a', '-s', '-screen 0 1280x720x24 +extension GLX +extension RANDR', 'sh', './gradlew',
               'runClient', '--console=plain', '-Porg.gradle.java.installations.paths=' + str(jdk)]
    if init:
        command += ['-I', str(init)]
    return command + args


def java_executable(jdk):
    return (Path(jdk) / 'bin' / ('java.exe' if plat.WINDOWS else 'java')).resolve()


def jdk_hashes(jdk):
    jdk = Path(jdk)
    files = [java_executable(jdk), jdk / 'release']
    files += [p for p in (jdk / 'bin/server/jvm.dll', jdk / 'lib/server/libjvm.so') if p.exists()]
    return {str(p.relative_to(jdk)).replace('\\', '/'): movement.sha(p) for p in files}


def tooling_hashes(archive):
    hashes = movement.sources()
    for name in TOOLING:
        hashes[name] = movement.sha(ROOT / name)
    for name in ('protocol.json', 'README.md'):
        path = archive.root / name
        if path.exists():
            hashes[str(path.relative_to(ROOT)).replace('\\', '/')] = movement.sha(path)
    return hashes


def artifact_hashes():
    return {str(p.relative_to(ROOT)).replace('\\', '/'): movement.sha(p)
            for folder in ('build/classes/java/main', 'build/resources/main')
            for p in sorted((ROOT / folder).rglob('*')) if p.is_file()}


def world_hashes():
    base = ROOT / 'run/saves/alacer-benchmark'
    return {str(p.relative_to(base)).replace('\\', '/'): movement.sha(p) for p in sorted(base.rglob('*')) if p.is_file()}


def kill_tree(process, client_pid):
    try:
        if plat.WINDOWS:
            for pid in (process.pid if process else None, client_pid):
                if pid:
                    subprocess.run(['taskkill', '/PID', str(pid), '/T', '/F'], capture_output=True)
        else:
            if process and process.poll() is None:
                os.killpg(process.pid, signal.SIGKILL)
            if client_pid:
                try:
                    os.kill(client_pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
        if process:
            process.wait(timeout=30)
    except Exception:  # noqa: BLE001 - best effort; the attempt already records the failure
        pass


def bin_series(series, seconds=5.0):
    """Mean of each numeric field per `seconds` bin (the archive's compact monitoring time series)."""
    out, current, start = [], [], None
    for row in series:
        if start is None or row['t'] - start >= seconds:
            if current:
                out.append(_bin(current))
            current, start = [], row['t']
        current.append(row)
    if current:
        out.append(_bin(current))
    return out


def _bin(rows):
    out = {'t': rows[0]['t']}
    for k in rows[0]:
        if k == 't':
            continue
        vals = [r[k] for r in rows if isinstance(r.get(k), (int, float)) and not isinstance(r.get(k), bool)]
        flags = [r[k] for r in rows if isinstance(r.get(k), bool)]
        out[k] = round(sum(vals) / len(vals), 4) if vals else (all(flags) if flags else None)
    return out


# ---------------------------------------------------------------------------------------------------------------

class Runner:
    def __init__(self, protocol, archive, jdk, init=None, platform=None):
        self.protocol = protocol
        self.archive = archive
        self.jdk = Path(jdk)
        self.init = init
        self.platform = platform or plat.current()
        self.manifest = json.loads(archive.manifest.read_text()) if archive.manifest.exists() else None

    # -- locking -------------------------------------------------------------------------------------------
    def acquire(self):
        self.archive.ensure()
        if self.archive.lock.exists():
            held = json.loads(self.archive.lock.read_text())
            alive = any(p['pid'] == held['pid'] for p in self.platform.processes().values())
            if alive and held['pid'] != os.getpid():
                raise SystemExit(f'another runner (pid {held["pid"]}) holds {self.archive.lock}')
            print(f'Removing stale lock from pid {held["pid"]} ({held["started_utc"]})', flush=True)
        write_json(self.archive.lock, {'pid': os.getpid(), 'started_utc': now_utc()})
        awake = self.platform.keep_awake()
        print('Keep awake:', awake['status'], awake.get('reason', ''), flush=True)

    def release(self):
        try:
            self.archive.lock.unlink()
        except FileNotFoundError:
            pass

    # -- manifest ------------------------------------------------------------------------------------------
    def state_hashes(self):
        return {'source_sha256': tooling_hashes(self.archive), 'runtime_artifacts_sha256': artifact_hashes(),
                'base_world_sha256': world_hashes(),
                'shared_config_sha256': movement.sha(ROOT / 'run/config/alacer.properties'),
                'jdk_sha256': jdk_hashes(self.jdk)}

    def check_manifest(self):
        current = self.state_hashes()
        for key, value in current.items():
            if value != self.manifest[key]:
                changed = sorted(k for k in set(value) | set(self.manifest[key]) if value.get(k) != self.manifest[key].get(k)) \
                    if isinstance(value, dict) else [key]
                raise CheckFailed(f'{key} differs from the frozen manifest: {changed[:6]}')
        snap = self.manifest['source_snapshot']
        check(movement.sha(ROOT / snap['path']) == snap['sha256'], 'sources ZIP changed')

    def freeze(self):
        archive = self.archive
        check(not archive.manifest.exists(), 'already frozen')
        check(self.platform.find_clients() == [], 'client running during freeze')
        attempts = archive.load(archive.attempts)
        for scene in self.protocol['pilot']['pairs']:
            check(any(a['status'] == 'passed-smoke' and a['scene'] == scene
                      and a.get('environment_rejections') == [] for a in attempts.values()),
                  f'a passing smoke launch of {scene} must precede the freeze')
        state = self.state_hashes()
        check(all(state['source_sha256'].values()), 'missing sources or tooling')
        check(state['runtime_artifacts_sha256'], 'no compiled artifacts: build first')
        check(state['base_world_sha256'], 'no base world')
        movement.ARCHIVE = archive.root
        snapshot = movement.snapshot(state['source_sha256'])
        manifest = dict(state, series=self.protocol['series'], frozen_at_utc=now_utc(), source_snapshot=snapshot,
                        protocol_sha256=movement.sha(archive.protocol_file), java_home=str(self.jdk),
                        platform=self.platform.static_info(), topology=self.platform.topology())
        write_json(archive.manifest, manifest, exclusive=True)
        self.manifest = manifest
        print('Frozen:', snapshot, flush=True)

    # -- one launch ----------------------------------------------------------------------------------------
    def expected_affinity(self, topology):
        policy = self.protocol['affinity']['policy']
        if policy == 'none':
            return None
        if policy != 'p-cores':
            raise SystemExit(f'unknown affinity policy {policy}')
        value = topology.get('value') if topology.get('status') == 'ok' else None
        check(value is not None, f'P-core policy: topology unavailable ({topology.get("reason")})')
        check(value['hybrid'], 'P-core policy: the operating system reports no distinct core classes')
        check(not value['multi_group'], 'P-core policy: more than one processor group is not supported')
        return value['performance_processors']

    def launch(self, label, stage, scene, meta=None, calibration=None, monitored=True):
        archive = self.archive
        rules.check_label(label)
        attempt_path = archive.attempts / (label + '.json')
        log = ROOT / 'build' / (label + '.log')
        raw = archive.raw / (label + '.jsonl')
        for path in (attempt_path, archive.records / (label + '.json'), log, raw):
            if path.exists():
                raise SystemExit(f'refusing to reuse label {label}: {path} exists')
        if list(BENCH.glob(f'*-{label}-vis_*')):
            raise SystemExit(f'refusing to reuse label {label}: a result folder exists')
        props = explicit_props(self.protocol, scene, label)
        attempt = {'label': label, 'stage': stage, 'scene': scene, **(meta or {}), 'status': 'preflight',
                   'started_utc': now_utc(), 'started_epoch': time.time(), 'props': props,
                   'props_identity': props_identity(props), 'frozen': self.manifest is not None,
                   'protocol_sha256': movement.sha(archive.protocol_file), 'reasons': [], 'flags': []}
        write_json(attempt_path, attempt, exclusive=True)
        raw.parent.mkdir(parents=True, exist_ok=True)
        log.parent.mkdir(parents=True, exist_ok=True)
        process = monitor = launcher = None
        client_pid = None
        started = time.monotonic()
        try:
            check(self.platform.find_clients() == [], 'another Minecraft client is running')
            if stage != 'smoke':
                check(self.manifest is not None, 'not frozen')
                self.check_manifest()
            before = self.state_hashes()
            topology = self.platform.topology()
            hardware = {'static': self.platform.static_info(), 'topology': topology}
            expected_affinity = self.expected_affinity(topology)
            gpu = plat.NvidiaSmi() if monitored and self.protocol['monitor'].get('gpu') else None
            if gpu is not None:
                hardware['gpu'] = gpu.static()
            interval = self.protocol['monitor']['interval_s']
            if monitored:
                monitor = mon.Monitor(self.platform, raw, interval=interval, top_n=self.protocol['monitor']['top_n'],
                                      gpu=gpu, topology=topology)
                monitor.start()
                quiet_started = time.monotonic()
                try:
                    attempt['quiet_wait_s'] = self.wait_quiet(monitor)
                except CheckFailed:
                    attempt['quiet_wait_s'] = time.monotonic() - quiet_started
                    raise
            command = launch_command(self.protocol, props, self.jdk, self.init)
            attempt.update(status='running', command=command, hardware_sha=rules.canonical_sha(hardware),
                           launch_epoch=time.time())
            write_json(attempt_path, attempt)
            if monitor:
                monitor.mark('launch', label=label)
            print(f'{datetime.datetime.now():%H:%M:%S} Starting {label}', flush=True)
            with log.open('x', encoding='utf-8') as out:
                process = subprocess.Popen(command, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT,
                                           env=dict(os.environ, JAVA_HOME=str(self.jdk)),
                                           start_new_session=not plat.WINDOWS)
                limit = float(self.protocol['launch']['timeout_s']) + 600
                while process.poll() is None:
                    found = monitor.client_pid if monitor else None
                    if found is None and not monitor:
                        clients = self.platform.find_clients()
                        found = clients[0]['pid'] if len(clients) == 1 else None
                        check(len(clients) <= 1, 'overlapping clients')
                    if found and launcher is None:
                        client_pid = found
                        launcher = self.verify_launcher(found, props)
                        attempt['launcher'] = launcher
                        if expected_affinity is not None:
                            self.platform.set_affinity(found, expected_affinity)
                            applied = self.platform.affinity(found)
                            check(applied['status'] == 'ok' and applied['value'] == expected_affinity,
                                  f'P-core affinity not applied: {applied}')
                            launcher['affinity_applied_after_s'] = time.monotonic() - started
                            if monitor:
                                monitor.mark('affinity', processors=expected_affinity)
                        write_json(attempt_path, attempt)
                    if monitor and monitor.extra_clients:
                        raise CheckFailed(f'overlapping Minecraft client(s): {monitor.extra_clients[:3]}')
                    check(time.monotonic() - started < limit, 'launch timed out')
                    time.sleep(0.25)
            check(process.returncode == 0, f'client/gradle exited with {process.returncode}')
            check(launcher is not None, 'client launcher never observed')
            if monitor:
                monitor.mark('exit')
                time.sleep(interval)
                monitor.stop()
            check(self.platform.find_clients() == [], 'client left running')
            after = self.state_hashes()
            for key in before:
                check(before[key] == after[key], f'{key} changed during the launch')
            if stage != 'smoke':
                self.check_manifest()
            record = self.build_record(label, stage, scene, props, attempt, launcher, monitor, hardware,
                                       expected_affinity, log)
            if monitor is not None:
                reasons, flags = rules.validate_environment(self.protocol, record,
                                                            calibration if stage == 'pilot' else None)
            else:
                reasons, flags = [], ['monitoring off (overhead stage)']
            record['flags'] = flags
            if stage == 'smoke':
                attempt['environment_rejections'] = reasons
                flags = flags + [f'(smoke) would reject: {r}' for r in reasons]
                reasons = []
            write_json(archive.records / (label + '.json'), record, exclusive=True)
            attempt.update(status=('passed-smoke' if stage == 'smoke' else 'passed') if not reasons else 'rejected-env',
                           reasons=reasons, flags=flags, record='records/' + label + '.json',
                           config_sha=record['config_sha'])
            print(f'{datetime.datetime.now():%H:%M:%S} {attempt["status"]}: {label}' +
                  ''.join('\n  reject: ' + r for r in reasons) + ''.join('\n  flag: ' + f for f in flags[:10]), flush=True)
        except CheckFailed as e:
            attempt.update(status='failed-checks', reasons=[str(e)])
            print(f'Failed checks: {label}: {e}', flush=True)
        except KeyboardInterrupt:
            attempt.update(status='interrupted', reasons=['interrupted by the operator (KeyboardInterrupt)'])
            raise
        except Exception as e:  # noqa: BLE001 - recorded as an error attempt
            attempt.update(status='error', reasons=[f'{type(e).__name__}: {e}'], error=traceback.format_exc())
            print(f'Error: {label}: {e}', flush=True)
        finally:
            if process is not None and process.poll() is None:
                kill_tree(process, client_pid)
            if monitor is not None:
                monitor.stop()
                attempt['monitor_errors'] = monitor.errors[:20]
            attempt.update(ended_utc=now_utc(), ended_epoch=time.time(), elapsed_seconds=time.monotonic() - started,
                           exit_code=process.returncode if process else None)
            for name, path in (('log', log), ('monitor_raw', raw)):
                if path.exists():
                    attempt[name] = {'path': str(path.relative_to(ROOT)).replace('\\', '/'), 'sha256': movement.sha(path)}
            write_json(attempt_path, attempt)
        return attempt

    def wait_quiet(self, monitor):
        cfg = self.protocol['monitor']
        interval = cfg['interval_s']
        check(interval > 0 and cfg['pre_launch_quiet_s'] > 0 and cfg['quiet_wait_max_s'] > 0,
              'quiet-window durations must be positive')
        need = math.ceil(cfg['pre_launch_quiet_s'] / interval)
        start = time.monotonic()
        wall_start = time.time()
        while True:
            time.sleep(interval)
            elapsed = time.monotonic() - start
            if elapsed >= cfg['quiet_wait_max_s']:
                monitor.mark('quiet-wait-expired', waited_s=elapsed)
                raise CheckFailed('pre-launch quiet window expired; launch rejected (no client started)')
            recent = list(monitor.samples[-need:])
            if len(recent) < need:
                continue
            stamps = [s.get('t', 0) for s in recent]
            now = time.time()
            if (stamps[0] < wall_start or not 0 <= now - stamps[-1] <= 1.5 * interval
                    or any(not 0 < b - a <= 1.5 * interval for a, b in zip(stamps, stamps[1:]))):
                continue  # A stalled monitor or a telemetry gap is not a quiet window.
            cpu = [s.get('cpu', {}) for s in recent]
            if any(r.get('status') != 'ok' or not isinstance(r.get('value'), dict) for r in cpu):
                continue
            values = [r['value'].get('background_cores') for r in cpu]
            if any(not isinstance(v, (int, float)) or not math.isfinite(v) or v < 0 for v in values):
                continue
            if elapsed >= cfg['pre_launch_quiet_s'] and sum(values) / len(values) <= self.protocol['limits']['background_cores_mean']:
                monitor.mark('quiet-window-passed', waited_s=elapsed, samples=need)
                return elapsed

    def verify_launcher(self, pid, props):
        exe = self.platform.executable(pid)
        cmd = self.platform.command_line(pid)
        check(exe is not None and cmd is not None, f'cannot read the client process {pid}')
        check(Path(exe).resolve() == java_executable(self.jdk), f'client runs {exe}, not {java_executable(self.jdk)}')
        check('-Xmx' + self.protocol['launch']['xmx'] in cmd, 'heap flag missing')
        for bad in ('StartFlightRecording', 'DebugNonSafepoints', 'TLABSize'):
            check(bad not in cmd, f'unexpected JVM flag {bad}')
        for k, v in props.items():
            check(f'-Dalacer.{k}={v}' in cmd, f'missing launcher flag {k}')
        return {'pid': pid, 'exe': exe, 'cmdline': cmd}

    # -- record --------------------------------------------------------------------------------------------
    def build_record(self, label, stage, scene, props, attempt, launcher, monitor, hardware, expected_affinity, log):
        folders = list(BENCH.glob(f'*-{label}-vis_*'))
        check(len(folders) == 1, f'expected one result folder, found {len(folders)}')
        folder = folders[0]
        summary = json.loads((folder / 'summary.json').read_text(encoding='utf-8'))
        env = summary['environment']
        expect = self.protocol['expect']
        phases = rules.expected_phases(self.protocol, scene)
        text = log.read_text(encoding='utf-8', errors='replace')
        self.check_log(text, scene)
        check(env.get('java.runtime.version', '').startswith(expect['java_runtime_prefix']),
              f'java runtime {env.get("java.runtime.version")}')
        check(env.get('java.vendor') == expect['java_vendor'], f'java vendor {env.get("java.vendor")}')
        check(expect['renderer_contains'] in env.get('gl.renderer', ''), f'renderer {env.get("gl.renderer")}')
        mobs = s28.SCENES['d0' if scene in rules.FLIGHTS else scene]['props']['benchmark.entities']
        expected_env = {'window': self.protocol['launch']['window'], 'vboActive': self.protocol['launch']['vbo'],
                        'alacer.configSource': 'build-defaults-and-jvm-overrides', 'benchmark.generationRun': 'false',
                        'options.renderDistance': '12', 'options.vsync': 'false', 'benchmark.mobs': mobs,
                        'benchmark.chunkFlight': scene if scene in rules.FLIGHTS else '', 'benchmark.toggle': '',
                        'benchmark.processorSampling': props['benchmark.processorSampling'],
                        'benchmark.mobPoseCheck': props['benchmark.mobPoseCheck'],
                        'benchmark.freezeMobs': props['benchmark.freezeMobs'],
                        'profiler.allocationAvailable': 'true'}
        for k, v in expected_env.items():
            check(env.get(k) == v, f'environment {k}={env.get(k)!r}, expected {v!r}')
        if props['benchmark.processorSampling'] == 'true':
            source = env.get('benchmark.processorSource', '')
            check(not source.startswith('unavailable') or 'processor_sampling' not in self.protocol['monitor']['critical_sources'],
                  f'processor sampling unavailable: {source}')
        check(list(summary['phases']) == list(phases), f'phases {list(summary["phases"])} != {list(phases)}')
        epoch = int(env['profiler.epochWallMillis'])
        metrics, configs, processors, poses = {}, {}, {}, {}
        topo_value = hardware['topology'].get('value') if hardware['topology'].get('status') == 'ok' else None
        for name, seconds in phases.items():
            p = summary['phases'][name]
            check(p['frames'] > 0 and p['dropped_frames'] == 0 and p['discarded_setup_frames'] == 1,
                  f'{name}: frames {p["frames"]}, dropped {p["dropped_frames"]}, discarded {p["discarded_setup_frames"]}')
            check(abs(p['target_seconds'] - seconds) < 1e-6, f'{name}: {p["target_seconds"]} s, expected {seconds}')
            check(p['client_thread_cpu_ms_per_frame'] > 0 and p['process_cpu_cores'] > 0, f'{name}: CPU time missing')
            config = p['config']
            for k in s28.ON:
                check(config['render.' + k] == 'true', f'{name}: render.{k} not on')
            for k in s28.OFF + LATER_OFF:
                check(config['render.' + k] == 'false', f'{name}: render.{k} not off')
            for k, v in config.items():
                check(not (k.endswith('.verify') and v != 'false'), f'{name}: {k} on')
            configs[name] = config
            metrics[name] = rules.phase_metrics(rules.read_rows(folder / (name + '.csv')), p, epoch)
            processors[name] = rules.processor_split(p, topo_value, expected_affinity)
            poses[name] = p.get('mob_pose')
        first = next(iter(configs.values()))
        check(all(c == first for c in configs.values()), 'phase configurations differ within the launch')
        monitor_out = None
        series = None
        if monitor is not None:
            samples, gpu = monitor.samples, monitor.gpu_samples()
            interval = self.protocol['monitor']['interval_s']
            first_phase, last_phase = next(iter(metrics.values())), list(metrics.values())[-1]
            launch_mark = next((m['t'] for m in monitor.marks if m['event'] == 'launch'), samples[0]['t'] if samples else 0)
            monitor_out = {
                'phases': {n: mon.summarize(samples, gpu, m['wall_start'], m['wall_end'], interval) for n, m in metrics.items()},
                'launch': mon.summarize(samples, gpu, first_phase['wall_start'], last_phase['wall_end'], interval),
                'pre_launch': mon.summarize(samples, gpu, launch_mark - self.protocol['monitor']['pre_launch_quiet_s'],
                                            launch_mark, interval),
                'marks': monitor.marks, 'errors': monitor.errors[:20], 'gpu_status': monitor.gpu.status if monitor.gpu else None,
            }
            series = bin_series(mon.downsample(samples))
        return {
            'label': label, 'stage': stage, 'scene': scene, 'pair_id': attempt.get('pair_id'),
            'started_epoch': attempt['started_epoch'], 'launch_epoch': attempt.get('launch_epoch'),
            'ended_epoch': time.time(), 'source': str(folder.relative_to(ROOT)).replace('\\', '/'),
            'files_sha256': {p.name: movement.sha(p) for p in sorted(folder.iterdir()) if p.is_file()},
            'environment': env, 'phase_configs': configs, 'config_sha': rules.canonical_sha(first),
            'props_identity': attempt['props_identity'], 'launcher': launcher, 'expected_affinity': expected_affinity,
            'phases_expected': phases, 'metrics': metrics, 'processors': processors, 'mob_pose': poses,
            'monitor': monitor_out, 'series_5s': series, 'hardware': hardware,
            'hardware_sha': attempt['hardware_sha'],
            'manifest_sha': movement.sha(self.archive.manifest) if self.archive.manifest.exists() else None,
            'artifacts_sha': rules.canonical_sha(artifact_hashes()), 'jdk_sha': rules.canonical_sha(jdk_hashes(self.jdk)),
        }

    def check_log(self, text, scene):
        check('BUILD SUCCESSFUL' in text and 'Benchmark: results written' in text, 'benchmark did not finish')
        for bad in BAD:
            check(bad not in text, f'log contains "{bad}"')
        check('> Task :compileJava UP-TO-DATE' in text and '> Task :processResources UP-TO-DATE' in text,
              'Gradle compiled during the launch (build first; nothing may change between launches)')
        for line in text.splitlines():
            if 'ismatch' in line:
                counts = re.findall(r'(\d+) mismatch', line)
                check(('final:' in line or 'verification:' in line) and counts and all(int(c) == 0 for c in counts),
                      'mismatch line: ' + line[:200])
        if scene in rules.FLIGHTS:
            check('fresh chunk-flight world copy verified byte-identical' in text, 'flight world copy not verified')

    # -- stages --------------------------------------------------------------------------------------------
    def attempts(self):
        return self.archive.load(self.archive.attempts)

    def pairs(self):
        return self.archive.load(self.archive.pairs)

    def finalize_unfinished(self):
        """Launch attempts left 'preflight'/'running' by an interruption become 'interrupted'; an undecided pair is
        decided now: rejected unless both launches passed and the pair checks hold (decided_on_resume)."""
        attempts = self.attempts()
        for label, a in attempts.items():
            if a['status'] not in rules.TERMINAL:
                a.update(status='interrupted', reasons=a.get('reasons', []) + ['found unfinished when the runner resumed'],
                         interrupted_detected_utc=now_utc())
                write_json(self.archive.attempts / (label + '.json'), a)
        attempts = self.attempts()
        state = rules.pilot_state(self.protocol, attempts, self.pairs())
        for u in state['undecided']:
            slot = next(s for s in state['slots'] if s['undecided'] is u)
            self.decide_pair(slot['scene'], slot['n'], u['k'], attempts, resumed=True)

    def decide_pair(self, scene, n, k, attempts, resumed=False):
        series = self.protocol['series']
        pid = rules.pair_id(series, scene, n, k)
        order = rules.side_order(n, self.protocol['pilot']['order'])
        labels = {s: rules.side_label(series, scene, n, s, k) for s in 'ab'}
        reasons = []
        for side in order:
            a = attempts.get(labels[side])
            if a is None:
                reasons.append(f'{labels[side]} never ran (pair incomplete)')
                break
            if a['status'] != 'passed':
                reasons.append(f'{labels[side]} {a["status"]}: ' + '; '.join(a.get('reasons', [])))
                break
        if not reasons:
            first = json.loads((self.archive.records / (labels[order[0]] + '.json')).read_text())
            second = json.loads((self.archive.records / (labels[order[1]] + '.json')).read_text())
            reasons = rules.pair_identity(first, second, self.protocol['pilot']['max_pair_gap_s'])
        decision = {'pair_id': pid, 'scene': scene, 'n': n, 'k': k, 'order': order, 'labels': labels,
                    'status': 'rejected' if reasons else 'accepted', 'reasons': reasons, 'decided_utc': now_utc(),
                    'decided_epoch': time.time(), 'decided_on_resume': resumed}
        write_json(self.archive.pairs / (pid + '.json'), decision, exclusive=True)
        print(f'Pair {pid}: {decision["status"]}' + ''.join('\n  ' + r for r in reasons), flush=True)
        return decision

    def run_smoke(self):
        check(self.manifest is None, 'smoke launches precede the freeze')
        attempts = self.attempts()
        for scene in self.protocol['pilot']['pairs']:
            k = 1
            while f'{self.protocol["series"]}-smoke-{scene}{rules.retry_suffix(k)}' in attempts:
                k += 1  # A repeated smoke gets a new label; earlier smoke attempts stay.
            attempt = self.launch(f'{self.protocol["series"]}-smoke-{scene}{rules.retry_suffix(k)}', 'smoke', scene)
            check(attempt['status'] == 'passed-smoke', f'{scene} smoke failed: {attempt["reasons"]}')

    def run_calibration(self):
        check(self.manifest is not None, 'not frozen: run smoke and freeze first')
        if self.archive.calibration.exists():
            print('calibration.json exists; calibration is written once (a new series is needed to redo it).')
            return json.loads(self.archive.calibration.read_text())['pass']
        self.finalize_unfinished()
        while True:
            state = rules.calibration_state(self.protocol, self.attempts())
            work = state['next']
            if work is None:
                break
            label = rules.calibration_label(self.protocol['series'], work['scene'], work['i'], work['k'])
            self.launch(label, 'calibration', work['scene'], {'slot': f'{work["scene"]}-{work["i"]}', 'k': work['k']})
        state = rules.calibration_state(self.protocol, self.attempts())
        passed = [s['labels'][-1] for s in state['slots'] if s['status'] == 'passed']
        records = [json.loads((self.archive.records / (label + '.json')).read_text()) for label in passed]
        calibration = rules.build_calibration(self.protocol, records)
        calibration.update(written_utc=now_utc(), slots=state['slots'],
                           protocol_sha256=movement.sha(self.archive.protocol_file))
        write_json(self.archive.calibration, calibration, exclusive=True)
        print('Calibration', 'passed' if calibration['pass'] else 'FAILED: ' + '; '.join(calibration['failures']), flush=True)
        return calibration['pass']

    def run_pilot(self, max_pairs=None):
        check(self.manifest is not None, 'not frozen: run smoke and freeze first')
        check(self.archive.calibration.exists(), 'run calibrate first')
        calibration = json.loads(self.archive.calibration.read_text())
        check(calibration['pass'], 'calibration failed: ' + '; '.join(calibration['failures']))
        self.finalize_unfinished()
        done = 0
        while max_pairs is None or done < max_pairs:
            state = rules.pilot_state(self.protocol, self.attempts(), self.pairs())
            work = state['next']
            if work is None:
                break
            scene, n, k = work['scene'], work['n'], work['k']
            series = self.protocol['series']
            pid = rules.pair_id(series, scene, n, k)
            for side in rules.side_order(n, self.protocol['pilot']['order']):
                label = rules.side_label(series, scene, n, side, k)
                a = self.launch(label, 'pilot', scene, {'pair_id': pid, 'slot': f'{scene}-{n}', 'k': k, 'side': side},
                                calibration=calibration)
                if a['status'] != 'passed':
                    break  # The whole pair is rejected; running the other side would not change that.
            self.decide_pair(scene, n, k, self.attempts())
            done += 1
        state = rules.pilot_state(self.protocol, self.attempts(), self.pairs())
        counts = {}
        for s in state['slots']:
            counts[s['status']] = counts.get(s['status'], 0) + 1
        print('Pilot slots:', counts, flush=True)
        return state['next'] is None and not state['undecided']

    def run_overhead(self):
        """Exploratory: alternating monitored / unmonitored d0 launches to bound the monitor's effect. Not A/A
        evidence and never pooled with the pilot."""
        cfg = self.protocol.get('overhead')
        check(cfg, 'protocol has no overhead stage')
        check(self.manifest is not None, 'not frozen')
        attempts = self.attempts()
        for n in range(1, cfg['pairs'] + 1):
            for side in rules.side_order(n, self.protocol['pilot']['order']):
                label = f'{self.protocol["series"]}-ov-{cfg["scene"]}-{n}-{"mon" if side == "a" else "nomon"}'
                if label not in attempts:
                    self.launch(label, 'overhead', cfg['scene'], {'pair': n}, monitored=side == 'a')


# ---------------------------------------------------------------------------------------------------------------

def plan(protocol):
    series = protocol['series']
    out = []

    def launch_seconds(scene):
        phases = rules.expected_phases(protocol, scene)
        # Pre-launch quiet window (at least its length plus one sample), startup and world load about 35 s, the
        # first settle at least 10 s, every later one at least 3 s, and the exit.
        quiet = protocol['monitor']['pre_launch_quiet_s'] + protocol['monitor']['interval_s']
        return quiet + 35 + 10 + 3 * (len(phases) - 1) + sum(phases.values()) + 5

    total = 0.0
    out.append('Calibration:')
    for scene, i in rules.calibration_slots(protocol['calibration']):
        out.append(f'  {rules.calibration_label(series, scene, i, 1)}')
        total += launch_seconds(scene)
    out.append('Pilot (pair: first side, second side):')
    for scene, n in rules.pilot_slots(protocol['pilot']):
        order = rules.side_order(n, protocol['pilot']['order'])
        out.append(f'  {rules.pair_id(series, scene, n, 1)}: ' +
                   ', '.join(rules.side_label(series, scene, n, s, 1) for s in order))
        total += 2 * launch_seconds(scene)
    for scene in protocol['pilot']['pairs']:
        phases = rules.expected_phases(protocol, scene)
        out.append(f'{scene}: {launch_seconds(scene) / 60:.1f} min per launch; phases ' +
                   ', '.join(f'{k} {v:g} s' for k, v in phases.items()))
    out.append(f'Expected time without repeats: {total / 3600:.2f} h (plus repeated pairs, up to '
               f'{protocol["pilot"]["max_attempts_per_pair"]} attempts per pair).')
    return '\n'.join(out)


def status(runner):
    attempts = runner.attempts()
    cal = rules.calibration_state(runner.protocol, attempts)
    pil = rules.pilot_state(runner.protocol, attempts, runner.pairs())
    lines = [f'Frozen: {runner.manifest is not None}; calibration.json: {runner.archive.calibration.exists()}']
    lines += [f'  calibration {s["scene"]}-{s["i"]}: {s["status"]} {s["labels"]}' for s in cal['slots']]
    lines += [f'  pilot {s["scene"]}-{s["n"]}: {s["status"]} ' + ', '.join(f'{a["pair_id"]}={a["status"]}' for a in s['attempts'])
              for s in pil['slots']]
    lines.append(f'Next: {pil["next"]}; undecided: {pil["undecided"]}')
    return '\n'.join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('stage', choices=('plan', 'smoke', 'freeze', 'calibrate', 'pilot', 'overhead', 'evaluate', 'status'))
    parser.add_argument('--protocol', required=True)
    parser.add_argument('--jdk', help='JDK 25 home (default: the protocol\'s java_home for this platform)')
    parser.add_argument('--init', help='Gradle init script (container only, for a Maven mirror)')
    parser.add_argument('--max-pairs', type=int, help='stop after this many pair attempts (split a series into sittings)')
    parser.add_argument('--draws', type=int, default=20000, help='resampling draws for evaluate')
    opts = parser.parse_args(argv)
    protocol = load_protocol(opts.protocol)
    if opts.stage == 'plan':
        print(plan(protocol))
        return 0
    archive = Archive(protocol, opts.protocol)
    if opts.stage == 'evaluate':
        import aa_report
        if archive.lock.exists():
            raise SystemExit('a runner holds the archive lock: no analysis during scored launches')
        report = aa_report.evaluate(archive.root, draws=opts.draws)
        write_json(archive.root / 'pilot.json', report)
        (archive.root / 'pilot.md').write_text(aa_report.markdown(report, protocol), encoding='utf-8', newline='\n')
        print(json.dumps(report['verdict'], indent=1))
        return 0
    jdk = opts.jdk or protocol['java_home'].get('windows' if plat.WINDOWS else 'linux')
    if not jdk or not java_executable(jdk).exists():
        raise SystemExit(f'JDK not found: {jdk}')
    runner = Runner(protocol, archive, jdk, opts.init)
    if opts.stage == 'status':
        print(status(runner))
        return 0
    runner.acquire()
    ok = True
    try:
        if opts.stage == 'smoke':
            runner.run_smoke()
        elif opts.stage == 'freeze':
            runner.freeze()
        elif opts.stage == 'calibrate':
            ok = runner.run_calibration()
        elif opts.stage == 'pilot':
            # Complete means every slot decided; an unfinished sitting (--max-pairs) is not an error.
            done = runner.run_pilot(opts.max_pairs)
            ok = done or opts.max_pairs is not None
        elif opts.stage == 'overhead':
            runner.run_overhead()
    except CheckFailed as e:
        print('Stopped:', e, flush=True)
        ok = False
    finally:
        runner.release()
    return 0 if ok else 1


if __name__ == '__main__':
    sys.exit(main())
