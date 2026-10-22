import java.nio.file.Path;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;
import net.adiont.alacer.profiler.JsonWriter;

/** Streaming supplement: avoids materializing the full JFR JSON repeatedly. */
public final class JfrPassAttribution {
    private static final class Window {
        String name; long lo, hi, samples, passSamples, allocationSamples, allocationWeight;
        long tickSamples, moveSamples, gatherSamples, entityQuerySamples, tickAllocationSamples, tickAllocationWeight;
        final Map<String, Long> objects = new LinkedHashMap<>(), sites = new LinkedHashMap<>();
        final Map<String, Long> boxSites = new LinkedHashMap<>();
    }
    public static void main(String[] args) throws Exception {
        Window[] windows = new Window[args.length - 2];
        for (int i = 0; i < windows.length; i++) {
            String[] p = args[i + 2].split(":"); Window w = new Window();
            w.name = p[0]; w.lo = Long.parseLong(p[1]); w.hi = Long.parseLong(p[2]); windows[i] = w;
        }
        try (RecordingFile recording = new RecordingFile(Path.of(args[0]))) {
            while (recording.hasMoreEvents()) {
                RecordedEvent e = recording.readEvent(); String type = e.getEventType().getName();
                boolean allocation = type.equals("jdk.ObjectAllocationSample");
                if (!allocation && !type.equals("jdk.ExecutionSample")) continue;
                RecordedThread thread = e.getThread(allocation ? "eventThread" : "sampledThread");
                if (thread == null || !thread.getJavaName().equals("main")) continue;
                long at = Math.addExact(Math.multiplyExact(e.getStartTime().getEpochSecond(), 1_000_000_000L), e.getStartTime().getNano());
                Window w = null;
                for (Window candidate : windows) if (at >= candidate.lo && at < candidate.hi) { w = candidate; break; }
                if (w == null) continue;
                boolean pass = false, tick = false, move = false, gather = false, entities = false;
                String boxSite = null, pendingBox = null; StringBuilder site = new StringBuilder(); int depth = 0;
                if (e.getStackTrace() != null) for (RecordedFrame frame : e.getStackTrace().getFrames()) {
                    String name = frame.getMethod().getType().getName() + "." + frame.getMethod().getName();
                    if (name.equals("net.minecraft.client.ParticleManager.render")) pass = true;
                    if (name.equals("net.minecraft.client.ParticleManager.tickParticle")) tick = true;
                    if (name.equals("net.minecraft.entity.Entity.move")) move = true;
                    if (name.equals("net.minecraft.world.World.getCollisions")) gather = true;
                    if (name.equals("net.minecraft.world.World.getEntities")) entities = true;
                    if (pendingBox != null) { boxSite = pendingBox + " <- " + name + ":" + frame.getLineNumber(); pendingBox = null; }
                    if (boxSite == null && name.startsWith("net.minecraft.util.math.Box.")) pendingBox = name;
                    if (depth++ < 4) { if (!site.isEmpty()) site.append(" <- "); site.append(name); }
                }
                if (!allocation) {
                    w.samples++; if (pass) w.passSamples++;
                    if (tick) { w.tickSamples++; if (move) w.moveSamples++; if (gather) w.gatherSamples++; if (entities) w.entityQuerySamples++; }
                    continue;
                }
                if (tick) {
                    long weight = e.getLong("weight"); w.tickAllocationSamples++; w.tickAllocationWeight += weight;
                    if (boxSite != null) w.boxSites.merge(boxSite, weight, Long::sum);
                }
                if (pass) {
                    long weight = e.getLong("weight"); w.allocationSamples++; w.allocationWeight += weight;
                    w.objects.merge(e.getClass("objectClass").getName(), weight, Long::sum);
                    w.sites.merge(site.toString(), weight, Long::sum);
                }
            }
        }
        JsonWriter out = new JsonWriter().beginObject()
            .field("scope", "client main thread; CSV phase windows; unlit ParticleManager.render stack")
            .field("allocation_note", "JFR sampled weights are estimates, not exact allocated bytes. No samples is not proof of elimination.")
            .beginObject("phases");
        for (Window w : windows) {
            out.beginObject(w.name).field("window_start_epoch_ns", w.lo).field("window_end_epoch_ns", w.hi)
                .field("client_execution_samples", w.samples).field("pass_execution_samples", w.passSamples)
                .field("pass_allocation_samples", w.allocationSamples).field("pass_allocation_sampled_weight", w.allocationWeight)
                .field("particle_tick_execution_samples", w.tickSamples).field("particle_move_execution_samples", w.moveSamples)
                .field("particle_gather_execution_samples", w.gatherSamples).field("particle_entity_query_execution_samples", w.entityQuerySamples)
                .field("particle_tick_allocation_samples", w.tickAllocationSamples).field("particle_tick_allocation_sampled_weight", w.tickAllocationWeight)
                .beginObject("objects_sampled_weight");
            for (var e : w.objects.entrySet()) out.field(e.getKey(), e.getValue());
            out.endObject().beginObject("sites_sampled_weight");
            for (var e : w.sites.entrySet()) out.field(e.getKey(), e.getValue());
            out.endObject().beginObject("particle_box_sites_sampled_weight");
            for (var e : w.boxSites.entrySet()) out.field(e.getKey(), e.getValue());
            out.endObject().endObject();
        }
        Files.writeString(Path.of(args[1]), out.endObject().endObject() + "\n");
    }
}
