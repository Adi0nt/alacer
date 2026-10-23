"""Capped serial native-GPU attribution. Immutable attempt; no retries or fixes."""
import datetime
import gzip
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import traceback

from analyze import compare

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
OLD = HERE.parent / '2026-10-23-modern-rendering'
JDK = Path('C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot')
ENV = {**os.environ, 'JAVA_HOME': str(JDK), 'PATH': str(JDK/'bin') + ';' + os.environ['PATH'], 'PYTHONDONTWRITEBYTECODE': '1'}
HEAD = '95cceb6b1e7a513cf94ed8a206abc49d04813a8f'
RECEIPTS = []
ATTEMPT = 'r1'


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def save(name, value):
    (HERE/name).write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def inherited():
    result = {}
    for name in ('performance-freeze.json', 'selection-freeze.json'):
        for p, h in json.loads((OLD/name).read_text()).items():
            require(p not in result or result[p] == h, 'Conflicting original manifest: ' + p)
            result[p] = h
    return result


def check(frozen):
    changed = [p for p, h in frozen.items() if not (ROOT/p).is_file() or digest(ROOT/p) != h]
    require(not changed, 'Frozen bytes changed: ' + repr(changed))


def check_idle():
    command = "Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^java(w)?\\.exe$' -and $_.CommandLine -match 'KnotClient|GradleWrapperMain.*runClient' } | Select-Object ProcessId,CommandLine | ConvertTo-Json -Compress"
    active = subprocess.check_output(['powershell.exe', '-NoProfile', '-Command', command], text=True).strip()
    require(not active, 'Another client/launcher is active: ' + active)


def validate(data, arm):
    env = data['environment']
    ref = json.loads((OLD/'raw/20261023-133916-mg-ablation-control-vis_on-ecl_on-ent0/summary.json').read_text())
    fixed = ('java.version', 'java.runtime.version', 'java.vm.version', 'cpu', 'gl.vendor', 'gl.renderer', 'gl.version',
             'window', 'options.renderDistance', 'jvm.maxHeapMB', 'vboActive', 'options.vsync', 'mods',
             'alacer.configSource', 'benchmark.generationRun', 'benchmark.backendScenario', 'benchmark.screenshots')
    for key in fixed:
        require(env[key] == ref['environment'][key], f'{key}: unexpected {env[key]}')
    require('StartFlightRecording' not in env['jvm.args'], 'Unexpected JFR')
    require(env['modernGl.forcedFallback'] == str(arm == 'F').lower(), 'Wrong fallback state')
    require((int(env['modernGl.vaoBinds']) > 0) == (arm == 'V'), 'Unexpected VAO activity')
    for key in ('namedCreates', 'namedUploads', 'vaoChecks', 'vaoFailures', 'uploadChecks', 'uploadFailures', 'contextDrops'):
        require(env['modernGl.' + key] == '0', 'Unexpected counter: ' + key)
    for phase in ('static', 'pan', 'fly'):
        p = data['phases'][phase]
        require(p['dropped_frames'] == 0 and p['alloc_mean_bytes'] >= 0 and p['client_thread_cpu_ms_per_frame'] > 0, 'Invalid phase metrics')
        wanted = dict(ref['phases'][phase]['config'])
        wanted['render.terrainVaos'] = str(arm != 'A').lower()
        require(p['config'] == wanted, 'Phase config changed: ' + phase)


def launch(stage, index, arm, frozen):
    check_idle()
    check(frozen)
    label = f'mg-follow-{ATTEMPT}-s{stage}-{index}{arm.lower()}'
    log = HERE/'logs'/(label + '.log')
    require(not log.exists(), 'Attempt log already exists: ' + label)
    require(not list((ROOT/'run/alacer/benchmarks').glob('*-' + label + '-*')), 'Attempt raw path already exists')
    args = [str(ROOT/'gradlew.bat'), '--offline', '--console=plain', 'runClient', '-Pwindow=1920x1080', '-Pxmx=2G',
            '-Palacer.benchmark=true', '-Palacer.benchmark.vbo=true', '-Palacer.benchmark.viewDistance=12',
            '-Palacer.benchmark.label=' + label, '-Palacer.render.fastChunkNeighbours=false',
            '-Palacer.render.directBufferUploads=false', '-Palacer.render.terrainVaos=' + str(arm != 'A').lower(),
            '-Palacer.modernGl.forceFallback=' + str(arm == 'F').lower()]
    receipt = {'label': label, 'stage': stage, 'index': index, 'arm': arm, 'command': args,
               'java_home': str(JDK), 'started_utc': now(), 'status': 'running', 'log': log.relative_to(ROOT).as_posix()}
    RECEIPTS.append(receipt)
    save('launches.json', RECEIPTS)
    print('START', label, now(), flush=True)
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
    require(code == 0, 'Launch failed: ' + label)
    text = log.read_text(encoding='utf-8', errors='replace')
    require(not any(x in text for x in ('Benchmark aborted', 'benchmark aborted', 'OpenGL Error', 'Mixin apply failed')), 'Client error: ' + label)
    matches = list((ROOT/'run/alacer/benchmarks').glob('*-' + label + '-*/summary.json'))
    require(len(matches) == 1, 'Missing/ambiguous summary: ' + label)
    receipt['summary'] = matches[0].relative_to(ROOT).as_posix()
    validate(json.loads(matches[0].read_text()), arm)
    check_idle()
    check(frozen)
    receipt['status'] = 'valid'
    save('launches.json', RECEIPTS)
    print('END', label, now(), flush=True)
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
    require(Path.cwd().resolve() == ROOT, 'Run from the specified worktree root')
    require(not (HERE/'freeze.json').exists(), 'Attempt r1 already exists; never overwrite it')
    require(subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip() == HEAD, 'Wrong HEAD')
    check_idle()
    frozen = inherited()
    check(frozen)
    for p in list(OLD.rglob('*')) + [HERE/'protocol.md', HERE/'run_diagnostic.py', HERE/'analyze.py', HERE/'.gitattributes']:
        if p.is_file():
            frozen[p.relative_to(ROOT).as_posix()] = digest(p)
    save('freeze.json', frozen)
    save('preflight.json', {'started_utc': now(), 'source_head': HEAD, 'branch': subprocess.check_output(['git', 'branch', '--show-current'], text=True).strip(),
         'java_home': str(JDK), 'gradle_java': subprocess.check_output([str(JDK/'bin/java.exe'), '-version'], stderr=subprocess.STDOUT, text=True),
         'frozen_entries': len(frozen), 'max_launches': 8, 'automatic_retries': 0})
    (HERE/'logs').mkdir(exist_ok=True)
    save('state.json', {'status': 'running', 'stage': 1, 'started_utc': now()})
    first = [launch(1, i, arm, frozen) for i, arm in enumerate('AVVA', 1)]
    result = compare(first, 'A')
    save('stage1-evaluation.json', result)
    archive()
    print('STAGE1 large_pan_gap_reproduced=' + str(result['large_pan_gap_reproduced']), flush=True)
    if result['large_pan_gap_reproduced']:
        save('state.json', {'status': 'running', 'stage': 2, 'updated_utc': now()})
        second = [launch(2, i, arm, frozen) for i, arm in enumerate('FVVF', 1)]
        save('stage2-evaluation.json', compare(second, 'F'))
        archive()
    check(frozen)
    save('state.json', {'status': 'complete', 'completed_launches': len(RECEIPTS), 'ended_utc': now(),
         'stage2_run': len(RECEIPTS) == 8, 'frozen_entries_unchanged': len(frozen), 'selection_evidence': False})
    print('COMPLETE', len(RECEIPTS), now(), flush=True)


if __name__ == '__main__':
    if (HERE/'freeze.json').exists():
        sys.exit('Attempt r1 already exists; preserve it and explicitly amend before any new attempt.')
    try:
        main()
    except Exception as error:
        if RECEIPTS:
            if RECEIPTS[-1]['status'] != 'valid':
                RECEIPTS[-1]['status'] = 'failed'
            save('launches.json', RECEIPTS)
            archive()
        save('failure.json', {'time_utc': now(), 'error': repr(error), 'traceback': traceback.format_exc()})
        save('state.json', {'status': 'failed', 'launch_count': len(RECEIPTS), 'time_utc': now()})
        raise
