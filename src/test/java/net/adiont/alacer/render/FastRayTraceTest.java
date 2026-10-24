package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.util.math.Vec3d;

class FastRayTraceTest {
	@Test
	void verificationRequiresExactHitCoordinates() {
		net.minecraft.world.HitResult a = new net.minecraft.world.HitResult(new Vec3d(0.0, 1, 2), net.minecraft.util.math.Direction.UP, net.minecraft.util.math.BlockPos.ORIGIN);
		net.minecraft.world.HitResult b = new net.minecraft.world.HitResult(new Vec3d(0.0, 1, 2), net.minecraft.util.math.Direction.UP, net.minecraft.util.math.BlockPos.ORIGIN);
		assertTrue(FastRayTrace.sameHit(a, b));
		b.facePos = new Vec3d(Math.nextUp(0.0), 1, 2);
		assertFalse(FastRayTrace.sameHit(a, b));
		b.facePos = new Vec3d(-0.0, 1, 2);
		// Vec3d's own constructor canonicalizes signed zero before either raycaster sees it.
		assertTrue(FastRayTrace.sameHit(a, b));
	}

	@BeforeEach
	void setUp() {
		FastRayTrace.clear();
	}

	@Test
	void nanCoordinatesReturnNullImmediately() {
		Vec3d nanFrom = new Vec3d(Double.NaN, 0.0, 0.0);
		Vec3d validTo = new Vec3d(1.0, 1.0, 1.0);
		assertNull(FastRayTrace.rayTrace(null, nanFrom, validTo, false, false, false));

		Vec3d validFrom = new Vec3d(0.0, 0.0, 0.0);
		Vec3d nanTo = new Vec3d(0.0, Double.NaN, 0.0);
		assertNull(FastRayTrace.rayTrace(null, validFrom, nanTo, false, false, false));

		assertEquals(2, FastRayTrace.traces());
		assertEquals(0, FastRayTrace.hits());
		assertEquals(0, FastRayTrace.misses());
	}

	@Test
	void clearResetsCounters() {
		Vec3d nan = new Vec3d(Double.NaN, 0, 0);
		FastRayTrace.rayTrace(null, nan, nan, false, false, false);
		assertEquals(1, FastRayTrace.traces());

		FastRayTrace.clear();
		assertEquals(0, FastRayTrace.traces());
		assertEquals(0, FastRayTrace.hits());
		assertEquals(0, FastRayTrace.misses());
		assertEquals(0, FastRayTrace.mismatches());
	}
}
