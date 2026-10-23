#!/usr/bin/env python3
"""Session 29: render.reuseShadowPositions gate series on the reference hardware (see the archive's gates.md).

Windows 11, i7-12700F, RTX 3060 Ti, Adoptium 25.0.4.1, 1920x1080. The launch list repeats session 28's gate series
with new labels, a fixed-pose screenshot capture and the target region judged on tick-free frames. The reference
checkout is untracked and shared, so the freeze is a hash manifest plus a sources ZIP (session 27's method), not a
git commit. Reuses session 26-28 helpers without changing them.

Usage (from the checkout root, after `gradlew.bat build`, no other client running):
  python tools/run_shadow_position_reference.py smoke                 (before the freeze; attempts, not evidence)
  python tools/run_shadow_position_reference.py freeze
  python tools/run_shadow_position_reference.py verify|screens|ab|startup [--only LABEL]
Run the groups serially, in that order.
"""
import argparse
import datetime
import json
import os
from pathlib import Path
import re
import subprocess
import time
import traceback

import run_allocation_selection as s28
import run_whole_client_attribution as prior

ROOT = prior.ROOT
ARCHIVE = ROOT / 'docs/benchmarks/2026-10-22-shadow-positions-reference'
MANIFEST = ARCHIVE / 'manifest.json'
BENCH = ROOT / 'run/alacer/benchmarks'
JDK = prior.JDK
sha = prior.movement.sha
clients = prior.clients
world_hashes = prior.world_hashes
OPTION = 'render.reuseShadowPositions'
WINDOW = '1920x1080'
RENDERER = 'NVIDIA GeForce RTX 3060 Ti'
DEFAULT_PHASES = tuple(s28.SCENES['d200']['phases'])
TERRAIN_PHASES = tuple(s28.SCENES['terrain']['phases'])
TOGGLE_PHASES = ('warmup-static-a', 'warmup-static-b', 'static-a1', 'static-b1', 'static-b2', 'static-a2',
                 'warmup-pan-a', 'warmup-pan-b', 'pan-a1', 'pan-b1', 'pan-b2', 'pan-a2')
FINAL = re.compile(r'Shadow position reuse final: (\d+) positions compared, (\d+) mismatched')
BAD = s28.BAD + ('Chunk render task was', 'OpenGL Error')
TOOLING = ('tools/run_shadow_position_reference.py', 'tools/run_allocation_selection.py',
           'tools/run_whole_client_attribution.py', 'tools/run_particle_movement.py', 'settings.gradle',
           'gradle/gradle-daemon-jvm.properties', 'gradle/wrapper/gradle-wrapper.properties',
           'docs/benchmarks/2026-10-22-shadow-positions-reference/gates.md',
           'docs/benchmarks/2026-10-22-shadow-positions-reference/gates.py')


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(value, indent=2) + '\n').encode())


def jobs():
    # Amendment 1 (gates.md): spr29-verify-d200-1 was interrupted under freeze 1 and is repeated as -r2.
    out = [('verify', 'spr29-verify-d200-1-r2', 'd200', {'on': True, 'verify': True, 'scale': '10'}),
           ('verify', 'spr29-verify-d200-2', 'd200', {'on': True, 'verify': True, 'scale': '10'}),
           ('verify', 'spr29-verify-walk', 'd200', {'on': True, 'verify': True, 'scale': '4', 'shadowDiagnostic': True})]
    out += [('screens', 'spr29-shot-absent-1', 'd200', {'on': False, 'scale': '1', 'screenshots': True}),
            ('screens', 'spr29-shot-on', 'd200', {'on': True, 'scale': '1', 'screenshots': True}),
            ('screens', 'spr29-shot-absent-2', 'd200', {'on': False, 'scale': '1', 'screenshots': True})]
    for n, reverse in enumerate((False, True, False, True), 1):
        out.append(('ab', f'spr29-ab-{n}', 'd200', {'on': False, 'scale': '2', 'toggle': True, 'reverse': reverse}))
    for scene, pairs in (('d200', 4), ('d0', 2), ('terrain', 2)):
        for n in range(1, pairs + 1):
            for side in (('absent', 'on') if n % 2 == 1 else ('on', 'absent')):
                out.append(('startup', f'spr29-st-{scene}-{n}-{side}', scene, {'on': side == 'on', 'scale': '2'}))
    # Amendment 2 (gates.md): the d0-2 pair is repeated as -r2 with the larger frame buffer.
    return [(g, label + '-r2' if label.startswith('spr29-st-d0-2-') else label, scene, spec)
            for g, label, scene, spec in out]


def smoke_jobs():
    # Shape the tooling only: every hook this series uses, at a short time scale.
    return [('smoke', 'spr29-smoke-verify', 'd200', {'on': True, 'verify': True, 'scale': '0.2', 'screenshots': True}),
            ('smoke', 'spr29-smoke-ab', 'd200', {'on': False, 'scale': '0.2', 'toggle': True, 'reverse': False})]


def options(scene, label, spec):
    props = s28.explicit_options(scene, label)
    props['benchmark.timeScale'] = spec['scale']
    props['benchmark.exit'] = 'true'
    props[OPTION] = 'true' if spec['on'] else 'false'
    props[OPTION + '.verify'] = 'true' if spec.get('verify') else 'false'
    props['benchmark.screenshots'] = 'true' if spec.get('screenshots') else 'false'
    props['benchmark.screenshotEndPose'] = 'true' if spec.get('screenshots') else 'false'
    props['benchmark.shadowDiagnostic'] = 'true' if spec.get('shadowDiagnostic') else 'false'
    props['benchmark.toggleReverse'] = 'true' if spec.get('reverse') else 'false'
    if spec.get('toggle'):
        props['benchmark.toggle'] = OPTION
    return props


def source_hashes():
    hashes = prior.movement.sources()
    for name in TOOLING:
        hashes[name] = sha(ROOT / name)
    assert all(hashes.values()), [k for k, v in hashes.items() if not v]
    return hashes


def artifacts():
    return {str(p.relative_to(ROOT)): sha(p) for folder in ('build/classes/java/main', 'build/resources/main')
            for p in sorted((ROOT / folder).rglob('*')) if p.is_file()}


def check(manifest):
    assert source_hashes() == manifest['source_sha256'], 'frozen sources/tooling changed'
    assert artifacts() == manifest['runtime_artifacts_sha256'], 'runtime artifacts changed'
    assert world_hashes() == manifest['base_world_sha256'], 'base world changed'
    assert sha(ROOT / 'run/config/alacer.properties') == manifest['shared_config_sha256'], 'shared configuration changed'
    assert sha(ROOT / manifest['source_snapshot']['path']) == manifest['source_snapshot']['sha256']


def freeze():
    assert clients() == [], 'client running during freeze'
    assert not MANIFEST.exists(), 'already frozen'
    smokes = [ARCHIVE / 'attempts' / (label + '.json') for _, label, _, _ in smoke_jobs()]
    assert all(p.exists() and json.loads(p.read_text())['status'] == 'passed-smoke' for p in smokes), \
        'both smoke launches must pass first'
    h = source_hashes()
    prior.movement.ARCHIVE = ARCHIVE
    snap = prior.movement.snapshot(h)
    assert world_hashes() and artifacts()
    m = {'session': 29, 'frozen_at_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
         'source_sha256': h, 'source_snapshot': snap, 'base_world_sha256': world_hashes(),
         'runtime_artifacts_sha256': artifacts(), 'shared_config_sha256': sha(ROOT / 'run/config/alacer.properties'),
         'java_home': str(JDK), 'jobs': jobs()}
    check(m)
    write(MANIFEST, m)
    print('Frozen:', snap, flush=True)


def validate(folder, label, scene, spec, props, log_text):
    summary = json.loads((folder / 'summary.json').read_bytes())
    e = summary['environment']
    expected = {'alacer.configSource': 'build-defaults-and-jvm-overrides', 'benchmark.generationRun': 'false',
                'window': WINDOW, 'vboActive': 'false', 'options.renderDistance': '12', 'options.vsync': 'false',
                'benchmark.mobs': s28.SCENES[scene]['props']['benchmark.entities'], 'benchmark.chests': '0',
                'benchmark.backendScenario': s28.SCENES[scene]['props']['benchmark.backendScenario'],
                'benchmark.particles': 'false', 'benchmark.chunkFlight': '', 'profiler.allocationAvailable': 'true',
                'benchmark.toggle': OPTION + ' (a=false, b=true)' if spec.get('toggle') else '',
                'benchmark.toggleReverse': props['benchmark.toggleReverse'],
                'benchmark.screenshots': props['benchmark.screenshots'],
                'benchmark.screenshotEndPose': props['benchmark.screenshotEndPose'],
                'benchmark.particleDiagnostic': 'false', 'benchmark.particleRemovalDiagnostic': 'false'}
    for k, v in expected.items():
        assert e.get(k) == v, (label, k, e.get(k), v)
    assert e['java.runtime.version'].startswith('25.0.4.1') and e['java.vendor'] == 'Eclipse Adoptium', label
    assert RENDERER in e['gl.renderer'], ('renderer', e['gl.renderer'])
    assert 'BUILD SUCCESSFUL' in log_text and 'Benchmark: results written' in log_text
    for bad in BAD:
        assert bad not in log_text, (label, bad)
    # Gradle is only the launcher: nothing compiles during a launch.
    assert '> Task :compileJava UP-TO-DATE' in log_text and '> Task :processResources UP-TO-DATE' in log_text
    for line in log_text.splitlines():
        if 'ismatch' in line:
            # Exit (final:) and periodic (verification:) summaries may report zero mismatches.
            counts = re.findall(r'(\d+) mismatch', line)
            assert ('final:' in line or 'verification:' in line) and counts and all(int(c) == 0 for c in counts), line
    final = FINAL.search(log_text)
    assert final, 'no shadow position summary'
    compared, mismatched = int(final[1]), int(final[2])
    assert mismatched == 0 and (compared > 0) == bool(spec.get('verify')), final[0]
    if spec.get('toggle'):
        expected_phases = TOGGLE_PHASES
    else:
        expected_phases = TERRAIN_PHASES if scene == 'terrain' else DEFAULT_PHASES
    assert set(summary['phases']) == set(expected_phases), sorted(summary['phases'])
    scale = float(spec['scale'])
    configs = {}
    for name, p in summary['phases'].items():
        assert p['frames'] > 0 and p['dropped_frames'] == 0 and p['discarded_setup_frames'] == 1, name
        if not spec.get('toggle'):
            assert abs(p['target_seconds'] - s28.SCENES[scene]['phases'][name] * scale) < 1e-9, name
        assert p['alloc_mean_bytes'] > 0 and p['client_thread_cpu_ms_per_frame'] > 0 and p['process_cpu_cores'] > 0, name
        c = json.loads((folder / (name + '.json')).read_bytes())['config']
        want = ('true' if name.split('-')[-1].startswith('b') else 'false') if spec.get('toggle') else props[OPTION]
        assert c[OPTION] == want and c[OPTION + '.verify'] == props[OPTION + '.verify'], ('option state', name)
        for k in s28.ON:
            assert c['render.' + k] == 'true', k
        for k in s28.OFF:
            assert c['render.' + k] == 'false', k
        for k, v in c.items():
            if k.endswith('.verify') and k != OPTION + '.verify':
                assert v == 'false', k
        for k in s28.PROFILER_OFF:
            assert c.get('profiler.' + k, 'false') == 'false', k
        configs[name] = c
    shots = {}
    if spec.get('screenshots'):
        for name in expected_phases:
            # The benchmark appends its own suffix to the label (for example -vis_on-ecl_on-ent200).
            found = sorted((ROOT / 'run/screenshots').glob(f'alacer-{label}-*-{name}.png'))
            assert len(found) == 1, ('screenshot', name, found)
            shots[name] = {'path': str(found[0].relative_to(ROOT)), 'sha256': sha(found[0])}
    return summary, configs, compared, mismatched, shots


def run(group, label, scene, spec, manifest):
    smoke = group == 'smoke'
    log = ROOT / 'build' / (label + '.log')
    attempt_path = ARCHIVE / 'attempts' / (label + '.json')
    assert not attempt_path.exists() and not log.exists() and not list(BENCH.glob('*-' + label + '-*')), 'reused label'
    assert not (ARCHIVE / 'gates' / (label + '.record.json')).exists(), 'label already recorded'
    attempt = {'label': label, 'group': group, 'scene': scene, 'spec': spec, 'status': 'preflight',
               'frozen_manifest': manifest is not None}
    write(attempt_path, attempt)  # Every attempt, including preflight failures, is kept.
    launcher = None
    process = None
    started = time.monotonic()
    try:
        assert clients() == [], 'another KnotClient/devlaunchinjector java.exe/javaw.exe client exists'
        if manifest:
            check(manifest)
        before = source_hashes()
        runtime_before = artifacts()
        base_before = world_hashes()
        shared_before = sha(ROOT / 'run/config/alacer.properties')
        assert base_before and shared_before
        props = options(scene, label, spec)
        command = [str(ROOT / 'gradlew.bat'), 'runClient', '--offline', '--console=plain',
                   '-Pwindow=' + WINDOW, '-Pxmx=2G'] + ['-Palacer.' + k + '=' + v for k, v in props.items()]
        attempt.update(command=command, status='running', base_world_sha256=base_before, source_sha256=before,
                       java_home=str(JDK))
        write(attempt_path, attempt)
        print('Starting:', label, flush=True)
        with log.open('x', encoding='utf-8') as out:
            process = subprocess.Popen(command, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT,
                                       env=dict(os.environ, JAVA_HOME=str(JDK)))
            while process.poll() is None:
                found = clients()
                if found:
                    assert len(found) == 1, 'overlapping clients'
                    if launcher is None:
                        launcher = found[0]
                        attempt['launcher'] = launcher
                        write(attempt_path, attempt)
                        assert Path(launcher['ExecutablePath']).resolve() == (JDK / 'bin/java.exe').resolve()
                        cmd = launcher['CommandLine']
                        assert '-Xmx2G' in cmd and 'StartFlightRecording' not in cmd and 'DebugNonSafepoints' not in cmd
                        for k, v in props.items():
                            assert '-Dalacer.' + k + '=' + v in cmd, ('missing launcher flag', k)
                assert time.monotonic() - started < 3000, 'launch timed out'
                time.sleep(1)
        assert process.returncode == 0 and launcher, 'client failed or launcher not observed'
        assert clients() == [], 'client left running'
        assert before == source_hashes(), 'sources/tooling changed during launch'
        assert runtime_before == artifacts(), 'compiled artifacts changed during launch'
        assert base_before == world_hashes(), 'base world changed'
        assert shared_before == sha(ROOT / 'run/config/alacer.properties'), 'shared configuration changed'
        if manifest:
            check(manifest)
        folders = list(BENCH.glob('*-' + label + '-*'))
        assert len(folders) == 1, 'result folder not unique'
        folder = folders[0]
        summary, configs, compared, mismatched, shots = validate(
            folder, label, scene, spec, props, log.read_text(encoding='utf-8', errors='replace'))
        record = {'label': label, 'group': group, 'scene': scene, 'spec': spec, 'command': command,
                  'java_home': str(JDK), 'launcher': launcher, 'source': str(folder.relative_to(ROOT)),
                  'source_snapshot': manifest['source_snapshot'] if manifest else None,
                  'files_sha256': {p.name: sha(p) for p in folder.iterdir() if p.is_file()},
                  'log': str(log.relative_to(ROOT)), 'log_sha256': sha(log),
                  'gl': {k: summary['environment'].get(k) for k in ('gl.vendor', 'gl.renderer', 'gl.version')},
                  'shadow_positions_compared': compared, 'shadow_positions_mismatched': mismatched,
                  'screenshots': shots, 'phase_configs': configs}
        if not smoke:
            write(ARCHIVE / 'gates' / (label + '.record.json'), record)
            write(ARCHIVE / 'gates' / (label + '.summary.json'), summary)
        attempt.update(status='passed-smoke' if smoke else 'passed', record=record)
        print('Passed:', label, flush=True)
    except BaseException:
        attempt.update(status='rejected', error=traceback.format_exc())
        if process is not None and process.poll() is None:
            subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], capture_output=True)
            process.wait(timeout=30)
        raise
    finally:
        attempt.update(elapsed_seconds=time.monotonic() - started, launcher=launcher,
                       exit_code=process.returncode if process else None)
        if log.exists():
            attempt.update(log=str(log.relative_to(ROOT)), log_sha256=sha(log))
        write(attempt_path, attempt)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('group', choices=('smoke', 'freeze', 'verify', 'screens', 'ab', 'startup'))
    parser.add_argument('--only')
    opts = parser.parse_args()
    if opts.group == 'freeze':
        freeze()
        raise SystemExit(0)
    if opts.group == 'smoke':
        assert not MANIFEST.exists(), 'smoke launches precede the freeze'
        selected, manifest = smoke_jobs(), None
    else:
        manifest = json.loads(MANIFEST.read_bytes())
        selected = [j for j in jobs() if j[0] == opts.group]
    if opts.only:
        assert opts.only in {j[1] for j in selected}, 'unknown label'
        selected = [j for j in selected if j[1] == opts.only]
    for group, label, scene, spec in selected:
        run(group, label, scene, spec, manifest)