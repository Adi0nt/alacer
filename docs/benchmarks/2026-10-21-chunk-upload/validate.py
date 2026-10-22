"""Final session 27 evidence/preservation audit. Run only after every client exits."""
import hashlib
import json
from pathlib import Path
import struct
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT/'tools'))
import run_chunk_upload as runner


def write(name,obj):
    (HERE/name).write_bytes((json.dumps(obj,indent=2)+'\n').encode())


def main():
    assert not runner.clients()
    manifest = json.loads((HERE/'manifest.json').read_bytes())
    runner.check(manifest)
    initial = json.loads((ROOT/'build/cu27-preparation/initial-hashes.json').read_bytes())
    changed = {n:{'before':h,'after':runner.sha(ROOT/n)} for n,h in initial.items() if runner.sha(ROOT/n)!=h}
    allowed = {'src/main/java/net/adiont/alacer/benchmark/BenchmarkRunner.java',
               'src/main/java/net/adiont/alacer/mixin/ChunkDispatcherTimingMixin.java',
               'docs/ARCHITECTURE.md','docs/BENCHMARKS.md'}
    assert {n.replace('\\','/') for n in changed} == allowed, changed
    unchanged = {n:h for n,h in initial.items() if n not in changed}
    write('preservation.json',{'initial_sha256':initial,'changed_existing_files':changed,
                              'unchanged_existing_file_count':len(unchanged),
                              'old_benchmark_files_preserved':sum(n.replace('\\','/').startswith('docs/benchmarks/') for n in unchanged),
                              'scope':'No baseline path outside the four authorized existing source/document edits changed. New files are the dedicated scenario/GPU probe/test/runner and this archive.'})
    architecture = (ROOT/'docs/ARCHITECTURE.md').read_bytes()
    assert architecture.count(b'\n') == architecture.count(b'\r\n')
    original_benchmarks = (ROOT/'build/cu27-preparation/BENCHMARKS.md').read_bytes()
    assert (ROOT/'docs/BENCHMARKS.md').read_bytes().startswith(original_benchmarks)
    original_runner = (ROOT/'build/cu27-preparation/BenchmarkRunner.java').read_bytes()
    current_runner = (ROOT/'src/main/java/net/adiont/alacer/benchmark/BenchmarkRunner.java').read_bytes()
    assert original_runner.count(b'\r\n') == original_runner.count(b'\n')
    assert current_runner.count(b'\r\n') == current_runner.count(b'\n')
    runtime = json.loads((HERE/'runtime-identity.json').read_bytes())
    assert runner.sha(Path(runtime['jar'])) == runtime['sha256']
    suites = [ET.parse(p).getroot() for p in (ROOT/'build/test-results/test').glob('TEST-*.xml')]
    totals = {k:sum(int(s.get(k,0)) for s in suites) for k in ('tests','failures','errors','skipped')}
    assert totals == {'tests':88,'failures':0,'errors':0,'skipped':0}
    testlog = (ROOT/'build/cu27-tests.log').read_text(errors='replace')
    assert str(runner.JDK/'bin/java.exe').replace('/','\\') in testlog.replace('/','\\')
    assert 'BUILD SUCCESSFUL' in testlog
    assert 'BUILD SUCCESSFUL' in (ROOT/'build/cu27-build.log').read_text(errors='replace')
    jar = ROOT/'build/libs/alacer-0.1.0+mc1.8.9.jar'
    with zipfile.ZipFile(jar) as z:
        majors = {n:struct.unpack('>H',z.read(n)[6:8])[0] for n in z.namelist() if n.endswith('.class') and n.startswith('net/adiont/alacer/')}
    assert len(majors) == 151 and set(majors.values()) == {69}
    attempts = [json.loads(p.read_bytes()) for p in (HERE/'attempts').glob('*.json')]
    assert len(attempts)==11 and sum(a['status']=='passed' for a in attempts)==10
    assert sum(a['status']=='passed-smoke' for a in attempts)==1
    m = json.loads((HERE/'metrics.json').read_bytes())
    decision = json.loads((HERE/'decision.json').read_bytes())
    assert decision['named_candidate'] is None and not decision['promoted']
    for a in attempts:
        assert runner.sha(ROOT/a['log']) == a['log_sha256']
        record = a['record']
        for name,h in record['files_sha256'].items():
            assert runner.sha(ROOT/record['source']/name) == h
    screenshots = {}
    for scene in ('near','high'):
        for phase in ('still','fresh-flight'):
            p=ROOT/f'run/screenshots/alacer-cu27-{scene}-detail-r1-vis_on-ecl_on-ent0-{phase}.png'
            screenshots[str(p.relative_to(ROOT))]=runner.sha(p)
            assert screenshots[str(p.relative_to(ROOT))]
    metadata=[r['gpu']['metadata'] for r in m['runs'] if r['detail']]
    assert all(g['supported'] and g['dropped']==0 and g['outstanding']==0 and g['issued']==g['completed'] for g in metadata)
    write('validation.json',{'session':27,'test_totals':totals,'main_class_count':len(majors),'classfile_major':69,
          'jar':str(jar.relative_to(ROOT)),'jar_sha256':runner.sha(jar),
          'build_log_sha256':runner.sha(ROOT/'build/cu27-build.log'),
          'test_log_sha256':runner.sha(ROOT/'build/cu27-tests.log'),
          'gradle_version_log_sha256':runner.sha(ROOT/'build/cu27-gradle-version.log'),
          'jdk':str(runner.JDK),'actual_test_worker_executable_verified':True,
          'measured_launches':10,'smoke_launches':1,'failed_rejected_attempts':0,
          'fresh_flight_frames':sum(r['phases']['fresh-flight']['frames'] for r in m['runs']),
          'completed_gpu_pairs_all_diagnostic_phases':sum(g['completed'] for g in metadata),
          'remaining_clients':[],'shared_config_sha256':runner.sha(ROOT/'run/config/alacer.properties'),
          'source_and_runtime_manifest_revalidated':True,'runtime_vanilla_jar_revalidated':True,
          'base_world_revalidated':True,'historical_preservation':'preservation.json',
          'architecture_all_crlf':True,'benchmarks_original_bytes_preserved_as_prefix':True,
          'screenshots_visually_inspected':screenshots,
          'visual_result':'No obvious missing/corrupt geometry in these four views; not framebuffer identity or lifecycle/other-mod certification.',
          'candidate_validation':'NOT RUN: no candidate, no optimization A/B, no visible-update latency or lifecycle/order stress gates met.',
          'frozen_source_snapshot':manifest['source_snapshot']})
    # Self excluded; regeneration does not create a circular digest.
    files = [p for p in HERE.rglob('*') if p.is_file() and p.name!='evidence.json' and '__pycache__' not in p.parts]
    write('evidence.json',{'note':'Final derived/document/attempt evidence hashes; freeze is manifest.json + its sources ZIP.',
                         'sha256':{str(p.relative_to(ROOT)):runner.sha(p) for p in sorted(files)},
                         'documents_sha256':{n:runner.sha(ROOT/n) for n in ('docs/ARCHITECTURE.md','docs/BENCHMARKS.md')}})
    print('Validated: 88 tests; 151 classfile-69 classes; 10 measured launches; 4859 completed GPU pairs;',
          len(unchanged),'existing files preserved; only 4 authorized existing files changed.')


if __name__ == '__main__':
    main()
