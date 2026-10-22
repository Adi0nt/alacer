"""Session 26: validate a run's hashes, write its CSV frame list and stream whole-frame stack keys (JfrWholeFrame).

Adapted from session 25's stream_regions.py (preserved unchanged): same hash checks, CSV frames joined to the
profiler wall epoch. Each CSV frame becomes one line of the frames file: phase, start and end epoch ns and flags
(1 ticking frame: tick_ns > 0; 2 frame_ns at or above the phase's nearest-rank p95; 4 at or above its p99).
The output is gzip-compressed JSON. Compile the helper first:
  javac -cp build/classes/java/main -d build/wc26-attribution-tools JfrWholeFrame.java
"""
import argparse
import csv
import gzip
import hashlib
import json
import math
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[3]
TOOLS = 'build/wc26-attribution-tools'


def nearest_rank(values, q):
    ordered = sorted(values)
    return ordered[max(0, math.ceil(q * len(ordered)) - 1)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('record', type=Path)
    parser.add_argument('output', type=Path)
    opts = parser.parse_args()
    record = json.loads(opts.record.read_text())
    folder = ROOT / record['source']
    sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
    assert sha(ROOT / record['jfr']) == record['jfr_sha256']
    assert sha(ROOT / record['log']) == record['log_sha256']
    for name, expected in record['files_sha256'].items():
        assert sha(folder / name) == expected
    helper = Path(__file__).resolve().parent / 'JfrWholeFrame.java'
    summary = json.loads((folder / 'summary.json').read_text())
    epoch_ns = int(summary['environment']['profiler.epochWallMillis']) * 1_000_000
    uncertainty = int(summary['environment']['profiler.epochWallUncertaintyNanos'])
    lines, thresholds = [], {}
    for name in summary['phases']:
        with (folder / (name + '.csv')).open() as f:
            rows = [(int(r['start_ns']), int(r['frame_ns']), int(r['tick_ns'])) for r in csv.DictReader(f)]
        assert rows
        p95 = nearest_rank([r[1] for r in rows], 0.95)
        p99 = nearest_rank([r[1] for r in rows], 0.99)
        thresholds[name] = {'p95_ns': p95, 'p99_ns': p99, 'frames': len(rows)}
        for start, frame, tick in rows:
            flags = (1 if tick > 0 else 0) | (2 if frame >= p95 else 0) | (4 if frame >= p99 else 0)
            lines.append(f'{name}\t{epoch_ns + start}\t{epoch_ns + start + frame}\t{flags}')
    frames_file = opts.output.with_name(opts.output.name + '.frames.tsv')
    raw = opts.output.with_name(opts.output.name + '.raw.json')
    frames_file.write_text('\n'.join(lines) + '\n')
    classpath = os.pathsep.join(str(ROOT / p) for p in (TOOLS, 'build/classes/java/main'))
    subprocess.run([str(Path(record['java_home']) / 'bin/java.exe'), '-cp', classpath, 'JfrWholeFrame',
                    str(ROOT / record['jfr']), str(raw), str(frames_file), str(uncertainty)], check=True)
    result = json.loads(raw.read_text())
    result.update({'record': str(opts.record), 'jfr_sha256': record['jfr_sha256'], 'helper_sha256': sha(helper),
                   'source_snapshot': record['source_snapshot'], 'frame_thresholds': thresholds,
                   'frames_file_sha256': sha(frames_file)})
    # mtime=0 keeps the compressed bytes a function of the content alone.
    with opts.output.open('wb') as raw_out, gzip.GzipFile(fileobj=raw_out, mode='wb', mtime=0) as out:
        out.write((json.dumps(result, indent=1) + '\n').encode('utf-8'))
    raw.unlink()
    frames_file.unlink()
    print('Streamed', record['label'], 'offset', result['offset_ns'], 'ns', flush=True)


if __name__ == '__main__':
    main()
