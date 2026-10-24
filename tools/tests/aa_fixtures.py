"""Synthetic fixtures for the A/A pilot tests. Everything here is made up; nothing is a measurement."""
import copy
import json
import math
import random
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOLS))

import aa_monitor as mon  # noqa: E402
import aa_rules as rules  # noqa: E402

ROOT = TOOLS.parent
PROTOCOL_PATH = ROOT / 'docs/benchmarks/2026-10-22-aa-pilot/protocol.json'
HIGH_PERFORMANCE = '8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c'
P_CORES = list(range(16))
E_CORES = [16, 17, 18, 19]


def protocol(**overrides):
    p = json.loads(PROTOCOL_PATH.read_text())
    for key, value in overrides.items():
        p[key] = value
    return p


def topology_12700f():
    """Topology as Windows reports an i7-12700F: 8 P-cores with SMT (class 1), 4 E-cores (class 0)."""
    cores = [{'smt': True, 'efficiency_class': 1, 'groups': [(0, 0b11 << (2 * i))]} for i in range(8)]
    cores += [{'smt': False, 'efficiency_class': 0, 'groups': [(0, 1 << (16 + i))]} for i in range(4)]
    return rules_topology(cores)


def rules_topology(cores):
    import aa_platform as plat
    return plat.topology_from_cores(cores, 'synthetic')


def samples(start, end, background=0.1, fg_client=True, plan=HIGH_PERFORMANCE, affinity=P_CORES, client_pid=4242,
            freq=4500.0, missing=(), spike=None, interval=1.0):
    """Monitor samples at 1 Hz over [start, end] with controllable machine state."""
    out = []
    t = start
    i = 0
    while t <= end + 1e-9:
        bg = background + (spike[1] if spike and spike[0][0] <= t <= spike[0][1] else 0.0)
        s = {'kind': 'sample', 't': t, 'client_pid': client_pid, 'self_cpu_ms': 2.0}
        s['cpu'] = {'status': 'ok', 'value': {'busy_pct': 10.0, 'busy_cores': 2.0 + bg, 'client_cores': 1.5,
                                               'harness_cores': 0.05, 'background_cores': bg,
                                               'top_background': [{'name': 'x.exe', 'pid': 1, 'cores': bg}]}}
        s['frequency'] = {'status': 'ok', 'value': {'mean_mhz': freq, 'p_mean_mhz': freq, 'e_mean_mhz': freq * 0.8}}
        s['foreground'] = {'status': 'ok', 'value': {'pid': client_pid if fg_client else 7, 'exe': 'java.exe' if fg_client else 'chrome.exe',
                                                     'is_client': fg_client}}
        s['power_plan'] = {'status': 'ok', 'value': {'guid': plan, 'name': 'High performance'}}
        s['memory'] = {'status': 'ok', 'value': {'available_mb': 20000.0}}
        s['client_affinity'] = {'status': 'ok', 'value': list(affinity)}
        for source in missing:
            s[source] = {'status': 'unavailable', 'value': None, 'reason': 'synthetic gap'}
        out.append(s)
        i += 1
        t = start + i * interval
    return out


def record(proto, label, scene, start, frame_ms=1.0, ref_ms=None, background=0.1, stage='pilot', fg_client=True,
           plan=HIGH_PERFORMANCE, affinity=P_CORES, e_frames=0, missing=(), env_overrides=None, config_change=None,
           props_identity='props', cmdline_extra='', spike=None, renderer='NVIDIA GeForce RTX 3060 Ti/PCIe/SSE2'):
    """A launch record shaped like the runner's, with every phase present and metrics scaled from frame_ms."""
    phases = rules.expected_phases(proto, scene)
    t = start + 40.0
    metrics, procs, poses, mon_phases = {}, {}, {}, {}
    smp = []
    for name, seconds in phases.items():
        fm = (ref_ms if ref_ms is not None else frame_ms) if name.startswith('reference') else frame_ms
        m = {'frames': int(seconds * 1000 / fm), 'tick_free_frames': 1000, 'tick_frame_share': 0.02,
             'frame_mean_ms': fm, 'p95_ms': fm * 1.5, 'p99_ms': fm * 2, 'slow1_mean_ms': fm * 2.5, 'tick_mean_ms': fm * 3,
             'tick_p90_ms': fm * 4, 'tick_p99_ms': fm * 5, 'alloc_per_tick_free_frame': 3000.0, 'alloc_per_frame': 3500.0,
             'terrain_setup_ms': 0.01, 'visible_chunks': 500.0, 'client_cpu_ms_per_frame': fm * 0.4,
             'process_cpu_ms_per_frame': fm * 1.4, 'wall_start': t, 'wall_end': t + seconds}
        metrics[name] = m
        s = samples(t - 3, t + seconds, background, fg_client, plan, affinity, missing=missing, spike=spike)
        smp += s
        gpu = [{'t': x['t'], 'utilization.gpu': 95.0, 'clocks.gr': 1900.0, 'pstate': 'P0'} for x in s]
        mon_phases[name] = mon.summarize(s, gpu, t, t + seconds)
        frames = m['frames']
        hist = {'0': frames - e_frames}
        if e_frames:
            hist['17'] = e_frames
        procs[name] = rules.processor_split({'client_processor_frames': hist, 'client_processor_unknown_frames': 0},
                                            topology_12700f()['value'], P_CORES)
        poses[name] = {'mobs_start': 0, 'mobs_end': 0, 'mobs_both': 0, 'max_move_blocks': 0.0, 'max_turn_degrees': 0.0}
        t += seconds + 3
    config = {'render.fastChunkVisibility': 'true', 'quality.leaves': '0'}
    if config_change:
        config.update(config_change)
    env = {'gl.vendor': 'NVIDIA Corporation', 'gl.renderer': renderer, 'gl.version': '4.6.0 NVIDIA 616.92',
           'vboActive': 'false', 'options.useVbo': 'false', 'window': '1920x1080', 'os': 'Windows 11 10.0 amd64',
           'cpu': '20x 12th Gen Intel(R) Core(TM) i7-12700F', 'cpu.threads': '20', 'java.runtime.version': '25.0.4.1+1-LTS',
           'java.vendor': 'Eclipse Adoptium', 'profiler.epochWallMillis': str(int(start * 1000)),
           'jvm.args': f'-Dalacer.benchmark.label={label} -Xmx2G', 'benchmark.joinSettleSeconds': '10.00'}
    env.update(env_overrides or {})
    launch = mon.summarize(smp, [], start + 40, t)
    return {
        'label': label, 'stage': stage, 'scene': scene, 'started_epoch': start, 'ended_epoch': t + 5,
        'environment': env, 'phase_configs': {n: config for n in phases}, 'config_sha': rules.canonical_sha(config),
        'props_identity': props_identity,
        'launcher': {'pid': 4242, 'exe': 'C:/jdk/bin/java.exe',
                     'cmdline': f'java -Xmx2G -Dalacer.benchmark.label={label} -Dalacer.x=1{cmdline_extra}'},
        'expected_affinity': P_CORES, 'phases_expected': phases, 'metrics': metrics, 'processors': procs,
        'mob_pose': poses, 'monitor': {'phases': mon_phases, 'launch': launch, 'pre_launch': launch},
        'series_5s': [], 'hardware': {'static': {'cpu_model': 'i7-12700F'}}, 'hardware_sha': 'hw',
        'manifest_sha': 'm', 'artifacts_sha': 'a', 'jdk_sha': 'j',
    }


def calibration_for(proto, scenes=('d0', 'terrain'), base=1.0, launches=4, seed=1):
    rng = random.Random(seed)
    recs = []
    t = 1_800_000_000.0
    for scene in scenes:
        for i in range(launches):
            ref = base * math.exp(rng.gauss(0, 0.005))
            recs.append(record(proto, f'cal-{scene}-{i}', scene, t, frame_ms=base, ref_ms=ref, stage='calibration'))
            t += 400
    return rules.build_calibration(proto, recs), recs


def clone(x):
    return copy.deepcopy(x)
