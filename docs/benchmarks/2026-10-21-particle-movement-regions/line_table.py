"""Session 25: derive Entity.move's line -> region table from the runtime Minecraft jar's own bytecode.

The runtime jar named in Loom's runClient argument file differs from the decompiled sources jar's line map,
so lines are never hardcoded. javap's instruction list and LineNumberTable give each instruction's line;
landmark invocations (identified by owner, name and ordinal) bound the regions. Writes lines.json with the
jar path and SHA256, every landmark and any line claimed by more than one region.
"""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
JAVAP = Path('C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/javap.exe')


def runtime_jar():
    args = (ROOT / 'build/loom-cache/argFiles/runClient').read_text()
    jars = [p for p in re.split(r'[;\s"]+', args) if re.search(r'minecraft-merged-[0-9a-f]+-1\.8\.9[^\\/]*\.jar$', p)]
    assert len(set(jars)) == 1, jars
    return Path(jars[0])


def method_code(text, signature):
    start = text.index(signature)
    body = text[start:]
    end = body.find('\n\n  ', 1)
    body = body if end < 0 else body[:end]
    code = {}
    for m in re.finditer(r'^ +(\d+): (\w+) *(.*)$', body.split('LineNumberTable:')[0], re.M):
        code[int(m[1])] = (m[2], m[3])
    lines = sorted((int(b), int(l)) for l, b in re.findall(r'line (\d+): (\d+)', body.split('LineNumberTable:')[1].split('LocalVariableTable:')[0]))
    def line(bci):
        found = None
        for b, l in lines:
            if b <= bci:
                found = l
        return found
    return [(bci, op, rest, line(bci)) for bci, (op, rest) in sorted(code.items())]


def landmarks(code):
    def calls(owner_name):
        return [c for c in code if c[1].startswith('invoke') and owner_name in c[2]]
    def field(name):
        return [c for c in code if c[1] == 'getfield' and name in c[2]]
    found = {
        'push': calls('Profiler.push:'), 'pop': calls('Profiler.pop:'),
        'getCollisions': calls('World.getCollisions:'), 'setShape': calls('Method setShape:'),
        'setPositionFromShape': calls('Method setPositionFromShape:'), 'floor': calls('MathHelper.floor:'),
        'checkFallDamage': calls('Method checkFallDamage:'), 'beforeCollision': calls('Block.beforeCollision:'),
        'checkBlockCollisions': calls('Method checkBlockCollisions:'), 'isInWaterOrRain': calls('Method isInWaterOrRain:'),
        'containsFireSource': calls('World.containsFireSource:'), 'stepHeight': field('Field stepHeight:'),
        'getBlockState': calls('World.getBlockState:'),
    }
    counts = {k: len(v) for k, v in found.items()}
    expected = {'push': 2, 'pop': 2, 'getCollisions': 5, 'setPositionFromShape': 2, 'checkFallDamage': 1,
                'beforeCollision': 1, 'checkBlockCollisions': 1, 'isInWaterOrRain': 1, 'containsFireSource': 1,
                'getBlockState': 2}
    for k, v in expected.items():
        assert counts[k] == v, (k, counts[k])
    return found


def regions(code, lm):
    gather = lm['getCollisions'][3]
    clip_end = [c for c in lm['setShape'] if c[0] > gather[0]][2]
    step = [c for c in lm['stepHeight'] if c[0] > clip_end[0]][0]
    rest = lm['setPositionFromShape'][1]
    under = [c for c in lm['floor'] if c[0] > rest[0]][0]
    bounds = [
        ('noclip', 0), ('pre', lm['push'][0][0]), ('gather', None), ('clip_publish', None),
        ('step', step[0]), ('profiler_tail', lm['pop'][0][0]), ('position_flags', rest[0]), ('block_under', under[0]),
        ('fall_damage', None), ('velocity_vertical_callback', None), ('step_sounds', None), ('block_collisions', None),
        ('water_rain', None), ('fire', None), ('fire_effects', None), ('profiler_tail', lm['pop'][1][0]),
    ]
    def line_start(call):
        return min(c[0] for c in code if c[3] == call[3])
    def line_end(call):
        return max(c[0] for c in code if c[3] == call[3])
    starts = {
        'gather': line_start(gather), 'clip_publish': line_end(gather) + 1,
        'fall_damage': line_start(lm['checkFallDamage'][0]), 'velocity_vertical_callback': line_end(lm['checkFallDamage'][0]) + 1,
        'step_sounds': line_end(lm['beforeCollision'][0]) + 1, 'block_collisions': line_start(lm['checkBlockCollisions'][0]),
        'water_rain': line_start(lm['isInWaterOrRain'][0]), 'fire': line_start(lm['containsFireSource'][0]),
        'fire_effects': line_end(lm['containsFireSource'][0]) + 1,
    }
    bounds = [(name, start if start is not None else starts[name]) for name, start in bounds]
    assert [b for _, b in bounds] == sorted(b for _, b in bounds), bounds
    table, conflicts = {}, {}
    for bci, op, rest_text, line in code:
        region = [name for name, start in bounds if start <= bci][-1]
        if line in table and table[line] != region:
            conflicts.setdefault(str(line), sorted({table[line], region}))
        table.setdefault(line, region)
    # A line spanning a region boundary is reported on its own rather than assigned to either side.
    for line, names in conflicts.items():
        table[int(line)] = 'boundary:' + '/'.join(names)
    return bounds, table, conflicts


def main(out=HERE / 'lines.json'):
    jar = runtime_jar()
    entity = subprocess.run([str(JAVAP), '-c', '-p', '-l', '-cp', str(jar), 'net.minecraft.entity.Entity'],
                            capture_output=True, text=True, check=True).stdout
    code = method_code(entity, 'public void move(double, double, double);')
    lm = landmarks(code)
    bounds, table, conflicts = regions(code, lm)
    result = {'runtime_jar': str(jar), 'runtime_jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
              'region_start_bci': bounds, 'line_region': {str(k): v for k, v in sorted(table.items())},
              'conflicting_lines': conflicts,
              'landmark_lines': {k: [[c[0], c[3]] for c in v] for k, v in lm.items()}}
    Path(out).write_text(json.dumps(result, indent=1) + '\n')
    print('Wrote', out, len(table), 'lines,', len(conflicts), 'conflicts')


if __name__ == '__main__':
    main(*sys.argv[1:])
