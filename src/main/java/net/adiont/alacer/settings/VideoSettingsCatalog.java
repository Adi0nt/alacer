package net.adiont.alacer.settings;

import java.util.List;
import net.adiont.alacer.settings.VideoSetting.Category;
import net.adiont.alacer.settings.VideoSetting.Effect;

/** Normal user options only. Intrusive profilers, verification and failed experiments are deliberately excluded; the overlay is not intrusive. */
public final class VideoSettingsCatalog {
	private VideoSettingsCatalog() { }
	private static VideoSetting choice(String key, Category group, String title, String help, int def, Effect effect, String... values) {
		return new VideoSetting(key,group,title,help,List.of(values),0,values.length-1,1,def,effect,false);
	}
	private static VideoSetting slider(String key, Category group, String title, String help, int min, int max, int step, int def, Effect effect) {
		return new VideoSetting(key,group,title,help,List.of(),min,max,step,def,effect,false);
	}
	private static VideoSetting optimization(String key, String title, String help, Effect effect) {
		return choice("render."+key,Category.PERFORMANCE,title,help,1,effect,"Off","On");
	}
	public static final List<VideoSetting> ALL = List.of(
		choice("graphics",Category.GRAPHICS,"Graphics","Base quality for features set to Follow Graphics. Leaves, weather, rain splashes and the vignette can be controlled separately in Details.",1,Effect.REBUILD,"Fast","Fancy"),
		slider("distance",Category.GRAPHICS,"Render distance","How far terrain is drawn. Higher values use more memory and increase terrain build and draw work.",2,16,1,12,Effect.REBUILD),
		slider("quality.entityDistance",Category.GRAPHICS,"Entity distance","How far away mobs, players, dropped items and other entities are drawn, as a percentage of Minecraft's distance for each entity's size. Lower values skip distant entities; entities beyond visible terrain are never drawn.",25,500,25,100,Effect.IMMEDIATE),
		slider("quality.blockEntityDistance",Category.GRAPHICS,"Block entity distance","How far away chests, signs, banners, heads and other block entities are drawn, as a percentage of Minecraft's 64 blocks (256 for beacon beams). Lower values skip distant block entities.",25,500,25,100,Effect.IMMEDIATE),
		choice("lighting",Category.GRAPHICS,"Smooth lighting","Minecraft's smooth-lighting quality. Enable Minimum or Maximum to use Lighting style and AO strength; Off bypasses both controls.",2,Effect.REBUILD,"Off","Minimum","Maximum"),
		choice("quality.lightingStyle",Category.GRAPHICS,"Lighting style","Vanilla preserves the original corner shading. Soft gently lifts ambient-occlusion midtones for less harsh block corners. Requires Smooth lighting. Applying rebuilds terrain.",0,Effect.REBUILD,"Vanilla","Soft"),
		slider("quality.aoStrength",Category.GRAPHICS,"AO strength","Controls ambient-occlusion corner darkness: 100% keeps the selected style's full shading; 0% removes only AO darkening. Sky light, block light and directional face shading remain. Requires Smooth lighting.",0,100,5,100,Effect.REBUILD),
		slider("gamma",Category.GRAPHICS,"Brightness","Adjusts how bright darker areas look without changing the world's lighting.",0,100,5,0,Effect.IMMEDIATE),
		choice("quality.biomeBlend",Category.GRAPHICS,"Biome blend","How many neighboring columns are averaged for grass, foliage and water colors. 3x3 is Minecraft's blending. Off shows hard biome borders and builds chunks fastest; 5x5 and 7x7 look smoother but make chunk building slower. Applying rebuilds terrain.",1,Effect.REBUILD,"Off","3x3","5x5","7x7"),
		choice("clouds",Category.GRAPHICS,"Clouds","Off hides clouds, Fast draws flat clouds, Fancy draws full cloud geometry. Clouds are hidden below 4 chunks of render distance.",2,Effect.IMMEDIATE,"Off","Fast","Fancy"),
		slider("quality.cloudHeight",Category.GRAPHICS,"Cloud height","Moves the cloud layer relative to the dimension's normal height. Works with Fast, Fancy and cached clouds. Zero keeps the original layer; requires Clouds enabled.",-64,128,4,0,Effect.IMMEDIATE),
		choice("quality.sky",Category.GRAPHICS,"Sky","Draws the sky pass, including the sun, moon and stars (and the End sky). Off leaves the background clear color and terrain fog; clouds remain independently controlled.",1,Effect.IMMEDIATE,"Off","On"),
		slider("quality.starBrightness",Category.GRAPHICS,"Star brightness","Makes the existing night-sky stars softer or brighter. 100% is vanilla. Requires Sky and Stars enabled; this does not change daylight or world lighting.",0,200,10,100,Effect.IMMEDIATE),
		slider("quality.weatherOpacity",Category.GRAPHICS,"Weather opacity","Makes rain and snow geometry more subtle without changing rain sounds, splash particles or server weather. Requires Weather enabled. 100% is vanilla.",0,100,5,100,Effect.IMMEDIATE),
		choice("particles",Category.GRAPHICS,"Particles","Controls Minecraft's particle density. Minimal still permits some essential effects.",0,Effect.IMMEDIATE,"All","Decreased","Minimal"),
		choice("quality.particleLimit",Category.GRAPHICS,"Particle limit","Most particles kept in each of Minecraft's eight particle groups; when a group is full its oldest particle is removed. Lower limits cap the cost of explosions, rain and other heavy effects. 4000 is Minecraft's limit.",4,Effect.IMMEDIATE,"250","500","1000","2000","4000"),
		slider("mipmaps",Category.GRAPHICS,"Mipmap levels","Reduces shimmering on distant textures. Applying this setting reloads textures.",0,4,1,4,Effect.RESOURCES),
		choice("quality.anisotropicFiltering",Category.GRAPHICS,"Anisotropic filtering","Keeps block and item textures sharp on surfaces seen at a steep angle, such as distant floors. Most visible with Mipmap levels above 0; higher levels use more GPU texture bandwidth. While on, block textures get edge borders in a larger texture atlas so filtering never picks up neighboring textures. Turning it on or off reloads textures; changing the level is immediate.",0,Effect.RESOURCES,"Off","2x","4x","8x","16x"),
		choice("quality.leaves",Category.DETAILS,"Leaves","Fast uses opaque leaves. Fancy uses cutout leaves. Follow Graphics uses the base Graphics setting. Applying rebuilds terrain.",0,Effect.REBUILD,"Follow Graphics","Fast","Fancy"),
		choice("quality.weather",Category.DETAILS,"Weather","Controls rain and snow drawing distance independently of Graphics. Off hides rain and snow geometry; rain sounds and splash particles remain.",0,Effect.IMMEDIATE,"Follow Graphics","Fast","Fancy","Off"),
		choice("quality.rainSplashes",Category.DETAILS,"Rain splashes","Fast spawns about a quarter as many rain splash and smoke particles as Fancy. Off spawns none. Rain sounds remain; the Particles setting still applies.",0,Effect.IMMEDIATE,"Follow Graphics","Fast","Fancy","Off"),
		choice("quality.vignette",Category.DETAILS,"Vignette","The darkened screen edges that appear when you stand in dark places such as caves. Off skips this full-screen overlay, which also hides the red world-border warning tint, as Fast Graphics does.",0,Effect.IMMEDIATE,"Follow Graphics","Off","On"),
		slider("quality.fogStart",Category.DETAILS,"Distance fog","Where overworld distance fog begins, as a percentage of the render distance; Minecraft uses 75%. Off removes distance fog and reveals the edge of loaded terrain. Water, lava, blindness and Nether fog are unchanged.",10,100,5,75,Effect.IMMEDIATE),
		choice("quality.stars",Category.DETAILS,"Stars","Draws stars in the night sky. Off leaves the sky color, sun, moon and clouds unchanged.",1,Effect.IMMEDIATE,"Off","On"),
		choice("quality.sunMoon",Category.DETAILS,"Sun and moon","Draws the sun and moon. Off leaves the sky color, sunrise colors, stars and clouds unchanged.",1,Effect.IMMEDIATE,"Off","On"),
		choice("quality.enchantGlint",Category.DETAILS,"Enchantment glint","The moving shimmer on enchanted items, potions and armor, including in inventories. Off skips the extra glint passes.",1,Effect.IMMEDIATE,"Off","On"),
		choice("quality.droppedItems",Category.DETAILS,"Dropped items","Fancy draws up to five copies of a dropped stack, depending on its size. Fast draws one copy per stack.",1,Effect.IMMEDIATE,"Fast","Fancy"),
		choice("shadows",Category.DETAILS,"Entity shadows","Draws ground shadows beneath entities. Does not change terrain lighting.",1,Effect.IMMEDIATE,"Off","On"),
		choice("bobbing",Category.DETAILS,"View bobbing","Adds the familiar camera movement when walking.",1,Effect.IMMEDIATE,"Off","On"),
		choice("alternatives",Category.DETAILS,"Alternate block models","Allows resource packs to vary a block's appearance. Applying rebuilds terrain.",1,Effect.REBUILD,"Off","On"),
		choice("quality.waterAnimation",Category.DETAILS,"Water animation","Animates still and flowing water textures. Off keeps the current frame and skips its texture updates.",1,Effect.IMMEDIATE,"Off","On"),
		choice("quality.lavaAnimation",Category.DETAILS,"Lava animation","Animates still and flowing lava textures. Off keeps the current frame and skips its texture updates.",1,Effect.IMMEDIATE,"Off","On"),
		choice("quality.fireAnimation",Category.DETAILS,"Fire animation","Animates fire textures. Off keeps the current frame and skips its texture updates.",1,Effect.IMMEDIATE,"Off","On"),
		choice("quality.portalAnimation",Category.DETAILS,"Portal animation","Animates the Nether portal texture. Off keeps the current frame and skips its texture updates.",1,Effect.IMMEDIATE,"Off","On"),
		choice("quality.otherAnimations",Category.DETAILS,"Other animations","Animates other textures, such as sea lanterns, prismarine, command blocks and resource-pack animations. Clocks and compasses always update.",1,Effect.IMMEDIATE,"Off","On"),
		choice("fullscreen",Category.DISPLAY,"Fullscreen","Switches between a window and fullscreen. Escape can return to this screen after applying.",0,Effect.DISPLAY,"Off","On"),
		choice("vsync",Category.DISPLAY,"VSync","Synchronizes presentation with your display to reduce tearing. Can increase input latency.",1,Effect.DISPLAY,"Off","On"),
		slider("fps",Category.DISPLAY,"Frame rate limit","Caps the frame rate to reduce heat and power use. VSync may impose a lower limit.",10,260,10,120,Effect.IMMEDIATE),
		choice("guiScale",Category.DISPLAY,"GUI scale","Changes interface size. The settings layout adapts after Apply.",0,Effect.DISPLAY,"Auto","Small","Normal","Large"),
		slider("fov",Category.DISPLAY,"Field of view","Controls how much of the world is visible. Wider views can make objects look smaller.",30,110,1,70,Effect.IMMEDIATE),
		slider("quality.fovEffects",Category.DISPLAY,"FOV effects","How much sprinting, flying, Speed and Slowness effects and drawing a bow change the field of view. Off keeps the view steady.",0,100,5,100,Effect.IMMEDIATE),
		slider("quality.damageTilt",Category.DISPLAY,"Damage tilt","How far the camera tilts when you take damage. The rotation on death is unchanged.",0,100,5,100,Effect.IMMEDIATE),
		slider("quality.viewBobbing",Category.DISPLAY,"Bobbing strength","How strongly the camera sways and tilts while you walk, when View bobbing (Details) is on. 100% is Minecraft's bobbing; Off keeps the view steady, like turning View bobbing off. Also applies to the held item's sway.",0,100,5,100,Effect.IMMEDIATE),
		slider("quality.distortion",Category.DISPLAY,"Distortion effects","How strongly nausea and standing in a Nether portal stretch and warp the view. Off keeps the view undistorted; the portal's purple screen overlay is unchanged.",0,100,5,100,Effect.IMMEDIATE),
		slider("quality.thirdPersonDistance",Category.DISPLAY,"Third-person distance","How far from you the camera sits in the two third-person views (F5); Minecraft uses 4 blocks.",1,16,1,4,Effect.IMMEDIATE),
		choice("quality.cameraCollision",Category.DISPLAY,"Third-person collision","On moves the third-person camera in front of blocks between it and you, as Minecraft does. Off lets it pass into walls, which can show what is behind them; some servers' rules forbid that.",1,Effect.IMMEDIATE,"Off","On"),
		choice("quality.fovTransition",Category.DISPLAY,"FOV transitions","How quickly the field of view follows sprinting, flying, Speed and Slowness effects and bows. Smooth and Very smooth ease each change over more time; Instant completes it within one game tick.",1,Effect.IMMEDIATE,"Instant","Vanilla","Smooth","Very smooth"),
		choice("quality.zoom",Category.DISPLAY,"Zoom","Magnification while the Zoom key is held. The key is unbound by default: assign it in Controls (Alacer). Zooming narrows the field of view only; mouse sensitivity is unchanged.",2,Effect.IMMEDIATE,"2x","3x","4x","6x","8x"),
		choice("anaglyph",Category.DISPLAY,"3D anaglyph","Stereo rendering for red/cyan glasses. Applying reloads resources.",0,Effect.RESOURCES,"Off","On"),
		choice("vbo",Category.PERFORMANCE,"Terrain VBOs","Uses Minecraft's vertex-buffer terrain backend when supported. Performance depends on the driver; this is a user choice, not an automatic recommendation.",0,Effect.REBUILD,"Off","On"),
		optimization("fastChunkVisibility","Chunk visibility","Reuses bounded storage when finding visible terrain, preserving the original traversal order.",Effect.IMMEDIATE),
		optimization("fastEntityChunkLookup","Entity lookup cache","Avoids repeated world-chunk lookups while gathering entities. Preserves the rendered result.",Effect.IMMEDIATE),
		optimization("throttleGlErrorChecks","GL error check throttling","Checks for graphics errors periodically. Falls back to checking every frame if an error is detected.",Effect.IMMEDIATE),
		optimization("avoidMatrixReads","Camera matrix tracking","Uses tracked camera matrices to avoid repeated driver state reads.",Effect.IMMEDIATE),
		optimization("cacheClouds","Cloud geometry cache","Reuses unchanged Fancy cloud geometry while preserving Minecraft's cloud appearance.",Effect.IMMEDIATE),
		optimization("fastEntityShadows","Shadow lookup cache","Reuses chunk references for entity shadow queries, preserving shadow geometry.",Effect.IMMEDIATE),
		optimization("fastEntityLighting","Entity lighting cache","Avoids repeated BlockPos allocations and chunk lookups for entity lighting.",Effect.IMMEDIATE),
		optimization("fastEntityRendererLookup","Entity renderer cache","Avoids repeated hash table lookups during entity rendering via a direct reference cache.",Effect.IMMEDIATE),
		optimization("fastEntityCulling","Fast entity culling","Evaluates distance and frustum before entity state preparation and renderer invocation.",Effect.IMMEDIATE),
		optimization("fastRayTrace","Crosshair raycast","Traces targeting and entity rays without intermediate vector and position allocations.",Effect.IMMEDIATE),
		optimization("fastRandomDisplayTicks","Random display ticks","Avoids allocations and redundant block queries during client ambient particle ticks.",Effect.IMMEDIATE),
		optimization("fastSoundThread","Sound command thread","Avoids thousands of InterruptedExceptions in the sound engine using lock-free parking.",Effect.IMMEDIATE),
		optimization("cameraHysteresis","Camera delta caching","Reuses visible chunk list when translation and rotation jitter are sub-threshold and no chunks are dirty.",Effect.IMMEDIATE),
		optimization("fastParticleLight","Particle lighting cache","Avoids repeated chunk lookups for particle lighting, preserving the light values.",Effect.IMMEDIATE),
		optimization("fastParticleRemoval","Particle cleanup","Removes expired particles in a single ordered pass.",Effect.IMMEDIATE),
		optimization("fastParticleVertices","Particle vertex writing","Writes the original particle geometry more directly. Hooks are selected at startup; changes take effect after restarting Minecraft.",Effect.RESTART),
		choice("advanced.chunkUpdateBudget",Category.ADVANCED,"Chunk update budget","Client-thread time per frame for uploading built chunks and queueing rebuilds, relative to Minecraft's quarter-frame budget. Higher shows terrain changes sooner but can cause frame-time spikes; lower keeps frames smoother while terrain fills in more slowly.",2,Effect.IMMEDIATE,"25%","50%","Vanilla","200%","400%"),
		slider("advanced.chunkThreads",Category.ADVANCED,"Chunk builder threads","Background threads that build chunk geometry; Minecraft uses 2. More threads can fill in terrain faster on CPUs with spare cores, but each uses about 10 MB of memory and competes with the game for CPU time. Takes effect after restarting Minecraft.",1,6,1,2,Effect.RESTART).asExperimental(),
		choice("profiler.hud",Category.ADVANCED,"Performance overlay","Shows Alacer's overlay: FPS, frame-time percentiles, CPU breakdown, memory and GC. F7 also toggles it. Measurement runs either way while the profiler is enabled.",0,Effect.IMMEDIATE,"Off","On")
	);
	public static VideoSetting byKey(String key) {
		return ALL.stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow(() -> new IllegalArgumentException(key));
	}
}
