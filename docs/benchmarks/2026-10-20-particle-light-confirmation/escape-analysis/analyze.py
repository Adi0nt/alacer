"""Validate the four attribution runs and inspect exact pass allocation plus JIT XML."""
import hashlib
import json
from pathlib import Path
import statistics as stats
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
HERE = Path(__file__).resolve().parent
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()

def compilation(path):
    tree = ET.parse(path).getroot()
    compiled = {x.get('compile_id'): dict(x.attrib) for x in tree.iter('nmethod')}
    result = {'light_compilations': [], 'light_inlining': [], 'light_blockpos_eliminations': []}
    for task in tree.iter('task'):
        klass = {x.get('id'): x.get('name', '').replace('/', '.') for x in task.iter('klass')}
        methods = {x.get('id'): klass.get(x.get('holder'), x.get('holder', '?')) + '.' + x.get('name', '?') for x in task.iter('method')}
        root = task.get('method', '')
        identifier = task.get('compile_id')
        if 'getLightLevel' in root or 'alacer.render.ParticleLight' in root:
            result['light_compilations'].append({'root': root, 'compile_id': identifier,
                'bytes': task.get('bytes'), 'compiler': compiled.get(identifier, {}).get('compiler'),
                'stamp': task.get('stamp')})
        for parent in task.iter():
            call = None
            for node in parent:
                if node.tag == 'call': call = methods.get(node.get('method'), node.get('method'))
                elif node.tag in ('inline_success', 'inline_fail') and call and (
                        'getLightLevel' in call or 'alacer.render.ParticleLight' in call):
                    result['light_inlining'].append({'root': root, 'compile_id': identifier,
                        'callee': call, 'outcome': node.tag, 'reason': node.get('reason')})
            if parent.tag == 'eliminate_allocation':
                kind = klass.get(parent.get('type'), parent.get('type'))
                stack = [methods.get(x.get('method'), x.get('method')) for x in parent.iter('jvms')]
                if kind and kind.endswith('.BlockPos') and any(x and 'getLightLevel' in x for x in stack):
                    result['light_blockpos_eliminations'].append({'root': root, 'compile_id': identifier, 'stack': stack})
    return result

def main():
    rows = []
    for variant in ('original', 'scalar'):
        manifest = json.loads((HERE / variant / 'manifest.json').read_text())
        for p in sorted((HERE / variant / 'runs').glob('*.record.json')):
            r = json.loads(p.read_text()); folder = ROOT / r['source']
            assert r['source_snapshot'] == manifest['source_snapshot']
            for name, expected in r['files_sha256'].items(): assert sha(folder / name) == expected
            assert sha(ROOT / r['log']) == r['log_sha256']
            assert sha(ROOT / r['jfr']) == r['jfr_sha256']
            assert sha(ROOT / r['jit']) == r['jit_sha256']
            diagnostics = json.loads((folder / 'particle-draw-diagnostics.json').read_text())
            sampled = json.loads(p.with_name(p.name.replace('.record.json', '.stream-jfr.json')).read_text())
            jit = compilation(ROOT / r['jit'])
            (HERE / (r['label'] + '.compilation.json')).write_text(json.dumps(jit, indent=2) + '\n')
            for phase in ('static', 'pan'):
                d = diagnostics['phases'][phase]
                rows.append({'label': r['label'], 'variant': variant, 'phase': phase,
                    'pass_allocation_bytes': d['pass_allocation_bytes'] / d['passes'],
                    'passes': d['passes'], 'sampled': sampled['phases'][phase],
                    'jit_blockpos_elimination_events': len(jit['light_blockpos_eliminations'])})
    gates = []
    for phase in ('static', 'pan'):
        original = stats.median(x['pass_allocation_bytes'] for x in rows if x['phase'] == phase and x['variant'] == 'original')
        scalar = [x for x in rows if x['phase'] == phase and x['variant'] == 'scalar']
        changes = {x['label']: 100 * (x['pass_allocation_bytes'] / original - 1) for x in scalar}
        gates.append({'phase': phase, 'original_median_bytes': original, 'scalar_change_percent': changes,
                      'both_scalar_launches_improve_over_10_percent': all(x < -10 for x in changes.values())})
    result = {'comparison': rows, 'attribution_thresholds': gates,
              'stable_interface_allocation_benefit': all(g['both_scalar_launches_improve_over_10_percent'] for g in gates),
              'selection': 'Attribution only; retained original restored, reuse remains disabled.'}
    (HERE / 'metrics.json').write_text(json.dumps(result, indent=2) + '\n')
    out = ['# Scalar table-interface attribution', '',
           'Exact client-thread allocated bytes around unlit passes; JFR weights are sampled estimates.', '',
           '| Launch | Phase | Exact B/pass | Pass allocation samples | Sampled MB | Light BlockPos elimination records in whole JIT log |',
           '|---|---|---:|---:|---:|---:|']
    for label in ('lc23-ea-original-r1', 'lc23-ea-scalar-r1', 'lc23-ea-scalar-r2', 'lc23-ea-original-r2'):
        for r in [x for x in rows if x['label'] == label]:
            s = r['sampled']
            out.append(f"| {label} | {r['phase']} | {r['pass_allocation_bytes']:.1f} | {s['pass_allocation_samples']} | {s['pass_allocation_sampled_weight']/1e6:.1f} | {r['jit_blockpos_elimination_events']} |")
    out += ['', 'Stable >10% reduction in both scalar launches, in both phases: ' + str(result['stable_interface_allocation_benefit']), '',
            'The original can also avoid many allocations. Removing the position argument did not make elimination dependable. '
            'Whole-log elimination records describe particular compilations, not a guarantee that those versions remained active in each phase. '
            'Per-launch compilation JSON retains inlining successes/failures and method identities. No timing claim or promotion follows.', '']
    (HERE / 'results.md').write_text('\n'.join(out))
    print(json.dumps(gates, indent=2))

if __name__ == '__main__': main()
