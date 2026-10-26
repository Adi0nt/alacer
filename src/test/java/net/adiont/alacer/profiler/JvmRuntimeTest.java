package net.adiont.alacer.profiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JvmRuntimeTest {
	@Test
	void reportsEffectiveRuntimeAndLayoutRatherThanRequestedDefaults() {
		Map<String, String> environment = new LinkedHashMap<>();
		JvmRuntime.describe(environment);
		assertEquals(System.getProperty("java.home"), environment.get("java.home"));
		assertEquals("true", environment.get("java.vectorModule"));
		String compact = environment.get("jvm.UseCompactObjectHeaders");
		assertTrue("true".equals(compact) || "false".equals(compact));
		assertTrue(Integer.parseInt(environment.get("jvm.ObjectAlignmentInBytes")) >= 8);
		for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
			if (argument.equals("-XX:+UseCompactObjectHeaders")) {
				assertEquals("true", compact);
			} else if (argument.equals("-XX:-UseCompactObjectHeaders")) {
				assertEquals("false", compact);
			}
		}
	}
}
