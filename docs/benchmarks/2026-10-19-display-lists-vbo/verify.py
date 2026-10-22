"""Freeze final local validation, decision and Java 8 baseline identities after collection."""
import hashlib
import json
import statistics
import struct
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

HERE=Path(__file__).resolve().parent


def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def write(name,value): (HERE/name).write_text(json.dumps(value,indent=2)+'\n')


def main():
    implementation=json.loads((HERE/'implementation.json').read_text())
    for name,value in implementation['sha256'].items(): assert sha(Path(name))==value, name
    initial=json.loads((HERE/'initial.json').read_text())['sha256']
    historical=[n for n in initial if n.startswith(('docs/benchmarks/2026-10-19-chunk-frustum/','docs/benchmarks/2026-10-19-chunk-pipeline/'))]
    assert all(sha(Path(n))==initial[n] for n in historical)
    base=Path('run/saves/alacer-benchmark')
    world={p.relative_to(base).as_posix():sha(p) for p in base.rglob('*') if p.is_file()}
    assert world==json.loads((HERE/'world.json').read_text())
    suites={p.name:{k:int(ET.parse(p).getroot().attrib[k]) for k in ['tests','failures','errors','skipped']} for p in Path('build/test-results/test').glob('TEST-*.xml')}
    totals={k:sum(v[k] for v in suites.values()) for k in ['tests','failures','errors','skipped']}
    assert totals=={'tests':47,'failures':0,'errors':0,'skipped':0},totals
    artifacts={}
    for p in Path('build/libs').glob('*.jar'):
        if 'sources' in p.name: continue
        with zipfile.ZipFile(p) as z:
            majors={struct.unpack('>H',z.read(n)[6:8])[0] for n in z.namelist() if n.startswith('net/adiont/alacer/') and n.endswith('.class')}
        assert majors=={52},(p,majors)
        artifacts[p.as_posix()]={'sha256':sha(p),'project_class_major_versions':sorted(majors)}
    assert artifacts
    images={}
    for backend in ['dl','vbo']:
        for phase in ['steady','sort','fly']:
            p=Path(f'run/screenshots/alacer-bvs-{backend}-detail2-vis_on-ecl_on-ent0-{phase}.png')
            data=p.read_bytes()
            assert struct.unpack('>II',data[16:24])==(1920,1080)
            images[p.as_posix()]={'sha256':sha(p),'size':[1920,1080]}
    # Benchmark-only settings must not persist into ordinary play.
    for name in ['run/options.txt','run/config/alacer.properties']:
        assert sha(Path(name))==initial[name], ('User settings changed',name)
    verification={'test_command':'.\\gradlew.bat test build --console=plain','executed_build_log':'build/s14-final-build.log',
                  'executed_build_log_sha256':sha(Path('build/s14-final-build.log')),
                  'final_up_to_date_build_log':'build/s14-verified-build.log','final_build_log_sha256':sha(Path('build/s14-verified-build.log')),
                  'runtime_boundary':'Tests/Gradle on configured JDK 25; actual game launches on Temurin 8u492; Java 25 game untested.',
                  'test_totals':totals,'test_suites':suites,'artifacts':artifacts,
                  'protected_historical_files_unchanged':len(historical),'base_world_files_unchanged':len(world),
                  'user_options_and_mod_config_byte_identical_to_initial':True,
                  'visually_inspected_images':images,
                  'visual_finding':'Steady, sort and fly endpoints viewed in both backends; no obvious geometry/foliage/water/cloud/transparency defect in those views. White glass is a limited ordering oracle; no pixel identity or arbitrary-mod certification.',
                  'full_launch_validation_logs':{'build/s14-final-validation.log':sha(Path('build/s14-final-validation.log')),'build/s14-gc-validation.log':sha(Path('build/s14-gc-validation.log'))}}
    write('verification.json',verification)
    metrics=json.loads((HERE/'metrics.json').read_text())
    companions=json.loads((HERE/'gc.json').read_text())['runs']
    assert len(metrics['controls'])==8 and len(metrics['diagnostics'])==4 and len(companions)==2
    queue=[]
    for i in [1,2]:
        a=metrics['diagnostics'][f'bvs-dl-detail{i}']['phases']['fly']['completed_intervals']['chunk_queue_wait_ns']
        b=metrics['diagnostics'][f'bvs-vbo-detail{i}']['phases']['fly']['completed_intervals']['chunk_queue_wait_ns']
        queue.append(100*(b['mean_per_call_ms']/a['mean_per_call_ms']-1))
    decision={'throughput_frame_cpu_gate_all_phases':all(c['vbo_frame_cpu_gate'] for c in metrics['comparison'].values()),
              'summary_tail_gate_all_phases':all(c['vbo_tail_gate'] for c in metrics['comparison'].values()),
              'full_responsiveness_gate':'Not cleared as an unqualified recommendation: queue-start latency regresses >10% at different task rates; exact visible-update latency is unavailable.',
              'diagnostic_fly_queue_start_change_percent':queue,'median_diagnostic_fly_queue_start_change_percent':statistics.median(queue),
              'fly_maximum_worse_for_vbo_pairs':4,
              'allocation_rate':'Client bytes/frame improve, client bytes/second regress in every phase. No allocation-rate optimization claimed.',
              'setting_result':'Vanilla VBO is a measured throughput preference for this machine/scenario, with tail/allocation/responsiveness tradeoffs. No universal smoothness claim.',
              'production_algorithm_default_or_backend_forcing_change_selected':False,
              'next_renderer_scenario':'Particles, independently designed; Java 25 game runtime/profile investigation remains separate and unverified.'}
    assert decision['throughput_frame_cpu_gate_all_phases'] and decision['summary_tail_gate_all_phases']
    write('decision.json',decision)
    reference=metrics['controls']['bvs-dl-r1']
    config=reference['phases']['steady']['config']
    baseline={'description':'Instrumented Java 8 renderer comparison baseline. Both backends frozen; VBO throughput preference is qualified, not a new global default or universal latency recommendation.',
              'game_runtime':'Temurin 1.8.0_492','bytecode_major':52,'build_and_tests':'Configured JDK 25',
              'implementation_manifest_sha256':sha(HERE/'implementation.json'),'world_manifest_sha256':sha(HERE/'world.json'),
              'metrics_sha256':sha(HERE/'metrics.json'),'controls_sha256':sha(HERE/'controls.json'),'gc_evidence_sha256':sha(HERE/'gc.json'),
              'verification_sha256':sha(HERE/'verification.json'),'decision_sha256':sha(HERE/'decision.json'),
              'artifacts':artifacts,'reference_environment':reference['environment'],
              'common_render_profiler_options':{k:v for k,v in config.items() if k.startswith(('render.','profiler.'))},
              'visual_options':{k:v for k,v in config.items() if k.startswith('options.') and k!='options.useVbo'},
              'profiles':{b:{'benchmark.vbo':str(b=='vbo').lower(),'control_labels':[f'bvs-{b}-r{i}' for i in range(1,5)]} for b in ['dl','vbo']},
              'recorder_primitive_column_capacity_bytes':190000*111*8,
              'java25_game_runtime_compatibility_verified':False}
    write('java8-baseline.json',baseline)
    print('Verified 47 tests, Java 8 classfiles, 14 full launches, six inspected images, protected archives/world/settings; froze both backend baselines')


if __name__=='__main__': main()
