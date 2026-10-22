"""Session 25: validate frozen evidence, classify movement samples by region and evaluate candidate eligibility.

Reads runs/*.record.json, the census JSON of region launches, runs/*.stream-keys.json produced by
stream_regions.py, and lines.json produced by line_table.py from the runtime jar. Writes metrics.json and
results.md. Eligibility follows protocol.md: a region (or one transformation's removable set) needs a median
share of at least 20% of hooks-absent move samples over the launch/phase combinations and at least 15% in each.
"""
import hashlib
import json
from pathlib import Path
import statistics as stats
import zipfile

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
JDK = Path('C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/java.exe')
SHARED = '694ff84d8e5672cd7a2ec0ee1b5a3e4eadbe09552d5d65eef93e16f61ceacd31'
REGIONS = ('pre', 'gather_bounds', 'gather', 'clip_publish', 'step', 'profiler_tail', 'position_flags', 'block_under',
           'fall_damage', 'velocity_vertical_callback', 'step_sounds', 'block_collisions', 'water_rain', 'fire',
           'fire_effects', 'noclip')
CENSUS_REGIONS = ('pre', 'gather_blocks', 'gather_entities', 'clip_publish', 'under_flags', 'block_collisions',
                  'water_rain', 'fire', 'tail')


def sha(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


def short(name):
    return name.rsplit('.', 2)[-2] + '.' + name.rsplit('.', 1)[-1] if name.count('.') >= 2 else name


def sub_region(region, move_callee, anchor, anchor_callee):
    """Callee-based detail inside a region; leaves are not used because inlined frames can be misattributed."""
    c = anchor_callee if anchor else move_callee
    if region == 'gather':
        if c == 'net.minecraft.world.World.getBlockState': return 'block_read'
        if c.endswith('.addCollisions'): return 'add_collisions'
        if c == 'net.minecraft.world.World.isChunkLoaded': return 'column_check'
        if c in ('net.minecraft.world.border.WorldBorder.contains', 'net.minecraft.world.World.isWithinBorder',
                 'net.minecraft.world.World.getWorldBorder', 'net.minecraft.entity.Entity.isOutsideWorldBorder',
                 'net.minecraft.entity.Entity.setOutsideWorldBorder'): return 'border'
        if c == 'net.minecraft.world.World.getEntities': return 'entity_query'
        if c == 'net.minecraft.util.math.Box.grown': return 'entity_query_bounds'
        if c in ('net.minecraft.entity.Entity.getCollisionShape', 'net.minecraft.entity.Entity.getCollisionAgainstShape',
                 'net.minecraft.util.math.Box.intersects'): return 'entity_loop'
        if c.startswith('net.minecraft.util.math.BlockPos$Mutable.'): return 'cell_setup'
        if c == 'net.minecraft.util.math.MathHelper.floor': return 'bounds'
        if c.startswith('com.google.common.collect.Lists.') or c.startswith('java.util.'): return 'lists'
        if c == '<self>': return 'loop_self'
        return 'other:' + short(c)
    if region == 'block_collisions':
        if not anchor: return 'call'
        if c == 'net.minecraft.world.World.getBlockState': return 'block_read'
        if c.endswith('.onEntityCollision'): return 'callbacks'
        if c == 'net.minecraft.world.World.isAreaLoaded': return 'area_check'
        if c == 'net.minecraft.util.math.BlockPos.<init>': return 'positions'
        if c == 'net.minecraft.entity.Entity.getShape': return 'bounds'
        if c == '<self>': return 'loop_self'
        return 'other:' + short(c)
    if region == 'fire':
        if not anchor:
            return 'bounds' if c in ('net.minecraft.util.math.Box.contract', 'net.minecraft.entity.Entity.getShape') else 'other:' + short(c)
        if c == 'net.minecraft.world.World.getBlockState': return 'block_read'
        if c == 'net.minecraft.world.World.isAreaLoaded': return 'area_check'
        if c == 'net.minecraft.util.math.MathHelper.floor': return 'bounds'
        if c.startswith('net.minecraft.util.math.BlockPos$Mutable.'): return 'cell_setup'
        if c == '<self>': return 'loop_self'
        return 'other:' + short(c)
    if region == 'water_rain':
        if not anchor: return 'call'
        if c == 'net.minecraft.world.World.isRaining': return 'raining'
        if c == 'net.minecraft.util.math.BlockPos.<init>': return 'positions'
        return 'self' if c == '<self>' else 'other:' + short(c)
    if region == 'block_under':
        if c == 'net.minecraft.world.World.getBlockState': return 'block_read'
        if c in ('net.minecraft.util.math.BlockPos.<init>', 'net.minecraft.util.math.BlockPos.down'): return 'positions'
        if c == 'net.minecraft.block.Block.getMaterial': return 'material'
        if c == 'net.minecraft.util.math.MathHelper.floor': return 'floor'
        return 'self' if c == '<self>' else 'other:' + short(c)
    return 'all'


def classify(keys, line_region):
    regions, subs, block_reads, chunk_map, block_reads_chunk = {}, {}, {}, {}, {}
    for key, count in keys.items():
        f = key.split('\t')
        move_line, move_callee, anchor, anchor_line, anchor_callee, leaf, chunk, read = f[:8]
        region = line_region.get(move_line, 'unknown:' + move_line)
        if region == 'gather' and anchor != 'net.minecraft.world.World.getCollisions':
            region, anchor = 'gather_bounds', ''
        if region in ('block_collisions', 'fire', 'water_rain') and anchor and not anchor.endswith(
                {'block_collisions': 'checkBlockCollisions', 'fire': 'containsFireSource', 'water_rain': 'isInWaterOrRain'}[region]):
            anchor = ''
        sub = sub_region(region, move_callee, anchor, anchor_callee)
        regions[region] = regions.get(region, 0) + count
        subs[region + '.' + sub] = subs.get(region + '.' + sub, 0) + count
        if read == 'true':
            block_reads[region] = block_reads.get(region, 0) + count
        if chunk == 'true':
            chunk_map[region] = chunk_map.get(region, 0) + count
            if read == 'true':
                block_reads_chunk[region] = block_reads_chunk.get(region, 0) + count
    return regions, subs, block_reads, chunk_map, block_reads_chunk


def main():
    manifest = json.loads((HERE / 'manifest.json').read_text())
    snapshot = manifest['source_snapshot']
    assert sha(ROOT / snapshot['path']) == snapshot['sha256']
    with zipfile.ZipFile(ROOT / snapshot['path']) as z:
        for name, h in manifest['source_sha256'].items():
            assert hashlib.sha256(z.read(name.replace('\\', '/'))).hexdigest() == h, name
    lines = json.loads((HERE / 'lines.json').read_text())
    assert sha(lines['runtime_jar']) == lines['runtime_jar_sha256']
    line_region = lines['line_region']
    records = []
    for p in sorted((HERE / 'runs').glob('*.record.json')):
        r = json.loads(p.read_text())
        folder = ROOT / r['source']
        assert r['source_snapshot'] == snapshot and r['frozen_manifest']
        assert r['base_world_sha256'] == manifest['base_world_sha256']
        assert sha(ROOT / r['log']) == r['log_sha256']
        assert Path(r['launcher']['ExecutablePath']).resolve() == JDK.resolve()
        assert r['shared_config_before_sha256'] == r['shared_config_after_sha256'] == SHARED
        for name, h in r['files_sha256'].items():
            assert sha(folder / name) == h, (r['label'], name)
        if 'jfr' in r:
            assert sha(ROOT / r['jfr']) == r['jfr_sha256']
        summary = json.loads((folder / 'summary.json').read_text())
        for phase, c in r['phase_configs'].items():
            assert json.loads((folder / (phase + '.json')).read_text())['config'] == c == summary['phases'][phase]['config']
        r['_summary'] = summary
        records.append(r)
    census_rows, jfr_rows = [], []
    for r in records:
        folder = ROOT / r['source']
        summary = r['_summary']
        if r['kind'] == 'regions':
            census = json.loads((folder / 'particle-movement-census.json').read_text())
            clock = census['clock_call_mean_ns_after_capture']
            for phase, data in census['phases'].items():
                c = data['total']
                timed = c['region_moves']
                raw = {k: c['region_' + k + '_ns'] for k in CENSUS_REGIONS}
                total = sum(raw.values())
                # Each mark-to-mark interval contains about one clock call; the adjusted figure removes that estimate.
                adjusted = {k: max(0.0, v - clock * timed) for k, v in raw.items()}
                adjusted_total = sum(adjusted.values())
                census_rows.append({
                    'label': r['label'], 'scale': r['scale'], 'phase': phase, 'moves': c['moves_returned'],
                    'region_moves': timed, 'no_clip_moves': c['region_no_clip_moves'], 'clock_call_ns': clock,
                    'raw_ns_per_region_move': {k: v / timed for k, v in raw.items()},
                    'raw_share_percent': {k: 100 * v / total for k, v in raw.items()},
                    'clock_adjusted_share_percent': {k: 100 * v / adjusted_total for k, v in adjusted.items()},
                    'raw_ns_per_region_move_total': total / timed,
                    'clock_adjusted_ns_per_region_move_total': adjusted_total / timed,
                    'no_clip_ns_per_move': c['region_no_clip_ns'] / max(1, c['region_no_clip_moves']),
                    'per_region_move': {k: c[k] / timed for k in (
                        'gather_columns_structural', 'gather_cells_structural', 'gather_in_height_cells_structural',
                        'gather_block_boxes', 'gather_entity_boxes', 'entity_query_chunks_structural',
                        'entity_query_sections_structural', 'entity_query_results', 'under_block_second_reads',
                        'vertical_collision_callbacks', 'block_collision_cells_structural', 'fire_cells_structural',
                        'fire_sources_found')},
                    'gather_share_of_move_wall_percent': 100 * c['collision_gather_wall_ns'] / c['move_wall_ns'],
                    'ticked_mean': summary['phases'][phase]['particles_ticked_mean'],
                })
        if r['kind'] == 'jfr':
            keys = json.loads((HERE / 'runs' / (r['label'] + '.stream-keys.json')).read_text())
            assert keys['jfr_sha256'] == r['jfr_sha256']
            for phase, data in keys['phases'].items():
                regions, subs, reads, chunk, reads_chunk = classify(data['execution_keys'], line_region)
                n = data['move_samples']
                assert sum(regions.values()) == n
                jfr_rows.append({
                    'label': r['label'], 'scale': r['scale'], 'phase': phase, 'move_samples': n,
                    'low_sample_flag': n < 300, 'client_samples': data['client_execution_samples'],
                    'tick_exact_samples': data['tick_particle_exact_samples'],
                    'tick_prefix_samples': data['tick_particle_prefix_samples'],
                    'move_share_of_client_percent': 100 * n / data['client_execution_samples'],
                    'region_samples': regions, 'sub_region_samples': subs,
                    'region_share_percent': {k: 100 * v / n for k, v in regions.items()},
                    'sub_region_share_percent': {k: 100 * v / n for k, v in subs.items()},
                    'block_state_read_share_percent': 100 * sum(reads.values()) / n,
                    'block_state_read_by_region_percent': {k: 100 * v / n for k, v in reads.items()},
                    'chunk_map_frame_share_percent': 100 * sum(chunk.values()) / n,
                    'block_read_with_chunk_map_share_percent': 100 * sum(reads_chunk.values()) / n,
                    'allocation_samples': data['move_allocation_samples'],
                    'allocation_weight': data['move_allocation_sampled_weight'],
                })
    # Eligibility over hooks-absent JFR launch/phase combinations.
    candidates = {k: [row['region_share_percent'].get(k, 0.0) for row in jfr_rows] for k in REGIONS}
    subs_all = sorted({k for row in jfr_rows for k in row['sub_region_share_percent']})
    candidates.update({k: [row['sub_region_share_percent'].get(k, 0.0) for row in jfr_rows] for k in subs_all})
    candidates['all_block_state_reads'] = [row['block_state_read_share_percent'] for row in jfr_rows]
    candidates['block_reads_with_chunk_map_frame'] = [row['block_read_with_chunk_map_share_percent'] for row in jfr_rows]
    candidates['all_chunk_map_frames'] = [row['chunk_map_frame_share_percent'] for row in jfr_rows]
    eligibility = {}
    for k, v in candidates.items():
        if not v:
            continue
        eligibility[k] = {'median_share_percent': stats.median(v), 'min_share_percent': min(v), 'max_share_percent': max(v),
                          'meets_share_thresholds': stats.median(v) >= 20 and min(v) >= 15}
    result = {'source_snapshot': snapshot, 'runtime_jar_sha256': lines['runtime_jar_sha256'],
              'census_regions': census_rows, 'jfr_regions': jfr_rows, 'share_eligibility': eligibility}
    (HERE / 'metrics.json').write_text(json.dumps(result, indent=2) + '\n')
    write_results(result)
    print('Wrote metrics.json and results.md')


def write_results(result):
    out = ['# Session 25 movement region results', '',
           'Hooks-absent JFR sample shares (1 ms sampling, DebugNonSafepoints). Counts are samples, not times.', '',
           '| Launch | Phase | Move samples | Move / client | ' + ' | '.join(REGIONS[:15]) + ' |',
           '|---|---|---:|---:|' + '---:|' * 15]
    for row in result['jfr_regions']:
        flag = ' (low)' if row['low_sample_flag'] else ''
        out.append(f"| {row['label']} | {row['phase']} | {row['move_samples']}{flag} | {row['move_share_of_client_percent']:.1f}% | "
                   + ' | '.join(f"{row['region_share_percent'].get(k, 0):.1f}" for k in REGIONS[:15]) + ' |')
    out += ['', '| Launch | Phase | All block-state reads | Reads with chunk-map frame | Any chunk-map frame |', '|---|---|---:|---:|---:|']
    for row in result['jfr_regions']:
        out.append(f"| {row['label']} | {row['phase']} | {row['block_state_read_share_percent']:.1f}% | "
                   f"{row['block_read_with_chunk_map_share_percent']:.1f}% | {row['chunk_map_frame_share_percent']:.1f}% |")
    out += ['', '## Largest shares over all JFR launch/phase combinations', '',
            '| Region or set | Median % | Min % | Max % | Meets 20% median / 15% min |', '|---|---:|---:|---:|---|']
    ranked = sorted(result['share_eligibility'].items(), key=lambda x: -x[1]['median_share_percent'])
    for k, v in ranked[:30]:
        out.append(f"| {k} | {v['median_share_percent']:.1f} | {v['min_share_percent']:.1f} | {v['max_share_percent']:.1f} | "
                   f"{'yes' if v['meets_share_thresholds'] else 'no'} |")
    out += ['', '## Region census (intrusive; includes mark costs)', '',
            '| Launch | Phase | Region moves | ns/move raw | ns/move clock-adjusted | ' + ' | '.join(CENSUS_REGIONS) + ' |',
            '|---|---|---:|---:|---:|' + '---:|' * len(CENSUS_REGIONS)]
    for row in result['census_regions']:
        out.append(f"| {row['label']} | {row['phase']} | {row['region_moves']} | {row['raw_ns_per_region_move_total']:.0f} | "
                   f"{row['clock_adjusted_ns_per_region_move_total']:.0f} | "
                   + ' | '.join(f"{row['clock_adjusted_share_percent'][k]:.1f}%" for k in CENSUS_REGIONS) + ' |')
    keys = ('gather_columns_structural', 'gather_in_height_cells_structural', 'gather_block_boxes', 'entity_query_chunks_structural',
            'entity_query_results', 'under_block_second_reads', 'vertical_collision_callbacks',
            'block_collision_cells_structural', 'fire_cells_structural', 'fire_sources_found')
    out += ['', '## Work per region-timed move (structural counts assume every column loaded)', '',
            '| Launch | Phase | ' + ' | '.join(keys) + ' |', '|---|---|' + '---:|' * len(keys)]
    for row in result['census_regions']:
        out.append(f"| {row['label']} | {row['phase']} | " + ' | '.join(f"{row['per_region_move'][k]:.3f}" for k in keys) + ' |')
    out += ['', 'Full per-launch sub-regions, allocation weights and census values are in metrics.json.', '']
    (HERE / 'results.md').write_text('\n'.join(out))


if __name__ == '__main__':
    main()
