"""Serial native-GPU performance selection runner for terrain shader pipeline replacement."""
import copy
import csv
import datetime
import gzip
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import traceback
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
OLD = HERE.parent / '2026-10-23-modern-rendering'
JDK = Path('C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot')
ENV = {**os.environ, 'JAVA_HOME': str(JDK), 'PATH': str(JDK/'bin') + ';' + os.environ['PATH'], 'PYTHONDONTWRITEBYTECODE': '1'}
RECEIPTS = []
LAYERS = ['terrain_solid', 'terrain_cutout_mipped', 'terrain_cutout', 'terrain_translucent']


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def save(name, value):
    (HERE/name).write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def source_hashes():
    paths = list((ROOT/'src').rglob('*')) + [HERE/'gates.md', HERE/'protocol.md', Path(__file__).resolve()]
    return {p.relative_to(ROOT).as_posix(): digest(p) for p in sorted(paths) if p.is_file()}


def check(frozen):
    changed = [p for p, h in frozen.items() if not (ROOT/p).is_file() or digest(ROOT/p) != h]
    require(not changed, 'Frozen bytes changed: ' + repr(changed))


def check_idle():
    command = "Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^java(w)?\\.exe$' -and $_.CommandLine -match 'KnotClient|GradleWrapperMain.*runClient' } | Select-Object ProcessId,CommandLine | ConvertTo-Json -Compress"
    active = subprocess.check_output(['powershell.exe', '-NoProfile', '-Command', command], text=True).strip()
    require(not active, 'Another client/launcher is active: ' + active)


def validate_launch(data, label, arm, is_terrain):
    env = data['environment']
    ref = json.loads((OLD/'raw/20261023-133916-mg-ablation-control-vis_on-ecl_on-ent0/summary.json').read_text())
    fixed = ('java.version', 'java.runtime.version', 'java.vm.version', 'cpu', 'gl.vendor', 'gl.renderer', 'gl.version',
             'window', 'options.renderDistance', 'jvm.maxHeapMB', 'vboActive', 'options.vsync',
             'alacer.configSource', 'benchmark.generationRun', 'benchmark.screenshots')
    for key in fixed:
        require(env[key] == ref['environment'][key], f'{key}: unexpected {env[key]}')
    require(sorted(env['mods'].split()) == sorted(ref['environment']['mods'].split()), 'Mods list mismatch')
    require(env['benchmark.backendScenario'] == str(is_terrain).lower(), 'Backend scenario mismatch')
    require('StartFlightRecording' not in env['jvm.args'], 'Unexpected JFR args')
    phases = ['steady', 'pan', 'sort', 'fly'] if is_terrain else ['static', 'pan', 'fly']
    for phase in phases:
        p = data['phases'][phase]
        require(p['dropped_frames'] == 0, f'Dropped frames in {phase}: {p["dropped_frames"]}')


def launch_client(scene, index, arm, frozen):
    check_idle()
    check(frozen)
    is_terrain = (scene == 'terrain')
    is_candidate = (arm == 'B')
    label = f'shader-{scene}-{index}{arm.lower()}'
    log = HERE / 'logs' / f'{label}.log'

    args = [
        str(ROOT / 'gradlew.bat'),
        '--offline',
        '--console=plain',
        'runClient',
        '-Pwindow=1920x1080',
        '-Pxmx=2G',
        '-Palacer.benchmark=true',
        '-Palacer.benchmark.vbo=true',
        '-Palacer.benchmark.viewDistance=12',
        f'-Palacer.benchmark.label={label}',
        '-Palacer.render.fastChunkNeighbours=false',
        '-Palacer.render.directBufferUploads=false',
        f'-Palacer.render.terrainVaos={str(is_candidate).lower()}',
        '-Palacer.modernGl.forceFallback=false'
    ]
    if is_terrain:
        args.append('-Palacer.benchmark.backendScenario=true')

    receipt = {
        'label': label,
        'scene': scene,
        'index': index,
        'arm': arm,
        'command': args,
        'java_home': str(JDK),
        'started_utc': now(),
        'status': 'starting',
        'log': log.relative_to(ROOT).as_posix()
    }
    RECEIPTS.append(receipt)
    save('launches.json', RECEIPTS)
    print(f'START [{scene} {index}/8 {arm}] {label} {now()}', flush=True)

    with log.open('x', encoding='utf-8') as stream:
        proc = subprocess.Popen(args, cwd=ROOT, env=ENV, stdout=stream, stderr=subprocess.STDOUT)
        receipt['launcher_pid'] = proc.pid
        save('launches.json', RECEIPTS)
        try:
            code = proc.wait(timeout=240)
        except subprocess.TimeoutExpired:
            subprocess.run(['taskkill.exe', '/PID', str(proc.pid), '/T', '/F'], capture_output=True)
            receipt.update(status='timeout', ended_utc=now())
            save('launches.json', RECEIPTS)
            raise RuntimeError('Launch timed out: ' + label)

    receipt.update(exit=code, ended_utc=now(), log_sha256=digest(log), status='validating')
    save('launches.json', RECEIPTS)
    require(code == 0, f'Launch failed ({code}): {label}')
    text = log.read_text(encoding='utf-8', errors='replace')
    require(not any(x in text for x in ('Benchmark aborted', 'benchmark aborted', 'OpenGL Error', 'Mixin apply failed')),
            'Client error in log: ' + label)
    matches = list((ROOT/'run/alacer/benchmarks').glob('*-' + label + '-*/summary.json'))
    require(len(matches) == 1, 'Missing/ambiguous summary: ' + label)
    receipt['summary'] = matches[0].relative_to(ROOT).as_posix()
    summary_data = json.loads(matches[0].read_text())
    validate_launch(summary_data, label, arm, is_terrain)
    check_idle()
    check(frozen)
    receipt['status'] = 'valid'
    save('launches.json', RECEIPTS)
    print(f'END [{scene} {index}/8 {arm}] {label} {now()}', flush=True)
    return receipt


def archive():
    manifest = json.loads((HERE/'raw-manifest.json').read_text()) if (HERE/'raw-manifest.json').exists() else {}
    for receipt in RECEIPTS:
        if 'summary' not in receipt:
            continue
        directory = ROOT / receipt['summary']
        directory = directory.parent
        target = HERE/'raw'/directory.name
        target.mkdir(parents=True, exist_ok=True)
        for p in directory.iterdir():
            if p.suffix not in ('.csv', '.json'):
                continue
            original = p.read_bytes()
            manifest[p.relative_to(ROOT).as_posix()] = hashlib.sha256(original).hexdigest()
            dest = target/(p.name + '.gz' if p.suffix == '.csv' else p.name)
            content = gzip.compress(original, mtime=0) if p.suffix == '.csv' else original
            if dest.exists():
                require(dest.read_bytes() == content, 'Refusing archive overwrite: ' + str(dest))
            else:
                dest.write_bytes(content)
    save('raw-manifest.json', manifest)


def main():
    require(Path.cwd().resolve() == ROOT, 'Run from specified worktree root')
    check_idle()
    global RECEIPTS
    if (HERE/'launches.json').exists():
        RECEIPTS = json.loads((HERE/'launches.json').read_text())
        frozen = json.loads((HERE/'shader-freeze.json').read_text())
    else:
        frozen = source_hashes()
        save('shader-freeze.json', frozen)
        with zipfile.ZipFile(HERE/'shader-source.zip', 'w', zipfile.ZIP_DEFLATED) as z:
            for p in sorted((ROOT/'src').rglob('*')):
                if p.is_file():
                    z.write(p, p.relative_to(ROOT).as_posix())

        save('preflight.json', {
            'started_utc': now(),
            'java_home': str(JDK),
            'gradle_java': subprocess.check_output([str(JDK/'bin/java.exe'), '-version'], stderr=subprocess.STDOUT, text=True),
            'frozen_source_entries': len(frozen),
            'launch_budget': 16,
            'pattern': 'ABBABAAB per scene (4 balanced pairs: A-B, B-A, B-A, A-B)'
        })
    (HERE/'logs').mkdir(exist_ok=True)

    existing_labels = {r['label']: r for r in RECEIPTS}
    # Pattern: ABBABAAB = 4 pairs: Pair 1 (A->B), Pair 2 (B->A), Pair 3 (B->A), Pair 4 (A->B)
    pattern = 'ABBABAAB'
    for scene in ['default', 'terrain']:
        for i, arm in enumerate(pattern, 1):
            label = f'shader-{scene}-{i}{arm.lower()}'
            if label in existing_labels:
                print(f'ALREADY RECORDED {label}: {existing_labels[label].get("status")}', flush=True)
                continue
            launch_client(scene, i, arm, frozen)

    archive()
    check(frozen)
    print(f'ALL {len(RECEIPTS)} LAUNCHES COMPLETE', flush=True)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        if RECEIPTS:
            if RECEIPTS[-1]['status'] != 'valid':
                RECEIPTS[-1]['status'] = 'failed'
            save('launches.json', RECEIPTS)
            archive()
        save('failure.json', {'time_utc': now(), 'error': repr(error), 'traceback': traceback.format_exc()})
        raise
