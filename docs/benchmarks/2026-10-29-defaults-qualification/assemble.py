"""Recreate the stopped campaign's results.json from retained evidence (no launches).

The manual UI and pytest observations below transcribe this session's tool results.
analyze.py is the unchanged release-pacing analyzer, retained for the planned toggles;
no toggle launch occurred, so no performance gate can be evaluated.
"""
import hashlib
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
import aa_platform as plat


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    if plat.current().find_clients():
        raise SystemExit('Refusing offline analysis while a game is running')
    launches = {p.stem: json.loads(p.read_text()) for p in sorted((HERE / 'launches').glob('*.json'))}
    junit = [ET.parse(p).getroot() for p in (HERE / 'build-evidence').glob('TEST-*.xml')]
    counts = {k: sum(int(x.get(k, 0)) for x in junit) for k in ('tests', 'failures', 'errors', 'skipped')}
    verification = {}
    options = ('cameraHysteresis', 'fastWorkerAo', 'fastTerrainPipeline', 'vectorFrustum',
               'flatOcclusion', 'fastMipmaps', 'fastModelTransforms')
    for option in options:
        label = 'q-verify-' + option
        record = launches.get(label)
        row = dict(status='not_run_campaign_stopped', comparisons=None, mismatches=None,
                   recommendation='needs Extended campaign', performance_launches=[], gates='not_measured')
        if record:
            log = (HERE / 'logs' / (label + '.log')).read_text(encoding='utf-8', errors='replace')
            emitted = [l for l in log.splitlines() if re.search(
                r'\b(?:mismatch|mismatched|difference|differed|changed the matrix|verification failed)\b', l,
                flags=re.I) and 'initializing:' not in l and 'Environment:' not in l]
            (HERE / 'launches' / (label + '-verifier-lines.txt')).write_text('\n'.join(emitted) + '\n')
            row.update(status='crashed_before_summary', emitted_difference_lines=len(emitted),
                       reason='Direction cast to RenderChunk in WorldRendererMixin.alacer$fastDirtyAdd',
                       recommendation='needs Extended campaign (correctness repair required first)')
        verification[option] = row
    summary = json.loads(next((HERE / 'runs').glob('*/summary.json')).read_text())
    results = dict(
        status='stopped_on_functional_failure', baseline='941405bf0a2afbb18785f8dd36d1d285dc37c633',
        branch='codex/1.0-qualification', mode='Standard',
        protocol_sha256=sha(HERE / 'PROTOCOL.md'),
        stop_reason='First verifier crashed during world entry; no retries or further launches',
        build=dict(junit=counts, pytest=dict(passed=119, skipped=2, subtests_passed=9, seconds=129.51),
                   runtime='Temurin 25.0.4.1+1-LTS', bytecode_major=69,
                   jar_sha256='98e06d881a4535770820bec12d9882ff0b2bfe6809966a40eb348225bb733eab',
                   packaged_licenses=['COPYING_alacer', 'COPYING.LESSER_alacer']),
        smoke=dict(manual=dict(title=True, world=True, f7=True, f8=True, f9=True, video_settings=True,
                               export='run/alacer/profiles/profile-20261028-173356.json', exit_code=0),
                   benchmark=dict(label='q-smoke', exit_code=0, phases=list(summary['phases'])),
                   errors=['Log4j Queue CLASS_NOT_FOUND / missing ServerGuiConsole appender (launcher stdout)',
                           'Twitch initialization failed', 'Missing RealmsNotificationsScreen class'],
                   mixin_apply_errors=0, gl_errors=0,
                   artifact_limit='runClient uses development classes/resources, not the remapped release jar'),
        verification=verification, performance_launch_count=0,
        performance_gates=[], improvement_at_least_3_percent=None,
        launches={k: {f: v.get(f) for f in ('status', 'exit_code', 'command', 'run', 'quiet_wait_s',
                                           'summary_missing')} for k, v in launches.items()},
        overlap=dict(same_calls=True, hook='ModelPartMixin.alacer$translate -> ZeroTranslates.shouldTranslate',
                     methods=['render', 'renderForceTransform', 'transform'],
                     skipped='GlStateManager.translatef with x == y == z == 0, including signed zero; underlying glTranslatef',
                     nonzero_translations_preserved=True, matrix_pushes_pops_preserved=True),
        preserved_state={p: sha(ROOT / p) for p in ('run/config/alacer.properties', 'run/options.txt',
                                                    'run/saves/alacer-benchmark/level.dat')},
        limitations=['No completed verifier and no A/B performance evidence',
                     'No worker latency measurements or mipmap startup/reload measurements',
                     'No F3+T resource reload', 'Final remapped release JAR not loaded by runClient',
                     'Standard cannot support release decisions or small-improvement claims',
                     'Terrain warmup list unchanged because campaign stopped before performance preparation'])
    (HERE / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
    print(json.dumps(dict(status=results['status'], junit=counts, launches=len(launches), performance=0)))


if __name__ == '__main__':
    main()
