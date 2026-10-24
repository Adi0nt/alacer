package net.adiont.alacer.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisplayTickMethodResolverTest {
	public static class MappedBlock { public void m_41538657(int argument) { } }
	public static class MappedTorch extends MappedBlock { @Override public void m_41538657(int argument) { } }
	public static class NamedBlock { public void randomDisplayTick(int argument) { } }
	@Test void productionMappingFindsInheritedAndOverriddenHandlers() {
		String name = DisplayTickMethodResolver.resolve(MappedBlock.class, "randomDisplayTick", () -> "m_41538657", int.class);
		assertEquals("m_41538657", name);
		assertFalse(DisplayTickMethodResolver.overrides(MappedBlock.class, MappedBlock.class, name, int.class));
		assertTrue(DisplayTickMethodResolver.overrides(MappedBlock.class, MappedTorch.class, name, int.class));
	}
	@Test void missingMappingsKeepCallbacksAndNamedRuntimeDoesNotNeedFabric() {
		assertEquals("randomDisplayTick", DisplayTickMethodResolver.resolve(NamedBlock.class, "randomDisplayTick", () -> { throw new AssertionError("must not load mappings"); }, int.class));
		assertNull(DisplayTickMethodResolver.resolve(MappedBlock.class, "randomDisplayTick", () -> "wrong", int.class));
		assertTrue(DisplayTickMethodResolver.overrides(MappedBlock.class, MappedBlock.class, null, int.class));
	}
}
