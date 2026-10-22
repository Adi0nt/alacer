"""Session 26: apply protocol.md's selection rule to the frozen analyzer's classification.

The frozen analyze.py ranks regions and aggregates (metrics.json). The protocol only lets a candidate be named when
the removable set of one exact transformation passes the share, corroboration, exactness and not-closed tests. This
script sums each conceivable set from the same stream keys with analyze.py's own Classifier (no new data, no changed
rule), per launch/phase, applies the protocol thresholds and writes decision.json. Sets are listed with the reason
they are or are not exact; they were written after the data, as session 25's decision.py was.
"""
import json
from pathlib import Path
import statistics as stats
import sys

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import analyze  # noqa: E402  (frozen classifier and loaders)

# name -> {'parts': [(region, sub-key prefix or None)], 'exactness': text, 'exact': bool, 'closed': text or None}
# A region name ending in '.' matches every region with that prefix; a sub-key prefix of None matches every sub-key.
SETS = {
    'visibility_search_whole': {
        'parts': [('terrain_setup', 'alacer.render>'), ('terrain_setup', 'alacer.hook>')],
        'exact': False, 'closed': None,
        'exactness': 'optimization 1 already replaces the search and runs it only when the view changed, as vanilla does; '
                     'its list depends on camera position, frustum and every visited chunk\'s compiled occlusion data, so '
                     'no exact transformation removes the per-chunk work (incremental or less frequent searches change the '
                     'result)'},
    'render_chunk_set_frame': {
        'parts': [('terrain_setup', 'alacer.render>RenderChunk.setFrame')],
        'exact': False, 'closed': None,
        'exactness': 'RenderChunk.frame is read only by setFrame inside vanilla\'s own search, which optimization 1 skips, '
                     'so skipping the stamp keeps vanilla\'s outputs; but it drops a vanilla state write other code may '
                     'read, and setFrame is the first touch of each RenderChunk, whose cache miss would move to the '
                     'following getCompiledChunk/bounds reads (sessions 21 and 25): its samples need not disappear'},
    'visibility_set_and_frustum_lookups': {
        'parts': [('terrain_setup', 'alacer.render>CompiledChunk.isVisible'),
                  ('terrain_setup', 'alacer.render>FrustumCuller.isVisible')],
        'exact': False, 'closed': 'frustum predicate (session 12)',
        'exactness': 'both are required reads per visited chunk face; a flat cache of occlusion bits needs the same '
                     'per-visit reference check, and the frustum test is session 12\'s closed territory'},
    'terrain_layer_chunk_filter': {
        'parts': [('terrain.', 'CompiledChunk.hasBlock'), ('terrain.', 'RenderChunk.getCompiledChunk'),
                  ('terrain.', 'RenderChunkList.add'), ('terrain.', 'ArrayList.get')],
        'exact': False, 'closed': None,
        'exactness': 'per-layer lists could be reused across frames only while neither the visible list nor any compiled '
                     'chunk changed; compiled data is replaced by builder threads and uploads between searches, so '
                     'detecting it needs the same per-chunk, per-layer reads'},
    'entity_draws': {
        'parts': [('entities.draw', None)],
        'exact': False, 'closed': 'model lists (9), zero translations, shadows beyond optimization 6 (11)',
        'exactness': 'spread over every entity renderer, model part, layer and shadow; no single exact change, and its '
                     'thin parts are closed'},
    'render_entities_attractor': {
        'parts': [('entities.loop.global', None), ('entities.loop.blockentities', 'Lighting.turnOn')],
        'exact': False, 'closed': None,
        'exactness': 'unexplained: one cheap leaf (HashMap.getNode under shouldRenderEntityOutlines, or Lighting.turnOn) '
                     'at the first Java code after native GL calls, while the ordinary profiler times the global phase '
                     'at 0.000 ms; the samples moved between the two lines across launches'},
    'client_entity_ticks': {
        'parts': [('tick.entities', None)],
        'exact': False, 'closed': None,
        'exactness': 'vanilla entity ticks of the placed NoAI mobs (movement, data trackers, chunk tracking); required '
                     'game logic with no removable subset in the counts'},
    'random_display_ticks': {
        'parts': [('tick.display_ticks', None)],
        'exact': False, 'closed': None,
        'exactness': 'vanilla samples random nearby blocks through the world RNG; the reads and RNG calls are the work '
                     'and their order is observable'},
}


def per_combination(rows_keys, classifier):
    """(label, scene, phase) -> {'total': n, 'parts': {(region, sub): n}, 'leaves': {(region, sub, leaf): n}}."""
    out = {}
    for record, keys in rows_keys:
        for phase, data in keys['phases'].items():
            if phase in analyze.WARMUPS:
                continue
            parts, leaves, slow = {}, {}, {}
            for key, n in data['keys']['execution.slow99'].items():
                region, sub = classifier.classify(key)
                slow[(region, sub)] = slow.get((region, sub), 0) + n
            for key, n in data['keys']['execution.all'].items():
                region, sub = classifier.classify(key)
                parts[(region, sub)] = parts.get((region, sub), 0) + n
                leaf = key.split('\t')[2] if '\t' in key else ''
                leaves[(region, sub, leaf)] = leaves.get((region, sub, leaf), 0) + n
            out[(record['label'], record['scene'], phase)] = {'total': data['execution_samples']['all'], 'parts': parts,
                                                               'leaves': leaves, 'slow99_total': data['execution_samples']['slow99'],
                                                               'slow99_parts': slow}
    return out


def matches(region, sub, part):
    want_region, want_sub = part
    if not (region == want_region or (want_region.endswith('.') and region.startswith(want_region))):
        return False
    return want_sub is None or sub.startswith(want_sub)


def evaluate(spec, combos, scenes, metrics):
    rows = {}
    for (label, scene, phase), c in combos.items():
        n = sum(v for (r, s), v in c['parts'].items() if any(matches(r, s, p) for p in spec['parts']))
        rows[(label, scene, phase)] = (n, 100 * n / c['total'] if c['total'] else 0.0)
    per_scene = {}
    for scene in analyze.SCENES:
        values = [v for (l, s, p), v in rows.items() if s == scene]
        if not values:
            continue
        driver = metrics['scenes'][scene]['driver_bound']
        consistent = min(x[1] for x in values) >= 5 and min(x[0] for x in values) >= 300 and not driver
        per_scene[scene] = {'median_percent': stats.median(x[1] for x in values), 'min_percent': min(x[1] for x in values),
                            'max_percent': max(x[1] for x in values), 'min_samples': min(x[0] for x in values),
                            'counted': consistent}
    ten = [s for s, v in per_scene.items() if v['counted'] and v['median_percent'] >= 10]
    twenty = [s for s, v in per_scene.items() if v['counted'] and v['median_percent'] >= 20 and s not in analyze.REFERENCE]
    share = (len(ten) >= 2 and any(s not in analyze.REFERENCE for s in ten)) or bool(twenty)
    tail = {}
    for scene in analyze.SCENES:
        if scene in analyze.REFERENCE:
            continue
        keys = [k for k in combos if k[1] == scene]
        n = sum(v for k in keys for (r, s), v in combos[k]['slow99_parts'].items() if any(matches(r, s, p) for p in spec['parts']))
        total = sum(combos[k]['slow99_total'] for k in keys)
        if total:
            tail[scene] = {'pooled_slow99_share_percent': 100 * n / total, 'pooled_samples': n}
    tail_scenes = [s for s, v in tail.items() if v['pooled_slow99_share_percent'] >= 20 and v['pooled_samples'] >= 300]
    share = share or bool(tail_scenes)
    groups = {analyze.group_of(p[0]) for p in spec['parts']}
    disagreement = sorted({r['scene'] for r in metrics['rows'] if not r['warmup'] for g in groups
                           if g and r['csv_corroboration'][g]['jfr_exceeds_csv'] and r['scene'] in ten + twenty + tail_scenes})
    return {'per_launch_phase_percent': {f'{l}/{p}': round(v[1], 2) for (l, s, p), v in rows.items()},
            'per_scene': per_scene, 'scenes_at_10': ten, 'scenes_at_20': twenty, 'slow99': tail,
            'tail_scenes_at_20': tail_scenes, 'meets_share_rule': share,
            'csv_disagreement_in_counted_scenes': disagreement,
            'eligible': share and not disagreement and spec['exact'] and not spec['closed']}


def main():
    metrics = json.loads((HERE / 'metrics.json').read_text())
    lines = json.loads((HERE / 'lines.json').read_text())
    classifier = analyze.Classifier(lines)
    manifest, records = analyze.validate_and_load()
    rows_keys = [(r, analyze.load_keys(HERE / 'runs' / (r['label'] + '.stream-keys.json.gz'), r)) for r in records]
    combos = per_combination(rows_keys, classifier)
    results = {name: {**evaluate(spec, combos, analyze.SCENES, metrics), 'exactness': spec['exactness'],
                      'exact': spec['exact'], 'closed': spec['closed']} for name, spec in SETS.items()}
    aggregates = {k: v for k, v in metrics['eligibility'].items() if v['meets_share_or_tail_rule']}
    eligible = sorted((n for n, r in results.items() if r['eligible']),
                      key=lambda n: -min([results[n]['per_scene'][s]['median_percent']
                                           for s in results[n]['scenes_at_10'] + results[n]['scenes_at_20']] or [0]))
    decision = {'rule': 'protocol.md selection rule: share (10% in two counted scenes incl. a non-reference one, or 20% in one '
                        'non-reference scene; counted = every combination >= 5% and >= 300 samples, not driver-bound), CSV '
                        'corroboration, exact removable set, not closed',
                'regions_and_aggregates_meeting_share_or_tail_rule': aggregates, 'sets': results,
                'eligible_sets': eligible, 'named_candidate': eligible[0] if eligible else None}
    (HERE / 'decision.json').write_text(json.dumps(decision, indent=2) + '\n')
    print('named candidate:', decision['named_candidate'])
    for name, r in results.items():
        print(f"{name}: share={r['meets_share_rule']} scenes10={r['scenes_at_10']} scenes20={r['scenes_at_20']} tail={r['tail_scenes_at_20']} "
              f"csv={r['csv_disagreement_in_counted_scenes']} exact={r['exact']} closed={bool(r['closed'])} -> {r['eligible']}")


if __name__ == '__main__':
    main()
