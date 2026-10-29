"""One retained qualification attempt; never retries. Run from repository root.

python <archive>/launch.py LABEL [alacer.key=value ...]
Use LABEL=manual for the one interactive non-benchmark smoke.
"""
import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

ARCHIVE = Path(__file__).resolve().parent
ROOT = ARCHIVE.parents[3]
sys.path.insert(0, str(ROOT / 'tools'))
import aa_monitor as mon
import aa_platform as plat

JDK = Path(r'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    label, *props = sys.argv[1:]
    target = ARCHIVE / 'launches' / (label + '.json')
    target.parent.mkdir(exist_ok=True)
    if target.exists():
        raise SystemExit('Attempt exists; refusing retry: ' + label)
    freeze = json.loads((ARCHIVE / 'freeze.json').read_text())
    for name, expected in freeze['source_hashes'].items():
        if sha(ROOT / name) != expected: raise SystemExit('Frozen source changed: ' + name)
    if sha(ROOT / 'build/libs/alacer-1.0.0+mc1.8.9.jar') != freeze['jar_sha256']:
        raise SystemExit('Frozen artifact changed')
    platform = plat.current()
    if platform.find_clients():
        raise SystemExit('Another client is running')
    raw = ROOT / 'run/alacer/benchmarks/qualification-r2-telemetry'
    raw.mkdir(exist_ok=True)
    log = raw / (label + '.log')
    command = ['cmd.exe', '/d', '/c', 'gradlew.bat', 'runClient', '-Pwindow=1920x1080', '-Pxmx=2G']
    if not label.endswith('-manual'):
        command += ['-Palacer.benchmark=true', '-Palacer.benchmark.label=' + label]
    command += ['-P' + p for p in props]
    record = dict(label=label, command=command, started_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                  protocol_sha256=sha(ARCHIVE / 'PROTOCOL.md'), source=subprocess.check_output(
                      ['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
                  hardware=platform.static_info(), topology=platform.topology(),
                  jar_sha256=sha(ROOT / 'build/libs/alacer-1.0.0+mc1.8.9.jar'), status='preflight',
                  log=str(log.relative_to(ROOT)), props=props)
    target.write_text(json.dumps(record, indent=2) + '\n')
    gpu = plat.NvidiaSmi()
    record['gpu'] = gpu.static()
    monitor = mon.Monitor(platform, raw / (label + '.jsonl'), gpu=gpu)
    monitor.start()
    try:
        if not label.endswith('-manual'):
            start = time.monotonic()
            while time.monotonic() - start < 120:
                time.sleep(1)
                recent = monitor.samples[-10:]
                readings = [s.get('cpu', {}) for s in recent]
                if (len(recent) == 10 and recent[0]['t'] >= time.time() - 12
                        and all(r.get('status') == 'ok' for r in readings)):
                    values = [r['value']['background_cores'] for r in readings]
                    if sum(values) / len(values) <= 1:
                        record['quiet_wait_s'] = time.monotonic() - start
                        monitor.mark('quiet-window-passed', seconds=record['quiet_wait_s'])
                        break
            else:
                raise RuntimeError('No pre-launch quiet window within 120 seconds; no client launched')
        record['status'] = 'running'
        record['launch_epoch'] = time.time()
        target.write_text(json.dumps(record, indent=2) + '\n')
        monitor.mark('launch', label=label)
        env = dict(os.environ, JAVA_HOME=str(JDK))
        with log.open('w', encoding='utf-8') as output:
            process = subprocess.Popen(command, cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT)
            seen = False
            while process.poll() is None:
                if monitor.client_pid and not seen:
                    pid = monitor.client_pid
                    record['client'] = dict(pid=pid, executable=platform.executable(pid),
                                            command_line=platform.command_line(pid),
                                            affinity=platform.affinity(pid), priority=platform.priority(pid))
                    seen = True
                    target.write_text(json.dumps(record, indent=2) + '\n')
                time.sleep(1)
            record['exit_code'] = process.returncode
        record['exit_epoch'] = time.time()
        record['status'] = 'completed' if process.returncode == 0 else 'failed'
        monitor.mark('exit', code=process.returncode)
    except Exception as error:
        record.update(status='failed', error=str(error))
        raise
    finally:
        monitor.stop()
        record['monitor_errors'] = monitor.errors
        record['freeze_sha256'] = sha(ARCHIVE / 'freeze.json')
        record['environment_monitor'] = mon.summarize(monitor.samples, monitor.gpu_samples())
        record['marks'] = monitor.marks
        record['environment_series'] = mon.downsample(monitor.samples)
        if not label.endswith('-manual'):
            matches = list((ROOT / 'run/alacer/benchmarks').glob('*-' + label + '-vis_*'))
            if len(matches) == 1 and (matches[0] / 'summary.json').exists():
                run = matches[0]
                dest = ARCHIVE / 'runs' / run.name
                dest.mkdir(parents=True, exist_ok=True)
                shutil.copy2(run / 'summary.json', dest / 'summary.json')
                record['run'] = str(run.relative_to(ROOT))
            else:
                record['summary_missing'] = True
        if (ROOT / 'run/logs/latest.log').exists():
            shutil.copy2(ROOT / 'run/logs/latest.log', raw / (label + '-minecraft.log'))
        for name in (label + '.log', label + '-minecraft.log'):
            if (raw / name).exists(): shutil.copy2(raw / name, ARCHIVE / 'logs' / name)
        target.write_text(json.dumps(record, indent=2) + '\n')
        print(json.dumps({k: record.get(k) for k in ('label', 'status', 'exit_code', 'run', 'error')}, indent=2))
    if record['status'] != 'completed' or record.get('summary_missing'):
        raise SystemExit(1)


if __name__ == '__main__':
    main()
