"""Session 24: validate frozen evidence and report predeclared startup equivalence warnings."""
import csv, hashlib, importlib.util, json, statistics as stats, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('light_metrics',ROOT/'docs/benchmarks/2026-10-20-particle-light-confirmation/analyze.py')
helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 m=json.loads((HERE/'manifest.json').read_text()); snapshot=m['source_snapshot']
 assert sha(ROOT/snapshot['path'])==snapshot['sha256']
 with zipfile.ZipFile(ROOT/snapshot['path']) as z:
  for name,h in m['source_sha256'].items(): assert hashlib.sha256(z.read(name.replace(chr(92),'/'))).hexdigest()==h
 records=[]; census=[]; values=[]
 for p in sorted((HERE/'runs').glob('*.record.json')):
  r=json.loads(p.read_text()); folder=ROOT/r['source']
  assert r['source_snapshot']==snapshot and r['frozen_manifest']
  assert r['base_world_sha256']==m['base_world_sha256']
  assert sha(ROOT/r['log'])==r['log_sha256']
  assert Path(r['launcher']['ExecutablePath']).resolve()==Path('C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/java.exe').resolve()
  if 'jfr' in r:assert sha(ROOT/r['jfr'])==r['jfr_sha256']
  for name,h in r['files_sha256'].items():assert sha(folder/name)==h
  assert r['shared_config_before_sha256']==r['shared_config_after_sha256']=='694ff84d8e5672cd7a2ec0ee1b5a3e4eadbe09552d5d65eef93e16f61ceacd31'
  summary=json.loads((folder/'summary.json').read_text())
  for phase,c in r['phase_configs'].items():assert json.loads((folder/(phase+'.json')).read_text())['config']==c==summary['phases'][phase]['config']
  records.append(r)
  if r['kind'] in ('census','inactive'):
   census.append({'label':r['label'],'scale':r['scale'],'kind':r['kind'],'census':json.loads((folder/'particle-movement-census.json').read_text())})
  for phase in ('static','pan'):
   rows=helper.load_rows(folder,phase);d=helper.metrics(rows);s=summary['phases'][phase]
   d['client_cpu_ms_per_frame']=s['client_thread_cpu_ms_per_frame']
   d['process_cpu_ms_per_frame']=s['process_cpu_cores']*s['recorded_wall_seconds']*1000/s['frames']
   d['tick_frame_alloc_bytes']=stats.mean(x['alloc_bytes'] for x in rows if x['particles_ticked']>0)
   d['ordinary_alloc_bytes']=stats.mean(x['alloc_bytes'] for x in rows if x['particles_ticked']==0)
   d['movement_ms_per_tick_frame']=stats.mean(x['particle_move_ns']/1e6 for x in rows if x['particles_ticked']>0)
   values.append({'label':r['label'],'kind':r['kind'],'scale':r['scale'],'phase':phase,'metrics':d})
 assert len([r for r in records if r['kind'] in ('absent','hook')])==4
 assert len([r for r in records if r['kind'] in ('census','inactive')])==4
 assert len([r for r in records if r['kind']=='detail'])==2
 pairs=[];warnings=[]
 for n in (1,2):
  for phase in ('static','pan'):
   a=next(x for x in values if x['label']==f'mc24-absent-1-r{n}' and x['phase']==phase)['metrics']
   b=next(x for x in values if x['label']==f'mc24-hook-1-r{n}' and x['phase']==phase)['metrics']
   d={k:helper.delta(a[k],b[k]) for k in a if k not in ('frames','tick_frames') and a[k]!=0}
   pairs.append({'pair':n,'phase':phase,'absent':a,'hook_only':b,'delta_percent':d})
 for phase in ('static','pan'):
  pp=[x for x in pairs if x['phase']==phase];med={k:stats.median(x['delta_percent'][k] for x in pp) for k in pp[0]['delta_percent']}
  limits={k:2 for k in ('frame_ms','non_tick_frame_ms','client_cpu_ms_per_frame','alloc_bytes_per_frame')}
  limits.update({k:5 for k in ('frame_p95_ms','frame_p99_ms','non_tick_frame_p95_ms','non_tick_frame_p99_ms','tick_frame_ms','tick_frame_p99_ms')})
  warnings.append({'phase':phase,'median_paired_delta_percent':med,'exceeded':{k:med[k] for k,v in limits.items() if med[k]>v},'drift_flagged_pairs':[x['pair'] for x in pp if abs(x['delta_percent']['frame_ms'])>10]})
 result={'source_snapshot':snapshot,'startup_pairs':pairs,'equivalence_warnings':warnings,'launch_values':values,'census':census,'selection':'No optimization promotion; attribution and two-pair dispatch controls only.'}
 (HERE/'metrics.json').write_text(json.dumps(result,indent=2)+'\n')
 out=['# Session 24 movement results','','## Startup hook-only minus hooks-absent','','| Pair | Phase | Frame | Ordinary frame | CPU/frame | Allocation/frame | Tick allocation | Frame p99 | Tick p99 |','|---|---|---:|---:|---:|---:|---:|---:|---:|']
 for r in pairs:
  d=r['delta_percent'];out.append(f"| {r['pair']} | {r['phase']} | "+' | '.join(f'{d[k]:+.2f}%' for k in ('frame_ms','non_tick_frame_ms','client_cpu_ms_per_frame','alloc_bytes_per_frame','tick_frame_alloc_bytes','frame_p99_ms','tick_frame_p99_ms'))+' |')
 out+=['','## Active movement census','','| Scale | Phase | Moves | noClip | Positive stepHeight | Step queries | Ordinary queries | Mean list length | Gather / move wall |','|---|---|---:|---:|---:|---:|---:|---:|---:|']
 for r in census:
  if r['kind']!='census':continue
  for phase,record in r['census']['phases'].items():
   c=record['total'];out.append(f"| {r['scale']} | {phase} | {c['moves_returned']} | {c['no_clip_at_entry']} | {c['positive_step_height_at_entry']} | {c['step_query_returns']} | {c['ordinary_query_returns']} | {c['ordinary_result_size_sum']/max(1,c['ordinary_query_returns']):.2f} | {100*c['collision_gather_wall_ns']/max(1,c['move_wall_ns']):.1f}% |")
 out+=['','Counts are client capture-window observations. Gather/rest times include probe costs; 3N/7N fields are structural scan estimates, not measured clip counts. Two startup pairs cannot certify equivalence. Full values, warnings and classes remain in metrics.json.','']
 (HERE/'results.md').write_text('\n'.join(out));print('Wrote results.md and metrics.json')
if __name__=='__main__':main()
