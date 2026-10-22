package net.adiont.alacer.settings;

import java.util.List;
import net.adiont.alacer.settings.VideoSetting.Category;
import net.adiont.alacer.settings.VideoSetting.Effect;

/** Normal user options only. Intrusive profilers, verification and failed experiments are deliberately excluded. */
public final class VideoSettingsCatalog {
	private VideoSettingsCatalog() { }
	private static VideoSetting choice(String key, Category group, String title, String help, int def, Effect effect, String... values) {
		return new VideoSetting(key,group,title,help,List.of(values),0,values.length-1,1,def,effect);
	}
	private static VideoSetting slider(String key, Category group, String title, String help, int min, int max, int step, int def, Effect effect) {
		return new VideoSetting(key,group,title,help,List.of(),min,max,step,def,effect);
	}
	private static VideoSetting optimization(String key, String title, String help, Effect effect) {
		return choice("render."+key,Category.PERFORMANCE,title,help,1,effect,"Off","On");
	}
	public static final List<VideoSetting> ALL = List.of(
		choice("graphics",Category.GRAPHICS,"Graphics","Base quality for features set to Follow Graphics. Leaves and weather can be controlled separately in Details.",1,Effect.REBUILD,"Fast","Fancy"),
		slider("distance",Category.GRAPHICS,"Render distance","How far terrain is drawn. Higher values use more memory and increase terrain build and draw work.",2,16,1,12,Effect.REBUILD),
		choice("lighting",Category.GRAPHICS,"Smooth lighting","Minecraft's smooth-lighting quality. Enable Minimum or Maximum to use Lighting style and AO strength; Off bypasses both controls.",2,Effect.REBUILD,"Off","Minimum","Maximum"),
		choice("quality.lightingStyle",Category.GRAPHICS,"Lighting style","Vanilla preserves the original corner shading. Soft gently lifts ambient-occlusion midtones for less harsh block corners. Requires Smooth lighting. Applying rebuilds terrain.",0,Effect.REBUILD,"Vanilla","Soft"),
		slider("quality.aoStrength",Category.GRAPHICS,"AO strength","Controls ambient-occlusion corner darkness: 100% keeps the selected style's full shading; 0% removes only AO darkening. Sky light, block light and directional face shading remain. Requires Smooth lighting.",0,100,5,100,Effect.REBUILD),
		slider("gamma",Category.GRAPHICS,"Brightness","Adjusts how bright darker areas look without changing the world's lighting.",0,100,5,0,Effect.IMMEDIATE),
		choice("clouds",Category.GRAPHICS,"Clouds","Off hides clouds, Fast draws flat clouds, Fancy draws full cloud geometry. Clouds are hidden below 4 chunks of render distance.",2,Effect.IMMEDIATE,"Off","Fast","Fancy"),
		choice("particles",Category.GRAPHICS,"Particles","Controls Minecraft's particle density. Minimal still permits some essential effects.",0,Effect.IMMEDIATE,"All","Decreased","Minimal"),
		slider("mipmaps",Category.GRAPHICS,"Mipmap levels","Reduces shimmering on distant textures. Applying this setting reloads textures.",0,4,1,4,Effect.RESOURCES),
		choice("quality.leaves",Category.DETAILS,"Leaves","Fast uses opaque leaves. Fancy uses cutout leaves. Follow Graphics uses the base Graphics setting. Applying rebuilds terrain.",0,Effect.REBUILD,"Follow Graphics","Fast","Fancy"),
		choice("quality.weather",Category.DETAILS,"Weather","Controls rain and snow drawing distance independently of Graphics. Off hides rain and snow geometry; rain sounds and splash particles remain.",0,Effect.IMMEDIATE,"Follow Graphics","Fast","Fancy","Off"),
		choice("shadows",Category.DETAILS,"Entity shadows","Draws ground shadows beneath entities. Does not change terrain lighting.",1,Effect.IMMEDIATE,"Off","On"),
		choice("bobbing",Category.DETAILS,"View bobbing","Adds the familiar camera movement when walking.",1,Effect.IMMEDIATE,"Off","On"),
		choice("alternatives",Category.DETAILS,"Alternate block models","Allows resource packs to vary a block's appearance. Applying rebuilds terrain.",1,Effect.REBUILD,"Off","On"),
		choice("fullscreen",Category.DISPLAY,"Fullscreen","Switches between a window and fullscreen. Escape can return to this screen after applying.",0,Effect.DISPLAY,"Off","On"),
		choice("vsync",Category.DISPLAY,"VSync","Synchronizes presentation with your display to reduce tearing. Can increase input latency.",1,Effect.DISPLAY,"Off","On"),
		slider("fps",Category.DISPLAY,"Frame rate limit","Caps the frame rate to reduce heat and power use. VSync may impose a lower limit.",10,260,10,120,Effect.IMMEDIATE),
		choice("guiScale",Category.DISPLAY,"GUI scale","Changes interface size. The settings layout adapts after Apply.",0,Effect.DISPLAY,"Auto","Small","Normal","Large"),
		slider("fov",Category.DISPLAY,"Field of view","Controls how much of the world is visible. Wider views can make objects look smaller.",30,110,1,70,Effect.IMMEDIATE),
		choice("anaglyph",Category.DISPLAY,"3D anaglyph","Stereo rendering for red/cyan glasses. Applying reloads resources.",0,Effect.RESOURCES,"Off","On"),
		choice("vbo",Category.PERFORMANCE,"Terrain VBOs","Uses Minecraft's vertex-buffer terrain backend when supported. Performance depends on the driver; this is a user choice, not an automatic recommendation.",0,Effect.REBUILD,"Off","On"),
		optimization("fastChunkVisibility","Chunk visibility","Reuses bounded storage when finding visible terrain, preserving the original traversal order.",Effect.IMMEDIATE),
		optimization("fastEntityChunkLookup","Entity lookup cache","Avoids repeated world-chunk lookups while gathering entities. Preserves the rendered result.",Effect.IMMEDIATE),
		optimization("throttleGlErrorChecks","GL error check throttling","Checks for graphics errors periodically. Falls back to checking every frame if an error is detected.",Effect.IMMEDIATE),
		optimization("avoidMatrixReads","Camera matrix tracking","Uses tracked camera matrices to avoid repeated driver state reads.",Effect.IMMEDIATE),
		optimization("cacheClouds","Cloud geometry cache","Reuses unchanged Fancy cloud geometry while preserving Minecraft's cloud appearance.",Effect.IMMEDIATE),
		optimization("fastEntityShadows","Shadow lookup cache","Reuses chunk references for entity shadow queries, preserving shadow geometry.",Effect.IMMEDIATE),
		optimization("fastParticleLight","Particle lighting cache","Avoids repeated chunk lookups for particle lighting, preserving the light values.",Effect.IMMEDIATE),
		optimization("fastParticleRemoval","Particle cleanup","Removes expired particles in a single ordered pass.",Effect.IMMEDIATE),
		optimization("fastParticleVertices","Particle vertex writing","Writes the original particle geometry more directly. Hooks are selected at startup; changes take effect after restarting Minecraft.",Effect.RESTART)
	);
	public static VideoSetting byKey(String key) {
		return ALL.stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow(() -> new IllegalArgumentException(key));
	}
}
