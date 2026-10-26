package net.adiont.alacer.render;

import net.adiont.alacer.Alacer;
import net.minecraft.client.render.Culler;
import net.minecraft.util.math.Box;

/** This boundary contains no Vector API types, so a module-optional launcher can use the culler. */
public final class VectorFrustumAccess {
	public interface Predicate {
		boolean prepare(Culler culler);
		boolean isVisible(Box box);
		default boolean isChunkVisible(int ox, int oy, int oz) { return false; }
		boolean verify(Box box, Culler culler);
		String verificationSummary();
	}
	private static java.util.function.Supplier<String> summary = () -> "Vector frustum not requested or unavailable";
	private VectorFrustumAccess() { }
	public static Predicate create() {
		if (ModuleLayer.boot().findModule("jdk.incubator.vector").isEmpty()) return null;
		try {
			Predicate predicate = (Predicate) Class.forName("net.adiont.alacer.render.VectorFrustum").getConstructor().newInstance();
			summary = predicate::verificationSummary;
			return predicate;
		} catch (ReflectiveOperationException | LinkageError e) {
			Alacer.LOGGER.warn("Vector frustum unavailable; retaining the scalar/culler path", e);
			return null;
		}
	}
	public static String summary() { return summary.get(); }
}
