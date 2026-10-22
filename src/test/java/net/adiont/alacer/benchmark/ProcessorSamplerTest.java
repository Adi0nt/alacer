package net.adiont.alacer.benchmark;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Locale;

import org.junit.jupiter.api.Test;

class ProcessorSamplerTest {
	@Test void reportsAProcessorOrSaysWhyNot() {
		ProcessorSampler sampler = ProcessorSampler.create();
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (os.startsWith("windows") || os.startsWith("linux")) {
			assertTrue(sampler.available(), sampler.source());
			int processors = Runtime.getRuntime().availableProcessors();
			for (int i = 0; i < 1000; i++) {
				int p = sampler.current();
				// The index is the machine's logical processor number; the JVM may be limited to fewer processors.
				assertTrue(p >= 0 && p < ProcessorSampler.MAX_PROCESSORS, "processor " + p);
			}
			assertTrue(processors > 0);
		} else {
			assertFalse(sampler.available());
			assertTrue(sampler.source().startsWith("unavailable: "));
			assertEquals(-1, sampler.current());
		}
	}
}
