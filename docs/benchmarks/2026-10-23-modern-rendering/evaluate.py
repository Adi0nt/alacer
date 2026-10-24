"""Evaluate the unchanged gates, after serial clients have stopped. Python standard library only."""
import csv, json, statistics, sys
from pathlib import Path

METRICS = ['frame_ms', 'cpu_ms', 'p99_ms', 'slow1_ms', 'alloc_bytes', 'visible_chunks', 'visibility_ms']
OPTIONS = ['render.fastChunkNeighbours', 'render.terrainVaos', 'render.directBufferUploads']

def mean_visible(path):
    total = count = 0
    with path.open(newline='') as stream:
        reader = csv.reader(stream)
        index = next(reader).index('visible_chunks')
        for row in reader:
            total += int(row[index]); count += 1
    return total / count

def metrics(p, path):
    return dict(zip(METRICS, [1000 / p['avg_fps'], p['client_thread_cpu_ms_per_frame'],
        p['frame_p99_ms'], 1000 / p['one_percent_low_fps'], p['alloc_mean_bytes'],
        mean_visible(path), p['visibility_mean_ms']]))

def evaluate(root, archive):
    receipts = json.loads((archive/'performance-launches.json').read_text())
    assert len(receipts) == 16, 'Incomplete series'
    result = {'correctness_review': 'See README and validation artifacts', 'gates': {}, 'phases': {}, 'runs': {}}
    valid = True
    for scene in ['default', 'terrain']:
        runs = []
        phases = ['static', 'pan', 'fly'] if scene == 'default' else ['steady', 'pan', 'sort', 'fly']
        for receipt, arm in zip(receipts[0:8] if scene == 'default' else receipts[8:16], 'ABBABAAB'):
            path = root / receipt['summary']; data = json.loads(path.read_text()); env = data['environment']
            checks = [env['java.version'].startswith('25.'), env['alacer.configSource'] == 'build-defaults-and-jvm-overrides',
                      env['modernGl.forcedFallback'] == 'false', env['modernGl.vaoSupported'] == 'true',
                      env['modernGl.dsaSupported'] == 'true', env['modernGl.vaoFailures'] == '0', env['modernGl.uploadFailures'] == '0',
                      env['modernGl.vaoChecks'] == '0', env['modernGl.uploadChecks'] == '0',
                      env['window'] == '1920x1080', env['options.renderDistance'] == '12', env['jvm.maxHeapMB'] == '2048',
                      env['vboActive'] == 'true', env['options.vsync'] == 'false', env['benchmark.screenshots'] == 'false',
                      env['profiler.allocationAvailable'] == 'true',
                      env['benchmark.backendScenario'] == str(scene == 'terrain').lower(), env['benchmark.generationRun'] == 'false',
                      '-XX:StartFlightRecording' not in env['jvm.args'],
                      (int(env['modernGl.namedUploads']) > 0) == (arm == 'B'),
                      (int(env['modernGl.vaoBinds']) > 0) == (arm == 'B')]
            values = {}
            for phase in phases:
                p = data['phases'][phase]; c = p['config']
                checks += [p['dropped_frames'] == 0, p['alloc_mean_bytes'] >= 0, p['client_thread_cpu_ms_per_frame'] > 0] + [c[k] == str(arm=='B').lower() for k in OPTIONS]
                checks += [c[k] == 'false' for k in ['render.fastChunkNeighbours.verify', 'render.modernGl.verify',
                    'render.fastChunkVisibility.verify', 'profiler.chunkDetail', 'profiler.entityDetail', 'profiler.particleDetail']]
                values[phase] = metrics(p, path.parent/(phase+'.csv'))
            valid &= all(checks)
            result['runs'][receipt['label']] = {'arm': arm, 'valid': all(checks), 'phases': values}
            runs.append((arm, values))
        for phase in phases:
            pairs = []
            for i in range(0, 8, 2):
                off, on = sorted(runs[i:i+2], key=lambda r: r[0])
                pairs.append({k: 100*(on[1][phase][k]/off[1][phase][k]-1) for k in METRICS})
            summary = {k: {'median_change_pct': statistics.median(p[k] for p in pairs),
                'lower_pairs': sum(p[k] < 0 for p in pairs), 'pair_changes_pct': [p[k] for p in pairs],
                'control_median': statistics.median(r[1][phase][k] for r in runs if r[0]=='A'),
                'candidate_median': statistics.median(r[1][phase][k] for r in runs if r[0]=='B')} for k in METRICS}
            result['phases'][scene+'/'+phase] = summary
    gates = result['gates']
    gates['valid_launches'] = bool(valid)
    for key, m in result['phases'].items():
        scene, phase = key.split('/')
        for metric, limit in [('frame_ms', 2), ('cpu_ms', 2), ('p99_ms', 5), ('slow1_ms', 5), ('alloc_bytes', 2)]:
            gates[key+'/'+metric+'_no_regression'] = m[metric]['median_change_pct'] <= limit
        gates[key+'/visible_chunks_unchanged'] = abs(m['visible_chunks']['median_change_pct']) <= 1
        if phase in ['static', 'steady']:
            gates[key+'/still_control'] = abs(m['frame_ms']['median_change_pct']) <= 2
        else:
            gates[key+'/visibility_improvement'] = m['visibility_ms']['median_change_pct'] <= -5
        if phase == 'pan':
            gates[key+'/visibility_repeat'] = m['visibility_ms']['lower_pairs'] >= 3
            gates[key+'/frame_or_cpu_benefit'] = any(m[k]['median_change_pct'] <= -1 and m[k]['lower_pairs'] >= 3 for k in ['frame_ms', 'cpu_ms'])
    result['performance_passed'] = all(gates.values())
    result['selection'] = 'All three remain off by default; no promotion in this work.'
    return result

if __name__ == '__main__':
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
    archive = Path(__file__).resolve().parent
    result = evaluate(root, archive)
    (archive/'evaluation.json').write_text(json.dumps(result, indent=2)+'\n')
    for key, m in result['phases'].items():
        print(key, ' '.join(k+'='+format(m[k]['median_change_pct'], '+.2f')+'%' for k in METRICS))
    print('Performance gates:', result['performance_passed'])
    print('Failed gates:', [k for k,v in result['gates'].items() if not v])
