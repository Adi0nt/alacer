"""Explicit ordering-only validator amendment; reuse the completed first control."""
import copy
import json
from pathlib import Path
import sys
import traceback

import run_diagnostic as base

HERE, ROOT = base.HERE, base.ROOT
original_save, original_validate = base.save, base.validate


def save(name, value):
    target = Path(name)
    original_save(target.stem + '-r2' + target.suffix, value)


def validate(data, arm):
    reference = json.loads((base.OLD/'raw/20261023-133916-mg-ablation-control-vis_on-ecl_on-ent0/summary.json').read_text())
    expected = reference['environment']['mods']
    actual = data['environment']['mods']
    base.require(sorted(actual.split()) == sorted(expected.split()), 'Loaded mod/version tokens differ')
    canonical = copy.deepcopy(data)
    canonical['environment']['mods'] = expected
    original_validate(canonical, arm)


def main():
    base.require(Path.cwd().resolve() == ROOT, 'Run from the specified worktree root')
    base.check_idle()
    prior = json.loads((HERE/'launches.json').read_text())
    base.require(len(prior) == 1 and prior[0]['arm'] == 'A' and prior[0]['exit'] == 0, 'Unexpected prior attempt')
    frozen = json.loads((HERE/'freeze.json').read_text())
    base.check(frozen)
    first = copy.deepcopy(prior[0])
    summary = ROOT/first['summary']
    log = ROOT/first['log']
    base.require(base.digest(log) == first['log_sha256'], 'First log changed')
    for p, h in json.loads((HERE/'raw-manifest.json').read_text()).items():
        base.require(base.digest(ROOT/p) == h, 'First raw file changed: ' + p)
    validate(json.loads(summary.read_text()), 'A')
    for p in list(summary.parent.iterdir()) + [log, HERE/'run_diagnostic_r2.py', HERE/'amendment-r2.md',
            HERE/'freeze.json', HERE/'failure.json', HERE/'state.json', HERE/'launches.json', HERE/'raw-manifest.json']:
        if p.is_file():
            frozen[p.relative_to(ROOT).as_posix()] = base.digest(p)
    base.check(frozen)
    save('freeze.json', frozen)
    save('preflight.json', {'started_utc': base.now(), 'source_head': base.HEAD, 'java_home': str(base.JDK),
         'frozen_entries': len(frozen), 'adopted_launch': first['label'], 'remaining_launch_budget': 7,
         'mod_list_order_only_amendment': True})
    first.update(status='valid', adopted_from='launches.json', original_harness_status=prior[0]['status'],
                 revalidated_utc=base.now(), amendment='amendment-r2.md')
    base.RECEIPTS.append(first)
    save('launches.json', base.RECEIPTS)
    save('state.json', {'status': 'running', 'stage': 1, 'adopted_launches': 1, 'started_utc': base.now()})
    print('ADOPTED', first['label'], 'mod/version tokens match; original r1 failure retained', flush=True)
    stage1 = [first] + [base.launch(1, i, arm, frozen) for i, arm in enumerate('VVA', 2)]
    result = base.compare(stage1, 'A')
    save('stage1-evaluation.json', result)
    base.archive()
    print('STAGE1 large_pan_gap_reproduced=' + str(result['large_pan_gap_reproduced']), flush=True)
    if result['large_pan_gap_reproduced']:
        save('state.json', {'status': 'running', 'stage': 2, 'updated_utc': base.now()})
        stage2 = [base.launch(2, i, arm, frozen) for i, arm in enumerate('FVVF', 1)]
        save('stage2-evaluation.json', base.compare(stage2, 'F'))
        base.archive()
    base.check(frozen)
    save('state.json', {'status': 'complete', 'completed_launches': len(base.RECEIPTS), 'ended_utc': base.now(),
         'stage2_run': len(base.RECEIPTS) == 8, 'frozen_entries_unchanged': len(frozen), 'selection_evidence': False})
    print('COMPLETE', len(base.RECEIPTS), base.now(), flush=True)


if __name__ == '__main__':
    if (HERE/'freeze-r2.json').exists():
        sys.exit('Amended attempt r2 already exists; preserve it before any further work.')
    base.save, base.validate, base.ATTEMPT = save, validate, 'r2'
    try:
        main()
    except Exception as error:
        if base.RECEIPTS:
            if base.RECEIPTS[-1]['status'] != 'valid':
                base.RECEIPTS[-1]['status'] = 'failed'
            save('launches.json', base.RECEIPTS)
            base.archive()
        save('failure.json', {'time_utc': base.now(), 'error': repr(error), 'traceback': traceback.format_exc()})
        save('state.json', {'status': 'failed', 'launch_count': len(base.RECEIPTS), 'time_utc': base.now()})
        raise
