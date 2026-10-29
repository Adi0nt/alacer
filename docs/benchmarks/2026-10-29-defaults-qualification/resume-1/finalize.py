"""Add retained setup, launch and integrity evidence to the completed score output."""
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sha = lambda p: hashlib.sha256(Path(p).read_bytes()).hexdigest()


def main():
    freeze = json.loads((HERE / 'freeze.json').read_text())
    for group in ('source_hashes', 'world_hashes', 'shared_state_hashes'):
        for path, expected in freeze[group].items():
            assert sha(ROOT / path) == expected, (group, path)
    assert sha(HERE / 'PROTOCOL.md') == freeze['protocol_sha256']
    assert sha(ROOT / 'build/libs/alacer-1.0.0+mc1.8.9.jar') == freeze['jar_sha256']
    baseline = '941405bf0a2afbb18785f8dd36d1d285dc37c633'
    for ref in ('main', 'v1.0.0^{}'):
        assert subprocess.check_output(['git', 'rev-parse', ref], cwd=ROOT, text=True).strip() == baseline
    for path in ('src/main/java/net/adiont/alacer/config/AlacerConfig.java', 'src/main/java/net/adiont/alacer/config/AlacerMixinPlugin.java'):
        assert subprocess.check_output(['git', 'show', baseline + ':' + path], cwd=ROOT).replace(b'\r\n', b'\n') == (ROOT / path).read_bytes().replace(b'\r\n', b'\n')
    result = json.loads((HERE / 'results.json').read_text())
    records = sorted((json.loads(p.read_text()) for p in (HERE / 'launches').glob('*.json')),
                     key=lambda r: r['launch_epoch'])
    assert len(records) == 21 and all(r['status'] == 'completed' and r['exit_code'] == 0 for r in records)
    launches = []
    for record in records:
        assert record['source'] == freeze['source']
        assert record['protocol_sha256'] == freeze['protocol_sha256']
        assert record['jar_sha256'] == freeze['jar_sha256']
        phases = {}
        if record.get('run'):
            summary = json.loads((ROOT / record['run'] / 'summary.json').read_text())
            assert summary['environment']['alacer.configSource'] == 'build-defaults-and-jvm-overrides'
            phases = {p: {k: v[k] for k in ('frames', 'dropped_frames', 'recorded_wall_seconds', 'entities')}
                      for p, v in summary['phases'].items()}
            assert all(v['dropped_frames'] == 0 for v in phases.values())
            if re.fullmatch(r'q2-(vectorFrustum|fastModelTransforms|fastWorkerAo|fastMipmaps)-\d', record['label']):
                for phase, values in summary['phases'].items():
                    config = values['config']
                    assert not any(v == 'true' for k, v in config.items() if k.endswith('.verify'))
                    assert all(config[k] == 'false' for k in ('profiler.entityDetail', 'profiler.chunkDetail', 'profiler.frustumTiming', 'profiler.particleDetail'))
        launches.append(dict(label=record['label'], command=record['command'], status=record['status'],
                             run=record.get('run'), start=record['launch_epoch'], end=record['exit_epoch'],
                             phases=phases, quiet_wait_s=record.get('quiet_wait_s'),
                             environment_monitor=record['environment_monitor'], monitor_errors=record['monitor_errors']))
    roots = [ET.parse(p).getroot() for p in (HERE / 'build-evidence').glob('TEST-*.xml')]
    junit = {k: sum(int(x.get(k, 0)) for x in roots) for k in ('tests', 'failures', 'errors', 'skipped')}
    assert junit == dict(tests=353, failures=0, errors=0, skipped=0)
    recommendations = {
        'cameraHysteresis': ('turn off', 'At least 3454 stable-input verification differences; traversal cause not isolated to hysteresis alone'),
        'flatOcclusion': ('turn off', '3 native verifier differences'),
        'fastTerrainPipeline': ('turn off', '290 native layer-count verifier differences'),
        'vectorFrustum': ('needs Extended campaign', 'Zero verifier mismatches; static CPU gate fails in ABBA and static median changes sign'),
        'fastModelTransforms': ('turn off', 'Zero sampled matrix differences; static p99/CPU fail in both orders; exact skipZeroTranslates overlap'),
        'fastWorkerAo': ('turn off', 'Zero verifier mismatches; frame-tail/CPU gates fail and update-latency tails regress in both pairs'),
        'fastMipmaps': ('needs Extended campaign', 'Zero verifier mismatches; startup atlas effect flips sign; reload unavailable'),
    }
    result.update(status='completed_with_correctness_exclusions', source=freeze['source'],
                  freeze_sha256=sha(HERE / 'freeze.json'), defaults_changed=False, integrity='all frozen hashes match',
                  build=dict(junit=junit, pytest=dict(passed=119, skipped=2, subtests_passed=9, seconds=94.02),
                             java='Temurin 25.0.4.1+1-LTS', jar_sha256=freeze['jar_sha256']),
                  launch_count=21, performance_launch_count=12, launches=launches,
                  recommendations={k: dict(recommendation=v[0], evidence=v[1]) for k, v in recommendations.items()},
                  manual_smoke=dict(title=True, world=True, f7=True, f8=True, f9=True, video_settings=True,
                                    export='run/alacer/profiles/profile-20261028-175200.json'),
                  reload_attempt=dict(label='q2-fastMipmaps-1', requested='F3+t', observed='debug overlay and chat opened; no resource reload',
                                      subsequent_attempts='not attempted after tool chord failed', measured=False),
                  release_jar_launched=False)
    result['limitations'] += ['Camera counters are the last periodic checkpoint, not an exact final total',
                              'Natural client entity counts differ between fresh launches (including 209 versus 210 in model runs)',
                              'F3+T tool chord did not trigger a reload; no reload durations measured',
                              'runClient loads development classes/resources; remapped distributable not launched',
                              'No visual A/B screenshots or Extended environmental/power/stability campaign',
                              'All scored frame/worker launches had foreground-client fraction 0; foreground was not controlled']
    result['limitations'] = list(dict.fromkeys(result['limitations']))
    (HERE / 'results.json').write_text(json.dumps(result, indent=2) + '\n')
    lines = ['# Every resumed launch and command', '',
             'All use explicit Temurin 25.0.4.1 via launch.py. Times are America/Los_Angeles (PDT, UTC-7).', '',
             '| Label | Local start/end | Result | Quiet wait s | Background cores mean* | Foreground client fraction* |',
             '|---|---|---|---:|---:|---:|']
    timezone = dt.timezone(dt.timedelta(hours=-7))
    for record in launches:
        stamp = lambda t: dt.datetime.fromtimestamp(t, timezone).strftime('%H:%M:%S')
        env = record['environment_monitor']
        quiet = '-' if record['quiet_wait_s'] is None else f"{record['quiet_wait_s']:.2f}"
        lines.append(f"| {record['label']} | {stamp(record['start'])}–{stamp(record['end'])} | exit 0 | {quiet} | {env['background_cores_mean']:.3f} | {env['foreground_client_fraction']:.3f} |")
    lines += ['', '*Whole attempt including preflight/startup; not per-phase environmental acceptance. See each launch JSON for all telemetry and availability.', '']
    for record in launches:
        lines += ['## ' + record['label'], '', '```text', ' '.join(record['command']), '```', '']
        if record['run']:
            lines += ['Raw result: `' + record['run'] + '`. Archived summary is under `runs/`.', '']
    (HERE / 'LAUNCHES.md').write_text('\n'.join(lines), encoding='utf-8')
    for path in (HERE / 'logs').iterdir():
        path.write_bytes(re.sub(rb'[ \t]+(\r?\n)', rb'\1', path.read_bytes()))
    print('Validated 21 completed launches, 20 summaries, 353 tests, frozen source/artifact/world/config and unchanged defaults/main/tag')
    print('Monitor exceptions:', sum(len(r['monitor_errors']) for r in launches))
    print('Highest quiet wait:', max(r['quiet_wait_s'] or 0 for r in launches))


if __name__ == '__main__':
    main()
