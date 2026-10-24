# Protocol: terrain shader pipeline replacement benchmark

## Objective

Evaluate the `TerrainShader` pipeline replacement for terrain rendering, which replaces CPU matrix stack pushes, pops, and 4x4 multiplications with GPU-side origin translations in a lightweight compatibility vertex shader, paired with fixed-function fragment processing.

## Hard budget

- 1 preflight correctness check.
- 16 selection launches (8 default scenario, 8 terrain backend scenario) in balanced `ABBABAAB` pairs.
- Stop rules:
  - If any client aborts, crashes with an unhandled exception, or generates OpenGL errors, stop immediately.
  - If a gate fails, retain the result and explain the gap.

## Order of execution

Pairs:
- Pair 1: `1a` (Control A) vs `2b` (Candidate B)
- Pair 2: `4a` (Control A) vs `3b` (Candidate B)
- Pair 3: `6a` (Control A) vs `5b` (Candidate B)
- Pair 4: `7a` (Control A) vs `8b` (Candidate B)

Order of execution for each scenario:
1. `1a` (A)
2. `2b` (B)
3. `3b` (B)
4. `4a` (A)
5. `5b` (B)
6. `6a` (A)
7. `7a` (A)
8. `8b` (B)
