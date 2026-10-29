"""Extract all verifier evidence after clients exit; never infers a pass from silence."""
import json
from pathlib import Path
import re
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(ROOT / 'tools'))
import aa_platform as plat


def main():
    if plat.current().find_clients():
        raise SystemExit('No offline analysis alongside a client')
    patterns = {
        'vectorFrustum': (r'Vector frustum verification final: (\d+) boxes compared, (\d+) mismatched, (\d+) outside exact limit',
                          ('comparisons', 'mismatches', 'outside_exact_limit')),
        'fastModelTransforms': (r'Model zero-translation verification final: (\d+) zero translations compared, (\d+) changed the matrix',
                                ('comparisons', 'mismatches')),
        'fastWorkerAo': (r'Worker AO verification final: (\d+) worker block queries, (\d+) light queries compared, (\d+) mismatches',
                         ('block_comparisons', 'light_comparisons', 'mismatches')),
        'fastMipmaps': (r'Fast mipmaps verification final: (\d+) sprites, (\d+) pixels compared, (\d+) mismatches',
                        ('sprites', 'comparisons', 'mismatches')),
        'fastTerrainPipeline': (r'Terrain pipeline verification final: (\d+) layer counts compared, (\d+) mismatches',
                                ('comparisons', 'mismatches')),
        'flatOcclusion': (r'Flat occlusion verification final: (\d+) mask hits, (\d+) updates, (\d+) mismatches',
                          ('mask_hits', 'updates', 'mismatches')),
        'cameraHysteresis': (r'Chunk visibility verification: (\d+) searches compared; (\d+) mismatched with stable input; (\d+) had compiled chunk data change mid-comparison \((\d+) of those differed\)',
                             ('comparisons_checkpoint', 'stable_mismatches_checkpoint', 'changing_input_comparisons_checkpoint', 'changing_input_differences_checkpoint')),
    }
    results = {}
    for option, (pattern, fields) in patterns.items():
        label = 'q2-verify-' + option
        record = json.loads((HERE / 'launches' / (label + '.json')).read_text())
        log = (HERE / 'logs' / (label + '.log')).read_text(errors='replace')
        matches = list(re.finditer(pattern, log))
        if not matches: raise SystemExit('Missing verifier counters: ' + option)
        counts = dict(zip(fields, map(int, matches[-1].groups())))
        eligible = (record['status'] == 'completed' and counts.get('mismatches') == 0
                    and counts.get('comparisons', counts.get('block_comparisons', 0)) > 0
                    and counts.get('outside_exact_limit', 0) == 0)
        lines = [l for l in log.splitlines() if re.search(
            r'\b(?:mismatch|mismatches|mismatched|difference|differed|changed the matrix|verification failed)\b', l, re.I)
                 and 'initializing:' not in l and 'Environment:' not in l]
        (HERE / 'launches' / (label + '-verifier-lines.txt')).write_text('\n'.join(lines) + '\n')
        results[option] = dict(counts=counts, performance_eligible=eligible, counter_line=matches[-1].group(),
                               final_counter=option != 'cameraHysteresis', verifier_label=label)
    (HERE / 'verification.json').write_text(json.dumps(results, indent=2) + '\n')
    print(json.dumps(results, indent=2))


if __name__ == '__main__':
    main()
