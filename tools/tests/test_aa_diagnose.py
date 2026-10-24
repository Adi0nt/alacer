import sys
import os
import time
import io
import threading
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from aa_diagnose import cpu_distribution_hint, frame_bins, java_names, thread_window
from aa_thread_cpu import WindowsThreadCpu, module_at


class Attribution(unittest.TestCase):
    def test_distribution_hints_are_descriptive_and_missing_is_unknown(self):
        self.assertEqual(cpu_distribution_hint(.18, .5), 'low-client-cpu')
        self.assertEqual(cpu_distribution_hint(.6, .63), 'client-heavy')
        self.assertEqual(cpu_distribution_hint(.6, 1), 'mixed')
        self.assertEqual(cpu_distribution_hint(0, 0), 'unknown')
        self.assertEqual(cpu_distribution_hint(float('nan'), 1), 'unknown')

    def test_bins_follow_elapsed_time_and_keep_long_frames(self):
        def row(t, frame):
            return dict(start_ns=t, frame_ns=frame, render_ns=100000, swap_ns=200000,
                        tick_ns=0, visible_chunks=1374, pending_chunks=0)
        bins = frame_bins([row(1000000000, 1000000), row(1001000000, 9000000), row(6000000000, 2000000)])
        self.assertEqual([x['frames'] for x in bins], [2, 1])
        self.assertEqual(bins[0]['frame_ms'], 5)
        self.assertEqual(bins[1]['start_s'], 5)
        self.assertEqual(bins[0]['visible_chunks'], 1374)

    def test_thread_identity_gaps_boundaries_and_missing_counters(self):
        def sample(t, created, cpu):
            return dict(t=t, self_cpu_s=.01, threads=[dict(id=3, created=created, cpu_s=cpu, module='driver.dll')])
        rows = [sample(0, 1, 0), sample(1, 1, .6), sample(2, 2, 9), sample(3, 2, 9.4), sample(7, 2, 20)]
        result = thread_window(rows, 1, 7, {3: 'Client thread'})
        self.assertAlmostEqual(result['covered_s'], 2)
        self.assertAlmostEqual(result['cpu_cores']['Client thread'], .2)
        self.assertAlmostEqual(result['sampler_cpu_cores'], .01)
        self.assertEqual(thread_window([], 0, 10, {})['sampler_cpu_cores'], None)

    def test_java_ids_and_native_origin_are_distinct(self):
        self.assertEqual(java_names(['"Client thread" #1 cpu=1 nid=0x10 runnable\n']), {16: 'Client thread'})
        self.assertEqual(java_names(['"Client thread" #1 cpu=1 nid=34796 runnable\n']), {34796: 'Client thread'})
        self.assertEqual(module_at(100, [(100, 20, 'a.dll')]), 'a.dll')
        self.assertEqual(module_at(120, [(100, 20, 'a.dll')]), None)
        self.assertEqual(module_at(None, [(100, 20, 'a.dll')]), None)

    def test_partial_trace_setup_can_be_cleaned_up(self):
        from session32_diagnostic import DiagnosticRunner
        runner = DiagnosticRunner.__new__(DiagnosticRunner)
        runner.trace_stop = threading.Event()
        runner.trace_log = io.StringIO()
        # A JFR attach failure can happen after the sampler starts but before the dumper exists.
        runner.finish_trace()
        self.assertTrue(runner.trace_stop.is_set())
        self.assertTrue(runner.trace_log.closed)

    @unittest.skipUnless(os.name == 'nt', 'Windows native telemetry')
    def test_real_windows_cpu_and_start_modules(self):
        sampler = WindowsThreadCpu(os.getpid())
        before = {(r['id'], r.get('created')): r for r in sampler.snapshot() if 'cpu_s' in r}
        end = time.thread_time() + .05
        while time.thread_time() < end:
            pass
        after = sampler.snapshot()
        common = [(before[(r['id'], r['created'])], r) for r in after if (r['id'], r.get('created')) in before]
        self.assertTrue(common)
        self.assertTrue(any(b['cpu_s'] - a['cpu_s'] >= .03 for a, b in common))
        self.assertTrue(any(r.get('module') and r.get('start_status') == 0 for r in after))


if __name__ == '__main__':
    unittest.main()
