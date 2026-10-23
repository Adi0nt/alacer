"""Quiet-window regressions and real Windows API integration, without launching a game."""
import json
import math
import os
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

import aa_fixtures as fx
import aa_monitor as mon
import aa_pilot as pilot
import aa_platform as plat


class QuietWindow(unittest.TestCase):
    def setUp(self):
        self.protocol = fx.protocol()
        self.protocol['monitor'].update(pre_launch_quiet_s=3, quiet_wait_max_s=7, interval_s=1)
        self.runner = pilot.Runner(self.protocol, SimpleNamespace(manifest=fx.ROOT / 'build/nonexistent-manifest.json'),
                                   sys.prefix, platform=plat.Platform())

    def run_window(self, values, timestamps=None, gaps=()):
        clock = [0.0]
        monitor = SimpleNamespace(samples=[], mark=Mock())

        def sleep(seconds):
            clock[0] += seconds
            i = int(clock[0]) - 1
            if i in gaps:
                return
            value = values[min(i, len(values) - 1)]
            reading = value if isinstance(value, dict) else plat.ok({'background_cores': value}, 'test')
            stamp = timestamps[min(i, len(timestamps) - 1)] if timestamps else 100 + clock[0]
            monitor.samples.append({'t': stamp, 'cpu': reading})

        self.monitor = monitor
        with patch.object(pilot.time, 'monotonic', side_effect=lambda: clock[0]), \
                patch.object(pilot.time, 'time', side_effect=lambda: 100 + clock[0]), \
                patch.object(pilot.time, 'sleep', side_effect=sleep):
            return self.runner.wait_quiet(monitor)

    def test_fresh_quiet_window_passes(self):
        self.assertEqual(self.run_window([0.1]), 3)
        self.assertEqual(self.monitor.mark.call_args.args[0], 'quiet-window-passed')

    def test_noisy_timeout_fails_closed(self):
        with self.assertRaisesRegex(pilot.CheckFailed, 'quiet window expired'):
            self.run_window([2.0])
        self.assertEqual(self.monitor.mark.call_args.args[0], 'quiet-wait-expired')

    def test_temporary_missing_telemetry_requires_a_new_complete_window(self):
        self.assertEqual(self.run_window([0.1, plat.error(OSError('probe failed')), 0.1]), 5)

    def test_missing_or_invalid_values_cannot_pass(self):
        for value in (None, math.nan, math.inf, -1, plat.unavailable('missing'), plat.ok({}, 'test')):
            with self.subTest(value=value), self.assertRaises(pilot.CheckFailed):
                self.run_window([value])

    def test_stale_and_duplicate_samples_cannot_pass(self):
        for stamps in ([50], [101], [110]):
            with self.subTest(stamps=stamps), self.assertRaises(pilot.CheckFailed):
                self.run_window([0.0], timestamps=stamps)

    def test_telemetry_gap_requires_a_new_contiguous_window(self):
        self.assertEqual(self.run_window([0.1], gaps=(1,)), 5)

    def test_timeout_wins_over_quiet_samples_at_deadline(self):
        self.protocol['monitor']['quiet_wait_max_s'] = 3
        with self.assertRaises(pilot.CheckFailed):
            self.run_window([0.0])

    def test_nonintegral_window_rounds_up(self):
        self.protocol['monitor']['pre_launch_quiet_s'] = 3.2
        self.assertEqual(self.run_window([0.0]), 4)

    def test_launch_timeout_preserves_attempt_without_starting_client(self):
        with tempfile.TemporaryDirectory(dir=fx.ROOT / 'build', prefix='aa-quiet-') as tmp:
            root = Path(tmp)
            archive = SimpleNamespace(attempts=root / 'attempts', records=root / 'records', raw=root / 'raw',
                                      protocol_file=fx.PROTOCOL_PATH)
            platform = Mock()
            platform.find_clients.return_value = []
            runner = pilot.Runner(self.protocol, SimpleNamespace(manifest=root / 'manifest.json'), sys.prefix,
                                  platform=platform)
            runner.archive = archive
            runner.state_hashes = Mock(return_value={})
            runner.expected_affinity = Mock(return_value=list(range(16)))
            runner.wait_quiet = Mock(side_effect=pilot.CheckFailed('quiet window expired'))
            monitor = Mock(errors=[])
            with patch.object(pilot.mon, 'Monitor', return_value=monitor), \
                    patch.object(pilot.plat, 'NvidiaSmi'), patch.object(pilot.subprocess, 'Popen') as popen:
                result = runner.launch('aa-quiet-test', 'smoke', 'd0')
            popen.assert_not_called()
            self.assertEqual(result['status'], 'failed-checks')
            self.assertNotIn('launch_epoch', result)
            self.assertIn('quiet_wait_s', result)
            self.assertEqual(json.loads((archive.attempts / 'aa-quiet-test.json').read_text())['status'], 'failed-checks')


class TelemetryFailures(unittest.TestCase):
    def test_windows_atomic_replace_retries_without_exposing_partial_json(self):
        with tempfile.TemporaryDirectory(dir=fx.ROOT / 'build', prefix='aa-write-') as tmp:
            path = Path(tmp) / 'attempt.json'
            pilot.write_json(path, {'status': 'before'})
            replace = pilot.os.replace
            tries = []
            def transient(source, target):
                tries.append(1)
                if len(tries) < 3:
                    self.assertEqual(json.loads(path.read_text()), {'status': 'before'})
                    raise PermissionError('temporary reader lock')
                return replace(source, target)
            with patch.object(pilot.plat, 'WINDOWS', True), patch.object(pilot.os, 'replace', side_effect=transient), \
                    patch.object(pilot.time, 'sleep') as sleep:
                pilot.write_json(path, {'status': 'after'})
            self.assertEqual(len(tries), 3)
            self.assertEqual(sleep.call_count, 2)
            self.assertEqual(json.loads(path.read_text()), {'status': 'after'})
            self.assertFalse(path.with_suffix('.json.tmp').exists())

    def test_permanent_atomic_replace_denial_fails_closed(self):
        with tempfile.TemporaryDirectory(dir=fx.ROOT / 'build', prefix='aa-write-') as tmp:
            path = Path(tmp) / 'attempt.json'
            pilot.write_json(path, {'status': 'before'})
            with patch.object(pilot.plat, 'WINDOWS', True), \
                    patch.object(pilot.os, 'replace', side_effect=PermissionError('locked')) as replace, \
                    patch.object(pilot.time, 'sleep'):
                with self.assertRaises(PermissionError):
                    pilot.write_json(path, {'status': 'after'})
            self.assertEqual(replace.call_count, 6)
            self.assertEqual(json.loads(path.read_text()), {'status': 'before'})
            self.assertEqual(json.loads(path.with_suffix('.json.tmp').read_text()), {'status': 'after'})

    def test_non_windows_replace_denial_is_not_retried(self):
        with tempfile.TemporaryDirectory(dir=fx.ROOT / 'build', prefix='aa-write-') as tmp:
            with patch.object(pilot.plat, 'WINDOWS', False), \
                    patch.object(pilot.os, 'replace', side_effect=PermissionError('denied')) as replace, \
                    patch.object(pilot.time, 'sleep') as sleep:
                with self.assertRaises(PermissionError):
                    pilot.write_json(Path(tmp) / 'attempt.json', {})
            replace.assert_called_once()
            sleep.assert_not_called()

    def test_fresh_windows_series_preserves_all_numeric_controls(self):
        old = fx.protocol()
        new = json.loads((fx.PROTOCOL_PATH.parent / 'windows2/protocol.json').read_text())
        for key in ('series', 'archive', 'purpose'):
            old.pop(key)
            new.pop(key)
        self.assertEqual(new['calibration'].pop('configuration_scope'), 'scene')
        self.assertEqual(old, new)

    def test_legacy_smoke_without_environmental_decision_cannot_authorize_freeze(self):
        with tempfile.TemporaryDirectory(dir=fx.ROOT / 'build', prefix='aa-freeze-') as tmp:
            archive = SimpleNamespace(manifest=Path(tmp) / 'manifest.json', attempts=Path(tmp),
                load=lambda _folder: {'d0': {'scene': 'd0', 'status': 'passed-smoke'}})
            platform = Mock()
            platform.find_clients.return_value = []
            runner = pilot.Runner(fx.protocol(), archive, sys.prefix, platform=platform)
            with self.assertRaisesRegex(pilot.CheckFailed, 'passing smoke launch of d0'):
                runner.freeze()
            self.assertFalse(archive.manifest.exists())

    def test_environmentally_rejected_smoke_cannot_authorize_freeze(self):
        with tempfile.TemporaryDirectory(dir=fx.ROOT / 'build', prefix='aa-freeze-') as tmp:
            archive = SimpleNamespace(manifest=Path(tmp) / 'manifest.json', attempts=Path(tmp),
                load=lambda _folder: {'d0': {'scene': 'd0', 'status': 'passed-smoke',
                                           'environment_rejections': ['foreground missing']}})
            platform = Mock()
            platform.find_clients.return_value = []
            runner = pilot.Runner(fx.protocol(), archive, sys.prefix, platform=platform)
            runner.state_hashes = Mock()
            with self.assertRaisesRegex(pilot.CheckFailed, 'passing smoke launch of d0'):
                runner.freeze()
            runner.state_hashes.assert_not_called()
            self.assertFalse(archive.manifest.exists())

    def test_smoke_command_stops_on_failed_launch(self):
        runner = pilot.Runner(fx.protocol(), SimpleNamespace(manifest=fx.ROOT / 'build/no-manifest.json'),
                              sys.prefix, platform=plat.Platform())
        runner.attempts = Mock(return_value={})
        runner.launch = Mock(return_value={'status': 'failed-checks', 'reasons': ['quiet window expired']})
        with self.assertRaisesRegex(pilot.CheckFailed, 'd0 smoke failed'):
            runner.run_smoke()
        self.assertEqual(runner.launch.call_count, 1)

    def test_health_check_fails_on_missing_critical_telemetry(self):
        monitor = SimpleNamespace(samples=[], topology=fx.topology_12700f(), errors=[], gpu_samples=lambda: [])
        self.assertEqual(len(mon.check_health(monitor)), 3)
        monitor.samples = fx.samples(0, 9)
        self.assertEqual(mon.check_health(monitor), [])
        monitor.samples[1]['cpu'] = plat.unavailable('temporary failure')
        self.assertEqual(mon.check_health(monitor), [])  # one missing sample is allowed by the frozen 90% rule
        monitor.samples[2]['cpu'] = plat.unavailable('temporary failure')
        self.assertTrue(any('cpu' in s for s in mon.check_health(monitor)))

    def test_unreadable_live_java_process_cannot_pass_as_no_clients(self):
        platform = Mock(spec=plat.Platform)
        platform.processes.return_value = {(123, 456): {'name': 'java.exe'}}
        platform.command_line.return_value = None
        platform.executable.return_value = 'java.exe'
        with self.assertRaisesRegex(OSError, 'cannot classify Java'):
            plat.Platform.find_clients(platform)

    def test_command_line_failure_is_retried(self):
        platform = Mock()
        platform.command_line.side_effect = [None, 'java KnotClient']
        monitor = mon.Monitor(platform, None)
        self.assertIsNone(monitor._cmdline((123, 456), 123))
        self.assertEqual(monitor._cmdline((123, 456), 123), 'java KnotClient')
        self.assertEqual(monitor._cmdline((123, 456), 123), 'java KnotClient')
        self.assertEqual(platform.command_line.call_count, 2)

    @unittest.skipUnless(plat.WINDOWS, 'Windows API fault injection')
    def test_process_enumeration_failure_is_not_an_empty_success(self):
        with patch.object(plat.kernel32, 'Process32FirstW', return_value=0):
            with self.assertRaises(OSError):
                plat.WindowsPlatform().processes()

    def test_process_probe_failure_creates_no_successful_cpu_sample(self):
        platform = Mock()
        platform.processes.side_effect = OSError('enumeration failed')
        monitor = mon.Monitor(platform, None)
        with self.assertRaises(OSError):
            monitor.sample_once()
        self.assertEqual(monitor.samples, [])


@unittest.skipUnless(plat.WINDOWS, 'live Windows probes')
class LiveWindows(unittest.TestCase):
    def test_process_identity_cpu_topology_and_foreground(self):
        p = plat.WindowsPlatform()
        try:
            processes = p.processes()
            me = [v for (pid, _), v in processes.items() if pid == os.getpid()]
            self.assertEqual(len(me), 1)
            self.assertIsNotNone(me[0]['cpu_s'])
            idle, total = p.system_times()
            self.assertGreater(total, idle)
            self.assertIn('python', p.command_line(os.getpid()).lower())
            self.assertEqual(Path(p.executable(os.getpid())).resolve(), Path(sys.executable).resolve())
            for reading in (p.topology(), p.foreground(), p.power_plan(), p.memory(),
                            p.affinity(os.getpid()), p.priority(os.getpid()), p.keep_awake()):
                self.assertEqual(reading['status'], 'ok', reading)
        finally:
            p.close()

    def test_affinity_applies_to_a_child_and_is_observed(self):
        import subprocess
        child = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(20)'])
        p = plat.WindowsPlatform()
        try:
            processors = p.affinity(child.pid)['value'][:1]
            p.set_affinity(child.pid, processors)
            self.assertEqual(p.affinity(child.pid)['value'], processors)
        finally:
            child.terminate()
            child.wait(timeout=10)
            p.close()


if __name__ == '__main__':
    unittest.main()
