package net.adiont.alacer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import paulscode.sound.SimpleThread;
import net.adiont.alacer.sound.FastSoundThread;

@Mixin(value = SimpleThread.class, remap = false)
public abstract class SoundThreadMixin {

	/**
	 * @author Alacer
	 * @reason Replace Thread.sleep with LockSupport.park to eliminate thousands of InterruptedExceptions during normal sound queueing.
	 */
	@Overwrite
	protected void snooze(long milliseconds) {
		FastSoundThread.snooze((Thread) (Object) this, milliseconds);
	}
}
