"""Disposable loopback-only vanilla server with a retained file-based console queue."""
import datetime
import json
from pathlib import Path
import re
import subprocess
import time
from profile import HERE, ROOT, WORK, JDK, sha, fetch

SERVER = WORK / 'loopback-server'


def prepare():
    SERVER.mkdir(exist_ok=False)
    metadata = json.loads((WORK / 'installer-instance/versions/1.8.9-gen2/1.8.9-gen2.json').read_text())['downloads']['server']
    fetch(SERVER / 'server.jar', metadata['url'], {'sha1': metadata['sha1']},
          [Path.home() / '.gradle/caches/fabric-loom/1.8.9/minecraft-server.jar'])
    (SERVER / 'eula.txt').write_text('eula=true\n')
    (SERVER / 'server.properties').write_text('\n'.join([
        'server-ip=127.0.0.1', 'server-port=25566', 'online-mode=false', 'max-players=2',
        'level-name=world', 'level-seed=1789', 'gamemode=3', 'force-gamemode=true',
        'difficulty=0', 'spawn-protection=0', 'view-distance=8', 'spawn-animals=false',
        'spawn-monsters=false', 'enable-query=false', 'enable-rcon=false',
        'motd=Alacer loopback release check']) + '\n')
    (SERVER / 'commands').mkdir()
    print('Prepared loopback server, SHA-256 ' + sha(SERVER / 'server.jar'))


def run():
    target = HERE / 'server.json'
    if target.exists(): raise RuntimeError('Server already attempted')
    args = [str(JDK / 'bin/java.exe'), '-Xms256M', '-Xmx1G', '-jar', str(SERVER / 'server.jar'), 'nogui']
    record = dict(args=args, server_jar_sha256=sha(SERVER / 'server.jar'),
                  started_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(), commands=[])
    target.write_text(json.dumps(record, indent=2) + '\n')
    start = time.monotonic()
    processed = set()
    with (SERVER / 'console.log').open('w', encoding='utf-8') as output:
        process = subprocess.Popen(args, cwd=SERVER, stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT, text=True)
        record['pid'] = process.pid
        while process.poll() is None:
            for path in sorted((SERVER / 'commands').glob('*.json')):
                if path.name in processed: continue
                commands = json.loads(path.read_text())
                for command in commands:
                    if not re.fullmatch(r'(list|stop|save-all|gamemode 3 ProfileTest|tp ProfileTest -?\d+ \d+ -?\d+|time set \d+|weather (clear|rain)|setblock -?\d+ \d+ -?\d+ (minecraft:)?(stone|glowstone|air))', command):
                        raise RuntimeError('Unsupported test console command')
                    process.stdin.write(command + '\n'); process.stdin.flush()
                    record['commands'].append(dict(command=command, utc=datetime.datetime.now(datetime.timezone.utc).isoformat()))
                processed.add(path.name)
                target.write_text(json.dumps(record, indent=2) + '\n')
            if time.monotonic() - start > 1200:
                process.stdin.write('stop\n'); process.stdin.flush()
                record['watchdog_stop'] = True
                try: process.wait(timeout=30)
                except subprocess.TimeoutExpired: process.kill()
            time.sleep(1)
        record.update(exit_code=process.returncode, duration_s=time.monotonic() - start,
                      ended_utc=datetime.datetime.now(datetime.timezone.utc).isoformat())
    (HERE / 'logs/server.log').write_text('\n'.join(l.rstrip() for l in (SERVER / 'console.log').read_text(errors='replace').splitlines()) + '\n')
    target.write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps(record, indent=2))


if __name__ == '__main__':
    import sys
    prepare() if sys.argv[1] == 'prepare' else run()
