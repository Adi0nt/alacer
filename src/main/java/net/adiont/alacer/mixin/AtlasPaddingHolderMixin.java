package net.adiont.alacer.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.adiont.alacer.render.AtlasPadding;
import net.minecraft.client.render.texture.Stitcher;

/** A holder's cell grows by its border on every side; vanilla's alignment, sorting and rotation see the larger cell. */
@Mixin(Stitcher.Holder.class)
public class AtlasPaddingHolderMixin implements AtlasPadding.Bordered {
	@Unique private int alacer$border;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void alacer$init(CallbackInfo ci) { this.alacer$border = AtlasPadding.stitching; }

	@ModifyReturnValue(method = {"getWidth", "getHeight"}, at = @At("RETURN"))
	private int alacer$bordered(int size) { return AtlasPadding.padded(size, this.alacer$border); }

	@Override public int alacer$border() { return this.alacer$border; }
	@Override public void alacer$setBorder(int border) { this.alacer$border = border; }
}
