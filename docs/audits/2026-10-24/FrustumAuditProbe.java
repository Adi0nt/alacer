package net.adiont.alacer.render;

import net.minecraft.client.render.FrustumData;

/** Audit reproducer, not a passing correctness gate or a performance benchmark. */
public final class FrustumAuditProbe {
    public static void main(String[] args) {
        FrustumData vanilla = new FrustumData();
        for (float[] plane : vanilla.frustum) plane[3] = 1;
        vanilla.frustum[0] = new float[] {1, 1, 0x1p-55f, 0};
        VectorFrustum candidate = new VectorFrustum();
        candidate.load(vanilla.frustum, 0, 0, 0);
        boolean expected = vanilla.contains(1, -1, 1, 1, -1, 1);
        int actual = candidate.test(1, -1, 1, 1, -1, 1);
        System.out.println("java=" + System.getProperty("java.runtime.version"));
        System.out.println("vanilla=" + expected + ", vector=" + actual);
        if (!expected || actual != VectorFrustum.OUTSIDE) {
            throw new AssertionError("Audit finding changed: re-evaluate the FMA reproduction");
        }
        System.out.println("REPRODUCED: vanilla sees the boundary point; nested FMA culls it");
    }
}
