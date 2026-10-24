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
}
