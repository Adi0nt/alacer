package net.adiont.alacer.benchmark;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class StabilityOptionsTest {
	private static StabilityOptions parse(String... pairs) {
		Map<String, String> props = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			props.put("alacer.benchmark." + pairs[i], pairs[i + 1]);
		}
		return StabilityOptions.parse(props::get);
	}

	@Test void absentOptionsChangeNothing() {
		StabilityOptions o = parse();
		assertFalse(o.any());
		assertFalse(o.changesPhases());
		assertEquals(0.0, o.referenceSeconds);
		assertTrue(o.repeatNames().isEmpty());
		assertTrue(o.toggleBlockNames().isEmpty());
		assertEquals(10.0, o.secondsFor("static", 10.0));
		// Explicit "false" and empty values are the same as absent.
		assertFalse(parse("processorSampling", "false", "reference", "", "repeatSegments", "").any());
	}

	@Test void parsesReferenceDurationsAndSegments() {
		StabilityOptions o = parse("reference", "20", "referencePost", "true", "phaseSeconds", "static:60, steady:100",
			"repeatSegments", "3", "repeatSegmentSeconds", "8", "processorSampling", "true", "mobPoseCheck", "TRUE",
			"freezeMobs", "true");
		assertTrue(o.any() && o.changesPhases());
		assertEquals(20.0, o.referenceSeconds);
		assertTrue(o.referencePost);
		assertEquals(60.0, o.secondsFor("static", 20.0));
		assertEquals(100.0, o.secondsFor("steady", 20.0));
		assertEquals(30.0, o.secondsFor("pan", 30.0));
		assertEquals(List.of("repeat-01", "repeat-02", "repeat-03"), o.repeatNames());
		assertEquals(8.0, o.repeatSegmentSeconds);
		assertTrue(o.processorSampling && o.mobPoseCheck && o.freezeMobs);
		assertTrue(parse("freezeMobs", "true").any());
		assertFalse(parse("freezeMobs", "true").changesPhases());
		Map<String, String> d = o.describe();
		assertEquals("20.000", d.get("benchmark.reference"));
		assertEquals("static:60.000,steady:100.000", d.get("benchmark.phaseSeconds"));
		assertEquals("3", d.get("benchmark.repeatSegments"));
	}

	@Test void malformedValuesAreErrorsNotDefaults() {
		assertThrows(IllegalArgumentException.class, () -> parse("reference", "abc"));
		assertThrows(IllegalArgumentException.class, () -> parse("reference", "-1"));
		assertThrows(IllegalArgumentException.class, () -> parse("reference", "NaN"));
		assertThrows(IllegalArgumentException.class, () -> parse("reference", "601"));
		assertThrows(IllegalArgumentException.class, () -> parse("referencePost", "true"));
		assertThrows(IllegalArgumentException.class, () -> parse("referencePost", "yes", "reference", "5"));
		assertThrows(IllegalArgumentException.class, () -> parse("phaseSeconds", "static=60"));
		assertThrows(IllegalArgumentException.class, () -> parse("phaseSeconds", "static:0"));
		assertThrows(IllegalArgumentException.class, () -> parse("phaseSeconds", "static:60,static:30"));
		assertThrows(IllegalArgumentException.class, () -> parse("repeatSegments", "65"));
		assertThrows(IllegalArgumentException.class, () -> parse("repeatSegments", "2.5"));
		assertThrows(IllegalArgumentException.class, () -> parse("repeatSegmentSeconds", "0"));
		assertThrows(IllegalArgumentException.class, () -> parse("processorSampling", "1"));
	}

	@Test void unknownPhaseNamesAreRejected() {
		StabilityOptions o = parse("phaseSeconds", "statc:60");
		assertThrows(IllegalArgumentException.class, () -> o.checkPhaseNames(List.of("static", "pan", "fly")));
		parse("phaseSeconds", "static:60").checkPhaseNames(List.of("static", "pan", "fly"));
	}

	@Test void toggleBlocksAlternateAbbaAndBaab() {
		StabilityOptions o = parse("toggleBlocks", "2", "toggleSegmentSeconds", "6");
		List<String> names = o.toggleBlockNames();
		assertEquals(List.of("static-01-a1", "static-01-b1", "static-01-b2", "static-01-a2",
			"static-02-b1", "static-02-a1", "static-02-a2", "static-02-b2"), names);
		long variants = names.stream().filter(StabilityOptions::isVariant).count();
		assertEquals(4, variants);
		assertTrue(StabilityOptions.isVariant("static-02-b1"));
		assertFalse(StabilityOptions.isVariant("static-02-a2"));
		assertEquals(6.0, o.toggleSegmentSeconds);
		// Each position in the sequence: B count equals A count over every two blocks (drift balance).
		double aMean = 0, bMean = 0;
		for (int i = 0; i < names.size(); i++) {
			if (StabilityOptions.isVariant(names.get(i))) { bMean += i; } else { aMean += i; }
		}
		assertEquals(aMean, bMean);
	}

	@Test void mobPoseComparisonReportsLargestMoveAndWrappedTurn() {
		Map<Integer, double[]> start = new HashMap<>();
		Map<Integer, double[]> end = new HashMap<>();
		start.put(1, new double[] {0, 64, 0, 350, 10});
		end.put(1, new double[] {0, 64, 0, 10, 10});      // 20 degree turn across the wrap
		start.put(2, new double[] {5, 64, 5, 0, 0});
		end.put(2, new double[] {8, 68, 5, 0, -30});      // moved 5 blocks; head turned 30 degrees
		start.put(3, new double[] {1, 1, 1, 0, 0});       // gone at the end
		end.put(4, new double[] {1, 1, 1, 0, 0});         // new at the end
		double[] r = StabilityOptions.compareMobPoses(start, end);
		assertArrayEquals(new double[] {3, 3, 2, 5.0, 30.0}, r, 1e-9);
		assertArrayEquals(new double[] {0, 0, 0, 0, 0}, StabilityOptions.compareMobPoses(Map.of(), Map.of()), 0.0);
	}
}
