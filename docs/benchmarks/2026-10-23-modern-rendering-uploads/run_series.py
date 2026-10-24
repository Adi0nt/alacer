"""Serial native-GPU performance selection runner for OpenGL 4.5 direct buffer uploads."""
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
    label = f'uploads-{scene}-{index}{arm.lower()}'
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
        '-Palacer.render.terrainShader=false',
        '-Palacer.render.terrainVaos=false',
        f'-Palacer.render.directBufferUploads={str(is_candidate).lower()}',
        '-Palacer.modernGl.forceFallback=false'
    ]
    if is_terrain:
        args.append('-Palacer.benchmark.backendScenario=true')

    started = now()
    out = subprocess.run(args, cwd=ROOT, env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    finished = now()
    log.parent.mkdir(parents=True, exist_ok=True)
    log.write_text(out.stdout, encoding='utf-8')
    require(out.returncode == 0, f'Launch {label} failed (rc={out.returncode}):\n' + out.stdout[-2000:])

    bench_dirs = sorted((ROOT / 'run/alacer/benchmarks').glob(f'*-{label}-*'))
    require(len(bench_dirs) == 1, f'Expected exactly 1 result dir for {label}, found {len(bench_dirs)}')
    bench_dir = bench_dirs[0]

    summary_file = bench_dir / 'summary.json'
    require(summary_file.is_file(), f'Missing summary.json for {label}')
    summary_data = json.loads(summary_file.read_text())
    validate_launch(summary_data, label, arm, is_terrain)

    target_raw_dir = HERE / 'raw' / bench_dir.name
    target_raw_dir.mkdir(parents=True, exist_ok=True)
    for src in bench_dir.iterdir():
        if src.is_file():
            dst = target_raw_dir / src.name
            if src.suffix == '.csv':
                compressed = target_raw_dir / (src.name + '.gz')
                with open(src, 'rb') as f_in, gzip.open(compressed, 'wb') as f_out:
                    f_out.write(f_in.read())
            else:
                dst.write_bytes(src.read_bytes())

    receipt = {
        'label': label,
        'scene': scene,
        'index': index,
        'arm': arm,
        'started': started,
        'finished': finished,
        'exit_code': out.returncode,
        'log_sha256': digest(log),
        'relative_raw_dir': bench_dir.name,
        'command': args
    }
    RECEIPTS.append(receipt)
    save('launches.json', RECEIPTS)
    print(f'[{now()}] Completed {label} (exit={out.returncode})')


def zip_source():
    target = HERE / 'upload-source.zip'
    with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_DEFLATED) as zf:
        for p in sorted((ROOT/'src').rglob('*')):
            if p.is_file():
                zf.write(p, p.relative_to(ROOT).as_posix())
        zf.write(HERE/'gates.md', 'gates.md')
        zf.write(HERE/'protocol.md', 'protocol.md')
        zf.write(Path(__file__).resolve(), 'run_series.py')
    print(f'Wrote {target} ({target.stat().st_size} bytes)')


def build_manifest():
    manifest = {}
    for p in sorted((HERE / 'raw').rglob('*')):
        if p.is_file():
            manifest[p.relative_to(HERE).as_posix()] = digest(p)
    for p in sorted((HERE / 'logs').glob('*.log')):
        if p.is_file():
            manifest[p.relative_to(HERE).as_posix()] = digest(p)
    save('raw-manifest.json', manifest)
    print(f'Manifest written: {len(manifest)} files')


def main():
    check_idle()
    frozen = source_hashes()
    save('upload-freeze.json', frozen)
    zip_source()

    plan = [
        # Default route (camera movement, 8 runs, ABBABAAB)
        ('default', 1, 'A'),
        ('default', 2, 'B'),
        ('default', 3, 'B'),
        ('default', 4, 'A'),
        ('default', 5, 'B'),
        ('default', 6, 'A'),
        ('default', 7, 'A'),
        ('default', 8, 'B'),
        # Terrain scenario (chunk churn, 8 runs, ABBABAAB)
        ('terrain', 1, 'A'),
        ('terrain', 2, 'B'),
        ('terrain', 3, 'B'),
        ('terrain', 4, 'A'),
        ('terrain', 5, 'B'),
        ('terrain', 6, 'A'),
        ('terrain', 7, 'A'),
        ('terrain', 8, 'B'),
    ]

    for scene, idx, arm in plan:
        launch_client(scene, idx, arm, frozen)

    build_manifest()
    print('Executing evaluate.py...')
    import evaluate
    evaluate.evaluate_archive(HERE)
    print('Series execution complete.')


if __name__ == '__main__':
    main()
