#!/usr/bin/env python3
"""Validate and package Session 19 evidence without rebuilding or touching shared config."""
import argparse
import difflib
import importlib.util
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

import run_particle_movement as common


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, default=Path('docs/benchmarks/2026-10-20-particle-entity-query'))
    parser.add_argument('--prefix', default='pe19v3')
    opts = parser.parse_args()
    root = common.ROOT
    archive = (root / opts.archive).resolve()
    assert archive.is_relative_to(root)
    spec = importlib.util.spec_from_file_location('session19_analysis', archive / 'analyze.py')
    analysis = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(analysis)
    records = [analysis.load_record(p)[0] for p in sorted((archive / 'runs').glob('*.record.json'))]
    controls = [r for r in records if r['label'].startswith(opts.prefix + '-ab-') and not r['diagnostic']]
    assert len(controls) == 8
    current = common.sources()
    assert all(r['source_sha256'] == current for r in controls)
    final = [r for r in records if r['label'].startswith(opts.prefix + '-')]
    assert all(r['source_sha256'] == current for r in final)
    shared = root / 'run/config/alacer.properties'
    shared_hash = common.sha(shared)
    assert shared_hash == '694ff84d8e5672cd7a2ec0ee1b5a3e4eadbe09552d5d65eef93e16f61ceacd31'
    for record in records:
        assert record['shared_config_before_sha256'] == record['shared_config_after_sha256'] == shared_hash
        if 'jfr' in record:
            assert common.sha(root / record['jfr']) == record['jfr_sha256']
        summary = json.loads((root / record['source'] / 'summary.json').read_text())
        env = summary['environment']
        assert env['java.version'].startswith('25.')
        assert env['alacer.configSource'] == 'build-defaults-and-jvm-overrides'
        assert env['options.renderDistance'] == '12' and env['options.vsync'] == 'false'
        assert env['vboActive'] == 'false' and env['window'] == '1920x1080'
        for config in record['phase_configs'].values():
            assert config['render.fastParticleMovement'] == 'false'
            assert config['render.reuseParticleBoxes'] == 'false'
    attempts = [json.loads(p.read_text()) for p in sorted((archive / 'attempts').glob('*.json'))]
    for attempt in attempts:
        snapshot = attempt['source_snapshot']
        assert common.sha(root / snapshot['path']) == snapshot['sha256']
        if 'log_sha256' in attempt:
            assert common.sha(root / attempt['log']) == attempt['log_sha256']
    tests = [ET.parse(p).getroot() for p in (root / 'build/test-results/test').glob('TEST-*.xml')]
    counts = {name: sum(int(t.get(name)) for t in tests) for name in ('tests', 'failures', 'errors')}
    assert counts == {'tests': 65, 'failures': 0, 'errors': 0}
    classes = list((root / 'build/classes/java').rglob('*.class'))
    assert classes and {int.from_bytes(p.read_bytes()[6:8], 'big') for p in classes} == {69}
    runtime = (root / 'build/pe19v3-java-runtime.txt').read_text()
    assert 'Launcher JVM:  25.' in runtime and 'Compatible with Java 25' in runtime
    metrics = json.loads((archive / 'metrics.json').read_text())
    assert len(metrics['comparison']) == 16 and metrics['final_prefix'] == opts.prefix
    baseline = json.loads((archive / 'runs/pe19-profile.record.json').read_text())
    with zipfile.ZipFile(root / baseline['source_snapshot']['path']) as old:
        previous = {name: old.read(name).decode('utf-8').replace('\r\n', '\n') for name in old.namelist()}
    patch = []
    for name in sorted(set(previous) | {p.replace('\\', '/') for p in current}):
        path = root / name
        before = previous.get(name, '')
        after = path.read_text(encoding='utf-8') if path.exists() else ''
        patch.extend(difflib.unified_diff(before.splitlines(True), after.splitlines(True),
                                        fromfile='a/' + name, tofile='b/' + name))
    (archive / 'implementation.patch').write_text(''.join(patch), encoding='utf-8')

    def package(name, paths):
        target = archive / name
        with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as out:
            for path in sorted(set(paths)):
                out.write(path, str(path.relative_to(root)).replace('\\', '/'))
        return {'path': str(target.relative_to(root)), 'sha256': common.sha(target)}

    evidence = package('validation-evidence.zip', list((root / 'build').glob('pe19*.log'))
                       + [root / 'build/pe19v3-java-runtime.txt']
                       + list((root / 'build/test-results/test').glob('*.xml')))
    tooling = package('tooling-source.zip', [root / 'tools' / name for name in (
        'run_particle_entity_query.py', 'run_particle_boxes.py', 'run_particle_movement.py',
        'particle_box_jfr.py', 'validate_particle_entity_query.py')] + [archive / 'analyze.py'])
    report = {'session': 19, 'java_major': 25, 'classfile_major': 69, **counts,
              'completed_final_controls': len(controls), 'final_phase_comparisons': 16,
              'superseded_completed_controls': len([r for r in records if not r['diagnostic'] and r not in controls]),
              'all_final_sources_identical_and_match_current': True,
              'final_source_snapshot': controls[0]['source_snapshot'],
              'shared_config_sha256': shared_hash, 'shared_config_preserved': True,
              'all_record_export_log_source_and_jfr_hashes_validated': True,
              'attempt_log_and_snapshot_hashes_validated': True,
              'environment_checked': ['java.version', 'alacer.configSource', 'window', 'vboActive',
                                      'options.renderDistance', 'options.vsync'],
              'validation_evidence': evidence, 'tooling_source': tooling,
              'analysis_log': {'path': 'build/pe19v3-analysis-final.log',
                               'sha256': common.sha(root / 'build/pe19v3-analysis-final.log')},
              'build_and_tests_are_not_visual_or_performance_evidence': True,
              'performance_gates_passed': metrics['all_performance_gates_passed'],
              'committed': False, 'pushed': False, 'published': False, 'driver_settings_changed': False}
    (archive / 'validation.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
