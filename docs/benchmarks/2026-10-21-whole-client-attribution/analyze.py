"""Session 26: validate frozen evidence, classify whole-frame samples by region and evaluate share eligibility.

Reads manifest.json, lines.json (line_table.py, from the runtime jar), runs/*.record.json, the CSVs they hash and
runs/*.stream-keys.json.gz (stream_frames.py). Writes metrics.json and results.md. Region rules are fixed below and
frozen with the protocol; a key whose anchors are not covered falls into an explicit "other" region with its anchor
or callee listed. Shares are of client-thread Java execution samples inside the phase's CSV frames. Eligibility
follows protocol.md (region aggregates only; a removable set of one exact transformation is assessed in decision.py
against the same thresholds). `--smoke ATTEMPT KEYS` classifies a pre-freeze smoke launch for tooling checks only.
"""
import csv
import gzip
import hashlib
import json
from pathlib import Path
import statistics as stats
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
JDK = Path('C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/java.exe')
SHARED = '694ff84d8e5672cd7a2ec0ee1b5a3e4eadbe09552d5d65eef93e16f61ceacd31'
SCENES = ('d0', 'd200', 'terrain', 'particles')
REFERENCE = ('particles',)
WARMUPS = ('warmup-pan', 'warmup-sort')
CATEGORIES = ('all', 'tick', 'nontick', 'slow95', 'slow99')
CSV_SECTIONS = ('frame', 'tick', 'render', 'world', 'swap', 'terrain_setup', 'visibility', 'chunk_upload',
                'terrain_solid', 'terrain_cutout_mipped', 'terrain_cutout', 'terrain_translucent', 'entities',
                'entity_prepare', 'entity_global', 'entity_outline', 'entity_loop', 'block_entity_loop', 'particles', 'particle_tick', 'sky', 'clouds', 'weather', 'hand',
                'gui', 'lightmap', 'pick', 'camera_setup', 'gl_error_check')

L1 = {'mc.tick': 'tick', 'gr.render': 'render', 'mc.updateDisplay': 'swap', 'util.run': 'tasks',
      'mc.logGlError': 'gl_error_check', 'display.sync': 'fps_sync', 'sound.listener': 'sound_listener'}
TICK = {'pm.tick': 'tick.particles', 'world.tickEntities': 'tick.entities', 'cw.tick': 'tick.world',
        'cw.displayTicks': 'tick.display_ticks', 'tm.tick': 'tick.textures', 'gr.tick': 'tick.game_renderer',
        'wr.tick': 'tick.world_renderer', 'gr.pick': 'tick.pick', 'cpim.tick': 'tick.interaction',
        'conn.tick': 'tick.network', 'sound.tick': 'tick.sound', 'music.tick': 'tick.sound', 'gui.tick': 'tick.gui'}
RENDER = {'gr.renderWorld': 'world', 'gui.render': 'hud'}
WORLD = {'gr.pass': 'pass', 'gr.lightmap': 'lightmap', 'gr.pick': 'pick'}
PASS = {'wr.setupRender': 'terrain_setup', 'wr.compile': 'chunk_upload', 'wr.layer': 'terrain', 'wr.entities': 'entities',
        'pm.renderLit': 'particles.lit', 'pm.render': 'particles.unlit', 'wr.sky': 'sky', 'gr.clouds': 'clouds',
        'wr.clouds': 'clouds', 'gr.weather': 'weather', 'gr.hand': 'hand', 'gr.setupCamera': 'camera_fog',
        'gr.setupFog': 'camera_fog', 'wr.outline': 'overlays', 'wr.mining': 'overlays', 'wr.border': 'overlays'}
# Region aggregates used for ranking next to the leaf regions (prefix match on leaf names).
AGGREGATES = {
    'agg:tick': ('tick.',), 'agg:render_pass': ('terrain', 'chunk_upload', 'entities', 'block_entities', 'particles.',
                                                 'sky', 'clouds', 'weather', 'hand', 'camera_fog', 'overlays', 'pass.other'),
    'agg:terrain_layers': ('terrain.',), 'agg:terrain_all': ('terrain', 'chunk_upload'),
    'agg:entities_all': ('entities.', 'block_entities.'), 'agg:particles_all': ('particles.', 'tick.particles'),
    'agg:alacer_frame': ('alacer.',), 'agg:world_render': ('world.', 'terrain', 'chunk_upload', 'entities', 'block_entities',
                                                            'particles.', 'sky', 'clouds', 'weather', 'hand', 'camera_fog',
                                                            'overlays', 'pass.other'),
}


# CSV corroboration (protocol.md): region groups covered by an ordinary-profiler section. A group's JFR estimate is its
# share of Java samples times the non-swap frame time; it disagrees when the estimate exceeds 3x the CSV mean + 0.02 ms.
CORROBORATION = {
    'terrain_setup': (('terrain_setup',), ('terrain_setup',)), 'chunk_upload': (('chunk_upload',), ('chunk_upload',)),
    'terrain.solid': (('terrain.solid',), ('terrain_solid',)), 'terrain.cutout_mipped': (('terrain.cutout_mipped',), ('terrain_cutout_mipped',)),
    'terrain.cutout': (('terrain.cutout',), ('terrain_cutout',)), 'terrain.translucent': (('terrain.translucent',), ('terrain_translucent',)),
    'entities.prepare': (('entities.loop.prepare',), ('entity_prepare',)), 'entities.global': (('entities.loop.global',), ('entity_global',)),
    'entities.outline': (('entities.loop.entityOutlines',), ('entity_outline',)),
    'entities.loop': (('entities.loop.entities', 'entities.draw', 'entities.visibility', 'entities.name_tags'), ('entity_loop',)),
    'block_entities': (('entities.loop.blockentities', 'block_entities.draw'), ('block_entity_loop',)),
    'particles': (('particles.lit', 'particles.unlit'), ('particles',)), 'sky': (('sky',), ('sky',)),
    'clouds': (('clouds',), ('clouds',)), 'weather': (('weather',), ('weather',)), 'hand': (('hand',), ('hand',)),
    'gui': (('render.hud',), ('gui',)), 'lightmap': (('world.lightmap',), ('lightmap',)), 'pick': (('world.pick',), ('pick',)),
    'tick': (('tick.',), ('tick',)), 'swap': (('swap',), ('swap',)),
}


def group_of(region):
    for name, (members, _) in CORROBORATION.items():
        if any(region == m or (m.endswith('.') and region.startswith(m)) for m in members):
            return name
    return None


def corroborate(regions, total, sections):
    out = {}
    available = sections['frame_ms'] - sections['swap_ms']
    for name, (members, columns) in CORROBORATION.items():
        n = sum(v for k, v in regions.items() if group_of(k) == name)
        estimate = (n / total * available) if name != 'swap' else n / total * sections['frame_ms']
        csv_ms = sum(sections[c + '_ms'] for c in columns)
        out[name] = {'jfr_estimate_ms': estimate, 'csv_ms': csv_ms, 'samples': n,
                     'jfr_exceeds_csv': name != 'swap' and estimate > 3 * csv_ms + 0.02}
    return out


def sha(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


def short(name):
    parts = name.rsplit('.', 2)
    return parts[-2] + '.' + parts[-1] if len(parts) >= 2 else name


class Classifier:
    def __init__(self, lines):
        self.sections = {k: v['line_section'] for k, v in lines['methods'].items()}
        self.layers = lines['methods']['gr.pass']['layer_call_lines']

    def section(self, anchor, line):
        return self.sections.get(anchor, {}).get(str(line), 'unmapped:' + str(line))

    def classify(self, key):
        """Returns (region, sub). Root-most anchor below each parent decides the child (exact frames only)."""
        f = key.split('\t')
        if f[0] in ('OUTSIDE', 'TRUNCATED'):
            return f[0].lower(), short(f[1])
        tokens = []
        for t in f[0].split(' '):
            name, _, line = t.rpartition(':') if not t.startswith('pkg:') else (t, '', '')
            tokens.append((name, int(line) if line else None))
        below = f[1]
        assert tokens[0][0] == 'mc.runGame', key
        deepest = tokens[-1][0]
        # Sub-key: the frame below the deepest chain element, prefixed by that element when it is a package scope.
        callee = (deepest[4:] + '>' if deepest.startswith('pkg:') else '') + short(below)

        def step(i, table):
            """First anchor after position i: (index, child) or (index, None) for an uncovered anchor."""
            pkgs = []
            for j in range(i + 1, len(tokens)):
                name = tokens[j][0]
                if name.startswith('pkg:'):
                    pkgs.append(name)
                    continue
                return j, table.get(name), pkgs
            return None, None, pkgs

        def pkg_region(prefix, pkgs):
            if 'pkg:alacer.benchmark' in pkgs:
                return prefix + 'alacer.benchmark_driver'
            if 'pkg:alacer.profiler' in pkgs:
                return prefix + 'alacer.profiler'
            return prefix + 'alacer.' + pkgs[0][len('pkg:alacer.'):]

        j, child, pkgs = step(0, L1)
        if j is None:
            if pkgs:
                return pkg_region('', pkgs), callee
            return 'frame_other', self.section('mc.runGame', tokens[0][1]) + ':' + callee
        if child is None:
            return 'frame_other', tokens[j][0]
        if child == 'tick':
            k, sub, pkgs = step(j, TICK)
            if k is None:
                if pkgs:
                    return pkg_region('tick.', pkgs), callee
                return 'tick.other', self.section('mc.tick', tokens[j][1]) + ':' + callee
            if sub is None:
                return 'tick.other', tokens[k][0]
            if sub == 'tick.particles':
                return sub, 'particle_update' if any(t[0] == 'pm.tickParticle' for t in tokens) else 'manager:' + callee
            return sub, callee
        if child != 'render':
            return child, callee
        k, sub, pkgs = step(j, RENDER)
        if k is None or sub is None:
            return 'render.other', (tokens[k][0] if k is not None else self.section('gr.render', tokens[j][1])) + ':' + callee
        if sub == 'hud':
            return 'render.hud', callee
        m, sub, pkgs = step(k, WORLD)
        if m is None or sub is None:
            return 'world.other', (tokens[m][0] if m is not None else self.section('gr.renderWorld', tokens[k][1])) + ':' + callee
        if sub != 'pass':
            return 'world.' + sub, callee
        pass_line = tokens[m][1]
        n, sub, pkgs = step(m, PASS)
        if n is None or sub is None:
            return 'pass.other', (tokens[n][0] + ':' if n is not None else '') + self.section('gr.pass', pass_line) + ':' + callee
        if sub == 'terrain':
            return 'terrain.' + self.layers.get(str(pass_line), 'unknown_layer'), callee
        if sub != 'entities':
            return sub, callee
        phase = self.section('wr.entities', tokens[n][1])
        rest = [t[0] for t in tokens[n + 1:]]
        if 'erd.render' in rest:
            return 'entities.draw', phase
        if 'berd.render' in rest:
            return 'block_entities.draw', phase
        if 'erd.shouldRender' in rest:
            return 'entities.visibility', phase
        if 'erd.nameTag' in rest:
            return 'entities.name_tags', phase
        return 'entities.loop.' + phase.replace('/', '.'), callee


def aggregate(counts):
    out = {}
    for agg, prefixes in AGGREGATES.items():
        out[agg] = sum(v for k, v in counts.items() if any(k == p or k.startswith(p) for p in prefixes))
    return out


def csv_sections(folder, phase, thresholds):
    with (folder / (phase + '.csv')).open() as f:
        rows = list(csv.DictReader(f))
    p95, p99 = thresholds['p95_ns'], thresholds['p99_ns']
    groups = {'all': rows, 'tick': [r for r in rows if int(r['tick_ns']) > 0],
              'nontick': [r for r in rows if int(r['tick_ns']) == 0],
              'slow95': [r for r in rows if int(r['frame_ns']) >= p95], 'slow99': [r for r in rows if int(r['frame_ns']) >= p99]}
    out = {}
    for name, group in groups.items():
        n = len(group)
        out[name] = {'frames': n}
        for s in CSV_SECTIONS:
            out[name][s + '_ms'] = sum(int(r[s + '_ns']) for r in group) / n / 1e6 if n else None
        out[name]['alloc_kb'] = sum(int(r['alloc_bytes']) for r in group) / n / 1024 if n else None
    return out


def phase_rows(record, keys, classifier):
    folder = ROOT / record['source']
    summary = json.loads((folder / 'summary.json').read_text())
    rows = []
    for phase, data in keys['phases'].items():
        regions = {c: {} for c in CATEGORIES}
        native = {}
        alloc = {'all': {}, 'tick': {}}
        subs = {}
        uncovered = {}
        for c in CATEGORIES:
            for key, n in data['keys']['execution.' + c].items():
                region, sub = classifier.classify(key)
                regions[c][region] = regions[c].get(region, 0) + n
                if c == 'all':
                    subs.setdefault(region, {})
                    subs[region][sub] = subs[region].get(sub, 0) + n
                    if region.endswith('other') or region.startswith('unmapped'):
                        uncovered[key] = n
        for key, n in data['keys']['native.all'].items():
            region, _ = classifier.classify(key)
            native[region] = native.get(region, 0) + n
        for c in ('all', 'tick'):
            for key, w in data['keys']['allocation_weight.' + c].items():
                region, _ = classifier.classify(key.rsplit('\t', 1)[0])
                alloc[c][region] = alloc[c].get(region, 0) + w
        objects = {}
        for key, w in data['keys']['allocation_weight.all'].items():
            obj = key.rsplit('\t', 1)[1]
            objects[obj] = objects.get(obj, 0) + w
        total = {c: data['execution_samples'][c] for c in CATEGORIES}
        frames = data['frames']
        assert sum(regions['all'].values()) == total['all'], (record['label'], phase)
        sections = csv_sections(folder, phase, keys['frame_thresholds'][phase])
        wall_s = sections['all']['frame_ms'] * frames['all'] / 1000
        rate = total['all'] / wall_s
        tail = {}
        for c in ('tick', 'slow95', 'slow99'):
            other_frames = frames['all'] - frames[c]
            tail[c] = {}
            for region in regions['all']:
                inside = regions[c].get(region, 0)
                outside = regions['all'][region] - inside
                if frames[c] and other_frames:
                    excess = inside / frames[c] - outside / other_frames
                    tail[c][region] = {'samples_per_frame_in': inside / frames[c], 'samples_per_frame_out': outside / other_frames,
                                       'excess_sample_ms': 1000 * excess / rate}
        p = summary['phases'][phase]
        row = {
            'label': record['label'], 'scene': record['scene'], 'phase': phase, 'warmup': phase in WARMUPS,
            'client_samples': total, 'native_samples': data['native_samples'], 'allocation_samples': data['allocation_samples'],
            'allocation_weight': data['allocation_weight'], 'frames': frames, 'truncated': data['truncated_execution_samples'],
            'java_samples_per_wall_second': rate,
            'region_samples': regions['all'],
            'region_share_percent': {c: {k: 100 * v / total[c] for k, v in regions[c].items()} if total[c] else {} for c in CATEGORIES},
            'aggregate_share_percent': {c: {k: 100 * v / total[c] for k, v in aggregate(regions[c]).items()} if total[c] else {}
                                        for c in CATEGORIES},
            'aggregate_samples': aggregate(regions['all']),
            'native_region_samples': native,
            'native_share_percent': {k: 100 * v / max(1, data['native_samples']['all']) for k, v in native.items()},
            'allocation_share_percent': {c: {k: 100 * v / max(1, data['allocation_weight'][c]) for k, v in alloc[c].items()}
                                         for c in alloc},
            'allocation_objects_top': dict(sorted(objects.items(), key=lambda x: -x[1])[:12]),
            'sub_regions_top': {r: dict(sorted(s.items(), key=lambda x: -x[1])[:12]) for r, s in subs.items()},
            'uncovered_keys_top': dict(sorted(uncovered.items(), key=lambda x: -x[1])[:15]),
            'tail_excess': tail,
            'csv_sections': sections,
            'swap_share_of_frame_percent': 100 * sections['all']['swap_ms'] / sections['all']['frame_ms'],
            'csv_corroboration': corroborate(regions['all'], total['all'], sections['all']),
            'summary': {k: p[k] for k in ('frames', 'avg_fps', 'frame_p50_ms', 'frame_p99_ms', 'frame_max_ms',
                                          'client_thread_cpu_ms_per_frame', 'client_thread_cpu_utilization',
                                          'process_cpu_cores', 'alloc_mean_bytes', 'entities')},
            'anchor_exact_execution_native': data['anchor_exact_execution_native'],
            'anchor_prefix_execution_native': data['anchor_prefix_execution_native'],
            'other_threads_top': dict(sorted(data['other_thread_execution_samples'].items(), key=lambda x: -x[1])[:8]),
            'offset_ns': keys['offset_ns'],
            'tick_alignment': {'at_zero': [keys['tick_samples_in_tick_frames_at_zero'], keys['tick_execution_samples_at_zero']],
                               'at_offset': [keys['tick_samples_in_tick_frames_at_offset'], keys['tick_execution_samples']],
                               'plateau_ns': keys['offset_plateau_ns'], 'plateau_reaches_limit': keys['offset_plateau_reaches_limit']},
        }
        rows.append(row)
    return rows


def scene_tables(rows):
    tables = {}
    for scene in SCENES:
        combos = [r for r in rows if r['scene'] == scene and not r['warmup']]
        if not combos:
            continue
        names = sorted({k for r in combos for k in r['region_share_percent']['all']} |
                       {k for r in combos for k in r['aggregate_share_percent']['all']})
        entry = {'combinations': len(combos), 'regions': {}}
        for name in names:
            def share(r, c='all'):
                src = r['aggregate_share_percent'] if name.startswith('agg:') else r['region_share_percent']
                return src[c].get(name, 0.0)
            def count(r):
                return (r['aggregate_samples'] if name.startswith('agg:') else r['region_samples']).get(name, 0)
            pooled99 = sum(share(r, 'slow99') * r['client_samples']['slow99'] / 100 for r in combos)
            total99 = sum(r['client_samples']['slow99'] for r in combos)
            pooled_tick = sum(share(r, 'tick') * r['client_samples']['tick'] / 100 for r in combos)
            total_tick = sum(r['client_samples']['tick'] for r in combos)
            values = [share(r) for r in combos]
            entry['regions'][name] = {
                'median_share_percent': stats.median(values), 'min_share_percent': min(values), 'max_share_percent': max(values),
                'min_samples': min(count(r) for r in combos),
                'per_phase_median_percent': {ph: stats.median([share(r) for r in combos if r['phase'] == ph])
                                             for ph in sorted({r['phase'] for r in combos})},
                'slow99_pooled_share_percent': 100 * pooled99 / total99 if total99 else 0.0, 'slow99_pooled_samples': pooled99,
                'tick_pooled_share_percent': 100 * pooled_tick / total_tick if total_tick else 0.0,
                'native_median_share_percent': stats.median([r['native_share_percent'].get(name, 0.0) for r in combos])
                if not name.startswith('agg:') else None,
                'allocation_median_share_percent': stats.median([r['allocation_share_percent']['all'].get(name, 0.0) for r in combos])
                if not name.startswith('agg:') else None,
            }
        entry['swap_share_median_percent'] = stats.median([r['swap_share_of_frame_percent'] for r in combos])
        entry['driver_bound'] = entry['swap_share_median_percent'] >= 50
        tables[scene] = entry
    return tables


def eligibility(tables, rows):
    """protocol.md share test for region aggregates; removable sets are assessed in decision.py."""
    names = sorted({k for t in tables.values() for k in t['regions']})
    out = {}
    for name in names:
        counted = {}
        for scene, t in tables.items():
            v = t['regions'].get(name)
            if not v:
                continue
            consistent = v['min_share_percent'] >= 5 and v['min_samples'] >= 300 and not t['driver_bound']
            counted[scene] = {'median': v['median_share_percent'], 'consistent': consistent}
        ten = [s for s, v in counted.items() if v['median'] >= 10 and v['consistent']]
        twenty = [s for s, v in counted.items() if v['median'] >= 20 and v['consistent'] and s not in REFERENCE]
        tail = [s for s, t in tables.items() if s not in REFERENCE and name in t['regions']
                and t['regions'][name]['slow99_pooled_share_percent'] >= 20 and t['regions'][name]['slow99_pooled_samples'] >= 300]
        share_rule = (len(ten) >= 2 and any(s not in REFERENCE for s in ten)) or bool(twenty)
        group = group_of(name) if not name.startswith('agg:') else None
        disagree = sorted({r['scene'] for r in rows if not r['warmup'] and group and r['csv_corroboration'][group]['jfr_exceeds_csv']})
        out[name] = {'scenes_at_10': ten, 'scenes_at_20': twenty, 'tail_scenes_at_20': tail,
                     'meets_share_or_tail_rule': share_rule or bool(tail), 'csv_group': group,
                     'csv_disagreement_scenes': disagree}
    return out


def validate_and_load():
    manifest = json.loads((HERE / 'manifest.json').read_text())
    snapshot = manifest['source_snapshot']
    assert sha(ROOT / snapshot['path']) == snapshot['sha256']
    with zipfile.ZipFile(ROOT / snapshot['path']) as z:
        for name, h in manifest['source_sha256'].items():
            assert hashlib.sha256(z.read(name.replace('\\', '/'))).hexdigest() == h, name
    records = []
    for p in sorted((HERE / 'runs').glob('*.record.json')):
        r = json.loads(p.read_text())
        folder = ROOT / r['source']
        assert r['source_snapshot'] == snapshot and r['frozen_manifest'] and not r['smoke']
        assert r['base_world_sha256'] == manifest['base_world_sha256']
        assert sha(ROOT / r['log']) == r['log_sha256'] and sha(ROOT / r['jfr']) == r['jfr_sha256']
        assert Path(r['launcher']['ExecutablePath']).resolve() == JDK.resolve()
        assert r['shared_config_before_sha256'] == r['shared_config_after_sha256'] == SHARED
        for name, h in r['files_sha256'].items():
            assert sha(folder / name) == h, (r['label'], name)
        summary = json.loads((folder / 'summary.json').read_text())
        for phase, c in r['phase_configs'].items():
            assert json.loads((folder / (phase + '.json')).read_text())['config'] == c == summary['phases'][phase]['config']
        records.append(r)
    return manifest, records


def load_keys(path, record):
    with gzip.open(path, 'rt', encoding='utf-8') as f:
        keys = json.load(f)
    assert keys['jfr_sha256'] == record['jfr_sha256']
    return keys


def main():
    lines = json.loads((HERE / 'lines.json').read_text())
    assert sha(lines['runtime_jar']) == lines['runtime_jar_sha256']
    classifier = Classifier(lines)
    if len(sys.argv) == 4 and sys.argv[1] == '--smoke':
        attempt = json.loads(Path(sys.argv[2]).read_text())
        rows = phase_rows(attempt['record'], load_keys(Path(sys.argv[3]), attempt['record']), classifier)
        for row in rows:
            print(row['label'], row['phase'], row['client_samples']['all'], 'native', row['native_samples']['all'],
                  'offset', row['offset_ns'], 'tick alignment', row['tick_alignment'])
            for k, v in sorted(row['region_share_percent']['all'].items(), key=lambda x: -x[1])[:25]:
                print(f'  {v:6.2f}%  {k}  {list(row["sub_regions_top"][k].items())[:3]}')
            print('  uncovered', list(row['uncovered_keys_top'].items())[:8])
            print('  csv', {k: (round(v['jfr_estimate_ms'], 3), round(v['csv_ms'], 3)) for k, v in row['csv_corroboration'].items()
                            if v['jfr_exceeds_csv']})
        return
    manifest, records = validate_and_load()
    rows = []
    for r in records:
        rows += phase_rows(r, load_keys(HERE / 'runs' / (r['label'] + '.stream-keys.json.gz'), r), classifier)
    tables = scene_tables(rows)
    result = {'source_snapshot': manifest['source_snapshot'], 'runtime_jar_sha256': lines['runtime_jar_sha256'],
              'rows': rows, 'scenes': tables, 'eligibility': eligibility(tables, rows)}
    (HERE / 'metrics.json').write_text(json.dumps(result, indent=1) + '\n')
    write_results(result)
    print('Wrote metrics.json and results.md')


def write_results(result):
    out = ['# Session 26 whole-client results', '',
           'Hooks-absent 1 ms JFR (DebugNonSafepoints). Shares are of client-thread Java execution samples inside each '
           'phase\'s CSV frames; samples are counts, not times. Native samples use a 20 ms period and are reported '
           'separately; CSV times come from the always-installed ordinary profiler. Warmup phases are excluded from '
           'scene medians.', '']
    out += ['## Launches', '', '| Launch | Phase | Frames | FPS | p99 ms | Java samples | Native | Alloc samples | '
            'Swap % of frame | Client CPU ms/frame | CPU util | Offset ms | Tick alignment |', '|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|']
    for r in result['rows']:
        s = r['summary']
        out.append(f"| {r['label']} | {r['phase']}{' (warmup)' if r['warmup'] else ''} | {s['frames']} | {s['avg_fps']:.0f} | "
                   f"{s['frame_p99_ms']:.2f} | {r['client_samples']['all']} | {r['native_samples']['all']} | "
                   f"{r['allocation_samples']['all']} | {r['swap_share_of_frame_percent']:.1f}% | "
                   f"{s['client_thread_cpu_ms_per_frame']:.3f} | {s['client_thread_cpu_utilization']:.2f} | "
                   f"{r['offset_ns'] / 1e6:+.1f} | {r['tick_alignment']['at_offset'][0]}/{r['tick_alignment']['at_offset'][1]} |")
    for scene, t in result['scenes'].items():
        out += ['', f"## Scene {scene}{' (reference)' if scene in REFERENCE else ''}: {t['combinations']} launch/phase "
                f"combinations, median swap {t['swap_share_median_percent']:.1f}% of frame time"
                f"{' (driver-bound)' if t['driver_bound'] else ''}", '',
                '| Region | Median % | Min % | Max % | Min samples | Per phase median % | Slow-1% frames % | Tick frames % | '
                'Native median % | Allocation median % |', '|---|---:|---:|---:|---:|---|---:|---:|---:|---:|']
        ranked = sorted(t['regions'].items(), key=lambda x: -x[1]['median_share_percent'])
        for name, v in ranked:
            if v['median_share_percent'] < 0.5 and v['slow99_pooled_share_percent'] < 1:
                continue
            fmt = lambda x: '' if x is None else f'{x:.1f}'
            out.append(f"| {name} | {v['median_share_percent']:.1f} | {v['min_share_percent']:.1f} | {v['max_share_percent']:.1f} | "
                       f"{v['min_samples']}{' (low)' if v['min_samples'] < 300 else ''} | "
                       + ', '.join(f'{k} {x:.1f}' for k, x in v['per_phase_median_percent'].items())
                       + f" | {v['slow99_pooled_share_percent']:.1f} | {v['tick_pooled_share_percent']:.1f} | "
                       f"{fmt(v['native_median_share_percent'])} | {fmt(v['allocation_median_share_percent'])} |")
    out += ['', '## CSV section means (ms per frame; ordinary profiler)', '',
            '| Launch | Phase | Frames | frame | swap | tick | world | terrain setup | upload | solid | cutout m. | cutout | '
            'transl. | entities | particles | sky | clouds | gui | slow-1% frame | slow-1% swap | slow-1% upload | slow-1% tick |',
            '|---|---|---:|' + '---:|' * 19]
    for r in result['rows']:
        a, s99 = r['csv_sections']['all'], r['csv_sections']['slow99']
        cols = ('frame', 'swap', 'tick', 'world', 'terrain_setup', 'chunk_upload', 'terrain_solid', 'terrain_cutout_mipped',
                'terrain_cutout', 'terrain_translucent', 'entities', 'particles', 'sky', 'clouds', 'gui')
        out.append(f"| {r['label']} | {r['phase']} | {a['frames']} | " + ' | '.join(f"{a[c + '_ms']:.3f}" for c in cols)
                   + ' | ' + ' | '.join(f"{s99[c + '_ms']:.3f}" for c in ('frame', 'swap', 'chunk_upload', 'tick')) + ' |')
    out += ['', '## Share eligibility of regions and aggregates (protocol.md)', '',
            '| Region | Scenes >= 10% | Scenes >= 20% | Slow-1% scenes >= 20% | Meets share or tail rule | CSV group | JFR > CSV in |',
            '|---|---|---|---|---|---|---|']
    for name, v in sorted(result['eligibility'].items()):
        if v['scenes_at_10'] or v['tail_scenes_at_20']:
            out.append(f"| {name} | {', '.join(v['scenes_at_10'])} | {', '.join(v['scenes_at_20'])} | "
                       f"{', '.join(v['tail_scenes_at_20'])} | {'yes' if v['meets_share_or_tail_rule'] else 'no'} | "
                       f"{v['csv_group'] or ''} | {', '.join(v['csv_disagreement_scenes'])} |")
    out += ['', '## CSV corroboration (JFR estimate vs ordinary-profiler section, ms per frame; flagged when > 3x + 0.02 ms)', '',
            '| Launch | Phase | Flagged groups (estimate / CSV ms) |', '|---|---|---|']
    for r in result['rows']:
        flagged = [f"{k} {v['jfr_estimate_ms']:.3f}/{v['csv_ms']:.3f}" for k, v in r['csv_corroboration'].items() if v['jfr_exceeds_csv']]
        out.append(f"| {r['label']} | {r['phase']} | {'; '.join(flagged) or 'none'} |")
    out += ['', 'Sub-regions, tail excess, allocation objects, anchor exact/prefix counts and other threads are in metrics.json.', '']
    (HERE / 'results.md').write_text('\n'.join(out))


if __name__ == '__main__':
    main()
