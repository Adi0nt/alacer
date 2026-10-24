package net.adiont.alacer.mixin;

import java.util.List;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.render.ParticleLimit;
import net.minecraft.client.ParticleManager;

/**
 * Vanilla removes a full list's oldest particle before adding. Under steady spawning the oldest particles are evicted
 * before they expire, so a list above a newly lowered limit would never shrink; its oldest excess is removed at once.
 * At the vanilla limit a list never exceeds 4000, so vanilla's single removal is all that runs.
 */
@Mixin(ParticleManager.class)
public class ParticleLimitMixin {
	@ModifyConstant(method = "add", constant = @Constant(intValue = 4000))
	private int alacer$limit(int vanilla) {
		return ParticleLimit.limit(Alacer.get().config().particleLimit);
	}
	@WrapOperation(method = "add", at = @At(value = "INVOKE", target = "Ljava/util/List;remove(I)Ljava/lang/Object;"))
	private Object alacer$trim(List<?> list, int index, Operation<Object> original) {
		int excess = ParticleLimit.excess(list.size(), ParticleLimit.limit(Alacer.get().config().particleLimit));
		if (excess <= 1) { return original.call(list, index); }
		Object oldest = list.get(0);
		list.subList(0, excess).clear();
		return oldest;
	}
}
