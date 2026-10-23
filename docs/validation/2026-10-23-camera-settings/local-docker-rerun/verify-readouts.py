"""Check archived live readouts; screenshot appearance is reviewed separately.

Usage: python3 -I docs/validation/2026-10-23-camera-settings/local-docker-rerun/verify-readouts.py
"""
from pathlib import Path
import json
import re
import struct

root = Path(__file__).resolve().parent
full = (root / "results-full.txt").read_text()
readback = (root / "results-readback.txt").read_text()
assert full.rstrip().endswith("DONE") and readback.rstrip().endswith("DONE")
assert "FAILED" not in full + readback


def offset(label):
    match = re.search(re.escape(label) + r" offset=\(([^)]+)\)", full)
    assert match, label
    return [float(x) for x in match[1].split(",")]


for strength, expected in ((100, .05), (50, .025), (0, 0)):
    assert abs(offset(f"viewBobbing={strength}")[2] - expected) < .0001
assert max(abs(a - b) for a, b in zip(offset("viewBobbing=0"), offset("vanilla View bobbing Off"))) < .0001
front8 = offset("front view thirdPersonDistance=8")
front4 = offset("front view thirdPersonDistance=4")
assert abs(front8[0] - front4[0] - 4) < .01
assert abs(front8[0] - 7.95) < .01
clipped = offset("wall behind collision=1 distance=4")
unclipped = offset("wall behind collision=0 distance=4")
assert abs(clipped[0]) < abs(unclipped[0]) - 1
assert abs(unclipped[0] + 3.95) < .01

duplicates = 0
for label, rate in (("Vanilla", .5), ("Instant", 1), ("Smooth", .25), ("Very_smooth", .125)):
    match = re.search(r"fovTransition=" + label + r" per-tick fov ([^\n]+)", full)
    assert match, label
    values = [float(x) for x in match[1].split()]
    if rate == 1:
        assert all(abs(x - 1.1) < .0001 for x in values[1:])
        continue
    checked = 0
    for before, after in zip(values, values[1:]):
        if before == after:
            duplicates += 1  # World-time observation can repeat the most recent renderer FOV sample.
            continue
        if 1.1 - before > .005:
            assert abs((after - before) / (1.1 - before) - rate) < .003, (label, before, after)
            checked += 1
    assert checked >= 4, (label, checked)

for factor, world in ((2, 35), (4, 17.5), (8, 8.75)):
    match = re.search(rf"zoom={factor}x held: world=([\d.]+) hand=([\d.]+) pressed=true", full)
    assert match and abs(float(match[1]) - world) < .0001 and float(match[2]) == 70
assert "released: world=70.0 hand=70.0 pressed=false" in full
saved = re.search(r"^saved (.+)$", full, re.M)[1]
assert "relaunch " + saved + " restartRequired=false" in readback
defaults = re.search(r"^defaults (.+)$", full, re.M)[1]
assert "restored " + defaults in readback
for percent in (100, 50, 0):
    assert f"distortion={percent} portalTime=0.8" in full

pngs = sorted((root / "screenshots").glob("*.png"))
assert len(pngs) == 18
for p in pngs:
    data = p.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    assert struct.unpack(">II", data[16:24]) == (854, 480), p
print(json.dumps({"readouts_pass": True, "screenshots": len(pngs),
    "fov_repeated_observations": duplicates,
    "limits": "Synthetic walking/effect/key state; visual inspection separate; no pixel-equivalence or performance claim"}, indent=2))
