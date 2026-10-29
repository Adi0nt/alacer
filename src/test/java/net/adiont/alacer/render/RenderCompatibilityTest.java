package net.adiont.alacer.render;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production runs on intermediary class names, so the startup compatibility check must name vanilla
 * targets through class constants that are remapped with the JAR, never named-mapping strings.
 */
class RenderCompatibilityTest {

	@Test void vanillaTargetsAreRemappableClassConstants() throws IOException {
		Set<String> strings = new HashSet<>(), classes = new HashSet<>();
		for (MethodNode method : read("net/adiont/alacer/render/RenderCompatibility.class").methods) {
			for (var instruction : method.instructions) {
				if (instruction instanceof LdcInsnNode ldc) {
					if (ldc.cst instanceof String s) strings.add(s);
					else if (ldc.cst instanceof Type t) classes.add(t.getInternalName());
				}
			}
		}
		for (String s : strings) assertFalse(s.startsWith("net.minecraft."), "hard-coded named class " + s);
		assertTrue(classes.containsAll(Set.of(
			"net/minecraft/block/Block", "net/minecraft/client/world/ClientWorld",
			"net/minecraft/client/render/vertex/VertexBuffer", "net/minecraft/client/render/world/VboRenderChunkList",
			"net/minecraft/client/render/world/RenderChunkStorage", "net/minecraft/client/render/world/RenderChunk")), classes::toString);
	}

	private static ClassNode read(String path) throws IOException {
		try (InputStream in = RenderCompatibilityTest.class.getClassLoader().getResourceAsStream(path)) {
			assertNotNull(in, path);
			ClassNode node = new ClassNode();
			new ClassReader(in).accept(node, 0);
			return node;
		}
	}
}
