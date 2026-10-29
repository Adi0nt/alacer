"""Retain one new functional attempt. Run from repository root; no retries."""
import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
JDK = Path(r'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    label, *extra = sys.argv[1:]
    target = HERE / 'launches' / (label + '.json')
    target.parent.mkdir(exist_ok=True)
    if target.exists():
        raise SystemExit('Label already attempted: ' + label)
    backup = ROOT / 'build/remaining-features-backup'
    backup.mkdir(exist_ok=True)
    for name in ('options.txt', 'config/alacer.properties'):
        p = ROOT / 'run' / name
        b = backup / p.name
        if not b.exists():
            shutil.copy2(p, b)
    options = ROOT / 'run/options.txt'
    lines = options.read_text().splitlines()
    lines = [line for line in lines if not line.startswith('soundCategory_master:')]
    options.write_text('\n'.join(lines) + '\nsoundCategory_master:0.0\n')
    raw = ROOT / 'run/alacer/benchmarks/remaining-features'
    raw.mkdir(exist_ok=True)
    log = raw / (label + '.log')
    props = {
        'benchmark': 'true', 'benchmark.label': label, 'benchmark.timeScale': '0.35',
        'benchmark.vbo': 'true', 'benchmark.screenshots': 'true',
        'benchmark.timeout': '360', 'profiler.hud': 'false',
    }
    for prop in extra:
        key, value = prop.split('=', 1)
        props[key] = value
    command = ['cmd.exe', '/d', '/c', 'gradlew.bat', 'runClient',
               '--console=plain', '-Pwindow=1280x720', '-Pxmx=2G']
    if (ROOT / 'build/remaining-feature-observer.jar').exists():
        command += ['--init-script', str(HERE / 'observer.init.gradle')]
    command += ['-Palacer.' + key + '=' + value for key, value in props.items()]
    record = dict(label=label, command=command, props=props,
                  source=subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
                  started_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                  java_home=str(JDK), options_sha256=sha(options),
                  jar_sha256=sha(ROOT / 'build/libs/alacer-1.0.0+mc1.8.9.jar'),
                  protocol_sha256=sha(HERE / 'PROTOCOL.md'),
                  master_volume='0.0', status='running')
    agent = ROOT / 'build/remaining-feature-observer.jar'
    if agent.exists(): record['observer_sha256'] = sha(agent)
    target.write_text(json.dumps(record, indent=2) + '\n')
    env = dict(os.environ, JAVA_HOME=str(JDK))
    start = time.monotonic()
    with log.open('w', encoding='utf-8') as out:
        process = subprocess.Popen(command, cwd=ROOT, env=env, stdout=out, stderr=subprocess.STDOUT)
        try:
            record['exit_code'] = process.wait(timeout=420)
        except subprocess.TimeoutExpired:
            subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], capture_output=True)
            record['exit_code'] = process.wait()
            record['timeout'] = True
    record['duration_s'] = time.monotonic() - start
    record['ended_utc'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
    record['status'] = 'completed' if record['exit_code'] == 0 else 'failed'
    record['log'] = str(log.relative_to(ROOT))
    folders = list((ROOT / 'run/alacer/benchmarks').glob('*-' + label + '-vis_*'))
    if len(folders) == 1 and (folders[0] / 'summary.json').exists():
        record['run'] = str(folders[0].relative_to(ROOT))
        dest = HERE / 'runs' / folders[0].name
        dest.mkdir(parents=True, exist_ok=True)
        for file in folders[0].glob('*'):
            if file.suffix in ('.json', '.png'):
                shutil.copy2(file, dest / file.name)
        for file in (ROOT / 'run/screenshots').glob('alacer-' + label + '-*.png'):
            shutil.copy2(file, dest / file.name)
    else:
        record['summary_missing'] = True
    (HERE / 'logs').mkdir(exist_ok=True)
    (HERE / 'logs' / log.name).write_text('\n'.join(
        line.rstrip() for line in log.read_text(encoding='utf-8', errors='replace').splitlines()) + '\n', encoding='utf-8')
    counters = raw / (label + '-counters.txt')
    if counters.exists():
        shutil.copy2(counters, HERE / 'logs' / counters.name)
    target.write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps({key: record.get(key) for key in
                      ('label', 'status', 'exit_code', 'duration_s', 'run', 'summary_missing')}, indent=2), flush=True)
    if record['status'] != 'completed' or record.get('summary_missing'):
        raise SystemExit(1)


if __name__ == '__main__':
    main()
