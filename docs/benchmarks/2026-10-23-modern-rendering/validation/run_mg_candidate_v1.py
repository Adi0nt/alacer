from pathlib import Path
import os,subprocess
root=Path.cwd();jdk='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot';env={**os.environ,'JAVA_HOME':jdk,'PATH':jdk+'/bin;'+os.environ['PATH']}
args=['runClient','--offline','--console=plain','-Pwindow=1920x1080','-Pxmx=2G','-Palacer.benchmark=true','-Palacer.benchmark.vbo=true','-Palacer.benchmark.viewDistance=12','-Palacer.benchmark.label=mg-attrib-candidate-v1','-Pjfr=mg-attrib-candidate-v1','-PjfrSettings='+str(root/'docs/benchmarks/2026-10-21-particle-movement-regions/movement-1ms.jfc'),'-PjfrDebugNonSafepoints=true']+[f'-Palacer.render.{k}=true' for k in ['fastChunkNeighbours','terrainVaos','directBufferUploads']]
with (root/'build/mg-attrib-candidate-v1.log').open('w') as stream:
 r=subprocess.run([str(root/'gradlew.bat')]+args,env=env,stdout=stream,stderr=subprocess.STDOUT)
assert r.returncode==0
