package net.adiont.alacer.render;

import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.adiont.alacer.Alacer;
import net.adiont.alacer.config.AlacerConfig;
import net.adiont.alacer.mixin.MinecraftAccessor;
import net.adiont.alacer.profiler.FrameProfiler.Section;
import net.minecraft.client.Minecraft;

/**
 * Vanilla's two per-frame GL error checks ({@code logGlError("Pre render")} and
 * {@code logGlError("Post render")} in {@code runGame}), optionally rate-limited.
 *
 * <p>{@code glGetError} makes the client thread wait until the driver has processed every
 * command issued so far. When the driver runs its own submission thread, that wait costs a
 * large share of the frame. With {@code render.throttleGlErrorChecks} the two checks run only in
 * one frame per second. GL error flags stay set until they are queried, so an error raised in a
 * skipped frame is still reported by the next check, in vanilla's log format with a note that it
 * may come from any frame since the previous check. The startup checks are not affected.
 *
 * <p>Two safeguards keep a pending error from changing behavior:
 * <ul>
 * <li>LWJGL itself calls {@code glGetError} when the swap interval or display mode changes
 * (vsync option, fullscreen toggle) and throws if an error is pending, which vanilla's
 * every-frame checks normally prevent. Those call sites run {@link #beforeDisplayChange()}
 * first, which reports pending errors the usual way.</li>
 * <li>After the first real error is reported, checks run every frame again for the rest of the
 * session, exactly as in vanilla. Setups that produce GL errors keep vanilla's behavior and
 * log output; error-free setups, the normal case, keep the saving.</li>
 * </ul>
 *
 * <p>Verification ({@code render.throttleGlErrorChecks.verify}) deliberately raises a harmless
 * {@code GL_INVALID_ENUM} in a skipped frame and confirms that the next check reports it. The
 * injected error does not trigger the fallback.
 *
 * <p>Everything here runs on the client thread.
 */
public final class GlErrorChecks {

	private static final long INTERVAL_NANOS = 1_000_000_000L;
	/** Upper bound on error flags read per check; implementations may hold one per error type. */
	private static final int MAX_ERRORS_PER_CHECK = 8;
	/** Not a valid {@code glEnable} capability, so it only sets {@code GL_INVALID_ENUM}. */
	private static final int INVALID_CAPABILITY = 0x7FFF;

	private static long nextCheck;
	private static long frame;
	private static boolean checkThisFrame;
	/** A check was skipped since errors were last read, so one may be pending. */
	private static boolean skipped;
	/** A real error was reported: check every frame from now on. */
	private static boolean errorSeen;

	private static boolean injectedPending;
	private static long injectedAt;
	private static long injected;
	private static long reported;
	private static long missed;

	private GlErrorChecks() {
	}

	/** Start of a {@code runGame()} iteration: decides whether this frame's checks run. */
	public static void onFrameStart() {
		long now = System.nanoTime();
		frame++;
		checkThisFrame = now - nextCheck >= 0;
		if (checkThisFrame) {
			nextCheck = now + INTERVAL_NANOS;
		}
	}

	/**
	 * True in the frame, about once per second, where the rate-limited checks read OpenGL.
	 * Decided at frame start whether or not rate limiting is on, so other once-per-second
	 * comparisons with OpenGL can share the frame that waits for the driver anyway.
	 */
	public static boolean checksThisFrame() {
		return checkThisFrame;
	}

	/** Number of the current {@code runGame()} iteration, for once-per-frame bookkeeping. */
	public static long frameNumber() {
		return frame;
	}

	/** Replaces one per-frame {@code logGlError(location)} call in {@code runGame}. */
	public static void check(Minecraft minecraft, String location, Operation<Void> vanilla) {
		Alacer alacer = Alacer.get();
		AlacerConfig config = alacer.config();
		alacer.begin(Section.GL_ERROR_CHECK);
		if (!config.throttleGlErrorChecks || errorSeen) {
			// Vanilla reads (and reports) anything left over from a skipped frame.
			skipped = false;
			injectedPending = false;
			vanilla.call(minecraft, location);
		} else if (checkThisFrame) {
			drain(minecraft, location);
		} else {
			skipped = true;
			if (config.verifyGlErrorChecks && !injectedPending && ((MinecraftAccessor) minecraft).alacer$logsGlErrors()) {
				GL11.glEnable(INVALID_CAPABILITY);
				injectedPending = true;
				injectedAt = System.nanoTime();
				injected++;
			}
		}
		alacer.end(Section.GL_ERROR_CHECK);
	}

	/**
	 * Called right before vanilla changes the swap interval or display mode at runtime, where
	 * LWJGL throws if a GL error is pending. Reports pending errors if a check was skipped.
	 */
	public static void beforeDisplayChange() {
		if (skipped) {
			drain(Minecraft.getInstance(), "Before display change");
		}
	}

	private static void drain(Minecraft minecraft, String location) {
		skipped = false;
		if (!((MinecraftAccessor) minecraft).alacer$logsGlErrors()) {
			return;
		}
		boolean sawInjected = false;
		for (int i = 0; i < MAX_ERRORS_PER_CHECK; i++) {
			int error = GL11.glGetError();
			if (error == GL11.GL_NO_ERROR) {
				break;
			}
			// Same three lines as vanilla, so log scanners keep working.
			Logger logger = MinecraftAccessor.alacer$getLogger();
			logger.error("########## GL ERROR ##########");
			logger.error("@ " + location + " (checked about once per second by Alacer; the error may come from any frame since the previous check)");
			logger.error(error + ": " + GLU.gluErrorString(error));
			if (injectedPending && !sawInjected && error == GL11.GL_INVALID_ENUM) {
				sawInjected = true;
			} else if (!errorSeen) {
				errorSeen = true;
				Alacer.LOGGER.info("OpenGL error detected: checking for GL errors every frame again for the rest of the session");
			}
		}
		if (injectedPending) {
			injectedPending = false;
			if (sawInjected) {
				reported++;
				Alacer.LOGGER.info("GL error check verification: injected error reported {} ms later at '{}' ({} injected, {} reported, {} missed)",
					(System.nanoTime() - injectedAt) / 1_000_000, location, injected, reported, missed);
			} else {
				missed++;
				Alacer.LOGGER.warn("GL error check verification: injected error was NOT reported at '{}' ({} injected, {} reported, {} missed)",
					location, injected, reported, missed);
			}
		}
	}
}
