"""Production guard retest and loopback teleport diagnosis (see PROTOCOL.md)."""
import datetime
import importlib.util
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location('rc1_profile', HERE.parent / '2026-10-29-release-checks/profile.py')
rc1 = importlib.util.module_from_spec(_spec); _spec.loader.exec_module(rc1)
ROOT, WORK, INSTALL, JDK, PROFILE, sha, expand = rc1.ROOT, rc1.WORK, rc1.INSTALL, rc1.JDK, rc1.PROFILE, rc1.sha, rc1.expand
ARTIFACT = ROOT / 'build/libs/alacer-1.0.0+mc1.8.9.jar'
JAR_SHA = '588631a2fe3fe61edf9ee551ee42ce4f0affcc5c0895405fc5d9031c9271e261'
SERVER_JAR = WORK / 'loopback-server/server.jar'
PORT = '25567'
GUARD = {
    'rc2-guard-default': [],
    'rc2-guard-verify': ['-Dalacer.render.fastRandomDisplayTicks.verify=true', '-Dalacer.render.fastChunkNeighbours.verify=true'],
    'rc2-guard-modern-verify': ['-Dalacer.render.modernGl.verify=true'],
}
ERRORS = r'^.*(?:OpenGL Error|GL ERROR|InvalidMixin|Unreported exception|EXCEPTION_ACCESS_VIOLATION|Benchmark timed out|MixinApplyError|MixinTransformerError).*$'


def now(): return datetime.datetime.now(datetime.timezone.utc).isoformat()


def instance(label, alacer=True):
    target = HERE / 'launches' / (label + '.json')
    target.parent.mkdir(exist_ok=True)
    if target.exists(): raise RuntimeError('Attempt exists: ' + label)
    if sha(ARTIFACT) != JAR_SHA: raise RuntimeError('Artifact changed')
    prepared = json.loads((WORK / 'prepared-profile.json').read_text())
    for name, expected in prepared['profile_hashes'].items():
        if sha(INSTALL / name) != expected: raise RuntimeError('Installer metadata changed')
    game = WORK / label
    game.mkdir(exist_ok=False); (game / 'mods').mkdir(); (game / 'saves').mkdir()
    if alacer: shutil.copy2(ARTIFACT, game / 'mods' / ARTIFACT.name)
    for file in (ROOT / '.gradle/loom-cache/remapClasspath.txt').read_text().split(';'):
        if 'net.ornithemc.osl-gen2' in file: shutil.copy2(file, game / 'mods' / Path(file).name)
    options = (ROOT / 'run/options.txt').read_text()
    options = re.sub(r'(?m)^soundCategory_master:[^\r\n]*', 'soundCategory_master:0.0', options)
    (game / 'options.txt').write_text(options)
    subs = dict(natives_directory=prepared['native'], launcher_name='AlacerProfileTest', launcher_version='1',
        classpath=';'.join(prepared['classpath']), auth_player_name='ProfileTest', version_name=PROFILE,
        game_directory=str(game), assets_root=prepared['assets'], assets_index_name=prepared['asset_index'],
        auth_uuid='00000000000000000000000000000001', auth_access_token='0', user_properties='{}', user_type='legacy')
    return target, prepared, game, subs


def write_args(game, args):
    argfile = game / 'java.args'
    argfile.write_text('\n'.join('"' + a.replace('\\', '\\\\').replace('"', '\\"') + '"' for a in args), encoding='utf-8')
    return argfile


def finish(record, game, label):
    text = (game / 'launcher.log').read_text(errors='replace')
    (HERE / 'logs').mkdir(exist_ok=True)
    (HERE / 'logs' / (label + '.log')).write_text('\n'.join(l.rstrip() for l in text.splitlines()) + '\n')
    record['functional_errors'] = re.findall(ERRORS, text, re.M)
    record['inspect_failure'] = 'could not inspect mixin compatibility' in text
    record['fallback_lines'] = re.findall(r'Modern rendering fallback[^\]<\r\n]*', text)
    m = re.search(r'Ambient tick verification final: (\d+) ticks, (\d+) blocks, (\d+) mismatches', text)
    if m: record['ambient'] = dict(zip(('ticks', 'blocks', 'mismatches'), map(int, m.groups())))
    record['display_tick_mismatch_lines'] = len(re.findall('FastRandomDisplayTicks mismatch', text))
    classes = game / 'class-load.log'
    if classes.exists():
        record['class_origins'] = [l for l in classes.read_text(errors='replace').splitlines() if 'net.adiont.alacer.Alacer source:' in l]
    return text


def guard(label):
    target, prepared, game, subs = instance(label)
    shutil.copytree(ROOT / 'run/saves/alacer-benchmark', game / 'saves/alacer-benchmark')
    props = ['-Dalacer.benchmark=true', '-Dalacer.benchmark.label=' + label, '-Dalacer.benchmark.vbo=true',
             '-Dalacer.benchmark.entities=200', '-Dalacer.benchmark.screenshots=true', '-Dalacer.profiler.hud=false',
             '-Dalacer.benchmark.timeout=180', '-Dalacer.benchmark.timeScale=0.35', *GUARD[label]]
    args = ['-Xmx2G', '--enable-native-access=ALL-UNNAMED', '--add-modules=jdk.incubator.vector',
            '-Xlog:class+load=info:file=class-load.log', prepared['logging_argument'], *props,
            *expand(prepared['jvm'], subs), prepared['main_class'], *expand(prepared['game'], subs), '--width', '1280', '--height', '720']
    argfile = write_args(game, args)
    record = dict(label=label, args=args, java=str(JDK / 'bin/java.exe'), game_dir=str(game), status='running',
        source=subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True, cwd=ROOT).strip(), jar_sha256=sha(ARTIFACT),
        profile_hashes=prepared['profile_hashes'], master_volume='0.0', started_utc=now())
    target.write_text(json.dumps(record, indent=2) + '\n')
    start = time.monotonic()
    with (game / 'launcher.log').open('w', encoding='utf-8') as out:
        p = subprocess.Popen([str(JDK / 'bin/java.exe'), '@' + str(argfile)], cwd=game, stdout=out, stderr=subprocess.STDOUT)
        try: record['exit_code'] = p.wait(timeout=240)
        except subprocess.TimeoutExpired: p.kill(); record['exit_code'] = p.wait(); record['timeout'] = True
    record.update(duration_s=time.monotonic() - start, ended_utc=now())
    record['status'] = 'completed' if record['exit_code'] == 0 else 'failed'
    finish(record, game, label)
    summaries = list((game / 'alacer/benchmarks').glob('*/summary.json'))
    failures = []
    if len(summaries) == 1:
        folder = summaries[0].parent; summary = json.loads(summaries[0].read_text())
        record['phases'] = {n: {k: ph[k] for k in ('frames', 'dropped_frames', 'recorded_wall_seconds', 'entities')} for n, ph in summary['phases'].items()}
        env = summary['environment']
        record['environment_checks'] = {k: env.get(k) for k in env if k.startswith('modernGl.') or k.startswith('chunkNeighbours.')}
        dest = HERE / 'runs' / folder.name; dest.mkdir(parents=True, exist_ok=True)
        record['raw_files_sha256'] = {f.name: sha(f) for f in folder.iterdir() if f.is_file()}
        shutil.copy2(summaries[0], dest / 'summary.json')
        for png in (game / 'screenshots').glob('alacer-' + label + '-*.png'): shutil.copy2(png, dest / png.name)
        if any(ph['dropped_frames'] for ph in record['phases'].values()): failures.append('dropped frames')
    else: failures.append('summary missing')
    if record['status'] != 'completed': failures.append('exit')
    if record['functional_errors']: failures.append('functional errors')
    if record['inspect_failure']: failures.append('compatibility inspection failed')
    if not record.get('class_origins') or not all(ARTIFACT.name in l for l in record['class_origins']): failures.append('class origin')
    amb = record.get('ambient') or {}
    if label != 'rc2-guard-modern-verify' and not amb.get('ticks'): failures.append('ambient fast path inactive')
    if label == 'rc2-guard-verify':
        if amb.get('mismatches') or record['display_tick_mismatch_lines']: failures.append('ambient mismatches')
        nb = re.match(r'(\d+) compared, (\d+) mismatches', str(record.get('environment_checks', {}).get('chunkNeighbours.verification', '')))
        if not nb or int(nb.group(1)) == 0 or int(nb.group(2)) != 0: failures.append('chunk neighbour verification')
    if label == 'rc2-guard-modern-verify':
        e = record.get('environment_checks', {})
        if e.get('modernGl.forcedFallback') != 'false': failures.append('forced fallback')
        if int(e.get('modernGl.namedUploads', 0)) == 0 or int(e.get('modernGl.vaoChecks', 0)) == 0: record['modern_not_exercised'] = True; failures.append('modern GL not exercised')
        if int(e.get('modernGl.uploadFailures', 1)) or int(e.get('modernGl.vaoFailures', 1)): failures.append('modern GL failures')
    record['gate_failures'] = failures
    record['gate'] = 'pass' if not failures else 'fail'
    target.write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps({k: record.get(k) for k in ('label', 'gate', 'gate_failures', 'exit_code', 'duration_s', 'phases', 'ambient', 'environment_checks', 'fallback_lines', 'functional_errors')}, indent=2), flush=True)
    if failures: raise SystemExit('STOP: ' + label + ' failed ' + ', '.join(failures))


KEYS = r'''
Add-Type @"
using System; using System.Runtime.InteropServices;
public class K {
 [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
 [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
 [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint flags, UIntPtr extra);
}
"@
$p = Get-Process -Id __PID__
[K]::keybd_event(0x12, 0x38, 0, [UIntPtr]::Zero); [K]::keybd_event(0x12, 0x38, 2, [UIntPtr]::Zero)
[K]::ShowWindow($p.MainWindowHandle, 9) | Out-Null
$ok = [K]::SetForegroundWindow($p.MainWindowHandle)
Start-Sleep -Milliseconds 800
foreach ($k in @(@(0x72, 0x3D), @(0x71, 0x3C))) {
 [K]::keybd_event($k[0], $k[1], 0, [UIntPtr]::Zero); Start-Sleep -Milliseconds 120
 [K]::keybd_event($k[0], $k[1], 2, [UIntPtr]::Zero); Start-Sleep -Milliseconds 1500
}
"foreground=$ok"
'''


def multiplayer(label):
    alacer = label == 'rc2-mp-alacer'
    target, prepared, game, subs = instance(label, alacer)
    server = WORK / (label + '-server'); server.mkdir(exist_ok=False); (server / 'eula.txt').write_text('eula=true\n')
    shutil.copy2(SERVER_JAR, server / 'server.jar')
    (server / 'server.properties').write_text('\n'.join([
        'server-ip=127.0.0.1', 'server-port=' + PORT, 'online-mode=false', 'max-players=2', 'level-name=world',
        'level-seed=1789', 'gamemode=3', 'force-gamemode=true', 'difficulty=0', 'spawn-protection=0', 'view-distance=8',
        'spawn-animals=false', 'spawn-monsters=false', 'enable-query=false', 'enable-rcon=false', 'motd=Alacer teleport diagnosis']) + '\n')
    sargs = [str(JDK / 'bin/java.exe'), '-Xms256M', '-Xmx1G', '-jar', str(server / 'server.jar'), 'nogui']
    args = ['-Xmx2G', '--enable-native-access=ALL-UNNAMED', '--add-modules=jdk.incubator.vector',
            '-Xlog:class+load=info:file=class-load.log', prepared['logging_argument'],
            *expand(prepared['jvm'], subs), prepared['main_class'], *expand(prepared['game'], subs),
            '--width', '1280', '--height', '720', '--server', '127.0.0.1', '--port', PORT]
    argfile = write_args(game, args)
    record = dict(label=label, alacer=alacer, args=args, server_args=sargs, server_jar_sha256=sha(server / 'server.jar'),
        java=str(JDK / 'bin/java.exe'), game_dir=str(game), mods=sorted(p.name for p in (game / 'mods').iterdir()),
        source=subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True, cwd=ROOT).strip(),
        jar_sha256=sha(ARTIFACT) if alacer else None, profile_hashes=prepared['profile_hashes'], master_volume='0.0',
        started_utc=now(), commands=[], events=[])
    target.write_text(json.dumps(record, indent=2) + '\n')
    console = (server / 'console.log').open('w', encoding='utf-8')
    srv = subprocess.Popen(sargs, cwd=server, stdin=subprocess.PIPE, stdout=console, stderr=subprocess.STDOUT, text=True)
    def slog(): return (server / 'console.log').read_text(errors='replace')
    def wait_for(pattern, limit):
        end = time.monotonic() + limit
        while time.monotonic() < end:
            if re.search(pattern, slog()): return True
            if srv.poll() is not None: return False
            time.sleep(0.5)
        return False
    def send(*commands):
        for c in commands:
            srv.stdin.write(c + '\n'); srv.stdin.flush(); record['commands'].append(dict(command=c, utc=now()))
    def event(name, **kw): record['events'].append(dict(event=name, utc=now(), **kw)); target.write_text(json.dumps(record, indent=2) + '\n')
    client = None
    try:
        if not wait_for(r'Done \(', 120): raise RuntimeError('server did not start')
        event('server_ready')
        out = (game / 'launcher.log').open('w', encoding='utf-8')
        client = subprocess.Popen([str(JDK / 'bin/java.exe'), '@' + str(argfile)], cwd=game, stdout=out, stderr=subprocess.STDOUT)
        record['client_pid'] = client.pid
        if not wait_for(r'ProfileTest joined the game', 150): raise RuntimeError('client did not join')
        event('joined'); time.sleep(20)
        send('gamemode 3 ProfileTest', 'tp ProfileTest 512 80 512', 'setblock 512 80 518 minecraft:glowstone', 'time set 18000')
        time.sleep(40)
        send('tp ProfileTest -190 80 280', 'setblock -190 80 286 minecraft:glowstone', 'time set 6000')
        time.sleep(55)
        keys = subprocess.run(['powershell', '-NoProfile', '-Command', KEYS.replace('__PID__', str(client.pid))], capture_output=True, text=True, timeout=60)
        event('keys', stdout=keys.stdout.strip(), stderr=keys.stderr.strip()[-400:])
        time.sleep(3)
        close = subprocess.run(['powershell', '-NoProfile', '-Command', f'(Get-Process -Id {client.pid}).CloseMainWindow()'], capture_output=True, text=True, timeout=30)
        event('close_requested', stdout=close.stdout.strip())
        try: record['client_exit_code'] = client.wait(timeout=60)
        except subprocess.TimeoutExpired: client.kill(); record['client_exit_code'] = client.wait(); record['client_killed'] = True
        event('client_exited')
    finally:
        if client and client.poll() is None: client.kill(); record['client_killed'] = True
        if srv.poll() is None:
            send('save-all', 'stop')
            try: record['server_exit_code'] = srv.wait(timeout=60)
            except subprocess.TimeoutExpired: srv.kill(); record['server_exit_code'] = srv.wait(); record['server_killed'] = True
        console.close()
        (HERE / 'logs').mkdir(exist_ok=True)
        (HERE / 'logs' / (label + '-server.log')).write_text('\n'.join(l.rstrip() for l in slog().splitlines()) + '\n')
        if (game / 'launcher.log').exists(): finish(record, game, label)
        shots = HERE / 'screenshots' / label; shots.mkdir(parents=True, exist_ok=True)
        record['screenshots'] = []
        for png in (game / 'screenshots').glob('*.png'):
            shutil.copy2(png, shots / png.name); record['screenshots'].append(dict(name=png.name, sha256=sha(png)))
        record['server_lines'] = re.findall(r'^\[[^\]]*\] \[Server thread/INFO\]: (?:ProfileTest|Teleported|Set the time|Block placed|Cannot place).*$', slog(), re.M)
        record['ended_utc'] = now()
        target.write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps({k: record.get(k) for k in ('label', 'client_exit_code', 'server_exit_code', 'client_killed', 'server_killed', 'functional_errors', 'inspect_failure', 'screenshots', 'server_lines', 'events')}, indent=2), flush=True)


def ui(label):
    import zipfile
    target, prepared, game, subs = instance(label)
    for name in ('release-world-A', 'release-world-B'):
        shutil.copytree(ROOT / 'run/saves/alacer-benchmark', game / 'saves' / name)
    (game / 'resourcepacks').mkdir()
    pack = game / 'resourcepacks/release-language-check.zip'
    with zipfile.ZipFile(pack, 'w') as archive:
        archive.writestr('pack.mcmeta', json.dumps({'pack': {'pack_format': 1, 'description': 'Alacer lifecycle language check'}}))
        archive.writestr('assets/minecraft/lang/en_US.lang', 'menu.singleplayer=Release Check Worlds\nmenu.multiplayer=Release Check Servers\n')
    args = ['-Xmx2G', '--enable-native-access=ALL-UNNAMED', '--add-modules=jdk.incubator.vector',
            '-Xlog:class+load=info:file=class-load.log', prepared['logging_argument'],
            *expand(prepared['jvm'], subs), prepared['main_class'], *expand(prepared['game'], subs), '--width', '1280', '--height', '720']
    argfile = write_args(game, args)
    record = dict(label=label, args=args, java=str(JDK / 'bin/java.exe'), game_dir=str(game), status='running',
        source=subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True, cwd=ROOT).strip(), jar_sha256=sha(ARTIFACT),
        resource_pack_sha256=sha(pack), profile_hashes=prepared['profile_hashes'], master_volume='0.0', started_utc=now())
    target.write_text(json.dumps(record, indent=2) + '\n')
    start = time.monotonic()
    with (game / 'launcher.log').open('w', encoding='utf-8') as out:
        p = subprocess.Popen([str(JDK / 'bin/java.exe'), '@' + str(argfile)], cwd=game, stdout=out, stderr=subprocess.STDOUT)
        record['pid'] = p.pid; target.write_text(json.dumps(record, indent=2) + '\n')
        try: record['exit_code'] = p.wait(timeout=900)
        except subprocess.TimeoutExpired: p.kill(); record['exit_code'] = p.wait(); record['timeout'] = True
    record.update(duration_s=time.monotonic() - start, ended_utc=now())
    record['status'] = 'completed' if record['exit_code'] == 0 else 'failed'
    text = finish(record, game, label)
    record['resource_reloads'] = len(re.findall(r'Reloading ResourceManager', text))
    record['reload_lines'] = re.findall(r'Reloading ResourceManager[^\]<\r\n]*', text)
    record['stopping'] = 'Stopping!' in text
    shots = HERE / 'screenshots' / label; shots.mkdir(parents=True, exist_ok=True)
    for png in (game / 'screenshots').glob('*.png'): shutil.copy2(png, shots / png.name)
    for name in ('options.txt', 'config/alacer.properties'):
        if (game / name).exists(): shutil.copy2(game / name, HERE / (label + '-final-' + Path(name).name))
    target.write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps({k: record.get(k) for k in ('label', 'status', 'exit_code', 'duration_s', 'functional_errors', 'inspect_failure', 'fallback_lines', 'ambient', 'resource_reloads', 'reload_lines', 'stopping', 'timeout')}, indent=2), flush=True)


if __name__ == '__main__':
    name = sys.argv[1]
    if name in GUARD: guard(name)
    elif name.startswith('rc2-ui'): ui(name)
    else: multiplayer(name)
