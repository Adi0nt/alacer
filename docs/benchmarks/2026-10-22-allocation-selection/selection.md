# Session 28 selection (items 3 and 4, written after decision.py)

`decision.py` output: [decision.json](decision.json). Items 1-2 pass for **`shadow.offset_pos` only**.

| Set | d200 static share r1 / r2 (samples) | d200 pan share r1 / r2 (samples) | Bytes per frame (median) | Items 1-2 |
|---|---|---|---|---|
| `shadow.offset_pos` | 22.1 / 15.5% (516 / 527) | 22.9 / 20.8% (847 / 859) | 19,710 static, 21,474 pan | pass (2 combinations) |
| `entity_light.pos` | 12.8 / 7.9% | 11.9 / 10.4% | 10,740 static, 10,921 pan | fail (pan only) |
| `name_check.strip` | 8.6% median | 8.9% median | 8,812 static, 8,709 pan | fail |
| `pick.block_trace` | 1.0% | 0.8% | below 1,100 everywhere (5.4% of d0 static) | fail |

Sampler corroboration (item 2): the JFR estimate of all client-thread bytes per frame was 1.000-1.005 times the
ThreadMXBean `alloc_bytes` mean in every launch and phase.

**Item 3, exactness of `shadow.offset_pos`.** In the runtime jar's `EntityRenderer.renderShadow`, bytecode 299-308:
`astore 33` (the iterator's `BlockPos.Mutable`), `aload 10` (the world), `aload 33`, `invokevirtual BlockPos.down`,
`invokevirtual World.getBlockState`. The `down()` result is consumed by that one call and never stored; the iterator's
position (local 33), not the `down()` result, is what `getRawBrightness` and `renderShadowOnBlock` receive. The call
is the one optimization 6 redirects to `EntityShadows.blockState`, which reads only the coordinates (its fast path) or
passes the position to vanilla `World.getBlockState` (option off at runtime, or a world that is not exactly
`ClientWorld`), which reads the coordinates as well. Supplying a reused `BlockPos.Mutable` set to
`(x, y - 1, z)` for exactly `ClientWorld` therefore removes every sampled object in the set, with the same query, the
same order and the same answer. `BlockPos.down()` is `offset(DOWN, 1)`, a new `BlockPos(x, y - 1, z)` with no range
check, so coordinates match for every input, including y = 0.

**Item 4.** Not on the closed list. It removes only the argument allocation; which shadow queries run and how they are
answered stay optimization 6's ("shadow queries beyond optimization 6" stay closed). It is no prior experiment.

**Named candidate: `shadow.offset_pos`**, minimum qualifying share 15.5%. Not named: `entity_light.pos` (qualifies
only in d200 pan), `name_check.strip`, `pick.block_trace`. The flood-fill sets are event-driven and not ranked here:
4.8 MB/s in fly at this frame rate (`Direction.values()` copies 3.0 MB/s), about 4.3 KB of allocation per
`getOpenChunkFaces` call; their size per frame on a GPU machine depends on its frame rate and needs measuring there.

Large aggregates outside the named sets, not selectable: `setup_render` is 20-47 KB per frame even with a still camera
(63% of d0 static frame-path allocation). Its top sites are `BlockModelRenderer.tesselateWithAmbientOcclusion`
position offsets, `World.findLight`/`updateLight` and `ChunkOcclusionGraph`, that is, chunk rebuilds and light updates
running on the client thread at this frame rate, work that runs per rebuilt chunk rather than per frame.
