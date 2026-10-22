package net.adiont.alacer.render;

import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * CPU copy of OpenGL's fixed-function modelview and projection matrix stacks, following the
 * same operations in the same order (column-major, each operation multiplies on the right).
 *
 * <p>The tracker only knows a matrix after it has seen that matrix loaded with
 * {@link #loadIdentity()}; everything before is unknown. Popping past the first tracked stack
 * level also makes the matrix unknown, since the level below was never seen. Other matrix modes
 * (texture, color) are ignored.
 *
 * <p>Pure Java: no OpenGL calls, so it can be unit tested. Not thread safe.
 */
public final class MatrixTracker {

	public static final int MODELVIEW = 0x1700;
	public static final int PROJECTION = 0x1701;

	private static final int DEPTH = 32;
	/** {@code GLU.PI}, so the perspective matrix matches LWJGL's float arithmetic. */
	private static final float GLU_PI = 3.14159265358979323846f;

	private final Stack modelview = new Stack();
	private final Stack projection = new Stack();
	private final float[] operand = new float[16];
	private Stack current;

	public MatrixTracker() {
		reset();
	}

	/** Forgets both matrices; the mode is assumed to be modelview until {@link #matrixMode} says otherwise. */
	public void reset() {
		this.modelview.reset();
		this.projection.reset();
		this.current = this.modelview;
	}

	public void matrixMode(int mode) {
		this.current = mode == MODELVIEW ? this.modelview : mode == PROJECTION ? this.projection : null;
	}

	public void loadIdentity() {
		if (this.current != null) {
			this.current.loadIdentity();
		}
	}

	public void push() {
		if (this.current != null) {
			this.current.push();
		}
	}

	public void pop() {
		if (this.current != null) {
			this.current.pop();
		}
	}

	public void translate(float x, float y, float z) {
		Stack s = this.current;
		if (s == null || !s.known()) {
			return;
		}
		float[] m = s.data;
		int o = s.offset();
		for (int i = 0; i < 4; i++) {
			m[o + 12 + i] = m[o + i] * x + m[o + 4 + i] * y + m[o + 8 + i] * z + m[o + 12 + i];
		}
	}

	public void scale(float x, float y, float z) {
		Stack s = this.current;
		if (s == null || !s.known()) {
			return;
		}
		float[] m = s.data;
		int o = s.offset();
		for (int i = 0; i < 4; i++) {
			m[o + i] *= x;
			m[o + 4 + i] *= y;
			m[o + 8 + i] *= z;
		}
	}

	/** {@code glRotatef}: {@code angle} in degrees around the axis (normalized as GL does). */
	public void rotate(float angle, float x, float y, float z) {
		Stack s = this.current;
		if (s == null || !s.known()) {
			return;
		}
		float length = (float) Math.sqrt(x * x + y * y + z * z);
		if (length == 0.0F) {
			return;
		}
		if (length != 1.0F) {
			x /= length;
			y /= length;
			z /= length;
		}
		double radians = Math.toRadians(angle);
		float c = (float) Math.cos(radians);
		float sn = (float) Math.sin(radians);
		float t = 1.0F - c;
		float[] r = this.operand;
		r[0] = x * x * t + c;
		r[1] = y * x * t + z * sn;
		r[2] = x * z * t - y * sn;
		r[3] = 0.0F;
		r[4] = x * y * t - z * sn;
		r[5] = y * y * t + c;
		r[6] = y * z * t + x * sn;
		r[7] = 0.0F;
		r[8] = x * z * t + y * sn;
		r[9] = y * z * t - x * sn;
		r[10] = z * z * t + c;
		r[11] = 0.0F;
		r[12] = 0.0F;
		r[13] = 0.0F;
		r[14] = 0.0F;
		r[15] = 1.0F;
		s.multiply(r);
	}

	/** {@code glMultMatrix}: 16 floats from the buffer's position, which is not changed. */
	public void multiply(FloatBuffer matrix) {
		Stack s = this.current;
		if (s == null || !s.known()) {
			return;
		}
		int p = matrix.position();
		for (int i = 0; i < 16; i++) {
			this.operand[i] = matrix.get(p + i);
		}
		s.multiply(this.operand);
	}

	public void ortho(double left, double right, double bottom, double top, double near, double far) {
		Stack s = this.current;
		if (s == null || !s.known()) {
			return;
		}
		float[] r = this.operand;
		Arrays.fill(r, 0.0F);
		r[0] = (float) (2.0 / (right - left));
		r[5] = (float) (2.0 / (top - bottom));
		r[10] = (float) (-2.0 / (far - near));
		r[12] = (float) (-(right + left) / (right - left));
		r[13] = (float) (-(top + bottom) / (top - bottom));
		r[14] = (float) (-(far + near) / (far - near));
		r[15] = 1.0F;
		s.multiply(r);
	}

	/** Same arithmetic as LWJGL's {@code Project.gluPerspective}, including its early return. */
	public void perspective(float fovy, float aspect, float zNear, float zFar) {
		Stack s = this.current;
		if (s == null || !s.known()) {
			return;
		}
		float radians = fovy / 2 * GLU_PI / 180;
		float deltaZ = zFar - zNear;
		float sine = (float) Math.sin(radians);
		if (deltaZ == 0 || sine == 0 || aspect == 0) {
			return;
		}
		float cotangent = (float) Math.cos(radians) / sine;
		float[] r = this.operand;
		Arrays.fill(r, 0.0F);
		r[0] = cotangent / aspect;
		r[5] = cotangent;
		r[10] = -(zFar + zNear) / deltaZ;
		r[11] = -1;
		r[14] = -2 * zNear * zFar / deltaZ;
		s.multiply(r);
	}

	public boolean isKnown(int mode) {
		Stack s = stack(mode);
		return s != null && s.known();
	}

	/** Copies the matrix into {@code out} (16 floats from index 0). Only meaningful if {@link #isKnown}. */
	public void get(int mode, float[] out) {
		Stack s = stack(mode);
		System.arraycopy(s.data, s.offset(), out, 0, 16);
	}

	private Stack stack(int mode) {
		return mode == MODELVIEW ? this.modelview : mode == PROJECTION ? this.projection : null;
	}

	/** One matrix stack; levels below the first tracked one are never seen. */
	private static final class Stack {
		final float[] data = new float[DEPTH * 16];
		final boolean[] known = new boolean[DEPTH];
		private final float[] product = new float[16];
		/** Current level, relative to the level that was current when tracking started. */
		int level;
		/** Pushes beyond {@link #DEPTH}, which GL performs but this copy does not. */
		int overflow;

		void reset() {
			this.level = 0;
			this.overflow = 0;
			this.known[0] = false;
		}

		int offset() {
			return this.level * 16;
		}

		boolean known() {
			return this.known[this.level];
		}

		void loadIdentity() {
			int o = offset();
			for (int i = 0; i < 16; i++) {
				this.data[o + i] = i % 5 == 0 ? 1.0F : 0.0F;
			}
			this.known[this.level] = true;
		}

		void push() {
			if (this.overflow > 0 || this.level + 1 >= DEPTH) {
				// Deeper than tracked: the matrix stays correct in GL but is unknown here.
				this.overflow++;
				this.known[this.level] = false;
				return;
			}
			System.arraycopy(this.data, offset(), this.data, offset() + 16, 16);
			this.known[this.level + 1] = this.known[this.level];
			this.level++;
		}

		void pop() {
			if (this.overflow > 0) {
				this.overflow--;
				return;
			}
			if (this.level == 0) {
				this.known[0] = false;
				return;
			}
			this.level--;
		}

		/** current = current * rhs. */
		void multiply(float[] rhs) {
			float[] m = this.data;
			int o = offset();
			float[] p = this.product;
			for (int col = 0; col < 4; col++) {
				float r0 = rhs[col * 4];
				float r1 = rhs[col * 4 + 1];
				float r2 = rhs[col * 4 + 2];
				float r3 = rhs[col * 4 + 3];
				for (int row = 0; row < 4; row++) {
					p[col * 4 + row] = m[o + row] * r0 + m[o + 4 + row] * r1 + m[o + 8 + row] * r2 + m[o + 12 + row] * r3;
				}
			}
			System.arraycopy(p, 0, m, o, 16);
		}
	}
}
