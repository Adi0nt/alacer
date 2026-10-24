package net.adiont.alacer.render;

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.mixin.FrustumCullerAccessor;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.FrustumCuller;
import net.minecraft.client.render.FrustumData;
import net.minecraft.util.math.Box;

/**
 * SIMD-vectorized frustum testing of chunk bounding boxes using the Java 25 Vector API
 * ({@code render.vectorFrustum}).
 *
 * <p>Vanilla evaluates the 6 frustum planes sequentially in double precision. This class packs
 * the plane equations into 256-bit SIMD registers (4-wide double vectors) and evaluates the
 * planes with separate multiply/add operations in vanilla's left-to-right order.
 */
public final class VectorFrustum implements VectorFrustumAccess.Predicate {

	private static final VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_256;
	static final double COORDINATE_LIMIT = 0x1p800;
	public static final int UNDECIDED = -1;
	public static final int OUTSIDE = 0;
	public static final int VISIBLE = 1;

	private static long compared;
	private static long mismatches;
	private static long undecided;

	private Culler culler;
	private double offsetX;
	private double offsetY;
	private double offsetZ;

	// Packed plane coefficients for 256-bit SIMD vectors (4 doubles per vector).
	// Vector 0: planes 0..3. Vector 1: planes 4..5, lanes 2..3 padding.
	private final double[] planeA0 = new double[4];
	private final double[] planeB0 = new double[4];
	private final double[] planeC0 = new double[4];
	private final double[] planeD0 = new double[4];

	private final double[] planeA1 = new double[4];
	private final double[] planeB1 = new double[4];
	private final double[] planeC1 = new double[4];
	private final double[] planeD1 = new double[4];

	private final boolean[] signA = new boolean[6];
	private final boolean[] signB = new boolean[6];
	private final boolean[] signC = new boolean[6];

	private final double[] cornerX0 = new double[4];
	private final double[] cornerY0 = new double[4];
	private final double[] cornerZ0 = new double[4];

	private final double[] cornerX1 = new double[4];
	private final double[] cornerY1 = new double[4];
	private final double[] cornerZ1 = new double[4];

	/**
	 * Reads the culler's planes and offsets for subsequent queries.
	 *
	 * @return false if the culler is incompatible or invalid
	 */
	public boolean prepare(Culler culler) {
		this.culler = null;
		if (culler == null || culler.getClass() != FrustumCuller.class || !ChunkFrustum.compatible()) {
			return false;
		}
		FrustumCullerAccessor access = (FrustumCullerAccessor) culler;
		FrustumData data = access.alacer$getFrustum();
		if (data == null || (data.getClass() != FrustumData.class && data.getClass() != Frustum.class)) {
			return false;
		}
		if (!load(data.frustum, access.alacer$getOffsetX(), access.alacer$getOffsetY(), access.alacer$getOffsetZ())) {
			return false;
		}
		this.culler = culler;
		return true;
	}

	boolean load(float[][] planes, double offsetX, double offsetY, double offsetZ) {
		if (planes == null || planes.length != 6) {
			return false;
		}
		for (int i = 0; i < 6; i++) {
			float[] p = planes[i];
			if (p == null || p.length < 4) {
				return false;
			}
			for (int j = 0; j < 4; j++) {
				if (!Float.isFinite(p[j])) {
					return false;
				}
			}
			this.signA[i] = p[0] >= 0.0f;
			this.signB[i] = p[1] >= 0.0f;
			this.signC[i] = p[2] >= 0.0f;
		}

		for (int i = 0; i < 4; i++) {
			this.planeA0[i] = planes[i][0];
			this.planeB0[i] = planes[i][1];
			this.planeC0[i] = planes[i][2];
			this.planeD0[i] = planes[i][3];
		}

		this.planeA1[0] = planes[4][0]; this.planeA1[1] = planes[5][0];
		this.planeB1[0] = planes[4][1]; this.planeB1[1] = planes[5][1];
		this.planeC1[0] = planes[4][2]; this.planeC1[1] = planes[5][2];
		this.planeD1[0] = planes[4][3]; this.planeD1[1] = planes[5][3];

		// Padding lanes 2 & 3: positive constant ensures LE 0.0 comparison is always false.
		this.planeA1[2] = 0.0; this.planeA1[3] = 0.0;
		this.planeB1[2] = 0.0; this.planeB1[3] = 0.0;
		this.planeC1[2] = 0.0; this.planeC1[3] = 0.0;
		this.planeD1[2] = 1.0; this.planeD1[3] = 1.0;

		this.offsetX = offsetX;
		this.offsetY = offsetY;
		this.offsetZ = offsetZ;
		return true;
	}

	public boolean isVisible(Box box) {
		int result = test(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
		if (result == UNDECIDED) {
			undecided++;
			return this.culler != null ? this.culler.isVisible(box) : true;
		}
		return result == VISIBLE;
	}

	public boolean verify(Box box, Culler culler) {
		boolean vanilla = culler.isVisible(box);
		int result = test(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
		compared++;
		if (result == UNDECIDED) {
			undecided++;
		} else if ((result == VISIBLE) != vanilla) {
			mismatches++;
			if (mismatches <= 10) {
				Alacer.LOGGER.warn("Vector frustum mismatch for box {} at offset {} {} {}: vanilla={}, vector={}",
					box, this.offsetX, this.offsetY, this.offsetZ, vanilla, !vanilla);
			}
		}
		if (compared % 1_000_000 == 0) {
			Alacer.LOGGER.info("Vector frustum verification: {}", summary());
		}
		return vanilla;
	}

	/**
	 * Tests all six planes in two batches, preserving vanilla's rounding order.
	 */
	public int test(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		double x0 = minX - this.offsetX;
		double y0 = minY - this.offsetY;
		double z0 = minZ - this.offsetZ;
		double x1 = maxX - this.offsetX;
		double y1 = maxY - this.offsetY;
		double z1 = maxZ - this.offsetZ;

		if (!(Math.abs(x0) + Math.abs(y0) + Math.abs(z0) + Math.abs(x1) + Math.abs(y1) + Math.abs(z1) <= COORDINATE_LIMIT)) {
			return UNDECIDED;
		}

		double xl = x0 <= x1 ? x0 : x1;
		double xh = x0 <= x1 ? x1 : x0;
		double yl = y0 <= y1 ? y0 : y1;
		double yh = y0 <= y1 ? y1 : y0;
		double zl = z0 <= z1 ? z0 : z1;
		double zh = z0 <= z1 ? z1 : z0;

		// Vector 0: planes 0..3
		this.cornerX0[0] = this.signA[0] ? xh : xl;
		this.cornerY0[0] = this.signB[0] ? yh : yl;
		this.cornerZ0[0] = this.signC[0] ? zh : zl;

		this.cornerX0[1] = this.signA[1] ? xh : xl;
		this.cornerY0[1] = this.signB[1] ? yh : yl;
		this.cornerZ0[1] = this.signC[1] ? zh : zl;

		this.cornerX0[2] = this.signA[2] ? xh : xl;
		this.cornerY0[2] = this.signB[2] ? yh : yl;
		this.cornerZ0[2] = this.signC[2] ? zh : zl;

		this.cornerX0[3] = this.signA[3] ? xh : xl;
		this.cornerY0[3] = this.signB[3] ? yh : yl;
		this.cornerZ0[3] = this.signC[3] ? zh : zl;

		DoubleVector va0 = DoubleVector.fromArray(SPECIES, this.planeA0, 0);
		DoubleVector vb0 = DoubleVector.fromArray(SPECIES, this.planeB0, 0);
		DoubleVector vc0 = DoubleVector.fromArray(SPECIES, this.planeC0, 0);
		DoubleVector vd0 = DoubleVector.fromArray(SPECIES, this.planeD0, 0);

		DoubleVector vx0 = DoubleVector.fromArray(SPECIES, this.cornerX0, 0);
		DoubleVector vy0 = DoubleVector.fromArray(SPECIES, this.cornerY0, 0);
		DoubleVector vz0 = DoubleVector.fromArray(SPECIES, this.cornerZ0, 0);

		// dot0 = va * vx + vb * vy + vc * vz + vd
		DoubleVector dot0 = va0.mul(vx0).add(vb0.mul(vy0)).add(vc0.mul(vz0)).add(vd0);
		VectorMask<Double> mask0 = dot0.compare(VectorOperators.LE, 0.0);
		if (mask0.anyTrue()) {
			return OUTSIDE;
		}

		// Vector 1: planes 4..5
		this.cornerX1[0] = this.signA[4] ? xh : xl;
		this.cornerY1[0] = this.signB[4] ? yh : yl;
		this.cornerZ1[0] = this.signC[4] ? zh : zl;

		this.cornerX1[1] = this.signA[5] ? xh : xl;
		this.cornerY1[1] = this.signB[5] ? yh : yl;
		this.cornerZ1[1] = this.signC[5] ? zh : zl;

		this.cornerX1[2] = 0.0; this.cornerX1[3] = 0.0;
		this.cornerY1[2] = 0.0; this.cornerY1[3] = 0.0;
		this.cornerZ1[2] = 0.0; this.cornerZ1[3] = 0.0;

		DoubleVector va1 = DoubleVector.fromArray(SPECIES, this.planeA1, 0);
		DoubleVector vb1 = DoubleVector.fromArray(SPECIES, this.planeB1, 0);
		DoubleVector vc1 = DoubleVector.fromArray(SPECIES, this.planeC1, 0);
		DoubleVector vd1 = DoubleVector.fromArray(SPECIES, this.planeD1, 0);

		DoubleVector vx1 = DoubleVector.fromArray(SPECIES, this.cornerX1, 0);
		DoubleVector vy1 = DoubleVector.fromArray(SPECIES, this.cornerY1, 0);
		DoubleVector vz1 = DoubleVector.fromArray(SPECIES, this.cornerZ1, 0);

		DoubleVector dot1 = va1.mul(vx1).add(vb1.mul(vy1)).add(vc1.mul(vz1)).add(vd1);
		VectorMask<Double> mask1 = dot1.compare(VectorOperators.LE, 0.0);
		// Lanes 0 & 1 are planes 4 & 5
		if (mask1.laneIsSet(0) || mask1.laneIsSet(1)) {
			return OUTSIDE;
		}

		return VISIBLE;
	}

	public static String summary() {
		return compared + " boxes compared, " + mismatches + " mismatched, " + undecided + " outside exact limit";
	}
	@Override public String verificationSummary() { return summary(); }
}
