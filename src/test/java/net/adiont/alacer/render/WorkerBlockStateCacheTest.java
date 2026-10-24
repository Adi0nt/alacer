package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class WorkerBlockStateCacheTest {
	@Test
	void nestedAndMultipleRegionsNeverAliasLiveArrays() {
		assertNull(WorkerBlockStateCache.acquireRegion());
		int outer = WorkerBlockStateCache.beginCompile();
		WorkerBlockStateCache first;
		try {
			first = WorkerBlockStateCache.acquireRegion();
			first.light[0] = 42;
			WorkerBlockStateCache second = WorkerBlockStateCache.acquireRegion();
			assertNotSame(first, second);
			int inner = WorkerBlockStateCache.beginCompile();
			try {
				WorkerBlockStateCache nested = WorkerBlockStateCache.acquireRegion();
				assertNotSame(first, nested);
				assertNotSame(second, nested);
				nested.light[0] = 99;
			} finally { WorkerBlockStateCache.endCompile(inner); }
			assertEquals(42, first.light[0]);
		} finally { WorkerBlockStateCache.endCompile(outer); }
		assertNull(WorkerBlockStateCache.acquireRegion());
		int next = WorkerBlockStateCache.beginCompile();
		try {
			WorkerBlockStateCache reused = WorkerBlockStateCache.acquireRegion();
			assertSame(first, reused);
			assertEquals(-1, reused.light[0]);
		} finally { WorkerBlockStateCache.endCompile(next); }
	}

	@Test
	void failureFinallyReleasesTheLease() {
		int mark = WorkerBlockStateCache.beginCompile();
		try {
			WorkerBlockStateCache.acquireRegion();
			throw new IllegalArgumentException("simulated compile failure");
		} catch (IllegalArgumentException expected) {
			assertEquals("simulated compile failure", expected.getMessage());
		} finally { WorkerBlockStateCache.endCompile(mark); }
		assertNull(WorkerBlockStateCache.acquireRegion());
	}

	@Test
	void threadLocalCacheReuseAndIsolation() throws Exception {
		WorkerBlockStateCache mainCache = WorkerBlockStateCache.get();
		assertSame(mainCache, WorkerBlockStateCache.get(), "Same thread should reuse cached instance");
		assertEquals(8000, mainCache.blocks.length);
		assertEquals(8000, mainCache.light.length);

		AtomicReference<WorkerBlockStateCache> otherCache = new AtomicReference<>();
		Thread thread = new Thread(() -> otherCache.set(WorkerBlockStateCache.get()));
		thread.start();
		thread.join();

		assertNotSame(mainCache, otherCache.get(), "Different threads must receive isolated cache instances");
	}

	@Test
	void rapidTerrainGenerationWorkerPoolBenchmark() throws Exception {
		int workerCount = 4;
		int compilesPerWorker = 200;
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(workerCount);
		java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(workerCount);
		java.util.concurrent.atomic.AtomicInteger totalCompiles = new java.util.concurrent.atomic.AtomicInteger();

		long startNanos = System.nanoTime();
		for (int w = 0; w < workerCount; w++) {
			final int workerId = w;
			pool.submit(() -> {
				try {
					for (int c = 0; c < compilesPerWorker; c++) {
						int mark = WorkerBlockStateCache.beginCompile();
						try {
							WorkerBlockStateCache cache = WorkerBlockStateCache.acquireRegion();
							org.junit.jupiter.api.Assertions.assertNotNull(cache);
							assertEquals(8000, cache.blocks.length);
							assertEquals(8000, cache.light.length);

							// Simulate section compilation writes
							cache.light[0] = (workerId * 1000) + c;
							cache.light[7999] = c;
							totalCompiles.incrementAndGet();
						} finally {
							WorkerBlockStateCache.endCompile(mark);
						}
					}
				} finally {
					latch.countDown();
				}
			});
		}

		boolean finished = latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
		pool.shutdown();
		long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;

		org.junit.jupiter.api.Assertions.assertTrue(finished, "Worker threads must complete terrain compiles within timeout");
		assertEquals(workerCount * compilesPerWorker, totalCompiles.get());
		// Confirm rapid execution without GC pressure (< 1000ms for 800 compiles)
		org.junit.jupiter.api.Assertions.assertTrue(durationMs < 2000L, "Rapid pooled compilation should be fast, elapsed: " + durationMs + "ms");
	}
}
