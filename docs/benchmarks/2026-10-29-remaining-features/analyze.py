"""Reconstruct retained functional observations. Does not assign performance gates."""
import json
import re
from launch import HERE, ROOT, sha


def main():
    output = {'launches': [], 'performance_approval': False}
    for file in sorted((HERE / 'launches').glob('*.json')):
        record = json.loads(file.read_text())
        label = record['label']
        if record['status'] == 'running': continue
        log = (HERE / 'logs' / (label + '.log')).read_text(errors='replace')
        summary_files = list((HERE / 'runs').glob('*-' + label + '-vis_*/summary.json'))
        item = {key: record.get(key) for key in (
            'label', 'status', 'exit_code', 'duration_s', 'jar_sha256', 'master_volume')}
        item['resource_driver_failed'] = 'FOLLOWUP RESOURCE FAILURE' in log
        item['native_crash'] = 'A fatal error has been detected' in log
        item['reload_completions'] = re.findall(r'FOLLOWUP reload (\d) completed; (.*)', log)
        item['atlas_load_ms'] = [int(n) / 1e6 for n in re.findall(r'Resource timing: atlas loadAndStitch (\d+) ns', log)]
        item['resource_reload_ms'] = [int(n) / 1e6 for n in re.findall(r'Resource timing: Minecraft reloadResources (\d+) ns', log)]
        item['verifier_final_lines'] = [line for line in log.splitlines() if ' final:' in line]
        item['functional_error_lines'] = [line for line in log.splitlines() if re.search(
            r'Mixin apply.*failed|InvalidMixin|OpenGL Error|GL ERROR|Exception in thread|Benchmark timed out|EXCEPTION_ACCESS_VIOLATION|IllegalAccessError|Namespace .* does not match', line)]
        item['startup_error_lines'] = [line for line in log.splitlines() if re.search(
            r'CLASS_NOT_FOUND|Unable to locate appender|Couldn.t initialize twitch|RealmsNotificationsScreen', line)]
        counters = HERE / 'logs' / (label + '-counters.txt')
        if counters.exists(): item['final_observer_snapshot'] = counters.read_text().split('snapshot ')[-1].strip()
        if len(summary_files) == 1:
            summary = json.loads(summary_files[0].read_text())
            item['phases'] = {name: {key: phase[key] for key in ('frames', 'dropped_frames', 'entities')}
                              for name, phase in summary['phases'].items()}
            item['runtime'] = {key: value for key, value in summary['environment'].items()
                               if key in ('java.version', 'java.home', 'os.name', 'os.version', 'cpu', 'window')
                               or key.startswith(('modernGl.', 'chunkNeighbours.'))}
            item['screenshots'] = [str(p.relative_to(HERE)) for p in summary_files[0].parent.glob('*.png')]
        output['launches'].append(item)
    output['identities'] = {
        'config_matches_backup': sha(ROOT / 'run/config/alacer.properties') == sha(ROOT / 'build/remaining-features-backup/alacer.properties'),
        'options_master_muted': 'soundCategory_master:0.0' in (ROOT / 'run/options.txt').read_text().splitlines(),
        'current_jar_sha256': sha(ROOT / 'build/libs/alacer-1.0.0+mc1.8.9.jar'),
    }
    (HERE / 'results.json').write_text(json.dumps(output, indent=2) + '\n')
    for item in output['launches']:
        print(item['label'], item['status'], 'driverFailed=' + str(item['resource_driver_failed']),
              'reloads=' + str(len(item['reload_completions'])), 'errors=' + str(len(item['functional_error_lines'])))


if __name__ == '__main__': main()
