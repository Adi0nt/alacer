import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;
import net.adiont.alacer.profiler.JsonWriter;

/**
 * Session 25: streams a JFR recording and aggregates client-thread samples inside particle movement into raw stack
 * keys; analyze.py maps them to Entity.move regions with a line table taken from the runtime Minecraft jar's own
 * bytecode (the runtime jar's lines differ from the decompiled sources jar's). Scope is the exact
 * ParticleManager.tickParticle frame (session 23's streaming scope); the tickParticle prefix count, which also
 * matches tickParticles, is reported separately.
 *
 * Key fields: Entity.move frame line; move's immediate callee (or <self>); the nearest anchor frame above move among
 * getCollisions, checkBlockCollisions, containsFireSource and isInWaterOrRain with its line and immediate callee; the
 * leaf method; whether a Long2ObjectHashMap or World.getBlockState frame is above move. JFR line attribution
 * in compiled code is approximate; these are sample counts, not times. Allocation weights are sampled estimates.
 */
public final class JfrMovementRegions {

	private static final String MOVE = "net.minecraft.entity.Entity.move";
	private static final String TICK = "net.minecraft.client.ParticleManager.tickParticle";
	private static final Set<String> ANCHORS = Set.of("net.minecraft.world.World.getCollisions",
		"net.minecraft.entity.Entity.checkBlockCollisions", "net.minecraft.world.World.containsFireSource",
		"net.minecraft.entity.Entity.isInWaterOrRain");
	private static final String BLOCK_STATE = "net.minecraft.world.World.getBlockState";
	/** The client chunk cache's map class; inlined bimorphic server lookups can also be reported under it. */
	private static final String CHUNK_MAP = "net.minecraft.util.Long2ObjectHashMap.";

	private static final class Window {
		String name; long lo, hi;
		long samples, tickSamples, tickPrefixSamples, moveSamples, moveOutsideTick, allocationSamples, allocationWeight;
		final Map<String, long[]> execution = new TreeMap<>(), allocation = new TreeMap<>();
	}

	private static String name(RecordedFrame frame) {
		return frame.getMethod().getType().getName() + "." + frame.getMethod().getName();
	}

	public static void main(String[] args) throws Exception {
		Window[] windows = new Window[args.length - 2];
		for (int i = 0; i < windows.length; i++) {
			String[] p = args[i + 2].split(":"); Window w = new Window();
			w.name = p[0]; w.lo = Long.parseLong(p[1]); w.hi = Long.parseLong(p[2]); windows[i] = w;
		}
		long executionEvents = 0, allocationEvents = 0, truncated = 0;
		try (RecordingFile recording = new RecordingFile(Path.of(args[0]))) {
			while (recording.hasMoreEvents()) {
				RecordedEvent e = recording.readEvent(); String type = e.getEventType().getName();
				boolean allocation = type.equals("jdk.ObjectAllocationSample");
				if (!allocation && !type.equals("jdk.ExecutionSample")) continue;
				if (allocation) allocationEvents++; else executionEvents++;
				RecordedThread thread = e.getThread(allocation ? "eventThread" : "sampledThread");
				if (thread == null || !"main".equals(thread.getJavaName())) continue;
				long at = Math.addExact(Math.multiplyExact(e.getStartTime().getEpochSecond(), 1_000_000_000L), e.getStartTime().getNano());
				Window w = null;
				for (Window candidate : windows) if (at >= candidate.lo && at < candidate.hi) { w = candidate; break; }
				if (w == null) continue;
				RecordedStackTrace trace = e.getStackTrace();
				List<RecordedFrame> frames = trace == null ? List.of() : trace.getFrames();
				if (trace != null && trace.isTruncated()) truncated++;
				int move = -1, tick = -1; boolean prefix = false;
				for (int i = 0; i < frames.size(); i++) {
					String n = name(frames.get(i));
					if (move < 0 && n.equals(MOVE)) move = i;
					if (tick < 0 && n.equals(TICK)) tick = i;
					if (n.startsWith(TICK)) prefix = true;
				}
				if (!allocation) {
					w.samples++;
					if (tick >= 0) w.tickSamples++;
					if (prefix) w.tickPrefixSamples++;
				}
				if (move < 0) continue;
				if (tick < move) { if (!allocation) w.moveOutsideTick++; continue; }
				boolean chunk = false, read = false; int anchor = -1;
				for (int i = move - 1; i >= 0; i--) {
					String n = name(frames.get(i));
					if (anchor < 0 && ANCHORS.contains(n)) anchor = i;
					if (n.startsWith(CHUNK_MAP)) chunk = true;
					if (n.equals(BLOCK_STATE)) read = true;
				}
				StringBuilder key = new StringBuilder();
				key.append(frames.get(move).getLineNumber()).append('\t').append(move == 0 ? "<self>" : name(frames.get(move - 1)));
				if (anchor >= 0) {
					key.append('\t').append(name(frames.get(anchor))).append('\t').append(frames.get(anchor).getLineNumber())
						.append('\t').append(anchor == 0 ? "<self>" : name(frames.get(anchor - 1)));
				} else {
					key.append("\t\t\t");
				}
				key.append('\t').append(name(frames.get(0))).append('\t').append(chunk).append('\t').append(read);
				if (allocation) {
					long weight = e.getLong("weight");
					w.allocationSamples++; w.allocationWeight += weight;
					key.append('\t').append(e.getClass("objectClass").getName());
					long[] v = w.allocation.computeIfAbsent(key.toString(), k -> new long[2]);
					v[0]++; v[1] += weight;
				} else {
					w.moveSamples++;
					w.execution.computeIfAbsent(key.toString(), k -> new long[1])[0]++;
				}
			}
		}
		JsonWriter out = new JsonWriter().beginObject().field("schema", "session25-movement-stack-keys-1")
			.field("scope", "client main thread; CSV phase windows; Entity.move below the exact ParticleManager.tickParticle frame")
			.field("key_fields", "move_line, move_callee, anchor, anchor_line, anchor_callee, leaf, chunk_map_frame, block_state_read[, object_class]")
			.field("note", "Execution sample counts and sampled allocation weights; not times or exact bytes.")
			.field("execution_events_in_file", executionEvents).field("allocation_events_in_file", allocationEvents)
			.field("truncated_stacks_in_windows", truncated)
			.beginObject("phases");
		for (Window w : windows) {
			out.beginObject(w.name).field("window_start_epoch_ns", w.lo).field("window_end_epoch_ns", w.hi)
				.field("client_execution_samples", w.samples).field("tick_particle_exact_samples", w.tickSamples)
				.field("tick_particle_prefix_samples", w.tickPrefixSamples).field("move_samples", w.moveSamples)
				.field("move_samples_outside_exact_tick", w.moveOutsideTick)
				.field("move_allocation_samples", w.allocationSamples).field("move_allocation_sampled_weight", w.allocationWeight)
				.beginObject("execution_keys");
			for (var e : w.execution.entrySet()) out.field(e.getKey(), e.getValue()[0]);
			out.endObject().beginObject("allocation_key_samples");
			for (var e : w.allocation.entrySet()) out.field(e.getKey(), e.getValue()[0]);
			out.endObject().beginObject("allocation_key_weight");
			for (var e : w.allocation.entrySet()) out.field(e.getKey(), e.getValue()[1]);
			out.endObject().endObject();
		}
		Files.writeString(Path.of(args[1]), out.endObject().endObject() + "\n");
	}
}
