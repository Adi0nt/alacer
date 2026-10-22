"""Summarize client-thread JFR execution and allocation samples for the particle session.
usage: python -I jfr_summary.py <jdk>/bin/jfr run/pt-jfr-r1.jfr  (prints a text report)"""
import collections, json, subprocess, sys

jfr, path = sys.argv[1], sys.argv[2]

def events(kind):
    out = subprocess.run([jfr, "print", "--json", "--stack-depth", "64", "--events", kind, path],
                         capture_output=True, check=True).stdout
    return json.loads(out)["recording"]["events"]

def frames(event):
    stack = event["values"].get("stackTrace") or {}
    result = []
    for f in stack.get("frames", []):
        m = f["method"]
        result.append(m["type"]["name"].rsplit(".", 1)[-1] + "." + m["name"])
    return result

def thread(event):
    t = event["values"].get("sampledThread") or event["values"].get("eventThread") or {}
    return t.get("javaName") or t.get("osName") or ""

GROUPS = [
    ("ParticleManager.render (unlit pass)", "ParticleManager.render"),
    ("ParticleManager.renderLit", "ParticleManager.renderLit"),
    ("  Entity.getLightLevel", "Entity.getLightLevel"),
    ("  BlockParticle.getLightLevel", "BlockParticle.getLightLevel"),
    ("  World.getLightColor", "World.getLightColor"),
    ("  BufferBuilder (any method)", "BufferBuilder."),
    ("  Tesselator.end", "Tesselator.end"),
    ("ParticleManager.tick", "ParticleManager.tick"),
    ("  ParticleManager.tickParticle", "ParticleManager.tickParticle"),
    ("  Entity.move", "Entity.move"),
    ("  World.getCollisions", "World.getCollisions"),
    ("  World.getEntities", "World.getEntities"),
    ("  ArrayList.removeAll/batchRemove", "ArrayList.batchRemove"),
    ("  Entity.equals", "Entity.equals"),
    ("ParticleManager.add", "ParticleManager.add"),
    ("Entity.<init> (particle construction)", "Entity.<init>"),
    ("BenchmarkRunner.emitParticles", "BenchmarkRunner.emitParticles"),
    ("WorldRenderer.renderEntities", "WorldRenderer.renderEntities"),
    ("Minecraft.tick", "Minecraft.tick"),
    ("GameRenderer.render", "GameRenderer.render"),
]

def report(kind, weight):
    evs = [e for e in events(kind) if thread(e) == "main"]
    total = sum(weight(e) for e in evs)
    inclusive = collections.Counter()
    top = collections.Counter()
    for e in evs:
        fs = frames(e)
        w = weight(e)
        if fs:
            top[fs[0]] += w
        seen = set()
        for label, needle in GROUPS:
            if any(needle in f for f in fs) and label not in seen:
                inclusive[label] += w
                seen.add(label)
    print(f"## {kind} on the client thread (main): {len(evs)} samples" + (f", {total/2**20:.1f} MiB sampled weight" if kind != "jdk.ExecutionSample" else ""))
    print()
    print("Inclusive (stack contains):")
    for label, _ in GROUPS:
        print(f"  {label:45} {inclusive[label] / total * 100:6.1f}%")
    print()
    print("Top self frames:")
    for name, w in top.most_common(25):
        print(f"  {name:70} {w / total * 100:6.1f}%")
    print()

report("jdk.ExecutionSample", lambda e: 1)
report("jdk.ObjectAllocationSample", lambda e: e["values"].get("weight", 0))
