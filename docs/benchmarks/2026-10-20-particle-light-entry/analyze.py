"""Session 21: analyze archived particle light entry A/B/B/A and startup launches against the predeclared gates.

Validates every record against its exported files, log, shared-config hash and the frozen source
manifest/snapshot (not today's working tree), then writes metrics.json, comparison.md and tails.md.
"""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import statistics as stats
import zipfile

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
OPTION = 'render.fastParticleLightEntry'
PREFIX = 'le21'
SHARED_CONFIG = '694ff84d8e5672cd7a2ec0ee1b5a3e4eadbe09552d5d65eef93e16f61ceacd31'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def percentile(values, p):
    return sorted(values)[max(0, math.ceil(len(values) * p / 100) - 1)]


def delta(a, b):
    return 100 * (b / a - 1)


def metrics(rows):
    frames = [r['frame_ns'] / 1e6 for r in rows]
    ticks = [r for r in rows if r['particles_ticked'] > 0]
    non_ticks = [r for r in rows if r['particles_ticked'] == 0]
    tick_frames = [r['frame_ns'] / 1e6 for r in ticks]
    passes = [r['particles_ns'] / 1e6 for r in rows]
    draws = [r['particle_draw_ns'] / 1e6 for r in rows]
    return {'frames': len(rows), 'tick_frames': len(ticks),
            'frame_ms': stats.mean(frames), 'frame_p95_ms': percentile(frames, 95), 'frame_p99_ms': percentile(frames, 99),
            'frame_max_ms': max(frames),
            'particle_pass_ms': stats.mean(passes), 'particle_draw_ms': stats.mean(draws),
            'particle_pass_excluding_draw_ms': stats.mean(p - d for p, d in zip(passes, draws)),
            'render_ms': stats.mean(r['render_ns'] / 1e6 for r in rows), 'swap_ms': stats.mean(r['swap_ns'] / 1e6 for r in rows),
            'tick_frame_ms': stats.mean(tick_frames), 'tick_frame_p95_ms': percentile(tick_frames, 95),
            'tick_frame_p99_ms': percentile(tick_frames, 99), 'tick_frame_max_ms': max(tick_frames),
            'non_tick_frame_ms': stats.mean(r['frame_ns'] / 1e6 for r in non_ticks),
            'non_tick_frame_p95_ms': percentile([r['frame_ns'] / 1e6 for r in non_ticks], 95),
            'non_tick_frame_p99_ms': percentile([r['frame_ns'] / 1e6 for r in non_ticks], 99),
            'particle_tick_ms': stats.mean(r['particle_tick_ns'] / 1e6 for r in ticks),
            'particles_rendered': stats.mean(r['particles_rendered'] for r in rows),
            'alloc_bytes_per_frame': stats.mean(r['alloc_bytes'] for r in rows),
            'alloc_bytes_per_second': sum(r['alloc_bytes'] for r in rows) / (sum(r['frame_ns'] for r in rows) / 1e9)}


def load_record(path, manifest):
    record = json.loads(path.read_text())
    folder = ROOT / record['source']
    for name, expected in record['files_sha256'].items():
        assert sha(folder / name) == expected, (record['label'], name)
    assert sha(ROOT / record['log']) == record['log_sha256'], record['label']
    assert record['shared_config_before_sha256'] == record['shared_config_after_sha256'] == SHARED_CONFIG
    assert record['frozen_manifest'] and record['source_snapshot'] == manifest['source_snapshot'], record['label']
    summary = json.loads((folder / 'summary.json').read_text())
    assert summary['environment']['alacer.configSource'] == 'build-defaults-and-jvm-overrides'
    assert summary['environment']['java.version'].startswith('25.')
    for phase, config in record['phase_configs'].items():
        assert json.loads((folder / (phase + '.json')).read_text())['config'] == config
        assert summary['phases'][phase]['config'] == config
        # Explicit options: the nine shipped optimizations on, every experiment off, no detail or verification.
        for key in ('fastChunkVisibility', 'fastEntityChunkLookup', 'throttleGlErrorChecks', 'avoidMatrixReads',
                    'cacheClouds', 'fastEntityShadows', 'fastParticleLight', 'fastParticleRemoval', 'fastParticleVertices'):
            assert config['render.' + key] == 'true', (record['label'], phase, key)
        for key in ('fastChunkFrustum', 'skipZeroTranslates', 'batchModelLists', 'fastParticleMovement',
                    'reuseParticleBoxes', 'scalarParticleEntityQuery'):
            assert config['render.' + key] == 'false', (record['label'], phase, key)
        if record['kind'] != 'diagnostic':
            assert all(v == 'false' for k, v in config.items() if k.endswith('.verify')), (record['label'], phase)
            assert config['profiler.particleDetail'] == 'false' and config['profiler.entityDetail'] == 'false'
    return record, folder, summary


def load_rows(folder, name):
    with (folder / (name + '.csv')).open() as f:
        return [{k: float(v) for k, v in row.items()} for row in csv.DictReader(f)]


def main():
    manifest = json.loads((HERE / 'manifest.json').read_text())
    snapshot = manifest['source_snapshot']
    assert sha(ROOT / snapshot['path']) == snapshot['sha256']
    with zipfile.ZipFile(ROOT / snapshot['path']) as source:
        for name, expected in manifest['source_sha256'].items():
            assert hashlib.sha256(source.read(name.replace('\\', '/'))).hexdigest() == expected
    records = [load_record(p, manifest) for p in sorted((HERE / 'runs').glob(PREFIX + '-*.record.json'))]
    controls = [r for r in records if r[0]['kind'] == 'control']
    absent = [r for r in records if r[0]['kind'] == 'absent']
    startup_on = [r for r in records if r[0]['kind'] == 'on']
    diagnostics = [r for r in records if r[0]['kind'] == 'diagnostic']
    assert len(controls) == 8, f'expected 8 controls, found {len(controls)}'
    data = []
    for record, folder, summary in controls:
        for phase in ('static', 'pan'):
            variants, segments = {}, {}
            for variant in ('a', 'b'):
                rows, cpu_ms, process_cpu_ms, frame_count = [], 0.0, 0.0, 0
                for segment in (variant + '1', variant + '2'):
                    name = phase + '-' + segment
                    segment_rows = load_rows(folder, name)
                    rows += segment_rows
                    segments[segment] = metrics(segment_rows)
                    s = summary['phases'][name]
                    assert s['config'][OPTION] == ('true' if variant == 'b' else 'false')
                    frame_count += s['frames']
                    cpu_ms += s['client_thread_cpu_ms_per_frame'] * s['frames']
                    process_cpu_ms += s['process_cpu_cores'] * s['recorded_wall_seconds'] * 1000
                variants[variant] = metrics(rows)
                variants[variant]['client_cpu_ms_per_frame'] = cpu_ms / frame_count
                variants[variant]['process_cpu_ms_per_frame'] = process_cpu_ms / frame_count
            a, b = variants['a'], variants['b']
            skip = ('frames', 'tick_frames')
            data.append({'label': record['label'], 'scale': record['scale'], 'phase': phase, 'a': a, 'b': b,
                         'segments': segments,
                         'delta_percent': {k: delta(a[k], b[k]) for k in a if k not in skip},
                         'drift_a2_vs_a1_percent': delta(segments['a1']['frame_ms'], segments['a2']['frame_ms']),
                         'b_pass_vs_a1_percent': delta(segments['a1']['particle_pass_ms'], b['particle_pass_ms']),
                         'b_pass_vs_a2_percent': delta(segments['a2']['particle_pass_ms'], b['particle_pass_ms']),
                         'b_frame_vs_a2_percent': delta(segments['a2']['frame_ms'], b['frame_ms'])})
    gates = []
    for scale in (1, 2):
        for phase in ('static', 'pan'):
            group = [r for r in data if r['scale'] == scale and r['phase'] == phase]
            assert len(group) == 4, (scale, phase, len(group))
            medians = {k: stats.median(r['delta_percent'][k] for r in group) for k in group[0]['delta_percent']}
            drift = stats.median(abs(r['drift_a2_vs_a1_percent']) for r in group)
            noisy = drift > 10
            checks = {
                '1_particle_pass_lower_in_3_of_4_launches': sum(r['delta_percent']['particle_pass_ms'] < 0 for r in group) >= 3,
                '2_median_particle_pass_saving_at_least_3_percent': medians['particle_pass_ms'] <= -3,
                '3_median_frame_mean_saving_at_least_1_percent': medians['frame_ms'] <= -1,
                '4_median_client_cpu_not_higher': medians['client_cpu_ms_per_frame'] <= 0,
                '5_median_allocation_regression_within_2_percent': medians['alloc_bytes_per_frame'] <= 2,
                '6a_median_frame_p95_within_5_percent': medians['frame_p95_ms'] <= 5,
                '6b_median_frame_p99_within_5_percent': medians['frame_p99_ms'] <= 5,
                '6c_median_tick_frame_mean_within_5_percent': medians['tick_frame_ms'] <= 5,
                '6d_median_tick_frame_p99_within_5_percent': medians['tick_frame_p99_ms'] <= 5,
                '7_particles_rendered_within_2_percent': all(abs(r['delta_percent']['particles_rendered']) <= 2 for r in group),
            }
            if noisy:
                checks['8_noisy_group_pass_beats_a1_and_a2_in_3_of_4_launches'] = sum(
                    r['b_pass_vs_a1_percent'] < 0 and r['b_pass_vs_a2_percent'] < 0 for r in group) >= 3
            gates.append({'scale': scale, 'phase': phase, 'median_delta_percent': medians,
                          'median_abs_drift_percent': drift, 'flagged_launches': [r['label'] for r in group if abs(r['drift_a2_vs_a1_percent']) > 10],
                          'noisy': noisy, 'checks': checks, 'passed': all(checks.values())})
    # Option on at startup versus hooks absent (separate launches, both without a toggle).
    startup = []
    for kind, group in (('absent', absent), ('on', startup_on)):
        for record, folder, summary in group:
            for phase in ('static', 'pan'):
                m = metrics(load_rows(folder, phase))
                m['client_cpu_ms_per_frame'] = summary['phases'][phase]['client_thread_cpu_ms_per_frame']
                assert summary['phases'][phase]['config'][OPTION] == str(kind == 'on').lower()
                startup.append({'label': record['label'], 'kind': kind, 'scale': record['scale'], 'phase': phase, 'metrics': m})
    startup_gates = []
    for scale in (1, 2):
        for phase in ('static', 'pan'):
            on = [r['metrics'] for r in startup if r['kind'] == 'on' and r['scale'] == scale and r['phase'] == phase]
            off = [r['metrics'] for r in startup if r['kind'] == 'absent' and r['scale'] == scale and r['phase'] == phase]
            if not on or not off:
                continue
            med = {k: (stats.median(x[k] for x in off), stats.median(x[k] for x in on)) for k in
                   ('frame_ms', 'particle_pass_ms', 'client_cpu_ms_per_frame', 'frame_p95_ms', 'frame_p99_ms', 'tick_frame_ms',
                    'tick_frame_p99_ms', 'alloc_bytes_per_frame', 'particles_rendered')}
            checks = {'median_frame_mean_lower': med['frame_ms'][1] < med['frame_ms'][0],
                      'median_particle_pass_lower': med['particle_pass_ms'][1] < med['particle_pass_ms'][0],
                      'median_cpu_not_higher': med['client_cpu_ms_per_frame'][1] <= med['client_cpu_ms_per_frame'][0],
                      'median_allocation_within_2_percent': delta(*med['alloc_bytes_per_frame']) <= 2}
            startup_gates.append({'scale': scale, 'phase': phase, 'launches_absent': len(off), 'launches_on': len(on),
                                  'medians_absent_on': med, 'change_percent': {k: delta(*v) for k, v in med.items()},
                                  'checks': checks, 'passed': all(checks.values())})
    result = {'prefix': PREFIX, 'option': OPTION, 'manifest_snapshot': snapshot, 'comparison': data, 'performance_gates': gates,
              'all_performance_gates_passed': all(g['passed'] for g in gates),
              'startup_comparison': startup, 'startup_gates': startup_gates,
              'all_startup_gates_passed': bool(startup_gates) and all(g['passed'] for g in startup_gates),
              'verification': [r[0]['verification_lines'] for r in diagnostics]}
    (HERE / 'metrics.json').write_text(json.dumps(result, indent=2) + '\n')

    out = ['# Session 21 particle light entry: measured results', '',
           'Java 25, display lists, 1920x1080, warmed A/B/B/A. A: vanilla chunk check and position copy (hooks installed,',
           'option off); B: range-test answer and reused position. Nine shipped optimizations on, every experiment off, all options explicit.',
           'B is compared with pooled A1+A2. Negative changes mean less time/allocation. Detail/JFR/verification off.', '',
           '| Launch | Phase | Pass A/B ms | Pass change | Pass excl. draw A/B ms | Draw A/B ms | Frame mean change | Frame p95 | Frame p99 | Tick-frame mean | Tick-frame p99 | CPU/frame | Alloc/frame | Rendered | A2/A1 drift | B vs A2 frame |',
           '|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|']
    for r in data:
        d, a, b = r['delta_percent'], r['a'], r['b']
        out.append(f"| {r['label']} | {r['phase']} | {a['particle_pass_ms']:.3f}/{b['particle_pass_ms']:.3f} | {d['particle_pass_ms']:+.2f}% | "
                   f"{a['particle_pass_excluding_draw_ms']:.3f}/{b['particle_pass_excluding_draw_ms']:.3f} | "
                   f"{a['particle_draw_ms']:.3f}/{b['particle_draw_ms']:.3f} | "
                   + ' | '.join(f"{d[k]:+.2f}%" for k in ('frame_ms', 'frame_p95_ms', 'frame_p99_ms', 'tick_frame_ms', 'tick_frame_p99_ms',
                                                         'client_cpu_ms_per_frame', 'alloc_bytes_per_frame', 'particles_rendered'))
                   + f" | {r['drift_a2_vs_a1_percent']:+.2f}% | {r['b_frame_vs_a2_percent']:+.2f}% |")
    out += ['', '## Predeclared gates (group medians of per-launch changes)', '',
            '| Scale / phase | Pass | Frame mean | CPU/frame | Alloc/frame | Frame p95 | Frame p99 | Tick-frame mean | Tick-frame p99 | Median abs drift | Result |',
            '|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|']
    for g in gates:
        m = g['median_delta_percent']
        out.append(f"| {g['scale']} / {g['phase']} | " + ' | '.join(f"{m[k]:+.2f}%" for k in (
            'particle_pass_ms', 'frame_ms', 'client_cpu_ms_per_frame', 'alloc_bytes_per_frame', 'frame_p95_ms', 'frame_p99_ms',
            'tick_frame_ms', 'tick_frame_p99_ms'))
            + f" | {g['median_abs_drift_percent']:.2f}%{' (noisy)' if g['noisy'] else ''} | "
            + ('PASS' if g['passed'] else 'FAIL: ' + ', '.join(k for k, v in g['checks'].items() if not v)) + ' |')
    out += ['', 'Source identity is checked against the frozen manifest and its ZIP snapshot.', '']
    if startup_gates:
        out += ['## Startup gates: option on at startup versus hooks absent (separate launches)', '',
                'Medians over launches; cross-launch, so confounded by launch-to-launch drift. Values absent -> on.', '',
                '| Scale / phase | Launches absent/on | Frame mean ms | Particle pass ms | CPU/frame ms | Frame p95 ms | Frame p99 ms | Tick-frame mean ms | Tick-frame p99 ms | Alloc KB/frame | Rendered | Result |',
                '|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|']
        for g in startup_gates:
            m, c = g['medians_absent_on'], g['change_percent']
            cells = []
            for k in ('frame_ms', 'particle_pass_ms', 'client_cpu_ms_per_frame', 'frame_p95_ms', 'frame_p99_ms', 'tick_frame_ms', 'tick_frame_p99_ms'):
                cells.append(f"{m[k][0]:.3f} -> {m[k][1]:.3f} ({c[k]:+.1f}%)")
            cells.append(f"{m['alloc_bytes_per_frame'][0]/1024:.1f} -> {m['alloc_bytes_per_frame'][1]/1024:.1f} ({c['alloc_bytes_per_frame']:+.1f}%)")
            cells.append(f"{m['particles_rendered'][0]:.0f} -> {m['particles_rendered'][1]:.0f}")
            out.append(f"| {g['scale']} / {g['phase']} | {g['launches_absent']}/{g['launches_on']} | " + ' | '.join(cells) + ' | '
                       + ('PASS' if g['passed'] else 'FAIL: ' + ', '.join(k for k, v in g['checks'].items() if not v)) + ' |')
        out += ['', '| Launch | Phase | Frame ms | Particle pass ms | Draw ms | CPU/frame ms | Rendered |', '|---|---|---:|---:|---:|---:|---:|']
        for r in startup:
            m = r['metrics']
            out.append(f"| {r['label']} | {r['phase']} | {m['frame_ms']:.3f} | {m['particle_pass_ms']:.3f} | {m['particle_draw_ms']:.3f} | "
                       f"{m['client_cpu_ms_per_frame']:.3f} | {m['particles_rendered']:.0f} |")
        out.append('')
    (HERE / 'comparison.md').write_text('\n'.join(out))

    tails = ['# Session 21 tails, CPU and allocation', '', 'All values A/B (pooled A1+A2 / pooled B1+B2), milliseconds unless marked.', '',
             '| Launch | Phase | Frame p95 | Frame p99 | Frame max | Non-tick p95 | Non-tick p99 | Tick-frame p95 | Tick-frame p99 | Tick-frame max | Render | Swap | Client CPU/frame | Process CPU/frame | Alloc KB/frame | Alloc MB/s |',
             '|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|']
    for r in data:
        keys = ('frame_p95_ms', 'frame_p99_ms', 'frame_max_ms', 'non_tick_frame_p95_ms', 'non_tick_frame_p99_ms', 'tick_frame_p95_ms',
                'tick_frame_p99_ms', 'tick_frame_max_ms', 'render_ms', 'swap_ms', 'client_cpu_ms_per_frame', 'process_cpu_ms_per_frame')
        tails.append(f"| {r['label']} | {r['phase']} | " + ' | '.join(f"{r['a'][k]:.3f}/{r['b'][k]:.3f}" for k in keys)
                     + f" | {r['a']['alloc_bytes_per_frame']/1024:.1f}/{r['b']['alloc_bytes_per_frame']/1024:.1f}"
                     + f" | {r['a']['alloc_bytes_per_second']/1e6:.1f}/{r['b']['alloc_bytes_per_second']/1e6:.1f} |")
    tails += ['', 'Tick-frame distributions hold roughly 200 (static) or 300 (pan) frames per variant; their tails are noisy.', '']
    (HERE / 'tails.md').write_text('\n'.join(tails))
    print('\n'.join(out[out.index('## Predeclared gates (group medians of per-launch changes)'):]))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, default=HERE)
    parser.add_argument('--prefix', default=PREFIX)
    opts = parser.parse_args()
    HERE = opts.archive.resolve()
    PREFIX = opts.prefix
    main()
