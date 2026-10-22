"""Pure rules of the A/A pilot: schedule, labels, resume state, metrics, validation, calibration, pair identity."""
import csv
import json
import math
import os
import tempfile
import unittest

import aa_fixtures as fx
import aa_rules as rules

S30_GATES = fx.ROOT / 'docs/benchmarks/2026-10-22-section-faces-reference/gates'


class Schedule(unittest.TestCase):
    def setUp(self):
        self.p = fx.protocol()

    def test_order_alternates_and_balances(self):
        orders = [rules.side_order(n, self.p['pilot']['order']) for n in range(1, 9)]
        self.assertEqual(orders, ['ab', 'ba', 'ba', 'ab'] * 2)
        # Each nominal side runs first equally often, and on average at the same position in the series.
        first_a = [n for n, o in enumerate(orders) if o[0] == 'a']
        first_b = [n for n, o in enumerate(orders) if o[0] == 'b']
        self.assertEqual(len(first_a), len(first_b))
        self.assertEqual(sum(first_a), sum(first_b))
        with self.assertRaises(AssertionError):
            rules.side_order(1, ['aa'])

    def test_slots_interleave_scenes_and_labels_are_unique(self):
        slots = rules.pilot_slots(self.p['pilot'])
        self.assertEqual(slots[:4], [('d0', 1), ('terrain', 1), ('d0', 2), ('terrain', 2)])
        self.assertEqual(len(slots), 16)
        labels = {rules.side_label('aa31', s, n, side, k) for s, n in slots for side in 'ab' for k in (1, 2, 3)}
        self.assertEqual(len(labels), 16 * 2 * 3)
        self.assertEqual(rules.side_label('aa31', 'd0', 3, 'b', 2), 'aa31-pl-d0-3-b-r2')
        self.assertEqual(rules.pair_id('aa31', 'terrain', 8, 1), 'aa31-pl-terrain-8')
        for bad in ('Bad_Label', 'x y', '-x', ''):
            with self.assertRaises(ValueError):
                rules.check_label(bad)

    def test_no_label_is_a_prefix_glob_of_another_result_folder(self):
        # Result folders are '<stamp>-<label>-vis_...'; the runner matches '*-<label>-vis_*', which must not
        # match a retry's folder.
        import fnmatch
        a, a2 = rules.side_label('aa31', 'd0', 1, 'a', 1), rules.side_label('aa31', 'd0', 1, 'a', 2)
        folder_a2 = f'20261022-120000-{a2}-vis_on-ecl_on-ent0'
        self.assertFalse(fnmatch.fnmatch(folder_a2, f'*-{a}-vis_*'))
        self.assertTrue(fnmatch.fnmatch(folder_a2, f'*-{a2}-vis_*'))

    def test_expected_phases_follow_the_protocol(self):
        d0 = rules.expected_phases(self.p, 'd0')
        self.assertEqual(list(d0)[:4], ['reference-pre', 'static', 'pan', 'fly'])
        self.assertEqual(d0['static'], 60.0)
        self.assertEqual(d0['pan'], 30.0)  # 15 s x time scale 2
        self.assertEqual(list(d0)[-1], 'reference-post')
        self.assertEqual(sum(1 for k in d0 if k.startswith('repeat-')), 8)
        near = rules.expected_phases(self.p, 'near')
        self.assertEqual(near['fresh-flight'], 40.0)  # flights run at time scale 1
        bad = fx.protocol()
        bad['scenes']['d0']['phase_seconds'] = {'statc': 60}
        with self.assertRaises(ValueError):
            rules.expected_phases(bad, 'd0')


class ResumeState(unittest.TestCase):
    def setUp(self):
        self.p = fx.protocol()

    def labels(self, scene, n, k):
        return {s: rules.side_label('aa31', scene, n, s, k) for s in 'ab'}

    def test_fresh_series_starts_at_the_first_slot(self):
        s = rules.pilot_state(self.p, {}, {})
        self.assertEqual(s['next'], {'scene': 'd0', 'n': 1, 'k': 1})
        self.assertEqual({e['status'] for e in s['slots']}, {'pending'})

    def test_rejected_pairs_are_retried_after_all_first_attempts(self):
        attempts, pairs = {}, {}
        for scene, n in rules.pilot_slots(self.p['pilot'])[:3]:
            for label in self.labels(scene, n, 1).values():
                attempts[label] = {'status': 'passed'}
            pairs[rules.pair_id('aa31', scene, n, 1)] = {'status': 'rejected' if (scene, n) == ('d0', 1) else 'accepted'}
        s = rules.pilot_state(self.p, attempts, pairs)
        self.assertEqual(s['next'], {'scene': 'terrain', 'n': 2, 'k': 1})  # untouched slots first
        # Once every slot has a first decision, the retry comes with new labels.
        for scene, n in rules.pilot_slots(self.p['pilot'])[3:]:
            pairs[rules.pair_id('aa31', scene, n, 1)] = {'status': 'accepted'}
            for label in self.labels(scene, n, 1).values():
                attempts[label] = {'status': 'passed'}
        s = rules.pilot_state(self.p, attempts, pairs)
        self.assertEqual(s['next'], {'scene': 'd0', 'n': 1, 'k': 2})
        self.assertEqual(s['slots'][0]['status'], 'retry')

    def test_retry_limit_exhausts_a_slot(self):
        attempts, pairs = {}, {}
        for k in (1, 2, 3):
            pairs[rules.pair_id('aa31', 'd0', 1, k)] = {'status': 'rejected'}
            for label in self.labels('d0', 1, k).values():
                attempts[label] = {'status': 'rejected-env'}
        s = rules.pilot_state(self.p, attempts, pairs)
        self.assertEqual(s['slots'][0]['status'], 'exhausted')
        self.assertNotEqual(s['next'], {'scene': 'd0', 'n': 1, 'k': 4})

    def test_accepted_pairs_are_never_repeated(self):
        attempts = {l: {'status': 'passed'} for l in self.labels('d0', 1, 1).values()}
        pairs = {rules.pair_id('aa31', 'd0', 1, 1): {'status': 'accepted'}}
        s = rules.pilot_state(self.p, attempts, pairs)
        self.assertEqual(s['slots'][0]['status'], 'accepted')
        self.assertEqual(s['next'], {'scene': 'terrain', 'n': 1, 'k': 1})

    def test_interrupted_pair_is_undecided_until_finalized(self):
        a = self.labels('d0', 1, 1)['a']
        s = rules.pilot_state(self.p, {a: {'status': 'running'}}, {})
        self.assertEqual(s['slots'][0]['status'], 'undecided')
        self.assertEqual(s['undecided'][0]['labels'], [a])
        self.assertEqual(s['undecided'][0]['pair_id'], 'aa31-pl-d0-1')

    def test_calibration_state(self):
        attempts = {rules.calibration_label('aa31', 'd0', 1, 1): {'status': 'rejected-env'},
                    rules.calibration_label('aa31', 'd0', 1, 2): {'status': 'passed'},
                    rules.calibration_label('aa31', 'terrain', 1, 1): {'status': 'running'}}
        s = rules.calibration_state(self.p, attempts)
        self.assertEqual(s['slots'][0]['status'], 'passed')
        self.assertEqual(s['slots'][1]['status'], 'undecided')
        self.assertEqual(s['next'], {'scene': 'd0', 'i': 2, 'k': 1})


class Metrics(unittest.TestCase):
    def write_phase(self, folder, frames):
        path = os.path.join(folder, 'p.csv')
        cols = ['frame_ns', 'tick_ns', 'alloc_bytes', 'terrain_setup_ns', 'visible_chunks', 'start_ns']
        with open(path, 'w', newline='') as f:
            w = csv.writer(f)
            w.writerow(cols)
            t = 5_000_000_000
            for i, (frame, tick) in enumerate(frames):
                w.writerow([frame, tick, 1000 if tick == 0 else 50000, 10_000, 400, t])
                t += frame
        return path

    def test_phase_metrics_from_benchmark_csv(self):
        frames = [(1_000_000, 0)] * 98 + [(5_000_000, 1)] + [(9_000_000, 1)]
        with tempfile.TemporaryDirectory() as d:
            rows = rules.read_rows(self.write_phase(d, frames))
        summary = {'recorded_wall_seconds': 0.112, 'client_thread_cpu_ms_per_frame': 0.4, 'process_cpu_cores': 1.5}
        m = rules.phase_metrics(rows, summary, epoch_wall_ms=1_800_000_000_000)
        self.assertEqual(m['frames'], 100)
        self.assertEqual(m['tick_free_frames'], 98)
        self.assertAlmostEqual(m['frame_mean_ms'], (98 + 5 + 9) / 100)
        # Nearest rank exactly as sessions 29-30's gates.py: rank round(0.99 n + 0.5) with Python's rounding.
        self.assertEqual(m['p99_ms'], 9.0)
        self.assertEqual(m['p95_ms'], 1.0)
        self.assertEqual(m['slow1_mean_ms'], 9.0)
        self.assertEqual(m['tick_mean_ms'], 7.0)
        self.assertEqual(m['alloc_per_tick_free_frame'], 1000.0)
        self.assertAlmostEqual(m['process_cpu_ms_per_frame'], 1.5 * 0.112 * 1000 / 100)
        self.assertAlmostEqual(m['wall_start'], 1_800_000_005.0)
        self.assertAlmostEqual(m['wall_end'], 1_800_000_005.0 + 0.112, places=6)
        with self.assertRaises(ValueError):
            rules.phase_metrics([], summary, 0)

    def test_missing_process_cpu_is_none(self):
        rows = [{'frame_ns': 1_000_000, 'tick_ns': 0, 'alloc_bytes': 1, 'terrain_setup_ns': 0, 'visible_chunks': 1,
                 'start_ns': 0}]
        m = rules.phase_metrics(rows, {'recorded_wall_seconds': 1, 'client_thread_cpu_ms_per_frame': 1,
                                       'process_cpu_cores': -1.0}, 0)
        self.assertIsNone(m['process_cpu_ms_per_frame'])
        self.assertIsNone(m['tick_mean_ms'])

    def test_processor_split(self):
        topo = fx.topology_12700f()['value']
        s = rules.processor_split({'client_processor_frames': {'2': 90, '18': 10}, 'client_processor_unknown_frames': 0}, topo)
        self.assertEqual((s['p_core_frames'], s['e_core_frames']), (90, 10))
        self.assertAlmostEqual(s['e_core_share'], 0.1)
        off = rules.processor_split({}, topo)
        self.assertEqual(off['status'], 'unavailable')
        flat = rules.processor_split({'client_processor_frames': {'0': 5}}, {'hybrid': False})
        self.assertIn('unavailable', flat['core_class'])
        self.assertNotIn('e_core_share', flat)

    def test_archived_summaries_parse_and_identify_configuration_differences(self):
        # Previously recorded data: session 30's pair 1 of d0 (option off and on) differ only in that option.
        off = json.loads((S30_GATES / 'sf30-st-d0-1-off.summary.json').read_text())
        on = json.loads((S30_GATES / 'sf30-st-d0-1-on.summary.json').read_text())
        off2 = json.loads((S30_GATES / 'sf30-st-d0-2-off.summary.json').read_text())
        c_off, c_on, c_off2 = (s['phases']['static']['config'] for s in (off, on, off2))
        self.assertNotEqual(rules.canonical_sha(c_off), rules.canonical_sha(c_on))
        self.assertEqual([k for k in c_off if c_off[k] != c_on[k]], ['render.fastCameraSectionFaces'])
        self.assertEqual(rules.canonical_sha(c_off), rules.canonical_sha(c_off2))
        e1 = rules.comparable_environment(off['environment'], 'sf30-st-d0-1-off')
        e2 = rules.comparable_environment(off2['environment'], 'sf30-st-d0-2-off')
        # Same machine, build and settings: only volatile keys, the label and the mods' listing order differed.
        self.assertNotEqual(off['environment']['mods'], off2['environment']['mods'])
        self.assertEqual(e1, e2)


class Validation(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.p = fx.protocol()
        cls.cal, cls.cal_records = fx.calibration_for(cls.p)

    def rec(self, **kw):
        return fx.record(self.p, 'aa31-pl-d0-1-a', 'd0', 1_800_010_000.0, **kw)

    def test_calibration_sets_bands_from_its_own_launches(self):
        self.assertTrue(self.cal['pass'], self.cal['failures'])
        ref = self.cal['scenes']['d0']['reference-pre']
        self.assertEqual(ref['frame_mean_ms']['band'], 0.05)  # predeclared
        self.assertEqual(ref['client_cpu_ms_per_frame']['band'], 0.05)  # 3 x tiny spread, clamped to the minimum
        self.assertEqual(len(ref['frame_mean_ms']['values']), 4)

    def test_noisy_or_short_calibration_fails(self):
        noisy = fx.clone(self.cal_records)
        for i, r in enumerate(noisy):
            r['metrics']['reference-pre']['frame_mean_ms'] *= 1.0 + 0.2 * (i % 2)
        self.assertFalse(rules.build_calibration(self.p, noisy)['pass'])
        short = [r for r in self.cal_records if r['scene'] == 'd0'][:2] + [r for r in self.cal_records if r['scene'] == 'terrain']
        c = rules.build_calibration(self.p, short)
        self.assertFalse(c['pass'])
        self.assertIn('d0: 2 valid calibration launches, need 3', c['failures'])

    def test_clean_launch_passes(self):
        reasons, flags = rules.validate_environment(self.p, self.rec(), self.cal)
        self.assertEqual(reasons, [])
        self.assertEqual(flags, [])

    def test_reference_band(self):
        reasons, _ = rules.validate_environment(self.p, self.rec(ref_ms=1.06), self.cal)
        self.assertTrue(any('reference-pre: frame_mean_ms' in r for r in reasons))
        self.assertTrue(any('reference-post: frame_mean_ms' in r for r in reasons))
        reasons, _ = rules.validate_environment(self.p, self.rec(ref_ms=1.04), self.cal)
        self.assertFalse(any('frame_mean_ms' in r for r in reasons))

    def test_scored_values_never_change_the_reference_decision(self):
        # A launch whose scored phases are 30% slower but whose reference is normal passes: the rule reads only
        # the reference phases and the environment, so the scored A/A data cannot tune their own acceptance.
        reasons, _ = rules.validate_environment(self.p, self.rec(frame_ms=1.3, ref_ms=1.0), self.cal)
        self.assertEqual(reasons, [])

    def test_background_load(self):
        reasons, _ = rules.validate_environment(self.p, self.rec(background=0.8), self.cal)
        self.assertTrue(any('background CPU 0.80 cores (mean)' in r for r in reasons))
        reasons, _ = rules.validate_environment(self.p, self.rec(spike=((1_800_010_060.0, 1_800_010_070.0), 3.0)), self.cal)
        self.assertTrue(any('5 s peak' in r for r in reasons))

    def test_foreground_power_plan_affinity_and_e_cores(self):
        reasons, _ = rules.validate_environment(self.p, self.rec(fg_client=False), self.cal)
        self.assertTrue(any('foreground' in r and 'chrome.exe' in r for r in reasons))
        reasons, _ = rules.validate_environment(self.p, self.rec(plan='381b4222-f694-41f0-9685-ff5bb260df2e'), self.cal)
        self.assertTrue(any('power plan' in r for r in reasons))
        reasons, _ = rules.validate_environment(self.p, self.rec(affinity=list(range(20))), self.cal)
        self.assertTrue(any('client affinity' in r for r in reasons))
        reasons, _ = rules.validate_environment(self.p, self.rec(e_frames=3), self.cal)
        self.assertTrue(any('efficiency cores under the P-core affinity policy' in r for r in reasons))

    def test_power_plan_change_within_a_launch(self):
        r = self.rec()
        r['monitor']['launch']['power_plans'].append({'guid': 'a1841308-3541-4fab-bc81-f71556f20b4a', 'name': 'Power saver'})
        reasons, _ = rules.validate_environment(self.p, r, self.cal)
        self.assertTrue(any('power plan changed during the launch' in r for r in reasons))

    def test_missing_critical_telemetry_rejects_and_missing_optional_telemetry_flags(self):
        reasons, flags = rules.validate_environment(self.p, self.rec(missing=('power_plan',)), self.cal)
        self.assertTrue(any('power_plan telemetry available in 0%' in r and 'synthetic gap' in r for r in reasons))
        reasons, flags = rules.validate_environment(self.p, self.rec(missing=('frequency',)), self.cal)
        self.assertEqual(reasons, [])
        self.assertTrue(any('frequency telemetry' in f for f in flags))
        r = self.rec()
        r['processors']['static'] = {'status': 'unavailable', 'reason': 'processor sampling off'}
        reasons, _ = rules.validate_environment(self.p, r, self.cal)
        self.assertTrue(any('processor sampling unavailable' in x for x in reasons))

    def test_monitor_gap_rejects(self):
        r = self.rec()
        r['monitor']['phases']['static']['coverage'] = 0.5
        reasons, _ = rules.validate_environment(self.p, r, self.cal)
        self.assertTrue(any('static: monitor coverage 0.5' in x for x in reasons))
        del r['monitor']['phases']['pan']
        reasons, _ = rules.validate_environment(self.p, r, self.cal)
        self.assertIn('pan: no monitor window', reasons)

    def test_backend_and_configuration_must_match_calibration(self):
        reasons, _ = rules.validate_environment(self.p, self.rec(renderer='Microsoft Basic Render Driver'), self.cal)
        self.assertIn('GPU backend or platform identity differs from calibration', reasons)
        reasons, _ = rules.validate_environment(self.p, self.rec(config_change={'quality.leaves': '1'}), self.cal)
        self.assertIn('configuration differs from calibration', reasons)

    def test_moving_mobs_reject_a_still_scene(self):
        r = self.rec()
        r['mob_pose']['static'] = {'mobs_start': 200, 'mobs_end': 200, 'mobs_both': 200, 'max_move_blocks': 3.0,
                                   'max_turn_degrees': 40.0}
        r['mob_pose']['fly'] = {'mobs_start': 7, 'mobs_end': 3, 'mobs_both': 0, 'max_move_blocks': 0.0,
                                'max_turn_degrees': 0.0}
        reasons, _ = rules.validate_environment(self.p, r, self.cal)
        self.assertTrue(any('a mob moved 3.00 blocks' in x for x in reasons))
        # Mobs entering and leaving range while the camera flies is expected, not a disturbance.
        self.assertFalse(any(x.startswith('fly:') for x in reasons))

    def test_without_calibration_only_absolute_rules_apply(self):
        reasons, _ = rules.validate_environment(self.p, self.rec(ref_ms=3.0), None)
        self.assertEqual(reasons, [])
        reasons, _ = rules.validate_environment(self.p, self.rec(background=2.0), None)
        self.assertTrue(reasons)


class PairIdentity(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.p = fx.protocol()

    def pair(self, gap=10.0, **b_kw):
        a = fx.record(self.p, 'aa31-pl-d0-1-a', 'd0', 1_800_000_000.0)
        b = fx.record(self.p, 'aa31-pl-d0-1-b', 'd0', a['ended_epoch'] + gap, **b_kw)
        return a, b

    def test_identical_back_to_back_launches_pass(self):
        a, b = self.pair()
        self.assertEqual(rules.pair_identity(a, b, 420), [])

    def test_each_mismatch_is_named(self):
        cases = {'launch options differ': dict(props_identity='other'),
                 'launcher command lines differ': dict(cmdline_extra=' -XX:+UseZGC'),
                 'recorded phase configurations differ': dict(config_change={'render.fastChunkVisibility': 'false'}),
                 'exported environment differs: gl.version': dict(env_overrides={'gl.version': '4.6.0 NVIDIA 617.00'})}
        for expected, kw in cases.items():
            a, b = self.pair(**kw)
            reasons = rules.pair_identity(a, b, 420)
            self.assertTrue(any(expected in r for r in reasons), (expected, reasons))
        a, b = self.pair()
        b['jdk_sha'] = 'other'
        self.assertIn('jdk_sha differs between sides', rules.pair_identity(a, b, 420))

    def test_volatile_fields_and_the_label_are_not_differences(self):
        a, b = self.pair(env_overrides={'benchmark.joinSettleSeconds': '12.40'})
        self.assertEqual(rules.pair_identity(a, b, 420), [])
        self.assertEqual(rules.normalize_command('x -Dalacer.benchmark.label=L1 y', 'L1'),
                         rules.normalize_command('x  -Dalacer.benchmark.label=L2 y', 'L2'))

    def test_not_back_to_back(self):
        a, b = self.pair(gap=900.0)
        self.assertTrue(any('not back to back' in r for r in rules.pair_identity(a, b, 420)))
        self.assertTrue(any('not back to back' in r for r in rules.pair_identity(b, a, 420)))  # wrong order


if __name__ == '__main__':
    unittest.main()
