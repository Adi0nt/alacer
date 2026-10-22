#!/usr/bin/env python3
"""Session 28, written after the gate series (not part of any frozen rule; never rescores gates.json).

Whole-frame alloc_bytes at ~20 FPS includes the client tick's allocation in most frames, which varied by tens of KB
between launches. This diagnostic repeats the in-process comparison on frames without a client tick (tick_ns == 0),
where the per-frame path is all that allocates. Usage: python3 posthoc.py <out.json>
"""
import csv
import json
import statistics
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent.parent


def nontick(folder, phases):
    out = []
    for name in phases:
        with open(ROOT / folder / (name + '.csv'), newline='') as f:
            out += [int(r['alloc_bytes']) for r in csv.DictReader(f) if int(r['tick_ns']) == 0]
    return out


result = {}
for label in ('alloc28g-ab-1-r2', 'alloc28g-ab-2', 'alloc28g-ab-3', 'alloc28g-ab-4'):
    source = json.loads((HERE / 'gates' / (label + '.record.json')).read_text())['source']
    for phase in ('static', 'pan'):
        a = nontick(source, (phase + '-a1', phase + '-a2'))
        b = nontick(source, (phase + '-b1', phase + '-b2'))
        result[f'{label}/{phase}'] = {'a_frames': len(a), 'b_frames': len(b),
                                      'a_median_bytes': statistics.median(a), 'b_median_bytes': statistics.median(b),
                                      'median_saving_bytes': statistics.median(a) - statistics.median(b)}
Path(sys.argv[1]).write_text(json.dumps(result, indent=1) + '\n')
for k, v in result.items():
    print(k, v)
