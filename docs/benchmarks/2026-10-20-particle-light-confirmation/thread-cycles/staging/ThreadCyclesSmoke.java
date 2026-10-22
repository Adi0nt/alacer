package net.adiont.alacer.render;

import java.util.concurrent.atomic.AtomicReference;

/** Standalone Java 25 smoke, deliberately not built or run during staging. */
public final class ThreadCyclesSmoke {
	private static volatile long sink;

	public static void main(String[] args) throws Exception {
		boolean expectReady = args.length == 1 && args[0].equals("--expect-ready");
		ThreadCycles cycles = ThreadCycles.open();
		try {
			System.out.println("supported=" + cycles.supported() + " status=" + cycles.status());
			if (expectReady) { require(cycles.available(), "Windows counter unavailable: " + cycles.status()); }
			if (cycles.available()) {
				long before = cycles.read();
				long value = 1;
				for (int i = 0; i < 10_000_000; i++) { value = value * 6364136223846793005L + i; }
				sink = value;
				long afterLoop = cycles.read();
				Thread.sleep(50);
				long afterSleep = cycles.read();
				require(before >= 0 && afterLoop > before && afterSleep >= afterLoop, "cycles must remain monotonic");
				System.out.println("loop_raw_cycles=" + (afterLoop - before)
					+ " sleep_raw_cycles=" + (afterSleep - afterLoop));
				// Sleep is observational: no ratio, elapsed-time conversion or frequency assumption.
			} else { require(cycles.read() == -1, "unavailable read"); }

			AtomicReference<Throwable> failure = new AtomicReference<>();
			Thread other = Thread.ofPlatform().unstarted(() -> {
				try {
					require(!cycles.available() && cycles.read() == -1, "foreign-thread read must be unavailable");
					try { cycles.close(); throw new AssertionError("foreign close accepted"); }
					catch (IllegalStateException expected) { }
				} catch (Throwable error) { failure.set(error); }
			});
			other.start(); other.join();
			if (failure.get() != null) { throw new AssertionError("thread ownership", failure.get()); }
			if (expectReady) { require(cycles.read() >= 0, "foreign access poisoned owner"); }
		} finally { cycles.close(); }
		cycles.close();
		require(!cycles.available() && cycles.read() == -1, "closed read");
		System.out.println("PASS ownership/close; status=" + cycles.status());
	}

	private static void require(boolean condition, String message) {
		if (!condition) { throw new AssertionError(message); }
	}
}
