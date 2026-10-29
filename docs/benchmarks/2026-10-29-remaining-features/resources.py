"""Four fresh resource functional launches, A/B/B/A; never performance promotion."""
import re
import subprocess
import sys
from launch import HERE, ROOT

for index, enabled in enumerate(('false', 'true', 'true', 'false'), 1):
    label = 'q3r2-reload-' + str(index)
    props = ['benchmark.timeScale=1', 'benchmark.resourceTiming=true',
             'followup.resourceReload=true', 'render.fastMipmaps=' + enabled,
             'render.fastMipmaps.verify=true']
    print('START ' + label + ' fastMipmaps=' + enabled, flush=True)
    result = subprocess.run([sys.executable, str(HERE / 'launch.py'), label, *props], cwd=ROOT)
    if result.returncode: raise SystemExit('STOP: failed launch ' + label)
    log = (HERE / 'logs' / (label + '.log')).read_text()
    completions = re.findall(r'FOLLOWUP reload (\d) completed; (.*)', log)
    if len(completions) != 3 or 'FOLLOWUP RESOURCE FAILURE' in log:
        raise SystemExit('STOP: missing reload ' + label)
    if any(not summary.endswith('0 mismatches') for _, summary in completions):
        raise SystemExit('STOP: resource verifier difference ' + label)
    print('DONE ' + label + ': ' + repr(completions), flush=True)
