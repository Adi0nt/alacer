package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DynamicFramePacingTest {

	@BeforeEach
	void setUp() {
		DynamicFramePacing.clearCounters();
	}

	@Test
	void pacingMarginScalesWithGpuWorkload() {
		long frameTarget144Fps = 6_944_444L; // ~6.94 ms

		// Low load: 1 ms GPU time out of 6.94 ms (~14% load)
		long tightMargin = DynamicFramePacing.computePacingMarginNanos(frameTarget144Fps);
		// Default initialized smoothed is 2ms (28% load), so <40% load -> 500,000 ns
		assertEquals(500_000L, tightMargin);
		assertTrue(DynamicFramePacing.estimatedQueueDepth() >= 0.5f);

		// Zero or negative inputs return safe 1.5ms default
		assertEquals(1_500_000L, DynamicFramePacing.computePacingMarginNanos(0));
	}

	@Test
	void clearResetsCounters() {
		DynamicFramePacing.clearCounters();
		assertEquals(0, DynamicFramePacing.lastGpuTimeNanos());
		assertEquals(0, DynamicFramePacing.querySamples());
		assertEquals(1.0f, DynamicFramePacing.estimatedQueueDepth(), 1e-4f);
	}
}
