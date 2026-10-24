# Selection and correctness gates: OpenGL 4.5 direct buffer uploads

These gates must be frozen before executing the 16-launch selection series.

## 1. Correctness Gates (Mandatory)

1. **Unit Test Gate**:
   `./gradlew.bat test --offline` must pass with zero failures (166 tests on Java 25).
2. **Execution Gate**:
   All 16 scheduled native GPU launches must complete with exit code 0 and valid summary/phase JSON receipts.
3. **Receipt Quality**:
   Zero dropped frames (`frames_dropped == 0`) across all scored phases in all 16 runs.
   Zero OpenGL error log entries in client logs.
4. **Workload Parity Gate**:
   In each balanced pair, the mean visible chunk count across moving phases must remain within $\pm 1.0\%$ of control.

---

## 2. Performance Selection Gates

Performance is evaluated as the median percentage delta of Candidate (B) vs Control (A) across the four balanced pairs in each scenario:

$$\Delta\% = \frac{\text{Candidate} - \text{Control}}{\text{Control}} \times 100$$

1. **Pan Frame Time Gate**:
   - Default route pan frame time median delta: $\le 0.0\%$ (neutral or faster).
   - Terrain route pan frame time median delta: $\le 0.0\%$ (neutral or faster).
2. **Pan CPU Gate**:
   - Default route client CPU time per frame median delta: $\le 0.0\%$.
   - Terrain route client CPU time per frame median delta: $\le 0.0\%$.
3. **Moving Phase Regression Ceiling**:
   - No scored moving phase (`pan`, `fly`, `steady`, `sort`) may regress in frame time by more than $+1.0\%$.
4. **Latency and Tails Gate**:
   - Frame time p99 median delta $\le +2.0\%$ in moving phases.

---

## 3. Decision Rule

- If all correctness gates pass and all performance selection gates pass: **ACCEPTED**.
- If any correctness gate fails or any moving phase regresses $> +1.0\%$: **REJECTED** (defaults remain strictly `false`).
