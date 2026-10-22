#!/usr/bin/env python3
"""Session 31: pure rules of the A/A pilot (no launches, no operating-system calls; unit-tested).

Schedule and labels, resume state from attempt files, per-phase metrics from the benchmark's CSV and summary
files, per-launch environmental validation against a calibration baseline, pair identity checks, and the
calibration baseline itself. See docs/benchmarks/2026-10-22-aa-pilot/README.md for the protocol these implement.
"""
import csv
import hashlib
import json
import math
import re
import statistics

# Scenario phases (seconds at time scale 1) exactly as BenchmarkRunner/BackendScenario/ChunkFlightScenario define them.
SCENARIO_PHASES = {
    'd0': {'static': 10, 'pan': 15, 'fly': 20},
    'd200': {'static': 10, 'pan': 15, 'fly': 20},
    'terrain': {'warmup-pan': 15, 'warmup-sort': 16, 'steady': 10, 'pan': 15, 'sort': 16, 'fly': 20},
    'near': {'warmup-pan': 10, 'still': 10, 'fresh-flight': 40},
    'high': {'warmup-pan': 10, 'still': 10, 'fresh-flight': 40},
}
FLIGHTS = ('near', 'high')
# Phases whose camera translates: mobs enter and leave view range there, so the stillness rules do not apply.
MOVING = ('fly', 'sort', 'warmup-sort', 'fresh-flight')
REFERENCE_PRE, REFERENCE_POST = 'reference-pre', 'reference-post'
TERMINAL = ('passed', 'passed-smoke', 'failed-checks', 'rejected-env', 'interrupted', 'error')
VOLATILE_ENV = ('profiler.epochWallMillis', 'profiler.epochWallUncertaintyNanos', 'benchmark.joinSettleSeconds',
                'jvm.args')
LABEL_RE = re.compile(r'^[a-z0-9][a-z0-9-]*$')


def canonical_sha(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


# ---------------------------------------------------------------------------------------------------------------
# Schedule and labels

def side_order(n, pattern):
    """Nominal order of pair n (1-based) from the protocol's repeating pattern, e.g. ['ab', 'ba', 'ba', 'ab']."""
    order = pattern[(n - 1) % len(pattern)]
    assert sorted(order) == ['a', 'b'], order
    return order


def pilot_slots(pilot):
    """Pair slots in run order: pair n of every scene (in protocol order) before pair n + 1 of any scene."""
    pairs = pilot['pairs']
    scenes = list(pairs)
    out = []
    if pilot.get('interleave_scenes', True):
        for n in range(1, max(pairs.values()) + 1):
            out += [(s, n) for s in scenes if n <= pairs[s]]
    else:
        out = [(s, n) for s in scenes for n in range(1, pairs[s] + 1)]
    return out


def retry_suffix(k):
    return '' if k == 1 else f'-r{k}'


def pair_id(series, scene, n, k):
    return f'{series}-pl-{scene}-{n}{retry_suffix(k)}'


def side_label(series, scene, n, side, k):
    return f'{series}-pl-{scene}-{n}-{side}{retry_suffix(k)}'


def calibration_label(series, scene, i, k):
    return f'{series}-cal-{scene}-{i}{retry_suffix(k)}'


def calibration_slots(calibration):
    per = calibration['launches_per_scene']
    return [(s, i) for i in range(1, per + 1) for s in calibration['scenes']]


def check_label(label):
    if not LABEL_RE.match(label) or len(label) > 80:
        raise ValueError('label must be lower-case letters, digits and hyphens: %r' % label)
    return label


# ---------------------------------------------------------------------------------------------------------------
# Resume state

def pilot_state(protocol, attempts, pairs):
    """attempts: {label: attempt}; pairs: {pair_id: decision}. Returns per-slot state and the next work item.

    A slot's pair attempt k is decided by its pairs/<id>.json file. A pair attempt with launch attempts but no
    decision was interrupted; it must be finalized as rejected (never completed or reused) before new work.
    """
    series = protocol['series']
    pilot = protocol['pilot']
    max_k = pilot['max_attempts_per_pair']
    slots = []
    for scene, n in pilot_slots(pilot):
        entry = {'scene': scene, 'n': n, 'attempts': [], 'status': 'pending', 'undecided': None}
        for k in range(1, max_k + 2):
            pid = pair_id(series, scene, n, k)
            labels = [side_label(series, scene, n, s, k) for s in 'ab']
            started = [l for l in labels if l in attempts]
            if pid in pairs:
                entry['attempts'].append({'k': k, 'pair_id': pid, 'status': pairs[pid]['status']})
                if pairs[pid]['status'] == 'accepted':
                    entry['status'] = 'accepted'
                    break
            elif started:
                entry['undecided'] = {'k': k, 'pair_id': pid, 'labels': started}
                break
            else:
                break
        if entry['status'] != 'accepted':
            rejected = sum(1 for a in entry['attempts'] if a['status'] == 'rejected')
            if entry['undecided'] is not None:
                entry['status'] = 'undecided'
            elif rejected >= max_k:
                entry['status'] = 'exhausted'
            elif rejected:
                entry['status'] = 'retry'
        slots.append(entry)
    work = next(({'scene': e['scene'], 'n': e['n'], 'k': 1} for e in slots if e['status'] == 'pending'), None)
    if work is None:
        work = next(({'scene': e['scene'], 'n': e['n'], 'k': len(e['attempts']) + 1} for e in slots
                     if e['status'] == 'retry'), None)
    return {'slots': slots, 'next': work, 'undecided': [e['undecided'] for e in slots if e['undecided']]}


def calibration_state(protocol, attempts):
    series = protocol['series']
    cal = protocol['calibration']
    max_k = cal['max_attempts']
    out = []
    for scene, i in calibration_slots(cal):
        entry = {'scene': scene, 'i': i, 'labels': [], 'status': 'pending'}
        for k in range(1, max_k + 1):
            label = calibration_label(series, scene, i, k)
            if label not in attempts:
                break
            entry['labels'].append(label)
            status = attempts[label]['status']
            if status == 'passed':
                entry['status'] = 'passed'
                break
            if status not in TERMINAL:
                entry['status'] = 'undecided'
                break
        if entry['status'] == 'pending' and len(entry['labels']) >= max_k:
            entry['status'] = 'exhausted'
        out.append(entry)
    work = next(({'scene': e['scene'], 'i': e['i'], 'k': len(e['labels']) + 1} for e in out if e['status'] == 'pending'), None)
    return {'slots': out, 'next': work}


# ---------------------------------------------------------------------------------------------------------------
# Expected phases and per-phase metrics

def expected_phases(protocol, scene):
    """{phase: recorded seconds} in recording order for a scene under the protocol's launch options."""
    launch = protocol['launch']
    scale = float(launch['time_scale_flight'] if scene in FLIGHTS else launch['time_scale'])
    spec = protocol['scenes'][scene]
    overrides = spec.get('phase_seconds', {})
    for name in overrides:
        if name not in SCENARIO_PHASES[scene]:
            raise ValueError(f'{scene}: unknown phase {name}')
    phases = {}
    stab = protocol['stability']
    if stab.get('reference', 0) > 0:
        phases[REFERENCE_PRE] = float(stab['reference'])
    for name, base in SCENARIO_PHASES[scene].items():
        phases[name] = float(overrides.get(name, base * scale))
    for i in range(1, spec.get('repeat_segments', 0) + 1):
        phases['repeat-%02d' % i] = float(spec['repeat_segment_seconds'])
    if stab.get('reference_post'):
        phases[REFERENCE_POST] = float(stab['reference'])
    return phases


def judged_phases(protocol, scene):
    return list(protocol['scenes'][scene]['judged'])


def percentile(values, q):
    """Nearest-rank percentile, as session 29/30 gates.py."""
    v = sorted(values)
    return v[min(len(v) - 1, max(0, int(round(q * len(v) + 0.5)) - 1))]


def read_rows(path):
    with open(path, newline='') as f:
        return [{k: int(v) for k, v in r.items()} for r in csv.DictReader(f)]


def phase_metrics(rows, phase_summary, epoch_wall_ms):
    """Metrics of one recorded phase from its per-frame rows and its summary.json entry (session 30's definitions,
    plus tick-frame mean and p90, and the phase's wall-clock window for aligning monitor samples)."""
    if not rows:
        raise ValueError('phase has no frames')
    frame = [r['frame_ns'] / 1e6 for r in rows]
    slow = sorted(frame, reverse=True)[:max(1, len(frame) // 100)]
    tick = [r['frame_ns'] / 1e6 for r in rows if r['tick_ns'] > 0]
    free = [r['alloc_bytes'] for r in rows if r['tick_ns'] == 0]
    n = len(rows)
    p = phase_summary
    wall = p['recorded_wall_seconds']
    start = epoch_wall_ms / 1000.0 + min(r['start_ns'] for r in rows) / 1e9
    end = epoch_wall_ms / 1000.0 + max(r['start_ns'] + r['frame_ns'] for r in rows) / 1e9
    m = {
        'frames': n, 'tick_free_frames': len(free), 'tick_frame_share': len(tick) / n,
        'frame_mean_ms': statistics.fmean(frame), 'p95_ms': percentile(frame, 0.95), 'p99_ms': percentile(frame, 0.99),
        'slow1_mean_ms': statistics.fmean(slow),
        'tick_mean_ms': statistics.fmean(tick) if tick else None,
        'tick_p90_ms': percentile(tick, 0.90) if tick else None,
        'tick_p99_ms': percentile(tick, 0.99) if tick else None,
        'alloc_per_tick_free_frame': statistics.fmean(free) if free else None,
        'alloc_per_frame': statistics.fmean(r['alloc_bytes'] for r in rows),
        'terrain_setup_ms': statistics.fmean(r['terrain_setup_ns'] for r in rows) / 1e6,
        'visible_chunks': statistics.fmean(r['visible_chunks'] for r in rows),
        'client_cpu_ms_per_frame': p['client_thread_cpu_ms_per_frame'],
        'process_cpu_ms_per_frame': (p['process_cpu_cores'] * wall * 1000.0 / n) if p['process_cpu_cores'] >= 0 else None,
        'wall_start': start, 'wall_end': end,
    }
    return m


def processor_split(phase_summary, topology_value, performance=None):
    """Client-thread frames per core class from the Java processor histogram and the OS topology."""
    hist = phase_summary.get('client_processor_frames')
    if hist is None:
        return {'status': 'unavailable', 'reason': 'processor sampling off'}
    unknown = phase_summary.get('client_processor_unknown_frames', 0)
    total = sum(hist.values()) + unknown
    out = {'status': 'ok', 'frames': total, 'unknown_frames': unknown,
           'processors': {int(k): v for k, v in hist.items()}}
    if not topology_value or not topology_value.get('hybrid'):
        out['core_class'] = 'unavailable: topology not hybrid or unknown'
        return out
    perf = set(performance if performance is not None else topology_value['performance_processors'])
    on_p = sum(v for k, v in hist.items() if int(k) in perf)
    out['p_core_frames'] = on_p
    out['e_core_frames'] = sum(hist.values()) - on_p
    out['e_core_share'] = out['e_core_frames'] / total if total else None
    return out


# ---------------------------------------------------------------------------------------------------------------
# Calibration baseline

REFERENCE_METRICS = ('frame_mean_ms', 'client_cpu_ms_per_frame', 'process_cpu_ms_per_frame')


def robust_log_spread(values):
    """1.4826 x median |ln(x / median)| as a fraction (0.05 = 5%)."""
    m = statistics.median(values)
    return math.expm1(1.4826 * statistics.median(abs(math.log(v / m)) for v in values))


def build_calibration(protocol, records):
    """records: accepted calibration records ({'scene', 'metrics': {phase: {...}}, 'environment', 'monitor'}).

    Per scene and reference phase: the median of each reference metric and its robust spread over launches. CPU
    bands are set from that spread (clamped to the protocol's limits); the frame-mean band is the predeclared one.
    Also the identity of the GPU backend and the observed machine state, which every pilot launch must match.
    """
    limits = protocol['limits']
    cal = protocol['calibration']
    out = {'scenes': {}, 'pass': True, 'failures': []}
    for scene in cal['scenes']:
        recs = [r for r in records if r['scene'] == scene]
        entry = {'launches': [r['label'] for r in recs]}
        if len(recs) < cal['min_valid_per_scene']:
            out['pass'] = False
            out['failures'].append(f'{scene}: {len(recs)} valid calibration launches, need {cal["min_valid_per_scene"]}')
            out['scenes'][scene] = entry
            continue
        for ref in (REFERENCE_PRE, REFERENCE_POST):
            if ref not in recs[0]['metrics']:
                continue
            e = {}
            for metric in REFERENCE_METRICS:
                vals = [r['metrics'][ref][metric] for r in recs]
                spread = robust_log_spread(vals)
                e[metric] = {'median': statistics.median(vals), 'spread': spread, 'values': vals}
                if metric == 'frame_mean_ms':
                    band = limits['reference_frame_mean_band']
                    if spread > cal['max_reference_spread']:
                        out['pass'] = False
                        out['failures'].append(f'{scene}/{ref}: calibration frame-mean spread {100 * spread:.2f}% '
                                               f'above {100 * cal["max_reference_spread"]:.1f}%')
                else:
                    band = min(limits['reference_cpu_band_max'],
                               max(limits['reference_cpu_band_min'], limits['reference_cpu_band_k'] * spread))
                e[metric]['band'] = band
            entry[ref] = e
        envs = {canonical_sha(backend_identity(r)) for r in recs}
        if len(envs) != 1:
            out['pass'] = False
            out['failures'].append(f'{scene}: GPU backend differs between calibration launches')
        entry['backend'] = backend_identity(recs[0])
        freqs = [r['monitor']['launch'].get('frequency_p_mean_mhz') for r in recs]
        freqs = [f for f in freqs if f is not None]
        entry['p_core_mhz_median'] = statistics.median(freqs) if freqs else None
        entry['background_cores_mean'] = [r['monitor']['launch'].get('background_cores_mean') for r in recs]
        out['scenes'][scene] = entry
    plans = {json.dumps(p, sort_keys=True) for r in records for p in r['monitor']['launch'].get('power_plans', [])}
    out['power_plans'] = [json.loads(p) for p in sorted(plans)]
    if len(plans) > 1:
        out['pass'] = False
        out['failures'].append('power plan changed during calibration')
    out['config_sha'] = sorted({r['config_sha'] for r in records})
    if len(out['config_sha']) > 1:
        out['pass'] = False
        out['failures'].append('calibration launches ran with different configurations')
    return out


def backend_identity(record):
    env = record['environment']
    keys = ('gl.vendor', 'gl.renderer', 'gl.version', 'vboActive', 'options.useVbo', 'window', 'os', 'cpu', 'cpu.threads',
            'java.runtime.version', 'java.vendor')
    out = {k: env.get(k) for k in keys}
    chunk = {c.get('backend.renderChunkClass') for c in record.get('phase_configs', {}).values()}
    out['backend.renderChunkClass'] = sorted(x for x in chunk if x)
    return out


# ---------------------------------------------------------------------------------------------------------------
# Per-launch environmental validation

def validate_environment(protocol, record, calibration=None, topology=None):
    """Returns (reasons, flags). Any reason rejects the launch (and therefore its whole pair).

    Without a calibration (the calibration stage itself) only the absolute rules apply: background, foreground,
    power plan, affinity, processor placement, telemetry coverage and the scene's stillness.
    """
    limits = protocol['limits']
    critical = set(protocol['monitor']['critical_sources'])
    reasons, flags = [], []
    mon = record['monitor']
    phases = list(record['metrics'])
    for phase in phases:
        w = mon['phases'].get(phase)
        if w is None:
            reasons.append(f'{phase}: no monitor window')
            continue
        cov = w.get('coverage')
        if cov is None or cov < limits['monitor_min_coverage']:
            reasons.append(f'{phase}: monitor coverage {cov if cov is None else round(cov, 2)} below '
                           f'{limits["monitor_min_coverage"]}')
        for source in ('cpu', 'power_plan', 'client_affinity', 'foreground', 'frequency', 'memory'):
            frac = w['availability'][source]['fraction_ok']
            if frac < limits['monitor_min_coverage']:
                text = f'{phase}: {source} telemetry available in {100 * frac:.0f}% of samples ' \
                       f'({", ".join(w["availability"][source]["reasons"]) or "no reason"})'
                (reasons if source in critical else flags).append(text)
        if 'gpu' in protocol['monitor'].get('gpu_sources', ()) and w['availability']['gpu']['ok'] == 0:
            flags.append(f'{phase}: no GPU telemetry')
        bg = w.get('background_cores_mean')
        if bg is not None and bg > limits['background_cores_mean']:
            reasons.append(f'{phase}: background CPU {bg:.2f} cores (mean) above {limits["background_cores_mean"]}')
        peak = w.get('background_rolling_max')
        if peak is not None and peak > limits['background_cores_peak']:
            reasons.append(f'{phase}: background CPU {peak:.2f} cores (5 s peak) above {limits["background_cores_peak"]}')
        fg = w.get('foreground_client_fraction')
        if fg is not None and fg < limits['foreground_client_min']:
            reasons.append(f'{phase}: client in the foreground for {100 * fg:.0f}% of samples '
                           f'(others: {", ".join(w["foreground_other_exes"]) or "none"})')
        un = w.get('unattributed_cores_mean')
        if un is not None and limits.get('unattributed_cores_flag') is not None and un > limits['unattributed_cores_flag']:
            flags.append(f'{phase}: {un:.2f} busy cores not attributed to any readable process')
        if w.get('memory_available_mb_min') is not None and w['memory_available_mb_min'] < limits['memory_available_mb_min']:
            flags.append(f'{phase}: available memory fell to {w["memory_available_mb_min"]:.0f} MB')
    launch = mon['launch']
    plans = launch.get('power_plans', [])
    expected_plan = protocol['monitor'].get('expected_power_plan')
    if len(plans) > 1:
        reasons.append('power plan changed during the launch: ' + ', '.join(str(p) for p in plans))
    elif len(plans) == 1:
        if expected_plan and plans[0]['guid'] != expected_plan:
            reasons.append(f'power plan {plans[0]} is not the protocol\'s {expected_plan}')
        if calibration and calibration.get('power_plans') and plans[0] not in calibration['power_plans']:
            reasons.append(f'power plan {plans[0]} differs from calibration {calibration["power_plans"]}')
    affinities = launch.get('client_affinities', [])
    expected_affinity = record.get('expected_affinity')
    if len(affinities) > 1:
        reasons.append(f'client affinity changed during the launch: {affinities}')
    elif expected_affinity is not None and affinities and affinities[0] != expected_affinity:
        reasons.append(f'client affinity {affinities[0]} is not the expected {expected_affinity}')
    # Where the client thread ran (Java processor sampling) and whether the scene stayed still.
    policy = protocol['affinity']['policy']
    for phase, split in record.get('processors', {}).items():
        if split.get('status') != 'ok':
            (reasons if 'processor_sampling' in critical else flags).append(
                f'{phase}: client-thread processor sampling unavailable ({split.get("reason")})')
            continue
        if split['unknown_frames']:
            text = f'{phase}: processor unknown in {split["unknown_frames"]} frames'
            (reasons if 'processor_sampling' in critical else flags).append(text)
        share = split.get('e_core_share')
        if policy == 'p-cores' and split.get('e_core_frames'):
            reasons.append(f'{phase}: client thread ran {split["e_core_frames"]} frames on efficiency cores under '
                           'the P-core affinity policy')
        elif share is not None and limits.get('e_core_share_max') is not None and share > limits['e_core_share_max']:
            reasons.append(f'{phase}: client thread on efficiency cores in {100 * share:.1f}% of frames')
        elif share is None and policy == 'p-cores':
            reasons.append(f'{phase}: core class of the client thread cannot be established ({split.get("core_class")})')
    for phase, pose in record.get('mob_pose', {}).items():
        if phase in MOVING:
            continue
        if pose and pose['mobs_both'] and pose['max_move_blocks'] > limits['mob_move_blocks_max']:
            reasons.append(f'{phase}: a mob moved {pose["max_move_blocks"]:.2f} blocks')
        if pose and pose['mobs_start'] != pose['mobs_end']:
            reasons.append(f'{phase}: mob count changed {pose["mobs_start"]} -> {pose["mobs_end"]}')
    if calibration is not None:
        scene_cal = calibration['scenes'].get(record['scene'])
        if scene_cal is None or REFERENCE_PRE not in scene_cal:
            reasons.append('no calibration baseline for this scene')
        else:
            for ref in (REFERENCE_PRE, REFERENCE_POST):
                if ref not in scene_cal or ref not in record['metrics']:
                    continue
                for metric in REFERENCE_METRICS:
                    base = scene_cal[ref][metric]
                    value = record['metrics'][ref][metric]
                    dev = math.expm1(abs(math.log(value / base['median'])))
                    if dev > base['band']:
                        reasons.append(f'{ref}: {metric} {value:.4f} deviates {100 * math.expm1(math.log(value / base["median"])):+.2f}% '
                                       f'from calibration {base["median"]:.4f} (band +/-{100 * base["band"]:.1f}%)')
            if canonical_sha(backend_identity(record)) != canonical_sha(scene_cal['backend']):
                reasons.append('GPU backend or platform identity differs from calibration')
            if record['config_sha'] not in calibration['config_sha']:
                reasons.append('configuration differs from calibration')
            base_mhz = scene_cal.get('p_core_mhz_median')
            for phase in phases:
                mhz = mon['phases'].get(phase, {}).get('frequency_p_mean_mhz')
                if base_mhz and mhz and abs(mhz / base_mhz - 1) > limits['frequency_flag']:
                    flags.append(f'{phase}: P-core effective clock {mhz:.0f} MHz vs calibration {base_mhz:.0f} MHz')
    return reasons, flags


# ---------------------------------------------------------------------------------------------------------------
# Pair identity

def normalize_command(cmd, label):
    """Launcher command line with the label property removed (the only intended difference between sides)."""
    if cmd is None:
        return None
    return ' '.join(cmd.replace('-Dalacer.benchmark.label=' + label, '-Dalacer.benchmark.label=<label>').split())


def comparable_environment(env, label):
    out = {k: v for k, v in env.items() if k not in VOLATILE_ENV}
    if 'mods' in out:
        # The loader lists mods in hash order, which differs between otherwise identical launches.
        out['mods'] = ' '.join(sorted(out['mods'].split()))
    if 'jvm.args' in env:
        out['jvm.args'] = env['jvm.args'].replace('-Dalacer.benchmark.label=' + label, '-Dalacer.benchmark.label=<label>')
    return out


def pair_identity(a, b, max_gap_s):
    """Checks that two passed launches, a then b in run order, form a valid A/A pair: same build, JVM arguments,
    options and hardware settings, run back to back. Returns a list of mismatch reasons (empty when identical)."""
    reasons = []
    if a['props_identity'] != b['props_identity']:
        reasons.append('launch options differ (other than the label)')
    if normalize_command(a['launcher']['cmdline'], a['label']) != normalize_command(b['launcher']['cmdline'], b['label']):
        reasons.append('launcher command lines differ (other than the label)')
    if a['launcher']['exe'] != b['launcher']['exe']:
        reasons.append('launcher executables differ')
    if a['config_sha'] != b['config_sha']:
        reasons.append('recorded phase configurations differ')
    if canonical_sha(comparable_environment(a['environment'], a['label'])) != \
            canonical_sha(comparable_environment(b['environment'], b['label'])):
        diff = sorted(k for k in set(a['environment']) | set(b['environment'])
                      if k not in VOLATILE_ENV and a['environment'].get(k) != b['environment'].get(k))
        reasons.append('exported environment differs: ' + ', '.join(diff[:8]))
    for key in ('manifest_sha', 'artifacts_sha', 'jdk_sha', 'hardware_sha', 'expected_affinity', 'phases_expected'):
        if a.get(key) != b.get(key):
            reasons.append(f'{key} differs between sides')
    gap = b['started_epoch'] - a['ended_epoch']
    if gap < 0 or gap > max_gap_s:
        reasons.append(f'launches not back to back: {gap:.0f} s between them (limit {max_gap_s} s)')
    return reasons
