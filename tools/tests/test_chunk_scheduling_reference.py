"""Runner validity tests; fixture timings are synthetic, never benchmark evidence."""
import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import run_chunk_scheduling_reference as runner


def fixture(job):
    p = runner.properties(job)
    config = {k: v for k, v in p.items() if k.startswith(('render.', 'profiler.'))}
    config['backend.renderChunkClass'] = 'net.minecraft.client.render.world.GlListRenderChunk'
    env = {'java.version': '25.0.4.1', 'gl.renderer': 'NVIDIA GeForce RTX 3060 Ti',
           'alacer.configSource': 'build-defaults-and-jvm-overrides', 'benchmark.generationRun': 'false',
           'window': '1920x1080', 'options.renderDistance': '12', 'options.vsync': 'false',
           'options.fpsLimit': '260', 'options.fancyGraphics': 'true', 'options.smoothLighting': '2',
           'options.mipmapLevels': '4', 'options.clouds': '2', 'options.useVbo': 'false', 'vboActive': 'false',
           'benchmark.mobs': '0', 'benchmark.chests': '0', 'benchmark.particles': 'false',
           'profiler.allocationAvailable': 'true', 'benchmark.toggle': '',
           **{k: p[k] for k in ('benchmark.chunkEdit', 'benchmark.chunkFlight',
                               'benchmark.backendScenario', 'benchmark.chunkUploadGpu', 'benchmark.screenshots')}}
    if job['scene'] == 'edit':
        env['benchmark.chunkEditBursts'] = '28'
    phases = {name: dict(config=copy.deepcopy(config), frames=100, dropped_frames=0,
                        update_latency_overflow=0, discarded_setup_frames=1,
                        target_seconds=seconds * job['scale'], recorded_wall_seconds=seconds * job['scale'],
                        client_thread_cpu_ms_per_frame=1, process_cpu_cores=1, alloc_mean_bytes=100, avg_fps=500,
                        one_percent_low_fps=200, frame_p99_ms=5, chunk_visible_uncompiled_mean=0,
                        update_latency_p90_ms=100, update_latency_count=10)
              for name, seconds in runner.PHASES[job['scene']].items()}
    return dict(environment=env, phases=phases)


LOG = ('> Task :compileJava UP-TO-DATE\n> Task :processResources UP-TO-DATE\n'
       'fresh chunk-flight world copy verified byte-identical\n'
       'Benchmark: results written\nBUILD SUCCESSFUL\n')


class ChunkSchedulingReferenceTest(unittest.TestCase):
    def test_bounded_serial_plan_and_independent_contrasts(self):
        self.assertEqual(16, len(runner.jobs('core')))
        full = runner.jobs('full')
        self.assertEqual(80, len(full))
        self.assertEqual(80, len({j['label'] for j in full}))
        for i in range(0, len(full), 8):
            self.assertEqual(runner.ORDER, ''.join(j['arm'] for j in full[i:i+8]))
        for job in full:
            p = runner.properties(job)
            self.assertEqual(str(job['arm'] == 'B').lower(), p[job['option']])
            self.assertEqual('false', p[next(k for k in runner.OPTIONS if k != job['option'])])

    def test_all_scene_receipts_validate(self):
        for job in runner.jobs('full'):
            runner.validate(fixture(job), job, LOG)

    def test_rejects_invalid_scoring_inputs(self):
        job = runner.jobs('core')[0]
        for edit in (
            lambda d: d['environment'].update({'benchmark.generationRun': 'true'}),
            lambda d: d['environment'].update({'gl.renderer': 'llvmpipe'}),
            lambda d: d['phases']['edit'].update(dropped_frames=1),
            lambda d: d['phases']['edit'].update(update_latency_overflow=1),
            lambda d: d['phases']['edit'].update(update_latency_count=0),
            lambda d: d['phases']['edit']['config'].update({'render.vectorFrustum.verify': 'true'}),
            lambda d: d['phases']['edit']['config'].update({'render.chunkTimeFloor': 'true'}),
            lambda d: d['phases']['edit']['config'].update({'backend.renderChunkClass': 'VboRenderChunk'}),
        ):
            doc = fixture(job)
            edit(doc)
            with self.assertRaises(RuntimeError):
                runner.validate(doc, job, LOG)
        for bad in ('InvalidInjectionException', 'MixinApplyError', 'OpenGL Error', 'Benchmark aborted'):
            with self.assertRaises(RuntimeError):
                runner.validate(fixture(job), job, LOG + bad)
        with self.assertRaises(RuntimeError):
            runner.validate(fixture(job), job, LOG.replace('compileJava UP-TO-DATE', 'compileJava'))

    def test_zero_baseline_is_not_an_infinite_json_number(self):
        self.assertEqual(0, runner.change(0, 0))
        self.assertIsNone(runner.change(0, 1))
        with self.assertRaises(RuntimeError):
            runner.change(float('nan'), 1)

    def test_exclusive_receipts(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'receipt.json'
            runner.write_new(path, {'status': 'first'})
            with self.assertRaises(FileExistsError):
                runner.write_new(path, {'status': 'second'})
            self.assertEqual({'status': 'first'}, json.loads(path.read_text()))

    def test_failed_preflight_is_retained_and_never_launches(self):
        with tempfile.TemporaryDirectory() as temp:
            archive = Path(temp)
            job = runner.jobs('core')[0]
            with patch.object(runner, 'clients', return_value=[{'ProcessId': 42}]), \
                    patch.object(runner.subprocess, 'Popen') as launch:
                with self.assertRaisesRegex(RuntimeError, 'another Minecraft client'):
                    runner.run_one(archive, job, Path('unused'))
                launch.assert_not_called()
            final = json.loads((archive / 'attempts' / (job['label'] + '.final.json')).read_bytes())
            self.assertEqual('failed', final['status'])
            self.assertIsNone(final['launcher'])

    def test_freeze_refuses_source_changes_since_smoke(self):
        with tempfile.TemporaryDirectory() as temp:
            archive = Path(temp)
            runner.write_new(archive / 'records/csr-smoke-fixture.json',
                             dict(status='passed', state={'sources': 'old'}))
            with patch.object(runner, 'clients', return_value=[]), \
                    patch.object(runner, 'state', return_value={'sources': 'changed'}):
                with self.assertRaisesRegex(RuntimeError, 'inputs changed since smoke'):
                    runner.freeze(archive, 'core', Path('unused'))
            self.assertFalse((archive / 'manifest.json').exists())

    def test_evaluator_checks_raw_hashes_and_candidate_direction(self):
        planned = runner.jobs('core')
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            state = {'synthetic': True}
            for job in planned:
                folder = root / job['label']
                folder.mkdir()
                summary = fixture(job)
                if job['arm'] == 'B':
                    for name, phase in summary['phases'].items():
                        if name in ('edit', 'fresh-flight'):
                            phase['update_latency_p90_ms'] = 60
                runner.write_new(folder / 'summary.json', summary)
                runner.write_new(root / 'records' / (job['label'] + '.json'),
                                 dict(status='passed', job=job, state=state, summary=summary,
                                      raw_folder=job['label'], raw_sha256=runner.hashes(folder)))
            frozen = dict(jobs=planned, state=state)
            with patch.object(runner, 'ROOT', root):
                result = runner.evaluate(root, frozen)
                self.assertTrue(result['passed'])
                self.assertEqual(16, result['completed'])
                key = runner.OPTIONS[0] + '/near/fresh-flight/update_latency_p90_ms'
                self.assertAlmostEqual(-0.4, result['metrics'][key]['median'])
                # A swapped/regressing candidate must fail the responsiveness gate.
                job = planned[1]
                receipt_path = root / 'records' / (job['label'] + '.json')
                receipt = json.loads(receipt_path.read_bytes())
                receipt['summary']['phases']['edit']['update_latency_p90_ms'] = 200
                raw = root / job['label'] / 'summary.json'
                raw.write_text(json.dumps(receipt['summary']))
                receipt['raw_sha256'] = runner.hashes(raw.parent)
                receipt_path.write_text(json.dumps(receipt))
                self.assertFalse(runner.evaluate(root, frozen)['passed'])
                (root / planned[0]['label'] / 'summary.json').write_text('{}')
                with self.assertRaises(RuntimeError):
                    runner.evaluate(root, frozen)


if __name__ == '__main__':
    unittest.main()
