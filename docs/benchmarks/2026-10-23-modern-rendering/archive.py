"""Archive this task's records after all clients stop; do not alter earlier archives."""
from pathlib import Path
import gzip, hashlib, json, shutil, zipfile

root = Path.cwd()
archive = Path(__file__).resolve().parent
records = {}
for run in sorted((root/'run/alacer/benchmarks').glob('*-mg-*')):
    if not (run/'summary.json').is_file():
        continue
    dest = archive/'raw'/run.name
    dest.mkdir(parents=True, exist_ok=True)
    for p in run.iterdir():
        if p.suffix == '.json':
            shutil.copy2(p, dest/p.name)
        elif p.suffix == '.csv':
            (dest/(p.name+'.gz')).write_bytes(gzip.compress(p.read_bytes(), mtime=0))
        if p.suffix in ['.json', '.csv']:
            records[p.relative_to(root).as_posix()] = hashlib.sha256(p.read_bytes()).hexdigest()
for folder, pattern, dest in [('build','mg-*.log','logs'), ('run/screenshots','alacer-mg-*.png','screenshots')]:
    target = archive/dest; target.mkdir(exist_ok=True)
    for p in (root/folder).glob(pattern):
        shutil.copy2(p, target/p.name)
        records[p.relative_to(root).as_posix()] = hashlib.sha256(p.read_bytes()).hexdigest()
for p in (root/'run').glob('mg-*.jfr'):
    records[p.relative_to(root).as_posix()] = hashlib.sha256(p.read_bytes()).hexdigest()
with zipfile.ZipFile(archive/'measured-source.zip', 'w', zipfile.ZIP_DEFLATED) as out:
    for p in sorted((root/'src').rglob('*')):
        if p.is_file(): out.write(p, p.relative_to(root).as_posix())
    for name in ['build.gradle', 'settings.gradle', 'gradle.properties', 'gradle/gradle-daemon-jvm.properties']:
        if (root/name).is_file(): out.write(root/name, name)
(archive/'raw-manifest.json').write_text(json.dumps(records, indent=2)+'\n')
print('Archived', len(records), 'original file hashes')
