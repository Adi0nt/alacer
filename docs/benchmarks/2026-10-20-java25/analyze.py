"""Recompute Java 25 metrics from retained CSVs using Alacer's existing comparison tool.

Run after tools/run_java25_validation.py completes. Does not touch Java 8 archives.
"""
import hashlib
import importlib.util
import json
from pathlib import Path
import statistics
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
spec = importlib.util.spec_from_file_location('compare', ROOT / 'tools/compare_benchmarks.py')
compare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(compare)


def write(name, data):
    (HERE / name).write_text(json.dumps(data, indent=2) + '\n')


def source(label):
    r = json.loads((HERE / 'runs' / f'{label}.record.json').read_text())
    folder = ROOT / r['source']
    for name, expected in r['files_sha256'].items():
        assert hashlib.sha256((folder / name).read_bytes()).hexdigest() == expected
    return str(folder)


def result(sources):
    # Retain the established pooled CSV comparison; append mean/max and absolute tail rates.
    values = compare.summarize(sources)
    values['frame mean ms'] = 1000 / values['avg fps']
    columns = compare.load(sources)
    frames = columns['frame_ns']
    values['frame max ms'] = max(frames) / 1e6
    duration = sum(frames) / 1e9
    for threshold in (8.333, 16.667, 33.333):
        count = sum(f > threshold * 1e6 for f in frames)
        values[f'frames above {threshold} ms'] = count
        values[f'frames above {threshold} ms per second'] = count / duration
    return values


def changes(a, b):
    return {key: 100 * (b[key] / a[key] - 1) for key in a if key in b and a[key] != 0}


def main():
    defaults = {}
    for density in (0, 200, 1000):
        runs = {}
        for repeat in (1, 2):
            label = f'j25-defaults-e{density}-r{repeat}'
            folder = source(label)
            summary = json.loads((Path(folder) / 'summary.json').read_text())
            assert not any(v == 'true' for k, v in summary['config'].items()
                           if k.endswith('.verify') or k in ('profiler.chunkDetail', 'profiler.entityDetail', 'profiler.frustumTiming'))
            runs[label] = {}
            for phase in ('static', 'pan'):
                a = result(compare.toggled([folder], phase, 'a'))
                b = result(compare.toggled([folder], phase, 'b'))
                for name, config in ((name, p['config']) for name, p in summary['phases'].items()):
                    if name.startswith(phase + '-'):
                        assert config['render.fastChunkFrustum'] == 'false'
                        assert config['render.batchModelLists'] == 'false'
                        assert config['render.skipZeroTranslates'] == 'false'
                        for option in ('fastChunkVisibility', 'fastEntityChunkLookup', 'throttleGlErrorChecks',
                                       'avoidMatrixReads', 'cacheClouds', 'fastEntityShadows'):
                            assert config['render.' + option] == str('-b' in name).lower()
                runs[label][phase] = {'a': a, 'b': b, 'change_percent': changes(a, b)}
        defaults[str(density)] = {'runs': runs, 'median_change_percent': {
            phase: {key: statistics.median(r[phase]['change_percent'][key] for r in runs.values())
                    for key in next(iter(runs.values()))[phase]['change_percent']
                    if all(key in r[phase]['change_percent'] for r in runs.values())}
            for phase in ('static', 'pan')}}
    backends = {}
    for backend in ('dl', 'vbo'):
        for repeat in (1, 2):
            label = f'j25-backend-{backend}-r{repeat}'
            folder = source(label)
            summary = json.loads((Path(folder) / 'summary.json').read_text())
            assert summary['environment']['benchmark.glassBlocks'] == '364'
            for phase in summary['phases'].values():
                assert phase['config']['options.useVbo'] == str(backend == 'vbo').lower()
                assert phase['config']['vboActive'] == str(backend == 'vbo').lower()
                assert (phase['config']['backend.renderChunkClass'] ==
                        'net.minecraft.client.render.world.RenderChunk') == (backend == 'vbo')
            backends[label] = {phase: result(compare.separate([folder], phase))
                               for phase in ('steady', 'pan', 'sort', 'fly')}
    pairs = {str(repeat): {phase: changes(backends[f'j25-backend-dl-r{repeat}'][phase],
                                        backends[f'j25-backend-vbo-r{repeat}'][phase])
                          for phase in ('steady', 'pan', 'sort', 'fly')} for repeat in (1, 2)}
    write('metrics.json', {'defaults': defaults, 'backends': backends, 'backend_pairs_change_percent': pairs,
                          'backend_median_change_percent': {
                              phase: {key: statistics.median(p[phase][key] for p in pairs.values())
                                      for key in pairs['1'][phase]
                                      if all(key in p[phase] for p in pairs.values())}
                              for phase in ('steady', 'pan', 'sort', 'fly')}})
    lines = ['# Java 25 measurements', '',
             'Two fresh launches per density; within each launch, A/B/B/A static/pan segments.',
             'A uses vanilla paths; B uses the same six existing defaults. No default change selected.', '',
             '| Mobs | Phase | Mean frame change | Client CPU/frame | Allocation/frame | Allocation/second | p99 |',
             '|---:|---|---:|---:|---:|---:|---:|']
    for density, data in defaults.items():
        for phase, values in data['median_change_percent'].items():
            keys = ('frame mean ms', 'client thread CPU ms/frame', 'alloc KB/frame', 'alloc MB/s', 'frame p99 ms')
            lines.append(f'| {density} | {phase} | ' + ' | '.join(f'{values[k]:+.1f}%' for k in keys) + ' |')
    lines += ['', '## Absolute baseline with current defaults', '',
              'Median of two launches; B segments pooled within each launch.', '',
              '| Mobs | Phase | Frame mean ms | Client CPU ms/frame | Allocation KB/frame | Allocation MB/s | p99 ms |',
              '|---:|---|---:|---:|---:|---:|---:|']
    for density, data in defaults.items():
        for phase in ('static', 'pan'):
            keys = ('frame mean ms', 'client thread CPU ms/frame', 'alloc KB/frame', 'alloc MB/s', 'frame p99 ms')
            values = [statistics.median(r[phase]['b'][key] for r in data['runs'].values()) for key in keys]
            lines.append(f'| {density} | {phase} | ' + ' | '.join(f'{value:.3f}' for value in values) + ' |')
    lines += ['', '## Terrain backend controls', '',
              'Two launches per backend, balanced DL/VBO/VBO/DL. Negative values mean lower cost.',
              'These are new runtime baselines, not a repetition of the historical four-pair selection gate.', '',
              '| Phase | Mean frame | Client CPU/frame | Allocation/frame | Allocation/second | p99 | Maximum |',
              '|---|---:|---:|---:|---:|---:|---:|---:|']
    for phase in ('steady', 'pan', 'sort', 'fly'):
        keys = ('frame mean ms', 'client thread CPU ms/frame', 'alloc KB/frame', 'alloc MB/s', 'frame p99 ms', 'frame max ms')
        values = {key: statistics.median(p[phase][key] for p in pairs.values()) for key in keys}
        lines.append(f'| {phase} | ' + ' | '.join(f'{values[k]:+.1f}%' for k in keys) + ' |')
    lines += ['', '| Backend | Phase | Frame mean ms | Client CPU ms/frame | Allocation KB/frame | Allocation MB/s | p99 ms | Max ms |',
              '|---|---|---:|---:|---:|---:|---:|---:|']
    for backend in ('dl', 'vbo'):
        for phase in ('steady', 'pan', 'sort', 'fly'):
            keys = ('frame mean ms', 'client thread CPU ms/frame', 'alloc KB/frame', 'alloc MB/s', 'frame p99 ms', 'frame max ms')
            values = [statistics.median(backends[f'j25-backend-{backend}-r{r}'][phase][key] for r in (1, 2)) for key in keys]
            lines.append(f'| {backend} | {phase} | ' + ' | '.join(f'{value:.3f}' for value in values) + ' |')
    lines += ['', 'Full per-run means, CPU, allocations, tails, maxima and slow-frame rates are in `metrics.json`.',
              'CSV identities and exact commands are in `runs/*.record.json`; raw CSVs remain in the local run directory.',
              'JFR/detail and verification runs are excluded from these tables.', '']
    (HERE / 'measurements.md').write_text('\n'.join(lines))
    print('\n'.join(lines))


if __name__ == '__main__':
    main()
