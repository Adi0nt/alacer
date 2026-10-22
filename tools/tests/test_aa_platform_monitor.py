"""Operating-system probes (pure parsers on synthetic buffers; live Linux probes) and monitor aggregation."""
import os
import struct
import sys
import tempfile
import time
import unittest

import aa_fixtures as fx
import aa_monitor as mon
import aa_platform as plat


def core_record(efficiency, mask, smt):
    """One SYSTEM_LOGICAL_PROCESSOR_INFORMATION_EX record for RelationProcessorCore (48 bytes)."""
    body = struct.pack('<BB20sH', 1 if smt else 0, efficiency, b'\0' * 20, 1) + struct.pack('<QH6s', mask, 0, b'\0' * 6)
    return struct.pack('<II', 0, 8 + len(body)) + body


class Topology(unittest.TestCase):
    def test_hybrid_topology_from_windows_records(self):
        # i7-12700F as Windows lists it: 8 P-cores with two logical processors each, then 4 E-cores.
        buf = b''.join(core_record(1, 0b11 << (2 * i), True) for i in range(8))
        buf += b''.join(core_record(0, 1 << (16 + i), False) for i in range(4))
        cores = plat.parse_processor_core_relations(buf)
        self.assertEqual(len(cores), 12)
        topo = plat.topology_from_cores(cores, 'test')
        v = topo['value']
        self.assertTrue(v['hybrid'])
        self.assertEqual(v['performance_processors'], list(range(16)))
        self.assertEqual(v['efficiency_processors'], [16, 17, 18, 19])
        self.assertEqual(v['logical_processors'], 20)

    def test_core_type_comes_from_the_os_not_from_processor_numbers(self):
        # A machine that lists E-cores first: numbering alone would pick the wrong cores.
        buf = b''.join(core_record(0, 1 << i, False) for i in range(4))
        buf += b''.join(core_record(1, 0b11 << (4 + 2 * i), True) for i in range(2))
        v = plat.topology_from_cores(plat.parse_processor_core_relations(buf), 'test')['value']
        self.assertEqual(v['performance_processors'], [4, 5, 6, 7])
        self.assertEqual(v['efficiency_processors'], [0, 1, 2, 3])

    def test_uniform_topology_is_not_hybrid(self):
        buf = b''.join(core_record(0, 1 << i, False) for i in range(4))
        v = plat.topology_from_cores(plat.parse_processor_core_relations(buf), 'test')['value']
        self.assertFalse(v['hybrid'])
        self.assertNotIn('performance_processors', v)

    def test_other_relationships_are_skipped(self):
        other = struct.pack('<II', 2, 16) + b'\0' * 8
        buf = other + core_record(1, 1, False)
        self.assertEqual(len(plat.parse_processor_core_relations(buf)), 1)
        with self.assertRaises(ValueError):
            plat.parse_processor_core_relations(struct.pack('<II', 0, 0))


class Parsers(unittest.TestCase):
    def test_command_line_buffer(self):
        text = 'java -Dalacer.benchmark=true net.fabricmc.devlaunchinjector.Main'
        raw = text.encode('utf-16-le')
        buf = struct.pack('<HH4xQ', len(raw), len(raw), 0) + raw
        self.assertEqual(plat.parse_unicode_string_buffer(buf, 8), text)

    def test_pdh_instances_and_cpu_lists(self):
        self.assertEqual(plat.pdh_instance_processor('0,7'), 7)
        self.assertEqual(plat.pdh_instance_processor('1,3'), 67)
        self.assertIsNone(plat.pdh_instance_processor('_Total'))
        self.assertIsNone(plat.pdh_instance_processor('0,_Total'))
        self.assertEqual(plat.parse_cpu_list('0-3,8,10-11\n'), [0, 1, 2, 3, 8, 10, 11])
        self.assertEqual(plat.mask_to_processors(plat.processors_to_mask([0, 5, 19])), [0, 5, 19])
        with self.assertRaises(ValueError):
            plat.processors_to_mask([64])

    def test_client_and_gradle_command_classification(self):
        client = 'java.exe -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient net.fabricmc.devlaunchinjector.Main'
        self.assertTrue(plat.is_client_command(client))
        self.assertFalse(plat.is_client_command('java -cp gradle.jar org.gradle.launcher.daemon.bootstrap.GradleDaemon 9.7.1'))
        self.assertTrue(plat.is_gradle_command('java org.gradle.wrapper.GradleWrapperMain runClient'))
        self.assertFalse(plat.is_client_command(None))

    def test_nvidia_missing_values_are_none_not_zero(self):
        fields = ['utilization.gpu', 'clocks.gr', 'pstate', 'clocks_event_reasons.active']
        s = plat.parse_nvidia_line('97, [N/A], P0, 0x0000000000000000\n', fields, 12.0)
        self.assertEqual(s['utilization.gpu'], 97.0)
        self.assertIsNone(s['clocks.gr'])
        self.assertEqual(s['pstate'], 'P0')

    def test_frequency_summary_splits_core_classes(self):
        topo = fx.topology_12700f()
        mhz = {p: 4600.0 for p in range(16)}
        mhz.update({p: 3600.0 for p in range(16, 20)})
        v = plat.summarize_frequency(mhz, 2100.0, topo)
        self.assertEqual(v['p_mean_mhz'], 4600.0)
        self.assertEqual(v['e_mean_mhz'], 3600.0)


@unittest.skipUnless(sys.platform.startswith('linux'), 'live Linux probes')
class LiveLinux(unittest.TestCase):
    def test_probes_answer_or_say_why_not(self):
        p = plat.LinuxPlatform()
        procs = p.processes()
        me = [v for (pid, _), v in procs.items() if pid == os.getpid()]
        self.assertEqual(len(me), 1)
        self.assertIsNotNone(me[0]['cpu_s'])
        idle, total = p.system_times()
        self.assertGreater(total, idle)
        for reading in (p.topology(), p.power_plan(), p.foreground(), p.frequency(p.topology()), p.memory(),
                        p.affinity(os.getpid())):
            self.assertIn(reading['status'], ('ok', 'unavailable', 'error'))
            if reading['status'] != 'ok':
                self.assertIsNone(reading['value'])
                self.assertTrue(reading['reason'])
        self.assertIn('python', p.command_line(os.getpid()))
        for client in p.find_clients():  # whatever runs right now must really be a client
            self.assertTrue(plat.is_client_command(client['cmdline']))

    def test_monitor_samples_this_process_and_measures_itself(self):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, 'm.jsonl')
            m = mon.Monitor(plat.LinuxPlatform(), path, interval=0.2)
            m.start()
            end = time.time() + 10.0
            while time.time() < end and len(m.samples) < 5:
                sum(i * i for i in range(20000))  # some CPU in this (harness) process
                time.sleep(0.01)  # the runner's main thread sleeps too; never starve the monitor of the GIL
            m.mark('test', note='x')
            m.stop()
            samples, marks = mon.load_jsonl(path)
            self.assertGreaterEqual(len(samples), 3)
            self.assertEqual(marks[0]['event'], 'test')
            s = mon.summarize(samples, interval=0.2)
            self.assertEqual(s['availability']['cpu']['ok'], len(samples) - 1)  # the first has no interval
            self.assertGreater(s['harness_cores_mean'], 0.0)  # this process is the runner: harness, not background
            self.assertIsNotNone(s['monitor_self_cores'])
            self.assertLess(s['monitor_self_cores'], 0.25)


class Summaries(unittest.TestCase):
    def test_missing_telemetry_is_counted_and_never_averaged_as_zero(self):
        smp = fx.samples(0, 9, background=0.2)
        for s in smp[:5]:
            s['frequency'] = plat.unavailable('counter not ready')
            s['cpu'] = plat.error(OSError('denied'))
        r = mon.summarize(smp, [], 0, 9)
        self.assertEqual(r['availability']['frequency']['ok'], 5)
        self.assertEqual(r['availability']['frequency']['fraction_ok'], 0.5)
        self.assertIn('counter not ready', r['availability']['frequency']['reasons'])
        self.assertEqual(r['frequency_mean_mhz'], 4500.0)  # mean of the answered samples only
        self.assertAlmostEqual(r['background_cores_mean'], 0.2)
        self.assertEqual(r['availability']['cpu']['counts'], {'error': 5, 'ok': 5})
        self.assertIsNone(r['unattributed_cores_mean'])  # the synthetic source never reported it: None, not 0

    def test_windowing_rolling_peak_and_step(self):
        smp = fx.samples(0, 59, background=0.1, spike=((20, 30), 2.0))
        r = mon.summarize(smp, [], 0, 59)
        self.assertAlmostEqual(r['background_cores_max'], 2.1)
        self.assertAlmostEqual(r['background_rolling_max'], 2.1)
        self.assertAlmostEqual(r['background_step'], 2.0)
        quiet = mon.summarize(smp, [], 35, 59)
        self.assertAlmostEqual(quiet['background_cores_max'], 0.1)
        self.assertEqual(quiet['samples'], 25)
        self.assertAlmostEqual(quiet['coverage'], 25 / 24)

    def test_foreground_power_plan_and_affinity_sets(self):
        smp = fx.samples(0, 9, fg_client=True) + fx.samples(10, 11, fg_client=False, plan='381b4222-f694-41f0-9685-ff5bb260df2e',
                                                              affinity=list(range(20)))
        r = mon.summarize(smp, [], 0, 11)
        self.assertAlmostEqual(r['foreground_client_fraction'], 10 / 12)
        self.assertEqual(r['foreground_other_exes'], ['chrome.exe'])
        self.assertEqual(len(r['power_plans']), 2)
        self.assertEqual(len(r['client_affinities']), 2)

    def test_gpu_samples_and_downsample(self):
        gpu = [{'t': 1.0, 'utilization.gpu': 90.0, 'clocks.gr': 1800.0, 'pstate': 'P0'},
               {'t': 2.0, 'utilization.gpu': None, 'clocks.gr': 1500.0, 'pstate': 'P2'}]
        r = mon.summarize(fx.samples(0, 3), gpu, 0, 3)
        self.assertEqual(r['gpu_util_mean'], 90.0)
        self.assertEqual(r['gpu_clock_min_mhz'], 1500.0)
        self.assertEqual(r['gpu_pstates'], ['P0', 'P2'])
        self.assertEqual(r['availability']['gpu'], {'samples': 2, 'ok': 1})
        rows = mon.downsample(fx.samples(0, 2))
        self.assertEqual(rows[0]['fg_client'], True)
        self.assertEqual(rows[0]['background_cores'], 0.1)

    def test_truncated_jsonl_line_is_tolerated(self):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, 'x.jsonl')
            with open(path, 'w') as f:
                f.write('{"kind": "sample", "t": 1}\n{"kind": "mark", "t": 2, "event": "launch"}\n{"kind": "samp')
            samples, marks = mon.load_jsonl(path)
            self.assertEqual((len(samples), len(marks)), (1, 1))


if __name__ == '__main__':
    unittest.main()
