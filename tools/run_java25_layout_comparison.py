#!/usr/bin/env python3
"""Run fresh Java 25 object-header controls in A/B/B/A order, serially.

This is a compatibility and exploratory performance check, not a default-selection gate.
Archived Java 8/25 benchmark results and the shared game config are not modified.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
ARCHIVE = ROOT / 'docs/benchmarks/2026-10-25-java25-layout'
RESULTS = ROOT / 'run/alacer/benchmarks'
FAILURE = re.compile(r'Benchmark aborted|Benchmark timed out|MixinApplyError|InjectionError|'
                     r'verification mismatch|OpenGLException|BUILD FAILED', re.I)
JDK = Path('C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot')


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def source_state():
    return (subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
            subprocess.check_output(['git', 'diff', '--binary'], cwd=ROOT),
            digest(ROOT / 'build/classes/java/main/net/adiont/alacer/Alacer.class'),
            digest(JDK / 'bin/java.exe'))


def main():
    if not (JDK / 'bin/java.exe').is_file():
        raise RuntimeError('Selected Temurin JDK is missing')
    if not (ROOT / 'run/saves/alacer-benchmark').is_dir():
        raise RuntimeError('The existing benchmark base world is missing')
    ARCHIVE.mkdir(parents=True, exist_ok=True)
    state = source_state()
    baseline_config = None
    rows = []
    for suffix, compact in [('a1', False), ('b1', True), ('b2', True), ('a2', False)]:
        label = 'j25-layout-20261025-' + suffix
        log_path = ARCHIVE / (label + '.log')
        if log_path.exists() or list(RESULTS.glob('*-' + label + '-vis_*')):
            raise RuntimeError(f'Existing attempt for {label}; choose fresh labels')
        if source_state() != state:
            raise RuntimeError('Source, compiled classes or JDK changed during comparison')
        command = [str(ROOT / 'gradlew.bat'), 'runClient', '--offline', '--console=plain',
                   '-PcompactObjectHeaders=' + str(compact).lower(),
                   '-Porg.gradle.java.installations.auto-detect=false',
                   '-Porg.gradle.java.installations.paths=' + str(JDK),
                   '-Pwindow=1920x1080', '-Pxmx=2G',
                   '-Palacer.benchmark=true', '-Palacer.benchmark.exit=true',
                   '-Palacer.benchmark.label=' + label,
                   '-Palacer.benchmark.timeScale=0.5',
                   '-Palacer.benchmark.timeout=600',
                   '-Palacer.benchmark.viewDistance=12',
                   '-Palacer.benchmark.vbo=false',
                   '-Palacer.benchmark.entities=0', '-Palacer.benchmark.chests=0',
                   '-Palacer.benchmark.screenshots=false',
                   '-Palacer.profiler.hud=false',
                   '-Palacer.profiler.chunkDetail=false',
                   '-Palacer.profiler.entityDetail=false',
                   '-Palacer.profiler.particleDetail=false']
        environment = dict(os.environ, JAVA_HOME=str(JDK), PATH=str(JDK / 'bin') + os.pathsep + os.environ['PATH'])
        print('Starting', label, flush=True)
        start = time.monotonic()
        with log_path.open('x', encoding='utf-8') as out:
            completed = subprocess.run(command, cwd=ROOT, env=environment,
                                       stdout=out, stderr=subprocess.STDOUT, timeout=720)
        log = log_path.read_text(encoding='utf-8', errors='replace')
        if completed.returncode or 'BUILD SUCCESSFUL' not in log or FAILURE.search(log):
            raise RuntimeError(f'{label} failed; see {log_path}')
        folders = list(RESULTS.glob('*-' + label + '-vis_*'))
        if len(folders) != 1:
            raise RuntimeError(f'{label}: missing or ambiguous benchmark summary')
        summary_path = folders[0] / 'summary.json'
        summary = json.loads(summary_path.read_text(encoding='utf-8'))
        actual = summary['environment']
        expected = {'java.runtime.version': '25.0.4.1+1-LTS',
                    'java.home': str(JDK), 'java.vectorModule': 'true',
                    'jvm.UseCompactObjectHeaders': str(compact).lower(),
                    'window': '1920x1080', 'vboActive': 'false',
                    'benchmark.generationRun': 'false',
                    'alacer.configSource': 'build-defaults-and-jvm-overrides'}
        for key, value in expected.items():
            if actual.get(key) != value:
                raise RuntimeError(f'{label}: {key} is {actual.get(key)!r}, expected {value!r}')
        phases = summary['phases']
        if set(phases) != {'static', 'pan', 'fly'} or any(p['frames'] <= 0 or p['dropped_frames'] for p in phases.values()):
            raise RuntimeError(f'{label}: incomplete phase data')
        configs = [p['config'] for p in phases.values()]
        if any(c != configs[0] for c in configs) or (baseline_config is not None and baseline_config != configs[0]):
            raise RuntimeError(f'{label}: game configuration drifted')
        baseline_config = configs[0]
        archived_summary = ARCHIVE / (label + '.summary.json')
        archived_summary.write_bytes(summary_path.read_bytes())
        rows.append({'label': label, 'compact_headers': compact, 'seconds': round(time.monotonic() - start, 2),
                     'command': command, 'raw_path': str(folders[0].relative_to(ROOT)),
                     'raw_sha256': {p.name: digest(p) for p in folders[0].iterdir() if p.is_file()},
                     'log_sha256': digest(log_path), 'summary_sha256': digest(summary_path),
                     'metrics': {name: {key: phase.get(key) for key in ('avg_fps', 'frame_p99_ms',
                         'client_thread_cpu_ms_per_frame', 'alloc_mean_bytes', 'heap_used_mean_bytes')}
                         for name, phase in phases.items()}})
        print('Passed', label, flush=True)
    report = {'runtime': 'Eclipse Temurin 25.0.4.1+1-LTS', 'head': state[0],
              'source_diff_sha256': hashlib.sha256(state[1]).hexdigest(),
              'class_sha256': state[2], 'jdk_sha256': state[3], 'runs': rows,
              'decision': 'Exploratory only; compact headers remain opt-in pending repeated performance and visual gates.'}
    (ARCHIVE / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
