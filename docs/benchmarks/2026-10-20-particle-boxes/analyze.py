"""Analyze archived movement experiments; validate measured snapshots, not today's source tree."""
import csv
import argparse
import hashlib
import json
import math
from pathlib import Path
import statistics as stats
import zipfile

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
OPTION = 'render.reuseParticleBoxes'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def percentile(values, p):
    return sorted(values)[max(0, math.ceil(len(values) * p / 100) - 1)]


def delta(a, b):
    return 100 * (b / a - 1)


def metrics(rows):
    ticks = [r for r in rows if r['particles_ticked'] > 0]
    assert ticks
    frames = [r['frame_ns'] / 1e6 for r in rows]
    tick_ms = [r['tick_ns'] / 1e6 for r in ticks]
    tick_frames = [r['frame_ns'] / 1e6 for r in ticks]
    particle = [r['particle_tick_ns'] / 1e6 for r in ticks]
    non_ticks = [r for r in rows if r['particles_ticked'] == 0]
    return {'frames': len(rows), 'tick_frames': len(ticks), 'frame_ms': stats.mean(frames),
            'frame_p95_ms': percentile(frames, 95), 'frame_p99_ms': percentile(frames, 99),
            'tick_ms': stats.mean(tick_ms), 'tick_p95_ms': percentile(tick_ms, 95),
            'tick_p99_ms': percentile(tick_ms, 99), 'particle_tick_ms': stats.mean(particle),
            'tick_frame_ms': stats.mean(tick_frames), 'tick_frame_p95_ms': percentile(tick_frames, 95),
            'tick_frame_p99_ms': percentile(tick_frames, 99),
            'tick_frame_max_ms': max(tick_frames), 'frame_max_ms': max(frames),
            'particle_tick_p95_ms': percentile(particle, 95), 'particle_tick_p99_ms': percentile(particle, 99),
            'particles_per_tick_frame': stats.mean(r['particles_ticked'] for r in ticks),
            'alloc_bytes_per_frame': stats.mean(r['alloc_bytes'] for r in rows),
            'alloc_bytes_per_second': sum(r['alloc_bytes'] for r in rows) / (sum(r['frame_ns'] for r in rows) / 1e9),
            'alloc_bytes_per_tick_frame': stats.mean(r['alloc_bytes'] for r in ticks),
            'non_tick_frame_ms': stats.mean(r['frame_ns'] / 1e6 for r in non_ticks),
            'non_tick_frame_p95_ms': percentile([r['frame_ns'] / 1e6 for r in non_ticks], 95),
            'non_tick_frame_p99_ms': percentile([r['frame_ns'] / 1e6 for r in non_ticks], 99),
            'non_tick_alloc_bytes_per_frame': stats.mean(r['alloc_bytes'] for r in non_ticks)}


def load_record(path):
    record = json.loads(path.read_text())
    folder = ROOT / record['source']
    for name, expected in record['files_sha256'].items():
        assert sha(folder / name) == expected, (record['label'], name)
    assert sha(ROOT / record['log']) == record['log_sha256']
    assert record['shared_config_before_sha256'] == record['shared_config_after_sha256']
    snapshot = record['source_snapshot']
    assert sha(ROOT / snapshot['path']) == snapshot['sha256']
    with zipfile.ZipFile(ROOT / snapshot['path']) as source:
        for name, expected in record['source_sha256'].items():
            assert hashlib.sha256(source.read(name.replace('\\', '/'))).hexdigest() == expected
    summary = json.loads((folder / 'summary.json').read_text())
    assert summary['environment']['alacer.configSource'] == 'build-defaults-and-jvm-overrides'
    # The runner validated the predeclared effective config. Compare each raw export against
    # that measured record, without importing today's launcher or looking at today's sources.
    for phase, config in record['phase_configs'].items():
        assert json.loads((folder / (phase + '.json')).read_text())['config'] == config
        assert summary['phases'][phase]['config'] == config
    return record, folder, summary


def main():
    records = [load_record(p) for p in sorted((HERE / 'runs').glob('*.record.json'))]
    controls = [r for r in records if not r[0]['diagnostic']]
    assert len(controls) == 8, f'expected 8 controls, found {len(controls)}'
    assert len({json.dumps(r[0]['source_sha256'], sort_keys=True) for r in controls}) == 1, 'mixed measured builds'
    data = []
    for record, folder, summary in controls:
        for phase in ('static', 'pan'):
            variants = {}
            segments = {}
            for variant in ('a', 'b'):
                rows = []
                cpu_ms, process_cpu_ms, frame_count = 0, 0, 0
                for segment in (variant + '1', variant + '2'):
                    name = phase + '-' + segment
                    with (folder / (name + '.csv')).open() as f:
                        segment_rows = [{k: float(v) for k, v in row.items()} for row in csv.DictReader(f)]
                    rows += segment_rows
                    segments[segment] = metrics(segment_rows)
                    s = summary['phases'][name]
                    frame_count += s['frames']
                    cpu_ms += s['client_thread_cpu_ms_per_frame'] * s['frames']
                    assert s['process_cpu_cores'] >= 0
                    process_cpu_ms += s['process_cpu_cores'] * s['recorded_wall_seconds'] * 1000
                variants[variant] = metrics(rows)
                variants[variant]['client_cpu_ms_per_frame'] = cpu_ms / frame_count
                variants[variant]['process_cpu_ms_per_frame'] = process_cpu_ms / frame_count
            a, b = variants['a'], variants['b']
            changes = {k: delta(a[k], b[k]) for k in a if k not in ('frames', 'tick_frames')}
            data.append({'label': record['label'], 'scale': record['scale'], 'phase': phase,
                         'a': a, 'b': b, 'delta_percent': changes, 'segments': segments,
                         'b_vs_a2_delta_percent': {k: delta(segments['a2'][k], b[k]) for k in a
                                                  if k not in ('frames', 'tick_frames', 'client_cpu_ms_per_frame', 'process_cpu_ms_per_frame')}})
    gates = []
    for scale in (1, 2):
        for phase in ('static', 'pan'):
            group = [r for r in data if r['scale'] == scale and r['phase'] == phase]
            medians = {k: stats.median(r['delta_percent'][k] for r in group) for k in group[0]['delta_percent']}
            checks = {'every_launch_particle_tick_improves': all(r['delta_percent']['particle_tick_ms'] < 0 for r in group),
                      'median_particle_tick_saving_at_least_2_percent': medians['particle_tick_ms'] <= -2,
                      'frame_p95_within_5_percent': medians['frame_p95_ms'] <= 5,
                      'tick_p95_within_5_percent': medians['tick_p95_ms'] <= 5,
                      'tick_frame_p95_within_5_percent': medians['tick_frame_p95_ms'] <= 5,
                      'client_cpu_within_2_percent': medians['client_cpu_ms_per_frame'] <= 2,
                      'allocation_within_2_percent': medians['alloc_bytes_per_frame'] <= 2,
                      'tick_frame_allocation_saving_at_least_2_percent': medians['alloc_bytes_per_tick_frame'] <= -2,
                      'all_populations_within_2_percent': all(abs(r['delta_percent']['particles_per_tick_frame']) <= 2 for r in group)}
            gates.append({'scale': scale, 'phase': phase, 'median_delta_percent': medians, 'checks': checks,
                          'passed': all(checks.values())})
    result = {'comparison': data, 'performance_gates': gates,
              'all_performance_gates_passed': all(g['passed'] for g in gates),
              'verification': [r[0]['verification_lines'] for r in records if r[0].get('kind') == 'diagnostic']}
    (HERE / 'metrics.json').write_text(json.dumps(result, indent=2) + '\n')
    out = ['# Session 18 particle Box reuse: measured results', '',
           'Java 25, display lists, 1920x1080, warmed A/B/B/A. A: vanilla Box allocation; B: guarded immutable zero-offset Box reuse.',
           'Eight shipped optimizations on; Session 17 cached movement reads off in both. Negative changes mean less time/allocation.',
           'Tick figures include only frames with particle ticks. Detail timers/verification/JFR off.', '',
           '| Launch | Phase | Particle tick A/B ms | Client tick mean change | Tick-frame mean change | Tick-frame p95 change | Tick-frame p99 change | Frame mean change | Frame p95 change | CPU change | Allocation change | Population change |',
           '|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|']
    for r in data:
        d = r['delta_percent']
        values = [d[k] for k in ('tick_ms', 'tick_frame_ms', 'tick_frame_p95_ms', 'tick_frame_p99_ms', 'frame_ms', 'frame_p95_ms',
                                 'client_cpu_ms_per_frame', 'alloc_bytes_per_frame', 'particles_per_tick_frame')]
        out.append(f"| {r['label']} | {r['phase']} | {r['a']['particle_tick_ms']:.3f}/{r['b']['particle_tick_ms']:.3f} | "
                   + ' | '.join(f'{v:+.2f}%' for v in values) + ' |')
    out += ['', '## Predeclared gates', '']
    for g in gates:
        out.append(f"- Scale {g['scale']}, {g['phase']}: {'PASS' if g['passed'] else 'FAIL'}. "
                   + '; '.join(k for k, v in g['checks'].items() if not v))
    out += ['', '## Drift and allocation checks', '',
            '| Launch | Phase | Frame A1/A2/B ms | B vs A2 frame change | Tick-frame alloc A/B KB | Non-tick alloc A/B KB |',
            '|---|---|---:|---:|---:|---:|']
    for r in data:
        out.append(f"| {r['label']} | {r['phase']} | {r['segments']['a1']['frame_ms']:.3f}/{r['segments']['a2']['frame_ms']:.3f}/{r['b']['frame_ms']:.3f}"
                   f" | {r['b_vs_a2_delta_percent']['frame_ms']:+.2f}%"
                   f" | {r['a']['alloc_bytes_per_tick_frame']/1024:.1f}/{r['b']['alloc_bytes_per_tick_frame']/1024:.1f}"
                   f" | {r['a']['non_tick_alloc_bytes_per_frame']/1024:.1f}/{r['b']['non_tick_alloc_bytes_per_frame']/1024:.1f} |")
    out += ['', 'Source identity is checked against archived ZIP snapshots. Analysis does not require the working tree',
            'to still match the measured build. Raw CSVs and logs remain local; their hashes are verified.', '']
    (HERE / 'comparison.md').write_text('\n'.join(out))
    details = ['# Session 18 tails and CPU', '',
               'All values are A/B, in milliseconds unless marked otherwise. No launch is excluded.',
               'Process CPU includes the integrated server and other Java threads; it is reconstructed from',
               'the exported phase process_cpu_cores and recorded_wall_seconds (rounded at export).', '',
               '| Launch | Phase | Frame p95 | Frame p99 | Tick p95 | Tick p99 | Tick-frame p95 | Tick-frame p99 | Particle-tick p95 | Particle-tick p99 | Client CPU/frame | Process CPU/frame | Allocation MB/s |',
               '|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|']
    for r in data:
        keys = ('frame_p95_ms', 'frame_p99_ms', 'tick_p95_ms', 'tick_p99_ms', 'tick_frame_p95_ms',
                'tick_frame_p99_ms', 'particle_tick_p95_ms', 'particle_tick_p99_ms',
                'client_cpu_ms_per_frame', 'process_cpu_ms_per_frame')
        details.append(f"| {r['label']} | {r['phase']} | "
                       + ' | '.join(f"{r['a'][k]:.3f}/{r['b'][k]:.3f}" for k in keys)
                       + f" | {r['a']['alloc_bytes_per_second']/1e6:.2f}/{r['b']['alloc_bytes_per_second']/1e6:.2f} |")
    details += ['', 'Tick-only distributions contain roughly 200 static or 300 pan frames per variant.',
                'Tail estimates are therefore noisy. Frame time includes all game/client/render work;',
                'tick-frame allocation is not an allocation count attributed solely to Entity.move.', '']
    (HERE / 'tails.md').write_text('\n'.join(details))
    print(json.dumps(gates, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, default=HERE, help='Analyze an independent replay archive')
    HERE = parser.parse_args().archive.resolve()
    main()
