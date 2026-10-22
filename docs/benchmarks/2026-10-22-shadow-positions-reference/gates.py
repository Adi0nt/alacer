#!/usr/bin/env python3
"""Session 29: evaluate the reference-hardware gate series for render.reuseShadowPositions (gates.md).

Usage: python gates.py <out.json>
Reads gates/<label>.record.json and each launch's raw CSVs/JSONs under run/alacer/benchmarks. Session 28's
gates.py is the base; the differences are the ones gates.md lists (tick-free target and gate 5, frame/CPU gates
judged, fixed-pose screenshots, new labels).
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
# Half of session 28's selection estimate (19,710 and 21,474 bytes per frame); unchanged.
ESTIMATE_HALF = {'static': 9855.0, 'pan': 10737.0}
MIN_TICK_FREE_FRAMES = 1000
COUNTERS = ('visible_chunks', 'terrain_solid_chunks', 'terrain_cutout_mipped_chunks', 'terrain_cutout_chunks',
            'terrain_translucent_chunks', 'entities_drawn', 'block_entities_drawn', 'shadows_drawn')
AB = tuple(f'spr29-ab-{n}' for n in range(1, 5))
VERIFY = ('spr29-verify-d200-1-r2', 'spr29-verify-d200-2', 'spr29-verify-walk')
STARTUP = (('d200', 4, ('static', 'pan', 'fly')), ('d0', 2, ('static', 'pan', 'fly')),
           ('terrain', 2, ('steady', 'pan', 'sort', 'fly')))


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
    free = [x['alloc_bytes'] for x in r if x['tick_ns'] == 0]
    client_cpu, process_cpu, wall = cpu(folder, phases)
    alloc = statistics.fmean(x['alloc_bytes'] for x in r)
    m = {'frames': len(r), 'tick_free_frames': len(free), 'tick_frame_share': len(tick) / len(r),
         # The judged allocation metric (target region and gate 5): client-thread bytes in frames without a client tick.
         'alloc_per_tick_free_frame': statistics.fmean(free) if free else float('nan'),
         'alloc_median_tick_free_frame': statistics.median(free) if free else float('nan'),
         'alloc_per_frame': alloc, 'alloc_per_second': alloc * len(r) / wall,
         'frame_mean_ms': statistics.fmean(frame), 'p95_ms': pct(frame, 0.95), 'p99_ms': pct(frame, 0.99),
         'slow1_mean_ms': statistics.fmean(slow), 'tick_p99_ms': pct(tick, 0.99) if tick else 0.0,
         'client_cpu_ms_per_frame': client_cpu, 'process_cpu_ms_per_frame': process_cpu}
    for c in COUNTERS:
        m[c] = statistics.fmean(x[c] for x in r)
    return m


def change(a, b):
    out = {}
    for k in a:
        if k in ('frames', 'tick_free_frames', 'tick_frame_share'):
            continue
        out[k] = (b[k] / a[k] - 1.0) if a[k] else (0.0 if b[k] == 0 else float('inf'))
    out['alloc_saving_bytes'] = a['alloc_per_tick_free_frame'] - b['alloc_per_tick_free_frame']
    out['resolved'] = min(a['tick_free_frames'], b['tick_free_frames']) >= MIN_TICK_FREE_FRAMES
    return out


def med(changes, key):
    return statistics.median(c[key] for c in changes)


def screenshots():
    def load(label, phase):
        return np.asarray(Image.open(ROOT / record(label)['screenshots'][phase]['path']).convert('RGB'), dtype=np.int16)

    out = {}
    for phase in ('static', 'pan', 'fly'):
        base = load('spr29-shot-absent-1', phase)
        res = {}
        for other in ('spr29-shot-on', 'spr29-shot-absent-2'):
            d = np.abs(load(other, phase) - base)
            res[other] = {'mean_abs_rgb': float(d.mean()), 'differing_pixels_percent': float((d.max(axis=2) > 0).mean() * 100)}
        on, ref = res['spr29-shot-on'], res['spr29-shot-absent-2']
        res['pass'] = (on['mean_abs_rgb'] <= ref['mean_abs_rgb'] * 1.1 + 1e-9
                       and on['differing_pixels_percent'] <= ref['differing_pixels_percent'] * 1.1 + 0.1)
        out[phase] = res
    return out


def main(out_path):
    result = {}
    verify = [record(l) for l in VERIFY]
    compared = sum(r['shadow_positions_compared'] for r in verify)
    mismatched = sum(r['shadow_positions_mismatched'] for r in verify)
    result['verify'] = {'compared': compared, 'mismatched': mismatched,
                        'per_launch': {r['label']: r['shadow_positions_compared'] for r in verify},
                        'pass': compared >= 10_000_000 and mismatched == 0}
    result['screenshots'] = screenshots()

    ab = {}
    for label in AB:
        r = record(label)
        per = {}
        for phase in ('static', 'pan'):
            a = metrics(r['source'], (f'{phase}-a1', f'{phase}-a2'))
            b = metrics(r['source'], (f'{phase}-b1', f'{phase}-b2'))
            a1 = metrics(r['source'], (f'{phase}-a1',))
            a2 = metrics(r['source'], (f'{phase}-a2',))
            per[phase] = {'A': a, 'B': b, 'change': change(a, b),
                          'a2_over_a1_frame_drift': a2['frame_mean_ms'] / a1['frame_mean_ms'] - 1.0,
                          'b_below_a1_and_a2_tick_free': b['alloc_per_tick_free_frame'] < min(
                              a1['alloc_per_tick_free_frame'], a2['alloc_per_tick_free_frame'])}
        ab[label] = per
    result['in_process'] = ab

    startup = {}
    for scene, pairs, phases in STARTUP:
        sp = {}
        for n in range(1, pairs + 1):
            sfx = '-r2' if (scene, n) == ('d0', 2) else ''  # Amendment 2 (gates.md)
            ra, rb = record(f'spr29-st-{scene}-{n}-absent{sfx}'), record(f'spr29-st-{scene}-{n}-on{sfx}')
            sp[n] = {}
            for phase in phases:
                a, b = metrics(ra['source'], (phase,)), metrics(rb['source'], (phase,))
                sp[n][phase] = {'absent': a, 'on': b, 'change': change(a, b)}
        startup[scene] = sp
    result['startup'] = startup

    def ab_changes(phase):
        return [ab[l][phase]['change'] for l in ab]

    def st_changes(scene, phase):
        return [startup[scene][n][phase]['change'] for n in startup[scene]]

    combos = [('in-process/d200/' + p, ab_changes(p)) for p in ('static', 'pan')]
    combos += [(f'startup/{s}/{p}', st_changes(s, p)) for s, _, phases in STARTUP for p in phases]
    selecting = [(name, ch) for name, ch in combos if '/d200/' in name]
    unresolved = [name for name, ch in combos if not all(c['resolved'] for c in ch)]

    judged = {'unresolved_tick_free': unresolved}
    # Gate 1 (target region), d200 static and pan, on tick-free frames.
    for phase in ('static', 'pan'):
        ip, st = ab_changes(phase), st_changes('d200', phase)
        drifts = [abs(ab[l][phase]['a2_over_a1_frame_drift']) for l in ab]
        g = {'in_process_lower': sum(1 for c in ip if c['alloc_per_tick_free_frame'] < 0),
             'in_process_median_change': med(ip, 'alloc_per_tick_free_frame'),
             'in_process_median_saving_bytes': med(ip, 'alloc_saving_bytes'),
             'startup_median_change': med(st, 'alloc_per_tick_free_frame'),
             'startup_median_saving_bytes': med(st, 'alloc_saving_bytes'),
             'needed_saving_bytes': ESTIMATE_HALF[phase],
             'median_abs_a2_a1_drift': statistics.median(drifts),
             'b_below_a1_and_a2': sum(1 for l in ab if ab[l][phase]['b_below_a1_and_a2_tick_free'])}
        g['pass'] = (g['in_process_lower'] >= 3 and g['in_process_median_change'] <= -0.05
                     and g['in_process_median_saving_bytes'] >= ESTIMATE_HALF[phase]
                     and g['startup_median_change'] <= -0.05 and g['startup_median_saving_bytes'] >= ESTIMATE_HALF[phase]
                     and not any(n.endswith('/d200/' + phase) for n in unresolved))
        # Gate 7: with median |A2/A1 - 1| above 10%, B must also beat A1 and A2 separately in 3 of 4 launches.
        g['gate7_flagged_launches'] = [l for l in ab if abs(ab[l][phase]['a2_over_a1_frame_drift']) > 0.10]
        g['gate7_pass'] = g['median_abs_a2_a1_drift'] <= 0.10 or g['b_below_a1_and_a2'] >= 3
        judged['target_' + phase] = g

    fails = {'gate2': [], 'gate3': [], 'gate4': [], 'gate5': [], 'gate6': [], 'startup': [], 'non_selecting': []}
    # Gate 2: frame mean saving of at least 2% in at least one d200 in-process phase; no regression above 1% anywhere.
    if not any(med(ab_changes(p), 'frame_mean_ms') <= -0.02 for p in ('static', 'pan')):
        fails['gate2'].append('no d200 in-process phase saved 2% frame mean')
    for name, ch in combos:
        if med(ch, 'frame_mean_ms') > 0.01:
            fails['gate2' if '/d200/' in name else 'non_selecting'].append(name + '/frame_mean_ms')
        if med(ch, 'alloc_per_tick_free_frame') > 0.02:
            fails['gate5'].append(name)
        for c in COUNTERS:
            if abs(med(ch, c)) > 0.02:
                fails['gate6'].append(f'{name}/{c}')
    for name, ch in selecting:
        # Gate 3: tails, gate 4: CPU, in the selecting scene.
        for k in ('p95_ms', 'p99_ms', 'tick_p99_ms'):
            if med(ch, k) > 0.05:
                fails['gate3'].append(f'{name}/{k}')
        if med(ch, 'slow1_mean_ms') > 0:
            fails['gate3'].append(name + '/slow1_mean_ms')
        if med(ch, 'client_cpu_ms_per_frame') > 0:
            fails['gate4'].append(name + '/client_cpu_ms_per_frame')
        if med(ch, 'process_cpu_ms_per_frame') > 0.02:
            fails['gate4'].append(name + '/process_cpu_ms_per_frame')
        # Startup-pair conditions (medians per side) in the selecting scene.
        if name.startswith('startup/') and med(ch, 'frame_mean_ms') >= 0:
            fails['startup'].append(name + '/frame_mean_ms not lower')
    judged.update({k + '_failures': v for k, v in fails.items()})
    judged['correctness_pass'] = result['verify']['pass'] and all(v['pass'] for v in result['screenshots'].values())
    judged['pass'] = (judged['correctness_pass'] and judged['target_static']['pass'] and judged['target_pan']['pass']
                      and judged['target_static']['gate7_pass'] and judged['target_pan']['gate7_pass']
                      and not any(fails.values()))

    reported = {}
    for name, ch in combos:
        reported[name] = {k: med(ch, k) for k in ('alloc_per_frame', 'alloc_median_tick_free_frame', 'alloc_per_second',
                                                  'frame_mean_ms', 'p95_ms', 'p99_ms', 'slow1_mean_ms', 'tick_p99_ms',
                                                  'client_cpu_ms_per_frame', 'process_cpu_ms_per_frame')}
    shares = []
    for label in AB:
        for phase in ('static', 'pan'):
            shares += [ab[label][phase][side]['tick_frame_share'] for side in ('A', 'B')]
    for scene in startup:
        for n in startup[scene]:
            for phase in startup[scene][n]:
                shares += [startup[scene][n][phase][side]['tick_frame_share'] for side in ('absent', 'on')]
    reported['tick_frame_share'] = {'min': min(shares), 'median': statistics.median(shares), 'max': max(shares)}
    result['judged'] = judged
    result['reported'] = reported
    Path(out_path).write_text(json.dumps(result, indent=1) + '\n')
    print(json.dumps(judged, indent=1))
    print(json.dumps(result['verify'], indent=1))
    print(json.dumps(result['screenshots'], indent=1))
    print(json.dumps(reported['tick_frame_share'], indent=1))


if __name__ == '__main__':
    main(sys.argv[1])