#!/usr/bin/env python3
"""Scope JFR sampled weights to particle movement, separately for full run and scored phases."""
import argparse
from collections import Counter
import csv
from datetime import datetime
import hashlib
import json
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('record', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    opts = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    record = json.loads(opts.record.read_text())
    recording = root / record['jfr']
    assert hashlib.sha256(recording.read_bytes()).hexdigest() == record['jfr_sha256']
    executable = Path(record['java_home']) / 'bin/jfr.exe'
    raw = subprocess.run([str(executable), 'print', '--json', '--stack-depth', '64', '--events',
                          'jdk.ObjectAllocationSample,jdk.ExecutionSample', str(recording)],
                         check=True, capture_output=True).stdout
    events = json.loads(raw)['recording']['events']
    folder = root / record['source']
    summary = json.loads((folder / 'summary.json').read_text())
    epoch = int(summary['environment']['profiler.epochWallMillis']) / 1000
    windows = {}
    phase_detail = {}
    for name in summary['phases']:
        with (folder / (name + '.csv')).open() as f:
            rows = [{k: float(v) for k, v in row.items()} for row in csv.DictReader(f)]
        windows[name] = (epoch + min(r['start_ns'] for r in rows) / 1e9,
                         epoch + max(r['start_ns'] + r['frame_ns'] for r in rows) / 1e9)
        ticks = [r for r in rows if r['particles_ticked'] > 0]
        phase_detail[name] = {'tick_frames': len(ticks), 'movement_ms_per_tick_frame': sum(r['particle_move_ns'] for r in ticks)/len(ticks)/1e6,
                              'particle_tick_ms_per_tick_frame': sum(r['particle_tick_ns'] for r in ticks)/len(ticks)/1e6,
                              'particles_per_tick_frame': sum(r['particles_ticked'] for r in ticks)/len(ticks)}
    reports = {}
    for scope in ['full-run', *windows]:
        totals, sites, counts, objects, execution = Counter(), Counter(), Counter(), Counter(), Counter()
        for event in events:
            v = event['values']
            thread = v.get('sampledThread') or v.get('eventThread') or {}
            if thread.get('javaName') != 'main':
                continue
            timestamp = datetime.fromisoformat(v['startTime']).timestamp()
            if scope in windows and not windows[scope][0] <= timestamp < windows[scope][1]:
                continue
            frames = (v.get('stackTrace') or {}).get('frames', [])
            names = [f['method']['type']['name'] + '.' + f['method']['name'] for f in frames]
            particle = any('ParticleManager.tickParticle' in n for n in names)
            if event['type'] == 'jdk.ExecutionSample':
                execution['client_samples'] += 1
                if particle:
                    execution['particle_tick_samples'] += 1
                    for n in ('Entity.move', 'World.getCollisions', 'World.getEntities'):
                        execution[n] += any(n in f for f in names)
                continue
            w = v['weight']
            totals['client_sampled_weight_bytes'] += w
            if not particle:
                continue
            totals['particle_tick_sampled_weight_bytes'] += w
            objects[v['objectClass']['name']] += w
            for i, name in enumerate(names):
                if name.startswith('net/minecraft/util/math/Box.'):
                    caller = names[i+1] + ':' + str(frames[i+1]['lineNumber']) if i+1 < len(names) else ''
                    site = name + ' <- ' + caller
                    sites[site] += w
                    counts[site] += 1
                    break
        reports[scope] = {'sampled_weights': dict(totals), 'execution_samples': dict(execution),
                          'box_allocation_sites': [{'site': s, 'weight_bytes': w, 'samples': counts[s]} for s, w in sites.most_common()],
                          'particle_tick_object_classes': dict(objects.most_common(12))}
    result = {'jfr': record['jfr'], 'jfr_sha256': record['jfr_sha256'], 'source_snapshot': record['source_snapshot'],
              'scope_note': 'JFR sampled weights are estimates, not exact allocated bytes. Phase windows use profiler epoch plus CSV start_ns; epoch uncertainty is retained in raw exports. Intrusive detail/JFR is attribution only.',
              'phase_detail': phase_detail, 'reports': reports}
    opts.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(phase_detail, indent=2))


if __name__ == '__main__':
    main()
