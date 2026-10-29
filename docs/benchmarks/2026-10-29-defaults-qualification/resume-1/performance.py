"""Sequential predeclared frame/worker launches, no retries or mipmap UI actions."""
import json
from pathlib import Path
import subprocess
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]


def main():
    verified = json.loads((HERE / 'verification.json').read_text())
    plan = []
    for option in ('cameraHysteresis', 'vectorFrustum', 'flatOcclusion', 'fastModelTransforms', 'fastTerrainPipeline'):
        if not verified[option]['performance_eligible']:
            print('EXCLUDED ' + option + ': correctness gate', flush=True)
            continue
        for index in (1, 2):
            props = ['alacer.benchmark.toggle=render.' + option]
            if index == 2: props += ['alacer.benchmark.toggleReverse=true']
            if option == 'fastModelTransforms': props += ['alacer.benchmark.entities=200']
            plan.append((option, index, props))
    if verified['fastWorkerAo']['performance_eligible']:
        for index, state in enumerate(('false', 'true', 'true', 'false'), 1):
            plan.append(('fastWorkerAo', index, ['alacer.render.fastWorkerAo=' + state,
                'alacer.benchmark.chunkEdit=true', 'alacer.benchmark.vbo=true', 'alacer.profiler.chunkLatency=true']))
    for option, index, props in plan:
        label = 'q2-' + option + '-' + str(index)
        print('START ' + label, flush=True)
        subprocess.run([sys.executable, str(HERE / 'launch.py'), label, *props], cwd=ROOT, check=True)
        log = (HERE / 'logs' / (label + '.log')).read_text(errors='replace')
        if any(s in log for s in ('InvalidMixin', 'OpenGL Error', 'GL ERROR', 'Exception in thread', 'Benchmark timed out')):
            raise SystemExit('STOP functional log error: ' + label)


if __name__ == '__main__':
    main()
