# VAO query attribution (after failed v1 selection, before this diagnostic)

The frozen v1 series failed: moving frame means regress by 59-65%. A separate
single-option launch reproduces the large regression with VAOs. JFR does not
identify a convincing Java hot spot for it; native-call return attribution is
not driver attribution. Do not claim a cause from those samples.

Use two serial ordinary default-route launches, all three options enabled,
1920x1080/d12/VBO/2G, no JFR, probes or verification. Change only a temporary
`modernGl.skipVaoQuery` startup diagnostic: first false, then true. Capture source
and exact commands. This is attribution, not performance selection; no gate or
original result changes. The skipped-query version deliberately lacks the
foreign-VAO compatibility guard and must not ship.

Pursue a replacement guard only if skipping the query cuts both static and pan
frame mean or CPU/frame by at least 25%, with no GL errors. Otherwise retire
this hypothesis rather than introducing private LWJGL access without evidence.
Any replacement needs separate correctness and frozen performance evidence.
