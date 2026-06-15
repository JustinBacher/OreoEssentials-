package fr.elias.oreoEssentials.util;

import fr.elias.oreoEssentials.OreoEssentials;

/**
 * Central resolver for the {@code display-mode} menu toggle (chest GUI vs. Paper
 * Dialog API).
 *
 * <p>There is one server-wide default — {@code menus.display-mode} in
 * {@code config.yml} — and each feature menu may carry its own {@code display-mode}
 * value. A per-feature value of {@code default} (or unset / blank / {@code inherit})
 * means "follow the global setting"; any other value ({@code inventory} /
 * {@code dialog}) is an explicit override that ignores the global.
 *
 * <p>This lets an admin flip every menu at once with a single
 * {@code menus.display-mode: dialog}, while still pinning individual menus.
 */
public final class DisplayMode {

    /** Config path of the server-wide default. */
    public static final String GLOBAL_KEY = "menus.display-mode";

    /** Sentinel meaning "inherit the global default". */
    public static final String INHERIT = "default";

    private DisplayMode() {
    }

    /** The raw server-wide default mode string (defaults to {@code inventory}). */
    public static String global() {
        return OreoEssentials.get().getConfig().getString(GLOBAL_KEY, "inventory");
    }

    /** True when the server-wide default is {@code dialog}. */
    public static boolean globalIsDialog() {
        return "dialog".equalsIgnoreCase(global());
    }

    /**
     * Resolves a per-feature {@code display-mode} value against the global default.
     *
     * @param featureValue the menu's own configured value, or {@code null} when unset
     * @return true when the effective mode is the Paper Dialog API
     */
    public static boolean isDialog(String featureValue) {
        if (featureValue != null) {
            String v = featureValue.trim();
            if (!v.isEmpty() && !v.equalsIgnoreCase(INHERIT) && !v.equalsIgnoreCase("inherit")) {
                return v.equalsIgnoreCase("dialog");
            }
        }
        return globalIsDialog();
    }
}
