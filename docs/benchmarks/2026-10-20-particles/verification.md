# Particle light verification

`pt-verify-r1`: the particle scenario at scale 1 plus `benchmark.particleDiagnostic=true`, with
`render.fastParticleLight=true` and `render.fastParticleLight.verify=true` (every answered query
also runs vanilla's `World.getLightColor`, which is used, and the two are compared). Full static
(10 s) and pan (15 s) durations; it is a correctness run and is excluded from all performance tables.
Same frozen build as the measured series (`implementation.json`, `source_sha256`).

The diagnostic adds 16 smoke particles per tick at y = 255.2 (they rise above the build limit, where
vanilla answers the light type's default) and 16 flames per tick at y = -1.5 (vanilla clamps the
query to y = 0), and every 30 frames the block under each of the 16 emitters cycles through glowstone
(block light), stone slab (neighbor-lit) and air on the integrated server, so block states and light
around the particles change while they are drawn. The emitters cover a 3x3 group of chunk columns.
Vanilla's neighbor-lit branch is common in the ordinary scene too: in `pt-jfr-r1`, `BlockPos.offset`
(the five neighbor copies) is 38% of the client thread's sampled allocation (`jfr-summary.txt`).

Launch log (`build/pt-verify-r1.log`), final lines:

```
Particle light queries final: 66729546 queries compared, 0 mismatched
Benchmark: particle diagnostic toggled 16 cells 267 times
```

192,390 particles were emitted over 795 client ticks; 3,866 (static) and 3,885 (pan) were drawn per
frame on average. No GL error, rendering/ticking crash or mismatch line was logged.

Not covered by this run: dimensions without sky light (the Nether/End branch is the same `hasNoSky`
check as vanilla but was not exercised), worlds that are subclasses of `ClientWorld` (they use
vanilla's path by design), and other threads (only the client thread uses the cache; others take
vanilla's path). The comparison establishes identical light values, from which vanilla builds the
lightmap texture coordinates; it does not compare framebuffers.
