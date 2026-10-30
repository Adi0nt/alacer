"""Read third-party mod JARs as data: metadata, bytecode target, intermediary references vs the Gen 2 game JAR."""
import json, re, sys, zipfile
from pathlib import Path

base = Path(sys.argv[1]); game = Path(sys.argv[2])
with zipfile.ZipFile(game) as g:
    game_classes = {n[:-6] for n in g.namelist() if n.endswith('.class')}
    game_text = None
unmapped = {c for c in game_classes if c.startswith('net/minecraft/unmapped/')}
report = {}
for jar in sorted(base.glob('*/*.jar')):
    r = report[jar.parent.name] = {}
    with zipfile.ZipFile(jar) as z:
        names = z.namelist()
        fmj = json.loads(z.read('fabric.mod.json').decode('utf-8')) if 'fabric.mod.json' in names else None
        r['fabric.mod.json'] = {k: fmj.get(k) for k in ('id', 'version', 'environment', 'depends', 'recommends', 'breaks', 'conflicts', 'mixins', 'entrypoints', 'accessWidener', 'jars', 'custom')} if fmj else None
        majors, refs, own = {}, set(), set()
        for n in names:
            if n.endswith('.class'):
                b = z.read(n)
                major = int.from_bytes(b[6:8], 'big'); majors[major] = majors.get(major, 0) + 1
                own.add(n[:-6])
                refs |= set(re.findall(rb'net/minecraft/unmapped/C_\d+', b))
                refs |= set(x for x in re.findall(rb'net/minecraft/[A-Za-z0-9_/$]+', b) if b'unmapped' not in x)
        r['class_major_versions'] = majors
        refs = {x.decode() for x in refs}
        named = sorted(x for x in refs if not x.startswith('net/minecraft/unmapped/') and x.rstrip('/') not in game_classes and not x.endswith('/'))
        um = {x for x in refs if x.startswith('net/minecraft/unmapped/')}
        r['intermediary_refs'] = len(um)
        r['intermediary_missing_in_gen2_game'] = sorted(um - unmapped)
        r['other_net_minecraft_refs_missing'] = named[:20]
        mixins = []
        for n in names:
            if n.endswith('.json') and n not in ('fabric.mod.json',) and 'mixin' in n.lower() and 'refmap' not in n.lower():
                try:
                    cfg = json.loads(z.read(n)); mixins.append({'config': n, 'package': cfg.get('package'), 'mixins': cfg.get('mixins'), 'client': cfg.get('client'), 'server': cfg.get('server'), 'refmap': cfg.get('refmap'), 'plugin': cfg.get('plugin'), 'required': cfg.get('required')})
                except Exception as e: mixins.append({'config': n, 'error': str(e)})
        r['mixin_configs'] = mixins
        targets = set()
        for n in names:
            if n.endswith('.class'):
                b = z.read(n)
                if b'Lorg/spongepowered/asm/mixin/Mixin;' in b:
                    targets |= {x.decode() for x in re.findall(rb'Lnet/minecraft/unmapped/C_\d+;', b)[:1]}
        r['nested_jars'] = [n for n in names if n.startswith('META-INF/jars/')]
print(json.dumps(report, indent=1))
