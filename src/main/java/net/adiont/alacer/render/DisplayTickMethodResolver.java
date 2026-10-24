package net.adiont.alacer.render;

import java.util.function.Supplier;

/** Resolve and validate the callback name before allowing a callback to be skipped. */
final class DisplayTickMethodResolver {
	static String resolve(Class<?> owner, String named, Supplier<String> mapped, Class<?>... parameters) {
		try { return owner.getMethod(named, parameters).getName(); }
		catch (NoSuchMethodException namedUnavailable) {
			try { return owner.getMethod(mapped.get(), parameters).getName(); }
			catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return null; }
		}
	}
	static boolean overrides(Class<?> owner, Class<?> type, String method, Class<?>... parameters) {
		if (method == null) return true;
		try { return type.getMethod(method, parameters).getDeclaringClass() != owner; }
		catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return true; }
	}
	private DisplayTickMethodResolver() { }
}
