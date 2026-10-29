# Additional functional fallback check

After successful native MDI validation, source review identified an interaction
worth checking: ModernVertexBufferMixin allocates resident slices when MDI is
enabled/supported, while the `modernGl.forceFallback` diagnostic can disable the
terrain shader that consumes those slices. Vanilla VertexBuffer drawing reads
the per-buffer ID. This is a hypothesis, not yet a measured failure.

Add one fresh functional attempt, `q3-mdi-fallback`, with MDI on, terrain shader
requested, forced modern-GL fallback, VBOs on, and GL-error throttling off.
Retain the log, summary, counters and screenshots. Compare terrain visibility
with the existing same-world normal and MDI screenshots. Stop this feature after
a functional failure; do not change defaults or repair rendering in this testing
campaign. This diagnostic does not emulate every unsupported GPU or foreign mod.
