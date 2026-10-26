package net.adiont.alacer.profiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class PerformanceAreaGraphRendererTest {

	@Test
	void adaptiveScaleClampsAndPads() {
		assertEquals(12.0F, PerformanceAreaGraphRenderer.adaptiveScale(3.9D), 0.001F);
		assertEquals(57.5F, PerformanceAreaGraphRenderer.adaptiveScale(50.0D), 0.001F);
		assertEquals(250.0F, PerformanceAreaGraphRenderer.adaptiveScale(1000.0D), 0.001F);

		assertEquals(2.0F, PerformanceAreaGraphRenderer.adaptiveTopScale(0.8D), 0.001F);
		assertEquals(3.6F, PerformanceAreaGraphRenderer.adaptiveTopScale(3.0D), 0.001F);
		assertEquals(250.0F, PerformanceAreaGraphRenderer.adaptiveTopScale(500.0D), 0.001F);
	}

	@Test
	void referenceLabelFitsWithinBounds() {
		assertTrue(PerformanceAreaGraphRenderer.referenceLabelFits(20, 0, 80));
		assertFalse(PerformanceAreaGraphRenderer.referenceLabelFits(3, 0, 80));
		assertFalse(PerformanceAreaGraphRenderer.referenceLabelFits(76, 0, 80));
	}

	@Test
	void cyclesModesProperly() {
		PerformanceAreaGraphRenderer.setMode(1);
		assertEquals(1, PerformanceAreaGraphRenderer.getMode());

		PerformanceAreaGraphRenderer.cycleMode();
		assertEquals(2, PerformanceAreaGraphRenderer.getMode());

		PerformanceAreaGraphRenderer.cycleMode();
		assertEquals(3, PerformanceAreaGraphRenderer.getMode());

		PerformanceAreaGraphRenderer.cycleMode();
		assertEquals(0, PerformanceAreaGraphRenderer.getMode());

		PerformanceAreaGraphRenderer.cycleMode();
		assertEquals(1, PerformanceAreaGraphRenderer.getMode());
	}

	@Test void scaleChoicesClampAndCycle() {
		PerformanceAreaGraphRenderer.setFixedScale(-5);
		assertEquals(0, PerformanceAreaGraphRenderer.getFixedScale());
		for (int i = 1; i <= 4; i++) {
			PerformanceAreaGraphRenderer.cycleScale();
			assertEquals(i % 4, PerformanceAreaGraphRenderer.getFixedScale());
		}
		PerformanceAreaGraphRenderer.setFixedScale(99);
		assertEquals(3, PerformanceAreaGraphRenderer.getFixedScale());
		PerformanceAreaGraphRenderer.setFixedScale(0);
	}
}
