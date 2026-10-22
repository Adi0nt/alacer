#!/usr/bin/env python3
"""Session 31: evaluate an A/A pilot archive and write pilot.json and pilot.md (needs numpy, via aa_stats).

Reads only the archive: protocol.json, manifest.json, calibration.json, attempts/, pairs/ and records/. Uses only
accepted pairs (both launches passed every check, identical configuration, back to back); a rejected pair is
excluded whole, never one side. Thresholds come from the protocol and the calibration, never from the scored data.
"""
import json
import math
import statistics
from pathlib import Path

import aa_rules as rules
import aa_stats as st

METRICS = ('frame_mean_ms', 'p95_ms', 'p99_ms', 'slow1_mean_ms', 'tick_mean_ms', 'tick_p90_ms', 'tick_p99_ms',
           'client_cpu_ms_per_frame', 'process_cpu_ms_per_frame', 'alloc_per_tick_free_frame', 'terrain_setup_ms')
ENV_KEYS = ('background_max', 'background_delta', 'reference_dev', 'freq_delta', 'gpu_clock_delta', 'e_core_share')


def load_json(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def load_dir(folder):
    folder = Path(folder)
    if not folder.exists():
        return {}
    return {p.stem: load_json(p) for p in sorted(folder.glob('*.json')) if not p.name.endswith('.summary.json')}


def pair_env(protocol, rec_a, rec_b, phase, calibration):
    """Environmental covariates of one pair in one phase (for correlations with |d|)."""
    wa, wb = rec_a['monitor']['phases'].get(phase, {}), rec_b['monitor']['phases'].get(phase, {})
    bga, bgb = wa.get('background_cores_mean'), wb.get('background_cores_mean')
    out = {'background_max': max(bga, bgb) if None not in (bga, bgb) else None,
           'background_delta': abs(bgb - bga) if None not in (bga, bgb) else None}
    devs = []
    scene_cal = (calibration or {}).get('scenes', {}).get(rec_a['scene'], {})
    for rec in (rec_a, rec_b):
        ref = rec['metrics'].get(rules.REFERENCE_PRE)
        base = scene_cal.get(rules.REFERENCE_PRE, {}).get('frame_mean_ms', {}).get('median')
        if ref and base:
            devs.append(abs(math.log(ref['frame_mean_ms'] / base)))
    out['reference_dev'] = max(devs) if len(devs) == 2 else None
    fa, fb = wa.get('frequency_p_mean_mhz') or wa.get('frequency_mean_mhz'), wb.get('frequency_p_mean_mhz') or wb.get('frequency_mean_mhz')
    out['freq_delta'] = abs(math.log(fb / fa)) if fa and fb else None
    ga, gb = wa.get('gpu_clock_mean_mhz'), wb.get('gpu_clock_mean_mhz')
    out['gpu_clock_delta'] = abs(math.log(gb / ga)) if ga and gb else None
    shares = [r.get('processors', {}).get(phase, {}).get('e_core_share') for r in (rec_a, rec_b)]
    out['e_core_share'] = max(shares) if None not in shares else None
    return out


def in_process_blocks(record, segments):
    """ABBA blocks of the repeat segments of one launch: per block d = ln((s2 + s3) / (s1 + s4)) of frame mean
    (A = segments 1 and 4, B = 2 and 3), balanced for a linear drift within the block."""
    out = []
    names = [s for s in segments if s in record['metrics']]
    for i in range(0, len(names) - 3, 4):
        s = [record['metrics'][n]['frame_mean_ms'] for n in names[i:i + 4]]
        out.append(math.log((s[1] + s[2]) / (s[0] + s[3])))
    return out


def evaluate(archive, draws=st.DEFAULT_DRAWS):
    archive = Path(archive)
    protocol = load_json(archive / 'protocol.json')
    calibration = load_json(archive / 'calibration.json') if (archive / 'calibration.json').exists() else None
    attempts = load_dir(archive / 'attempts')
    pairs = load_dir(archive / 'pairs')
    records = load_dir(archive / 'records')
    gate = protocol['gate']
    power = protocol['power']
    state = rules.pilot_state(protocol, attempts, pairs)

    accepted = {}
    for pid, p in pairs.items():
        if p['status'] != 'accepted':
            continue
        rec_a, rec_b = records[p['labels']['a']], records[p['labels']['b']]
        # Re-check what the runner decided: an accepted pair must have two passed, identical launches.
        assert attempts[p['labels']['a']]['status'] == 'passed' and attempts[p['labels']['b']]['status'] == 'passed', pid
        first, second = (rec_a, rec_b) if p['order'] == 'ab' else (rec_b, rec_a)
        assert not rules.pair_identity(first, second, protocol['pilot']['max_pair_gap_s']), pid
        accepted.setdefault(p['scene'], []).append((p, rec_a, rec_b))

    results = {}
    pooled_inputs = {}
    for scene in protocol['pilot']['pairs']:
        results[scene] = {}
        rows_by_phase = accepted.get(scene, [])
        phases = rules.judged_phases(protocol, scene) + [ph for ph in (rules.REFERENCE_PRE, rules.REFERENCE_POST)
                                                         if ph in rules.expected_phases(protocol, scene)]
        for phase in phases:
            results[scene][phase] = {}
            for metric in METRICS:
                rows = []
                for p, ra, rb in rows_by_phase:
                    a, b = ra['metrics'][phase].get(metric), rb['metrics'][phase].get(metric)
                    if a is None or b is None or a <= 0 or b <= 0:
                        continue
                    row = {'d': math.log(b / a), 't': ra['started_epoch'], 'pair': p['pair_id']}
                    row.update(pair_env(protocol, ra, rb, phase, calibration))
                    rows.append(row)
                results[scene][phase][metric] = st.analyze_pairs(
                    rows, targets_pct=power['targets_pct'], planned_n=power['planned_pairs_per_scene'], draws=draws,
                    env_keys=ENV_KEYS if metric == gate['metric'] else ())
                if phase in rules.judged_phases(protocol, scene):
                    pooled_inputs.setdefault(metric, {}).setdefault(f'{scene}/{phase}', []).extend(
                        (r['pair'], r['d']) for r in rows)

    pooled = {m: st.pooled(v, planned_n=power['planned_pairs_per_scene'], draws=draws) for m, v in pooled_inputs.items()}

    # In-process (repeat segments) against separate-launch (reference-pre pairs) stability, where both exist.
    in_process = {}
    for scene in protocol['pilot']['pairs']:
        spec = protocol['scenes'][scene]
        if not spec.get('repeat_segments'):
            continue
        segs = ['repeat-%02d' % i for i in range(1, spec['repeat_segments'] + 1)]
        blocks = []
        for p, ra, rb in accepted.get(scene, []):
            blocks += in_process_blocks(ra, segs) + in_process_blocks(rb, segs)
        sep = results[scene].get(rules.REFERENCE_PRE, {}).get('frame_mean_ms', {})
        in_process[scene] = {
            'segment_seconds': spec['repeat_segment_seconds'], 'blocks': len(blocks),
            'in_process_block_spread_pct': st.pct(st.robust_scale_zero(blocks)) if blocks else None,
            'in_process_block_median_pct': st.pct(statistics.median(blocks)) if blocks else None,
            'separate_launch_reference_spread_pct': sep.get('robust_spread_pct'),
            'note': 'In-process blocks compare 2 x %g s against 2 x %g s of the same view in one launch; the separate-launch '
                    'figure is the reference-pre pair spread (%s s per side). Lengths differ; compare orders of magnitude.'
                    % (spec['repeat_segment_seconds'], spec['repeat_segment_seconds'], protocol['stability']['reference'])}

    # Verdict 1: the predeclared stability gate.
    gate_rows = []
    for scene, phases in gate['scene_phases'].items():
        for phase in phases:
            r = results.get(scene, {}).get(phase, {}).get(gate['metric'], {'n': 0})
            n = r['n']
            spread = r.get('robust_spread_pct')
            status = ('unresolved' if n < gate['min_accepted_pairs'] else
                      'pass' if spread is not None and spread <= gate['max_robust_spread_pct'] else 'fail')
            gate_rows.append({'scene': scene, 'phase': phase, 'pairs': n, 'robust_spread_pct': spread,
                              'spread_ci95_pct': [r['spread_ci']['lo_pct'], r['spread_ci']['hi_pct']] if r.get('spread_ci') else None,
                              'status': status})
    gate_status = ('fail' if any(g['status'] == 'fail' for g in gate_rows) else
                   'unresolved' if any(g['status'] == 'unresolved' for g in gate_rows) else 'pass')

    # Verdict 2: could a 2% effect be resolved with 80% power at the planned pair count?
    power_rows = []
    target = power['decision_target_pct']
    for scene, phases in gate['scene_phases'].items():
        for phase in phases:
            r = results.get(scene, {}).get(phase, {}).get(gate['metric'], {})
            res = r.get('resolution', {}).get('planned_n')
            mde = res['mde80_pct'] if res else None
            power_rows.append({'scene': scene, 'phase': phase, 'planned_pairs': power['planned_pairs_per_scene'],
                               'mde80_pct': mde, 'pairs_needed': r.get('pairs_needed_mde80'),
                               'status': 'unresolved' if mde is None else 'yes' if mde <= target else 'no'})
    pooled_gate = pooled.get(gate['metric'], {})
    pooled_mde = pooled_gate.get('planned', {}).get('normal_mde80_pct')
    power_status = ('unresolved' if any(p['status'] == 'unresolved' for p in power_rows) else
                    'yes' if all(p['status'] == 'yes' for p in power_rows) else
                    'pooled only' if pooled_mde is not None and pooled_mde <= target else 'no')

    # Verdict 3: do disturbances or other issues invalidate the conclusion?
    launches = [a for a in attempts.values() if a.get('stage') == 'pilot']
    pair_decisions = [p for p in pairs.values()]
    rejected = [p for p in pair_decisions if p['status'] == 'rejected']
    reasons = {}
    for p in rejected:
        for r in p['reasons']:
            key = r.split(':')[0] if ':' in r else r
            reasons[key] = reasons.get(key, 0) + 1
    issues = []
    rate = len(rejected) / len(pair_decisions) if pair_decisions else None
    if rate is not None and rate > protocol['validity']['max_rejected_pair_fraction']:
        issues.append(f'{len(rejected)} of {len(pair_decisions)} pair attempts rejected '
                      f'(limit {100 * protocol["validity"]["max_rejected_pair_fraction"]:.0f}%)')
    exhausted = [f'{s["scene"]}-{s["n"]}' for s in state['slots'] if s['status'] == 'exhausted']
    if exhausted:
        issues.append('pair slots exhausted their retries: ' + ', '.join(exhausted))
    pending = [f'{s["scene"]}-{s["n"]}' for s in state['slots'] if s['status'] in ('pending', 'retry', 'undecided')]
    if pending:
        issues.append('pilot incomplete: ' + ', '.join(pending))
    if calibration is None or not calibration.get('pass'):
        issues.append('no passing calibration')
    for g in gate_rows:
        r = results.get(g['scene'], {}).get(g['phase'], {}).get(gate['metric'], {})
        trend = r.get('trend_vs_time', {})
        if trend.get('p') is not None and trend['p'] < protocol['validity']['trend_p']:
            issues.append(f'{g["scene"]}/{g["phase"]}: time trend in pair changes (rho {trend["rho"]:.2f}, p {trend["p"]:.3f})')
        for key, c in (r.get('abs_change_vs_env') or {}).items():
            if c.get('p') is not None and c['p'] < protocol['validity']['env_correlation_p'] and c['rho'] > 0:
                issues.append(f'{g["scene"]}/{g["phase"]}: |change| rises with {key} (rho {c["rho"]:.2f}, p {c["p"]:.3f})')
    flags = {}
    unavailable = {}
    for label, rec in records.items():
        if rec.get('stage') != 'pilot':
            continue
        for f in rec.get('flags', []):
            key = f.split(': ', 1)[-1].split(' (')[0]
            flags[key] = flags.get(key, 0) + 1
        for phase, w in rec['monitor']['phases'].items():
            for source, a in w['availability'].items():
                if source != 'gpu' and a.get('fraction_ok', 1) < 1:
                    unavailable.setdefault(source, set()).update(a.get('reasons', []))
    validity_status = 'invalidated' if any(i.startswith(('pilot incomplete', 'no passing')) for i in issues) else \
        'qualified' if issues else 'no disturbance detected by the predeclared rules'

    verdict = {
        'stability_gate': {'status': gate_status, 'rule': f'robust per-pair spread of {gate["metric"]} <= '
                           f'{gate["max_robust_spread_pct"]}% in every required scene-phase with >= '
                           f'{gate["min_accepted_pairs"]} accepted pairs', 'rows': gate_rows},
        'power_2pct': {'status': power_status, 'rule': f'MDE80 of the median of {power["planned_pairs_per_scene"]} '
                       f'pairs <= {target}% in every required scene-phase', 'rows': power_rows,
                       'pooled_mde80_pct': pooled_mde},
        'validity': {'status': validity_status, 'issues': issues, 'rejected_pair_reasons': reasons,
                     'flags': flags, 'telemetry_not_always_available': {k: sorted(v) for k, v in unavailable.items()}},
        'note': 'Passing the stability gate does not by itself imply adequate power for 2%; both are reported. '
                'Resampled intervals from a few pairs are planning estimates, not proof of measurement accuracy.',
    }
    # Exploratory overhead stage: monitored against unmonitored d0 launches (not A/A, never pooled with the pilot).
    overhead = {}
    ov = protocol.get('overhead')
    if ov:
        series = protocol['series']
        for n in range(1, ov['pairs'] + 1):
            on, off = records.get(f'{series}-ov-{ov["scene"]}-{n}-mon'), records.get(f'{series}-ov-{ov["scene"]}-{n}-nomon')
            if not on or not off:
                continue
            overhead[n] = {ph: st.pct(math.log(on['metrics'][ph]['frame_mean_ms'] / off['metrics'][ph]['frame_mean_ms']))
                           for ph in rules.judged_phases(protocol, ov['scene'])}
    monitor_cost = [r['monitor']['launch'].get('monitor_self_cores') for r in records.values()
                    if r.get('stage') == 'pilot' and r['monitor']['launch'].get('monitor_self_cores') is not None]
    report = {
        'protocol_sha': rules.canonical_sha(protocol), 'series': protocol['series'],
        'manifest': load_json(archive / 'manifest.json') if (archive / 'manifest.json').exists() else None,
        'calibration': calibration,
        'launches': [{'label': a['label'], 'stage': a.get('stage'), 'scene': a.get('scene'), 'status': a['status'],
                      'reasons': a.get('reasons', []), 'flags': a.get('flags', []), 'started_utc': a.get('started_utc'),
                      'elapsed_s': a.get('elapsed_seconds')} for a in sorted(attempts.values(), key=lambda x: x.get('started_epoch') or 0)],
        'pairs': sorted(pairs.values(), key=lambda p: p.get('decided_epoch') or 0),
        'slots': state['slots'],
        'monitoring': {'monitor_self_cores_mean': statistics.fmean(monitor_cost) if monitor_cost else None,
                       'series': {label: rec.get('series_5s') for label, rec in records.items() if rec.get('stage') == 'pilot'},
                       'per_launch': {label: rec['monitor']['phases'] for label, rec in records.items() if rec.get('stage') == 'pilot'}},
        'environment': next((r.get('hardware') for r in records.values() if r.get('hardware')), None),
        'results': results, 'pooled': pooled, 'in_process': in_process, 'verdict': verdict,
        'overhead_exploratory': {'frame_mean_change_monitored_vs_not_pct': overhead,
                                 'note': 'Exploratory: two or so pairs bound the monitor\'s effect only coarsely; the '
                                         'direct measure is the monitor thread\'s own CPU time.'},
        'pilot_launches': len(launches),
    }
    return report


def fmt(v, digits=2, suffix='%'):
    return '-' if v is None else f'{v:+.{digits}f}{suffix}' if suffix == '%' else f'{v:.{digits}f}{suffix}'


def markdown(report, protocol):
    v = report['verdict']
    gate = protocol['gate']
    lines = [f'# A/A pilot `{report["series"]}`: summary', '',
             'Generated by `tools/aa_pilot.py evaluate` from this archive. Synthetic data, container runs and '
             'software-GL runs are never evidence about the reference machine.', '',
             '## Verdict', '',
             f'1. **Stability gate: {v["stability_gate"]["status"]}.** {v["stability_gate"]["rule"]}.',
             f'2. **2% resolvable with 80% power at {protocol["power"]["planned_pairs_per_scene"]} pairs per scene: '
             f'{v["power_2pct"]["status"]}** (pooled over the judged scene-phases: MDE80 '
             f'{fmt(v["power_2pct"]["pooled_mde80_pct"], 2, "%").lstrip("+")}).',
             f'3. **Validity: {v["validity"]["status"]}.**'] + [f'   - {i}' for i in v['validity']['issues']] + [
             '', v['note'], '', '## Gate rows', '',
             '| Scene/phase | Accepted pairs | Robust spread | 95% interval of the spread | Gate | MDE80 at planned n | 2% |',
             '|---|---|---|---|---|---|---|']
    for g, p in zip(v['stability_gate']['rows'], v['power_2pct']['rows']):
        ci = g['spread_ci95_pct']
        lines.append(f'| {g["scene"]}/{g["phase"]} | {g["pairs"]} | {fmt(g["robust_spread_pct"]).lstrip("+")} | '
                     f'{"-" if not ci else f"{ci[0]:.2f}-{ci[1]:.2f}%"} | {g["status"]} | '
                     f'{fmt(p["mde80_pct"]).lstrip("+")} | {p["status"]} |')
    lines += ['', f'## Per scene and phase ({gate["metric"]} and secondary metrics)', '',
              '| Scene/phase | Metric | n | Median | Mean | Robust spread | Max abs | Outliers | Median 95% CI | n for 1/2/5% (normal) |',
              '|---|---|---|---|---|---|---|---|---|---|']
    for scene, phases in report['results'].items():
        for phase, metrics in phases.items():
            for metric in ('frame_mean_ms', 'p99_ms', 'tick_mean_ms', 'client_cpu_ms_per_frame', 'process_cpu_ms_per_frame'):
                r = metrics.get(metric, {})
                if not r.get('n'):
                    continue
                need = r.get('pairs_needed_mde80', {})
                needs = '/'.join(str(need.get(k, {}).get('normal') or '>512') for k in ('1%', '2%', '5%'))
                ci = r.get('median_ci95_pct')
                lines.append(f'| {scene}/{phase} | {metric} | {r["n"]} | {fmt(r["median_pct"])} | {fmt(r["mean_pct"])} | '
                             f'{fmt(r["robust_spread_pct"]).lstrip("+")} | {fmt(r["max_abs_pct"]).lstrip("+")} | '
                             f'{len(r["outliers"])} | {"-" if not ci else f"{ci[0]:+.2f} to {ci[1]:+.2f}%"} | {needs} |')
    if report['in_process']:
        lines += ['', '## In-process against separate-launch stability', '']
        for scene, r in report['in_process'].items():
            lines.append(f'- {scene}: {r["blocks"]} in-process ABBA blocks, spread {fmt(r["in_process_block_spread_pct"]).lstrip("+")}; '
                         f'separate-launch reference pairs {fmt(r["separate_launch_reference_spread_pct"]).lstrip("+")}. {r["note"]}')
    lines += ['', '## Launches and pairs', '',
              f'{report["pilot_launches"]} pilot launch attempts; {len(report["pairs"])} pair decisions.', '',
              '| Pair | Status | Reasons |', '|---|---|---|']
    for p in report['pairs']:
        lines.append(f'| {p["pair_id"]} | {p["status"]} | {"; ".join(p["reasons"])[:300] or "-"} |')
    mc = report['monitoring']['monitor_self_cores_mean']
    lines += ['', '## Monitoring', '',
              f'- Monitor thread CPU: {"-" if mc is None else f"{1000 * mc:.2f} ms per second ({100 * mc:.3f}% of one core)"}.',
              '- Telemetry not always available: ' + (', '.join(f'{k} ({"; ".join(v) or "no reason"})' for k, v in
                                                        v['validity']['telemetry_not_always_available'].items()) or 'none') + '.',
              '- Flags (not rejections): ' + (', '.join(f'{k} x{n}' for k, n in v['validity']['flags'].items()) or 'none') + '.',
              '']
    return '\n'.join(lines)
