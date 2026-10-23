package net.adiont.alacer.render;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.lwjgl.MemoryUtil;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;
import net.minecraft.client.render.platform.GLX;

/** Retains vanilla's four conventional BLOCK pointers in one compatibility-profile VAO per VBO. */
public final class TerrainVertexArray {
	private static final Set<TerrainVertexArray> LIVE = Collections.newSetFromMap(new IdentityHashMap<>());
	private int name;
	private ContextCapabilities context;
	private boolean ready, blocked;

	/** Binds the array; true means its pointer setup may be reused. */
	public boolean bind() {
		if (this.blocked) { return false; }
		ContextCapabilities current = GLContext.getCapabilities();
		if (this.context != null && this.context != current) {
			// Never delete an old context's numeric name in a new context: that could delete an unrelated VAO.
			LIVE.remove(this); this.name = 0; this.context = null; this.ready = false; this.blocked = true; ModernGl.contextDrop(); return false;
		}
		if (this.name == 0) {
			this.context = current; this.name = GL30.glGenVertexArrays(); ModernGl.created();
			LIVE.add(this);
			GL30.glBindVertexArray(this.name);
			GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
			GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
			GLX.clientActiveTexture(GLX.GL_TEXTURE0); GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
			GLX.clientActiveTexture(GLX.GL_TEXTURE1); GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
			GLX.clientActiveTexture(GLX.GL_TEXTURE0);
		} else { GL30.glBindVertexArray(this.name); }
		ModernGl.bound();
		return this.ready;
	}

	public boolean blocked() { return this.blocked; }
	public void configured(int buffer) { this.ready = true; ModernGl.setup(); check(buffer); }

	private static long pointer(int parameter) {
		ByteBuffer value = GL11.glGetPointer(parameter, 0L);
		return value == null ? 0L : MemoryUtil.getAddress0(value);
	}

	public void check(int buffer) {
		if (!ModernGl.verify()) { return; }
		boolean equal = GL11.glGetBoolean(GL11.GL_VERTEX_ARRAY) && GL11.glGetBoolean(GL11.GL_COLOR_ARRAY)
			&& GL11.glGetInteger(GL15.GL_VERTEX_ARRAY_BUFFER_BINDING) == buffer
			&& GL11.glGetInteger(GL15.GL_COLOR_ARRAY_BUFFER_BINDING) == buffer
			&& GL11.glGetInteger(GL11.GL_VERTEX_ARRAY_SIZE) == 3 && GL11.glGetInteger(GL11.GL_VERTEX_ARRAY_TYPE) == GL11.GL_FLOAT
			&& GL11.glGetInteger(GL11.GL_VERTEX_ARRAY_STRIDE) == 28 && pointer(GL11.GL_VERTEX_ARRAY_POINTER) == 0
			&& GL11.glGetInteger(GL11.GL_COLOR_ARRAY_SIZE) == 4 && GL11.glGetInteger(GL11.GL_COLOR_ARRAY_TYPE) == GL11.GL_UNSIGNED_BYTE
			&& GL11.glGetInteger(GL11.GL_COLOR_ARRAY_STRIDE) == 28 && pointer(GL11.GL_COLOR_ARRAY_POINTER) == 12;
		for (int unit = 0; unit < 2; unit++) {
			GLX.clientActiveTexture(unit == 0 ? GLX.GL_TEXTURE0 : GLX.GL_TEXTURE1);
			equal &= GL11.glGetBoolean(GL11.GL_TEXTURE_COORD_ARRAY)
				&& GL11.glGetInteger(GL15.GL_TEXTURE_COORD_ARRAY_BUFFER_BINDING) == buffer
				&& GL11.glGetInteger(GL11.GL_TEXTURE_COORD_ARRAY_SIZE) == 2
				&& GL11.glGetInteger(GL11.GL_TEXTURE_COORD_ARRAY_STRIDE) == 28
				&& GL11.glGetInteger(GL11.GL_TEXTURE_COORD_ARRAY_TYPE) == (unit == 0 ? GL11.GL_FLOAT : GL11.GL_SHORT)
				&& pointer(GL11.GL_TEXTURE_COORD_ARRAY_POINTER) == (unit == 0 ? 16 : 24);
		}
		GLX.clientActiveTexture(GLX.GL_TEXTURE0); ModernGl.checked(equal);
	}

	public void delete() {
		LIVE.remove(this); retire();
	}

	private void retire() {
		if (this.name != 0 && this.context == GLContext.getCapabilities()) { GL30.glDeleteVertexArrays(this.name); ModernGl.deleted(); }
		this.name = 0; this.context = null; this.ready = false; this.blocked = false;
	}

	/** Vanilla retains terrain VBO storage while at the title screen; retire our extra objects immediately. */
	public static void clear() {
		for (TerrainVertexArray array : LIVE) { array.retire(); }
		LIVE.clear();
	}
}
