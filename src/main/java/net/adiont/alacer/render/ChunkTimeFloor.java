package net.adiont.alacer.render;

/**
 * {@code render.chunkTimeFloor}: a minimum for the time {@code compileChunksUntil} may spend uploading built chunks and
 * queueing rebuilds. Vanilla allows a quarter of a 60 FPS frame minus the time the frame has already used, so below
 * about 60 FPS its deadline has usually passed before chunk work starts; it then uploads one chunk layer and queues one
 * rebuild per frame, and visible updates back up behind the shared buffers. The floor is 1/32 of the previous frame's
 * duration (at most vanilla's own largest budget), so its cost stays a small, fixed share of the frame at any frame
 * rate, and above it vanilla's deadline is used unchanged. Client thread only; pure apart from the clock passed in.
 */
public final class ChunkTimeFloor {
	public static final int DIVISOR = 32;
	/** Vanilla's largest budget: a quarter of a 60 FPS frame. */
	public static final long MAX_NANOS = 1_000_000_000L / 60 / 4;
	/** Longer gaps (loading screens, pauses) are not frames and are ignored. */
	public static final long MAX_FRAME_NANOS = 250_000_000L;
	private long previous;

	/** The deadline to use, given vanilla's and the current time; called once per frame. */
	public long deadline(long vanilla, long now) {
		long frame = this.previous == 0L ? 0L : now - this.previous;
		this.previous = now;
		long floor = frame <= 0L || frame > MAX_FRAME_NANOS ? 0L : Math.min(MAX_NANOS, frame / DIVISOR);
		return floor == 0L || vanilla - now >= floor ? vanilla : now + floor;
	}

	/** Forget the previous frame (option switched on again, world replaced). */
	public void reset() {
		this.previous = 0L;
	}
}
