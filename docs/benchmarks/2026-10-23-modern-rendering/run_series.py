"""Native Windows/Java 25 serial launches. Run from the repository root; never overwrite attempts."""
from pathlib import Path
import hashlib, json, os, subprocess, sys, time

ROOT = Path.cwd()
ARCHIVE = ROOT / 'docs/benchmarks/2026-10-23-modern-rendering'
JDK = os.environ.get('JAVA_HOME', 'C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot')
ENV = {**os.environ, 'JAVA_HOME': JDK, 'PATH': JDK + '/bin;' + os.environ['PATH']}
OPTIONS = ['fastChunkNeighbours', 'terrainVaos', 'directBufferUploads']

def hashes():
    paths = list((ROOT/'src').rglob('*')) + [ROOT/'build.gradle', ARCHIVE/'gates.md', Path(__file__).resolve()]
    paths += list((ROOT/'run/saves/alacer-benchmark').rglob('*'))
    return {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(paths) if p.is_file()}

def jobs(group):
    if group == 'correctness':
        for arm in ['a1', 'a2', 'b1']:
            yield 'mg-shot-' + arm, [f'-Palacer.render.{k}={str(arm[0]=="b").lower()}' for k in OPTIONS] + [
                '-Palacer.benchmark.backendScenario=true', '-Palacer.benchmark.screenshots=true', '-Palacer.benchmark.screenshotEndPose=true']
        for name, extra in [('forced-fallback', ['-Palacer.modernGl.forceFallback=true']),
                            ('display-list', ['-Palacer.benchmark.vbo=false'])]:
            yield 'mg-' + name, [f'-Palacer.render.{k}=true' for k in OPTIONS] + extra
        yield 'mg-verify-edit-r2', ['-Palacer.benchmark.chunkEdit=true', '-Palacer.render.fastChunkNeighbours=true',
            '-Palacer.render.fastChunkNeighbours.verify=true', '-Palacer.render.fastChunkVisibility.verify=true', '-Palacer.render.modernGl.verify=true']
        for scene in ['default', 'terrain']:
            yield 'mg-verify-' + scene + '-r2', ['-Palacer.render.fastChunkNeighbours=true',
                '-Palacer.render.fastChunkNeighbours.verify=true', '-Palacer.render.fastChunkVisibility.verify=true', '-Palacer.render.modernGl.verify=true'] + (
                    ['-Palacer.benchmark.backendScenario=true'] if scene == 'terrain' else [])
    elif group == 'guard':
        yield 'mg-verify-guard', ['-Palacer.benchmark.backendScenario=true', '-Palacer.render.fastChunkNeighbours=true',
            '-Palacer.render.fastChunkNeighbours.verify=true', '-Palacer.render.fastChunkVisibility.verify=true', '-Palacer.render.modernGl.verify=true']
    elif group == 'performance':
        for scene in ['default', 'terrain']:
            for i, arm in enumerate('ABBABAAB', 1):
                yield f'mg-perf-{scene}-{i}{arm.lower()}', [f'-Palacer.render.{k}={str(arm=="B").lower()}' for k in OPTIONS] + (
                    ['-Palacer.benchmark.backendScenario=true'] if scene == 'terrain' else [])
    elif group == 'ablations':
        for active in ['control'] + OPTIONS:
            yield 'mg-ablation-' + active, [f'-Palacer.render.{k}={str(k==active).lower()}' for k in OPTIONS]
    elif group == 'smoke':
        yield 'mg-shipping-smoke', ['-Palacer.benchmark.vbo=false'] + [f'-Palacer.render.{k}=false' for k in OPTIONS]
    else:
        raise ValueError(group)

def main():
    group = sys.argv[1]
    suffix = '-' + sys.argv[2] if len(sys.argv) > 2 else ''
    attempt = group + suffix
    freeze_file = ARCHIVE / (attempt + '-freeze.json')
    frozen = hashes()
    if freeze_file.exists():
        raise RuntimeError('Attempt already exists; preserve it and choose an explicitly amended attempt')
    freeze_file.write_text(json.dumps(frozen, indent=2) + '\n')
    receipts = []
    for raw_label, extra in jobs(group):
        label = raw_label + suffix
        assert hashes() == frozen, 'Frozen source/world changed'
        log = ROOT/'build'/(label+'.log')
        assert not log.exists(), f'Never overwrite {log}'
        cmd = [str(ROOT/'gradlew.bat'), '--offline', '--console=plain', 'runClient', '-Pwindow=1920x1080', '-Pxmx=2G',
               '-Palacer.benchmark=true', '-Palacer.benchmark.vbo=true', '-Palacer.benchmark.viewDistance=12',
               '-Palacer.benchmark.label='+label] + extra
        print('START', label, time.strftime('%H:%M:%S'), flush=True)
        with log.open('w', encoding='utf-8') as stream:
            result = subprocess.run(cmd, cwd=ROOT, env=ENV, stdout=stream, stderr=subprocess.STDOUT)
        text = log.read_text(encoding='utf-8', errors='replace')
        matches = list((ROOT/'run/alacer/benchmarks').glob('*-'+label+'-*/summary.json'))
        assert result.returncode == 0 and len(matches) == 1, (label, result.returncode, matches)
        assert not any(term in text for term in ['Benchmark aborted', 'benchmark aborted', 'Modern standalone lifecycle FAILED', 'OpenGL Error', 'Mixin apply failed']), label
        summary = json.loads(matches[0].read_text())
        assert summary['environment']['java.version'].startswith('25.'), label
        assert hashes() == frozen, 'Frozen source/world changed during launch'
        receipts.append({'label': label, 'command': cmd, 'summary': matches[0].relative_to(ROOT).as_posix(), 'exit': result.returncode,
                         'log_sha256': hashlib.sha256(log.read_bytes()).hexdigest()})
        (ARCHIVE/(attempt+'-launches.json')).write_text(json.dumps(receipts, indent=2)+'\n')
        print('END', label, time.strftime('%H:%M:%S'), flush=True)
    print('COMPLETE', group, flush=True)

if __name__ == '__main__':
    main()
