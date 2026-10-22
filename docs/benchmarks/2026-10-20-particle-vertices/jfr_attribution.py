"""Session 20: client-thread JFR execution-sample attribution for the particle scenario, scoped to scored phases.
usage: jfr_attribution.py RECORDING.jfr BENCH_DIR"""
import json, subprocess, sys, csv
from collections import Counter
from datetime import datetime
from pathlib import Path
jfr, folder = Path(sys.argv[1]), Path(sys.argv[2])
exe = 'C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/jfr.exe'
raw = subprocess.run([exe, 'print', '--json', '--stack-depth', '96', '--events', 'jdk.ExecutionSample,jdk.ObjectAllocationSample', str(jfr)], check=True, capture_output=True).stdout
events = json.loads(raw)['recording']['events']
summary = json.loads((folder / 'summary.json').read_text())
epoch = int(summary['environment']['profiler.epochWallMillis']) / 1000
windows = {}
for name in summary['phases']:
    with (folder / (name + '.csv')).open() as f:
        rows = [{k: float(v) for k, v in r.items()} for r in csv.DictReader(f)]
    windows[name] = (epoch + min(r['start_ns'] for r in rows) / 1e9, epoch + max(r['start_ns'] + r['frame_ns'] for r in rows) / 1e9)
    fr = [r['frame_ns'] / 1e6 for r in rows]
    tick = [r for r in rows if r.get('particles_ticked', 0) > 0]
    print(f"{name}: frames {len(rows)} mean {sum(fr)/len(fr):.3f} ms; particles pass {sum(r['particles_ns'] for r in rows)/len(rows)/1e6:.3f} ms; tick frames {len(tick)} particle_tick/tick frame {sum(r['particle_tick_ns'] for r in tick)/max(1,len(tick))/1e6:.3f} ms; rendered {sum(r['particles_rendered'] for r in rows)/len(rows):.0f}")
GROUPS = [
    ('render pass', 'ParticleManager.render'),
    ('tick', 'ParticleManager.tick'),
    ('  Entity.move (tick)', 'Entity.move'),
]
for scope in windows:
    lo, hi = windows[scope]
    total = 0; incl = Counter(); leafs = Counter(); inrender = Counter(); alloc = Counter(); allocr = Counter()
    for e in events:
        v = e['values']
        th = v.get('sampledThread') or v.get('eventThread') or {}
        if th.get('javaName') != 'main': continue
        t = datetime.fromisoformat(v['startTime']).timestamp()
        if not lo <= t < hi: continue
        frames = (v.get('stackTrace') or {}).get('frames', [])
        names = [f['method']['type']['name'].split('/')[-1] + '.' + f['method']['name'] for f in frames]
        if e['type'] == 'jdk.ObjectAllocationSample':
            if any('ParticleManager.render' == n for n in names):
                alloc[v['objectClass']['name'] + ' <- ' + (names[0] if names else '')] += v['weight']
            continue
        total += 1
        for label, key in GROUPS:
            if any(n == key for n in names): incl[label] += 1
        if any(n == 'ParticleManager.render' for n in names):
            # attribute: deepest frame among interesting categories
            cat = None
            for n in names:  # leaf first
                if n.startswith('BufferBuilder.') or n.startswith('VertexFormat') or n.startswith('DirectByteBuffer') or n.startswith('VertexFormatElement'):
                    cat = 'BufferBuilder/' + n.split('.')[0]; break
                if 'getLightLevel' in n or 'ParticleLight' in n or 'getLightColor' in n or 'WorldChunk' in n or 'getBrightness' in n:
                    cat = 'light'; break
                if n.endswith('.render') and 'Particle' in n:
                    cat = 'render-self:' + n; break
                if 'Tesselator.end' in n or 'BufferUploader' in n or 'GL11' in n:
                    cat = 'draw'; break
            inrender[cat or ('other:' + names[0])] += 1
            leafs[names[0]] += 1
    print(f"\n[{scope}] client samples {total}")
    for k, c in incl.most_common(): print(f"  {k:28} {c:6} {100*c/total:5.1f}%")
    rs = sum(inrender.values()) or 1
    print('  render-pass breakdown:')
    for k, c in inrender.most_common(14): print(f"    {k:60} {c:5} {100*c/rs:5.1f}% of pass")
    print('  render-pass leaf frames:')
    for k, c in leafs.most_common(14): print(f"    {k:60} {c:5} {100*c/rs:5.1f}%")
    print('  render-pass alloc sampled MB:', {k: round(w/1e6, 2) for k, w in alloc.most_common(6)})
