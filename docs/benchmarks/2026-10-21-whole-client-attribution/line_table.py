"""Session 26: derive line -> section tables for the whole-frame classifier from the runtime Minecraft jar's bytecode.

Generalizes session 25's line_table.py (preserved unchanged). The runtime jar named in Loom's runClient argument
file has different line numbers from the decompiled sources jar, so no line is hardcoded. For each anchor method
below, javap's instruction list and LineNumberTable give every instruction's line; vanilla's own profiler calls
(ldc name + Profiler.push/swap, Profiler.pop) bound the sections, tracked as a stack in bytecode order. A line whose
instructions fall in more than one section is reported as boundary:<a>|<b> rather than assigned to either side.
GameRenderer's pass method also maps each terrain-layer call line to its BlockLayer (the getstatic before the call),
because the layer is an argument and not visible in a stack frame. Writes lines.json with the jar path and SHA256.
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

# anchor id -> (class, javap signature prefix)
METHODS = {
    'mc.runGame': ('net.minecraft.client.Minecraft', 'private void runGame();'),
    'mc.tick': ('net.minecraft.client.Minecraft', 'public void tick();'),
    'gr.render': ('net.minecraft.client.render.GameRenderer', 'public void render(float, long);'),
    'gr.renderWorld': ('net.minecraft.client.render.GameRenderer', 'public void renderWorld(float, long);'),
    'gr.pass': ('net.minecraft.client.render.GameRenderer', 'private void render(int, float, long);'),
    'wr.entities': ('net.minecraft.client.render.world.WorldRenderer', 'public void renderEntities('),
    'wr.setupRender': ('net.minecraft.client.render.world.WorldRenderer', 'public void setupRender('),
}
LAYER_CALL = 'net/minecraft/client/render/world/WorldRenderer.render:(Lnet/minecraft/client/render/block/BlockLayer;DILnet/minecraft/entity/Entity;)I'


def runtime_jar():
    args = (ROOT / 'build/loom-cache/argFiles/runClient').read_text()
    jars = [p for p in re.split(r'[;\s"]+', args) if re.search(r'minecraft-merged-[0-9a-f]+-1\.8\.9[^\/]*\.jar$', p)]
    assert len(set(jars)) == 1, jars
    return Path(jars[0])


def method_code(text, signature):
    assert text.count(signature) == 1, signature
    body = text[text.index(signature):]
    end = body.find('\n\n  ', 1)
    body = body if end < 0 else body[:end]
    code = [(int(m[1]), m[2], m[3]) for m in re.finditer(r'^ +(\d+): (\w+) *(.*)$', body.split('LineNumberTable:')[0], re.M)]
    table = sorted((int(b), int(l)) for l, b in
                   re.findall(r'line (\d+): (\d+)', body.split('LineNumberTable:')[1].split('LocalVariableTable:')[0]))

    def line(bci):
        found = None
        for b, l in table:
            if b <= bci:
                found = l
        return found
    return [(bci, op, rest, line(bci)) for bci, op, rest in code]


def sections(code):
    """bci -> section path from vanilla's profiler calls, in bytecode order."""
    stack, pending, out, calls = [], None, {}, []
    for bci, op, rest, line in code:
        out[bci] = '/'.join(stack) if stack else '<none>'
        if op.startswith('ldc') and '// String ' in rest:
            pending = rest.split('// String ', 1)[1]
        elif op.startswith('invoke') and 'util/profiler/Profiler.push:' in rest:
            stack.append(pending); calls.append(['push', pending, bci, line])
        elif op.startswith('invoke') and 'util/profiler/Profiler.swap:' in rest:
            if stack:
                stack[-1] = pending
            else:
                stack.append(pending)
            calls.append(['swap', pending, bci, line])
        elif op.startswith('invoke') and 'util/profiler/Profiler.pop:' in rest:
            if stack:
                stack.pop()
            calls.append(['pop', None, bci, line])
    return out, calls


def line_sections(code, by_bci):
    table, conflicts = {}, {}
    for bci, op, rest, line in code:
        if line is None:
            continue
        s = by_bci[bci]
        table.setdefault(line, set()).add(s)
    result = {}
    for line, names in table.items():
        if len(names) == 1:
            result[str(line)] = next(iter(names))
        else:
            result[str(line)] = 'boundary:' + '|'.join(sorted(names))
            conflicts[str(line)] = sorted(names)
    return dict(sorted(result.items(), key=lambda x: int(x[0]))), conflicts


def layer_lines(code):
    layers, last_layer = {}, None
    for bci, op, rest, line in code:
        if op == 'getstatic' and 'BlockLayer.' in rest:
            last_layer = rest.split('BlockLayer.', 1)[1].split(':', 1)[0]
        if op.startswith('invoke') and LAYER_CALL in rest:
            assert last_layer is not None
            assert str(line) not in layers, 'two layer calls on one line'
            layers[str(line)] = last_layer.lower()
            last_layer = None
    assert sorted(layers.values()) == ['cutout', 'cutout_mipped', 'solid', 'translucent'], layers
    return layers


def main(out=HERE / 'lines.json'):
    jar = runtime_jar()
    texts, result = {}, {'runtime_jar': str(jar), 'runtime_jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
                          'methods': {}}
    for anchor, (cls, signature) in METHODS.items():
        if cls not in texts:
            texts[cls] = subprocess.run([str(JAVAP), '-c', '-p', '-l', '-cp', str(jar), cls],
                                        capture_output=True, text=True, check=True).stdout
        code = method_code(texts[cls], signature)
        by_bci, calls = sections(code)
        table, conflicts = line_sections(code, by_bci)
        entry = {'class': cls, 'signature': signature, 'line_section': table, 'boundary_lines': conflicts,
                 'profiler_calls': calls}
        if anchor == 'gr.pass':
            entry['layer_call_lines'] = layer_lines(code)
        result['methods'][anchor] = entry
    Path(out).write_text(json.dumps(result, indent=1) + '\n')
    print('Wrote', out, {k: len(v['line_section']) for k, v in result['methods'].items()})


if __name__ == '__main__':
    main(*sys.argv[1:])
