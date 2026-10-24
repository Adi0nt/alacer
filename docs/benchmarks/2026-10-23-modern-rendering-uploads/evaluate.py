"""Evaluates balanced pairs of Control (A) vs Candidate (B) for OpenGL 4.5 direct buffer uploads."""
import csv
import gzip
import json
from pathlib import Path
import statistics
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
LAYERS = ['terrain_solid', 'terrain_cutout_mipped', 'terrain_cutout', 'terrain_translucent']


def parse_metrics(raw_dir, phase):
    data = json.loads((raw_dir / 'summary.json').read_text())
    p = data['phases'][phase]
    csv_gz = raw_dir / (phase + '.csv.gz')
    total = count = 0
    if csv_gz.exists():
        with gzip.open(csv_gz, 'rt', newline='', encoding='utf-8') as f:
            rows = csv.DictReader(f)
            for row in rows:
                total += int(row['visible_chunks'])
                count += 1
    elif (raw_dir / (phase + '.csv')).exists():
        with open(raw_dir / (phase + '.csv'), 'r', newline='', encoding='utf-8') as f:
            rows = csv.DictReader(f)
            for row in rows:
                total += int(row['visible_chunks'])
                count += 1

    return {
        'frame_ms': 1000 / p['avg_fps'],
        'cpu_ms': p['client_thread_cpu_ms_per_frame'],
        'p99_ms': p['frame_p99_ms'],
        'slow1_ms': 1000 / p['one_percent_low_fps'],
        'alloc_bytes': p['alloc_mean_bytes'],
        'visible_chunks': total / count if count else 0,
        'visibility_ms': p['visibility_mean_ms'],
        'terrain_layers_ms': sum(p[k + '_mean_ms'] for k in LAYERS),
        'render_ms': p['render_mean_ms'],
        'setup_ms': p['terrain_setup_mean_ms'],
        'upload_ms': p['chunk_upload_mean_ms'],
        'swap_ms': p['swap_mean_ms'],
        'process_cpu_cores': p['process_cpu_cores'],
        'chunk_vbo_calls': p['chunk_vbo_calls_mean'],
        'chunk_updates_published': p['chunk_updates_published_mean'],
        **{k + '_ms': p[k + '_mean_ms'] for k in LAYERS}
    }


def evaluate_archive(bench_dir=None):
    bench_dir = Path(bench_dir) if bench_dir else HERE
    launches = json.loads((bench_dir / 'launches.json').read_text())
    by_scene = {}
    for r in launches:
        if r.get('exit_code', 0) != 0:
            continue
        scene = r['scene']
        by_scene.setdefault(scene, []).append(r)

    evaluation = {
        'benchmark': '2026-10-23-modern-rendering-uploads',
        'scenes': {},
        'gates_pass': True,
        'failed_gates': []
    }

    for scene, runs in by_scene.items():
        is_terrain = (scene == 'terrain')
        phases = ['steady', 'pan', 'sort', 'fly'] if is_terrain else ['static', 'pan', 'fly']
        # Order: ABBABAAB = Pairs: (1A, 2B), (4A, 3B), (6A, 5B), (7A, 8B)
        run_map = {r['index']: r for r in runs}
        pairs = [
            (run_map[1], run_map[2]),
            (run_map[4], run_map[3]),
            (run_map[6], run_map[5]),
            (run_map[7], run_map[8])
        ]

        scene_res = {'pairs': [], 'medians': {}}
        for pair_idx, (rA, rB) in enumerate(pairs, 1):
            pair_data = {'pair': f'{rA["label"]} (Control) vs {rB["label"]} (Candidate)', 'phases': {}}
            pathA = bench_dir / 'raw' / rA['relative_raw_dir']
            pathB = bench_dir / 'raw' / rB['relative_raw_dir']
            for ph in phases:
                mA = parse_metrics(pathA, ph)
                mB = parse_metrics(pathB, ph)
                deltas = {}
                for k in mA:
                    valA = mA[k]
                    valB = mB[k]
                    pct = ((valB - valA) / valA * 100) if valA != 0 else 0
                    deltas[k] = {'control': valA, 'candidate': valB, 'delta_pct': pct}
                pair_data['phases'][ph] = deltas
            scene_res['pairs'].append(pair_data)

        # Compute median deltas across the 4 pairs
        for ph in phases:
            scene_res['medians'][ph] = {}
            for k in scene_res['pairs'][0]['phases'][ph]:
                pcts = [p['phases'][ph][k]['delta_pct'] for p in scene_res['pairs']]
                scene_res['medians'][ph][k] = statistics.median(pcts)

        evaluation['scenes'][scene] = scene_res

    # Check gates
    for scene in ['default', 'terrain']:
        if scene not in evaluation['scenes']:
            continue
        meds = evaluation['scenes'][scene]['medians']
        # Gate 1: Pan frame time improvement or <= 0% regression
        pan_frame_med = meds['pan']['frame_ms']
        if pan_frame_med > 0.0:
            evaluation['gates_pass'] = False
            evaluation['failed_gates'].append(f'{scene} pan frame time regressed {pan_frame_med:.2f}% (limit 0.0%)')

        # Gate 2: Pan CPU/frame improvement or <= 0% regression
        pan_cpu_med = meds['pan']['cpu_ms']
        if pan_cpu_med > 0.0:
            evaluation['gates_pass'] = False
            evaluation['failed_gates'].append(f'{scene} pan CPU/frame regressed {pan_cpu_med:.2f}% (limit 0.0%)')

        # Gate 3: No scored moving phase frame regression > 1.0%
        for ph in meds:
            if ph in ('pan', 'fly', 'steady', 'sort'):
                f_med = meds[ph]['frame_ms']
                if f_med > 1.0:
                    evaluation['gates_pass'] = False
                    evaluation['failed_gates'].append(f'{scene} {ph} frame time regressed {f_med:.2f}% (limit 1.0%)')
                p99_med = meds[ph]['p99_ms']
                if p99_med > 2.0:
                    evaluation['gates_pass'] = False
                    evaluation['failed_gates'].append(f'{scene} {ph} p99 regressed {p99_med:.2f}% (limit 2.0%)')

    out_file = bench_dir / 'evaluation.json'
    out_file.write_text(json.dumps(evaluation, indent=2) + '\n', encoding='utf-8')
    print('EVALUATION COMPLETE. Gates pass:', evaluation['gates_pass'])
    if not evaluation['gates_pass']:
        print('Failed gates:', evaluation['failed_gates'])
    return evaluation


if __name__ == '__main__':
    bdir = sys.argv[1] if len(sys.argv) > 1 else HERE
    evaluate_archive(bdir)
