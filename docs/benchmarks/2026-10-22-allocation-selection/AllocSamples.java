import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;

import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;

/**
 * Session 28: writes every jdk.ObjectAllocationSample of a recording as one tab-separated line
 * (gzip): thread, wall epoch milliseconds, sampled weight in bytes, allocated class, truncated flag,
 * then the stack leaf-first as Class.method:line joined by '|'. A trailer line per thread counts samples.
 *
 * Usage: java AllocSamples.java <recording.jfr> <out.tsv.gz>
 */
public final class AllocSamples {

	public static void main(String[] args) throws IOException {
		Path in = Path.of(args[0]);
		Path out = Path.of(args[1]);
		TreeMap<String, long[]> perThread = new TreeMap<>();
		try (RecordingFile file = new RecordingFile(in);
			BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
				new GZIPOutputStream(Files.newOutputStream(out)), StandardCharsets.UTF_8))) {
			while (file.hasMoreEvents()) {
				RecordedEvent e = file.readEvent();
				if (!e.getEventType().getName().equals("jdk.ObjectAllocationSample")) {
					continue;
				}
				RecordedThread t = e.getThread("eventThread");
				String thread = t == null ? "?" : String.valueOf(t.getJavaName());
				long weight = e.getLong("weight");
				RecordedClass c = e.getClass("objectClass");
				long[] count = perThread.computeIfAbsent(thread, k -> new long[2]);
				count[0]++;
				count[1] += weight;
				StringBuilder line = new StringBuilder(256);
				line.append(thread.replace('\t', ' ')).append('\t');
				line.append(e.getStartTime().toEpochMilli() + (e.getStartTime().getNano() % 1_000_000) / 1e6).append('\t');
				line.append(weight).append('\t');
				line.append(c == null ? "?" : c.getName()).append('\t');
				RecordedStackTrace st = e.getStackTrace();
				line.append(st != null && st.isTruncated() ? 1 : 0).append('\t');
				if (st != null) {
					List<RecordedFrame> frames = st.getFrames();
					for (int i = 0; i < frames.size(); i++) {
						RecordedFrame f = frames.get(i);
						if (i > 0) {
							line.append('|');
						}
						line.append(f.getMethod().getType().getName()).append('.').append(f.getMethod().getName())
							.append(':').append(f.getLineNumber());
					}
				}
				w.write(line.toString());
				w.newLine();
			}
			for (var entry : perThread.entrySet()) {
				w.write("#thread\t" + entry.getKey().replace('\t', ' ') + "\t" + entry.getValue()[0] + "\t" + entry.getValue()[1]);
				w.newLine();
			}
		}
	}
}
