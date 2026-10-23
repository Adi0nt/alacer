from pathlib import Path
import subprocess,json,os,hashlib,zipfile
root=Path.cwd();a=root/'docs/benchmarks/2026-10-23-modern-rendering';jdk='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot';env={**os.environ,'JAVA_HOME':jdk,'PATH':jdk+'/bin;'+os.environ['PATH']}
def hashes():return {p.relative_to(root).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((root/'src').rglob('*')) if p.is_file()}
frozen=hashes();(a/'query-freeze.json').write_text(json.dumps(frozen,indent=2)+'\n');receipts=[]
with zipfile.ZipFile(a/'query-diagnostic-source.zip','w',zipfile.ZIP_DEFLATED) as z:
 for p in sorted((root/'src').rglob('*')):
  if p.is_file():z.write(p,p.relative_to(root).as_posix())
for skip in [False,True]:
 label='mg-query-'+('skip' if skip else 'original');log=root/'build'/(label+'.log');assert not log.exists()
 args=['runClient','--offline','--console=plain','-Pwindow=1920x1080','-Pxmx=2G','-Palacer.benchmark=true','-Palacer.benchmark.vbo=true','-Palacer.benchmark.viewDistance=12','-Palacer.benchmark.label='+label,'-Palacer.modernGl.skipVaoQuery='+str(skip).lower()]+[f'-Palacer.render.{k}=true' for k in ['fastChunkNeighbours','terrainVaos','directBufferUploads']]
 assert hashes()==frozen;print('START',label,flush=True)
 with log.open('w') as stream:r=subprocess.run([str(root/'gradlew.bat')]+args,env=env,stdout=stream,stderr=subprocess.STDOUT)
 matches=list((root/'run/alacer/benchmarks').glob('*-'+label+'-*/summary.json'));assert r.returncode==0 and len(matches)==1;assert hashes()==frozen
 receipts.append({'label':label,'command':args,'summary':matches[0].relative_to(root).as_posix()});(a/'query-launches.json').write_text(json.dumps(receipts,indent=2)+'\n');print('END',label,flush=True)
