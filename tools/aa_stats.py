#!/usr/bin/env python3
"""Session 31: statistics for A/A pilots (needs numpy; the runner and monitor do not).

Unit: one pair of back-to-back launches (or two segments of one launch) with identical builds and settings. Its
change is d = ln(B / A), with A and B the nominal sides; percentages are e^d - 1. Under an A/A design the true change
is zero, so everything below describes noise, and nothing here is evidence of a performance difference.

Assumptions (stated, not hidden):
- Pairs are exchangeable within one scene and phase; scenes and phases are never assumed to share a variance.
  Pooled summaries are stratified by scene-phase and labelled as pooled.
- With balanced nominal order the null distribution of d is symmetric about 0. The sign-flip model resamples the
  observed |d| with random signs; it keeps the observed tails but cannot produce values beyond the largest seen.
- The normal model uses the robust scale as sigma. Both models are planning estimates for a future series run under
  the same conditions; neither is proof of measurement accuracy, and a resampled interval from 8 pairs is coarse.
- Time order matters: lag-1 autocorrelation and a rank trend test are reported, and an effective sample size
  n_eff = n (1 - r1) / (1 + r1) (for r1 > 0) inflates the planning numbers when pairs are correlated.
"""
import math
import statistics

import numpy as np

Z975 = 1.959963984540054
Z80 = 0.8416212335729143
MEDIAN_EFFICIENCY = 1.2533141373155  # sd(median) ~= sqrt(pi / 2) * sigma / sqrt(n) for normal noise
DEFAULT_DRAWS = 20000
GRID = (4, 6, 8, 10, 12, 16, 20, 24, 32, 48, 64, 96, 128, 192, 256, 384, 512)


def pct(d):
    return None if d is None else 100.0 * math.expm1(d)


def log_ratio(a, b):
    if a is None or b is None or not (a > 0 and b > 0):
        raise ValueError('log ratio needs two positive values: %r, %r' % (a, b))
    return math.log(b / a)


def robust_scale_zero(d):
    """1.4826 x median |d|: the per-pair spread around the A/A truth (zero), the pilot's gate statistic."""
    return 1.4826 * statistics.median(abs(x) for x in d) if d else None


def mad(d):
    """1.4826 x median |d - median(d)|: spread around the observed centre."""
    if not d:
        return None
    m = statistics.median(d)
    return 1.4826 * statistics.median(abs(x - m) for x in d)


def outliers(d, threshold=3.5):
    """Indices with modified z-score |0.6745 (d - median) / MAD| above threshold (Iglewicz-Hoaglin). Flagged only:
    an outlier is evidence about the machine, never removed from the statistic."""
    if len(d) < 3:
        return []
    m = statistics.median(d)
    raw = statistics.median(abs(x - m) for x in d)
    if raw == 0:
        spread = statistics.fmean(abs(x - m) for x in d) * 1.2533
        if spread == 0:
            return []
        return [i for i, x in enumerate(d) if abs(x - m) / spread > threshold]
    return [i for i, x in enumerate(d) if abs(0.6745 * (x - m) / raw) > threshold]


def _ranks(x):
    order = sorted(range(len(x)), key=lambda i: x[i])
    ranks = [0.0] * len(x)
    i = 0
    while i < len(order):
        j = i
        while j + 1 < len(order) and x[order[j + 1]] == x[order[i]]:
            j += 1
        for k in range(i, j + 1):
            ranks[order[k]] = (i + j) / 2.0
        i = j + 1
    return ranks


def spearman(x, y):
    """Spearman rank correlation with a permutation p-value (two-sided, fixed seed). None if undefined."""
    pairs = [(a, b) for a, b in zip(x, y) if a is not None and b is not None]
    n = len(pairs)
    if n < 4:
        return {'rho': None, 'n': n, 'p': None}
    rx, ry = np.array(_ranks([p[0] for p in pairs])), np.array(_ranks([p[1] for p in pairs]))
    if rx.std() == 0 or ry.std() == 0:
        return {'rho': None, 'n': n, 'p': None}
    rho = float(np.corrcoef(rx, ry)[0, 1])
    rng = np.random.default_rng(31)
    perms = np.array([np.corrcoef(rx, rng.permutation(ry))[0, 1] for _ in range(2000)])
    p = float((np.sum(np.abs(perms) >= abs(rho) - 1e-12) + 1) / (len(perms) + 1))
    return {'rho': rho, 'n': n, 'p': p}


def lag1(d):
    if len(d) < 4:
        return None
    x = np.asarray(d, dtype=float) - np.mean(d)
    denom = float(np.sum(x * x))
    return float(np.sum(x[1:] * x[:-1]) / denom) if denom > 0 else None


def effective_n(d):
    r = lag1(d)
    n = len(d)
    if r is None or r <= 0:
        return float(n), r
    return max(1.0, n * (1 - r) / (1 + r)), r


def normal_resolution(sigma, n, stat='median'):
    """R95 and MDE80 (log units) of the median (or mean) of n pairs with normal per-pair noise sigma."""
    se = sigma / math.sqrt(n) * (MEDIAN_EFFICIENCY if stat == 'median' else 1.0)
    return Z975 * se, (Z975 + Z80) * se


def sign_flip_resolution(d, n, draws=DEFAULT_DRAWS, seed=31, stat=np.median):
    """R95 (central 95% half-width under the null) and MDE80 of stat over n pairs, resampling |d| with random signs.
    MDE80: the smallest true shift whose shifted statistic lands beyond -R95 in 80% of draws."""
    mags = np.abs(np.asarray(d, dtype=float))
    if len(mags) == 0 or not np.any(mags):
        return 0.0, 0.0
    rng = np.random.default_rng(seed)
    sample = rng.choice(mags, size=(draws, n), replace=True) * rng.choice((-1.0, 1.0), size=(draws, n))
    s = stat(sample, axis=1)
    lo, hi, q80 = np.quantile(s, (0.025, 0.975, 0.80))
    r95 = float(max(-lo, hi))
    return r95, float(r95 + q80)


def bootstrap_ci(d, draws=DEFAULT_DRAWS, seed=31, stat=np.median, strata=None):
    """Percentile 95% interval of stat(d) resampling pairs with replacement, within strata if given. Describes the
    pilot's own estimate; with few pairs it is coarse and is not a statement of measurement accuracy."""
    d = np.asarray(d, dtype=float)
    if len(d) < 2:
        return None, None
    rng = np.random.default_rng(seed)
    if strata is None:
        s = stat(rng.choice(d, size=(draws, len(d)), replace=True), axis=1)
    else:
        strata = np.asarray(strata)
        parts = [rng.choice(d[strata == g], size=(draws, int(np.sum(strata == g))), replace=True) for g in np.unique(strata)]
        s = stat(np.concatenate(parts, axis=1), axis=1)
    lo, hi = np.quantile(s, (0.025, 0.975))
    return float(lo), float(hi)


def spread_ci(d, draws=DEFAULT_DRAWS, seed=31):
    """Bootstrap 95% interval and one-sided 80% upper bound of robust_scale_zero, to show how well 8 pairs pin it."""
    a = np.abs(np.asarray(d, dtype=float))
    if len(a) < 2:
        return None
    rng = np.random.default_rng(seed)
    s = 1.4826 * np.median(rng.choice(a, size=(draws, len(a)), replace=True), axis=1)
    lo, hi, up80 = np.quantile(s, (0.025, 0.975, 0.80))
    return {'lo_pct': pct(float(lo)), 'hi_pct': pct(float(hi)), 'upper80_pct': pct(float(up80))}


def n_needed(mde_fn, target_log, grid=GRID):
    for n in grid:
        if mde_fn(n) <= target_log:
            return n
    return None


def analyze_pairs(rows, targets_pct=(1.0, 2.0, 5.0), planned_n=16, draws=DEFAULT_DRAWS, env_keys=()):
    """rows: [{'d': log ratio, 't': start time, 'pair': id, <env key>: value, ...}] for ONE scene-phase-metric.

    Returns per-pair changes, centre and spread, outliers, time structure, environmental correlations, the observed
    centre's bootstrap interval, null resolution (sign-flip and normal), MDE80 at the pilot's n and at planned_n, and
    the pairs needed per target effect. Rows are sorted by time first.
    """
    rows = sorted(rows, key=lambda r: (r.get('t') is None, r.get('t') or 0))
    d = [r['d'] for r in rows]
    n = len(d)
    out = {'n': n, 'pairs': [{'pair': r.get('pair'), 'pct': pct(r['d']), 'log_ratio': r['d']} for r in rows]}
    if n == 0:
        return out
    sigma = robust_scale_zero(d)
    out.update({
        'median_pct': pct(statistics.median(d)), 'mean_pct': pct(statistics.fmean(d)),
        'robust_spread_pct': pct(sigma), 'mad_pct': pct(mad(d)),
        'rms_pct': pct(math.sqrt(statistics.fmean(x * x for x in d))),
        'max_abs_pct': pct(max(abs(x) for x in d)),
        'outliers': [rows[i].get('pair') for i in outliers(d)],
    })
    neff, r1 = effective_n(d)
    out['lag1_autocorrelation'] = r1
    out['effective_n'] = neff
    out['trend_vs_time'] = spearman([r.get('t') for r in rows], d)
    out['abs_change_vs_env'] = {k: spearman([r.get(k) for r in rows], [abs(x) for x in d]) for k in env_keys}
    lo, hi = bootstrap_ci(d, draws=draws)
    out['median_ci95_pct'] = [pct(lo), pct(hi)] if lo is not None else None
    out['spread_ci'] = spread_ci(d, draws=draws)
    if n >= 2:
        inflation = math.sqrt(n / neff) if neff and neff < n else 1.0
        res = {}
        for label, m in (('pilot_n', n), ('planned_n', planned_n)):
            r95_sf, mde_sf = sign_flip_resolution(d, m, draws=draws)
            r95_nm, mde_nm = normal_resolution(sigma * inflation, m)
            res[label] = {'n': m, 'sign_flip_r95_pct': pct(r95_sf), 'sign_flip_mde80_pct': pct(mde_sf),
                          'normal_r95_pct': pct(r95_nm), 'normal_mde80_pct': pct(mde_nm),
                          'mde80_pct': pct(max(mde_sf * inflation, mde_nm))}
        out['resolution'] = res
        out['time_correlation_inflation'] = inflation
        needed = {}
        for target in targets_pct:
            t = math.log1p(target / 100.0)
            needed[f'{target:g}%'] = {
                'normal': n_needed(lambda m: normal_resolution(sigma * inflation, m)[1], t),
                'sign_flip': n_needed(lambda m: sign_flip_resolution(d, m, draws=min(draws, 5000))[1] * inflation, t),
            }
        out['pairs_needed_mde80'] = needed
    return out


def pooled(per_stratum, planned_n=16, draws=DEFAULT_DRAWS):
    """Pooled summary over scene-phase strata {'scene/phase': [(pair_id, d), ...]}.

    Phases of one scene come from the same launch pairs, so they are not independent: the bootstrap resamples whole
    pairs within each scene (keeping all their phases), and the planning model counts independent units per scene
    (planned_n pairs x number of scenes), with the root-mean-square of the strata's robust sigmas. Stated
    approximation; strata differ.
    """
    names = [k for k, v in per_stratum.items() if v]
    if not names:
        return {'n': 0}
    d = [x for k in names for _, x in per_stratum[k]]
    scenes = sorted({k.split('/')[0] for k in names})
    rng = np.random.default_rng(31)
    by_scene = {}
    for k in names:
        for pair, x in per_stratum[k]:
            by_scene.setdefault(k.split('/')[0], {}).setdefault(pair, []).append(x)
    stats = []
    for _ in range(draws):
        sample = []
        for scene in scenes:
            pairs = list(by_scene[scene].values())
            for i in rng.integers(0, len(pairs), size=len(pairs)):
                sample += pairs[i]
        stats.append(np.median(sample))
    lo, hi = np.quantile(stats, (0.025, 0.975))
    sigmas = {k: robust_scale_zero([x for _, x in per_stratum[k]]) for k in names}
    rms_sigma = math.sqrt(statistics.fmean(s * s for s in sigmas.values()))
    units = planned_n * len(scenes)
    r95, mde = normal_resolution(rms_sigma, units)
    return {'n': len(d), 'strata': len(names), 'scenes': scenes, 'median_pct': pct(statistics.median(d)),
            'robust_spread_pct': pct(robust_scale_zero(d)), 'median_ci95_pct': [pct(float(lo)), pct(float(hi))],
            'strata_spread_pct': {k: pct(s) for k, s in sigmas.items()},
            'planned': {'pairs_per_scene': planned_n, 'independent_units': units, 'normal_r95_pct': pct(r95),
                        'normal_mde80_pct': pct(mde)}}
