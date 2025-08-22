package io.github.jochyoua.sandtrap.utilities;

import io.github.jochyoua.sandtrap.SandTrap;
import java.util.function.Supplier;

public class DebugLogger {

    private DebugLogger() {
        throw new UnsupportedOperationException("DebugLogger is a utility class and cannot be instantiated.");
    }

    /**
     * Logs a lazily evaluated debug message if debug mode is enabled.
     *
     * @param plugin  The plugin instance.
     * @param messageSupplier A supplier that generates the debug message.
     */
    public static void log(SandTrap plugin, Supplier<String> messageSupplier) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[DEBUG] " + messageSupplier.get());
        }
    }
}
