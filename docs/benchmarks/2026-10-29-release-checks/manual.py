"""Normal, non-benchmark profile client for the retained UI/loopback lifecycle attempt."""
import datetime
import json
from pathlib import Path
import re
import shutil
import subprocess
import time
import zipfile
from profile import HERE, ROOT, WORK, INSTALL, JDK, PROFILE, sha, expand


def main():
    label = 'rc-ui-loopback'
    target = HERE / 'launches' / (label + '.json')
    if target.exists(): raise RuntimeError('Lifecycle client already attempted')
    prepared = json.loads((WORK / 'prepared-profile.json').read_text())
    for name, expected in prepared['profile_hashes'].items():
        if sha(INSTALL / name) != expected: raise RuntimeError('Installer metadata changed')
    game = WORK / label
    game.mkdir(exist_ok=False); (game / 'mods').mkdir(); (game / 'saves').mkdir(); (game / 'resourcepacks').mkdir()
    artifact = ROOT / 'build/libs/alacer-1.0.0+mc1.8.9.jar'
    shutil.copy2(artifact, game / 'mods' / artifact.name)
    for file in (ROOT / '.gradle/loom-cache/remapClasspath.txt').read_text().split(';'):
        if 'net.ornithemc.osl-gen2' in file: shutil.copy2(file, game / 'mods' / Path(file).name)
    options = (ROOT / 'run/options.txt').read_text()
    options = re.sub(r'(?m)^soundCategory_master:[^\r\n]*', 'soundCategory_master:0.0', options)
    (game / 'options.txt').write_text(options)
    for name in ('release-world-A', 'release-world-B'):
        shutil.copytree(ROOT / 'run/saves/alacer-benchmark', game / 'saves' / name)
    pack = game / 'resourcepacks/release-language-check.zip'
    with zipfile.ZipFile(pack, 'w') as archive:
        archive.writestr('pack.mcmeta', json.dumps({'pack': {'pack_format': 1, 'description': 'Alacer lifecycle language check'}}))
        archive.writestr('assets/minecraft/lang/en_US.lang', 'menu.singleplayer=Release Check Worlds\nmenu.multiplayer=Release Check Servers\n')
    substitutions = dict(natives_directory=prepared['native'], launcher_name='AlacerProfileTest', launcher_version='1',
        classpath=';'.join(prepared['classpath']), auth_player_name='ProfileTest', version_name=PROFILE,
        game_directory=str(game), assets_root=prepared['assets'], assets_index_name=prepared['asset_index'],
        auth_uuid='00000000000000000000000000000001', auth_access_token='0', user_properties='{}', user_type='legacy')
    args = ['-Xmx2G', '--enable-native-access=ALL-UNNAMED', '--add-modules=jdk.incubator.vector',
            '-Xlog:class+load=info:file=class-load.log', prepared['logging_argument'],
            *expand(prepared['jvm'], substitutions), prepared['main_class'],
            *expand(prepared['game'], substitutions), '--width', '1280', '--height', '720']
    argfile = game / 'java.args'
    argfile.write_text('\n'.join('"' + a.replace('\\', '\\\\').replace('"', '\\"') + '"' for a in args), encoding='utf-8')
    record = dict(label=label, args=args, java=str(JDK / 'bin/java.exe'), game_dir=str(game),
        jar_sha256=sha(artifact), resource_pack_sha256=sha(pack), status='running', master_volume='0.0',
        started_utc=datetime.datetime.now(datetime.timezone.utc).isoformat())
    target.write_text(json.dumps(record, indent=2) + '\n')
    start = time.monotonic()
    with (game / 'launcher.log').open('w', encoding='utf-8') as output:
        process = subprocess.Popen([str(JDK / 'bin/java.exe'), '@' + str(argfile)], cwd=game, stdout=output, stderr=subprocess.STDOUT)
        try: record['exit_code'] = process.wait(timeout=900)
        except subprocess.TimeoutExpired:
            process.kill(); record['exit_code'] = process.wait(); record['timeout'] = True
    record.update(duration_s=time.monotonic() - start, ended_utc=datetime.datetime.now(datetime.timezone.utc).isoformat())
    record['status'] = 'completed' if record['exit_code'] == 0 else 'failed'
    text = (game / 'launcher.log').read_text(errors='replace')
    record['functional_errors'] = re.findall(r'^.*(?:OpenGL Error|GL ERROR|InvalidMixin|Unreported exception|EXCEPTION_ACCESS_VIOLATION).*$', text, re.M)
    (HERE / 'logs' / (label + '.log')).write_text('\n'.join(l.rstrip() for l in text.splitlines()) + '\n')
    screenshots = HERE / 'screenshots'; screenshots.mkdir(exist_ok=True)
    for p in (game / 'screenshots').glob('*.png'): shutil.copy2(p, screenshots / p.name)
    if (game / 'config/alacer.properties').exists():
        shutil.copy2(game / 'config/alacer.properties', HERE / 'lifecycle-final-config.properties')
    shutil.copy2(game / 'options.txt', HERE / 'lifecycle-final-options.txt')
    target.write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps({k:record.get(k) for k in ('status','exit_code','duration_s','functional_errors','timeout')}, indent=2))


if __name__ == '__main__': main()
