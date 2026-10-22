package net.adiont.alacer.profiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.adiont.alacer.profiler.FrameProfiler.Counter;
import net.adiont.alacer.profiler.FrameProfiler.Section;

class FrameProfilerTest {

	private static void spin(long nanos) {
		long end = System.nanoTime() + nanos;
		while (System.nanoTime() < end) {
			// Busy-wait: sleeping is far too coarse on Windows.
		}
	}

	private static long last(FrameProfiler profiler, int column) {
		MetricRing frames = profiler.frames();
		long[] out = new long[1];
		frames.copyRecent(column, 1, out);
		return out[0];
	}

	@Test
	void columnLayoutIsConsistent() {
		Set<String> names = new HashSet<>();
		for (String name : FrameProfiler.COLUMN_NAMES) {
			assertTrue(name != null && !name.isEmpty());
			assertTrue(names.add(name), "duplicate column " + name);
		}
		assertEquals(FrameProfiler.COLUMNS, names.size());
		assertEquals("frame_ns", FrameProfiler.COLUMN_NAMES[FrameProfiler.COL_FRAME]);
		assertEquals("visibility_ns", FrameProfiler.COLUMN_NAMES[FrameProfiler.COL_VISIBILITY]);
		assertEquals("entity_draw_ns", FrameProfiler.COLUMN_NAMES[FrameProfiler.column(Section.ENTITY_DRAW)]);
		assertEquals("entities_drawn", FrameProfiler.COLUMN_NAMES[FrameProfiler.column(Counter.ENTITIES_DRAWN)]);
		assertEquals("start_ns", FrameProfiler.COLUMN_NAMES[FrameProfiler.COL_START]);
		assertEquals(FrameProfiler.COLUMNS - 1, FrameProfiler.COL_START);
	}

	@Test
	void sectionsCountersAndPhasesAreRecordedPerFrame() {
		FrameProfiler profiler = new FrameProfiler(256);
		profiler.frameBoundary();

		profiler.begin(Section.ENTITIES);
		profiler.switchPhase(Section.ENTITY_PREPARE);
		spin(200_000);
		profiler.switchPhase(Section.ENTITY_LOOP);
		spin(1_000_000);
		profiler.endPhase();
		profiler.end(Section.ENTITIES);
		profiler.add(Section.ENTITY_DRAW, 12345L);
		profiler.add(Section.ENTITY_DRAW, 5L);
		profiler.count(Counter.ENTITIES_DRAWN);
		profiler.count(Counter.ENTITIES_DRAWN);
		profiler.frameBoundary();

		long prepare = last(profiler, FrameProfiler.column(Section.ENTITY_PREPARE));
		long loop = last(profiler, FrameProfiler.column(Section.ENTITY_LOOP));
		long total = last(profiler, FrameProfiler.COL_ENTITIES);
		assertTrue(prepare >= 200_000, "prepare " + prepare);
		assertTrue(loop >= 1_000_000, "loop " + loop);
		assertTrue(total >= prepare + loop, "phases are within the enclosing section");
		assertEquals(12350L, last(profiler, FrameProfiler.column(Section.ENTITY_DRAW)));
		assertEquals(2L, last(profiler, FrameProfiler.column(Counter.ENTITIES_DRAWN)));
		assertTrue(last(profiler, FrameProfiler.COL_FRAME) >= total);

		// The next frame starts from zero.
		profiler.frameBoundary();
		assertEquals(0L, last(profiler, FrameProfiler.column(Section.ENTITY_DRAW)));
		assertEquals(0L, last(profiler, FrameProfiler.column(Counter.ENTITIES_DRAWN)));
		assertEquals(0L, last(profiler, FrameProfiler.column(Section.ENTITY_LOOP)));
	}

	@Test
	void endPhaseWithoutPhaseIsHarmless() {
		FrameProfiler profiler = new FrameProfiler(256);
		profiler.frameBoundary();
		profiler.endPhase();
		profiler.frameBoundary();
		assertEquals(1, profiler.frames().size());
	}

	@Test
	void listenerSeesEveryCompletedFrame() {
		FrameProfiler profiler = new FrameProfiler(256);
		int[] seen = {0};
		profiler.setListener((frames, row) -> seen[0]++);
		for (int i = 0; i < 5; i++) {
			profiler.frameBoundary();
		}
		// The first boundary only opens a frame.
		assertEquals(4, seen[0]);
	}
}
