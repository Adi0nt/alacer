import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;
import net.adiont.alacer.profiler.JsonWriter;

/**
 * Session 26: whole-frame stack keys. Generalizes session 25's JfrMovementRegions (preserved unchanged) from one
 * movement scope to every client-thread sample of a frame. analyze.py maps the keys to regions with the runtime jar's
 * own line tables (line_table.py); this helper only records what the stacks contain.
 *
 * Inputs: recording, output JSON, a frames file written by stream_frames.py (one CSV frame per line:
 * phase, start and end epoch ns, flags 1 = ticking frame, 2 = frame time at or above the phase p95, 4 = at or above
 * p99), and the profiler's wall-epoch uncertainty in ns. Client-thread ("main") jdk.ExecutionSample,
 * jdk.NativeMethodSample and jdk.ObjectAllocationSample events are assigned to the frame containing their timestamp.
 *
 * Key: the anchor chain from the root-most exact Minecraft.runGame frame towards the leaf. An anchor is an EXACT frame
 * (class, method and JVM descriptor; '*' accepts any overload of that exact class and method), written id:line where
 * line is that frame's line (a call site unless the anchor is the leaf). Consecutive frames of one anchor collapse to
 * the root-most. Package scopes (Alacer profiler, benchmark driver, render code and "$alacer$" mixin methods) are
 * recorded as pkg:<scope> markers, collapsed the same way. Then the frame directly below the deepest chain element
 * (or <self>) and the leaf. Samples without a runGame frame are keyed OUTSIDE (or TRUNCATED for truncated stacks).
 * Prefix scope is reported separately per anchor: samples with a frame whose class.method starts with the anchor's
 * class.method (for example ParticleManager.render also matches renderLit), next to the exact count.
 *
 * Frame offset: the profiler epoch is known to within the reported uncertainty. Over offsets in [-u, +u] (0.1 ms
 * steps), count the execution samples directly under Minecraft.tick that land in ticking frames; take the plateau of
 * the best count that contains the smallest-magnitude best offset and apply its midpoint to every event. Counts at
 * offset 0 and at the chosen offset are reported, with the plateau and whether it reaches the search limit. Phase
 * membership follows the frame found. Samples are counts, not times; allocation weights are sampled estimates.
 */
public final class JfrWholeFrame {

	private static final String RUN_GAME = "net.minecraft.client.Minecraft.runGame()V";
	private static final String[][] ANCHORS = {
		{"mc.runGame", "net.minecraft.client.Minecraft", "runGame", "()V"},
		{"mc.tick", "net.minecraft.client.Minecraft", "tick", "()V"},
		{"mc.updateDisplay", "net.minecraft.client.Minecraft", "updateDisplay", "()V"},
		{"mc.logGlError", "net.minecraft.client.Minecraft", "logGlError", "(Ljava/lang/String;)V"},
		{"util.run", "net.minecraft.util.Utils", "run", "*"},
		{"display.update", "org.lwjgl.opengl.Display", "update", "*"},
		{"display.sync", "org.lwjgl.opengl.Display", "sync", "*"},
		{"gr.render", "net.minecraft.client.render.GameRenderer", "render", "(FJ)V"},
		{"gr.renderWorld", "net.minecraft.client.render.GameRenderer", "renderWorld", "(FJ)V"},
		{"gr.pass", "net.minecraft.client.render.GameRenderer", "render", "(IFJ)V"},
		{"gr.lightmap", "net.minecraft.client.render.GameRenderer", "updateLightMap", "(F)V"},
		{"gr.pick", "net.minecraft.client.render.GameRenderer", "pick", "(F)V"},
		{"gr.setupCamera", "net.minecraft.client.render.GameRenderer", "setupCamera", "(FI)V"},
		{"gr.setupFog", "net.minecraft.client.render.GameRenderer", "setupFog", "(IF)V"},
		{"gr.clouds", "net.minecraft.client.render.GameRenderer", "renderClouds", "(Lnet/minecraft/client/render/world/WorldRenderer;FI)V"},
		{"gr.weather", "net.minecraft.client.render.GameRenderer", "renderSnowAndRain", "(F)V"},
		{"gr.hand", "net.minecraft.client.render.GameRenderer", "renderItemInHand", "(FI)V"},
		{"gr.tick", "net.minecraft.client.render.GameRenderer", "tick", "()V"},
		{"wr.setupRender", "net.minecraft.client.render.world.WorldRenderer", "setupRender", "*"},
		{"wr.compile", "net.minecraft.client.render.world.WorldRenderer", "compileChunksUntil", "(J)V"},
		{"wr.layer", "net.minecraft.client.render.world.WorldRenderer", "render", "(Lnet/minecraft/client/render/block/BlockLayer;DILnet/minecraft/entity/Entity;)I"},
		{"wr.renderLastChunks", "net.minecraft.client.render.world.WorldRenderer", "renderLastChunks", "*"},
		{"wr.entities", "net.minecraft.client.render.world.WorldRenderer", "renderEntities", "*"},
		{"wr.sky", "net.minecraft.client.render.world.WorldRenderer", "renderSky", "(FI)V"},
		{"wr.clouds", "net.minecraft.client.render.world.WorldRenderer", "renderClouds", "(FI)V"},
		{"wr.outline", "net.minecraft.client.render.world.WorldRenderer", "renderBlockOutline", "*"},
		{"wr.mining", "net.minecraft.client.render.world.WorldRenderer", "renderMiningProgress", "*"},
		{"wr.border", "net.minecraft.client.render.world.WorldRenderer", "renderWorldBorder", "*"},
		{"wr.tick", "net.minecraft.client.render.world.WorldRenderer", "tick", "()V"},
		{"crd.runTasks", "net.minecraft.client.render.world.ChunkRenderDispatcher", "runTasksUntil", "*"},
		{"crd.rebuildSync", "net.minecraft.client.render.world.ChunkRenderDispatcher", "rebuildSync", "*"},
		{"crd.upload", "net.minecraft.client.render.world.ChunkRenderDispatcher", "upload", "*"},
		{"pm.render", "net.minecraft.client.ParticleManager", "render", "*"},
		{"pm.renderLit", "net.minecraft.client.ParticleManager", "renderLit", "*"},
		{"pm.tick", "net.minecraft.client.ParticleManager", "tick", "()V"},
		{"pm.tickParticle", "net.minecraft.client.ParticleManager", "tickParticle", "*"},
		{"erd.render", "net.minecraft.client.render.entity.EntityRenderDispatcher", "render", "*"},
		{"erd.shouldRender", "net.minecraft.client.render.entity.EntityRenderDispatcher", "shouldRender", "*"},
		{"erd.nameTag", "net.minecraft.client.render.entity.EntityRenderDispatcher", "renderNameTag", "*"},
		{"berd.render", "net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher", "render", "*"},
		{"gui.render", "net.minecraft.client.gui.GameGui", "render", "(F)V"},
		{"gui.tick", "net.minecraft.client.gui.GameGui", "tick", "()V"},
		{"world.tickEntities", "net.minecraft.world.World", "tickEntities", "()V"},
		{"cw.tick", "net.minecraft.client.world.ClientWorld", "tick", "()V"},
		{"cw.displayTicks", "net.minecraft.client.world.ClientWorld", "doRandomDisplayTicks", "(III)V"},
		{"tm.tick", "net.minecraft.client.render.texture.TextureManager", "tick", "()V"},
		{"cpim.tick", "net.minecraft.client.ClientPlayerInteractionManager", "tick", "()V"},
		{"conn.tick", "net.minecraft.network.Connection", "tick", "()V"},
		{"sound.tick", "net.minecraft.client.sound.system.SoundManager", "tick", "()V"},
		{"sound.listener", "net.minecraft.client.sound.system.SoundManager", "updateListener", "*"},
		{"music.tick", "net.minecraft.client.sound.MusicManager", "tick", "()V"},
	};
	private static final String[] KIND = {"execution", "native", "allocation"};
	private static final String[] CATEGORY = {"all", "tick", "nontick", "slow95", "slow99"};

	private static final class Phase {
		final String name; final List<long[]> frames = new ArrayList<>();
		long lo = Long.MAX_VALUE, hi = Long.MIN_VALUE;
		final long[] frameCount = new long[CATEGORY.length];
		final long[][] samples = new long[KIND.length][CATEGORY.length];
		final long[] allocationWeight = new long[CATEGORY.length];
		long truncated;
		final Map<String, long[]> anchorExact = new TreeMap<>(), anchorPrefix = new TreeMap<>();
		final Map<String, Long> threads = new TreeMap<>();
		@SuppressWarnings("unchecked")
		final Map<String, Long>[][] keys = new Map[KIND.length][CATEGORY.length];
		final Map<String, Long>[] weights = new Map[CATEGORY.length];
		Phase(String name) {
			this.name = name;
			for (int k = 0; k < KIND.length; k++) for (int c = 0; c < CATEGORY.length; c++) keys[k][c] = new HashMap<>();
			for (int c = 0; c < CATEGORY.length; c++) weights[c] = new HashMap<>();
		}
	}

	private record Event(long at, int kind, String key, long weight, boolean tick, boolean truncated,
		boolean[] exact, boolean[] prefix) { }

	private static String id(RecordedMethod m) {
		return m.getType().getName() + "." + m.getName();
	}

	private static int anchor(RecordedMethod m) {
		String type = m.getType().getName(), name = m.getName();
		for (int i = 0; i < ANCHORS.length; i++) {
			String[] a = ANCHORS[i];
			if (a[1].equals(type) && a[2].equals(name) && (a[3].equals("*") || a[3].equals(m.getDescriptor()))) return i;
		}
		return -1;
	}

	private static String scope(RecordedMethod m) {
		String type = m.getType().getName(), name = m.getName();
		if (type.startsWith("net.adiont.alacer.profiler.")) return "pkg:alacer.profiler";
		if (type.startsWith("net.adiont.alacer.benchmark.") || (type.startsWith("net.adiont.alacer.Alacer") && name.startsWith("lambda$")))
			return "pkg:alacer.benchmark";
		if (type.startsWith("net.adiont.alacer.render.")) return "pkg:alacer.render";
		if (name.contains("$alacer$")) return "pkg:alacer.hook";
		return null;
	}

	public static void main(String[] args) throws Exception {
		Path recordingPath = Path.of(args[0]), output = Path.of(args[1]);
		long uncertainty = Long.parseLong(args[3]);
		Map<String, Phase> phases = new java.util.LinkedHashMap<>();
		for (String line : Files.readAllLines(Path.of(args[2]))) {
			if (line.isBlank()) continue;
			String[] p = line.split("\t");
			Phase phase = phases.computeIfAbsent(p[0], Phase::new);
			long lo = Long.parseLong(p[1]), hi = Long.parseLong(p[2]);
			phase.frames.add(new long[] {lo, hi, Long.parseLong(p[3])});
			phase.lo = Math.min(phase.lo, lo); phase.hi = Math.max(phase.hi, hi);
		}
		long globalLo = Long.MAX_VALUE, globalHi = Long.MIN_VALUE;
		for (Phase phase : phases.values()) {
			phase.frames.sort((a, b) -> Long.compare(a[0], b[0]));
			globalLo = Math.min(globalLo, phase.lo); globalHi = Math.max(globalHi, phase.hi);
		}
		long margin = uncertainty + 2_000_000L;
		List<Event> events = new ArrayList<>();
		Map<String, Map<String, Long>> threadsByPhaseRaw = new HashMap<>();
		List<long[]> otherThreadSamples = new ArrayList<>();
		List<String> otherThreadNames = new ArrayList<>();
		long executionEvents = 0, nativeEvents = 0, allocationEvents = 0;
		try (RecordingFile recording = new RecordingFile(recordingPath)) {
			while (recording.hasMoreEvents()) {
				RecordedEvent e = recording.readEvent();
				String type = e.getEventType().getName();
				int kind = type.equals("jdk.ExecutionSample") ? 0 : type.equals("jdk.NativeMethodSample") ? 1
					: type.equals("jdk.ObjectAllocationSample") ? 2 : -1;
				if (kind < 0) continue;
				if (kind == 0) executionEvents++; else if (kind == 1) nativeEvents++; else allocationEvents++;
				long at = Math.addExact(Math.multiplyExact(e.getStartTime().getEpochSecond(), 1_000_000_000L), e.getStartTime().getNano());
				if (at < globalLo - margin || at >= globalHi + margin) continue;
				RecordedThread thread = e.getThread(kind == 2 ? "eventThread" : "sampledThread");
				String threadName = thread == null ? "<none>" : thread.getJavaName();
				if (!"main".equals(threadName)) {
					if (kind == 0) { otherThreadSamples.add(new long[] {at, otherThreadNames.size()}); otherThreadNames.add(threadName); }
					continue;
				}
				RecordedStackTrace trace = e.getStackTrace();
				List<RecordedFrame> frames = trace == null ? List.of() : trace.getFrames();
				boolean truncated = trace != null && trace.isTruncated();
				boolean[] exact = new boolean[ANCHORS.length], prefix = new boolean[ANCHORS.length];
				int root = -1;
				for (int i = 0; i < frames.size(); i++) {
					RecordedMethod m = frames.get(i).getMethod();
					int a = anchor(m);
					if (a >= 0) exact[a] = true;
					String n = id(m);
					for (int j = 0; j < ANCHORS.length; j++) if (n.startsWith(ANCHORS[j][1] + "." + ANCHORS[j][2])) prefix[j] = true;
					if ((n + m.getDescriptor()).equals(RUN_GAME)) root = i;
				}
				StringBuilder key = new StringBuilder();
				boolean tick = false;
				if (root < 0) {
					key.append(truncated ? "TRUNCATED" : "OUTSIDE").append('\t');
					key.append(frames.isEmpty() ? "<none>" : id(frames.get(frames.size() - 1).getMethod()));
					key.append('\t').append(frames.isEmpty() ? "<none>" : id(frames.get(0).getMethod()));
				} else {
					String last = null; int deepest = root;
					key.append("mc.runGame:").append(frames.get(root).getLineNumber());
					for (int i = root - 1; i >= 0; i--) {
						RecordedMethod m = frames.get(i).getMethod();
						int a = anchor(m);
						String element = a >= 0 ? ANCHORS[a][0] : scope(m);
						if (element == null) continue;
						if (element.equals(last)) { deepest = i; continue; }
						key.append(' ').append(element);
						if (a >= 0) key.append(':').append(frames.get(i).getLineNumber());
						if (a >= 0 && ANCHORS[a][0].equals("mc.tick") && last == null) tick = true;
						last = element; deepest = i;
					}
					key.append('\t').append(deepest == 0 ? "<self>" : id(frames.get(deepest - 1).getMethod()));
					key.append('\t').append(id(frames.get(0).getMethod()));
				}
				long weight = 0;
				if (kind == 2) {
					weight = e.getLong("weight");
					key.append('\t').append(e.getClass("objectClass").getName());
				}
				events.add(new Event(at, kind, key.toString(), weight, tick, truncated, exact, prefix));
			}
		}
		// Offset calibration on execution samples directly under Minecraft.tick.
		long limit = uncertainty / 100_000L * 100_000L;
		int steps = (int) (2 * limit / 100_000L) + 1;
		long[] inTick = new long[steps], inFrames = new long[steps];
		for (int s = 0; s < steps; s++) {
			long offset = -limit + s * 100_000L;
			for (Event ev : events) {
				if (ev.kind != 0 || !ev.tick) continue;
				long[] f = find(phases, ev.at + offset);
				if (f == null) continue;
				inFrames[s]++;
				if ((f[2] & 1) != 0) inTick[s]++;
			}
		}
		// Best count; among equal counts the plateau containing the smallest-magnitude offset, then its midpoint.
		long bestCount = -1; int first = -1;
		for (int s = 0; s < steps; s++) {
			long offset = -limit + s * 100_000L;
			if (inTick[s] > bestCount || (inTick[s] == bestCount && Math.abs(offset) < Math.abs(-limit + first * 100_000L))) {
				bestCount = inTick[s]; first = s;
			}
		}
		int lo = first, hi = first;
		while (lo > 0 && inTick[lo - 1] == bestCount) lo--;
		while (hi < steps - 1 && inTick[hi + 1] == bestCount) hi++;
		int chosen = (lo + hi) / 2;
		long best = -limit + chosen * 100_000L;
		int zero = (int) (limit / 100_000L);
		long zeroCount = inTick[zero], tickSamples = inFrames[chosen], zeroFrames = inFrames[zero];
		boolean atLimit = lo == 0 || hi == steps - 1;
		long unassigned = 0;
		for (Event ev : events) {
			long at = ev.at + best;
			Phase phase = null; long[] frame = null;
			for (Phase p : phases.values()) {
				if (at >= p.lo && at < p.hi) { frame = find(p, at); phase = p; break; }
			}
			if (frame == null) { unassigned++; continue; }
			int flags = (int) frame[2];
			boolean[] in = {true, (flags & 1) != 0, (flags & 1) == 0, (flags & 2) != 0, (flags & 4) != 0};
			for (int c = 0; c < CATEGORY.length; c++) {
				if (!in[c]) continue;
				phase.samples[ev.kind][c]++;
				phase.keys[ev.kind][c].merge(ev.key, 1L, Long::sum);
				if (ev.kind == 2) { phase.allocationWeight[c] += ev.weight; phase.weights[c].merge(ev.key, ev.weight, Long::sum); }
			}
			if (ev.truncated && ev.kind == 0) phase.truncated++;
			if (ev.kind == 0 || ev.kind == 1) {
				for (int a = 0; a < ANCHORS.length; a++) {
					if (ev.exact[a]) phase.anchorExact.computeIfAbsent(ANCHORS[a][0], k -> new long[2])[ev.kind]++;
					if (ev.prefix[a]) phase.anchorPrefix.computeIfAbsent(ANCHORS[a][0], k -> new long[2])[ev.kind]++;
				}
			}
		}
		for (long[] s : otherThreadSamples) {
			long at = s[0] + best;
			for (Phase p : phases.values()) {
				if (at >= p.lo && at < p.hi) { p.threads.merge(otherThreadNames.get((int) s[1]), 1L, Long::sum); break; }
			}
		}
		for (Phase p : phases.values()) {
			for (long[] f : p.frames) {
				int flags = (int) f[2];
				boolean[] in = {true, (flags & 1) != 0, (flags & 1) == 0, (flags & 2) != 0, (flags & 4) != 0};
				for (int c = 0; c < CATEGORY.length; c++) if (in[c]) p.frameCount[c]++;
			}
		}
		JsonWriter out = new JsonWriter().beginObject().field("schema", "session26-whole-frame-stack-keys-1")
			.field("scope", "client main thread; events assigned to CSV frames after the calibrated epoch offset")
			.field("key_fields", "anchor chain (id:line, pkg:scope), frame below the deepest chain element, leaf[, object class]")
			.field("note", "Sample counts and sampled allocation weights; not times or exact bytes. Native samples use their own period.")
			.field("execution_events_in_file", executionEvents).field("native_events_in_file", nativeEvents)
			.field("allocation_events_in_file", allocationEvents)
			.field("epoch_uncertainty_ns", uncertainty).field("offset_ns", best)
			.field("offset_plateau_ns", (-limit + lo * 100_000L) + ".." + (-limit + hi * 100_000L))
			.field("offset_plateau_reaches_limit", atLimit)
			.field("tick_execution_samples", tickSamples).field("tick_samples_in_tick_frames_at_offset", bestCount)
			.field("tick_execution_samples_at_zero", zeroFrames)
			.field("tick_samples_in_tick_frames_at_zero", zeroCount).field("unassigned_main_events", unassigned)
			.beginObject("phases");
		for (Phase p : phases.values()) {
			out.beginObject(p.name).field("window_start_epoch_ns", p.lo).field("window_end_epoch_ns", p.hi)
				.field("truncated_execution_samples", p.truncated).beginObject("frames");
			for (int c = 0; c < CATEGORY.length; c++) out.field(CATEGORY[c], p.frameCount[c]);
			out.endObject();
			for (int k = 0; k < KIND.length; k++) {
				out.beginObject(KIND[k] + "_samples");
				for (int c = 0; c < CATEGORY.length; c++) out.field(CATEGORY[c], p.samples[k][c]);
				out.endObject();
			}
			out.beginObject("allocation_weight");
			for (int c = 0; c < CATEGORY.length; c++) out.field(CATEGORY[c], p.allocationWeight[c]);
			out.endObject().beginObject("anchor_exact_execution_native");
			for (var e : p.anchorExact.entrySet()) out.field(e.getKey(), e.getValue()[0] + "/" + e.getValue()[1]);
			out.endObject().beginObject("anchor_prefix_execution_native");
			for (var e : p.anchorPrefix.entrySet()) out.field(e.getKey(), e.getValue()[0] + "/" + e.getValue()[1]);
			out.endObject().beginObject("other_thread_execution_samples");
			for (var e : p.threads.entrySet()) out.field(e.getKey(), e.getValue());
			out.endObject().beginObject("keys");
			for (int k = 0; k < KIND.length; k++) {
				for (int c = 0; c < CATEGORY.length; c++) {
					out.beginObject(KIND[k] + "." + CATEGORY[c]);
					for (var e : new TreeMap<>(p.keys[k][c]).entrySet()) out.field(e.getKey(), e.getValue());
					out.endObject();
				}
			}
			for (int c = 0; c < CATEGORY.length; c++) {
				out.beginObject("allocation_weight." + CATEGORY[c]);
				for (var e : new TreeMap<>(p.weights[c]).entrySet()) out.field(e.getKey(), e.getValue());
				out.endObject();
			}
			out.endObject().endObject();
		}
		Files.writeString(output, out.endObject().endObject() + "\n");
	}

	private static long[] find(Map<String, Phase> phases, long at) {
		for (Phase p : phases.values()) if (at >= p.lo && at < p.hi) return find(p, at);
		return null;
	}

	/** Frames are contiguous within a phase; binary search on start times. */
	private static long[] find(Phase p, long at) {
		int lo = 0, hi = p.frames.size() - 1, found = -1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (p.frames.get(mid)[0] <= at) { found = mid; lo = mid + 1; } else hi = mid - 1;
		}
		if (found < 0) return null;
		long[] f = p.frames.get(found);
		return at < f[1] ? f : null;
	}
}
