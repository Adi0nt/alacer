#!/usr/bin/env python3
"""Session 31: low-overhead environmental monitor for A/A pilot launches (standard library only).

A background thread samples once per interval (default 1 s): system-wide busy CPU, CPU used by the client, by the
benchmark harness (this runner, Gradle, nvidia-smi) and by every other process (the "background", with the top
processes by name), the effective CPU clock (overall and per core class), the foreground process's executable
name, the active power plan, the client's affinity and priority, and available memory. GPU utilization and clocks
come from one long-running nvidia-smi process when it exists. Each sample also records the monitor's own CPU time,
so its overhead is measured, not assumed.

Every source carries an explicit status per sample ('ok', 'unavailable', 'error'), and `summarize()` reports how
many samples each source answered. Values that a source did not provide are None, never zero.

Samples are appended to a JSON Lines file as they are taken, so an interrupted launch keeps its evidence.
"""
import json
import os
import threading
import time

import aa_platform as plat

HARNESS_NAMES = ('nvidia-smi', 'nvidia-smi.exe', 'conhost.exe', 'cmd.exe', 'gradlew', 'gradlew.bat')


class Monitor:
    def __init__(self, platform, out_path, interval=1.0, top_n=8, gpu=None, root_pid=None, topology=None):
        self.platform = platform
        self.out_path = out_path
        self.interval = interval
        self.top_n = top_n
        self.gpu = gpu
        self.root_pid = root_pid or os.getpid()
        self.topology = topology if topology is not None else platform.topology()
        self.client_pid = None
        self.extra_clients = []  # Client-like processes other than the tracked client: overlap evidence.
        self.gradle_pids = set()
        self.samples = []
        self.cmdline_cache = {}
        self.errors = []
        self._stop = threading.Event()
        self._thread = None
        self._prev = None
        self._lock = threading.Lock()
        self.marks = []

    # -----------------------------------------------------------------------------------------------------
    def start(self):
        if self.gpu is not None:
            self.gpu.start()
        self._thread = threading.Thread(target=self._run, name='aa-monitor', daemon=True)
        self._thread.start()

    def stop(self):
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=10)
        if self.gpu is not None:
            self.gpu.stop()
        self.platform.close()

    def mark(self, event, **data):
        """Timestamped runner events (launch started, client seen, affinity applied, ...)."""
        entry = dict(t=time.time(), event=event, **data)
        with self._lock:
            self.marks.append(entry)
            self._write(dict(entry, kind='mark'))

    def _write(self, entry):
        with open(self.out_path, 'a', encoding='utf-8', newline='\n') as f:
            f.write(json.dumps(entry, sort_keys=True) + '\n')

    def _run(self):
        next_t = time.monotonic()
        while not self._stop.is_set():
            try:
                self.sample_once()
            except Exception as e:  # noqa: BLE001 - recorded; the series validator treats gaps as missing evidence
                self.errors.append(f'{time.time():.3f} {type(e).__name__}: {e}')
            next_t += self.interval
            self._stop.wait(max(0.0, next_t - time.monotonic()))

    # -----------------------------------------------------------------------------------------------------
    def _cmdline(self, key, pid):
        if key not in self.cmdline_cache:
            self.cmdline_cache[key] = self.platform.command_line(pid)
        return self.cmdline_cache[key]

    def sample_once(self):
        thread_cpu0 = time.thread_time()
        t = time.time()
        mono = time.monotonic()
        procs = self.platform.processes()
        idle, total = self.platform.system_times()
        sample = {'kind': 'sample', 't': t}
        # Classify java processes once per (pid, start): the client, Gradle, or other.
        clients = []
        for key, p in procs.items():
            if p['name'].lower() in plat.JAVA_NAMES:
                cmd = self._cmdline(key, p['pid'])
                if plat.is_client_command(cmd):
                    clients.append(p['pid'])
                elif plat.is_gradle_command(cmd):
                    self.gradle_pids.add(p['pid'])
        if self.client_pid is None and len(clients) == 1:
            self.client_pid = clients[0]
        others = [c for c in clients if c != self.client_pid]
        if others:
            self.extra_clients.append({'t': t, 'pids': others})
        sample['clients_seen'] = clients
        harness = self._harness(procs)
        prev = self._prev
        self._prev = {'mono': mono, 'idle': idle, 'total': total,
                      'cpu': {k: p['cpu_s'] for k, p in procs.items() if p['cpu_s'] is not None}}
        if prev is None:
            sample['cpu'] = plat.unavailable('first sample (no interval yet)', self.platform.name)
        else:
            wall = mono - prev['mono']
            d_total = total - prev['total']
            d_busy = d_total - (idle - prev['idle'])
            client = harness_cores = background = 0.0
            unreadable = 0
            top = []
            for key, p in procs.items():
                if p['pid'] == 0:
                    continue  # System Idle Process
                if p['cpu_s'] is None:
                    unreadable += 1
                    continue
                cores = (p['cpu_s'] - prev['cpu'].get(key, p['cpu_s'])) / wall if key in prev['cpu'] else None
                if cores is None:
                    # New since the last sample: count its whole CPU time within this interval.
                    cores = p['cpu_s'] / wall
                if p['pid'] == self.client_pid:
                    client += cores
                elif p['pid'] in harness:
                    harness_cores += cores
                else:
                    background += cores
                    if cores > 0.005:
                        top.append((cores, p['name'], p['pid']))
            top.sort(reverse=True)
            sample['cpu'] = plat.ok({
                'interval_s': wall, 'busy_pct': 100.0 * d_busy / d_total if d_total > 0 else None,
                'busy_cores': d_busy / wall, 'client_cores': client if self.client_pid else None,
                'harness_cores': harness_cores, 'background_cores': background,
                'unattributed_cores': d_busy / wall - client - harness_cores - background,
                'unreadable_processes': unreadable,
                'top_background': [{'name': n, 'pid': pid, 'cores': round(c, 4)} for c, n, pid in top[:self.top_n]]},
                self.platform.name + ':process-times')
        sample['frequency'] = self.platform.frequency(self.topology)
        sample['foreground'] = self._foreground()
        sample['power_plan'] = self.platform.power_plan()
        sample['memory'] = self.platform.memory()
        if self.client_pid is not None:
            sample['client_affinity'] = self.platform.affinity(self.client_pid)
            sample['client_priority'] = self.platform.priority(self.client_pid)
        sample['client_pid'] = self.client_pid
        sample['self_cpu_ms'] = 1000.0 * (time.thread_time() - thread_cpu0)
        sample['self_wall_ms'] = 1000.0 * (time.monotonic() - mono)
        with self._lock:
            self.samples.append(sample)
            self._write(sample)
        return sample

    def _foreground(self):
        r = self.platform.foreground()
        if r['status'] == 'ok':
            v = dict(r['value'])
            v['is_client'] = self.client_pid is not None and v.get('pid') == self.client_pid
            return dict(r, value=v)
        return r

    def _harness(self, procs):
        """This runner's process tree and the Gradle daemon (which starts the client) are harness, not background."""
        children = {}
        for p in procs.values():
            children.setdefault(p['ppid'], []).append(p['pid'])
        out = set()
        stack = [self.root_pid] + [g for g in self.gradle_pids if g != self.client_pid]
        while stack:
            pid = stack.pop()
            if pid in out or pid == self.client_pid:
                continue
            out.add(pid)
            stack += children.get(pid, [])
        if self.gpu is not None and self.gpu.process is not None:
            out.add(self.gpu.process.pid)
        return out

    def gpu_samples(self):
        return list(self.gpu.samples) if self.gpu is not None else []


# ---------------------------------------------------------------------------------------------------------------
# Aggregation over a time window (pure; unit-tested with synthetic samples).

def _mean(values):
    values = [v for v in values if v is not None]
    return sum(values) / len(values) if values else None


def _value(sample, source, key=None):
    r = sample.get(source)
    if not r or r.get('status') != 'ok':
        return None
    v = r['value']
    return v if key is None else v.get(key)


def window(samples, start, end):
    return [s for s in samples if s.get('kind', 'sample') == 'sample' and start <= s['t'] <= end]


def summarize(samples, gpu=(), start=None, end=None, interval=1.0, rolling=5):
    """Per-source availability and the aggregates the validator reads, over [start, end] (all samples if None)."""
    if start is not None:
        samples = window(samples, start, end)
        gpu = [g for g in gpu if start <= g['t'] <= end]
    n = len(samples)
    expected = None if start is None else max(1, int((end - start) / interval))
    availability = {}
    for source in ('cpu', 'frequency', 'foreground', 'power_plan', 'memory', 'client_affinity'):
        statuses = [s.get(source, {}).get('status', 'missing') for s in samples]
        counts = {}
        for st in statuses:
            counts[st] = counts.get(st, 0) + 1
        reasons = sorted({s[source].get('reason') for s in samples if source in s and s[source].get('reason')})
        availability[source] = {'samples': n, 'ok': counts.get('ok', 0), 'counts': counts, 'reasons': reasons[:5],
                                'fraction_ok': counts.get('ok', 0) / n if n else 0.0}
    availability['gpu'] = {'samples': len(gpu), 'ok': sum(1 for g in gpu if g.get('utilization.gpu') is not None)}
    background = [_value(s, 'cpu', 'background_cores') for s in samples]
    bg = [b for b in background if b is not None]
    roll = [sum(bg[i:i + rolling]) / rolling for i in range(len(bg) - rolling + 1)] if len(bg) >= rolling else []
    fg = [_value(s, 'foreground') for s in samples]
    fg_ok = [f for f in fg if f is not None]
    plans = sorted({(_value(s, 'power_plan', 'guid'), _value(s, 'power_plan', 'name')) for s in samples
                    if _value(s, 'power_plan') is not None}, key=str)
    affinities = sorted({tuple(_value(s, 'client_affinity')) for s in samples if _value(s, 'client_affinity') is not None})
    tops = {}
    for s in samples:
        for p in (_value(s, 'cpu', 'top_background') or []):
            tops[p['name']] = tops.get(p['name'], 0.0) + p['cores']
    out = {
        'samples': n, 'expected_samples': expected,
        'coverage': (n / expected) if expected else None,
        'availability': availability,
        'busy_pct_mean': _mean(_value(s, 'cpu', 'busy_pct') for s in samples),
        'client_cores_mean': _mean(_value(s, 'cpu', 'client_cores') for s in samples),
        'harness_cores_mean': _mean(_value(s, 'cpu', 'harness_cores') for s in samples),
        'background_cores_mean': _mean(bg),
        'background_cores_max': max(bg) if bg else None,
        'background_rolling_max': max(roll) if roll else (max(bg) if bg else None),
        'background_step': (max(roll) - min(roll)) if roll else None,
        # Busy CPU no readable process accounts for (protected or system processes, interrupts and DPCs, which
        # include the graphics driver's work for the client itself): reported and flagged, not gated.
        'unattributed_cores_mean': _mean(_value(s, 'cpu', 'unattributed_cores') for s in samples),
        'unreadable_processes_max': max((_value(s, 'cpu', 'unreadable_processes') or 0 for s in samples), default=None),
        'top_background_core_seconds': dict(sorted(tops.items(), key=lambda kv: -kv[1])[:8]),
        'frequency_mean_mhz': _mean(_value(s, 'frequency', 'mean_mhz') for s in samples),
        'frequency_p_mean_mhz': _mean(_value(s, 'frequency', 'p_mean_mhz') for s in samples),
        'frequency_e_mean_mhz': _mean(_value(s, 'frequency', 'e_mean_mhz') for s in samples),
        'frequency_min_mhz': min((_value(s, 'frequency', 'mean_mhz') for s in samples
                                  if _value(s, 'frequency', 'mean_mhz') is not None), default=None),
        'foreground_client_fraction': (sum(1 for f in fg_ok if f.get('is_client')) / len(fg_ok)) if fg_ok else None,
        'foreground_other_exes': sorted({f.get('exe') or '(none)' for f in fg_ok if not f.get('is_client')}),
        'power_plans': [{'guid': g, 'name': nm} for g, nm in plans],
        'client_affinities': [list(a) for a in affinities],
        'memory_available_mb_min': min((_value(s, 'memory', 'available_mb') for s in samples
                                        if _value(s, 'memory', 'available_mb') is not None), default=None),
        'gpu_util_mean': _mean(g.get('utilization.gpu') for g in gpu),
        'gpu_clock_mean_mhz': _mean(g.get('clocks.gr') for g in gpu),
        'gpu_clock_min_mhz': min((g['clocks.gr'] for g in gpu if g.get('clocks.gr') is not None), default=None),
        'gpu_pstates': sorted({g.get('pstate') for g in gpu if g.get('pstate')}),
        'monitor_self_cpu_ms_mean': _mean(s.get('self_cpu_ms') for s in samples),
        'monitor_self_cpu_ms_max': max((s.get('self_cpu_ms', 0.0) for s in samples), default=None),
        'monitor_self_cores': (sum(s.get('self_cpu_ms', 0.0) for s in samples) / 1000.0 / (n * interval)) if n else None,
    }
    return out


def load_jsonl(path):
    samples, marks = [], []
    with open(path, encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                continue  # A truncated last line from an interrupted launch.
            (marks if entry.get('kind') == 'mark' else samples).append(entry)
    return samples, marks


def downsample(samples, keep=('busy_pct', 'client_cores', 'background_cores', 'harness_cores')):
    """Compact per-sample time series for the archive: time, CPU fields, effective clocks, foreground flag."""
    out = []
    for s in samples:
        row = {'t': round(s['t'], 3)}
        for k in keep:
            v = _value(s, 'cpu', k)
            row[k] = None if v is None else round(v, 4)
        for k in ('mean_mhz', 'p_mean_mhz', 'e_mean_mhz'):
            v = _value(s, 'frequency', k)
            row['freq_' + k] = None if v is None else round(v, 1)
        fg = _value(s, 'foreground')
        row['fg_client'] = None if fg is None else bool(fg.get('is_client'))
        row['self_cpu_ms'] = round(s.get('self_cpu_ms', 0.0), 3)
        out.append(row)
    return out


if __name__ == '__main__':
    # Standalone check: python tools/aa_monitor.py [seconds]  -> prints a summary of this machine right now.
    import sys
    seconds = float(sys.argv[1]) if len(sys.argv) > 1 else 5
    path = os.path.join(os.getcwd(), 'build', 'aa-monitor-check.jsonl')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if os.path.exists(path):
        os.remove(path)
    p = plat.current()
    m = Monitor(p, path, gpu=plat.NvidiaSmi())
    m.start()
    time.sleep(seconds)
    m.stop()
    print(json.dumps({'static': p.static_info(), 'topology': m.topology, 'gpu': m.gpu.status,
                      'summary': summarize(m.samples, m.gpu_samples()), 'errors': m.errors}, indent=1, default=str))
