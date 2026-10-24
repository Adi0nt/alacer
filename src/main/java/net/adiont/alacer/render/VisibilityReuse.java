package net.adiont.alacer.render;

/** Exact search-input identity. Small motion is insufficient to preserve a strict culling boundary. */
final class VisibilityReuse {
	private final double[] saved = new double[64];
	private Object camera, storage;
	private boolean valid;
	boolean matches(Object camera, Object storage, double[] input) {
		if (!this.valid || this.camera != camera || this.storage != storage) return false;
		for (int i = 0; i < this.saved.length; i++) {
			if (!Double.isFinite(input[i]) || Double.doubleToRawLongBits(input[i]) != Double.doubleToRawLongBits(this.saved[i])) return false;
		}
		return true;
	}
	void remember(Object camera, Object storage, double[] input) {
		System.arraycopy(input, 0, this.saved, 0, this.saved.length);
		this.camera = camera;
		this.storage = storage;
		this.valid = true;
	}
	void invalidate() { this.valid = false; this.camera = null; this.storage = null; }
}
