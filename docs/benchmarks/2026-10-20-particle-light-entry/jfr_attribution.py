"""Client-thread JFR attribution of the particle render pass, scored phases only (session 21: light entry).
usage: le_jfr.py RECORDING.jfr BENCH_DIR"""
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
    print(f"{name}: frames {len(rows)} mean {sum(fr)/len(fr):.3f} ms; particles pass {sum(r['particles_ns'] for r in rows)/len(rows)/1e6:.3f} ms; "
          f"alloc/frame {sum(r.get('alloc_bytes', 0) for r in rows)/len(rows):.0f} B; rendered {sum(r['particles_rendered'] for r in rows)/len(rows):.0f}")

def category(names):
    # names: leaf first. Entry = isChunkLoaded subtree or the position copy; query = getLightColor/ParticleLight subtree.
    for n in names:
        if n.startswith('World.isChunkLoaded') or 'ParticleLightEntry' in n:
            return 'light entry (isChunkLoaded)'
        if n.startswith('BlockPos.<init>') or n.startswith('Vec3i.<init>'):
            return 'light entry (BlockPos copy)'
        if 'ParticleLight.' in n or n.startswith('World.getLightColor') or n.startswith('WorldChunk.'):
            return 'light query'
        if n.endswith('.getLightLevel'):
            return 'light (getLightLevel self)'
        if n.startswith('BufferBuilder.') or n.startswith('ParticleVert') or 'ByteBuffer' in n or n.startswith('Buffer.'):
            return 'vertex writes'
        if 'Tesselator.end' in n or 'BufferUploader' in n or 'GL11' in n:
            return 'draw'
    for n in names:
        if n.endswith('.render') and 'Particle' in n and not n.startswith('ParticleManager'):
            return 'particle render self'
    return 'other:' + names[0]

for scope in windows:
    lo, hi = windows[scope]
    total = 0; render = 0; cats = Counter(); leafs = Counter(); alloc = Counter(); kinds = Counter()
    for e in events:
        v = e['values']
        th = v.get('sampledThread') or v.get('eventThread') or {}
        if th.get('javaName') != 'main': continue
        t = datetime.fromisoformat(v['startTime']).timestamp()
        if not lo <= t < hi: continue
        frames = (v.get('stackTrace') or {}).get('frames', [])
        names = [f['method']['type']['name'].split('/')[-1] + '.' + f['method']['name'] for f in frames]
        inpass = 'ParticleManager.render' in names
        if e['type'] == 'jdk.ObjectAllocationSample':
            if inpass:
                alloc[v['objectClass']['name'].split('/')[-1] + ' <- ' + ' <- '.join(names[:3])] += v['weight']
            continue
        total += 1
        if not inpass: continue
        render += 1
        cats[category(names)] += 1
        leafs[names[0]] += 1
        if names[0] == 'Long2ObjectHashMap.getNode':
            kinds[' <- '.join(f"{f['method']['type']['name'].split('/')[-1]}.{f['method']['name']}[{f.get('type')}]" for f in frames[:7])] += 1
    print(f"\n[{scope}] client samples {total}; particle pass {render} ({100*render/max(1,total):.1f}%)")
    for k, c in cats.most_common(): print(f"    {k:44} {c:5} {100*c/max(1,render):5.1f}% of pass")
    print('  leaf frames:')
    for k, c in leafs.most_common(12): print(f"    {k:60} {c:5} {100*c/max(1,render):5.1f}%")
    for k, c in kinds.most_common(3): print('  getNode:', c, k)
    print('  pass alloc sampled MB:')
    for k, w in alloc.most_common(6): print(f"    {w/1e6:8.2f} {k}")
