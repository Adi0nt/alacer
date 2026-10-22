"""Freeze Java 25 compatibility, preservation and instrumentation evidence after collection."""
import hashlib
import json
from pathlib import Path
import re
import shutil
import struct
import subprocess
import xml.etree.ElementTree as ET
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(name, data):
    (HERE / name).write_text(json.dumps(data, indent=2) + '\n')


def main():
    before = json.loads((ROOT / 'build/java25-migration-before.json').read_text())
    allowed = {'src/main/resources/alacer.mixins.json', 'src/main/resources/fabric.mod.json',
               'src/main/java/net/adiont/alacer/Alacer.java', 'docs/ARCHITECTURE.md',
               'docs/BENCHMARKS.md', 'build.gradle', 'README.md'}
    changed = [name for name, expected in before.items() if sha(ROOT / name) != expected]
    assert set(changed) <= allowed
    historical = {name: expected for name, expected in before.items() if name.startswith('docs/benchmarks/')}
    world = json.loads((ROOT / 'build/java25-world-before.json').read_text())
    assert all(sha(ROOT / name) == expected for name, expected in world.items())
    assert (ROOT / 'run/options.txt').read_bytes() == (ROOT / 'build/options.txt.java25-before').read_bytes()
    assert (ROOT / 'run/config/alacer.properties').read_bytes() == (ROOT / 'build/alacer.properties.java25-before').read_bytes()
    write('preservation.json', {'changed_existing_files': changed,
          'all_preexisting_java_sources_except_environment_metadata_unchanged': True,
          'historical_archive_sha256': historical, 'base_world_sha256': world,
          'game_options_and_config_unchanged': True,
          'original_files_sha256': before})
    implementation = [p for p in (ROOT / 'src').rglob('*') if p.is_file()]
    implementation += [ROOT / name for name in ('AGENTS.md', 'build.gradle', 'settings.gradle',
                       'gradle.properties', 'gradle/gradle-daemon-jvm.properties',
                       'gradle/wrapper/gradle-wrapper.properties', 'run-client.bat',
                       'tools/run_java25_validation.py', 'tools/compare_benchmarks.py',
                       'docs/benchmarks/2026-10-20-java25/analyze.py',
                       'docs/benchmarks/2026-10-20-java25/verify.py')]
    write('implementation.json', {str(p.relative_to(ROOT)).replace('\\', '/'): sha(p)
                                  for p in implementation})
    tests = []
    for path in (ROOT / 'build/test-results/test').glob('TEST-*.xml'):
        node = ET.parse(path).getroot()
        assert int(node.attrib['failures']) == 0 and int(node.attrib['errors']) == 0
        assert int(node.attrib.get('skipped', 0)) == 0
        tests.append({'suite': node.attrib['name'], 'tests': int(node.attrib['tests']), 'sha256': sha(path)})
    assert sum(t['tests'] for t in tests) == 49
    artifacts = {}
    for path in (ROOT / 'build/libs').glob('*.jar'):
        with zipfile.ZipFile(path) as z:
            versions = {struct.unpack('>H', z.read(n)[6:8])[0] for n in z.namelist()
                        if n.startswith('net/adiont/') and n.endswith('.class')}
            if not path.name.endswith('-sources.jar'):
                assert versions == {69}
                assert json.loads(z.read('fabric.mod.json'))['depends']['java'] == '>=25'
                assert json.loads(z.read('alacer.mixins.json'))['compatibilityLevel'] == 'JAVA_25'
        artifacts[str(path.relative_to(ROOT))] = {'sha256': sha(path), 'project_class_majors': sorted(versions)}
    records = [json.loads(p.read_text()) for p in (HERE / 'runs').glob('*.record.json')]
    assert len(records) == 17
    images = json.loads((HERE / 'images.json').read_text())
    assert len(images) == 8
    for name, image in images.items():
        assert image['inspected'] and sha(ROOT / name) == image['sha256']
    for record in records:
        log = HERE / 'logs' / (record['label'] + '.log')
        assert sha(log) == record['log_sha256']
        summary = json.loads((HERE / 'runs' / (record['label'] + '.summary.json')).read_text())
        assert summary['environment']['java.version'].startswith('25.')
        assert summary['environment']['profiler.allocationAvailable'] == 'true'
        assert summary['environment']['benchmark.generationRun'] == 'false'
        for phase in summary['phases'].values():
            assert phase['frames'] > 0 and phase['dropped_frames'] == 0
            assert phase['discarded_setup_frames'] == 1 and phase['alloc_mean_bytes'] > 0
            assert phase['client_thread_cpu_ms_per_frame'] > 0 and phase['process_cpu_cores'] > 0
    diagnostics = {}
    for backend in ('dl', 'vbo'):
        text = (HERE / 'logs' / f'j25-verify-{backend}.log').read_text()
        for required in ('Chunk visibility verification:', 'Entity chunk lookup verification:',
                         'Matrix read verification:', 'Zero translation verification:',
                         'Cloud cache verification final:', 'native feedback comparisons'):
            assert required in text, required
        assert not re.search(r'\b[1-9]\d* (?:mismatched|mismatches|changed the matrix|differed|tiles differed|empty comparisons)\b', text)
        for counter in ('Entity shadow queries final', 'Chunk frustum verification final'):
            assert re.search(counter + r': [1-9]\d* .*?, 0 mismatched', text)
        diagnostics[backend] = [line for line in text.splitlines() if re.search(
            r'Chunk visibility verification:|Matrix read verification:|Zero translation verification:|Entity chunk lookup verification:|verification final:|queries final:|Model list batch final:', line)][-15:]
    gl = (HERE / 'logs/j25-gl-error.log').read_text()
    count = re.findall(r'\((\d+) injected, (\d+) reported, (\d+) missed\)', gl)[-1]
    assert int(count[0]) > 0 and count[0] == count[1] and count[2] == '0'
    diagnostics['gl_error_injected_reported_missed'] = list(map(int, count))
    cloud = (HERE / 'logs/j25-cloud-sweep.log').read_text()
    diagnostics['cloud_sweep_final'] = next(line for line in cloud.splitlines() if 'Cloud cache verification final:' in line)
    assert '0 differed, 0 tiles differed' in diagnostics['cloud_sweep_final']
    shadow = (HERE / 'logs/j25-shadow-diagnostic.log').read_text()
    assert re.search(r'shadow diagnostic toggled 36 cells [1-9]\d* times', shadow)
    diagnostics['shadow_diagnostic_final'] = [line for line in shadow.splitlines() if 'queries final:' in line or 'diagnostic toggled' in line]
    jfr = ROOT / 'run/java25-detail.jfr'
    proc = subprocess.run(['jfr', 'summary', str(jfr)], capture_output=True, text=True, check=True)
    (HERE / 'jfr-summary.txt').write_text(proc.stdout)
    events = {name: int(count) for name, count in re.findall(r'^\s+(jdk\.\w+)\s+(\d+)\s+\d+\s*$', proc.stdout, re.M)}
    for name in ('jdk.ExecutionSample', 'jdk.ObjectAllocationSample', 'jdk.GarbageCollection'):
        assert events[name] > 0, name
    diagnostics['jfr'] = {'source': str(jfr.relative_to(ROOT)), 'sha256': sha(jfr), 'event_counts': events,
                          'classification': 'intrusive diagnostic; excluded from performance controls'}
    for filename in ('java25-audit-release8.log', 'java25-bytecode-smoke.log'):
        audit = (ROOT / 'build' / filename).read_text()
        assert 'BUILD SUCCESSFUL' in audit and 'java.version=25.' in audit
    batch = (ROOT / 'build/java25-batch-dry-run.log').read_text()
    assert 'BUILD SUCCESSFUL' in batch and ':runClient SKIPPED' in batch
    write('verification.json', {'tests': tests, 'total_tests': 49, 'artifacts': artifacts,
                               'successful_suite_launches': len(records),
                               'additional_runtime_audit_launches': 2,
                               'batch_launcher_dry_run_from_outside_project': 'passed',
                               'diagnostics': diagnostics})
    for filename in ('java25-build.log', 'java25-audit-release8.log', 'java25-bytecode-smoke.log',
                     'java25-dependencies-before.txt', 'java25-batch-dry-run.log'):
        shutil.copyfile(ROOT / 'build' / filename, HERE / filename)
    print('Verified 49 tests, classfile 69, 17 client launches, JFR/allocation/CPU, unchanged Java 8 archives and renderer sources')


if __name__ == '__main__':
    main()
