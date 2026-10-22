package net.adiont.alacer.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;

/** Merge only explicit edits, retaining unrelated property values; publish a complete file or leave the old one. */
public final class SettingsFile {
	private SettingsFile() { }
	public static void update(Path file, Map<String, String> changes) throws IOException {
		Path target = file.toAbsolutePath();
		Properties stored = new Properties();
		if (Files.exists(target)) {
			try (Reader in = Files.newBufferedReader(target, StandardCharsets.UTF_8)) { stored.load(in); }
			catch (IllegalArgumentException e) { throw new IOException("Cannot update an invalid settings file", e); }
		}
		changes.forEach(stored::setProperty);
		Files.createDirectories(target.getParent());
		Path temporary = Files.createTempFile(target.getParent(), "alacer-settings-", ".tmp");
		try {
			try (Writer out = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
				stored.store(out, "Alacer settings. JVM overrides apply to this launch only.");
			}
			try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
			catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
		} finally { Files.deleteIfExists(temporary); }
	}
}
