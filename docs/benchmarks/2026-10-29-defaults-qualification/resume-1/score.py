"""Score completed predeclared observations; never launches or changes gates."""
import json
from pathlib import Path
import re
import subprocess
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(ROOT / 'tools'))
import aa_platform as plat
import compare_benchmarks as compare


def changes(a, b):
    return {k: 100 * (b[k] / a[k] - 1) for k in a if isinstance(a[k], (float, int))
            and a[k] and isinstance(b.get(k), (float, int))}


def gates(delta, keys):
    return {k: dict(nonregression=delta.get(k) is not None and delta[k] <= 3,
                    improves_3_percent=delta.get(k) is not None and delta[k] <= -3)
            for k in keys}


def main():
    if plat.current().find_clients(): raise SystemExit('No offline analysis alongside a client')
    records = {p.stem: json.loads(p.read_text()) for p in (HERE / 'launches').glob('*.json')}
    path = lambda label: ROOT / records[label]['run']
    labels = [l for l in records if re.fullmatch(r'q2-(vectorFrustum|fastModelTransforms)-[12]', l)]
    proc = subprocess.run([sys.executable, str(HERE / 'analyze.py'), *(str(path(l)) for l in sorted(labels))],
                          capture_output=True, text=True, check=True)
    (HERE / 'inprocess.json').write_text(proc.stdout)
    toggles = json.loads(proc.stdout)
    for run, launch in toggles.items():
        for phase, row in launch['phases'].items():
            row['gates'] = gates(row['change_pct'], ('median_ms', 'p99_ms', 'client_cpu_ms_per_frame'))
    reversals = {}
    for option in ('vectorFrustum', 'fastModelTransforms'):
        ordered = [v for k, v in sorted(toggles.items()) if '-' + option + '-' in k]
        reversals[option] = {phase: {metric: ordered[0]['phases'][phase]['change_pct'][metric]
                                    * ordered[1]['phases'][phase]['change_pct'][metric] < 0
                                    for metric in ('median_ms', 'p99_ms', 'client_cpu_ms_per_frame')}
                             for phase in ('static', 'pan')} if len(ordered) == 2 else None
    workers = {}
    for index in range(1, 5):
        label = 'q2-fastWorkerAo-' + str(index)
        if label not in records: continue
        run = path(label)
        summary = json.loads((run / 'summary.json').read_text())
        workers[label] = {phase: dict(frame=compare.summarize([(str(run), phase)]),
                                      update_latency={k: v for k, v in summary['phases'][phase].items()
                                                      if k.startswith('update_latency_')}) for phase in ('still', 'edit')}
    pairs = {}
    for name, aa, bb in (('A1-B2', [1], [2]), ('A4-B3', [4], [3]), ('pooled', [1, 4], [2, 3])):
        a_labels = ['q2-fastWorkerAo-' + str(i) for i in aa]
        b_labels = ['q2-fastWorkerAo-' + str(i) for i in bb]
        if not all(l in records for l in a_labels + b_labels): continue
        command = [sys.executable, str(ROOT / 'tools/compare_benchmarks.py'), '--phases', 'still,edit',
                   *(str(path(l)) for l in a_labels), '--', *(str(path(l)) for l in b_labels)]
        output = subprocess.run(command, capture_output=True, text=True, check=True)
        (HERE / ('worker-' + name + '.md')).write_text(output.stdout.rstrip() + '\n', encoding='utf-8')
        rows = {}
        for phase in ('still', 'edit'):
            a = compare.summarize([(str(path(l)), phase) for l in a_labels])
            b = compare.summarize([(str(path(l)), phase) for l in b_labels])
            delta = changes(a, b)
            rows[phase] = dict(a=a, b=b, change_pct=delta,
                               gates=gates(delta, ('frame p50 ms', 'frame p99 ms', 'client thread CPU ms/frame')))
            if len(aa) == 1:
                la = workers[a_labels[0]][phase]['update_latency']
                lb = workers[b_labels[0]][phase]['update_latency']
                rows[phase]['update_latency_change_pct'] = changes(la, lb)
        pairs[name] = rows
    resources = {}
    for index in range(1, 5):
        label = 'q2-fastMipmaps-' + str(index)
        if label not in records: continue
        log = (HERE / 'logs' / (label + '.log')).read_text(errors='replace')
        resources[label] = {kind: [int(n) / 1e6 for n in re.findall('Resource timing: ' + re.escape(kind) + r' (\d+) ns', log)]
                            for kind in ('atlas loadAndStitch', 'Minecraft reloadResources')}
    resource_pairs = {}
    for name, a, b in (('A1-B2', 1, 2), ('A4-B3', 4, 3)):
        if not all('q2-fastMipmaps-' + str(i) in resources for i in (a, b)): continue
        row = {}
        for scope, kind, index in (('startup_atlas', 'atlas loadAndStitch', 0),
                                   ('reload_atlas', 'atlas loadAndStitch', 1),
                                   ('reload_total', 'Minecraft reloadResources', 1)):
            av, bv = (resources['q2-fastMipmaps-' + str(i)][kind] for i in (a, b))
            row[scope] = dict(a_ms=av[index], b_ms=bv[index], change_pct=100 * (bv[index] / av[index] - 1)) if len(av) > index and len(bv) > index else None
        resource_pairs[name] = row
    result = dict(mode='Standard', protocol_sha256=next(iter(records.values()))['protocol_sha256'],
                  verification=json.loads((HERE / 'verification.json').read_text()),
                  inprocess=toggles, order_sign_reversals=reversals, worker_launches=workers,
                  worker_comparisons=pairs, resource_launches=resources, resource_pairs=resource_pairs,
                  limitations=['Other shipped options remain on, including options with verifier differences',
                               'ChunkLatency measures visible-update end-to-end latency; isolated worker-build/upload detail timers are unavailable with detail profiling off',
                               'Standard mode cannot support release decisions or small-improvement claims'])
    (HERE / 'results.json').write_text(json.dumps(result, indent=2) + '\n')
    for run, launch in toggles.items():
        print(run)
        for phase, row in launch['phases'].items():
            print(phase, {k: round(row['change_pct'][k], 2) for k in ('median_ms', 'p99_ms', 'client_cpu_ms_per_frame')})
    for pair, phases in pairs.items():
        print(pair, {p: {k: round(row['change_pct'][k], 2) for k in ('frame p50 ms', 'frame p99 ms', 'client thread CPU ms/frame')} for p, row in phases.items()})
    print('Resources:', resources, resource_pairs)


if __name__ == '__main__':
    main()
