package net.adiont.alacer.profiler;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DebugOverlayLayoutTest {
	@Test void chartAndColumnsStaySeparateAtCommonGuiScales() {
		for (int[] size : new int[][] {{320, 180}, {320, 240}, {427, 240}, {640, 360}, {960, 540}, {1920, 1080}}) {
			int width = size[0], height = size[1];
			assertTrue(DebugOverlayLayout.panelWidth(width, height) <= width - 4);
			assertTrue(DebugOverlayLayout.panelHeight(width, height) <= height - 4);
			for (boolean right : new boolean[] {true, false}) {
				int rows = DebugOverlayLayout.textRows(width, height, 9, true, right);
				if (right || DebugOverlayLayout.compact(width, height))
					assertTrue(2 + rows * 9 < DebugOverlayLayout.panelTop(width, height));
			}
		}
	}
	@Test void hiddenGraphReturnsTextSpaceToBothColumns() {
		assertTrue(DebugOverlayLayout.textRows(427, 240, 9, false, true)
			> DebugOverlayLayout.textRows(427, 240, 9, true, true));
	}
}
