"""Format existing scored metrics; does not read/re-score raw launches or change any gates.

The frozen analyzer added ordinary-frame metrics but retained its old startup table header.
Use this correctly labelled report for presentation; metrics.json is the scored result.
"""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
m = json.loads((HERE / 'metrics.json').read_text())
keys = ('particle_pass_ms', 'frame_ms', 'non_tick_frame_ms', 'client_cpu_ms_per_frame',
        'alloc_bytes_per_frame', 'frame_p95_ms', 'frame_p99_ms', 'tick_frame_ms', 'tick_frame_p99_ms')
head = '| Phase | Pass | Frame | Ordinary frame | Client CPU/frame | Alloc/frame | Frame p95 | Frame p99 | Tick frame | Tick p99 | Result |'
sep = '|---|' + '---:|' * len(keys) + '---|'
out = ['# Session 23 results', '', 'All changes are percentages; negative means lower. No launch is excluded.', '',
       '## In-process confirmation: median per-launch changes', '', head, sep]
for g in m['performance_gates']:
    d = g['median_delta_percent']
    out.append('| ' + g['phase'] + ' | ' + ' | '.join(f'{d[k]:+.2f}%' for k in keys)
               + ' | ' + ('PASS' if g['passed'] else 'FAIL') + ' |')
out += ['', '## Startup confirmation: changes between launch medians', '', head, sep]
for g in m['startup_gates']:
    d = g['change_percent']
    failed = ', '.join(k for k, v in g['checks'].items() if not v)
    out.append('| ' + g['phase'] + ' | ' + ' | '.join(f'{d[k]:+.2f}%' for k in keys)
               + ' | ' + ('PASS' if g['passed'] else 'FAIL: ' + failed) + ' |')
out += ['', '## Startup launches (actual values)', '',
        '| Launch | Phase | Frame ms | Pass ms | Client CPU ms/frame | Allocation KB/frame | Tick-frame p99 ms |',
        '|---|---|---:|---:|---:|---:|---:|']
for r in m['startup_comparison']:
    d = r['metrics']
    out.append(f"| {r['label']} | {r['phase']} | {d['frame_ms']:.3f} | {d['particle_pass_ms']:.3f} | "
               f"{d['client_cpu_ms_per_frame']:.3f} | {d['alloc_bytes_per_frame']/1024:.1f} | {d['tick_frame_p99_ms']:.3f} |")
out += ['', 'The frozen analyzer\'s startup Markdown header omits the ordinary-frame columns it prints. '
        'This report reads its unchanged metrics.json and supplies correctly labelled columns. '
        'Gate evaluation and raw evidence are unchanged.', '']
(HERE / 'results.md').write_text('\n'.join(out))
print('Formatted results.md from existing metrics.json')
