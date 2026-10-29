"""Run the remaining three frozen functional attempts; stop on the first failure."""
import datetime
import json
import re
import subprocess
import sys
from launch import HERE, ROOT, sha


def validate(label):
    record = json.loads((HERE / 'launches' / (label + '.json')).read_text())
    log = (HERE / 'logs' / (label + '.log')).read_text(errors='replace')
    if record['status'] != 'completed' or record.get('summary_missing'):
        raise SystemExit('STOP: incomplete ' + label)
    if re.search(r'OpenGL Error|GL ERROR|InvalidMixin|Mixin apply.*failed|EXCEPTION_ACCESS_VIOLATION|Unreported exception|Benchmark timed out', log):
        raise SystemExit('STOP: functional error ' + label)
    summary = json.loads(next((HERE / 'runs').glob('*-' + label + '-vis_*/summary.json')).read_text())
    if any(p['dropped_frames'] or not p['frames'] for p in summary['phases'].values()):
        raise SystemExit('STOP: incomplete frame record ' + label)
    if label == 'v1h-mdi':
        counters = (HERE / 'logs' / (label + '-counters.txt')).read_text()
        checks = re.findall(r'MultiDrawIndirect.verifiedCommands=(\d+)', counters)
        failures = re.findall(r'MultiDrawIndirect.verifyMismatches=(\d+)', counters)
        if not checks or int(checks[-1]) <= 0 or not failures or any(int(n) for n in failures):
            raise SystemExit('STOP: inactive/failed MDI verifier')
    print('PASS ' + label, flush=True)


validate('v1h-fallback')
first = json.loads((HERE / 'launches/v1h-fallback.json').read_text())
deadline = datetime.datetime.fromisoformat(first['started_utc']) + datetime.timedelta(minutes=12)
jobs = [
    ('v1h-mdi', ['render.multiDrawIndirect=true', 'render.multiDrawIndirect.verify=true', 'render.throttleGlErrorChecks=false']),
    ('v1h-storage-toggle', ['benchmark.toggle=render.multiDrawIndirect', 'modernGl.forceFallback=true', 'render.throttleGlErrorChecks=false']),
    ('v1h-production-fallback', None),
]
freeze = json.loads((HERE / 'freeze.json').read_text())
for label, props in jobs:
    if (deadline - datetime.datetime.now(datetime.timezone.utc)).total_seconds() < 180:
        raise SystemExit('STOP: insufficient deadline remaining')
    for name, expected in freeze['source_sha256'].items():
        if sha(ROOT / name) != expected: raise SystemExit('STOP: source changed ' + name)
    if sha(ROOT / 'build/libs/alacer-1.0.0+mc1.8.9.jar') != freeze['jar_sha256']:
        raise SystemExit('STOP: artifact changed')
    command = [sys.executable, str(HERE / ('production.py' if props is None else 'launch.py'))]
    if props is not None: command += [label, *props]
    print('START ' + label, flush=True)
    if subprocess.run(command, cwd=ROOT).returncode: raise SystemExit('STOP: failed ' + label)
    validate(label)
