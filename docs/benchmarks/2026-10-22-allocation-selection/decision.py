#!/usr/bin/env python3
"""Session 28: apply protocol.md's selection rule to the six classified attribution launches.

Usage: python3 decision.py <runs dir> <out.json>
Reads <label>.classified.json for alloc28-{d0,d200,terrain}-r{1,2}.
"""
import json
import statistics
import sys
from pathlib import Path

SCENES = ('d0', 'd200', 'terrain')
RANKED = (('d0', 'static'), ('d0', 'pan'), ('d200', 'static'), ('d200', 'pan'), ('terrain', 'steady'), ('terrain', 'pan'))
REPORTED = (('d0', 'fly'), ('d200', 'fly'), ('terrain', 'sort'), ('terrain', 'fly'))
SELECTABLE = ('shadow.offset_pos', 'entity_light.pos', 'name_check.strip', 'pick.block_trace')
EVENT_DRIVEN = ('flood.all', 'flood.direction_values')
SHARE = 10.0
SAMPLES = 50
BYTES = 2048.0


def main(runs, out):
    launches = {s: [json.loads((runs / f'alloc28-{s}-r{n}.classified.json').read_text()) for n in (1, 2)]
                for s in SCENES}
    corroboration = {}
    for scene, phase in RANKED + REPORTED:
        for launch in launches[scene]:
            p = launch['phases'][phase]
            ratio = p['jfr_total_bytes_per_frame'] / p['csv_alloc_mean_bytes']
            corroboration[f"{launch['label']}/{phase}"] = {'jfr_over_csv': ratio, 'pass': 0.5 <= ratio <= 2.0}
    sets = {}
    all_names = sorted({n for ls in launches.values() for l in ls for p in l['phases'].values() for n in p['sets']})
    for name in all_names:
        combos = {}
        for scene, phase in RANKED + REPORTED:
            vals = []
            for launch in launches[scene]:
                p = launch['phases'][phase]
                s = p['sets'].get(name, {'samples': 0, 'share_percent': 0.0, 'bytes_per_frame': 0.0})
                seconds = (p['end_ms'] - p['start_ms']) / 1000.0
                vals.append({'label': launch['label'], 'samples': s['samples'], 'share_percent': s['share_percent'],
                             'bytes_per_frame': s['bytes_per_frame'],
                             'bytes_per_second': s['bytes_per_frame'] * p['frames'] / seconds,
                             'corroborated': corroboration[f"{launch['label']}/{phase}"]['pass']})
            ranked = (scene, phase) in RANKED
            qualifies = ranked and all(v['share_percent'] >= SHARE and v['bytes_per_frame'] >= BYTES
                                       and v['samples'] >= SAMPLES and v['corroborated'] for v in vals)
            combos[f'{scene}/{phase}'] = {
                'ranked': ranked, 'launches': vals, 'qualifies': qualifies,
                'median_share_percent': statistics.median(v['share_percent'] for v in vals),
                'median_bytes_per_frame': statistics.median(v['bytes_per_frame'] for v in vals),
                'median_bytes_per_second': statistics.median(v['bytes_per_second'] for v in vals)}
        qualifying = [k for k, v in combos.items() if v['qualifies']]
        sets[name] = {'selectable': name in SELECTABLE, 'qualifying_combinations': qualifying,
                      'share_and_size_pass': len(qualifying) >= 2,
                      'min_qualifying_share': min((min(v['share_percent'] for v in combos[k]['launches'])
                                                   for k in qualifying), default=0.0),
                      'combinations': combos}
    passing = sorted((n for n, v in sets.items() if v['selectable'] and v['share_and_size_pass']),
                     key=lambda n: -sets[n]['min_qualifying_share'])
    result = {'rule': {'share_percent': SHARE, 'samples': SAMPLES, 'bytes_per_frame': BYTES, 'ranked': RANKED},
              'corroboration': corroboration, 'passing_items_1_2': passing, 'sets': sets}
    Path(out).write_text(json.dumps(result, indent=1) + '\n')
    print('Items 1-2 pass (ordered by minimum qualifying share):', passing or 'none')
    for name in SELECTABLE + EVENT_DRIVEN + ('shadow.all', 'entity_light.all', 'name_check.all', 'name_check.get_name', 'pick.all',
                              'agg.render_entities', 'agg.setup_render', 'agg.alacer_profiler', 'agg.alacer_benchmark'):
        if name not in sets:
            continue
        row = [f"{k}: {v['median_share_percent']:.1f}% {v['median_bytes_per_frame']:.0f} B/f" +
               (' Q' if v['qualifies'] else '') for k, v in sets[name]['combinations'].items()]
        print(name, '|', '; '.join(row))
    for name in EVENT_DRIVEN:
        if name in sets:
            print(name, 'bytes/s', {k: round(v['median_bytes_per_second']) for k, v in sets[name]['combinations'].items()})


if __name__ == '__main__':
    main(Path(sys.argv[1]), sys.argv[2])
