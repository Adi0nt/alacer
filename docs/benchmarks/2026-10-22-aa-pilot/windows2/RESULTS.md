# Session 31 Windows A/A outcome: aa31w2

Completed 2026-10-22, America/Los_Angeles. **Calibration FAIL; full A/A pilot NOT RUN; 2% power UNRESOLVED.**

The frozen runner stopped before the first pair because accepted terrain reference-pre frame means have
**12.7093% robust spread**, above the unchanged 5% calibration limit. This is a measured stability failure.
The earlier aa31 failure was a distinct cross-scene configuration validator defect; that original archive
and its failed verdict remain untouched. The corrected aa31w2 series was frozen separately and never rescored.

## Validation and scope

- Windows integration: PASS on i7-12700F / RTX 3060 Ti, NVIDIA 616.92, actual OpenGL 4.6.
- Java 25: 131 tests pass. Python: 107 discovered, 105 pass, two Linux-only skips.
- Fresh d0 and terrain smoke: PASS, including all environmental checks. See [validation.json](validation.json).
- Fresh calibration: 12 launches, seven environmentally accepted (three d0, four terrain), five rejected.
- Full pilot: zero launches, zero pairs. The 3% paired stability gate, confidence intervals and MDE80 are unresolved.
- Future 16-pair detection of a 2% improvement: UNRESOLVED. This system is not yet qualified for such A/B claims.
- No rendering default, candidate selection or numeric gate changed. No PR was opened.

The Windows fixes are in `39a0a5a`; the separate freeze is `6b75fc5`. Frozen protocol SHA256:
`ffacd672b9ea918119715697ef3a1444b2e5feb69a378bc467c90bb0da6e6dbb`. Source snapshot: [sources-7b681622c657fef3.zip](sources-7b681622c657fef3.zip).

## Calibration references

Spread is the frozen `expm1(1.4826 * median(abs(log(x / median(x)))))` statistic across accepted launches.
These are separate-launch calibration observations, not paired A/A estimates. Their sample sizes are small.

| Scene / reference | Launches | Median ms | Min–max ms | Robust spread | 5% calibration gate |
|---|---:|---:|---:|---:|---|
| d0 / reference-pre | 3 | 0.585053 | 0.580074–0.599530 | 1.2752% | PASS |
| d0 / reference-post | 3 | 0.580192 | 0.571934–0.588679 | 2.1480% | PASS |
| terrain / reference-pre | 4 | 0.565870 | 0.504561–0.616527 | 12.7093% | FAIL |
| terrain / reference-post | 4 | 0.626848 | 0.517238–0.633364 | 1.3496% | PASS |

The low robust spread of terrain/reference-post does not remove the fast final launch: its mean was
0.517238 ms, versus 0.622013, 0.633364 and 0.631682 ms for the other three. Every value is retained.

## Judged phases observed during calibration

Descriptive only: these launches are not the eight required pairs. The numbers below cannot pass or fail
the 3% **per-pair** gate and cannot supply its confidence intervals or power estimates.

| Scene / phase | Launches | Median ms | Separate-launch robust spread |
|---|---:|---:|---:|
| d0 / static | 3 | 0.604779 | 4.0287% |
| d0 / pan | 3 | 0.933176 | 0.9802% |
| d0 / fly | 3 | 1.193921 | 1.7430% |
| terrain / steady | 4 | 0.574822 | 17.8051% |
| terrain / pan | 4 | 0.786385 | 38.5575% |
| terrain / sort | 4 | 0.824022 | 30.8508% |
| terrain / fly | 4 | 1.042538 | 17.2328% |

## Every calibration attempt

| Attempt | Outcome | Reasons |
|---|---|---|
| `aa31w2-cal-d0-1` | passed | none |
| `aa31w2-cal-terrain-1` | passed | none |
| `aa31w2-cal-d0-2` | passed | none |
| `aa31w2-cal-terrain-2` | passed | none |
| `aa31w2-cal-d0-3` | passed | none |
| `aa31w2-cal-terrain-3` | passed | none |
| `aa31w2-cal-d0-4` | rejected-env | repeat-01: background CPU 0.64 cores (mean) above 0.5 |
| `aa31w2-cal-d0-4-r2` | rejected-env | pan: background CPU 0.51 cores (mean) above 0.5; repeat-06: background CPU 0.73 cores (mean) above 0.5 |
| `aa31w2-cal-d0-4-r3` | rejected-env | repeat-08: background CPU 0.54 cores (mean) above 0.5 |
| `aa31w2-cal-terrain-4` | rejected-env | fly: background CPU 1.38 cores (mean) above 0.5; fly: background CPU 4.02 cores (5 s peak) above 1.5 |
| `aa31w2-cal-terrain-4-r2` | rejected-env | warmup-pan: background CPU 0.52 cores (mean) above 0.5; pan: background CPU 0.54 cores (mean) above 0.5 |
| `aa31w2-cal-terrain-4-r3` | passed | none |

d0 slot 4 exhausted all three attempts; its earlier three accepted slots meet the predeclared minimum.
Terrain slot 4 passed its third attempt. Calibration then failed its reference spread check.
The five environmental rejections are calibration launches, not a pilot rejected-pair fraction.

## Measured disturbances and unexplained timing states

- Accepted terrain reference-pre means were 0.607102, 0.616527, 0.504561 and 0.524638 ms.
  Client-thread CPU/frame changed from 0.5843/0.5857 to 0.1849/0.2029 ms, while process CPU/frame
  was 0.630780/0.627130/0.671672/0.673793 ms. Similar timing changes appear in the judged phases.
  This is a useful attribution lead, not proof of a JIT, driver, scheduler or warmup cause.
- Those accepted initial references had 100% foreground and critical telemetry, the same configuration
  hash and 1,374 visible chunks, P-core sampling only, and 0.117–0.263 readable background cores.
  Mean reported P-core frequency was 3,308–3,445 MHz and GPU clock 1,761–1,784 MHz. These observations
  do not establish a hardware-state explanation for the frame-time groups.
- The failed d0-4 attempts exceeded background CPU limits during short repeat/pan windows.
  ChatGPT was the largest named consumer in the d0-4-r2 repeat-06 rejection: 4.6777 core-seconds
  over eight seconds, with 0.7310 background cores overall.
- Terrain-4 fly measured 1.3821 background cores and a 4.02-core five-second peak. Claude accounted
  for 29.4443 core-seconds and ChatGPT 9.6128 in that phase. These explain observed environmental
  breaches; they do not prove the cause of accepted-launch timing variation.
- Many phases flag more than one busy core unattributed to readable processes. Protected processes,
  driver/DPC work and other sources have not been separated; no particular source is claimed.

Across all 12 calibration attempts, all 126 phases had >=100% expected sampling coverage and 100%
critical availability/foreground. All 4,328,653 processor samples were known P-core samples, with
zero E-core frames. No monitor errors occurred. Phase monitor self CPU ranged from 0.586% to 2.148%
of one core. Its effect on frame times was not isolated; the optional monitor-on/off study was not run.
The initial smoke hang remains unexplained and did not recur in the subsequent completed launches.

## Evidence, restoration and next action

[calibration.json](calibration.json), [pilot.md](pilot.md), [pilot.json](pilot.json),
[observations.json](observations.json), every attempt and every compact record are retained.
[Runner output](runner-output.txt) (trailing spaces normalized) and [foreground events](foreground-events.jsonl) are also archived.
After scoring, the frozen source/tooling, source ZIP, compiled artifacts, JDK, base world and shared
configuration hashes were verified unchanged. All 308 raw result files for this series (1,798,590,052
bytes) still match their recorded SHA256 values; raw CSV/JSON data and monitor JSONL remain locally
under `run/`, with client logs under `build/`. They are not all uploaded to Git.

The runner exited, no Minecraft client or runner lock remained, and foreground automation was stopped.
The original Ultimate Performance plan `6926823e-1958-4e8c-a849-b485e20e7181` was restored.
The original dirty main checkout and its unrelated work remain separate.

**Next action:** use a separate diagnostic series to attribute the terrain timing groups, collecting
client/worker/native CPU and compilation/driver activity while reproducing identical fresh launches.
Compare the initial and final references and test an explicitly declared warmup/settling change if the
diagnostic supports it. Pause other work before a new scored series. Document any amendment, run fresh
smoke, freeze a new label and rerun calibration before the 8+8 pairs. Do not reuse, rescore or discard
these attempts, extend exhausted slots, or relax the 5%/3%/CPU limits. No new diagnostic series was run.
