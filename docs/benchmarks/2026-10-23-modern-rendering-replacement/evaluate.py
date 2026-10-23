"""Evaluates balanced pairs of Control (A) vs Candidate (B) against predeclared gates."""
import csv
import json
from pathlib import Path
import statistics
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
LAYERS = ['terrain_solid', 'terrain_cutout_mipped', 'terrain_cutout', 'terrain_translucent']


def parse_metrics(summary_path, phase):
    data = json.loads(summary_path.read_text())
    p = data['phases'][phase]
    with (summary_path.parent / (phase + '.csv')).open(newline='') as f:
        rows = csv.DictReader(f)
        count = total = 0
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
        **{k + '_ms': p[k + '_mean_ms'] for k in LAYERS}
    }


def analyze(dest=None):
    launches = json.loads((HERE/'launches.json').read_text())
    by_scene = {}
    for r in launches:
        if r.get('status') != 'valid':
            continue
        scene = r['scene']
        by_scene.setdefault(scene, []).append(r)

    evaluation = {'scenes': {}, 'gates_pass': True, 'failed_gates': []}

    for scene, runs in by_scene.items():
        is_terrain = (scene == 'terrain')
        phases = ['steady', 'pan', 'sort', 'fly'] if is_terrain else ['static', 'pan', 'fly']
        # Order: ABBABAAB = Pairs: (1A, 2B), (4A, 3B), (5A, 6B), (8A, 7B)
        run_map = {r['index']: r for r in runs}
        pairs = [
            (run_map[1], run_map[2]),
            (run_map[4], run_map[3]),
            (run_map[5], run_map[6]),
            (run_map[8], run_map[7])
        ]

        scene_res = {'pairs': [], 'medians': {}}
        for pair_idx, (rA, rB) in enumerate(pairs, 1):
            pair_data = {'pair': f'{rA["label"]} vs {rB["label"]}', 'phases': {}}
            pathA = ROOT / rA['summary']
            pathB = ROOT / rB['summary']
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
        # Gate 1: Pan frame time improvement or <= 1% regression
        pan_frame_med = meds['pan']['frame_ms']
        if pan_frame_med > 1.0:
            evaluation['gates_pass'] = False
            evaluation['failed_gates'].append(f'{scene} pan frame time regressed {pan_frame_med:.2f}% (limit 1.0%)')

        # Gate 2: Pan CPU/frame improvement or <= 1% regression
        pan_cpu_med = meds['pan']['cpu_ms']
        if pan_cpu_med > 1.0:
            evaluation['gates_pass'] = False
            evaluation['failed_gates'].append(f'{scene} pan CPU/frame regressed {pan_cpu_med:.2f}% (limit 1.0%)')

        # Gate 3: No scored phase frame regression > 2.0%
        for ph in meds:
            f_med = meds[ph]['frame_ms']
            if f_med > 2.0:
                evaluation['gates_pass'] = False
                evaluation['failed_gates'].append(f'{scene} {ph} frame time regressed {f_med:.2f}% (limit 2.0%)')

        # Gate 4: Pan terrain layers must not regress
        layers_med = meds['pan']['terrain_layers_ms']
        if layers_med > 1.0:
            evaluation['gates_pass'] = False
            evaluation['failed_gates'].append(f'{scene} pan terrain layers regressed {layers_med:.2f}% (limit 1.0%)')

    out_file = Path(dest) if dest else (HERE / 'evaluation.json')
    out_file.write_text(json.dumps(evaluation, indent=2) + '\n')
    print('EVALUATION COMPLETE. Gates pass:', evaluation['gates_pass'])
    if not evaluation['gates_pass']:
        print('Failed gates:', evaluation['failed_gates'])
    return evaluation


if __name__ == '__main__':
    analyze()
