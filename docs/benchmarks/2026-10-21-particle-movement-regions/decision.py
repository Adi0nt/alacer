"""Session 25: apply protocol.md's candidate eligibility to the frozen analyzer's metrics.json.

The frozen analyze.py reports single regions/sub-regions; the protocol also admits "a set of sub-regions
removable by one exact transformation". This script sums the sets each conceivable transformation could
remove, per launch/phase, without recomputing any metric, and writes decision.json. It adds no data.
"""
import json
from pathlib import Path
import statistics as stats

HERE = Path(__file__).resolve().parent

# Each set lists what a single exact change could remove at most, with the reason it is or is not exact.
SETS = {
    'fire_scan_answered_from_block_collision_scan': {
        'parts': ['fire'],
        'exactness': 'needs proof that onEntityCollision callbacks and isInWaterOrRain changed no world state or shape; '
                     'skipping the containsFireSource call also hides particle calls from other mods\' injections'},
    'post_gather_rereads_via_world_mutation_epoch': {
        'parts': ['fire.block_read', 'fire.area_check', 'fire.loop_self', 'fire.bounds',
                  'block_collisions.block_read', 'block_under.block_read'],
        'exactness': 'block-under, block-collision and fire cells lie inside the gather cells (except the second '
                     'block-under read), but reuse crosses addCollisions, checkFallDamage, beforeCollision and '
                     'onEntityCollision callbacks; exact only with an epoch covering every client world mutation path'},
    'chunk_lookup_reuse_for_block_reads (session 17 territory)': {
        'parts': ['@block_read_with_chunk_map'],
        'exactness': 'exact with the checked chunk-reference cache, but this is session 17\'s failed experiment; '
                     'the protocol forbids rebranding it'},
    'client_area_and_column_checks_answered_as_range_tests': {
        'parts': ['gather.column_check', 'fire.area_check', 'block_collisions.area_check'],
        'exactness': 'exact on ClientChunkCache (hasChunk is constant true); attribution is compilation-dependent: '
                     'in mr25-jfr-2-r1 column_check fell to 0.7-0.8% while gather.block_read rose by about 11 points'},
    'entity_query': {
        'parts': ['gather.entity_query', 'gather.entity_query_bounds'],
        'exactness': 'getEntities returned zero entities in every census move, but the query (and its injections) '
                     'must still run; session 19 already scalarized its bounds and failed'},
}


def share(row, part):
    if part.startswith('@'):
        return row[part[1:] + '_share_percent']
    if '.' in part:
        return row['sub_region_share_percent'].get(part, 0.0)
    return row['region_share_percent'].get(part, 0.0)


def main():
    metrics = json.loads((HERE / 'metrics.json').read_text())
    rows = metrics['jfr_regions']
    assert len(rows) == 8 and all(not r['low_sample_flag'] for r in rows)
    results = {}
    for name, spec in SETS.items():
        values = [sum(share(r, p) for p in spec['parts']) for r in rows]
        results[name] = {'per_launch_phase_percent': [round(v, 2) for v in values],
                         'median_percent': stats.median(values), 'min_percent': min(values),
                         'meets_share_thresholds': stats.median(values) >= 20 and min(values) >= 15,
                         'exactness': spec['exactness']}
    single = {k: v for k, v in metrics['share_eligibility'].items() if v['meets_share_thresholds']}
    decision = {
        'session': 25,
        'selection': 'no movement replacement eligible; none implemented',
        'single_regions_meeting_share_thresholds': single,
        'why_singles_do_not_qualify': {
            'gather': 'a mix of column checks, block reads, addCollisions callbacks and the entity query; no single exact '
                      'transformation removes it',
            'all_block_state_reads': 'first reads of each cell are required; only lookups or re-reads are removable',
            'all_chunk_map_frames': 'includes inlined bimorphic chunk-source code attributed to constant-true client checks',
            'gather.block_read+column_check': 'stable 21.8-24.4% combined, but neither part is removable as a whole',
        },
        'transformation_sets': results,
        'existing_movement_experiments_remain_disabled': True,
        'defaults_changed': False,
    }
    (HERE / 'decision.json').write_text(json.dumps(decision, indent=2) + '\n')
    for name, r in results.items():
        print(f"{name}: median {r['median_percent']:.1f}% min {r['min_percent']:.1f}% eligible={r['meets_share_thresholds']}")


if __name__ == '__main__':
    main()
