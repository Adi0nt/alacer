"""Statistics of the A/A pilot (aa_stats.py), on synthetic data and on session 30's archived still pairs."""
import json
import math
import random
import unittest

import aa_fixtures as fx  # noqa: F401  (puts tools/ on the path)
import aa_stats as st

S30 = fx.ROOT / 'docs/benchmarks/2026-10-22-section-faces-reference/gates.json'
STILL30 = (('d0', 'static'), ('d0', 'pan'), ('d200', 'static'), ('d200', 'pan'), ('terrain', 'steady'),
           ('terrain', 'pan'), ('near', 'still'), ('high', 'still'))


def session30_still_frame_mean():
    g = json.loads(S30.read_text())
    return [math.log(r['on']['frame_mean_ms'] / r['off']['frame_mean_ms'])
            for scene, phase in STILL30 for r in g['pairs'][scene][phase]]


class SpreadAndCentre(unittest.TestCase):
    def test_robust_scale_around_zero(self):
        d = [0.01, -0.02, 0.03, -0.04, 0.05]
        self.assertAlmostEqual(st.robust_scale_zero(d), 1.4826 * 0.03)
        self.assertAlmostEqual(st.mad([1.0, 2.0, 3.0]), 1.4826 * 1.0)
        self.assertIsNone(st.robust_scale_zero([]))

    def test_reproduces_session_31_noise_floor_on_recorded_data(self):
        # The session 31 noise-floor README reports a robust scale of 16.7% for these 32 still pairs.
        d = session30_still_frame_mean()
        self.assertEqual(len(d), 32)
        self.assertAlmostEqual(st.pct(st.robust_scale_zero(d)), 16.7, delta=0.05)
        self.assertGreater(st.pct(max(abs(x) for x in d)), 160.0)

    def test_outliers_are_flagged_not_removed(self):
        d = [0.01, -0.01, 0.005, -0.004, 0.002, 0.8]
        self.assertEqual(st.outliers(d), [5])
        r = st.analyze_pairs([{'d': x, 't': i, 'pair': f'p{i}'} for i, x in enumerate(d)], draws=2000)
        self.assertEqual(r['outliers'], ['p5'])
        self.assertEqual(r['n'], 6)  # still counted
        self.assertAlmostEqual(r['max_abs_pct'], st.pct(0.8))
        # Identical values: no division by zero, no outliers.
        self.assertEqual(st.outliers([0.0, 0.0, 0.0, 0.0]), [])

    def test_log_ratio_refuses_missing_values(self):
        self.assertAlmostEqual(st.log_ratio(2.0, 2.2), math.log(1.1))
        for a, b in ((None, 1.0), (0.0, 1.0), (1.0, -1.0)):
            with self.assertRaises(ValueError):
                st.log_ratio(a, b)


class Resolution(unittest.TestCase):
    def test_normal_model_matches_the_noise_floor_planning_table(self):
        # Planning table (normal noise, sigma 3%): the median of 16 pairs resolves about +/-1.76%.
        sigma = math.log1p(0.03)
        r95, mde = st.normal_resolution(sigma, 16)
        self.assertAlmostEqual(st.pct(r95), 1.8, delta=0.1)
        self.assertAlmostEqual(mde / r95, (st.Z975 + st.Z80) / st.Z975)

    def test_sign_flip_agrees_with_normal_for_normal_data(self):
        rng = random.Random(5)
        d = [rng.gauss(0, 0.03) for _ in range(400)]
        sf, _ = st.sign_flip_resolution(d, 16, draws=20000)
        nm, _ = st.normal_resolution(st.robust_scale_zero(d), 16)
        self.assertAlmostEqual(sf, nm, delta=0.15 * nm)

    def test_more_pairs_never_need_less_and_targets_are_ordered(self):
        rng = random.Random(7)
        rows = [{'d': rng.gauss(0, 0.02), 't': i, 'pair': i} for i in range(8)]
        r = st.analyze_pairs(rows, draws=4000)
        need = r['pairs_needed_mde80']
        n1, n2, n5 = (need[k]['normal'] or 10 ** 6 for k in ('1%', '2%', '5%'))
        self.assertGreaterEqual(n1, n2)
        self.assertGreaterEqual(n2, n5)
        self.assertLess(r['resolution']['planned_n']['mde80_pct'], r['resolution']['pilot_n']['mde80_pct'])

    def test_median_resolution_tracks_the_bulk_and_resists_a_minority_of_outliers(self):
        base = [0.01, -0.012, 0.008, -0.009, 0.011, -0.01, 0.009, -0.008]
        tight = [{'d': x, 't': i} for i, x in enumerate(base)]
        broad = [{'d': 5 * x, 't': i} for i, x in enumerate(base)]
        outlying = [{'d': x, 't': i} for i, x in enumerate([0.01, -0.012, 0.6, -0.009, 0.4, -0.01, 0.009, -0.5])]
        mde = {k: st.analyze_pairs(v, draws=4000)['resolution']['planned_n']['mde80_pct']
               for k, v in (('tight', tight), ('broad', broad), ('outlying', outlying))}
        self.assertGreater(mde['broad'], 4 * mde['tight'])
        # Three wild pairs of eight move the median's resolution little; they are reported as outliers instead.
        self.assertLess(mde['outlying'], 1.5 * mde['tight'])
        self.assertEqual(len(st.analyze_pairs(outlying, draws=1000)['outliers']), 3)

    def test_session30_still_pairs_cannot_resolve_2pct_at_16_pairs(self):
        rows = [{'d': x, 't': i} for i, x in enumerate(session30_still_frame_mean())]
        r = st.analyze_pairs(rows, draws=4000)
        self.assertGreater(r['resolution']['planned_n']['mde80_pct'], 2.0)
        self.assertIsNone(r['pairs_needed_mde80']['1%']['normal'])  # beyond 512 pairs at a 16.7% spread


class TimeStructure(unittest.TestCase):
    def test_drift_reduces_effective_sample_size_and_shows_as_a_trend(self):
        d = [0.002 * i for i in range(16)]
        neff, r1 = st.effective_n(d)
        self.assertGreater(r1, 0.5)
        self.assertLess(neff, 6)
        trend = st.spearman(list(range(16)), d)
        self.assertAlmostEqual(trend['rho'], 1.0)
        self.assertLess(trend['p'], 0.01)
        r = st.analyze_pairs([{'d': x, 't': i} for i, x in enumerate(d)], draws=2000)
        self.assertGreater(r['time_correlation_inflation'], 1.5)

    def test_independent_pairs_keep_their_sample_size(self):
        d = [0.01, -0.01] * 8
        neff, r1 = st.effective_n(d)
        self.assertLess(r1, 0)
        self.assertEqual(neff, 16.0)

    def test_environmental_correlation(self):
        bg = [0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7]
        rows = [{'d': (0.01 + b / 10) * (-1) ** i, 't': i, 'background_max': b} for i, b in enumerate(bg)]
        r = st.analyze_pairs(rows, env_keys=('background_max',), draws=2000)
        c = r['abs_change_vs_env']['background_max']
        self.assertAlmostEqual(c['rho'], 1.0)
        self.assertLess(c['p'], 0.01)
        # A missing covariate is skipped, not read as zero.
        self.assertEqual(st.spearman([1, None, 3, 4, 5], [1, 2, 3, 4, 5])['n'], 4)


class Pooled(unittest.TestCase):
    def test_pooled_resamples_pairs_and_counts_scenes_not_phases(self):
        strata = {'d0/static': [('p1', 0.01), ('p2', -0.01), ('p3', 0.02), ('p4', -0.02)],
                  'd0/pan': [('p1', 0.012), ('p2', -0.008), ('p3', 0.018), ('p4', -0.02)],
                  'terrain/fly': [('q1', 0.1), ('q2', -0.1), ('q3', 0.05), ('q4', -0.05)]}
        r = st.pooled(strata, planned_n=16, draws=500)
        self.assertEqual(r['strata'], 3)
        self.assertEqual(r['scenes'], ['d0', 'terrain'])
        self.assertEqual(r['n'], 12)
        self.assertLess(r['strata_spread_pct']['d0/static'], r['strata_spread_pct']['terrain/fly'])
        # Two phases of the same pairs add no independent units: 16 pairs x 2 scenes.
        self.assertEqual(r['planned']['independent_units'], 32)
        lo, hi = r['median_ci95_pct']
        self.assertLessEqual(lo, hi)


if __name__ == '__main__':
    unittest.main()
