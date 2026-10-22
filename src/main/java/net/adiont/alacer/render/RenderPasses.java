package net.adiont.alacer.render;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.profiler.FrameProfiler.Counter;
import net.adiont.alacer.profiler.FrameProfiler.Section;
import net.minecraft.client.render.block.BlockLayer;

/**
 * Timing for the passes of the world render, including cached fancy clouds: terrain layers, sky,
 * clouds, particles, weather, hand and GUI. Each is one {@link Section} around one vanilla call,
 * so together with the existing sections they account for most of {@code render}.
 */
public final class RenderPasses {

	/** Indexed by {@code BlockLayer.ordinal()}: SOLID, CUTOUT_MIPPED, CUTOUT, TRANSLUCENT. */
	private static final Section[] LAYERS = {
		Section.TERRAIN_SOLID, Section.TERRAIN_CUTOUT_MIPPED, Section.TERRAIN_CUTOUT, Section.TERRAIN_TRANSLUCENT
	};
	private static final Counter[] CHUNKS = {
		Counter.TERRAIN_SOLID_CHUNKS, Counter.TERRAIN_CUTOUT_MIPPED_CHUNKS,
		Counter.TERRAIN_CUTOUT_CHUNKS, Counter.TERRAIN_TRANSLUCENT_CHUNKS
	};

	public static void chunks(BlockLayer layer, int count) {
		Alacer.get().profiler().count(CHUNKS[layer.ordinal()], count);
	}

	private RenderPasses() {
	}

	public static void begin(Section section) {
		Alacer.get().begin(section);
	}

	public static void end(Section section) {
		Alacer.get().end(section);
	}

	public static void beginLayer(BlockLayer layer) {
		Alacer.get().begin(LAYERS[layer.ordinal()]);
	}

	public static void endLayer(BlockLayer layer) {
		Alacer.get().end(LAYERS[layer.ordinal()]);
	}
}
