#!/usr/bin/env python3
"""Session 28: classify one launch's client-thread JFR allocation samples by phase, path and named set.

Usage: python3 classify.py <record.json | attempt.json> <out.json> --jdk <jdk25>

Samples come from AllocSamples.java (all jdk.ObjectAllocationSample events, weights in bytes). A sample belongs to
a phase when its wall time lies in [epoch + first frame start, epoch + last frame start + last frame time]; the
profiler epoch is known to about 1 ms, which is irrelevant at 5-80 s phases. Paths: 'tick' = the stack contains
Minecraft.tick (runs at 20 Hz however fast frames are), 'frame' = every other client-thread sample (the per-frame
path: rendering, scheduled tasks, profiler/benchmark hooks). Named sets are defined below, before measurement.
"""
import argparse
import csv
import gzip
import json
from collections import defaultdict
from pathlib import Path
import subprocess

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent.parent
# JFR keeps the name the thread had when first seen: the client thread is 'main' until Minecraft renames it.
CLIENT_THREADS = ('Client thread', 'main')

MC_TICK = 'net.minecraft.client.Minecraft.tick'
SHADOW = 'net.minecraft.client.render.entity.EntityRenderer.renderShadow'
ENTITY_LIGHT = ('net.minecraft.entity.Entity.getBrightness', 'net.minecraft.entity.Entity.getLightLevel')
APPLY_ROTATION = 'net.minecraft.client.render.entity.LivingEntityRenderer.applyRotation'
STRIP = 'net.minecraft.text.Formatting.strip'
GET_NAME = 'net.minecraft.entity.Entity.getName'
PICK = 'net.minecraft.client.render.GameRenderer.pick'
WORLD_RAYTRACE = 'net.minecraft.world.World.rayTrace'
OPEN_FACES = 'net.minecraft.client.render.world.WorldRenderer.getOpenChunkFaces'
DIRECTION_VALUES = 'net.minecraft.util.math.Direction.values'
RENDER_ENTITIES = 'net.minecraft.client.render.world.WorldRenderer.renderEntities'
SETUP_RENDER = 'net.minecraft.client.render.world.WorldRenderer.setupRender'
BLOCKPOS_CLASSES = ('net.minecraft.util.math.BlockPos', 'net.minecraft.util.math.BlockPos$Mutable')
POS_METHOD_PREFIXES = ('net.minecraft.util.math.BlockPos.', 'net.minecraft.util.math.Vec3i.',
                       'net.minecraft.util.math.BlockPos$Mutable.')


def method(frame):
    return frame.rsplit(':', 1)[0]


def methods(frames):
    return [method(f) for f in frames]


def first_non_pos(ms):
    """Index of the first frame (leaf-first) outside BlockPos/Vec3i methods."""
    for i, m in enumerate(ms):
        if not m.startswith(POS_METHOD_PREFIXES):
            return i
    return len(ms)


def sets_for(cls, ms):
    """Named sets a sample belongs to (fixed before measurement; see protocol.md)."""
    out = []
    present = set(ms)
    i = first_non_pos(ms)
    caller = ms[i] if i < len(ms) else None
    pos_alloc = cls in BLOCKPOS_CLASSES
    offset_chain = any(m.endswith(('.down', '.up', '.offset', '.east', '.west', '.north', '.south')) for m in ms[:i])
    # A: BlockPos made by an offset (down) directly in renderShadow (the iterator's own Mutable is not included).
    if SHADOW in present:
        out.append('shadow.all')
        if pos_alloc and caller == SHADOW and offset_chain:
            out.append('shadow.offset_pos')
    # B: entity brightness for rendering: Entity.getBrightness/getLightLevel and the world light reads beneath them.
    if present.intersection(ENTITY_LIGHT):
        out.append('entity_light.all')
        if pos_alloc:
            out.append('entity_light.pos')
    # C: the Dinnerbone/Grumm name check in LivingEntityRenderer.applyRotation.
    if APPLY_ROTATION in present:
        out.append('name_check.all')
        if STRIP in present:
            out.append('name_check.strip')
        elif GET_NAME in present:
            out.append('name_check.get_name')
    # D: the per-frame crosshair pick.
    if PICK in present:
        out.append('pick.all')
        if WORLD_RAYTRACE in present:
            out.append('pick.block_trace')
        else:
            out.append('pick.other')
    # E: vanilla's camera-section flood fill (reached through optimization 1's open-faces cache misses).
    if OPEN_FACES in present:
        out.append('flood.all')
        if DIRECTION_VALUES in present:
            out.append('flood.direction_values')
    # Aggregates, reported and never selectable by themselves.
    if RENDER_ENTITIES in present:
        out.append('agg.render_entities')
    if SETUP_RENDER in present:
        out.append('agg.setup_render')
    if any(m.startswith('net.adiont.alacer.profiler.') for m in ms):
        out.append('agg.alacer_profiler')
    if any(m.startswith('net.adiont.alacer.benchmark.') for m in ms):
        out.append('agg.alacer_benchmark')
    return out


def site_key(cls, ms):
    """Allocation site for the top-sites table: class plus the first three frames outside the JDK."""
    own = [m for m in ms if not m.startswith(('java.', 'jdk.', 'sun.'))][:3]
    return cls + ' @ ' + ' < '.join(own)


def load_samples(jfr, cache, jdk):
    if not cache.exists():
        subprocess.run([str(Path(jdk) / 'bin/java'), str(HERE / 'AllocSamples.java'), str(jfr), str(cache)], check=True)
    threads = {}
    samples = []
    with gzip.open(cache, 'rt', encoding='utf-8') as f:
        for line in f:
            parts = line.rstrip('\n').split('\t')
            if parts[0] == '#thread':
                threads[parts[1]] = {'samples': int(parts[2]), 'weight': int(parts[3])}
                continue
            if parts[0] not in CLIENT_THREADS:
                continue
            frames = parts[5].split('|') if len(parts) > 5 and parts[5] else []
            samples.append((float(parts[1]), int(parts[2]), parts[3], int(parts[4]), frames))
    return samples, threads


def phase_windows(folder, summary):
    epoch = int(summary['environment']['profiler.epochWallMillis'])
    out = {}
    for name in summary['phases']:
        with open(folder / (name + '.csv'), newline='') as f:
            rows = list(csv.DictReader(f))
        first = int(rows[0]['start_ns'])
        last = int(rows[-1]['start_ns']) + int(rows[-1]['frame_ns'])
        ticking = sum(1 for r in rows if int(r['tick_ns']) > 0)
        alloc = [int(r['alloc_bytes']) for r in rows]
        out[name] = {'start_ms': epoch + first / 1e6, 'end_ms': epoch + last / 1e6, 'frames': len(rows),
                     'ticking_frames': ticking, 'csv_alloc_mean_bytes': sum(alloc) / len(alloc),
                     'frame_mean_ms': sum(int(r['frame_ns']) for r in rows) / len(rows) / 1e6}
    return out


def classify(record_path, jdk):
    record = json.loads(Path(record_path).read_text())
    if 'record' in record:  # smoke attempt
        record = record['record']
    folder = ROOT / record['source']
    summary = json.loads((folder / 'summary.json').read_text())
    jfr = ROOT / record['jfr']
    cache = ROOT / 'build' / (record['label'] + '.alloc.tsv.gz')
    samples, threads = load_samples(jfr, cache, jdk)
    windows = phase_windows(folder, summary)
    result = {'label': record['label'], 'scene': record['scene'], 'threads': threads, 'phases': {}}
    for name, w in windows.items():
        tot = defaultdict(lambda: [0, 0])
        sets = defaultdict(lambda: [0, 0])
        sites = defaultdict(lambda: [0, 0])
        truncated = 0
        for t, weight, cls, trunc, frames in samples:
            if not (w['start_ms'] <= t < w['end_ms']):
                continue
            ms = methods(frames)
            path = 'tick' if MC_TICK in ms else 'frame'
            truncated += trunc
            tot[path][0] += 1
            tot[path][1] += weight
            if path != 'frame':
                continue
            for s in sets_for(cls, ms):
                sets[s][0] += 1
                sets[s][1] += weight
            k = site_key(cls, ms)
            sites[k][0] += 1
            sites[k][1] += weight
        frame_w = tot['frame'][1]
        frames = w['frames']
        phase = dict(w)
        phase.update({
            'truncated_samples': truncated,
            'samples': {p: v[0] for p, v in tot.items()},
            'jfr_bytes_per_frame': {p: v[1] / frames for p, v in tot.items()},
            'jfr_total_bytes_per_frame': sum(v[1] for v in tot.values()) / frames,
            'sets': {s: {'samples': v[0], 'share_percent': 100.0 * v[1] / frame_w if frame_w else 0.0,
                         'bytes_per_frame': v[1] / frames} for s, v in sorted(sets.items())},
            'top_sites': [{'site': k, 'samples': v[0], 'share_percent': 100.0 * v[1] / frame_w if frame_w else 0.0,
                           'bytes_per_frame': v[1] / frames}
                          for k, v in sorted(sites.items(), key=lambda kv: -kv[1][1])[:30]],
        })
        result['phases'][name] = phase
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('record')
    parser.add_argument('out')
    parser.add_argument('--jdk', required=True)
    opts = parser.parse_args()
    Path(opts.out).parent.mkdir(parents=True, exist_ok=True)
    Path(opts.out).write_text(json.dumps(classify(opts.record, opts.jdk), indent=1) + '\n')
