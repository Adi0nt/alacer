package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.render.CameraEffects;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;

/**
 * Camera settings. Every value is vanilla's own number passed on unchanged at its default. Targets (checked against
 * the mapped 1.8.9 bytecode): applyViewBobbing makes one translatef and three rotatef calls, each linear in the bob
 * and tilt; setupCamera's only scalef is the nausea/portal stretch; transformCamera reads the two camera distance
 * fields (constant 4.0) once and twice and ray-traces eight times for collisions; tickFov's only 0.5 is its
 * approach rate; getFov reads the configured FOV only for the world projection, never for the hand.
 */
@Mixin(GameRenderer.class)
public class CameraControlsMixin {
	@ModifyArg(method = "applyViewBobbing", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;translatef(FFF)V"), index = 0)
	private float alacer$bobX(float vanilla) { return CameraEffects.bobbing(vanilla, Alacer.get().config().viewBobbing); }

	@ModifyArg(method = "applyViewBobbing", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;translatef(FFF)V"), index = 1)
	private float alacer$bobY(float vanilla) { return CameraEffects.bobbing(vanilla, Alacer.get().config().viewBobbing); }

	@ModifyArg(method = "applyViewBobbing", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;rotatef(FFFF)V"), index = 0)
	private float alacer$bobAngle(float vanilla) { return CameraEffects.bobbing(vanilla, Alacer.get().config().viewBobbing); }

	@ModifyArg(method = "setupCamera", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;scalef(FFF)V"), index = 0)
	private float alacer$distortion(float stretch) { return CameraEffects.distortion(stretch, Alacer.get().config().distortion); }

	@ModifyExpressionValue(method = "transformCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/GameRenderer;newCamPos:F", opcode = Opcodes.GETFIELD))
	private float alacer$distanceNew(float vanilla) { return CameraEffects.thirdPersonDistance(vanilla, Alacer.get().config().thirdPersonDistance); }

	@ModifyExpressionValue(method = "transformCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/GameRenderer;oldCamPos:F", opcode = Opcodes.GETFIELD))
	private float alacer$distanceOld(float vanilla) { return CameraEffects.thirdPersonDistance(vanilla, Alacer.get().config().thirdPersonDistance); }

	@WrapOperation(method = "transformCamera", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;rayTrace(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/world/HitResult;"))
	private HitResult alacer$collision(ClientWorld world, Vec3d from, Vec3d to, Operation<HitResult> original) {
		return Alacer.get().config().cameraCollision == 0 ? null : original.call(world, from, to);
	}

	@ModifyConstant(method = "tickFov", constant = @Constant(floatValue = 0.5F))
	private float alacer$fovTransition(float vanilla) { return CameraEffects.fovTransition(vanilla, Alacer.get().config().fovTransition); }

	@ModifyExpressionValue(method = "getFov", at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;fov:F", opcode = Opcodes.GETFIELD))
	private float alacer$zoom(float fov) { return CameraEffects.zoom(fov, Alacer.get().zooming(), Alacer.get().config().zoom); }
}
