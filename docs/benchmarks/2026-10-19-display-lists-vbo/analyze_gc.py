"""Post-hoc Java 8 GC/safepoint logging companions; never performance-selection evidence."""
import argparse
import csv
import datetime
import importlib.util
import json
import re
import sys
from pathlib import Path

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('backend_analysis', HERE/'analyze.py')
base=importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)
PREFIX=re.compile(r'(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d+[+-]\d{4}): (\d+\.\d+): ')
STOP=re.compile(r'Total time for which application threads were stopped: ([\d.]+) seconds, Stopping threads took: ([\d.]+) seconds')


def overlap(a,b,c,d):
    return max(0,min(b,d)-max(a,c))


def events(path):
    gc=[]
    stopped=[]
    offsets=[]
    for line in path.read_text().splitlines():
        match=PREFIX.search(line)
        if not match: continue
        wall=datetime.datetime.fromisoformat(match[1]).timestamp()*1000
        uptime=float(match[2])*1000
        offsets.append(wall-uptime)
        stop=STOP.search(line)
        if stop:
            duration=float(stop[1])*1000
            stopped.append({'start_ms':wall-duration,'end_ms':wall,'duration_ms':duration,
                            'synchronization_ms':float(stop[2])*1000,'raw':line})
        elif re.search(r'\[(?:Full GC|GC) \(',line):
            durations=re.findall(r', ([\d.]+) secs\]',line)
            assert durations, line
            duration=float(durations[-1])*1000
            gc.append({'start_ms':wall,'end_ms':wall+duration,'duration_ms':duration,'raw':line})
    assert gc and stopped, ('No GC/safepoint events',path)
    spread=max(offsets)-min(offsets)
    assert spread<50, ('Large clock jump/log timestamp drift; alignment needs manual review',spread)
    return gc,stopped,spread


def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--prefix',default='bvgc')
    args=ap.parse_args()
    result={'description':'Post-hoc GC logging companions, excluded from performance selection. GC durations and total safepoint durations overlap; never add them. Safepoint total includes synchronization. Date stamps on stopped-time lines are emitted at safepoint end. Frame/log alignment assumes no clock jump and ordinary log timestamp/formatting latency.',
            'semantic_source':'https://github.com/openjdk/jdk8u/blob/master/hotspot/src/share/vm/services/runtimeService.cpp',
            'runs':{}}
    for backend in ['dl','vbo']:
        label=args.prefix+'-'+backend
        run=base.collect(label,backend=='vbo',False)
        env=run['environment']
        assert '-XX:+PrintGCApplicationStoppedTime' in env['jvm.args']
        log=Path('run',label+'.gc.log')
        gc,stopped,spread=events(log)
        epoch=int(env['profiler.epochWallMillis'])
        uncertainty=int(env['profiler.epochWallUncertaintyNanos'])/1e6+2+spread # anchor, log rounding and observed wall/uptime drift
        item={'summary':run,'gc_log_sha256':base.sha(log),'clock_allowance_ms':uncertainty,'wall_minus_uptime_spread_ms':spread,'all_gc_events':gc,'all_safepoints':stopped,'phases':{}}
        for phase in base.PHASES:
            with (Path(run['raw_directory'])/(phase+'.csv')).open() as f:
                rows=[{k:int(v) for k,v in r.items()} for r in csv.DictReader(f)]
            begin=epoch+rows[0]['start_ns']/1e6
            end=epoch+(rows[-1]['start_ns']+rows[-1]['frame_ns'])/1e6
            gcs=[g for g in gc if overlap(begin,end,g['start_ms'],g['end_ms'])>0]
            stops=[g for g in stopped if overlap(begin,end,g['start_ms'],g['end_ms'])>0]
            worst=[]
            for r in sorted(rows,key=lambda r:r['frame_ns'],reverse=True)[:10]:
                a=epoch+r['start_ns']/1e6
                b=a+r['frame_ns']/1e6
                worst.append({'start_ns':r['start_ns'],'frame_ms':r['frame_ns']/1e6,
                              'estimated_gc_overlap_ms':sum(overlap(a,b,g['start_ms'],g['end_ms']) for g in gcs),
                              'conservative_gc_overlap_ms':sum(overlap(a+uncertainty,b-uncertainty,g['start_ms'],g['end_ms']) for g in gcs),
                              'estimated_safepoint_overlap_ms':sum(overlap(a,b,g['start_ms'],g['end_ms']) for g in stops),
                              'conservative_safepoint_overlap_ms':sum(overlap(a+uncertainty,b-uncertainty,g['start_ms'],g['end_ms']) for g in stops),
                              'overlapping_safepoints':[g for g in stops if overlap(a,b,g['start_ms'],g['end_ms'])>0],
                              'chunk_upload_ms':r['chunk_upload_ns']/1e6})
            item['phases'][phase]={'gc_events':gcs,'safepoints':stops,'gc_duration_total_ms':sum(g['duration_ms'] for g in gcs),
                                   'safepoint_duration_total_ms':sum(g['duration_ms'] for g in stops),'worst_frames':worst}
        result['runs'][label]=item
        p=item['phases']['fly'];w=p['worst_frames'][0]
        print(label,'fly GC ms',round(p['gc_duration_total_ms'],3),'safepoint ms',round(p['safepoint_duration_total_ms'],3),
              'worst frame / conservative safepoint overlap ms',round(w['frame_ms'],3),round(w['conservative_safepoint_overlap_ms'],3))
    out=HERE if args.prefix=='bvgc' else HERE/'series'/args.prefix
    out.mkdir(parents=True,exist_ok=True)
    (out/'gc.json').write_text(json.dumps(result,indent=2)+'\n')


if __name__=='__main__': main()
