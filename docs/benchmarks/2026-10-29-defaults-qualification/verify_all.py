"""Run the seven predeclared verifiers sequentially, once each, without retries."""
import json
from pathlib import Path
import re
import subprocess
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
OPTIONS = ('cameraHysteresis', 'vectorFrustum', 'flatOcclusion', 'fastModelTransforms',
           'fastTerrainPipeline', 'fastWorkerAo', 'fastMipmaps')


def main():
    for option in OPTIONS:
        label = 'q-verify-' + option
        props = ['alacer.render.' + option + '.verify=true']
        if option == 'fastModelTransforms':
            props += ['alacer.benchmark.entities=200']
        if option == 'fastWorkerAo':
            props += ['alacer.benchmark.chunkEdit=true', 'alacer.benchmark.vbo=true',
                      'alacer.profiler.chunkLatency=true']
        command = [sys.executable, str(HERE / 'launch.py'), label, *props]
        print('START ' + option, flush=True)
        result = subprocess.run(command, cwd=ROOT)
        record = json.loads((HERE / 'launches' / (label + '.json')).read_text())
        log = (ROOT / record['log']).read_text(encoding='utf-8', errors='replace')
        lines = [line for line in log.splitlines() if re.search(
            r'\b(?:mismatch|mismatched|difference|differed|changed the matrix|verification failed)\b', line,
            flags=re.I) and 'initializing:' not in line and 'Environment:' not in line]
        (HERE / 'launches' / (label + '-verifier-lines.txt')).write_text('\n'.join(lines) + '\n', encoding='utf-8')
        if result.returncode or record['status'] != 'completed' or record.get('summary_missing'):
            raise SystemExit('STOP: functional/preflight failure in ' + option)
        if re.search(r'Mixin apply.*failed|InvalidMixin|OpenGL Error|GL ERROR|Exception in thread|Benchmark timed out', log):
            raise SystemExit('STOP: functional failure in ' + option)
        print('DONE ' + option + '; retained verifier output for review', flush=True)


if __name__ == '__main__':
    main()
