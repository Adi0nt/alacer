import _thread
import json
import subprocess
import sys
import threading
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import aa_pilot as aa
from aa_thread_cpu import WindowsThreadCpu

JDK = Path('C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot')
ARCHIVE = ROOT / 'docs/benchmarks/2026-10-22-driver-settings-recheck2'


class Recheck(aa.Runner):
    def verify_launcher(self, pid, props):
        identity = super().verify_launcher(pid, props)
        label = props['benchmark.label']
        self.trace_stop = threading.Event()
        def sample():
            sampler = WindowsThreadCpu(pid)
            with (self.archive.raw / (label + '-threads.jsonl')).open('x') as out:
                while not self.trace_stop.is_set():
                    start, cpu = time.monotonic(), time.thread_time()
                    try:
                        row = {'t': time.time(), 'threads': sampler.snapshot()}
                    except OSError as error:
                        row = {'t': time.time(), 'error': str(error)}
                    row.update(self_cpu_s=time.thread_time() - cpu, self_wall_s=time.monotonic() - start)
                    out.write(json.dumps(row) + '\n')
                    out.flush()
                    self.trace_stop.wait(max(0, 5 - (time.monotonic() - start)))
        self.sampler = threading.Thread(target=sample, daemon=True)
        self.sampler.start()
        def dump():
            if self.trace_stop.wait(45):
                return
            start = time.time()
            try:
                result = subprocess.run([str(JDK / 'bin/jcmd.exe'), str(pid), 'Thread.print'],
                                        capture_output=True, text=True, timeout=15)
                text = result.stdout + result.stderr
            except Exception as error:
                text = f'dump failed: {error}'
            (self.archive.raw / (label + '-dump-45.txt')).write_text(
                f'epoch_start={start}\nepoch_end={time.time()}\n' + text, encoding='utf-8')
        self.dumper = threading.Thread(target=dump, daemon=True)
        self.dumper.start()
        return identity

    def stop_trace(self):
        if hasattr(self, 'trace_stop'):
            self.trace_stop.set()
        for name in ('sampler', 'dumper'):
            if getattr(self, name, None):
                getattr(self, name).join(timeout=20)


def main():
    protocol_file = ARCHIVE / 'protocol.json'
    p = json.loads(protocol_file.read_text())
    archive = aa.Archive(p, protocol_file)
    runner = Recheck(p, archive, JDK)
    aa.check(runner.platform.find_clients() == [], 'another client is running')
    original = runner.platform.power_plan()
    aa.check(original['status'] == 'ok', 'original power plan unavailable')
    state = {'started_epoch': time.time(), 'original_power_plan': original, 'budget_s': 900, 'attempts': []}
    aa.write_json(ARCHIVE / 'state.json', state, exclusive=True)
    start = time.monotonic()
    deadline = threading.Timer(900, _thread.interrupt_main)
    runner.acquire()
    try:
        subprocess.run(['powercfg', '/setactive', p['monitor']['expected_power_plan']], check=True)
        deadline.start()
        for i in (1, 2):
            if time.monotonic() - start > 900 - 420:
                state['stop_reason'] = 'insufficient remaining budget'
                break
            label = f'diag32off2-terrain-{i}'
            try:
                result = runner.launch(label, 'smoke', 'terrain', {'diagnostic_only': True}, monitored=True)
            finally:
                runner.stop_trace()
            state['attempts'].append({'label': label, 'status': result['status'],
                                      'environment_rejections': result.get('environment_rejections')})
            aa.write_json(ARCHIVE / 'state.json', state)
            if result['status'] != 'passed-smoke' or result.get('environment_rejections'):
                state['stop_reason'] = 'functional/environmental failure; no retry'
                break
    except KeyboardInterrupt:
        state['stop_reason'] = 'deadline/operator interruption; no retry'
    finally:
        deadline.cancel()
        runner.stop_trace()
        restored = subprocess.run(['powercfg', '/setactive', original['value']['guid']], capture_output=True)
        state.update(ended_epoch=time.time(), elapsed_s=time.monotonic() - start,
                     restore_exit_code=restored.returncode, final_power_plan=runner.platform.power_plan(),
                     clients_remaining=runner.platform.find_clients())
        aa.write_json(ARCHIVE / 'state.json', state)
        runner.release()


if __name__ == '__main__':
    main()
