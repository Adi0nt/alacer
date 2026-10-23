package net.adiont.alacer.render;

/** Maximum particles kept in each of the particle manager's eight lists; vanilla removes the oldest at 4000. */
public final class ParticleLimit {
	public static final int VANILLA_CHOICE = 4;
	private static final int[] LIMITS = {250, 500, 1000, 2000, 4000};
	private ParticleLimit() { }

	public static int limit(int choice) { return LIMITS[Math.max(0, Math.min(LIMITS.length - 1, choice))]; }

	/** Oldest particles to remove so a full list has room for one more within the limit; vanilla removes one. */
	public static int excess(int size, int limit) { return Math.max(1, size - limit + 1); }
}
