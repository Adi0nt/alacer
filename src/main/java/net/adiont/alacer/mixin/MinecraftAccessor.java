package net.adiont.alacer.mixin;

import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.Minecraft;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {

	/** Vanilla's logger, so rate-limited GL error reports appear exactly where vanilla's do. */
	@Accessor("LOGGER")
	static Logger alacer$getLogger() {
		throw new AssertionError();
	}

	/** Vanilla's switch for its GL error checks (always true in vanilla). */
	@Accessor("logGlErrors")
	boolean alacer$logsGlErrors();
}
