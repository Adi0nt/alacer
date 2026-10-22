package net.adiont.alacer.render;

import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import net.adiont.alacer.Alacer;

/** Debug-only, native feedback comparison; never used for performance measurements. */
public final class ModelListVerification {
	private static final FloatBuffer A = BufferUtils.createFloatBuffer(65536);
	private static final FloatBuffer B = BufferUtils.createFloatBuffer(65536);
	private static final FloatBuffer STATE_A = BufferUtils.createFloatBuffer(32);
	private static final FloatBuffer STATE_B = BufferUtils.createFloatBuffer(32);
	private static long comparisons, floats, mismatches, empty;

	private ModelListVerification() {
	}

	public static boolean compare(int vanilla, int alternate, int faces) {
		int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();
		GL11.glOrtho(-32, 32, -32, 32, -128, 128);
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();
		GL11.glViewport(0, 0, 256, 256);
		boolean same = true;
		try {
			for (int view = 0; view < 3; view++) {
				GL11.glLoadIdentity();
				GL11.glRotatef(23 + 37 * view, 0.3F, 0.7F, 0.2F);
				GL11.glScalef(view == 2 ? -1.25F : 0.75F, 1.5F, 0.5F);
				int na = feedback(vanilla, A, STATE_A);
				int nb = feedback(alternate, B, STATE_B);
				comparisons++;
				boolean equal = na > 0 && na == nb;
				if (na == 0 || nb == 0) {
					empty++;
				}
				if (equal) {
					for (int i = 0; i < na; i++) {
						floats++;
						if (Float.floatToRawIntBits(A.get(i)) != Float.floatToRawIntBits(B.get(i))) {
							equal = false;
						}
					}
					for (int i = 0; i < 7; i++) {
						if (Float.floatToRawIntBits(STATE_A.get(i)) != Float.floatToRawIntBits(STATE_B.get(i))) {
							equal = false;
						}
					}
				}
				if (!equal) {
					mismatches++;
					same = false;
					if (mismatches <= 10) {
						Alacer.LOGGER.warn("Model list feedback differs: faces={}, view={}, floats={} vs {}", faces, view, na, nb);
					}
				}
			}
		} finally {
			GL11.glMatrixMode(GL11.GL_MODELVIEW);
			GL11.glPopMatrix();
			GL11.glMatrixMode(GL11.GL_PROJECTION);
			GL11.glPopMatrix();
			GL11.glMatrixMode(mode);
			GL11.glPopAttrib();
		}
		return same;
	}

	private static int feedback(int list, FloatBuffer buffer, FloatBuffer state) {
		buffer.clear();
		GL11.glPushAttrib(GL11.GL_CURRENT_BIT);
		try {
			GL11.glFeedbackBuffer(GL11.GL_3D_COLOR_TEXTURE, buffer);
			GL11.glRenderMode(GL11.GL_FEEDBACK);
			GL11.glCallList(list);
			int count = GL11.glRenderMode(GL11.GL_RENDER);
			state.clear();
			GL11.glGetFloat(GL11.GL_CURRENT_NORMAL, state);
			state.position(3);
			GL11.glGetFloat(GL11.GL_CURRENT_TEXTURE_COORDS, state);
			return count;
		} finally {
			GL11.glPopAttrib();
		}
	}

	public static String summary() {
		return comparisons + " native feedback comparisons, " + floats + " floats compared, "
			+ mismatches + " mismatches, " + empty + " empty comparisons";
	}
}
