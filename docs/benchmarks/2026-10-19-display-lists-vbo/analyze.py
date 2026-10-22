"""Validate and reproduce Session 14 from raw CSVs; never executes old archive generators."""
import argparse
import csv
import hashlib
import json
import math
import statistics as st
from pathlib import Path

HERE = Path(__file__).resolve().parent
RAW = Path('run/alacer/benchmarks')
PHASES = {'warmup-pan':15, 'warmup-sort':16, 'steady':10, 'pan':15, 'sort':16, 'fly':20}
SCORED = ['steady', 'pan', 'sort', 'fly']
LAYERS = ['terrain_solid', 'terrain_cutout_mipped', 'terrain_cutout', 'terrain_translucent']
DEFAULTS = ['fastChunkVisibility', 'fastEntityChunkLookup', 'throttleGlErrorChecks', 'avoidMatrixReads', 'cacheClouds', 'fastEntityShadows']
EXPERIMENTS = ['fastChunkFrustum', 'skipZeroTranslates', 'batchModelLists']
TOP = ['terrain_setup','chunk_upload','entities','camera_setup','frustum','frustum_culler','lightmap','pick','sky','clouds',*LAYERS,'particles','weather','hand','gui']


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def quantile(a, q):
    return sorted(a)[max(0, math.ceil(len(a)*q)-1)]


def collect(label, vbo, detail, scale=1):
    paths = list(RAW.glob('*-'+label+'-*'))
    assert len(paths) == 1, (label, paths)
    path = paths[0]
    s = json.loads((path/'summary.json').read_text())
    env = s['environment']
    backend = str(vbo).lower()
    expected = {'java.version':'1.8.0_492','window':'1920x1080','options.renderDistance':'12',
                'options.fancyGraphics':'true','options.smoothLighting':'2','options.mipmapLevels':'4',
                'options.clouds':'2','options.particles':'0','options.entityShadows':'true',
                'options.vsync':'false','options.fpsLimit':'260','options.useVbo':backend,'vboActive':backend,
                'benchmark.generationRun':'false','benchmark.anchor':'-200.5 65.0 267.5',
                'benchmark.mobs':'0','benchmark.chests':'0','benchmark.backendScenario':'true','benchmark.glassBlocks':'364'}
    for k,v in expected.items():
        assert env[k] == v, (label,k,env[k],v)
    assert 'i7-12700F' in env['cpu'] and 'RTX 3060 Ti' in env['gl.renderer']
    assert env['gl.version'] == '4.6.0 NVIDIA 616.92'
    assert '-Xmx2G' in env['jvm.args'] and 'FlightRecording' not in env['jvm.args']
    if not detail:
        assert 'benchmark.screenshots=true' not in env['jvm.args']
    logpath = Path('build',label+'.log')
    log = logpath.read_text(errors='replace')
    assert 'BUILD SUCCESSFUL' in log and 'Benchmark: results written' in log
    assert '364 stained-glass blocks' in log
    for bad in ['Benchmark aborted','Mixin apply failed','InjectionError','OpenGL Error','Chunk render task was',
                'Exception in thread','ClassCastException','Matrix read mismatch','falling back to OpenGL',
                'reading matrices from OpenGL every frame again','Could not execute task']:
        assert bad not in log, (label,bad)
    result = {'raw_directory':str(path),'environment':env,'log_sha256':sha(logpath),'phases':{}}
    assert set(s['phases']) == set(PHASES)
    for name, seconds in PHASES.items():
        p = s['phases'][name]
        c = p['config']
        assert p['dropped_frames']==0 and p['discarded_setup_frames']==1
        assert p['target_seconds']==seconds*scale
        assert seconds*scale-.03 <= p['recorded_wall_seconds'] < seconds*scale+1
        assert p['settle_s'] < 90, (label,name,'settle timeout')
        for key in DEFAULTS: assert c['render.'+key]=='true'
        for key in EXPERIMENTS: assert c['render.'+key]=='false'
        assert all(v=='false' for k,v in c.items() if k.endswith('.verify'))
        for key in ['entityDetail','shadowTiming','frustumTiming','hud']: assert c['profiler.'+key]=='false'
        assert c['profiler.enabled']=='true' and c['profiler.chunkDetail']==str(detail).lower()
        assert c['options.useVbo']==c['vboActive']==backend
        quality={'options.fov':'70.0','options.gamma':'0.0','options.anaglyph':'false','options.viewBobbing':'true'}
        for k in ['options.fancyGraphics','options.smoothLighting','options.mipmapLevels','options.clouds',
                  'options.particles','options.renderDistance','options.vsync','options.fpsLimit','options.entityShadows']:
            quality[k]=expected[k]
        for k,v in quality.items(): assert c[k]==v, (label,name,k,c[k],v)
        clazz = 'RenderChunk' if vbo else 'GlListRenderChunk'
        assert c['backend.renderChunkClass']=='net.minecraft.client.render.world.'+clazz
        csvpath = path/(name+'.csv')
        with csvpath.open(newline='') as f:
            rows = [{k:int(v) for k,v in r.items()} for r in csv.DictReader(f)]
        n = len(rows)
        assert n == p['frames'] and n>0
        total = sum(r['frame_ns'] for r in rows)
        assert abs(total/1e9-p['recorded_wall_seconds'])<.03
        assert abs(n*1e9/total-p['avg_fps'])<.02
        assert all(a['start_ns']+a['frame_ns']==b['start_ns'] for a,b in zip(rows,rows[1:]))
        assert all(r['alloc_bytes']>=0 for r in rows)
        frames=[r['frame_ns'] for r in rows]
        assert abs(quantile(frames,.99)/1e6-p['frame_p99_ms'])<.00011
        assert abs(max(frames)/1e6-p['frame_max_ms'])<.00011
        assert abs(st.mean(r['alloc_bytes'] for r in rows)-p['alloc_mean_bytes'])<.001
        if not detail:
            diagnostic = [k for k in rows[0] if (k.startswith('chunk_') and k not in ['chunk_upload_ns','chunk_matrix_ns','chunk_matrix_reads']) or k=='terrain_submit_ns' or k.endswith('_chunks') and k.startswith('terrain_')]
            assert all(r[k]==0 for r in rows for k in diagnostic), (label,'detail leaked')
        else:
            opposite = 'chunk_gl_list_calls' if vbo else 'chunk_vbo_calls'
            assert all(r[opposite]==0 for r in rows), (label,'wrong backend uploads')
            assert sum(r['terrain_submit_ns'] for r in rows)>0
            if name=='sort': assert sum(r['chunk_resort_worker_calls'] for r in rows)>0
        m = dict(p)
        m.update(csv_sha256=sha(csvpath), mean_ms=st.mean(frames)/1e6,
                 client_allocation_bytes_per_second=sum(r['alloc_bytes'] for r in rows)/(total/1e9),
                 slowest_1pct_mean_ms=st.mean(sorted(frames)[-max(1,math.floor(n*.01)):])/1e6,
                 pending_max=max(r['pending_chunks'] for r in rows),
                 pending_time_weighted=sum(r['pending_chunks']*r['frame_ns'] for r in rows)/total,
                 pending_p99=quantile([r['pending_chunks'] for r in rows],.99),
                 terrain_layers_mean_ms=sum(st.mean(r[l+'_ns'] for r in rows) for l in LAYERS)/1e6,
                 render_residual_mean_ms=st.mean(r['render_ns']-sum(r[t+'_ns'] for t in TOP) for r in rows)/1e6)
        for threshold,ms in [('8ms',8),('16_67ms',1000/60),('33_33ms',1000/30)]:
            count=sum(t>ms*1e6 for t in frames)
            m['frames_over_'+threshold]=count
            m['fraction_over_'+threshold]=count/n
            m['per_second_over_'+threshold]=count/(total/1e9)
        m['worst_frames'] = [{k:v for k,v in r.items() if k in ['start_ns','frame_ns','alloc_bytes','pending_chunks'] or k.endswith('_ns')}
                             for r in sorted(rows,key=lambda r:r['frame_ns'],reverse=True)[:10]]
        if detail:
            m['completed_intervals']={}
            for ns,count in [('chunk_build_worker_ns','chunk_build_worker_calls'),('chunk_resort_worker_ns','chunk_resort_worker_calls'),
                             ('chunk_queue_wait_ns','chunk_task_starts'),('chunk_upload_latency_ns','chunk_task_completions'),
                             ('chunk_buffer_wait_ns','chunk_buffer_waits'),('chunk_schedule_wait_ns','chunk_observed_schedules'),
                             ('chunk_gl_list_ns','chunk_gl_list_calls'),('chunk_vbo_ns','chunk_vbo_calls')]:
                calls=sum(r[count] for r in rows)
                ns_total=sum(r[ns] for r in rows)
                m['completed_intervals'][ns]={'calls':calls,'total_ms':ns_total/1e6,'mean_per_call_ms':ns_total/max(1,calls)/1e6}
                m['completed_intervals'][ns]['calls_per_second']=calls/(total/1e9)
            m['time_weighted_layer_chunks']={l:sum(r[l+'_chunks']*r['frame_ns'] for r in rows)/total for l in LAYERS}
            m['time_weighted_gauges']={k:sum(r[k]*r['frame_ns'] for r in rows)/total for k in ['chunk_dirty','chunk_queued','chunk_ready_uploads','chunk_available_buffers']}
            m['observed_dirty_age_mean_ms']=sum(r['chunk_dirty_age_ns'] for r in rows)/max(1,sum(r['chunk_dirty'] for r in rows))/1e6
            m['observed_dirty_age_max_ms']=max(r['chunk_dirty_age_max_ns'] for r in rows)/1e6
        result['phases'][name]=m
    (HERE/'runs').mkdir(exist_ok=True)
    (HERE/'runs'/(label+'.summary.json')).write_bytes((path/'summary.json').read_bytes())
    return result


def compare(runs,prefix):
    result={}
    keys=['mean_ms','client_thread_cpu_ms_per_frame','alloc_mean_bytes','client_allocation_bytes_per_second','frame_p99_ms','frame_p99_9_ms',
          'slowest_1pct_mean_ms','frame_max_ms','terrain_layers_mean_ms','chunk_upload_mean_ms','swap_mean_ms','sky_mean_ms','pending_time_weighted']
    for phase in SCORED:
        pairs=[]
        for i in range(1,5):
            a=runs[f'{prefix}-dl-r{i}']['phases'][phase]
            b=runs[f'{prefix}-vbo-r{i}']['phases'][phase]
            pairs.append({'round':i,'vbo_change_percent':{k:100*(b[k]/a[k]-1) if a[k] else None for k in keys}})
        result[phase]={'pairs':pairs,'median_vbo_change_percent':{},'vbo_lower_pairs':{},'backend_ranges':{}}
        for key in keys:
            changes=[p['vbo_change_percent'][key] for p in pairs if p['vbo_change_percent'][key] is not None]
            result[phase]['median_vbo_change_percent'][key]=st.median(changes) if changes else None
            result[phase]['vbo_lower_pairs'][key]=sum(x<0 for x in changes)
        for backend in ['dl','vbo']:
            values=[runs[f'{prefix}-{backend}-r{i}']['phases'][phase] for i in range(1,5)]
            result[phase]['backend_ranges'][backend]={k:{'min':min(v[k] for v in values),'max':max(v[k] for v in values),'median':st.median(v[k] for v in values)} for k in keys}
        result[phase]['same_backend_drift_r4_vs_r1_percent']={}
        for backend in ['dl','vbo']:
            first=runs[f'{prefix}-{backend}-r1']['phases'][phase]
            last=runs[f'{prefix}-{backend}-r4']['phases'][phase]
            result[phase]['same_backend_drift_r4_vs_r1_percent'][backend]={k:100*(last[k]/first[k]-1) if first[k] else None for k in keys}
        med=result[phase]['median_vbo_change_percent']
        result[phase]['vbo_frame_cpu_gate']=all(med[k]<=-5 and result[phase]['vbo_lower_pairs'][k]>=3 for k in ['mean_ms','client_thread_cpu_ms_per_frame'])
        result[phase]['vbo_tail_gate']=all(med[k]<=10 for k in ['frame_p99_ms','slowest_1pct_mean_ms'])
    return result


def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--prefix',default='bvs')
    ap.add_argument('--smoke',action='store_true')
    ap.add_argument('--controls-only',action='store_true',help='Validate eight controls without requiring the subsequent diagnostics')
    args=ap.parse_args()
    base=Path('run/saves/alacer-benchmark')
    assert {p.relative_to(base).as_posix():sha(p) for p in base.rglob('*') if p.is_file()}==json.loads((HERE/'world.json').read_text()),'base changed'
    initial=json.loads((HERE/'initial.json').read_text())['sha256']
    for name,value in initial.items():
        if name.startswith(('docs/benchmarks/2026-10-19-chunk-frustum/','docs/benchmarks/2026-10-19-chunk-pipeline/')):
            assert sha(Path(name))==value,('historical archive changed',name)
    if args.smoke:
        runs={f'{args.prefix}-{b}-smoke':collect(f'{args.prefix}-{b}-smoke',b=='vbo',True,.25) for b in ['dl','vbo']}
        (HERE/f'smoke-{args.prefix}.json').write_text(json.dumps(runs,indent=2)+'\n')
        print('Validated corrected diagnostic smoke, both actual Java 8 backends; excluded from selection')
        return
    frozen=json.loads((HERE/'implementation.json').read_text())['sha256']
    for name,value in frozen.items(): assert sha(Path(name))==value,('frozen implementation changed',name)
    runs={}
    for b,i in [('dl',1),('vbo',1),('vbo',2),('dl',2),('vbo',3),('dl',3),('dl',4),('vbo',4)]:
        label=f'{args.prefix}-{b}-r{i}'
        runs[label]=collect(label,b=='vbo',False)
    if args.controls_only:
        out = HERE if args.prefix=='bvs' else HERE/'series'/args.prefix
        out.mkdir(parents=True,exist_ok=True)
        comparisons=compare(runs,args.prefix)
        (out/'controls.json').write_text(json.dumps({'controls':runs,'comparison':comparisons},indent=2)+'\n')
        print('Validated all eight controls; diagnostics not used for performance selection')
        for phase,c in comparisons.items(): print(phase,c['median_vbo_change_percent'])
        return
    diagnostics={}
    for b,i in [('dl',1),('vbo',1),('vbo',2),('dl',2)]:
        label=f'{args.prefix}-{b}-detail{i}'
        diagnostics[label]=collect(label,b=='vbo',True)
    comparisons=compare(runs,args.prefix)
    out = HERE if args.prefix=='bvs' else HERE/'series'/args.prefix
    out.mkdir(parents=True,exist_ok=True)
    (out/'metrics.json').write_text(json.dumps({'controls':runs,'diagnostics':diagnostics,'comparison':comparisons},indent=2)+'\n')
    lines=['# Session 14 measurements','','DL = display lists; VBO = vanilla VBO setting. Diagnostic launches are excluded from selection.',
           '','| Run | Phase | Mean ms | p99 ms | Max ms | Slowest 1% ms | Client CPU ms/frame | Allocation B/frame | Terrain ms | Upload ms | Pending mean/max |',
           '|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|']
    for label,run in {**runs,**diagnostics}.items():
        for phase in SCORED:
            p=run['phases'][phase]
            lines.append(f"| {label} | {phase} | {p['mean_ms']:.3f} | {p['frame_p99_ms']:.3f} | {p['frame_max_ms']:.3f} | {p['slowest_1pct_mean_ms']:.3f} | {p['client_thread_cpu_ms_per_frame']:.3f} | {p['alloc_mean_bytes']:.0f} | {p['terrain_layers_mean_ms']:.3f} | {p['chunk_upload_mean_ms']:.3f} | {p['pending_chunks_mean']:.1f}/{p['pending_max']} |")
    lines += ['','## Paired VBO change (negative means lower cost)','','| Phase | Mean % | Client CPU % | p99 % | Slowest 1% % | Allocation % | Terrain % | Mean/CPU gate | Tails gate |',
              '|---|---:|---:|---:|---:|---:|---:|---|---|']
    for phase,c in comparisons.items():
        m=c['median_vbo_change_percent']
        values=' | '.join(f'{m[k]:+.2f}' for k in ['mean_ms','client_thread_cpu_ms_per_frame','frame_p99_ms','slowest_1pct_mean_ms','alloc_mean_bytes','terrain_layers_mean_ms'])
        lines.append(f"| {phase} | {values} | {c['vbo_frame_cpu_gate']} | {c['vbo_tail_gate']} |")
    (out/'measurements.md').write_text('\n'.join(lines)+'\n')
    print('Validated eight full controls, four full diagnostics, all CSV/config/backend/log gates; reproduced metrics and paired comparisons')
    for phase,c in comparisons.items(): print(phase, c['median_vbo_change_percent'])


if __name__=='__main__': main()
