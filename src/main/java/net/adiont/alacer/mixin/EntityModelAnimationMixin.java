package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.adiont.alacer.render.EntityDrawDetails;
import net.adiont.alacer.render.EntityDrawDetails.Stage;
import net.minecraft.client.render.model.entity.QuadrupedModel;
import net.minecraft.client.render.model.entity.WolfModel;

/** Times the animation setup repeated inside the benchmark mobs' model draws, including layers. */
@Mixin({QuadrupedModel.class, WolfModel.class})
public class EntityModelAnimationMixin {

	@Inject(method = "render", at = {@At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/entity/QuadrupedModel;setupAnimation(FFFFFFLnet/minecraft/entity/Entity;)V"), @At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/entity/WolfModel;setupAnimation(FFFFFFLnet/minecraft/entity/Entity;)V")})
	private void alacer$animation0(CallbackInfo ci) {
		EntityDrawDetails.begin(Stage.ANIMATION);
	}

	@Inject(method = "render", at = {@At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/entity/QuadrupedModel;setupAnimation(FFFFFFLnet/minecraft/entity/Entity;)V", shift = At.Shift.AFTER), @At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/entity/WolfModel;setupAnimation(FFFFFFLnet/minecraft/entity/Entity;)V", shift = At.Shift.AFTER)})
	private void alacer$animation1(CallbackInfo ci) {
		EntityDrawDetails.end(Stage.ANIMATION);
	}
}
