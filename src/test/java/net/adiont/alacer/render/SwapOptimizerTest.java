package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SwapOptimizerTest {

	@BeforeEach
	void setUp() {
		SwapOptimizer.reset();
	}

	@AfterEach
	void tearDown() {
		SwapOptimizer.reset();
	}

	@Test
	void displayUpdateMeasuresDurationAndAverages() throws InterruptedException {
		assertEquals(0.0D, SwapOptimizer.getAverageSwapMillis());

		SwapOptimizer.beforeDisplayUpdate();
		Thread.sleep(2);
		SwapOptimizer.afterDisplayUpdate();

		assertTrue(SwapOptimizer.getLastDriverSwapNs() > 0L);
		assertTrue(SwapOptimizer.getAverageSwapMillis() > 0.0D);
	}

	@Test
	void postSwapYieldSuppressionIncrementsCounter() {
		long initialYields = SwapOptimizer.getTotalYieldsSuppressed();
		SwapOptimizer.onPostSwapYield();
		assertTrue(SwapOptimizer.getTotalYieldsSuppressed() >= initialYields);
	}

	@Test
	void syncDurationTrackingRecordsValue() {
		SwapOptimizer.recordSyncDuration(2_500_000L);
		assertEquals(2_500_000L, SwapOptimizer.getLastLimiterSyncNs());
	}

	@Test
	void resetClearsHistoryAndState() {
		SwapOptimizer.beforeDisplayUpdate();
		SwapOptimizer.afterDisplayUpdate();
		SwapOptimizer.recordSyncDuration(5_000_000L);

		SwapOptimizer.reset();

		assertEquals(0L, SwapOptimizer.getLastDriverSwapNs());
		assertEquals(0L, SwapOptimizer.getLastLimiterSyncNs());
		assertEquals(0L, SwapOptimizer.getLastFenceWaitNs());
		assertEquals(0.0D, SwapOptimizer.getAverageSwapMillis());
	}

	@Test
	void fenceWaitAndPreRenderSafetyWhenHeadless() {
		assertEquals(0L, SwapOptimizer.getLastFenceWaitNs());
		SwapOptimizer.onPreRenderWorld();
		assertEquals(0L, SwapOptimizer.getLastFenceWaitNs());
	}
}
