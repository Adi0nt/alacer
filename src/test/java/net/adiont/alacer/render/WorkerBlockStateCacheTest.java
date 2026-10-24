package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class WorkerBlockStateCacheTest {

	@Test
	void threadLocalCacheReuseAndIsolation() throws Exception {
		WorkerBlockStateCache mainCache = WorkerBlockStateCache.get();
		assertSame(mainCache, WorkerBlockStateCache.get(), "Same thread should reuse cached instance");
		assertEquals(8000, mainCache.blocks.length);
		assertEquals(8000, mainCache.light.length);
		assertEquals(-1, mainCache.light[0]);

		AtomicReference<WorkerBlockStateCache> otherCache = new AtomicReference<>();
		Thread thread = new Thread(() -> otherCache.set(WorkerBlockStateCache.get()));
		thread.start();
		thread.join();

		assertNotSame(mainCache, otherCache.get(), "Different threads must receive isolated cache instances");
	}
}
