#!/usr/bin/env python3
"""Session 28: evaluate the gate series for render.reuseShadowPositions (gates.md).

Usage: python3 gates.py <out.json>
Reads gates/<label>.record.json and each launch's raw CSVs/JSONs under run/alacer/benchmarks.
"""
import csv
import json
import statistics
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent.parent
GATES = HERE / 'gates'
ESTIMATE_HALF = {'static': 9855.0, 'pan': 10737.0}
COUNTERS = ('visible_chunks', 'terrain_solid_chunks', 'terrain_cutout_mipped_chunks', 'terrain_cutout_chunks',
            'terrain_translucent_chunks', 'entities_drawn', 'block_entities_drawn', 'shadows_drawn')


def record(label):
    return json.loads((GATES / (label + '.record.json')).read_text())


def rows(folder, phases):
    out = []
    for name in phases:
        with open(ROOT / folder / (name + '.csv'), newline='') as f:
            out += [{k: int(v) for k, v in r.items()} for r in csv.DictReader(f)]
    return out


def cpu(folder, phases):
    thread = process = frames = wall = 0.0
    summary = json.loads((ROOT / folder / 'summary.json').read_text())['phases']
    for name in phases:
        p = summary[name]
        n = p['frames']
        frames += n
        thread += p['client_thread_cpu_ms_per_frame'] * n
        process += p['process_cpu_cores'] * p['recorded_wall_seconds'] * 1000.0
        wall += p['recorded_wall_seconds']
    return thread / frames, process / frames, wall


def pct(values, q):
    v = sorted(values)
    return v[min(len(v) - 1, max(0, int(round(q * len(v) + 0.5)) - 1))]


def metrics(folder, phases):
    r = rows(folder, phases)
    frame = [x['frame_ns'] / 1e6 for x in r]
    slow = sorted(frame, reverse=True)[:max(1, len(frame) // 100)]
    tick = [x['frame_ns'] / 1e6 for x in r if x['tick_ns'] > 0]
    client_cpu, process_cpu, wall = cpu(folder, phases)
    alloc = statistics.fmean(x['alloc_bytes'] for x in r)
    m = {'frames': len(r), 'alloc_per_frame': alloc, 'alloc_per_second': alloc * len(r) / wall,
         'frame_mean_ms': statistics.fmean(frame), 'p95_ms': pct(frame, 0.95), 'p99_ms': pct(frame, 0.99),
         'slow1_mean_ms': statistics.fmean(slow), 'tick_p99_ms': pct(tick, 0.99) if tick else 0.0,
         'client_cpu_ms_per_frame': client_cpu, 'process_cpu_ms_per_frame': process_cpu}
    for c in COUNTERS:
        m[c] = statistics.fmean(x[c] for x in r)
    return m


def change(a, b):
    out = {}
    for k in a:
        if k == 'frames':
            continue
        out[k] = (b[k] / a[k] - 1.0) if a[k] else (0.0 if b[k] == 0 else float('inf'))
    out['alloc_saving_bytes'] = a['alloc_per_frame'] - b['alloc_per_frame']
    return out


def counters_ok(ch):
    return all(abs(ch[c]) <= 0.02 for c in COUNTERS)


def screenshots():
    def load(label, phase):
        return np.asarray(Image.open(ROOT / record(label)['screenshots'][phase]['path']).convert('RGB'), dtype=np.int16)

    out = {}
    for phase in ('static', 'pan', 'fly'):
        base = load('alloc28g-shot-absent-1-r2', phase)
        res = {}
        for other in ('alloc28g-shot-on', 'alloc28g-shot-absent-2'):
            d = np.abs(load(other, phase) - base)
            res[other] = {'mean_abs_rgb': float(d.mean()), 'differing_pixels_percent': float((d.max(axis=2) > 0).mean() * 100)}
        on, ref = res['alloc28g-shot-on'], res['alloc28g-shot-absent-2']
        res['pass'] = (on['mean_abs_rgb'] <= ref['mean_abs_rgb'] * 1.1 + 1e-9
                       and on['differing_pixels_percent'] <= ref['differing_pixels_percent'] * 1.1 + 0.1)
        out[phase] = res
    return out


def main(out_path):
    result = {}
    verify = [record(l) for l in ('alloc28g-verify-d200-1-r2', 'alloc28g-verify-d200-2', 'alloc28g-verify-walk')]
    compared = sum(r['shadow_positions_compared'] for r in verify)
    mismatched = sum(r['shadow_positions_mismatched'] for r in verify)
    result['verify'] = {'compared': compared, 'mismatched': mismatched,
                        'per_launch': {r['label']: r['shadow_positions_compared'] for r in verify},
                        'pass': compared >= 10_000_000 and mismatched == 0}
    result['screenshots'] = screenshots()

    ab = {}
    for n in range(1, 5):
        r = record(f'alloc28g-ab-{n}' + ('-r2' if n == 1 else ''))
        per = {}
        for phase in ('static', 'pan'):
            a = metrics(r['source'], (f'{phase}-a1', f'{phase}-a2'))
            b = metrics(r['source'], (f'{phase}-b1', f'{phase}-b2'))
            a1 = metrics(r['source'], (f'{phase}-a1',))
            a2 = metrics(r['source'], (f'{phase}-a2',))
            per[phase] = {'A': a, 'B': b, 'change': change(a, b),
                          'a2_over_a1_frame_drift': a2['frame_mean_ms'] / a1['frame_mean_ms'] - 1.0}
        ab[r['label']] = per
    result['in_process'] = ab

    startup = {}
    for scene, pairs in (('d200', 4), ('d0', 2), ('terrain', 2)):
        phases = ('steady', 'pan', 'sort', 'fly') if scene == 'terrain' else ('static', 'pan', 'fly')
        sp = {}
        for n in range(1, pairs + 1):
            ra, rb = record(f'alloc28g-st-{scene}-{n}-absent'), record(f'alloc28g-st-{scene}-{n}-on')
            sp[n] = {}
            for phase in phases:
                a, b = metrics(ra['source'], (phase,)), metrics(rb['source'], (phase,))
                sp[n][phase] = {'absent': a, 'on': b, 'change': change(a, b)}
        startup[scene] = sp
    result['startup'] = startup

    judged = {}
    for phase in ('static', 'pan'):
        changes = [ab[l][phase]['change'] for l in ab]
        lower = sum(1 for c in changes if c['alloc_per_frame'] < 0)
        med_rel = statistics.median(c['alloc_per_frame'] for c in changes)
        med_bytes = statistics.median(c['alloc_saving_bytes'] for c in changes)
        st = [startup['d200'][n][phase]['change'] for n in startup['d200']]
        st_rel = statistics.median(c['alloc_per_frame'] for c in st)
        st_bytes = statistics.median(c['alloc_saving_bytes'] for c in st)
        judged['target_' + phase] = {
            'in_process_lower': lower, 'in_process_median_change': med_rel, 'in_process_median_saving_bytes': med_bytes,
            'startup_median_change': st_rel, 'startup_median_saving_bytes': st_bytes,
            'needed_saving_bytes': ESTIMATE_HALF[phase],
            'pass': lower >= 3 and med_rel <= -0.05 and med_bytes >= ESTIMATE_HALF[phase]
                    and st_rel <= -0.05 and st_bytes >= ESTIMATE_HALF[phase]}
    regressions, counter_fail = [], []
    for phase in ('static', 'pan'):
        changes = [ab[l][phase]['change'] for l in ab]
        if statistics.median(c['alloc_per_frame'] for c in changes) > 0.02:
            regressions.append('in-process/' + phase)
        for c in COUNTERS:
            if abs(statistics.median(x[c] for x in changes)) > 0.02:
                counter_fail.append(f'in-process/{phase}/{c}')
    for scene, sp in startup.items():
        for phase in next(iter(sp.values())):
            changes = [sp[n][phase]['change'] for n in sp]
            if statistics.median(c['alloc_per_frame'] for c in changes) > 0.02:
                regressions.append(f'startup/{scene}/{phase}')
            for c in COUNTERS:
                med = statistics.median(x[c] for x in changes)
                if abs(med) > 0.02:
                    counter_fail.append(f'startup/{scene}/{phase}/{c}')
    judged['gate5_allocation_regressions'] = regressions
    judged['gate6_counter_failures'] = counter_fail
    judged['correctness_pass'] = result['verify']['pass'] and all(v['pass'] for v in result['screenshots'].values())
    judged['allocation_pass'] = judged['target_static']['pass'] and judged['target_pan']['pass'] and not regressions
    judged['counters_pass'] = not counter_fail
    reported = {}
    for phase in ('static', 'pan'):
        changes = [ab[l][phase]['change'] for l in ab]
        reported['in_process_' + phase] = {k: statistics.median(c[k] for c in changes)
                                           for k in ('frame_mean_ms', 'p95_ms', 'p99_ms', 'slow1_mean_ms', 'tick_p99_ms',
                                                     'client_cpu_ms_per_frame', 'process_cpu_ms_per_frame',
                                                     'alloc_per_second')}
        reported['in_process_' + phase]['median_abs_a2_a1_drift'] = statistics.median(
            abs(ab[l][phase]['a2_over_a1_frame_drift']) for l in ab)
    for scene, sp in startup.items():
        for phase in next(iter(sp.values())):
            changes = [sp[n][phase]['change'] for n in sp]
            reported[f'startup_{scene}_{phase}'] = {k: statistics.median(c[k] for c in changes)
                                                    for k in ('alloc_per_frame', 'frame_mean_ms', 'p95_ms', 'p99_ms',
                                                              'tick_p99_ms', 'client_cpu_ms_per_frame',
                                                              'process_cpu_ms_per_frame', 'alloc_per_second')}
    result['judged'] = judged
    result['reported_not_judged'] = reported
    Path(out_path).write_text(json.dumps(result, indent=1) + '\n')
    print(json.dumps(judged, indent=1))
    print(json.dumps(reported, indent=1))
    print(json.dumps(result['verify'], indent=1))
    print(json.dumps(result['screenshots'], indent=1))


if __name__ == '__main__':
    main(sys.argv[1])
