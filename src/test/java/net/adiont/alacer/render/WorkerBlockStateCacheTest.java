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
	void concurrentCompileLeasesKeepArraysAndContentsIsolated() throws Exception {
		int workerCount = 4;
		int compilesPerWorker = 200;
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(workerCount);
		java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(workerCount);
		WorkerBlockStateCache[] live = new WorkerBlockStateCache[workerCount];
		java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
		try {
			for (int w = 0; w < workerCount; w++) {
				final int workerId = w;
				futures.add(pool.submit(() -> {
					for (int c = 0; c < compilesPerWorker; c++) {
						int mark = WorkerBlockStateCache.beginCompile();
						try {
							WorkerBlockStateCache cache = WorkerBlockStateCache.acquireRegion();
							org.junit.jupiter.api.Assertions.assertNotNull(cache);
							assertEquals(-1, cache.light[0]);
							assertEquals(-1, cache.light[7999]);
							live[workerId] = cache;
							int marker = workerId * compilesPerWorker + c;
							cache.light[0] = marker;
							cache.light[7999] = c;
							barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
							for (int other = 0; other < workerCount; other++) {
								if (other == workerId) continue;
								assertNotSame(cache.blocks, live[other].blocks);
								assertNotSame(cache.light, live[other].light);
							}
							assertEquals(marker, cache.light[0]);
							assertEquals(c, cache.light[7999]);
							// Keep every lease alive until every worker has read its markers.
							barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
						} finally {
							WorkerBlockStateCache.endCompile(mark);
						}
						assertNull(WorkerBlockStateCache.acquireRegion());
					}
					return null;
				}));
			}
			// Propagate assertion failures and exceptions out of executor tasks.
			for (java.util.concurrent.Future<?> future : futures) future.get(30, java.util.concurrent.TimeUnit.SECONDS);
		} finally {
			pool.shutdownNow();
			org.junit.jupiter.api.Assertions.assertTrue(pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
		}
	}
}
