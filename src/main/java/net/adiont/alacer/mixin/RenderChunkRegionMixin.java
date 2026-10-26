package net.adiont.alacer.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.adiont.alacer.render.WorkerBlockStateCache;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.world.RenderChunkRegion;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Pool only region arrays whose complete lifetime is inside a chunk compile scope. */
@Mixin(RenderChunkRegion.class)
public class RenderChunkRegionMixin {
	@Shadow private int[] light;
	@Shadow private BlockState[] blocks;
	@Shadow private BlockPos center;
	@Unique private int alacer$centerX;
	@Unique private int alacer$centerY;
	@Unique private int alacer$centerZ;
	@Unique private WorkerBlockStateCache alacer$cache;
	@Unique private boolean alacer$verify;
	@Unique private RenderChunkRegion alacer$reference;

	// Capture the choice after super(), before either allocation. A concurrent live toggle
	// cannot change the constructor's allocation/assignment decision halfway through.
	@Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/world/RenderChunkRegion;center:Lnet/minecraft/util/math/BlockPos;", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
	private void alacer$lease(World world, BlockPos min, BlockPos max, int padding, CallbackInfo ci) {
		if (Alacer.get() == null || WorkerBlockStateCache.constructingReference()) return;
		AlacerConfig config = Alacer.get().config();
		if (config != null && (config.fastWorkerAo || config.verifyWorkerAo)) {
			this.alacer$cache = WorkerBlockStateCache.acquireRegion();
			this.alacer$verify = config.verifyWorkerAo;
		}
	}

	@ModifyConstant(method = "<init>", constant = @Constant(intValue = 8000))
	private int alacer$arraySize(int size) { return this.alacer$cache == null ? size : 0; }

	@Inject(method = "<init>", at = @At("RETURN"))
	private void alacer$install(World world, BlockPos min, BlockPos max, int padding, CallbackInfo ci) {
		this.alacer$centerX = this.center.getX();
		this.alacer$centerY = this.center.getY();
		this.alacer$centerZ = this.center.getZ();
		if (this.alacer$cache == null) return;
		// Vanilla creates two zero-length arrays and fills the empty light array. The
		// large arrays are never allocated; install leased arrays before publication.
		this.light = this.alacer$cache.light;
		this.blocks = this.alacer$cache.blocks;
		if (this.alacer$verify) this.alacer$reference = WorkerBlockStateCache.reference(world, min, max, padding);
	}

	@Inject(method = "index", at = @At("HEAD"), cancellable = true)
	private void alacer$fastIndex(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue((pos.getX() - this.alacer$centerX) * 400 + (pos.getZ() - this.alacer$centerZ) * 20 + (pos.getY() - this.alacer$centerY));
	}

	@ModifyReturnValue(method = "getBlockState", at = @At("RETURN"))
	private BlockState alacer$verifyBlock(BlockState actual, BlockPos pos) {
		if (this.alacer$reference == null) return actual;
		BlockState expected = this.alacer$reference.getBlockState(pos);
		WorkerBlockStateCache.verifyBlockState(pos, actual, expected);
		return expected;
	}

	@ModifyReturnValue(method = "getLightColor", at = @At("RETURN"))
	private int alacer$verifyLight(int actual, BlockPos pos, int blockLight) {
		if (this.alacer$reference == null) return actual;
		int expected = this.alacer$reference.getLightColor(pos, blockLight);
		WorkerBlockStateCache.verifyLight(pos, actual, expected);
		return expected;
	}
}
