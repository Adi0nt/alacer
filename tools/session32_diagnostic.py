#!/usr/bin/env python3
"""Bounded, exploratory Session 32 reproduction; reuses the existing smoke launcher, never calibration/pilot."""
import _thread
import json
import subprocess
import threading
import time
from pathlib import Path

import aa_pilot as aa
from aa_thread_cpu import WindowsThreadCpu

ARCHIVE = aa.ROOT / 'docs/benchmarks/2026-10-22-stability-investigation'
JDK = Path('C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot')


class DiagnosticRunner(aa.Runner):
    def verify_launcher(self, pid, props):
        identity = super().verify_launcher(pid, props)
        label = props['benchmark.label']
        raw = self.archive.raw
        self.trace_stop = threading.Event()
        def samples():
            sampler = WindowsThreadCpu(pid)
            with (raw / (label + '-threads.jsonl')).open('x') as out:
                while not self.trace_stop.is_set():
                    start, cpu = time.monotonic(), time.thread_time()
                    try:
                        row = {'t': time.time(), 'threads': sampler.snapshot()}
                    except OSError as error:
                        row = {'t': time.time(), 'error': str(error)}
                    row.update(self_cpu_s=time.thread_time() - cpu, self_wall_s=time.monotonic() - start)
                    out.write(json.dumps(row) + '\n')
                    out.flush()
                    self.trace_stop.wait(max(0, 1 - (time.monotonic() - start)))
        self.sampler = threading.Thread(target=samples, daemon=True)
        self.sampler.start()
        command = [str(JDK / 'bin/jcmd.exe'), str(pid), 'JFR.start', 'name=diag32', 'settings=default',
                   'dumponexit=true', 'filename=' + str(raw / (label + '.jfr'))]
        result = subprocess.run(command, capture_output=True, text=True, timeout=20)
        (raw / (label + '-jfr-start.txt')).write_text(result.stdout + result.stderr, encoding='utf-8')
        aa.check(result.returncode == 0 and 'Started recording' in result.stdout, 'diagnostic JFR attach failed')

        def dumps():
            last = 0
            for seconds in (45, 280):
                if self.trace_stop.wait(seconds - last):
                    return
                last = seconds
                start = time.time()
                result = subprocess.run([str(JDK / 'bin/jcmd.exe'), str(pid), 'Thread.print'],
                                        capture_output=True, text=True, timeout=15)
                (raw / f'{label}-dump-{seconds}.txt').write_text(
                    f'epoch_start={start}\nepoch_end={time.time()}\n' + result.stdout + result.stderr,
                    encoding='utf-8')

        self.dumper = threading.Thread(target=dumps, daemon=True)
        self.dumper.start()
        return identity

    def finish_trace(self):
        if hasattr(self, 'trace_stop'):
            self.trace_stop.set()
            if getattr(self, 'dumper', None):
                self.dumper.join(timeout=20)
        if getattr(self, 'sampler', None):
            self.sampler.join(timeout=5)
        self.trace_log.close()


def main():
    protocol_file = ARCHIVE / 'protocol.json'
    protocol = json.loads(protocol_file.read_text())
    archive = aa.Archive(protocol, protocol_file)
    runner = DiagnosticRunner(protocol, archive, JDK)
    aa.check(runner.platform.find_clients() == [], 'client already running')
    original = runner.platform.power_plan()
    aa.check(original['status'] == 'ok', 'cannot record original power plan')
    state = json.loads((ARCHIVE / 'diagnostic-state.json').read_text())
    aa.check(original['value']['guid'] == state['original_power_plan']['value']['guid'], 'restore original plan first')
    # Interrupt the main launch at the hard deadline; Runner.finally kills only its owned client/tree.
    deadline = threading.Timer(max(0, 1800 - (time.time() - state['started_epoch'])), _thread.interrupt_main)
    runner.acquire()
    try:
        subprocess.run(['powercfg', '/setactive', protocol['monitor']['expected_power_plan']], check=True)
        deadline.start()
        for number, monitored in enumerate((True, False, False, True), 1):
            if time.time() - state['started_epoch'] > 1800 - 420:
                state['stop_reason'] = 'insufficient budget for another launch'
                break
            label = f'diag32v2-terrain-{number}-' + ('mon' if monitored else 'nomon')
            runner.trace_log = (archive.raw / (label + '-sampler.log')).open('x')
            try:
                result = runner.launch(label, 'smoke', 'terrain',
                                       {'diagnostic_only': True, 'monitored': monitored}, monitored=monitored)
            finally:
                runner.finish_trace()
            state['launches'].append({'label': label, 'status': result['status'],
                                      'environment_rejections': result.get('environment_rejections')})
            aa.write_json(ARCHIVE / 'diagnostic-state.json', state)
            if result['status'] != 'passed-smoke':
                state['stop_reason'] = 'functional failure; no retry'
                break
    except KeyboardInterrupt:
        state['stop_reason'] = 'deadline or operator interruption; no retry'
    finally:
        deadline.cancel()
        restore = subprocess.run(['powercfg', '/setactive', original['value']['guid']], capture_output=True, text=True)
        state.update(ended_epoch=time.time(), elapsed_s=time.time() - state['started_epoch'],
                     restore_exit_code=restore.returncode, final_power_plan=runner.platform.power_plan(),
                     clients_remaining=runner.platform.find_clients())
        aa.write_json(ARCHIVE / 'diagnostic-state.json', state)
        runner.release()


if __name__ == '__main__':
    main()
