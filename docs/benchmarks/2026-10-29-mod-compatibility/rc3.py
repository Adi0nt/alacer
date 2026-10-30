"""Mod compatibility smoke: rc2 guard harness plus third-party mods in mods/ (see PROTOCOL.md)."""
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import shutil
import sys

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location('rc2', HERE.parent / '2026-10-29-release-checks-2/rc2.py')
rc2 = importlib.util.module_from_spec(_spec); _spec.loader.exec_module(rc2)
rc2.HERE = HERE  # archive launches/logs/runs here
MODS = rc2.WORK / 'third-party-mods'
OSL_CONFIG = Path.home() / '.gradle/caches/modules-2/files-2.1/net.ornithemc.osl-gen2/config/0.6.2+mc14w27a-mc15w39c/66c9c45e50d9aa3340f23b3d5c0ef51c9dfc5c01/config-0.6.2+mc14w27a-mc15w39c.jar'
EXPECTED = {
    'lomka-0.7.0-ornithe+1.8.9.jar': 'aa610f5e1efd319f3e21a45b28f5f77fdc11c03d1664250e9eb53e6cfff641c7',
    'LegacySkinLayers-1.0.1.jar': 'ca2b68e500567ec837c101a318dfdd08c5fb8a0f9a4fa13eafac63378763e3f6',
    'Ksyxis-1.4.6.jar': 'e044b1b25227ceb49e3fa592543af602034a0a6a85b57edfeb41668f3bedf111',
    'asynclogger-2.2.3+1.8.9-ornithe.jar': '227847e9446f16fa1757070b8059f43c9a4e7106eeedad6d6d105e838dbb46b6',
}
VERIFY = ['fastRandomDisplayTicks', 'fastEntityCulling', 'fastEntityRendererLookup', 'fastEntityLighting',
          'fastEntityShadows', 'fastRayTrace', 'skipZeroTranslates', 'batchModelLists']
rc2.GUARD['rc3-mods-default'] = rc2.GUARD['rc3b-mods-default'] = []
rc2.GUARD['rc3-mods-verify'] = rc2.GUARD['rc3b-mods-verify'] = ['-Dalacer.render.%s.verify=true' % k for k in VERIFY]
MOD_IDS = ['alacer', 'lomka', 'legacy-skin-layers', 'ksyxis', 'asynclogger', 'osl-config']
# Amendment 1: rc3b drops Legacy Skin Layers (undeclared Kotlin runtime) and osl-config.
DROPPED = {'LegacySkinLayers-1.0.1.jar', OSL_CONFIG.name}
_instance = rc2.instance


def instance(label, alacer=True):
    result = _instance(label, alacer)
    mods = result[2] / 'mods'
    for path in list(MODS.glob('*/*.jar')) + [OSL_CONFIG]:
        if label.startswith('rc3b') and path.name in DROPPED: continue
        if path.name in EXPECTED and hashlib.sha256(path.read_bytes()).hexdigest() != EXPECTED[path.name]:
            raise RuntimeError('Third-party mod changed: ' + path.name)
        shutil.copy2(path, mods / path.name)
    return result


rc2.instance = instance


def check(label):
    target = HERE / 'launches' / (label + '.json')
    record = json.loads(target.read_text())
    text = (HERE / 'logs' / (label + '.log')).read_text(errors='replace')
    loading = re.search(r'Loading \d+ mods:(.*?)(?:\]\]>|\n\S)', text, re.S)
    listed = loading.group(1) if loading else ''
    record['mods_dir'] = sorted(p.name for p in (Path(record['game_dir']) / 'mods').iterdir())
    ids = [m for m in MOD_IDS if not (label.startswith('rc3b') and m in ('legacy-skin-layers', 'osl-config'))]
    record['mods_loaded'] = {m: bool(re.search(r'-\s+' + re.escape(m) + r'\s', listed)) for m in ids}
    record['foreign_modified'] = re.findall(r'Modern rendering fallback: \S+ is modified by \S+', text)
    record['mixin_warnings'] = re.findall(r'^.*(?:Mixin|mixin).*(?:conflict|failed|Error|error|could not|Cannot).*$', text, re.M)[:20]
    finals = dict(re.findall(r'(\w[\w ()-]*?) (?:verification|queries|cache) final: ([^\]<\r\n]*)', text))
    record['verifier_finals'] = {k: v for k, v in finals.items()}
    failures = list(record.get('gate_failures', []))
    if not all(record['mods_loaded'].values()): failures.append('mods not all loaded')
    if record['mixin_warnings']: failures.append('mixin warnings')
    if label in ('rc3-mods-verify', 'rc3b-mods-verify'):
        lines = re.findall(r'(Ambient tick verification final: [^\]<\r\n]*|Entity culling verification final: [^\]<\r\n]*|Entity renderer cache final: [^\]<\r\n]*|Entity lighting queries final: [^\]<\r\n]*|Entity shadow queries final: [^\]<\r\n]*|Ray trace verification final: [^\]<\r\n]*|Model zero-translation verification final: [^\]<\r\n]*|Model list batch final: [^\]<\r\n]*)', text)
        record['verifier_lines'] = lines
        for line in lines:
            mism = re.findall(r'(\d+) (?:mismatches|mismatched)', line)
            if any(int(m) for m in mism): failures.append('mismatch: ' + line[:60])
    record['gate_failures'] = failures
    record['gate'] = 'pass' if not failures else 'fail'
    target.write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps({k: record.get(k) for k in ('label', 'gate', 'gate_failures', 'mods_loaded', 'foreign_modified', 'mixin_warnings', 'ambient', 'verifier_lines', 'phases')}, indent=2))
    if failures: raise SystemExit('STOP: ' + label)


def control(label):
    """Same five extra mods, no Alacer, normal launch; record whether it reaches the title screen."""
    import subprocess, time
    target, prepared, game, subs = rc2.instance(label, alacer=False)
    args = ['-Xmx2G', '--enable-native-access=ALL-UNNAMED', '--add-modules=jdk.incubator.vector',
            '-Xlog:class+load=info:file=class-load.log', prepared['logging_argument'],
            *rc2.expand(prepared['jvm'], subs), prepared['main_class'], *rc2.expand(prepared['game'], subs), '--width', '1280', '--height', '720']
    argfile = rc2.write_args(game, args)
    record = dict(label=label, alacer=False, args=args, game_dir=str(game), mods=sorted(p.name for p in (game / 'mods').iterdir()),
                  master_volume='0.0', started_utc=rc2.now())
    target.write_text(json.dumps(record, indent=2) + '\n')
    with (game / 'launcher.log').open('w', encoding='utf-8') as out:
        p = subprocess.Popen([str(rc2.JDK / 'bin/java.exe'), '@' + str(argfile)], cwd=game, stdout=out, stderr=subprocess.STDOUT)
        try: record['exit_code'] = p.wait(timeout=90); record['exited_by_itself'] = True
        except subprocess.TimeoutExpired:
            record['exited_by_itself'] = False
            keys = subprocess.run(['powershell', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(HERE.parent / '2026-10-29-release-checks-2/ui.ps1'), str(p.pid), 'shot'], capture_output=True, text=True, timeout=60)
            record['keys'] = keys.stdout.strip(); time.sleep(3); p.kill(); record['exit_code'] = p.wait(); record['killed_after_title_check'] = True
    text = (game / 'launcher.log').read_text(errors='replace')
    (HERE / 'logs').mkdir(exist_ok=True)
    (HERE / 'logs' / (label + '.log')).write_text('\n'.join(l.rstrip() for l in text.splitlines()) + '\n')
    record['crash_lines'] = re.findall(r'(?:Could not execute entrypoint[^\r\n]*|Caused by: [^\r\n]*)', text)[:8]
    shots = HERE / 'screenshots' / label; shots.mkdir(parents=True, exist_ok=True)
    for png in (game / 'screenshots').glob('*.png'): shutil.copy2(png, shots / png.name)
    record['ended_utc'] = rc2.now()
    target.write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps({k: record.get(k) for k in ('label', 'exit_code', 'exited_by_itself', 'crash_lines', 'mods')}, indent=2))


if __name__ == '__main__':
    label = sys.argv[1]
    if label.endswith('-control'): control(label); sys.exit()
    try: rc2.guard(label)
    except SystemExit as e: print(e)
    check(label)
