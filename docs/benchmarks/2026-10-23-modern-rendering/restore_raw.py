"""Restore scored raw inputs in a NEW directory for portable evaluation from the committed archive."""
from pathlib import Path
import gzip, hashlib, json, sys

archive = Path(__file__).resolve().parent
dest = Path(sys.argv[1]).resolve()
dest.mkdir(parents=True, exist_ok=False)
manifest = json.loads((archive/'raw-manifest.json').read_text())
receipts = json.loads((archive/'performance-launches.json').read_text())
count = 0
for receipt in receipts:
    relative = Path(receipt['summary']).parent
    source = archive/'raw'/relative.name
    target = dest/relative
    target.mkdir(parents=True, exist_ok=False)
    for p in source.iterdir():
        if p.suffix == '.json':
            name, data = p.name, p.read_bytes()
        elif p.name.endswith('.csv.gz'):
            name, data = p.name[:-3], gzip.decompress(p.read_bytes())
        else:
            continue
        assert hashlib.sha256(data).hexdigest() == manifest[(relative/name).as_posix()]
        (target/name).write_bytes(data); count += 1
print('Restored and hash-verified', count, 'scored raw files to', dest)
