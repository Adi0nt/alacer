# Session 20 verification

## Design

`render.fastParticleVertices.verify=true` (requires the option on) lets **vanilla** write every
element of each qualifying vertex and keeps vanilla's bytes. Before each vanilla element call the
candidate's bytes for the same call are computed with `ParticleVertexWriter` into a 28-byte
scratch buffer in the same byte order as the real buffer, from the same arguments and buffer
offsets; after the call they are compared byte by byte with what vanilla wrote at the element's
offset in the current vertex. A vertex counts as complete only when its position, texture,
color and light calls arrive in that order at element indices 0, 1, 2, 3 of the same vertex and
the element index is back at 0 afterwards (what the direct path leaves). Fallback vertices
(another format, `uncolored`, another thread, element index not 0) and passes whose particle
format layout differs are counted. Mismatches log the element and both byte strings.

The unit tests independently compare the writer with a real vanilla `BufferBuilder` (no mixins
in tests) for 24,000 quads written at vertex 1 and 2 after a vertex 0, with random values and
edge values: signed zero, NaN, infinities, subnormals, `Float.MAX_VALUE`, colors outside 0-1
(vanilla's int truncation and byte wrap), light coordinates outside 16 bits, and nonzero, huge,
tiny and NaN buffer offsets. The layout check accepts `PARTICLE` and an equal copy and rejects
a padded copy, a reordered layout, `POSITION_TEX_COLOR` and an empty format.

## Live results

| Launch | Source | Scale / diagnostics | Elements compared | Complete vertices | Incomplete | Vanilla vertices | Layout-rejected passes | Mismatches |
|---|---|---|---:|---:|---:|---:|---:|---:|
| pv20-check-2 | frozen 21fb3f8c | 2, light + removal diagnostics | 950,264,304 | 237,566,076 | 0 | 0 | 0 / 16,457 | **0** |
| pv20-pilot-verify | pre-freeze | 1, none | 1,139,892,640 | 284,973,160 | 0 | 0 | 0 / 27,505 | **0** |

The diagnostics in `pv20-check-2` cycled the blocks under the 16 emitters 203 times
(glowstone, slab, air: block light and neighbor-lit light), added particles above the build
height and below y = 0, and added 7,235 probe particles: 33,267 layer moves while ticking (3,122
on the dying tick), 43,904 additions to full lists while ticking, 786 deaths with a live equal-id
twin, 795 duplicates and 80 `equals`-overriding probes. Every probe renders through
`Particle.render`, so their vertices were compared too.

## Coverage and limits

- Covered render code: `Particle.render` (block-free billboard particles and every subclass that
  calls `super.render`: smoke, flame, redstone, explosion in the scenario; cloud, crit, emotion,
  spell, portal, etc. share the same code) and `BlockParticle.render` (block break).
- Not covered (vanilla path): render methods that build their own vertices without
  `super.render` (barrier, firework overlay/spark subclasses' own code, large explosion,
  item pickup, ...), which keep vanilla's element path; the lit pass's own renderers.
- Comparison is of the vertex buffer bytes, which are what `Tesselator.end` uploads; the
  framebuffer was not compared. Draw calls, state changes and particle order are unchanged.
- A format object changed during a pass, another mod's `@Redirect` of the same calls, and
  skyless dimensions/multiplayer were not exercised.
