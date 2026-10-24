#!/usr/bin/env python3
"""Read-only attribution from existing results. Writes only a new output; never scores or edits an archive."""
import argparse
import csv
import json
import math
import re
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def cpu_distribution_hint(client_ms, frame_ms):
    """Descriptive buckets motivated by Session 31, never acceptance criteria or driver attribution."""
    if frame_ms <= 0 or not math.isfinite(client_ms) or not math.isfinite(frame_ms):
        return 'unknown'
    share = client_ms / frame_ms
    return 'low-client-cpu' if share < .5 else 'client-heavy' if share > .8 else 'mixed'


def frame_bins(rows, seconds=5):
    """Bin by elapsed frame start (not frame count); retain every frame, including tails."""
    bins = {}
    origin = None
    for row in rows:
        start = int(row['start_ns'])
        if origin is None:
            origin = start
        i = int((start - origin) / (seconds * 1e9))
        entry = bins.setdefault(i, {'start_s': i * seconds, 'frames': 0, 'sums': defaultdict(float)})
        entry['frames'] += 1
        for name in ('frame_ns', 'render_ns', 'swap_ns', 'tick_ns', 'visible_chunks', 'pending_chunks'):
            entry['sums'][name] += float(row[name])
    return [dict(start_s=e['start_s'], frames=e['frames'],
                 **{k.replace('_ns', '_ms'): v / e['frames'] / (1e6 if k.endswith('_ns') else 1)
                    for k, v in e['sums'].items()}) for e in bins.values()]


def java_names(texts):
    names = {}
    for text in texts:
        for name, native in re.findall(r'^"([^"]+)"[^\n]*nid=(0x[0-9a-fA-F]+|[0-9]+)', text, re.M):
            names[int(native, 16 if native.startswith('0x') else 10)] = name
    return names


def thread_window(samples, start, end, names):
    """Use complete intervals inside the phase; reject recycled tid counters and missing CPU, never treat as zero."""
    totals = defaultdict(float)
    covered = self_cpu = 0.0
    for previous, current in zip(samples, samples[1:]):
        if not (start <= previous['t'] < current['t'] <= end):
            continue
        if 'threads' not in previous or 'threads' not in current:
            continue
        elapsed = current['t'] - previous['t']
        # A gap is explicitly excluded rather than interpolated into continuous thread coverage.
        if elapsed > 2.5:
            continue
        old = {(r['id'], r.get('created')): r for r in previous['threads'] if 'cpu_s' in r}
        covered += elapsed
        self_cpu += current['self_cpu_s']
        for row in current['threads']:
            before = old.get((row['id'], row.get('created')))
            if before is None or 'cpu_s' not in row:
                continue
            delta = row['cpu_s'] - before['cpu_s']
            if not math.isfinite(delta) or delta < 0:
                continue
            name = names.get(row['id'], 'native-origin:' + str(row.get('module')))
            totals[name] += delta
    return {'covered_s': covered, 'sampler_cpu_cores': self_cpu / covered if covered else None,
            'cpu_cores': {k: v / covered for k, v in sorted(totals.items(), key=lambda x: -x[1])}
            if covered else {}}


def analyze(archive, include_bins=False, supplemental_labels=()):
    output = {'archive': str(archive), 'scoring': 'none; descriptive attribution only', 'launches': []}
    records = [(json.loads(path.read_text()), json.loads((archive / 'attempts' / path.name).read_text()), 'record')
               for path in sorted((archive / 'records').glob('*.json'))]
    for label in supplemental_labels:
        # Explicit opt-in for attribution from a completed but rejected raw launch. Never manufacture a record
        # in the archive or imply that this restores its validity.
        import aa_rules
        aa_rules.check_label(label)
        attempt = json.loads((archive / 'attempts' / (label + '.json')).read_text())
        if any(r['label'] == label for r, _, _ in records):
            raise ValueError(f'{label} already has a record')
        folders = list((ROOT / 'run/alacer/benchmarks').glob(f'*-{label}-vis_*'))
        if len(folders) != 1:
            raise ValueError(f'{label}: expected one raw result folder')
        folder = folders[0]
        summary = json.loads((folder / 'summary.json').read_text())
        epoch = int(summary['environment']['profiler.epochWallMillis'])
        metrics = {name: aa_rules.phase_metrics(aa_rules.read_rows(folder / (name + '.csv')), p, epoch)
                   for name, p in summary['phases'].items()}
        records.append((dict(label=label, scene=attempt['scene'], source=str(folder), metrics=metrics, monitor=None),
                        attempt, 'supplemental-raw; original failure retained'))
    for record, attempt, origin in records:
        source = ROOT / record['source']
        label = record['label']
        raw = ROOT / 'run/alacer/stability' / ('diag32' if label.startswith('diag32') else 'aa31w2')
        trace = raw / (label + '-threads.jsonl')
        samples = [json.loads(line) for line in trace.read_text().splitlines()] if trace.exists() else []
        names = java_names(p.read_text() for p in raw.glob(label + '-dump-*.txt'))
        phases = {}
        for phase, metrics in record['metrics'].items():
            detail = json.loads((source / (phase + '.json')).read_text())
            client = metrics['client_cpu_ms_per_frame']
            process = metrics['process_cpu_ms_per_frame']
            row = {'frame_ms': metrics['frame_mean_ms'], 'client_cpu_ms': client, 'process_cpu_ms': process,
                   'off_client_cpu_ms': process - client,
                   'cpu_distribution_hint': cpu_distribution_hint(client, metrics['frame_mean_ms']),
                   'sections_ms': {k: detail['sections_ms'][k]['mean'] for k in ('render', 'swap', 'tick', 'terrain_setup')},
                   'visible_chunks': detail['chunks'],
                   'allocation_bytes_per_frame': detail['render_thread_alloc_bytes_per_frame']['mean']}
            if include_bins:
                with (source / (phase + '.csv')).open(newline='') as frames:
                    row['bins_5s'] = frame_bins(csv.DictReader(frames))
            if samples:
                row['threads'] = thread_window(samples, metrics['wall_start'], metrics['wall_end'], names)
            if record['monitor']:
                row['monitor'] = record['monitor']['phases'][phase]
            phases[phase] = row
        references = [phases.get(n, {}).get('cpu_distribution_hint', 'unknown')
                      for n in ('reference-pre', 'reference-post')]
        output['launches'].append({'label': label, 'status': attempt['status'],
                                   'origin': origin,
                                   'environment_assessed': record['monitor'] is not None,
                                   'environment_rejections': attempt.get('environment_rejections', attempt.get('reasons')),
                                   'scene': record['scene'], 'reference_distribution_changed': references[0] != references[1],
                                   'phases': phases})
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--bins', action='store_true')
    parser.add_argument('--supplemental-label', action='append', default=[],
                        help='explicit rejected raw attempt for attribution only; never creates a record')
    args = parser.parse_args()
    result = analyze(args.archive, args.bins, args.supplemental_label)
    with args.output.open('x', encoding='utf-8') as out:
        json.dump(result, out, indent=1, allow_nan=False)
        out.write('\n')


if __name__ == '__main__':
    main()
