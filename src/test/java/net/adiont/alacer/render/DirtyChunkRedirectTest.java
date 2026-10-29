package net.adiont.alacer.render;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.*;

/** Inspects bytecode without initializing Minecraft or applying the mixin. */
class DirtyChunkRedirectTest {

	private static final String START = "Lnet/minecraft/client/render/world/ChunkRenderDispatcher;clear()V";

	@Test void dirtySetRedirectsExcludeVanillaDirectionSets() throws IOException {
		ClassNode mixin = read("net/adiont/alacer/mixin/WorldRendererMixin.class");
		ClassNode vanilla = read("net/minecraft/client/render/world/WorldRenderer.class");
		MethodNode setup = vanilla.methods.stream().filter(m -> m.name.equals("setupRender")).findFirst().orElseThrow();
		Map<String, String> targets = Map.of(
			"alacer$reusedDirtySet", "Lcom/google/common/collect/Sets;newLinkedHashSet()Ljava/util/LinkedHashSet;",
			"alacer$fastDirtyContains", "Ljava/util/Set;contains(Ljava/lang/Object;)Z",
			"alacer$fastDirtyAdd", "Ljava/util/Set;add(Ljava/lang/Object;)Z",
			"alacer$fastDirtyAddAll", "Ljava/util/Set;addAll(Ljava/util/Collection;)Z");
		for (var entry : targets.entrySet()) {
			MethodNode method = mixin.methods.stream().filter(m -> m.name.equals(entry.getKey())).findFirst().orElseThrow();
			AnnotationNode redirect = method.visibleAnnotations.stream()
				.filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;")).findFirst().orElseThrow();
			assertEquals(1, value(redirect, "require"));
			assertEquals(1, value(redirect, "allow"));
			AnnotationNode slice = (AnnotationNode) ((List<?>) value(redirect, "slice")).getFirst();
			AnnotationNode from = (AnnotationNode) value(slice, "from");
			assertEquals(START, value(from, "target"));
			AnnotationNode at = (AnnotationNode) ((List<?>) value(redirect, "at")).getFirst();
			assertEquals(entry.getValue(), value(at, "target"));
			boolean inside = false;
			int selected = 0, excluded = 0, boundaries = 0;
			for (var instruction : setup.instructions) {
				if (instruction instanceof MethodInsnNode call) {
					String member = "L" + call.owner + ";" + call.name + call.desc;
					if (member.equals(START)) { inside = true; boundaries++; }
					if (member.equals(entry.getValue())) {
						if (inside) selected++; else excluded++;
					}
				}
			}
			assertEquals(1, boundaries, "one scheduling boundary");
			assertEquals(1, selected, entry.getKey() + " selects only its dirty-set call");
			if (!entry.getKey().equals("alacer$reusedDirtySet")) {
				assertTrue(excluded > 0, entry.getKey() + " leaves direction-set operations untouched");
			}
		}
	}

	private static Object value(AnnotationNode node, String key) {
		for (int i = 0; i < node.values.size(); i += 2) {
			if (node.values.get(i).equals(key)) return node.values.get(i + 1);
		}
		throw new AssertionError("Missing annotation member " + key);
	}

	private static ClassNode read(String path) throws IOException {
		try (var stream = DirtyChunkRedirectTest.class.getClassLoader().getResourceAsStream(path)) {
			assertNotNull(stream, path);
			ClassNode node = new ClassNode();
			new ClassReader(stream).accept(node, 0);
			return node;
		}
	}
}
