package fr.elias.oreoEssentials.modules.mobs;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

/**
 * Lightweight bridge to UltimateChristmas Santa.
 * Safe even if UltimateChristmas isn't installed.
 */
public final class SantaHook {

    private static Object cachedSantaManager = null;
    private static boolean lookedUp = false;

    private SantaHook() {}

    private static Object getSantaManager() {
        // only try lookup once to avoid spam
        if (lookedUp) return cachedSantaManager;
        lookedUp = true;

        var plugin = Bukkit.getPluginManager().getPlugin("UltimateChristmas");
        if (plugin != null && plugin.isEnabled()) {
            try {
                cachedSantaManager = plugin.getClass().getMethod("getSantaManager").invoke(plugin);
            } catch (Throwable ignored) {
                cachedSantaManager = null;
            }
        }
        return cachedSantaManager;
    }

    public static boolean isSanta(Entity e) {
        Object sm = getSantaManager();
        if (sm == null || e == null) return false;
        try {
            Object result = sm.getClass().getMethod("isSanta", Entity.class).invoke(sm, e);
            return Boolean.TRUE.equals(result);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
