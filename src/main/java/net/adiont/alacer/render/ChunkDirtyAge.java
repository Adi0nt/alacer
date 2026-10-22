package net.adiont.alacer.render;

import net.adiont.alacer.profiler.DirtyQueueAge;

/** Diagnostic state attached only when chunk-detail mixins apply. */
public interface ChunkDirtyAge {
	DirtyQueueAge alacer$dirtyAge();
}
