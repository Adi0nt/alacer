"""Read diagnostic receipts after all clients have stopped; no timing selection from these runs."""
from pathlib import Path
import json, re

root = Path.cwd()
archive = Path(__file__).resolve().parent
receipts = json.loads((archive/'correctness-r2-launches.json').read_text())
assert len(receipts) == 8, 'Incomplete diagnostics'
result = {}
for receipt in receipts:
    data = json.loads((root/receipt['summary']).read_text()); env = data['environment']; label = receipt['label']
    log = (root/'build'/(label+'.log')).read_text(encoding='utf-8', errors='replace')
    counters = {k:int(v) for k,v in env.items() if k.startswith('modernGl.') and v.isdecimal()}
    assert env['window'] == '1920x1080' and env['java.version'].startswith('25.')
    assert counters['modernGl.vaoFailures'] == counters['modernGl.uploadFailures'] == 0
    assert not any(x in log for x in ['OpenGL Error', 'Mixin apply failed', 'Named VBO upload readback differs', 'Chunk visibility mismatch with unchanged'])
    entry = {'counters':counters, 'neighbours':env['chunkNeighbours.verification'], 'vboActive':env['vboActive']}
    if 'verify-' in label:
        assert counters['modernGl.vaoChecks'] > 0 and counters['modernGl.uploadChecks'] > 0
        assert re.match(r'[1-9][0-9]* compared, 0 mismatches,', entry['neighbours'])
        reports = re.findall(r'Chunk visibility verification: (\d+) searches compared; (\d+) mismatched with stable input; (\d+) had compiled chunk data change mid-comparison \((\d+) of those differed\)', log)
        assert reports and all(int(r[1]) == 0 for r in reports)
        entry['latest_visibility_report'] = dict(zip(['searches', 'stable_mismatches', 'inconclusive', 'inconclusive_differed'], map(int,reports[-1])))
        if 'edit' in label:
            assert int(env['benchmark.chunkEditBursts']) > 0
            entry['edit_bursts'] = env['benchmark.chunkEditBursts']
    if 'forced-fallback' in label or 'display-list' in label:
        assert counters['modernGl.vaoBinds'] == 0 and counters['modernGl.namedUploads'] == 0
        assert re.search(r'[1-9][0-9]* indexed lookups', entry['neighbours'])
        if 'forced-fallback' in label: assert env['modernGl.forcedFallback'] == 'true'
        if 'display-list' in label: assert env['vboActive'] == 'false'
    result[label] = entry
(archive/'correctness.json').write_text(json.dumps(result,indent=2)+'\n')
print('PASS',len(result),'diagnostic launches; timing not scored')
