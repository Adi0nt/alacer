# Source amendment after the first functional pass

The exact formerly crashing flags completed the first development regression
check without native/GL/mixin failures and visibly rendered terrain. Before the
remaining three attempts, source review found a related startup-selection gap:
MDI by itself (or as a benchmark toggle) did not select the modern buffer/lifecycle
mixins unless another modern option was enabled. ModernGl.pipelineActive already
treated MDI as a request, so the selection rules disagreed.

Add MDI and its benchmark toggle to that startup gate, with two regression tests,
then rebuild/retest before another client. Preserve the first source freeze as
freeze-v1.json; freeze.json records the amended sources/artifact and full suite.
The first pass is evidence for its own earlier source, not the amended JAR.

Keep the original four-attempt budget and twelve-minute deadline. In attempt 2,
request only MDI and its verifier, without a separate terrainShader flag. In
attempt 3, use only the MDI benchmark toggle plus forced fallback. In the final
production attempt, request only MDI plus forced fallback. This additionally
checks the repaired startup gate; there is no extra launch or retry. No
performance claim or acceptance threshold changed.
