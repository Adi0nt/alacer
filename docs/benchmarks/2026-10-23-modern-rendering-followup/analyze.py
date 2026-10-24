"""Read this follow-up's records only; never change the original scored archive."""
import csv
import gzip
import json
import statistics
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
PHASES = ('static', 'pan', 'fly')
LAYERS = ('terrain_solid', 'terrain_cutout_mipped', 'terrain_cutout', 'terrain_translucent')


def metrics(receipt):
    summary_path = ROOT / receipt['summary']
    summary = json.loads(summary_path.read_text())
    result = {}
    for phase in PHASES:
        p = summary['phases'][phase]
        with (summary_path.parent / (phase + '.csv')).open(newline='') as f:
            rows = csv.DictReader(f)
            count = total = 0
            for row in rows:
                total += int(row['visible_chunks'])
                count += 1
        result[phase] = {
            'frame_ms': 1000 / p['avg_fps'],
            'cpu_ms': p['client_thread_cpu_ms_per_frame'],
            'p99_ms': p['frame_p99_ms'],
            'slow1_ms': 1000 / p['one_percent_low_fps'],
            'alloc_bytes': p['alloc_mean_bytes'],
            'visible_chunks': total / count,
            'visibility_ms': p['visibility_mean_ms'],
            'terrain_layers_ms': sum(p[k + '_mean_ms'] for k in LAYERS),
            'render_ms': p['render_mean_ms'],
            'setup_ms': p['terrain_setup_mean_ms'],
            'upload_ms': p['chunk_upload_mean_ms'],
            'swap_ms': p['swap_mean_ms'],
            'process_cpu_cores': p['process_cpu_cores'],
            **{k + '_ms': p[k + '_mean_ms'] for k in LAYERS},
        }
    return result


def compare(receipts, baseline):
    if len(receipts) != 4:
        raise ValueError('Requires exactly four completed launches')
    runs = [{'label': r['label'], 'arm': r['arm'], 'phases': metrics(r)} for r in receipts]
    phases = {}
    for phase in PHASES:
        pairs = []
        for i in (0, 2):
            pair = runs[i:i+2]
            a = next(r['phases'][phase] for r in pair if r['arm'] == baseline)
            b = next(r['phases'][phase] for r in pair if r['arm'] == 'V')
            pairs.append({k: 100 * (b[k] / a[k] - 1) if a[k] else None for k in a})
        phases[phase] = {
            k: {'pair_changes_pct': [p[k] for p in pairs],
                'median_change_pct': statistics.median(p[k] for p in pairs) if all(p[k] is not None for p in pairs) else None,
                'baseline_values': [r['phases'][phase][k] for r in runs if r['arm'] == baseline],
                'vao_values': [r['phases'][phase][k] for r in runs if r['arm'] == 'V']}
            for k in runs[0]['phases'][phase]}
    visible_ok = all(abs(v) <= 1 for v in phases['pan']['visible_chunks']['pair_changes_pct'])
    large_gap = any(all(v >= 20 for v in phases['pan'][k]['pair_changes_pct']) for k in ('frame_ms', 'cpu_ms'))
    return {'baseline': baseline, 'runs': runs, 'phases': phases,
            'pan_visible_pair_bound_passed': visible_ok,
            'large_pan_gap_reproduced': visible_ok and large_gap,
            'selection_evidence': False}
