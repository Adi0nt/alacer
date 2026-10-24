"""Runner integration without the game: a fake launch drives the real schedule, pair decisions, rejection,
retries, resume after interruption and evaluation. Result-folder and log parsing run on synthetic benchmark output."""
import csv
import json
import math
import random
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

import aa_fixtures as fx
import aa_pilot as pilot
import aa_platform as plat
import aa_report as report
import aa_rules as rules


def make_protocol(tmp, series='aa31t', **changes):
    p = fx.protocol()
    p['series'] = series
    p['archive'] = str(tmp.relative_to(fx.ROOT))
    for k, v in changes.items():
        p[k] = v
    path = tmp.parent / (tmp.name + '-protocol.json')
    path.write_text(json.dumps(p))
    return p, path


class FakeRunner(pilot.Runner):
    """Writes attempts and records as the real launch would, with outcomes chosen per label."""

    def __init__(self, protocol, archive, outcomes=None, noise=None, frame_fn=None):
        super().__init__(protocol, archive, jdk=sys.prefix, platform=plat.Platform())
        self.outcomes = outcomes or {}
        self.noise = noise or {}
        self.frame_fn = frame_fn
        self.clock = 1_800_000_000.0
        self.rng = random.Random(3)
        self.launched = []

    def launch(self, label, stage, scene, meta=None, calibration=None, monitored=True):
        outcome = self.outcomes.get(label, 'pass')
        self.launched.append(label)
        attempt = {'label': label, 'stage': stage, 'scene': scene, **(meta or {}), 'status': 'running',
                   'started_epoch': self.clock, 'reasons': [], 'flags': []}
        path = self.archive.attempts / (label + '.json')
        assert not path.exists(), 'label reused: ' + label
        pilot.write_json(path, attempt, exclusive=True)
        if outcome == 'crash':  # The process dies without updating its attempt (power loss, kill).
            raise KeyboardInterrupt
        if outcome == 'interrupt':
            attempt.update(status='interrupted', reasons=['interrupted by the operator (KeyboardInterrupt)'])
            pilot.write_json(path, attempt)
            raise KeyboardInterrupt
        frame = self.frame_fn(label) if self.frame_fn else math.exp(self.rng.gauss(0, self.noise.get(scene, 0.01)))
        rec = fx.record(self.protocol, label, scene, self.clock, frame_ms=frame, ref_ms=1.0, stage=stage)
        self.clock = rec['ended_epoch'] + 5
        if outcome == 'fail':
            attempt.update(status='failed-checks', reasons=['client/gradle exited with 1'])
        else:
            reasons, flags = rules.validate_environment(self.protocol, rec, calibration if stage == 'pilot' else None)
            if outcome == 'reject-env':
                reasons = reasons + ['static: background CPU 0.90 cores (mean) above 0.5']
            pilot.write_json(self.archive.records / (label + '.json'), rec, exclusive=True)
            attempt.update(status='passed' if not reasons else 'rejected-env', reasons=reasons, flags=flags)
        attempt['ended_epoch'] = self.clock
        pilot.write_json(path, attempt)
        return attempt


class PilotFlow(unittest.TestCase):
    def setUp(self):
        (fx.ROOT / 'build').mkdir(exist_ok=True)
        self.tmp = Path(tempfile.mkdtemp(prefix='aa-test-', dir=fx.ROOT / 'build'))
        self.archive_dir = self.tmp / 'archive'
        self.protocol, self.protocol_path = make_protocol(self.archive_dir)
        self.archive = pilot.Archive(self.protocol, self.protocol_path)
        self.archive.ensure()

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)
        Path(str(self.archive_dir) + '-protocol.json').unlink(missing_ok=True)

    def runner(self, frozen=True, **kw):
        r = FakeRunner(self.protocol, pilot.Archive(self.protocol, self.protocol_path), **kw)
        if frozen:
            r.manifest = {'synthetic': True}  # the fake launch does not check hashes
        return r

    def test_calibration_and_pilot_refuse_before_the_freeze(self):
        for stage in ('run_calibration', 'run_pilot'):
            with self.assertRaises(pilot.CheckFailed):
                getattr(self.runner(frozen=False), stage)()
        self.assertFalse(self.archive.calibration.exists())

    def calibrate(self, **kw):
        r = self.runner(**kw)
        r.run_calibration()
        cal = json.loads(self.archive.calibration.read_text())
        self.assertTrue(cal['pass'], cal['failures'])
        return r

    def test_pilot_requires_a_passing_calibration(self):
        with self.assertRaises(pilot.CheckFailed):
            self.runner().run_pilot()

    def test_calibration_is_written_once_and_retries_failed_launches(self):
        r = self.calibrate(outcomes={'aa31t-cal-d0-2': 'fail'})
        self.assertIn('aa31t-cal-d0-2-r2', r.launched)
        cal = json.loads(self.archive.calibration.read_text())
        self.assertEqual(len(cal['scenes']['d0']['launches']), 4)
        before = self.archive.calibration.read_bytes()
        self.runner().run_calibration()
        self.assertEqual(self.archive.calibration.read_bytes(), before)

    def test_rejected_side_rejects_the_whole_pair_and_it_is_repeated_at_the_end(self):
        self.calibrate()
        # Pair 3 runs B first (order ab, ba, ba, ab); its B side is rejected, so A never runs.
        r = self.runner(outcomes={'aa31t-pl-d0-3-b': 'reject-env'})
        r.run_pilot()
        pl = [l for l in r.launched if '-pl-' in l]
        self.assertNotIn('aa31t-pl-d0-3-a', pl)
        self.assertEqual(pl[-2:], ['aa31t-pl-d0-3-b-r2', 'aa31t-pl-d0-3-a-r2'])
        pairs = r.pairs()
        self.assertEqual(pairs['aa31t-pl-d0-3']['status'], 'rejected')
        self.assertIn('aa31t-pl-d0-3-b rejected-env', pairs['aa31t-pl-d0-3']['reasons'][0])
        self.assertEqual(pairs['aa31t-pl-d0-3-r2']['status'], 'accepted')
        self.assertEqual(sum(1 for p in pairs.values() if p['status'] == 'accepted'), 16)
        # Every attempt is kept.
        self.assertTrue((self.archive.attempts / 'aa31t-pl-d0-3-b.json').exists())

    def test_passed_side_of_a_rejected_pair_is_never_used(self):
        self.calibrate()
        # Pair 1 runs A then B: A passes, B fails. A's record exists but must not enter any statistic.
        r = self.runner(outcomes={'aa31t-pl-d0-1-b': 'fail'})
        r.run_pilot()
        self.assertEqual(r.attempts()['aa31t-pl-d0-1-a']['status'], 'passed')
        rep = report.evaluate(self.archive.root, draws=500)
        pairs = [p['pair'] for p in rep['results']['d0']['static']['frame_mean_ms']['pairs']]
        self.assertNotIn('aa31t-pl-d0-1', pairs)
        self.assertIn('aa31t-pl-d0-1-r2', pairs)
        self.assertEqual(len(pairs), 8)

    def test_a_tampered_acceptance_is_refused(self):
        self.calibrate()
        r = self.runner(outcomes={'aa31t-pl-d0-1-b': 'reject-env'})
        r.run_pilot()
        path = self.archive.pairs / 'aa31t-pl-d0-1.json'
        p = json.loads(path.read_text())
        p.update(status='accepted', reasons=[])
        path.write_text(json.dumps(p))
        with self.assertRaises((AssertionError, KeyError)):
            report.evaluate(self.archive.root, draws=200)

    def test_interruption_mid_pair_rejects_and_reschedules_it(self):
        self.calibrate()
        with self.assertRaises(KeyboardInterrupt):
            self.runner(outcomes={'aa31t-pl-d0-2-a': 'interrupt'}).run_pilot()
        # d0-2 runs B then A: B passed, A was interrupted. Nothing decided the pair yet.
        r = self.runner()
        state = rules.pilot_state(self.protocol, r.attempts(), r.pairs())
        self.assertEqual(state['slots'][2]['status'], 'undecided')
        r.run_pilot()
        pairs = r.pairs()
        self.assertEqual(pairs['aa31t-pl-d0-2']['status'], 'rejected')
        self.assertTrue(pairs['aa31t-pl-d0-2']['decided_on_resume'])
        self.assertEqual(pairs['aa31t-pl-d0-2-r2']['status'], 'accepted')
        # Accepted pairs from before the interruption are not repeated.
        self.assertEqual(r.launched.count('aa31t-pl-d0-1-a'), 0)
        self.assertEqual(sum(1 for p in pairs.values() if p['status'] == 'accepted'), 16)

    def test_hard_crash_leaves_a_running_attempt_that_resume_marks_interrupted(self):
        self.calibrate()
        with self.assertRaises(KeyboardInterrupt):
            self.runner(outcomes={'aa31t-pl-terrain-1-a': 'crash'}).run_pilot()
        self.assertEqual(json.loads((self.archive.attempts / 'aa31t-pl-terrain-1-a.json').read_text())['status'], 'running')
        r = self.runner()
        r.run_pilot(max_pairs=1)
        a = r.attempts()['aa31t-pl-terrain-1-a']
        self.assertEqual(a['status'], 'interrupted')
        self.assertIn('found unfinished when the runner resumed', a['reasons'])
        self.assertEqual(r.pairs()['aa31t-pl-terrain-1']['status'], 'rejected')
        self.assertIn('aa31t-pl-terrain-1-a interrupted: found unfinished when the runner resumed',
                      r.pairs()['aa31t-pl-terrain-1']['reasons'])


    def test_one_side_complete_and_the_other_never_started_is_rejected(self):
        self.calibrate()
        r = self.runner()
        r.launch('aa31t-pl-d0-1-a', 'pilot', 'd0', {'pair_id': 'aa31t-pl-d0-1'},
                 calibration=json.loads(self.archive.calibration.read_text()))
        r2 = self.runner()
        r2.run_pilot(max_pairs=0)
        d = r2.pairs()['aa31t-pl-d0-1']
        self.assertEqual(d['status'], 'rejected')
        self.assertIn('aa31t-pl-d0-1-b never ran (pair incomplete)', d['reasons'])

    def test_retry_limit_and_unresolved_gate(self):
        self.calibrate()
        outcomes = {rules.side_label('aa31t', 'terrain', 5, 'a', k): 'fail' for k in (1, 2, 3)}
        r = self.runner(outcomes=outcomes)
        r.run_pilot()
        self.assertNotIn('aa31t-pl-terrain-5-a-r4', r.launched)
        state = rules.pilot_state(self.protocol, r.attempts(), r.pairs())
        self.assertEqual(next(s for s in state['slots'] if (s['scene'], s['n']) == ('terrain', 5))['status'], 'exhausted')
        rep = report.evaluate(self.archive.root, draws=300)
        self.assertEqual(rep['verdict']['stability_gate']['status'], 'unresolved')
        self.assertTrue(any('exhausted' in i for i in rep['verdict']['validity']['issues']))

    def test_max_pairs_splits_a_series_into_sittings(self):
        self.calibrate()
        r = self.runner()
        r.run_pilot(max_pairs=3)
        self.assertEqual(len(r.pairs()), 3)
        self.runner().run_pilot()
        self.assertEqual(len(self.runner().pairs()), 16)

    def test_evaluation_distinguishes_gate_power_and_validity(self):
        self.calibrate()
        self.runner(noise={'d0': 0.004, 'terrain': 0.08}).run_pilot()
        rep = report.evaluate(self.archive.root, draws=1000)
        rows = {(g['scene'], g['phase']): g for g in rep['verdict']['stability_gate']['rows']}
        self.assertEqual(rows[('d0', 'static')]['status'], 'pass')
        self.assertEqual(rows[('terrain', 'steady')]['status'], 'fail')
        self.assertEqual(rep['verdict']['stability_gate']['status'], 'fail')
        power = {(p['scene'], p['phase']): p['status'] for p in rep['verdict']['power_2pct']['rows']}
        self.assertEqual(power[('d0', 'static')], 'yes')
        self.assertEqual(power[('terrain', 'steady')], 'no')
        self.assertEqual(rep['verdict']['validity']['status'], 'no disturbance detected by the predeclared rules')
        md = report.markdown(rep, self.protocol)
        self.assertIn('**Stability gate: fail.**', md)
        self.assertIn('| d0/static | 8 |', md)
        # The pilot's own pairs supply the statistics; the reference pairs are reported too (not gated).
        self.assertIn('reference-pre', rep['results']['d0'])
        self.assertEqual(rep['in_process']['d0']['blocks'], 32)  # 16 launches x 2 ABBA blocks of 8 segments

    def test_gate_passing_does_not_imply_power(self):
        self.calibrate()
        # Prescribed pair changes of about +/-1.95%: a robust spread of 2.9% passes the 3% gate, but the median of
        # 16 such pairs cannot resolve 2% with 80% power.
        mags = [0.018, 0.0195, 0.0195, 0.021, 0.019, 0.0195, 0.02, 0.0195]

        def frame(label):
            n = int(label.split('-')[3])
            return math.exp((-1) ** n * mags[n - 1]) if label.split('-')[4] == 'b' else 1.0

        self.runner(frame_fn=frame).run_pilot()
        rep = report.evaluate(self.archive.root, draws=2000)
        v = rep['verdict']
        self.assertEqual(v['stability_gate']['status'], 'pass')
        self.assertTrue(all(r['status'] == 'no' for r in v['power_2pct']['rows']))
        # Pooled over d0 and terrain (32 independent pairs at the planned size) it would resolve 2%: reported as
        # such, never as per-scene power.
        self.assertEqual(v['power_2pct']['status'], 'pooled only')
        row = v['power_2pct']['rows'][0]
        self.assertGreater(row['mde80_pct'], 2.0)
        self.assertGreater(row['pairs_needed']['2%']['normal'], 16)


class ArchiveGuards(unittest.TestCase):
    def test_refuses_historical_archives_and_replaced_protocols(self):
        p = fx.protocol()
        p['archive'] = 'docs/benchmarks/2026-10-22-section-faces-reference'
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / 'p.json'
            path.write_text(json.dumps(p))
            with self.assertRaises(SystemExit):
                pilot.Archive(p, path)
            p['archive'] = '../outside'
            path.write_text(json.dumps(p))
            with self.assertRaises(SystemExit):
                pilot.Archive(p, path)
        # The real archive accepts only its own protocol file.
        real = fx.protocol()
        with tempfile.TemporaryDirectory() as d:
            changed = dict(real, limits=dict(real['limits'], background_cores_mean=5.0))
            path = Path(d) / 'p.json'
            path.write_text(json.dumps(changed))
            with self.assertRaises(SystemExit):
                pilot.Archive(changed, path)
        pilot.Archive(real, fx.PROTOCOL_PATH)

    def test_label_reuse_is_refused_before_anything_runs(self):
        (fx.ROOT / 'build').mkdir(exist_ok=True)
        tmp = Path(tempfile.mkdtemp(prefix='aa-test-', dir=fx.ROOT / 'build'))
        try:
            protocol, path = make_protocol(tmp / 'archive')
            archive = pilot.Archive(protocol, path)
            archive.ensure()
            pilot.write_json(archive.attempts / 'aa31t-pl-d0-1-a.json', {'status': 'passed'})
            runner = pilot.Runner(protocol, archive, jdk=sys.prefix, platform=plat.Platform())
            with self.assertRaises(SystemExit):
                runner.launch('aa31t-pl-d0-1-a', 'pilot', 'd0')
            self.assertEqual(json.loads((archive.attempts / 'aa31t-pl-d0-1-a.json').read_text()), {'status': 'passed'})
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
            Path(str(tmp / 'archive') + '-protocol.json').unlink(missing_ok=True)

    def test_both_sides_get_identical_options(self):
        p = fx.protocol()
        a = pilot.explicit_props(p, 'terrain', 'aa31-pl-terrain-1-a')
        b = pilot.explicit_props(p, 'terrain', 'aa31-pl-terrain-1-b')
        self.assertEqual(pilot.props_identity(a), pilot.props_identity(b))
        self.assertEqual({k for k in a if a[k] != b[k]}, {'benchmark.label'})
        self.assertEqual(a['benchmark.phaseSeconds'], 'steady:60')
        self.assertEqual(a['render.fastCameraSectionFaces'], 'false')
        self.assertEqual(a['render.reuseShadowPositions.verify'], 'false')
        self.assertNotIn('benchmark.toggle', a)  # nothing toggled in an A/A pilot
        self.assertEqual(a['benchmark.processorSampling'], 'true')
        cmd = pilot.launch_command(p, a, '/jdk', None)
        self.assertIn('-Palacer.benchmark.label=aa31-pl-terrain-1-a', cmd)


class ResultParsing(unittest.TestCase):
    """build_record and check_log on a synthetic result folder shaped like BenchmarkRunner's output."""

    def setUp(self):
        (fx.ROOT / 'build').mkdir(exist_ok=True)
        self.tmp = Path(tempfile.mkdtemp(prefix='aa-test-', dir=fx.ROOT / 'build'))
        self.protocol, path = make_protocol(self.tmp / 'archive')
        self.archive = pilot.Archive(self.protocol, path)
        self.archive.ensure()
        self.bench = self.tmp / 'benchmarks'
        self.saved = pilot.BENCH
        pilot.BENCH = self.bench
        self.runner = pilot.Runner(self.protocol, self.archive, jdk=sys.prefix, platform=plat.Platform())
        self.label = 'aa31t-pl-d0-1-a'
        self.props = pilot.explicit_props(self.protocol, 'd0', self.label)

    def tearDown(self):
        pilot.BENCH = self.saved
        shutil.rmtree(self.tmp, ignore_errors=True)
        Path(str(self.tmp / 'archive') + '-protocol.json').unlink(missing_ok=True)

    def write_result(self, mutate=None):
        folder = self.bench / f'20261022-120000-{self.label}-vis_on-ecl_on-ent0'
        folder.mkdir(parents=True)
        config = {'render.' + k: 'true' for k in pilot.s28.ON}
        config.update({'render.' + k: 'false' for k in pilot.s28.OFF + pilot.LATER_OFF})
        config.update({'render.' + k + '.verify': 'false' for k in pilot.s28.ON + pilot.s28.OFF + pilot.LATER_OFF})
        phases = {}
        t = 1_000_000_000
        for name, seconds in rules.expected_phases(self.protocol, 'd0').items():
            with open(folder / (name + '.csv'), 'w', newline='') as f:
                w = csv.writer(f)
                w.writerow(['frame_ns', 'tick_ns', 'alloc_bytes', 'terrain_setup_ns', 'visible_chunks', 'start_ns'])
                for i in range(200):
                    w.writerow([1_000_000, 1 if i % 50 == 0 else 0, 2000, 5000, 300, t])
                    t += 1_000_000
            t += 3_000_000_000
            phases[name] = {'config': config, 'frames': 200, 'discarded_setup_frames': 1, 'target_seconds': seconds,
                            'recorded_wall_seconds': 0.2, 'dropped_frames': 0, 'client_thread_cpu_ms_per_frame': 0.4,
                            'process_cpu_cores': 1.4, 'client_processor_frames': {'3': 200},
                            'client_processor_unknown_frames': 0}
        env = {'java.runtime.version': '25.0.4.1+1-LTS', 'java.vendor': 'Eclipse Adoptium',
               'gl.renderer': 'NVIDIA GeForce RTX 3060 Ti/PCIe/SSE2', 'window': '1920x1080', 'vboActive': 'false',
               'alacer.configSource': 'build-defaults-and-jvm-overrides', 'benchmark.generationRun': 'false',
               'options.renderDistance': '12', 'options.vsync': 'false', 'benchmark.mobs': '0', 'benchmark.chunkFlight': '',
               'benchmark.toggle': '', 'benchmark.processorSampling': 'true', 'benchmark.mobPoseCheck': 'true',
               'benchmark.freezeMobs': 'true',
               'profiler.allocationAvailable': 'true', 'benchmark.processorSource': 'windows:GetCurrentProcessorNumberEx',
               'profiler.epochWallMillis': '1800000000000'}
        summary = {'label': self.label, 'environment': env, 'config': config, 'phases': phases}
        if mutate:
            mutate(summary)
        (folder / 'summary.json').write_text(json.dumps(summary))
        return folder

    def log(self, text=None):
        path = self.tmp / 'x.log'
        path.write_text(text if text is not None else
                        '> Task :compileJava UP-TO-DATE\n> Task :processResources UP-TO-DATE\n'
                        'Camera section faces final: 0 section reads, 0 flood fills, 0 reused answers; 0 compared with vanilla, 0 mismatched\n'
                        'Benchmark: results written to x\nBUILD SUCCESSFUL in 2m\n')
        return path

    def build(self, log=None):
        hardware = {'static': {}, 'topology': fx.topology_12700f()}
        attempt = {'started_epoch': 1.0, 'props_identity': pilot.props_identity(self.props), 'hardware_sha': 'h'}
        return self.runner.build_record(self.label, 'pilot', 'd0', self.props, attempt, {'cmdline': ''}, None, hardware,
                                        fx.P_CORES, log or self.log())

    def test_valid_output_parses_into_metrics_and_processor_placement(self):
        self.write_result()
        rec = self.build()
        self.assertEqual(list(rec['metrics']), list(rules.expected_phases(self.protocol, 'd0')))
        self.assertAlmostEqual(rec['metrics']['static']['frame_mean_ms'], 1.0)
        self.assertEqual(rec['processors']['static']['e_core_frames'], 0)
        self.assertEqual(rec['processors']['static']['p_core_frames'], 200)
        self.assertIsNone(rec['monitor'])

    def test_each_runner_check(self):
        cases = {
            'dropped': lambda s: s['phases']['static'].update(dropped_frames=3),
            'missing phase': lambda s: s['phases'].pop('repeat-04'),
            'duration': lambda s: s['phases']['static'].update(target_seconds=20.0),
            'renderer': lambda s: s['environment'].update({'gl.renderer': 'llvmpipe (LLVM 19.1.1, 256 bits)'}),
            'experiment on': lambda s: s['phases']['pan']['config'].update({'render.reuseShadowPositions': 'true'}),
            'verify on': lambda s: s['phases']['pan']['config'].update({'render.cacheClouds.verify': 'true'}),
            'generation run': lambda s: s['environment'].update({'benchmark.generationRun': 'true'}),
            'sampling unavailable': lambda s: s['environment'].update({'benchmark.processorSource': 'unavailable: x'}),
        }
        for name, mutate in cases.items():
            for p in self.bench.glob('*'):
                shutil.rmtree(p)
            self.write_result(lambda s, m=mutate: (m(s), s)[1])
            with self.assertRaises(pilot.CheckFailed, msg=name):
                self.build()

    def test_log_checks(self):
        self.write_result()
        good = self.log().read_text()
        for name, text in {'mismatch': good.replace('0 mismatched', '2 mismatched'),
                           'compiled': good.replace('> Task :compileJava UP-TO-DATE', '> Task :compileJava'),
                           'gl error': good + 'GL ERROR 1282\n', 'unfinished': good.replace('Benchmark: results written to x', ''),
                           'abort': good + 'Benchmark aborted: x\n'}.items():
            with self.assertRaises(pilot.CheckFailed, msg=name):
                self.runner.check_log(text, 'd0')
        with self.assertRaises(pilot.CheckFailed):
            self.runner.check_log(good, 'near')  # flights must log the byte-identical world copy
        self.runner.check_log(good, 'd0')

    def test_series_binning(self):
        rows = [{'t': float(i), 'busy_pct': float(i), 'fg_client': True} for i in range(12)]
        b = pilot.bin_series(rows, 5.0)
        self.assertEqual([r['t'] for r in b], [0.0, 5.0, 10.0])
        self.assertEqual(b[0]['busy_pct'], 2.0)
        self.assertTrue(b[0]['fg_client'])


if __name__ == '__main__':
    unittest.main()
