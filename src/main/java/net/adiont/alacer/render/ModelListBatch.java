package net.adiont.alacer.render;

import java.nio.ByteBuffer;
import java.lang.ref.WeakReference;
import java.util.List;

import org.lwjgl.opengl.GL11;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.mixin.BoxAccessor;
import net.minecraft.client.render.model.Box;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.Polygon;
import net.minecraft.client.render.platform.MemoryTracker;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;

/**
 * Captures vanilla's face uploads during ModelPart.compile, then compiles their unchanged
 * vertex stream in one draw. The vanilla list and all its compilation hooks still run.
 * Fixed scratch storage is shared on the client thread; parts keep only two list identifiers.
 */
public final class ModelListBatch {

	private static final int MAX_FACES = 1024;
	private static final int MAX_LISTS = 512;
	private static final WeakReference<?>[] OWNERS = new WeakReference<?>[MAX_LISTS];
	private static final int[] LISTS = new int[MAX_LISTS];
	private static final int INTS_PER_VERTEX = 6;
	private static final int[] VERTICES = new int[MAX_FACES * 4 * INTS_PER_VERTEX];
	/** Checked by the upload hook, which does nothing outside a model compilation. */
	public static boolean capturing;
	private static int expectedFaces, faces, words;
	private static boolean valid;
	private static ModelPart capturingOwner;
	private static long compiled, rejected, sourceDraws;

	private ModelListBatch() {
	}

	public static void begin(ModelPart part) {
		capturing = false;
		capturingOwner = null;
		expectedFaces = faces = words = 0;
		valid = false;
		// Subclasses can issue arbitrary commands and cannot safely be concatenated.
		if (part.getClass() != ModelPart.class || part.boxes.isEmpty()) {
			return;
		}
		List<Box> boxes = part.boxes;
		for (int i = 0; i < boxes.size(); i++) {
			Box box = boxes.get(i);
			if (box == null || box.getClass() != Box.class) {
				return;
			}
			Polygon[] polygons = ((BoxAccessor) box).alacer$faces();
			if (polygons == null || polygons.length != 6) {
				return;
			}
			for (int j = 0; j < polygons.length; j++) {
				if (polygons[j] == null || polygons[j].getClass() != Polygon.class) {
					return;
				}
			}
			expectedFaces += polygons.length;
			if (expectedFaces > MAX_FACES) {
				return;
			}
		}
		capturingOwner = part;
		valid = capturing = true;
	}

	/** Called before vanilla's uploader reads or clears the already-finished buffer. */
	public static void capture(BufferBuilder builder) {
		if (!valid) {
			return;
		}
		if (builder.getDrawMode() != GL11.GL_QUADS || builder.getVertexCount() != 4
			|| builder.getFormat().getVertexSize() != INTS_PER_VERTEX * 4
			|| !DefaultVertexFormat.ENTITY.equals(builder.getFormat()) || faces >= expectedFaces) {
			valid = false;
			return;
		}
		ByteBuffer data = builder.getBuffer();
		for (int i = 0; i < 4 * INTS_PER_VERTEX; i++) {
			VERTICES[words++] = data.getInt(i * 4);
		}
		faces++;
	}

	/** Called after glEndList; an existing alternate is redefined on vanilla recompilation. */
	public static int finish(ModelPart owner, int vanillaList, int alternate) {
		capturing = false;
		boolean complete = owner == capturingOwner && valid && faces == expectedFaces && faces > 0;
		capturingOwner = null;
		if (!complete) {
			rejected++;
			if (alternate != 0) {
				release(alternate);
			}
			return 0;
		}
		if (alternate == 0) {
			alternate = allocate(owner);
			if (alternate == 0) {
				rejected++;
				return 0;
			}
		}
		Tesselator tess = Tesselator.getInstance();
		BufferBuilder buffer = tess.getBuffer();
		if (buffer.getBuffer().capacity() < words * 4) {
			rejected++;
			release(alternate);
			return 0;
		}
		buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.ENTITY);
		// vertices(int[]) takes the whole array; emit only the captured prefix without allocating.
		ByteBuffer data = buffer.getBuffer();
		for (int i = 0; i < words; i++) {
			data.putInt(i * 4, VERTICES[i]);
		}
		((net.adiont.alacer.mixin.BufferBuilderAccessor) buffer).alacer$vertexCount(faces * 4);
		// Negative control for the debug verifier: perturb a real uploaded position.
		if (compiled == 0 && Alacer.get().config().verifyBatchModelLists
			&& Boolean.getBoolean("alacer.benchmark.modelListFault")) {
			data.putFloat(0, data.getFloat(0) + 0.125F);
		}
		GL11.glNewList(alternate, GL11.GL_COMPILE);
		tess.end();
		GL11.glEndList();
		compiled++;
		sourceDraws += faces;
		if (Alacer.get().config().verifyBatchModelLists && !ModelListVerification.compare(vanillaList, alternate, faces)) {
			release(alternate);
			return 0;
		}
		return alternate;
	}

	/** Cold path only: reclaim lists for collected parts and cap driver memory ownership. */
	private static int allocate(ModelPart owner) {
		int free = -1;
		for (int i = 0; i < MAX_LISTS; i++) {
			if (OWNERS[i] != null && OWNERS[i].get() == null) {
				MemoryTracker.releaseList(LISTS[i]);
				OWNERS[i] = null;
				LISTS[i] = 0;
			}
			if (OWNERS[i] == null && free < 0) {
				free = i;
			}
		}
		if (free < 0) {
			return 0;
		}
		int list = MemoryTracker.getLists(1);
		OWNERS[free] = new WeakReference<>(owner);
		LISTS[free] = list;
		return list;
	}

	private static void release(int list) {
		for (int i = 0; i < MAX_LISTS; i++) {
			if (LISTS[i] == list) {
				OWNERS[i] = null;
				LISTS[i] = 0;
				break;
			}
		}
		MemoryTracker.releaseList(list);
	}

	public static int select(int vanilla, int source, int alternate) {
		return vanilla == source && alternate != 0 && Alacer.get().config().batchModelLists ? alternate : vanilla;
	}

	public static String summary() {
		return compiled + " lists compiled, " + sourceDraws + " face draws -> " + compiled
			+ " batched draws, " + rejected + " unsupported compilations; " + ModelListVerification.summary();
	}
}
