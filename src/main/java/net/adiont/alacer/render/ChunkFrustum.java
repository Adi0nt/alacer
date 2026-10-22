package net.adiont.alacer.render;

import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.mixin.FrustumCullerAccessor;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.FrustumCuller;
import net.minecraft.client.render.FrustumData;
import net.minecraft.util.math.Box;

/**
 * Vanilla's frustum test for the chunk visibility search, evaluated at one corner per plane
 * instead of up to eight ({@code render.fastChunkFrustum}).
 *
 * <p>{@code FrustumCuller.isVisible(Box)} subtracts the camera offsets from the box and calls
 * {@code FrustumData.contains}, which rejects the box as soon as one of the six planes has no
 * corner with {@code side[0] * x + side[1] * y + side[2] * z + side[3] > 0.0} (float
 * coefficients promoted to double, evaluated left to right).
 *
 * <p>This class evaluates exactly that expression, at the corner that takes the larger
 * coordinate on each axis whose coefficient is {@code >= 0} and the smaller one otherwise. When
 * every coefficient is finite and every translated coordinate is at most 2^800 in magnitude,
 * no product or partial sum can overflow, so no corner's value is NaN or infinite. IEEE
 * multiplication and addition with round-to-nearest are monotone in each operand, so that
 * corner's value is then at least the value of every other corner, computed with the same
 * operations in the same order. Vanilla passes a plane if some corner is {@code > 0.0}, which is
 * the case exactly when that corner is: the decision is identical for every box. Outside these
 * bounds (NaN, infinite or huge values) vanilla's test is called instead.
 *
 * <p>Only the exact {@code FrustumCuller} class over a {@code FrustumData} or {@code Frustum} is
 * answered this way, and only while no other mod's mixin targets those classes; otherwise the
 * culler is called. The planes and offsets are read once per search, at its start; nothing
 * changes them during the search (client thread only).
 */
public final class ChunkFrustum {

	/** Translated coordinates up to this magnitude cannot overflow with finite float coefficients. */
	static final double COORDINATE_LIMIT = 0x1p800;
	static final int UNDECIDED = -1;
	static final int OUTSIDE = 0;
	static final int VISIBLE = 1;

	private static int compatibility;
	private static long compared;
	private static long mismatches;
	private static long undecided;

	private Culler culler;
	private double offsetX;
	private double offsetY;
	private double offsetZ;
	// Plane coefficients as doubles (float to double widening is exact).
	private double a0, b0, c0, d0;
	private double a1, b1, c1, d1;
	private double a2, b2, c2, d2;
	private double a3, b3, c3, d3;
	private double a4, b4, c4, d4;
	private double a5, b5, c5, d5;

	/**
	 * Reads the culler's planes and offsets for the boxes tested next.
	 *
	 * @return false if this test does not apply to the culler, which must then be called itself
	 */
	public boolean prepare(Culler culler) {
		this.culler = null;
		if (culler == null || culler.getClass() != FrustumCuller.class || !compatible()) {
			return false;
		}
		FrustumCullerAccessor access = (FrustumCullerAccessor) culler;
		FrustumData data = access.alacer$getFrustum();
		if (data == null || data.getClass() != FrustumData.class && data.getClass() != Frustum.class) {
			return false;
		}
		if (!load(data.frustum, access.alacer$getOffsetX(), access.alacer$getOffsetY(), access.alacer$getOffsetZ())) {
			return false;
		}
		this.culler = culler;
		return true;
	}

	/** Loads planes and offsets; false if a coefficient is not finite or the planes are malformed. */
	boolean load(float[][] planes, double offsetX, double offsetY, double offsetZ) {
		if (planes == null || planes.length != 6) {
			return false;
		}
		for (float[] plane : planes) {
			if (plane == null || plane.length < 4) {
				return false;
			}
			for (int i = 0; i < 4; i++) {
				if (!Float.isFinite(plane[i])) {
					return false;
				}
			}
		}
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		this.offsetZ = offsetZ;
		this.a0 = planes[0][0]; this.b0 = planes[0][1]; this.c0 = planes[0][2]; this.d0 = planes[0][3];
		this.a1 = planes[1][0]; this.b1 = planes[1][1]; this.c1 = planes[1][2]; this.d1 = planes[1][3];
		this.a2 = planes[2][0]; this.b2 = planes[2][1]; this.c2 = planes[2][2]; this.d2 = planes[2][3];
		this.a3 = planes[3][0]; this.b3 = planes[3][1]; this.c3 = planes[3][2]; this.d3 = planes[3][3];
		this.a4 = planes[4][0]; this.b4 = planes[4][1]; this.c4 = planes[4][2]; this.d4 = planes[4][3];
		this.a5 = planes[5][0]; this.b5 = planes[5][1]; this.c5 = planes[5][2]; this.d5 = planes[5][3];
		return true;
	}

	/** Vanilla's decision for the prepared culler. */
	public boolean isVisible(Box box) {
		int result = test(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
		if (result == UNDECIDED) {
			undecided++;
			return this.culler.isVisible(box);
		}
		return result == VISIBLE;
	}

	/** Runs vanilla's test and this one, counts any difference and returns vanilla's answer. */
	public boolean verify(Box box) {
		boolean vanilla = this.culler.isVisible(box);
		int result = test(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
		compared++;
		if (result == UNDECIDED) {
			undecided++;
		} else if ((result == VISIBLE) != vanilla) {
			mismatches++;
			if (mismatches <= 10) {
				Alacer.LOGGER.warn("Chunk frustum test mismatch for {} at offset {} {} {}: vanilla {}, plane test {}",
					box, this.offsetX, this.offsetY, this.offsetZ, vanilla, !vanilla);
			}
		}
		if (compared % 1_000_000 == 0) {
			Alacer.LOGGER.info("Chunk frustum verification: {}", summary());
		}
		return vanilla;
	}

	/**
	 * The decision for a box in world coordinates, or {@link #UNDECIDED} when a coordinate is NaN,
	 * infinite or too large for the argument in the class comment.
	 */
	int test(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		// The same subtractions as FrustumCuller.isVisible.
		double x0 = minX - this.offsetX;
		double y0 = minY - this.offsetY;
		double z0 = minZ - this.offsetZ;
		double x1 = maxX - this.offsetX;
		double y1 = maxY - this.offsetY;
		double z1 = maxZ - this.offsetZ;
		// False for NaN and infinities too. The rounded sum is at least each of its terms.
		if (!(Math.abs(x0) + Math.abs(y0) + Math.abs(z0) + Math.abs(x1) + Math.abs(y1) + Math.abs(z1) <= COORDINATE_LIMIT)) {
			return UNDECIDED;
		}
		// Boxes built from two block positions are not sorted, so order each axis here.
		double xl = x0 <= x1 ? x0 : x1;
		double xh = x0 <= x1 ? x1 : x0;
		double yl = y0 <= y1 ? y0 : y1;
		double yh = y0 <= y1 ? y1 : y0;
		double zl = z0 <= z1 ? z0 : z1;
		double zh = z0 <= z1 ? z1 : z0;
		// FrustumData.discriminant at the corner with the largest value, plane by plane.
		if (!(this.a0 * (this.a0 >= 0.0 ? xh : xl) + this.b0 * (this.b0 >= 0.0 ? yh : yl) + this.c0 * (this.c0 >= 0.0 ? zh : zl) + this.d0 > 0.0)
			|| !(this.a1 * (this.a1 >= 0.0 ? xh : xl) + this.b1 * (this.b1 >= 0.0 ? yh : yl) + this.c1 * (this.c1 >= 0.0 ? zh : zl) + this.d1 > 0.0)
			|| !(this.a2 * (this.a2 >= 0.0 ? xh : xl) + this.b2 * (this.b2 >= 0.0 ? yh : yl) + this.c2 * (this.c2 >= 0.0 ? zh : zl) + this.d2 > 0.0)
			|| !(this.a3 * (this.a3 >= 0.0 ? xh : xl) + this.b3 * (this.b3 >= 0.0 ? yh : yl) + this.c3 * (this.c3 >= 0.0 ? zh : zl) + this.d3 > 0.0)
			|| !(this.a4 * (this.a4 >= 0.0 ? xh : xl) + this.b4 * (this.b4 >= 0.0 ? yh : yl) + this.c4 * (this.c4 >= 0.0 ? zh : zl) + this.d4 > 0.0)
			|| !(this.a5 * (this.a5 >= 0.0 ? xh : xl) + this.b5 * (this.b5 >= 0.0 ? yh : yl) + this.c5 * (this.c5 >= 0.0 ? zh : zl) + this.d5 > 0.0)) {
			return OUTSIDE;
		}
		return VISIBLE;
	}

	/** True unless another mod's mixin targets one of the classes whose test this reproduces. */
	private static boolean compatible() {
		if (compatibility == 0) {
			compatibility = 1;
			try {
				for (Class<?> type : new Class<?>[]{FrustumCuller.class, FrustumData.class, Frustum.class}) {
					for (IMixinInfo mixin : Mixins.getMixinsForClass(type.getName())) {
						if (!mixin.getClassName().startsWith("net.adiont.alacer.")) {
							compatibility = -1;
							Alacer.LOGGER.info("render.fastChunkFrustum: {} is changed by mixin {}; the chunk visibility search calls the culler instead",
								type.getName(), mixin.getClassName());
						}
					}
				}
			} catch (RuntimeException | LinkageError e) {
				compatibility = -1;
				Alacer.LOGGER.warn("render.fastChunkFrustum: could not list the mixins applied to the frustum classes; the chunk visibility search calls the culler instead", e);
			}
		}
		return compatibility > 0;
	}

	public static String summary() {
		return compared + " boxes compared, " + mismatches + " mismatched, " + undecided + " answered by vanilla's test (outside the exact range)";
	}
}
