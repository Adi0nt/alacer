package net.adiont.alacer.profiler;

import java.lang.management.ManagementFactory;
import java.util.Map;

import com.sun.management.HotSpotDiagnosticMXBean;

/** Runtime identity and effective layout flags for reproducible Java 25 comparisons. */
public final class JvmRuntime {
	private JvmRuntime() {
	}

	/** Called when exporting a profile, never from the per-frame sampling path. */
	public static void describe(Map<String, String> environment) {
		environment.put("java.home", System.getProperty("java.home"));
		environment.put("java.vectorModule", String.valueOf(ModuleLayer.boot().findModule("jdk.incubator.vector").isPresent()));
		for (String flag : new String[] {"UseCompactObjectHeaders", "UseCompressedOops", "ObjectAlignmentInBytes"}) {
			environment.put("jvm." + flag, option(flag));
		}
	}

	private static String option(String name) {
		try {
			HotSpotDiagnosticMXBean bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
			return bean == null ? "unavailable" : bean.getVMOption(name).getValue();
		} catch (IllegalArgumentException | UnsupportedOperationException | SecurityException | LinkageError ignored) {
			return "unavailable";
		}
	}
}
