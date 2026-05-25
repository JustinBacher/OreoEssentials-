package fr.elias.oreoEssentials.api;

/**
 * API for the CommandToggle module — enables/disables Bukkit commands at runtime.
 *
 * <p>Obtain via {@link OreoEssentialsAPI#commandToggle()}. Returns {@code null} if disabled.
 */
public interface ICommandToggleAPI {

    /**
     * Re-evaluates the toggle config and registers/unregisters commands accordingly.
     */
    void applyToggles();

    /**
     * Reloads the toggle configuration from disk and re-applies toggles.
     */
    void reload();
}
