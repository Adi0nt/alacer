package net.adiont.alacer.render;

/** Groups animated atlas sprites by name. A frozen sprite keeps its current frame; clocks and compasses always update. */
public final class TextureAnimations {
	public static final int WATER = 0, LAVA = 1, FIRE = 2, PORTAL = 3, OTHER = 4;
	private TextureAnimations() { }

	public static int group(String spriteName) {
		return switch (spriteName) {
			case "minecraft:blocks/water_still", "minecraft:blocks/water_flow" -> WATER;
			case "minecraft:blocks/lava_still", "minecraft:blocks/lava_flow" -> LAVA;
			case "minecraft:blocks/fire_layer_0", "minecraft:blocks/fire_layer_1" -> FIRE;
			case "minecraft:blocks/portal" -> PORTAL;
			default -> OTHER;
		};
	}

	/** Settings are 0 Off, 1 On, indexed by group. */
	public static boolean animate(String spriteName, int water, int lava, int fire, int portal, int other) {
		if ((water & lava & fire & portal & other) == 1) { return true; }
		return switch (group(spriteName)) {
			case WATER -> water != 0;
			case LAVA -> lava != 0;
			case FIRE -> fire != 0;
			case PORTAL -> portal != 0;
			default -> other != 0;
		};
	}
}
