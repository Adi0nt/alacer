"""Restore/hash-check this archive into a NEW directory and reproduce the evaluation."""
import gzip
import hashlib
import json
from pathlib import Path
import sys

import evaluate

HERE = Path(__file__).resolve().parent


def main():
    if len(sys.argv) != 2:
        raise SystemExit('Usage: python -B replay.py NEW_OUTPUT_DIRECTORY')
    output = Path(sys.argv[1]).resolve()
    output.mkdir(parents=True, exist_ok=False)
    manifest = json.loads((HERE / 'raw-manifest.json').read_text())
    for original, expected in manifest.items():
        relative = Path(original)
        if relative.is_absolute() or '..' in relative.parts or relative.parts[:3] != ('run', 'alacer', 'benchmarks'):
            raise ValueError('Unexpected raw path: ' + original)
        compressed = relative.suffix == '.csv'
        source = HERE / 'raw' / relative.parent.name / (relative.name + '.gz' if compressed else relative.name)
        content = gzip.decompress(source.read_bytes()) if compressed else source.read_bytes()
        if hashlib.sha256(content).hexdigest() != expected:
            raise ValueError('Raw hash mismatch: ' + original)
        target = output / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(content)

    receipts = json.loads((HERE / 'launches.json').read_text())
    for r in receipts:
        log = HERE / 'logs' / Path(r['log']).name
        if hashlib.sha256(log.read_bytes()).hexdigest() != r['log_sha256']:
            raise ValueError('Log hash mismatch: ' + r['label'])

    evaluate.ROOT = output
    restored = evaluate.analyze(dest=output / 'evaluation.json')
    recorded = json.loads((HERE / 'evaluation.json').read_text())
    match = restored == recorded
    if not match:
        raise ValueError('Evaluation does not reproduce!')

    result = {
        'raw_files_verified': len(manifest),
        'logs_verified': len(receipts),
        'evaluation_structurally_identical': match,
        'note': 'JSON numeric/structural equality; no cross-platform byte-identity claim.'
    }
    (output / 'replay-verification.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
