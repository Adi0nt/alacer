package net.adiont.alacer.render;

import net.minecraft.client.render.FrustumData;

/** Regression/fallback probe for the repaired implementation, not a performance benchmark. */
public final class RecoveryProbe {
	public static void main(String[] args) {
		if (args.length != 0 && args[0].equals("no-module")) {
			if (ModuleLayer.boot().findModule("jdk.incubator.vector").isPresent()) throw new AssertionError("module is resolved");
			if (VectorFrustumAccess.create() != null) throw new AssertionError("expected culler fallback");
			System.out.println("PASS: unresolved Vector module selects fallback without linking vector classes");
			return;
		}
		FrustumData vanilla = new FrustumData();
		for (float[] plane : vanilla.frustum) plane[3] = 1;
		vanilla.frustum[0] = new float[] {1, 1, 0x1p-55f, 0};
		VectorFrustum fast = new VectorFrustum();
		fast.load(vanilla.frustum, 0, 0, 0);
		if (!vanilla.contains(1, -1, 1, 1, -1, 1) || fast.test(1, -1, 1, 1, -1, 1) != VectorFrustum.VISIBLE)
			throw new AssertionError("cancellation-boundary mismatch");
		System.out.println("PASS: vanilla and vector accept the cancellation boundary");
	}
}
