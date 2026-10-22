#!/usr/bin/env python3
"""Session 31: noise floor of the session 29 and 30 reference-hardware series (README.md, "Method").

Usage: python noise.py [out.json]   (default: noise.json next to this file; needs numpy)
Reads both sessions' gates.json and attempt files, read only. A new analysis of existing data: it rescores nothing.
"""
import json
import math
import statistics
import sys
from datetime import datetime
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
S29 = HERE.parent / '2026-10-22-shadow-positions-reference'
S30 = HERE.parent / '2026-10-22-section-faces-reference'
METRICS = ('frame_mean_ms', 'p95_ms', 'p99_ms', 'slow1_mean_ms', 'tick_p99_ms', 'client_cpu_ms_per_frame',
           'process_cpu_ms_per_frame', 'alloc_per_tick_free_frame', 'terrain_setup_ms')
MOVING = ('fly', 'sort', 'fresh-flight')
# Set A: session 30's still scene-phases (the option does no work there).
STILL30 = (('d0', 'static'), ('d0', 'pan'), ('d200', 'static'), ('d200', 'pan'), ('terrain', 'steady'),
           ('terrain', 'pan'), ('near', 'still'), ('high', 'still'))
# Recorded phase length in seconds: BenchmarkRunner (static 10, pan 15, fly 20), BackendScenario (steady 10, pan 15,
# sort 16, fly 20) at time scale 2; ChunkFlightScenario (still 10, fresh-flight 40) at time scale 1.
PHASE_SECONDS = {'static': 20, 'pan': 30, 'fly': 40, 'steady': 20, 'sort': 32, 'still': 10, 'fresh-flight': 40}
# Which side ran first in pair n (gates.md of each session; checked against the result-folder timestamps below).
FIRST30 = {1: 'off', 2: 'on', 3: 'on', 4: 'off'}
NS = (4, 6, 8, 12, 16, 24, 32, 48, 64, 96, 128, 192, 256)
REPORT_NS = (4, 6, 8, 12, 16, 24, 32, 48, 64)
DRAWS = 200_000
CHUNK = 20_000
SEED = 31
# Added after the first run (README, "Additions").
SIGMAS_PCT = (1, 2, 3, 4, 6, 8, 12, 17)
INDEX_METRICS = ('frame_mean_ms', 'client_cpu_ms_per_frame', 'process_cpu_ms_per_frame')


def pct(d):
    return 100.0 * math.expm1(d)


def null_stats(draws, r95_from=None):
    """Central 95% half-width (log units), its 80%-power shift and the quantiles of a statistic's null draws."""
    lo, hi, q80 = np.quantile(draws, (0.025, 0.975, 0.80))
    r95 = max(-lo, hi) if r95_from is None else r95_from
    # Smallest true (negative) shift e for which the shifted statistic lands at or below -R95 in 80% of draws.
    return r95, r95 + q80


def resolution(sampler, stat):
    """R95 and MDE80 (percent) of stat over n draws for every n in NS."""
    out = {}
    for n in NS:
        parts = [stat(sampler((CHUNK, n)), axis=1) for _ in range(DRAWS // CHUNK)]
        r95, mde = null_stats(np.concatenate(parts))
        out[n] = {'r95_pct': pct(r95), 'mde80_pct': pct(mde)}
    return out


def needed(res, limit):
    for n in NS:
        if res[n]['r95_pct'] <= limit:
            return n
    return None


def load(path):
    return json.loads(path.read_text())


def stamp(attempt):
    return attempt['record']['source'].replace('\\', '/').split('/')[-1][:15]


def launch_index(g29, g30):
    """Per launch: median over its still phases of ln(x / that scene-phase's median over every launch of the series).

    Session 30's option does no work in still phases. In session 29 the option works in d200 static and pan; its
    frame-mean effect there (medians -2.5% to +2.3%) is small against the deviations this index shows.
    """
    launches = {}
    for scene, phases in g30['pairs'].items():
        for phase, rows in phases.items():
            if phase in MOVING:
                continue
            for m in INDEX_METRICS:
                vals = {(r['pair'], s): r[s][m] for r in rows for s in ('off', 'on')}
                mid = statistics.median(vals.values())
                for (n, side), v in vals.items():
                    launches.setdefault(f'sf30-st-{scene}-{n}-{side}', {}).setdefault(m, []).append(math.log(v / mid))
    for scene, pairs in g29['startup'].items():
        for phase in pairs['1']:
            if phase in MOVING:
                continue
            for m in INDEX_METRICS:
                vals = {(n, s): pairs[n][phase][s][m] for n in pairs for s in ('absent', 'on')}
                mid = statistics.median(vals.values())
                for (n, side), v in vals.items():
                    sfx = '-r2' if (scene, n) == ('d0', '2') else ''  # Session 29 amendment 2
                    launches.setdefault(f'spr29-st-{scene}-{n}-{side}{sfx}', {}).setdefault(m, []).append(
                        math.log(v / mid))
    out = []
    for label, metrics in launches.items():
        folder = S30 if label.startswith('sf30') else S29
        out.append({'label': label, 'started': stamp(load(folder / 'attempts' / (label + '.json'))),
                    **{m + '_pct': pct(statistics.median(v)) for m, v in metrics.items()}})
    return sorted(out, key=lambda r: r['started'])


def main(out_path):
    g29, g30 = load(S29 / 'gates.json'), load(S30 / 'gates.json')
    result = {'inputs': {'session29': str(S29.name), 'session30': str(S30.name)}, 'draws': DRAWS, 'seed': SEED}

    # Launch order within session 30 pairs, from the result-folder timestamps of the records.
    order_check = []
    for scene, phases in g30['pairs'].items():
        for n in range(1, 5):
            stamps = {side: stamp(load(S30 / 'attempts' / f'sf30-st-{scene}-{n}-{side}.json')) for side in ('off', 'on')}
            order_check.append(min(stamps, key=stamps.get) == FIRST30[n])
    result['order_matches_gates_md'] = all(order_check)

    # Set A: still pairs.
    set_a = {m: [] for m in METRICS}
    second_over_first = {m: [] for m in METRICS}
    per_phase = {}
    for scene, phase in STILL30:
        rows = g30['pairs'][scene][phase]
        entry = {'seconds': PHASE_SECONDS[phase],
                 'frames_median': statistics.median(r[s]['frames'] for r in rows for s in ('off', 'on')),
                 'tick_frames_median': statistics.median(r[s]['frames'] - r[s]['tick_free_frames']
                                                         for r in rows for s in ('off', 'on'))}
        entry['tick_p99_rests_on_frames'] = entry['tick_frames_median'] / 100.0
        for m in METRICS:
            d = [math.log(r['on'][m] / r['off'][m]) for r in rows]
            set_a[m] += d
            second_over_first[m] += [x if FIRST30[r['pair']] == 'off' else -x for x, r in zip(d, rows)]
            entry[m] = {'rms_pct': pct(math.sqrt(statistics.fmean(x * x for x in d))),
                        'median4_pct': pct(statistics.median(d)), 'pairs_pct': [pct(x) for x in d]}
        per_phase[f'{scene}/{phase}'] = entry

    def spread(d):
        a = np.abs(np.asarray(d))
        return {'n': len(d), 'rms_pct': pct(math.sqrt(statistics.fmean(x * x for x in d))),
                'robust_pct': pct(1.4826 * float(np.median(a))), 'max_abs_pct': pct(float(a.max())),
                'mean_pct': pct(statistics.fmean(d))}

    result['set_a'] = {m: spread(set_a[m]) for m in METRICS}
    result['set_a_per_phase'] = per_phase
    result['order_effect'] = {m: {'mean_pct': pct(statistics.fmean(v)), 'median_pct': pct(statistics.median(v)),
                                  'second_slower_or_higher': sum(1 for x in v if x > 0), 'pairs': len(v)}
                              for m, v in second_over_first.items()}

    # Set B: between-launch spread of the off/absent side, per scene-phase, pooled per session and class.
    groups = []
    for scene, phases in g30['pairs'].items():
        for phase, rows in phases.items():
            groups.append(('s30', scene, phase, {m: [r['off'][m] for r in rows] for m in METRICS}))
    for scene, pairs in g29['startup'].items():
        for phase in pairs['1']:
            groups.append(('s29', scene, phase, {m: [pairs[n][phase]['absent'][m] for n in pairs]
                                                 for m in METRICS if m != 'terrain_setup_ms'}))
    set_b = {}
    per_group = {}
    for session in ('s30', 's29', 'both'):
        for cls in ('still', 'moving'):
            pooled = {}
            for m in METRICS:
                ss = df = 0.0
                for s, scene, phase, vals in groups:
                    if (session != 'both' and s != session) or ((phase in MOVING) != (cls == 'moving')) or m not in vals:
                        continue
                    logs = [math.log(v) for v in vals[m]]
                    ss += statistics.variance(logs) * (len(logs) - 1)
                    df += len(logs) - 1
                if df:
                    sd = math.sqrt(ss / df)
                    pooled[m] = {'df': df, 'launch_sd_pct': pct(sd), 'implied_pair_sd_pct': pct(math.sqrt(2) * sd),
                                 'sd_log': sd}
            set_b[f'{session}/{cls}'] = pooled
    for s, scene, phase, vals in groups:
        per_group[f'{s}/{scene}/{phase}'] = {m: {'launches': len(v), 'launch_sd_pct': pct(statistics.stdev(
            [math.log(x) for x in v])), 'range_pct': pct(math.log(max(v) / min(v)))} for m, v in vals.items()}
    result['set_b'] = set_b
    result['set_b_per_scene_phase'] = per_group

    # Set C: secondary checks.
    d0 = g29['startup']['d0']
    result['set_c'] = {
        's29_d0_pairs': {m: [pct(math.log1p(d0[n][p]['change'][m])) for n in d0 for p in d0[n]]
                         for m in METRICS if m != 'terrain_setup_ms'},
        's29_in_process_a2_over_a1_frame_mean_pct': {l: {p: 100.0 * g29['in_process'][l][p]['a2_over_a1_frame_drift']
                                                         for p in ('static', 'pan')} for l in g29['in_process']}}

    # Resolution of the median (and mean) of n null pair changes.
    rng = np.random.default_rng(SEED)
    res = {}
    for m in METRICS:
        a = np.asarray(set_a[m])
        entry = {}
        for label, stat in (('median', np.median), ('mean', np.mean)):
            entry[f'empirical_{label}'] = resolution(lambda size: rng.choice(a, size=size, replace=True), stat)
            for cls in ('still', 'moving'):
                b = set_b['both/' + cls].get(m) or set_b['s30/' + cls].get(m)
                if b:
                    sigma = math.sqrt(2) * b['sd_log']
                    entry[f'normal_{cls}_{label}'] = resolution(
                        lambda size, s=sigma: rng.normal(0.0, s, size=size), stat)
            # Added after the first run (README, "Additions"): set A's |d| with random signs. A null with balanced
            # order is symmetric, and this removes the sample offset that the plain resampling cannot shrink.
            mags = np.abs(a)
            entry[f'symmetric_{label}'] = resolution(
                lambda size: rng.choice(mags, size=size, replace=True) * rng.choice((-1.0, 1.0), size=size), stat)
        for key in list(entry):
            r = entry[key]
            entry[key] = {'per_n': {n: r[n] for n in REPORT_NS},
                          'n_for_r95_2pct': needed(r, 2.0), 'n_for_r95_1pct': needed(r, 1.0)}
        res[m] = entry
    result['resolution'] = res

    # Added after the first run: R95 of the median and mean of n pairs for a given per-pair spread (normal noise), to
    # state what spread a planned series needs; and a per-launch speed index showing when the spread arose.
    planning = {}
    for sigma_pct in SIGMAS_PCT:
        sigma = math.log1p(sigma_pct / 100.0)
        planning[sigma_pct] = {label: {n: v['r95_pct'] for n, v in resolution(
            lambda size, s=sigma: rng.normal(0.0, s, size=size), stat).items() if n in REPORT_NS}
            for label, stat in (('median', np.median), ('mean', np.mean))}
    result['planning_r95_by_pair_sd'] = planning
    result['launch_index'] = launch_index(g29, g30)

    # Time cost: launch durations and start-to-start overhead.
    durations = {}
    for session, folder, prefix in (('s29', S29, 'spr29-st-'), ('s30', S30, 'sf30-st-')):
        starts = []
        for path in sorted((folder / 'attempts').glob(prefix + '*.json')):
            att = load(path)
            if att.get('status') != 'passed':
                continue
            durations.setdefault(f"{session}/{att['scene']}", []).append(att['elapsed_seconds'])
            starts.append((datetime.strptime(stamp(att), '%Y%m%d-%H%M%S'), att['elapsed_seconds']))
        starts.sort()
        gaps = [(b[0] - a[0]).total_seconds() - a[1] for a, b in zip(starts, starts[1:])]
        durations[f'{session}/gap_after_launch_median_s'] = statistics.median(gaps)
    result['time'] = {k: (statistics.fmean(v) if isinstance(v, list) else v) for k, v in durations.items()}

    Path(out_path).write_text(json.dumps(result, indent=1) + '\n', newline='\n')
    summary = {m: {'set_a': result['set_a'][m],
                   'symmetric_median_r95_n4_n16_n32': [res[m]['symmetric_median']['per_n'][n]['r95_pct']
                                                       for n in (4, 16, 32)],
                   'n_for_2pct_1pct': (res[m]['symmetric_median']['n_for_r95_2pct'],
                                       res[m]['symmetric_median']['n_for_r95_1pct'])} for m in METRICS}
    print(json.dumps(summary, indent=1))


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else HERE / 'noise.json')
