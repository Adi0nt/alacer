"""Reproduce the frustum findings on built named classes; does not launch Minecraft."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--jdk', type=Path, default=Path('C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot'))
    p.add_argument('--output', type=Path)
    a = p.parse_args()
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    out = a.output or ROOT / 'build' / ('frustum-audit-' + stamp)
    out.mkdir(parents=True, exist_ok=False)
    jars = sorted((ROOT / '.gradle/loom-cache/minecraftMaven').rglob('*.jar'))
    minecraft = None
    for jar in jars:
        with zipfile.ZipFile(jar) as z:
            if 'net/minecraft/client/render/FrustumData.class' in z.namelist():
                minecraft = jar
                break
    if minecraft is None:
        raise RuntimeError('build named Minecraft classes first')
    cp = os.pathsep.join(map(str, (ROOT / 'build/classes/java/main', minecraft)))
    commands = [('compile', [str(a.jdk / 'bin/javac.exe'), '--release', '25', '--add-modules',
                             'jdk.incubator.vector', '-cp', cp, '-d', str(out),
                             str(HERE / 'FrustumAuditProbe.java')])]
    run_cp = str(out) + os.pathsep + cp
    java = str(a.jdk / 'bin/java.exe')
    target = ['-cp', run_cp, 'net.adiont.alacer.render.FrustumAuditProbe']
    commands += [('vector', [java, '--add-modules=jdk.incubator.vector', *target]),
                 ('no-avx', [java, '-XX:UseAVX=0', '--add-modules=jdk.incubator.vector', *target]),
                 ('no-module', [java, *target])]
    receipts = []
    for name, cmd in commands:
        r = subprocess.run(cmd, capture_output=True, text=True)
        receipts.append(dict(name=name, command=cmd, exit_code=r.returncode, stdout=r.stdout, stderr=r.stderr))
        if name == 'compile' and r.returncode:
            raise RuntimeError(r.stderr)
    result = dict(probe_source_sha256=hashlib.sha256((HERE / 'FrustumAuditProbe.java').read_bytes()).hexdigest(),
                  target_class_sha256=hashlib.sha256((ROOT / 'build/classes/java/main/net/adiont/alacer/render/VectorFrustum.class').read_bytes()).hexdigest(),
                  receipts=receipts)
    (out / 'results.json').write_text(json.dumps(result, indent=2) + '\n')
    checks = [r['exit_code'] == 0 and 'REPRODUCED:' in r['stdout'] for r in receipts[1:3]]
    checks.append(receipts[3]['exit_code'] != 0 and 'NoClassDefFoundError' in receipts[3]['stderr'])
    if not all(checks):
        raise RuntimeError('findings changed: inspect ' + str(out / 'results.json'))
    print(out / 'results.json')


if __name__ == '__main__':
    main()
