# Particle vertex NaN comparison investigation (2026-10-20)

The intermittent `ParticleVertexWriterTest.edgeValuesMatchVanilla` failure is an
invalid raw-NaN equality requirement, not a color or light conversion defect.
The original strict test failed under C1 before the change. An independent raw
probe reproduced the reported index 35 / expected 127 / actual -1 signature.
Tests and the optional runtime verifier now ignore only NaN-to-NaN representation
differences in the five FLOAT lanes. Every other bit must match. The four direct
write methods have unchanged bytecode; optimization 9 remains on by default.

## Cause and exact input

A vertex is 28 bytes. Byte 35 is `28 + 4 + 3`: the last byte of vertex 1's
Y-position FLOAT on this little-endian machine. It is not a color byte or a light
short. The entire lane is `0x7fc00000` versus `0xffc00000`, so its bytes are
`00 00 c0 7f` versus `00 00 c0 ff`. Both values are quiet NaNs. Byte 63 is the
same Y-position lane in vertex 2 (`56 + 4 + 3`). Vertex 2 writes `(y, z, x)`, so
its Y input is the original `z`.

`raw-c2.log` captures the reported direction at zero-based seeded iteration 2362
(case 2363) with these exact inputs:

- `offsetX = 0x7ff8000000000000`, `offsetY = 0xfff8000000000000`,
  `offsetZ = 0x7ff8000000000000` (raw double bits).
- `(x, y, z) = (0x3f800001, 0x7fc00000, 0x7fc00000)` (raw float bits).
- `(u, v) = (0x1.fep7, -0x1.0p0)`.
- `(red, green, blue, alpha) = (NaN, 0x1.fep7, -0x1.2a05f2p31, Infinity)`.
- `(lightU, lightV) = (0, 1)`.

Both implementations evaluate `(float)(y + offsetY)`: positive NaN plus negative
NaN. The test creates the latter with `-offset` when `offset = Double.NaN`.
Interpreted/C1/C2 execution can select different NaN encodings for the same
expression. This explains why a fixed Random seed does not stabilize raw bytes.
C1 also reproduces the reverse direction, first at iteration 129 in the probe.
The historical failure did not record its input row, so its precise iteration
cannot be recovered from that log; the captures here reproduce its signature
and establish the inputs independently.

Java specifies NaN results without requiring a particular sign/payload for
arithmetic results or NaN primitive conversions. See
[JLS 25, 4.2.3](https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.2.3),
[JLS 25, 5.1.3](https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.3),
and [Float raw-bit semantics](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Float.html#floatToRawIntBits(float)).
The measurements establish tier-dependent NaN representation selection; they do
not establish a compiler defect. No color, light, finite-value, signed-zero or
infinity mismatch was found.

## Raw reproductions

All runs used Windows x64, native little endian, Temurin 25.0.4.1+1-LTS, with the
project's actual remapped vanilla `BufferBuilder` and actual `ParticleVertexWriter`.
`ParticleVertexWriterProbe.java` retains the original seed-20 edge generator and
three-vertex write sequence. It counts raw mismatches, prints exact input bits,
and independently compares all FLOAT lanes allowing only NaN-to-NaN differences;
packed color/light stay exact. It does not call the new comparison helper.
Each repetition restarts `Random(20)` and writes 4,000 cases.

| Process | JVM flags | Cases | Raw mismatching cases | Value mismatches |
| --- | --- | ---: | ---: | ---: |
| Default 1 | default tiered | 400,000 | 20 | 0 |
| Default 2 | default tiered | 400,000 | 2,488 | 0 |
| Default 3 | default tiered | 400,000 | 30 | 0 |
| Default 4 | default tiered | 400,000 | 2,515 | 0 |
| Default 5 | default tiered | 400,000 | 2,450 | 0 |
| C1 | `-XX:TieredStopAtLevel=1` | 400,000 | 5,699 | 0 |
| C2 | `-XX:-TieredCompilation -Xbatch` | 400,000 | 5,670 | 0 |
| Interpreter | `-Xint` | 40,000 | 0 | 0 |

Total: 2,840,000 cases / 8,520,000 vertices, 18,872 raw mismatching cases,
zero mismatches under the value comparison. Mismatch counts and first failing
iterations depend on compilation timing and are not reproducibility gates.
`probe-results.json` and `raw-*.log` retain these captures.

`before-c1-test.xml` and `before-c1-gradle.log` retain the unmodified test failure
at byte 63 (the same NaN sign issue). The original session 22 log remains at
`build/lr22-build-2.log`; it was not changed.

## Change and validation

`ParticleVertexWriter.firstMismatch` is used by both the test and
`ParticleVertices` verification. It accepts different bits only when both values
in the same position/UV0 FLOAT lane are NaNs. It never normalizes the actual
written buffer. It rejects NaN-to-finite/infinity changes, signed-zero changes,
opposite infinities, arbitrary color/light changes and every individual byte
corruption, with regression controls for both buffer byte orders and different
vertex base offsets.

- The original strict C1 test failed before the fix.
- All four focused tests passed under default, `-Xint`, C1 and C2 flags; the
  `after-*-test.xml` and Gradle logs record the matrix.
- `gradlew.bat build --rerun-tasks` passed on explicit Java 25. The final full
  build also passed with **81 tests, zero failures/errors**; see `final-build.log`,
  `final-test.xml` and `final-suite-results.json`.
- `runtime-and-bytecode.json` records `javap -c -p` equality of `position`,
  `texture`, `color` and `light` before/after, and preserved existing line endings.
- `reproduce.ps1` was executed successfully against the fixed project. It still
  reproduces raw NaN differences, independently of the fixed assertion.

The shipping writer can write different raw NaN bits for pathological game
inputs too; the old blanket byte-for-byte claim was too strong. These runs found
no finite-input correctness defect and justify repairing the comparison rather
than adding branches or canonicalization to the writing path. A NaN remains NaN
in either path. No game was launched for this task, and no new transformed-client,
framebuffer or performance evidence is claimed. The modified live verifier was
validated through its shared comparison helper, not a new client launch. Earlier
client measurements and frozen Java 8 archives were not rerun or rewritten.

## Reproduce

From the repository root in PowerShell:

```powershell
& ./docs/benchmarks/2026-10-20-particle-vertex-nan/reproduce.ps1
```

The script explicitly sets `JAVA_HOME` to
`C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot`, runs the focused test,
exports its real runtime classpath using the temporary Gradle init script,
compiles the archived probe with that runtime's `javac --release 25`, and runs
fresh default/C1/C2/interpreted JVMs. Output is under
`build/particle-vertex-nan-repro/`; archived captures are not overwritten. A
nonzero value mismatch fails the probe. Raw NaN mismatch counts are diagnostic.

To force an individual focused test JVM, after running the script once:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot'
./gradlew.bat test --tests net.adiont.alacer.render.ParticleVertexWriterTest --rerun-tasks -I docs/benchmarks/2026-10-20-particle-vertex-nan/test-runtime.init.gradle '-PparticleEdgeJvmArgs=-XX:TieredStopAtLevel=1'
```

Replace the property with `-PparticleEdgeJvmArgs=-Xint` or
`-PparticleEdgeJvmArgs=-XX:-TieredCompilation,-Xbatch`; omit it for default tiered
compilation. `JAVA_HOME` controls Gradle and the project's Java 25 toolchain
controls its compiler/test launcher. The init script records the selected test
Java executable; the standalone probe uses that exact executable.
