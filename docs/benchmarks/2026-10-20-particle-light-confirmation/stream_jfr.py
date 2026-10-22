"""Validate a run's hashes, then stream JFR phase attribution through Java 25 RecordingFile.

Compile JfrPassAttribution.java with JsonWriter on the classpath before invoking this helper.
"""
import argparse
import csv
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[3]

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
    summary = json.loads((folder / 'summary.json').read_text())
    epoch_ns = int(summary['environment']['profiler.epochWallMillis']) * 1_000_000
    windows = []
    for name in summary['phases']:
        if 'warmup' in name:
            continue
        lo, hi = None, None
        with (folder / (name + '.csv')).open() as f:
            for row in csv.DictReader(f):
                start = int(row['start_ns'])
                end = start + int(row['frame_ns'])
                lo = start if lo is None else min(lo, start)
                hi = end if hi is None else max(hi, end)
        assert lo is not None
        windows.append(f'{name}:{epoch_ns + lo}:{epoch_ns + hi}')
    classpath = os.pathsep.join(str(ROOT / p) for p in ('build/lc23-attribution-tools', 'build/classes/java/main'))
    subprocess.run([str(Path(record['java_home']) / 'bin/java.exe'), '-cp', classpath,
                    'JfrPassAttribution', str(ROOT / record['jfr']), str(opts.output), *windows], check=True)
    result = json.loads(opts.output.read_text())
    result.update({'record': str(opts.record), 'jfr_sha256': record['jfr_sha256'],
                   'source_snapshot': record['source_snapshot'],
                   'epoch_uncertainty_ns': summary['environment']['profiler.epochWallUncertaintyNanos']})
    opts.output.write_text(json.dumps(result, indent=2) + '\n')
    print('Streamed', record['label'], flush=True)

if __name__ == '__main__':
    main()
