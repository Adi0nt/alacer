import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Non-shipped driver: schedules real reloadResources calls on Minecraft's client thread. */
public final class ResourceReloadDriver {
    public static void start(Instrumentation instrumentation) {
        Thread driver = new Thread(() -> {
            try {
                Thread.sleep(35000);
                Class<?> minecraft = null;
                for (Class<?> type : instrumentation.getAllLoadedClasses()) {
                    if (type.getName().equals("net.minecraft.client.Minecraft")
                        && type.getClassLoader() != null
                        && type.getClassLoader().getClass().getName().contains("Knot")) minecraft = type;
                }
                if (minecraft == null) throw new IllegalStateException("Development Minecraft class not loaded");
                Object client = minecraft.getMethod("getInstance").invoke(null);
                if (client == null) throw new IllegalStateException("Selected client is uninitialized: " + minecraft.getClassLoader());
                System.out.println("FOLLOWUP client loader=" + minecraft.getClassLoader());
                Method schedule = minecraft.getMethod("executeTask", Runnable.class);
                Method reload = minecraft.getMethod("reloadResources");
                for (int i = 1; i <= 3; i++) {
                    final int index = i;
                    Runnable action = () -> {
                        try {
                            if (client.getClass().getField("world").get(client) == null) {
                                throw new IllegalStateException("Resource test needs a loaded world");
                            }
                            System.out.println("FOLLOWUP reload " + index + " begin thread=" + Thread.currentThread().getName());
                            reload.invoke(client);
                            ClassLoader loader = client.getClass().getClassLoader();
                            Object summary = Class.forName("net.adiont.alacer.render.FastMipmaps", false, loader)
                                .getMethod("verificationSummary").invoke(null);
                            System.out.println("FOLLOWUP reload " + index + " completed; " + summary);
                        } catch (Throwable failure) {
                            throw new RuntimeException("Resource follow-up failed", failure);
                        }
                    };
                    ((Future<?>) schedule.invoke(client, action)).get(30, TimeUnit.SECONDS);
                    Thread.sleep(5000);
                }
            } catch (Throwable failure) {
                System.err.println("FOLLOWUP RESOURCE FAILURE");
                failure.printStackTrace();
            }
        }, "followup-resource-driver");
        driver.setDaemon(true);
        driver.start();
    }
}
