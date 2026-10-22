"""Callers of a leaf method inside ParticleManager.render (client thread). usage: le_stacks.py REC.jfr LEAF [depth]"""
import json, subprocess, sys
from collections import Counter
exe = 'C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/jfr.exe'
raw = subprocess.run([exe, 'print', '--json', '--stack-depth', '64', '--events', 'jdk.ExecutionSample', sys.argv[1]], check=True, capture_output=True).stdout
leaf = sys.argv[2]; depth = int(sys.argv[3]) if len(sys.argv) > 3 else 8
c = Counter(); total = 0
for e in json.loads(raw)['recording']['events']:
    v = e['values']
    if (v.get('sampledThread') or {}).get('javaName') != 'main': continue
    fr = (v.get('stackTrace') or {}).get('frames', [])
    names = [f['method']['type']['name'].split('/')[-1] + '.' + f['method']['name'] + ':' + str(f.get('lineNumber')) for f in fr]
    if not any(n.startswith('ParticleManager.render') for n in names): continue
    total += 1
    idx = [i for i, n in enumerate(names) if n.split(':')[0] == leaf]
    if idx: c[' <- '.join(names[idx[0]:idx[0] + depth])] += 1
print('pass samples', total)
for k, n in c.most_common(12): print(n, k)
