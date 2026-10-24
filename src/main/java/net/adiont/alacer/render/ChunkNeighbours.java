package net.adiont.alacer.render;

/** Immutable toroidal storage adjacency. Replaces floor division/remainder on every search edge. */
public final class ChunkNeighbours {
	private final int[] next;

	public ChunkNeighbours(int sx, int sy, int sz, int[] dx, int[] dy, int[] dz) {
		if (sx <= 0 || sy <= 0 || sz <= 0 || dx.length != 6 || dy.length != 6 || dz.length != 6) {
			throw new IllegalArgumentException("Invalid chunk grid");
		}
		int count = Math.multiplyExact(Math.multiplyExact(sx, sy), sz);
		this.next = new int[Math.multiplyExact(count, 6)];
		for (int slot = 0; slot < count; slot++) {
			int x = slot % sx, y = slot / sx % sy, z = slot / (sx * sy);
			for (int d = 0; d < 6; d++) {
				if (dx[d] % 16 != 0 || dy[d] % 16 != 0 || dz[d] % 16 != 0) {
					throw new IllegalArgumentException("Offsets must be section aligned");
				}
				int ny = y + dy[d] / 16;
				this.next[slot * 6 + d] = ny < 0 || ny >= sy ? -1
					: (Math.floorMod(z + dz[d] / 16, sz) * sy + ny) * sx + Math.floorMod(x + dx[d] / 16, sx);
			}
		}
	}

	public int next(int slot, int direction) { return this.next[slot * 6 + direction]; }
}
