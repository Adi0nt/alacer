"""Session 27: hash-checked ordinary CSV sections, frame/tick tails and separate intrusive GPU attribution."""
from array import array
import csv
import hashlib
import json
import math
from pathlib import Path
import statistics as st
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / 'tools'))
import run_chunk_upload as runner


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def write(name, value):
    (HERE / name).write_bytes((json.dumps(value, indent=2)+'\n').encode())


def percentile(values, q):
    return sorted(values)[max(0, math.ceil(len(values)*q)-1)]


def phase(folder, name, summary, detail):
    with (folder / (name+'.csv')).open(newline='') as f:
        rd = csv.DictReader(f)
        cols = {k: array('q') for k in rd.fieldnames}
        for r in rd:
            for k, v in r.items():
                cols[k].append(int(v))
    n = len(cols['frame_ns'])
    assert n == summary['frames'] and n > 0
    starts, frames = cols['start_ns'], cols['frame_ns']
    for i in range(n-1):
        assert starts[i] + frames[i] == starts[i+1], (name, i, 'clock continuity')
    seconds = sum(frames)/1e9
    assert abs(seconds-summary['recorded_wall_seconds']) < .05
    assert abs(n/seconds-summary['avg_fps']) < .05
    ranked = sorted(range(n), key=lambda i: frames[i], reverse=True)
    tick = [i for i in range(n) if cols['tick_ns'][i] > 0]
    assert tick, 'no tick frames'
    groups = {'all': range(n), 'tick': tick, 'nontick': [i for i in range(n) if cols['tick_ns'][i] == 0],
              'slow99': ranked[:math.ceil(n*.01)], 'slow95': ranked[:math.ceil(n*.05)],
              'tick_slow99': sorted(tick, key=lambda i: frames[i], reverse=True)[:math.ceil(len(tick)*.01)]}
    result = {'frames': n, 'csv_sha256': sha(folder / (name+'.csv')), 'seconds': seconds, 'groups': {},
              'frame_mean_ms': sum(frames)/n/1e6, 'frame_p95_ms': percentile(frames,.95)/1e6,
              'frame_p99_ms': percentile(frames,.99)/1e6, 'frame_max_ms': max(frames)/1e6,
              'tick_frame_p99_ms': percentile([frames[i] for i in tick],.99)/1e6,
              'cpu_ms_per_frame': summary['client_thread_cpu_ms_per_frame'],
              'process_cpu_ms_per_frame': summary['process_cpu_cores']*seconds*1000/n,
              'allocation_bytes_per_frame': sum(cols['alloc_bytes'])/n,
              'allocation_bytes_per_second': sum(cols['alloc_bytes'])/seconds,
              'over_8ms': sum(x > 8e6 for x in frames), 'over_16_67ms': sum(x > 1e9/60 for x in frames)}
    for group, indices in groups.items():
        entry = {'frames': len(indices), 'sections_ms': {k[:-3]: sum(v[i] for i in indices)/len(indices)/1e6
                 for k, v in cols.items() if k.endswith('_ns') and k != 'start_ns'},
                 'allocation_bytes': sum(cols['alloc_bytes'][i] for i in indices)/len(indices),
                 'gauges': {k: sum(v[i] for i in indices)/len(indices)
                            for k, v in cols.items() if not k.endswith('_ns') and k != 'alloc_bytes'}}
        entry['upload_fraction'] = entry['sections_ms']['chunk_upload']/entry['sections_ms']['frame']
        result['groups'][group] = entry
    # Time bins distinguish initially prebuilt view-distance chunks from sustained fresh terrain.
    result['bins'] = []
    for b in range(math.ceil(seconds/10)):
        ids = [i for i in range(n) if b*10e9 <= starts[i]-starts[0] < (b+1)*10e9]
        if not ids:
            continue
        tail = sorted(ids, key=lambda i: frames[i], reverse=True)[:math.ceil(len(ids)*.01)]
        result['bins'].append({'from_second': b*10, 'frames': len(ids),
                               'frame_ms': st.mean(frames[i] for i in ids)/1e6,
                               'upload_ms': st.mean(cols['chunk_upload_ns'][i] for i in ids)/1e6,
                               'slow99_frame_ms': st.mean(frames[i] for i in tail)/1e6,
                               'slow99_upload_ms': st.mean(cols['chunk_upload_ns'][i] for i in tail)/1e6})
    result['worst_frames'] = [{k: v[i] for k, v in cols.items()} for i in ranked[:20]]
    result['completed_intervals'] = {}
    if detail:
        for ns, count in (('chunk_gl_list_ns','chunk_gl_list_calls'), ('chunk_vbo_ns','chunk_vbo_calls'),
                          ('chunk_build_worker_ns','chunk_build_worker_calls'), ('chunk_build_client_ns','chunk_build_client_calls'),
                          ('chunk_queue_wait_ns','chunk_task_starts'), ('chunk_upload_latency_ns','chunk_task_completions'),
                          ('chunk_buffer_wait_ns','chunk_buffer_waits'), ('chunk_schedule_wait_ns','chunk_observed_schedules')):
            calls = sum(cols[count])
            result['completed_intervals'][ns[:-3]] = {'calls': calls, 'calls_per_second': calls/seconds,
                                                     'mean_per_call_ms': sum(cols[ns])/max(1,calls)/1e6}
        result['deadline_expired_fraction'] = sum(cols['chunk_expired_deadlines'])/n
        result['dirty_age_mean_ms'] = sum(cols['chunk_dirty_age_ns'])/max(1,sum(cols['chunk_dirty']))/1e6
        result['dirty_age_max_ms'] = max(cols['chunk_dirty_age_max_ns'])/1e6
        result['queue_full_fraction'] = sum(x == 100 for x in cols['chunk_queued'])/n
        result['buffers_empty_fraction'] = sum(x == 0 for x in cols['chunk_available_buffers'])/n
        result['gl_list_to_upload_ratio'] = sum(cols['chunk_gl_list_ns'])/max(1,sum(cols['chunk_upload_ns']))
    else:
        # Matrix capture belongs to the existing shipped optimization, not intrusive chunk attribution.
        assert all(not any(v) for k, v in cols.items() if k.startswith('chunk_')
                   and k not in ('chunk_upload_ns', 'chunk_matrix_ns', 'chunk_matrix_reads'))
    return result


def gpu(folder):
    metadata = json.loads((folder/'chunk-upload-gpu.json').read_bytes())
    with (folder/'chunk-upload-gpu.csv').open() as f:
        rows = list(csv.DictReader(f))
    assert len(rows) == metadata['issued']
    out = {'metadata': metadata, 'phases': {}}
    for name in runner_jobs_phases():
        complete = [r for r in rows if r['phase'] == name and int(r['command_stream_ns']) >= 0]
        by_work = {}
        for group, ids in (('all', complete), ('nonempty', [r for r in complete if r['nonempty'] == '1']),
                           ('empty', [r for r in complete if r['nonempty'] == '0'])):
            if not ids:
                by_work[group] = {'samples': 0}; continue
            host = [int(r['host_ns'])/1e6 for r in ids]
            command = [int(r['command_stream_ns'])/1e6 for r in ids]
            by_work[group] = {'samples': len(ids), 'host_mean_ms': st.mean(host), 'host_p99_ms': percentile(host,.99),
                             'command_stream_mean_ms': st.mean(command), 'command_stream_p99_ms': percentile(command,.99)}
        out['phases'][name] = by_work
    return out


def runner_jobs_phases():
    return ('warmup-pan', 'still', 'fresh-flight')


def main():
    m = json.loads((HERE/'manifest.json').read_bytes())
    runner.check(m)
    snapshot = m['source_snapshot']
    with zipfile.ZipFile(ROOT/snapshot['path']) as z:
        for name, digest in m['source_sha256'].items():
            assert hashlib.sha256(z.read(name.replace('\\','/'))).hexdigest() == digest
    records = []
    for p in sorted((HERE/'runs').glob('*.record.json')):
        r = json.loads(p.read_bytes())
        assert r['source_snapshot'] == snapshot and r['frozen_manifest'] and not r['smoke']
        assert r['base_world_sha256'] == m['base_world_sha256']
        assert r['source_sha256'] == m['source_sha256']
        folder = ROOT/r['source']
        attempt = json.loads((HERE/'attempts'/(r['label']+'.json')).read_bytes())
        assert attempt['status'] == 'passed'
        assert sha(ROOT/attempt['log']) == attempt['log_sha256']
        for name, digest in r['files_sha256'].items():
            assert sha(folder/name) == digest
        log = (ROOT/attempt['log']).read_text(encoding='utf-8',errors='replace')
        summary, _ = runner.validate(folder,r,log,runner.options(r))
        assert Path(r['launcher']['ExecutablePath']).resolve() == (runner.JDK/'bin/java.exe').resolve()
        row = {'label': r['label'], 'scene': r['scene'], 'detail': r['detail'],
               'phases': {name: phase(folder,name,summary['phases'][name],r['detail']) for name in runner_jobs_phases()}}
        if r['detail']:
            row['gpu'] = gpu(folder)
        records.append(row)
    expected = {j['label'] for j in runner.jobs()}
    assert {r['label'] for r in records} == expected, 'missing planned launches'
    scenes = {}
    for scene in ('near','high'):
        controls = [r['phases']['fresh-flight'] for r in records if r['scene']==scene and not r['detail']]
        diagnostics = [r['phases']['fresh-flight'] for r in records if r['scene']==scene and r['detail']]
        mean_share = [p['groups']['all']['upload_fraction'] for p in controls]
        tail_share = [p['groups']['slow99']['upload_fraction'] for p in controls]
        tail_ms = [p['groups']['slow99']['sections_ms']['chunk_upload'] for p in controls]
        signal = sum(a >= .10 or (b >= .20 and c >= .5) for a,b,c in zip(mean_share,tail_share,tail_ms)) >= 2
        dc = st.median([p['groups']['all']['sections_ms']['chunk_upload']/p['frame_mean_ms'] for p in diagnostics])
        cc = st.median(mean_share)
        corroboration = all(p['gl_list_to_upload_ratio'] >= .70 for p in diagnostics) and abs(dc/cc-1) <= .25
        scenes[scene] = {'controls':3,'diagnostics':2, 'median_mean_upload_fraction':st.median(mean_share),
                         'median_slow99_upload_fraction':st.median(tail_share),'median_slow99_upload_ms':st.median(tail_ms),
                         'large_region_rule':signal, 'diagnostic_upload_fraction_relative_change': dc/cc-1,
                         'list_compilation_corroborated':corroboration,
                         'region_eligible':signal and corroboration,
                         'note':'Region eligibility alone never names a transformation or permits implementation.'}
    result = {'session':27,'source_snapshot':snapshot,'runs':records,'scenes':scenes}
    write('metrics.json',result)
    lines = ['# Session 27 measurements','', 'Ordinary-profiler controls select regions; intrusive diagnostics are separate. Warmup is archived but unranked.', '',
             '| Launch | Phase | Mean ms | p99 ms | Slow-1% ms | Upload mean ms | Upload slow-1% ms | Tick p99 ms | CPU ms/frame | Alloc B/frame |',
             '|---|---|---:|---:|---:|---:|---:|---:|---:|---:|']
    for r in records:
        for name,p in r['phases'].items():
            lines.append(f"| {r['label']} | {name} | {p['frame_mean_ms']:.3f} | {p['frame_p99_ms']:.3f} | "
                         f"{p['groups']['slow99']['sections_ms']['frame']:.3f} | {p['groups']['all']['sections_ms']['chunk_upload']:.3f} | "
                         f"{p['groups']['slow99']['sections_ms']['chunk_upload']:.3f} | {p['tick_frame_p99_ms']:.3f} | "
                         f"{p['cpu_ms_per_frame']:.3f} | {p['allocation_bytes_per_frame']:.0f} |")
    lines += ['', 'Full CSV groups (all, tick, nontick, slow95, slow99, tick_slow99), 10 s bins, worst frames, '
              'queue gauges, completed intervals, CPU, allocation/second and sparse GPU distributions are in metrics.json.', '']
    (HERE/'results.md').write_bytes('\n'.join(lines).encode())
    print(json.dumps(scenes,indent=2))


if __name__ == '__main__':
    main()
