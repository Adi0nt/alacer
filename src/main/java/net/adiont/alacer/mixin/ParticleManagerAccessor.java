package net.adiont.alacer.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.ParticleManager;
import net.minecraft.client.entity.particle.Particle;

@Mixin(ParticleManager.class)
public interface ParticleManagerAccessor {

	/** The particle lists by atlas and layer; read by the removal diagnostic's probes only. */
	@Accessor("particles")
	List<Particle>[][] alacer$particles();
}
