#!/usr/bin/env python3
"""Session 28 gate series for render.reuseShadowPositions (see the archive's gates.md).

Usage:
  python3 tools/run_shadow_position_gates.py freeze            (records HEAD after the gates commit)
  python3 tools/run_shadow_position_gates.py <group> [--only LABEL] --jdk <jdk25> --init <mirror.gradle>
Groups: verify, screens, ab, startup (run serially, in that order). Reuses run_allocation_selection.py's
helpers and option lists without changing them.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import threading
import time

import run_allocation_selection as base

ROOT = base.ROOT
ARCHIVE = base.ARCHIVE
BENCH = base.BENCH
# Amendments 1 and 2 (gates.md) re-froze the runner; freeze-gates.json and -2 record the earlier freezes.
FREEZE = ARCHIVE / 'freeze-gates-3.json'
FROZEN_PATHS = base.FROZEN_PATHS + ('tools/run_shadow_position_gates.py',
                                    'docs/benchmarks/2026-10-22-allocation-selection/gates.md',
                                    'docs/benchmarks/2026-10-22-allocation-selection/gates.py',
                                    'docs/benchmarks/2026-10-22-allocation-selection/selection.md')
OPTION = 'render.reuseShadowPositions'
DEFAULT_PHASES = ('static', 'pan', 'fly')
TOGGLE_PHASES = ('warmup-static-a', 'warmup-static-b', 'static-a1', 'static-b1', 'static-b2', 'static-a2',
                 'warmup-pan-a', 'warmup-pan-b', 'pan-a1', 'pan-b1', 'pan-b2', 'pan-a2')
TERRAIN_PHASES = tuple(base.SCENES['terrain']['phases'])
FINAL = re.compile(r'Shadow position reuse final: (\d+) positions compared, (\d+) mismatched')


def jobs():
    out = []
    # Labels ending in -r2 replace attempts rejected by runner bugs fixed in amendment 1 (kept under attempts/).
    out += [('verify', 'alloc28g-verify-d200-1-r2', 'd200', {'on': True, 'verify': True, 'scale': '10'}),
            ('verify', 'alloc28g-verify-d200-2', 'd200', {'on': True, 'verify': True, 'scale': '10'}),
            ('verify', 'alloc28g-verify-walk', 'd200', {'on': True, 'verify': True, 'scale': '4', 'shadowDiagnostic': True})]
    out += [('screens', 'alloc28g-shot-absent-1-r2', 'd200', {'on': False, 'scale': '1', 'screenshots': True}),
            ('screens', 'alloc28g-shot-on', 'd200', {'on': True, 'scale': '1', 'screenshots': True}),
            ('screens', 'alloc28g-shot-absent-2', 'd200', {'on': False, 'scale': '1', 'screenshots': True})]
    for n, reverse in enumerate((False, True, False, True), 1):
        out.append(('ab', f'alloc28g-ab-{n}' + ('-r2' if n == 1 else ''), 'd200', {'on': False, 'scale': '2', 'toggle': True, 'reverse': reverse}))
    for scene, pairs in (('d200', 4), ('d0', 2), ('terrain', 2)):
        for n in range(1, pairs + 1):
            sides = ('absent', 'on') if n % 2 == 1 else ('on', 'absent')
            for side in sides:
                out.append(('startup', f'alloc28g-st-{scene}-{n}-{side}', scene, {'on': side == 'on', 'scale': '2'}))
    return out


def options(scene, label, spec):
    props = base.explicit_options(scene, label)
    props['benchmark.timeScale'] = spec['scale']
    props[OPTION] = 'true' if spec['on'] else 'false'
    props[OPTION + '.verify'] = 'true' if spec.get('verify') else 'false'
    props['benchmark.screenshots'] = 'true' if spec.get('screenshots') else 'false'
    props['benchmark.shadowDiagnostic'] = 'true' if spec.get('shadowDiagnostic') else 'false'
    props['benchmark.toggleReverse'] = 'true' if spec.get('reverse') else 'false'
    if spec.get('toggle'):
        props['benchmark.toggle'] = OPTION
    return props


def tree_state():
    head = base.git('rev-parse', 'HEAD')
    dirty = base.git('status', '--porcelain', '--', *FROZEN_PATHS)
    return head, dirty


def run(group, label, scene, spec, jdk, init):
    record_path = ARCHIVE / 'gates' / (label + '.record.json')
    attempt_path = ARCHIVE / 'attempts' / (label + '.json')
    assert not record_path.exists() and not attempt_path.exists(), 'label already recorded: ' + label
    assert not list(BENCH.glob('*-' + label + '-*')), 'label already used'
    log = ROOT / 'build' / (label + '.log')
    assert not log.exists(), 'log already exists'
    assert base.clients() == [], 'another client is running'
    frozen = json.loads(FREEZE.read_text())
    head, dirty = tree_state()
    assert dirty == '', 'frozen paths differ from HEAD:\n' + dirty
    assert base.git('diff', '--name-only', frozen['commit'], 'HEAD', '--', *FROZEN_PATHS) == '', 'frozen paths changed'
    assert base.world_hashes() == frozen['base_world_sha256'], 'base world differs from the freeze'
    props = options(scene, label, spec)
    args = ['xvfb-run', '-a', '-s', '-screen 0 1280x720x24 +extension GLX +extension RANDR',
            'sh', './gradlew', 'runClient', '--console=plain', '-I', str(init),
            '-Porg.gradle.java.installations.paths=' + str(jdk), '-Pwindow=' + base.WINDOW, '-Pxmx=2G']
    args += ['-Palacer.' + k + '=' + v for k, v in props.items()]
    base_world = base.world_hashes()
    attempt_path.parent.mkdir(parents=True, exist_ok=True)
    attempt = {'label': label, 'group': group, 'scene': scene, 'spec': spec, 'command': args, 'head': head,
               'status': 'running'}
    attempt_path.write_text(json.dumps(attempt, indent=2) + '\n')
    start = time.monotonic()
    print('Starting:', label, flush=True)
    launchers = []

    def watch(process):
        while process.poll() is None:
            for found in base.clients():
                if found['pid'] not in {l['pid'] for l in launchers}:
                    launchers.append(found)
            time.sleep(1)

    with log.open('w', encoding='utf-8') as out:
        process = subprocess.Popen(args, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT, env=dict(os.environ, JAVA_HOME=str(jdk)))
        watcher = threading.Thread(target=watch, args=(process,), daemon=True)
        watcher.start()
        try:
            process.wait(timeout=3000)
        except subprocess.TimeoutExpired:
            process.kill()
            raise
        watcher.join()
    elapsed = time.monotonic() - start
    attempt.update({'exit_code': process.returncode, 'log': str(log.relative_to(ROOT)), 'log_sha256': base.sha(log),
                    'launchers': launchers, 'elapsed_seconds': elapsed})

    def fail(reason):
        attempt['status'] = 'rejected'
        attempt['reason'] = reason
        attempt_path.write_text(json.dumps(attempt, indent=2) + '\n')
        raise SystemExit('Rejected ' + label + ': ' + reason)

    if len(launchers) != 1:
        fail('expected exactly one client launcher, saw %d' % len(launchers))
    launcher = launchers[0]
    if Path(launcher['exe']).resolve() != (Path(jdk) / 'bin/java').resolve():
        fail('unexpected launcher ' + launcher['exe'])
    command = launcher['cmdline']
    if '-Xmx2G' not in command or any(a.startswith('-XX:StartFlightRecording') for a in command):
        fail('heap size missing or JFR present')
    for k, v in props.items():
        if '-Dalacer.' + k + '=' + v not in command:
            fail('missing launcher property ' + k)
    if process.returncode != 0:
        fail('launch failed')
    if tree_state() != (head, dirty):
        fail('sources changed during launch')
    if base.world_hashes() != base_world:
        fail('base world changed')
    text = log.read_text(encoding='utf-8', errors='replace')
    if 'BUILD SUCCESSFUL' not in text:
        fail('build not successful')
    for bad in base.BAD:
        if bad in text:
            fail('log contains ' + bad)
    for line in text.splitlines():
        if 'ismatch' in line:
            # Exit (final:) and periodic (verification:) summaries may report zero mismatches.
            counts = re.findall(r'(\d+) mismatch', line)
            if ('final:' not in line and 'verification:' not in line) or not counts or any(int(c) != 0 for c in counts):
                fail('mismatch line: ' + line.strip())
    final = FINAL.search(text)
    if not final:
        fail('no shadow position summary')
    compared, mismatched = int(final[1]), int(final[2])
    if mismatched != 0 or (compared > 0) != bool(spec.get('verify')):
        fail('unexpected shadow position summary: ' + final[0])
    folders = list(BENCH.glob('*-' + label + '-*'))
    if len(folders) != 1:
        fail('result folder not unique')
    folder = folders[0]
    summary = json.loads((folder / 'summary.json').read_text())
    e = summary['environment']
    checks = {
        'java': e['java.runtime.version'].startswith('25.0.4.1') and e['java.vendor'] == 'Eclipse Adoptium',
        'generation': e['benchmark.generationRun'] == 'false',
        'window': e['window'] == base.WINDOW and e['vboActive'] == 'false',
        'view/vsync': e['options.renderDistance'] == '12' and e['options.vsync'] == 'false',
        'mobs': e['benchmark.mobs'] == base.SCENES[scene]['props']['benchmark.entities'] and e['benchmark.chests'] == '0',
        'scenario': e['benchmark.backendScenario'] == base.SCENES[scene]['props']['benchmark.backendScenario']
                    and e['benchmark.particles'] == 'false',
        'allocation': e['profiler.allocationAvailable'] == 'true',
        # The benchmark exports a toggle as '<key> (a=false, b=true)'.
        'toggle': (e['benchmark.toggle'].split(' (')[0] == OPTION if spec.get('toggle') else e['benchmark.toggle'] == '')
                  and e['benchmark.toggleReverse'] == ('true' if spec.get('reverse') else 'false'),
        'renderer': e['gl.renderer'].startswith('llvmpipe'),
    }
    for name, ok in checks.items():
        if not ok:
            fail('environment check failed: ' + name)
    expected = TOGGLE_PHASES if spec.get('toggle') else TERRAIN_PHASES if scene == 'terrain' else DEFAULT_PHASES
    if set(summary['phases']) != set(expected):
        fail('unexpected phases ' + str(sorted(summary['phases'])))
    configs = {}
    for name, phase in summary['phases'].items():
        if not (phase['frames'] > 0 and phase['dropped_frames'] == 0 and phase['discarded_setup_frames'] == 1):
            fail('frame checks failed in ' + name)
        config = json.loads((folder / (name + '.json')).read_text())['config']
        want = ('true' if name.split('-')[-1].startswith('b') else 'false') if spec.get('toggle') else props[OPTION]
        if config[OPTION] != want or config[OPTION + '.verify'] != props[OPTION + '.verify']:
            fail('option state differs in ' + name)
        for key in base.ON:
            if config['render.' + key] != 'true':
                fail('option not on: ' + key)
        for key in base.OFF:
            if config['render.' + key] != 'false':
                fail('option not off: ' + key)
        configs[name] = config
    shots = {}
    if spec.get('screenshots'):
        for name in expected:
            # The benchmark appends its own suffix to the label (for example -vis_on-ecl_on-ent200).
            found = sorted((ROOT / 'run/screenshots').glob(f'alacer-{label}-*-{name}.png'))
            if len(found) != 1:
                fail('screenshot missing or not unique: ' + name)
            shot = found[0]
            shots[name] = {'path': str(shot.relative_to(ROOT)), 'sha256': base.sha(shot)}
    record = {'label': label, 'group': group, 'scene': scene, 'spec': spec, 'command': args, 'head': head,
              'launcher': launcher, 'elapsed_seconds': elapsed, 'source': str(folder.relative_to(ROOT)),
              'files_sha256': {p.name: base.sha(p) for p in folder.iterdir() if p.is_file()},
              'log': str(log.relative_to(ROOT)), 'log_sha256': base.sha(log),
              'shadow_positions_compared': compared, 'shadow_positions_mismatched': mismatched,
              'verification_lines': [l for l in text.splitlines() if 'final:' in l or 'verification:' in l],
              'screenshots': shots, 'phase_configs': configs}
    record_path.parent.mkdir(parents=True, exist_ok=True)
    record_path.write_text(json.dumps(record, indent=2) + '\n')
    (record_path.parent / (label + '.summary.json')).write_text(json.dumps(summary, indent=2) + '\n')
    attempt['status'] = 'passed'
    attempt_path.write_text(json.dumps(attempt, indent=2) + '\n')
    print('Passed:', label, flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('group', choices=('freeze', 'verify', 'screens', 'ab', 'startup'))
    parser.add_argument('--only')
    parser.add_argument('--jdk', type=Path)
    parser.add_argument('--init', type=Path)
    opts = parser.parse_args()
    if opts.group == 'freeze':
        assert not FREEZE.exists(), 'already frozen'
        head, dirty = tree_state()
        assert dirty == '', 'commit the gate series first:\n' + dirty
        FREEZE.write_text(json.dumps({'commit': head, 'frozen_at': time.strftime('%Y-%m-%dT%H:%M:%S%z'),
                                      'frozen_paths': FROZEN_PATHS, 'base_world_sha256': base.world_hashes()},
                                     indent=2) + '\n')
        print('Frozen at', head)
        raise SystemExit(0)
    assert opts.jdk and opts.init, '--jdk and --init are required'
    selected = [j for j in jobs() if j[0] == opts.group and (not opts.only or j[1] == opts.only)]
    assert selected, 'nothing selected'
    for group, label, scene, spec in selected:
        run(group, label, scene, spec, opts.jdk.resolve(), opts.init.resolve())
