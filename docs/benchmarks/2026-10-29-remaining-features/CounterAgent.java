import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Set;

/** Non-shipped counter observer, with an explicit opt-in resource-reload test driver. */
public final class CounterAgent {
    private static Instrumentation instrumentation;
    private static Path output;
    private static final Set<String> TARGETS = Set.of(
        "net.adiont.alacer.render.MultiDrawIndirect",
        "net.adiont.alacer.sound.FastSoundThread",
        "net.adiont.alacer.render.FastEntityPipeline");

    public static void premain(String args, Instrumentation inst) {
        instrumentation = inst;
        output = Path.of(args);
        if (Boolean.getBoolean("alacer.followup.resourceReload")) ResourceReloadDriver.start(inst);
        Runtime.getRuntime().addShutdownHook(new Thread(CounterAgent::snapshot, "followup-counter-final"));
        Thread observer = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(5000);
                    snapshot();
                }
            } catch (InterruptedException ignored) { }
        }, "followup-counter-observer");
        observer.setDaemon(true);
        observer.start();
    }

    private static synchronized void snapshot() {
        try {
            StringBuilder text = new StringBuilder("snapshot " + Instant.now() + "\n");
            for (Class<?> type : instrumentation.getAllLoadedClasses()) {
                if (!TARGETS.contains(type.getName())) continue;
                for (Method method : type.getDeclaredMethods()) {
                    if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers())
                        || method.getParameterCount() != 0 || !(method.getReturnType() == long.class
                        || method.getName().equals("verificationSummary"))) continue;
                    text.append(type.getSimpleName()).append('.').append(method.getName())
                        .append('=').append(method.invoke(null)).append('\n');
                }
            }
            Files.writeString(output, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable failure) {
            failure.printStackTrace();
        }
    }
}
