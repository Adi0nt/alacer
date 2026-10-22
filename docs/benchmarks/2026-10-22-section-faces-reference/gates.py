#!/usr/bin/env python3
"""Session 30: evaluate the reference-hardware gate series for render.fastCameraSectionFaces (gates.md).

Usage: python gates.py <out.json>
Reads gates/<label>.record.json and each launch's raw CSVs/JSONs under run/alacer/benchmarks. Session 29's gates.py
is the base: the same per-launch metrics plus terrain_setup, separate-launch pairs only, and gates.md's conditions.
"""
import csv
import json
import statistics
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent.parent
GATES = HERE / 'gates'
MIN_TICK_FREE_FRAMES = 1000
PAIRS = 4
COUNTERS = ('visible_chunks', 'terrain_solid_chunks', 'terrain_cutout_mipped_chunks', 'terrain_cutout_chunks',
            'terrain_translucent_chunks')
VERIFY = ('sf30-verify-d200', 'sf30-verify-terrain', 'sf30-verify-near', 'sf30-verify-high')
SHOTS = ('sf30-shot-off-1', 'sf30-shot-on', 'sf30-shot-off-2')
# Judged phases per scene: moving phases translate the camera; still phases never leave their block.
MOVING = {'d0': ('fly',), 'd200': ('fly',), 'terrain': ('sort', 'fly'), 'near': ('fresh-flight',), 'high': ('fresh-flight',)}
STILL = {'d0': ('static', 'pan'), 'd200': ('static', 'pan'), 'terrain': ('steady', 'pan'), 'near': ('still',),
         'high': ('still',)}
FLIGHT_COUNTER_LIMIT = 0.05
COUNTER_LIMIT = 0.02


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
         'alloc_per_tick_free_frame': statistics.fmean(free) if free else float('nan'),
         'alloc_per_frame': alloc, 'alloc_per_second': alloc * len(r) / wall,
         'frame_mean_ms': statistics.fmean(frame), 'p95_ms': pct(frame, 0.95), 'p99_ms': pct(frame, 0.99),
         'slow1_mean_ms': statistics.fmean(slow), 'tick_p99_ms': pct(tick, 0.99) if tick else 0.0,
         'terrain_setup_ms': statistics.fmean(x['terrain_setup_ns'] for x in r) / 1e6,
         'visibility_searches': statistics.fmean(x['visibility_searches'] for x in r),
         'client_cpu_ms_per_frame': client_cpu, 'process_cpu_ms_per_frame': process_cpu}
    for c in COUNTERS:
        m[c] = statistics.fmean(x[c] for x in r)
    return m


def change(off, on):
    out = {}
    for k in off:
        if k in ('frames', 'tick_free_frames', 'tick_frame_share'):
            continue
        out[k] = (on[k] / off[k] - 1.0) if off[k] else (0.0 if on[k] == 0 else float('inf'))
    out['resolved'] = min(off['tick_free_frames'], on['tick_free_frames']) >= MIN_TICK_FREE_FRAMES
    return out


def med(changes, key):
    return statistics.median(c[key] for c in changes)


def screenshots():
    import numpy as np
    from PIL import Image

    def load(label, phase):
        return np.asarray(Image.open(ROOT / record(label)['screenshots'][phase]['path']).convert('RGB'), dtype=np.int16)

    out = {}
    for phase in ('static', 'pan', 'fly'):
        base = load(SHOTS[0], phase)
        res = {}
        for other in SHOTS[1:]:
            d = np.abs(load(other, phase) - base)
            res[other] = {'mean_abs_rgb': float(d.mean()), 'differing_pixels_percent': float((d.max(axis=2) > 0).mean() * 100)}
        on, ref = res[SHOTS[1]], res[SHOTS[2]]
        res['pass'] = (on['mean_abs_rgb'] <= ref['mean_abs_rgb'] * 1.1 + 1e-9
                       and on['differing_pixels_percent'] <= ref['differing_pixels_percent'] * 1.1 + 0.1)
        out[phase] = res
    return out


def evaluate(pairs, verify, shots):
    """pairs: {scene: {phase: [change, ...]}} over PAIRS pairs. Returns the judged conditions of gates.md."""
    moving = [(s, p) for s in MOVING for p in MOVING[s]]
    still = [(s, p) for s in STILL for p in STILL[s]]
    pooled = [c for s, p in moving for c in pairs[s][p]]
    j = {}
    # 1. Region: pooled moving terrain_setup <= -3%, and lower in >= 3 of 4 pairs in every moving scene-phase.
    region_counts = {f'{s}/{p}': sum(1 for c in pairs[s][p] if c['terrain_setup_ms'] < 0) for s, p in moving}
    j['region'] = {'pooled_moving_terrain_setup': med(pooled, 'terrain_setup_ms'), 'pairs_lower': region_counts}
    j['region']['pass'] = j['region']['pooled_moving_terrain_setup'] <= -0.03 and all(n >= 3 for n in region_counts.values())
    # 2. Benefit: one frame-level saving, pooled over moving pairs.
    b = {k: med(pooled, k) for k in ('frame_mean_ms', 'slow1_mean_ms', 'p99_ms')}
    b['pass'] = b['frame_mean_ms'] <= -0.01 or b['slow1_mean_ms'] <= -0.02 or b['p99_ms'] <= -0.02
    j['benefit'] = b
    # 3. Allocation direction on tick-free frames.
    alloc_counts, unresolved = {}, []
    for s, p in moving:
        resolved = [c for c in pairs[s][p] if c['resolved']]
        if len(resolved) < len(pairs[s][p]):
            unresolved.append(f'{s}/{p}')
        alloc_counts[f'{s}/{p}'] = sum(1 for c in resolved if c['alloc_per_tick_free_frame'] < 0)
    resolved_pooled = [c for c in pooled if c['resolved']]
    j['allocation'] = {'pairs_lower': alloc_counts, 'unresolved_pairs_in': unresolved,
                       'pooled_moving_change': med(resolved_pooled, 'alloc_per_tick_free_frame') if resolved_pooled else None,
                       'pooled_moving_alloc_per_second_change': med(pooled, 'alloc_per_second')}
    j['allocation']['pass'] = (all(n >= 3 for n in alloc_counts.values()) and resolved_pooled != []
                               and j['allocation']['pooled_moving_change'] < 0)
    # 4. No regressions in any judged scene-phase; 5. still phases unchanged; C3 counters.
    regress, still_fail, counter_fail = [], [], []
    limits = {'frame_mean_ms': 0.02, 'p99_ms': 0.05, 'slow1_mean_ms': 0.05, 'tick_p99_ms': 0.05,
              'client_cpu_ms_per_frame': 0.02, 'process_cpu_ms_per_frame': 0.02}
    for s, p in moving + still:
        ch = pairs[s][p]
        for k, limit in limits.items():
            if med(ch, k) > limit:
                regress.append(f'{s}/{p}/{k}')
        counter_limit = FLIGHT_COUNTER_LIMIT if p == 'fresh-flight' else COUNTER_LIMIT
        for k in COUNTERS:
            if abs(med(ch, k)) > counter_limit:
                counter_fail.append(f'{s}/{p}/{k}')
    for s, p in still:
        ch = pairs[s][p]
        if med(ch, 'alloc_per_tick_free_frame') > 0.02:
            still_fail.append(f'{s}/{p}/alloc_per_tick_free_frame')
        if med(ch, 'terrain_setup_ms') > 0.02:
            still_fail.append(f'{s}/{p}/terrain_setup_ms')
    j['regressions'] = {'failures': regress, 'pass': not regress}
    j['still'] = {'failures': still_fail, 'pass': not still_fail}
    j['counters'] = {'failures': counter_fail, 'pass': not counter_fail}
    j['verify'] = verify
    j['screenshots'] = shots
    shots_pass = all(v['pass'] for v in shots.values())
    j['correctness_pass'] = verify['pass'] and shots_pass and j['counters']['pass']
    others = (j['region']['pass'] and j['allocation']['pass'] and j['regressions']['pass'] and j['still']['pass']
              and j['correctness_pass'])
    j['pass'] = others and j['benefit']['pass']
    j['outcome'] = ('may be promoted under these gates (separate decision)' if j['pass'] else
                    'closed: exact, no demonstrated frame-time benefit' if others else 'closed')
    return j


def main(out_path):
    verify_records = [record(l) for l in VERIFY]
    total = {k: sum(r['section_faces'][k] for r in verify_records)
             for k in ('compared', 'mismatched', 'flood_fills', 'reused_answers', 'section_reads')}
    verify = dict(total, per_launch={r['label']: r['section_faces'] for r in verify_records})
    verify['pass'] = (total['compared'] >= 5000 and total['mismatched'] == 0 and total['flood_fills'] >= 100
                      and total['reused_answers'] >= 100)
    pairs, raw = {}, {}
    for scene in MOVING:
        pairs[scene], raw[scene] = {}, {}
        for phase in MOVING[scene] + STILL[scene]:
            pairs[scene][phase], raw[scene][phase] = [], []
            for n in range(1, PAIRS + 1):
                off = metrics(record(f'sf30-st-{scene}-{n}-off')['source'], (phase,))
                on = metrics(record(f'sf30-st-{scene}-{n}-on')['source'], (phase,))
                pairs[scene][phase].append(change(off, on))
                raw[scene][phase].append({'pair': n, 'off': off, 'on': on})
    judged = evaluate(pairs, verify, screenshots())
    reported = {f'{s}/{p}': {k: med(pairs[s][p], k) for k in pairs[s][p][0] if k != 'resolved'}
                for s in pairs for p in pairs[s]}
    shares = [side['tick_frame_share'] for s in raw for p in raw[s] for r in raw[s][p] for side in (r['off'], r['on'])]
    reported['tick_frame_share'] = {'min': min(shares), 'median': statistics.median(shares), 'max': max(shares)}
    Path(out_path).write_text(json.dumps({'judged': judged, 'reported_median_changes': reported, 'pairs': raw},
                                         indent=1) + '\n')
    print(json.dumps(judged, indent=1))


if __name__ == '__main__':
    main(sys.argv[1])
