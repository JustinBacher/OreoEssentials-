package fr.elias.oreoEssentials.config.menu;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.config.SettingsConfig;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

import static fr.elias.oreoEssentials.util.DialogButtons.button;
import static fr.elias.oreoEssentials.util.DialogButtons.list;
import static fr.elias.oreoEssentials.util.DialogButtons.multiAction;

/**
 * Paper Dialog-API front-end for the OreoEssentials settings menu. Mirrors
 * {@link OeSettingsMenu} (per-feature enable/disable toggles + reload) but renders
 * through the 1.21.6+ Dialog API. Gated behind
 * {@code settings-gui.display-mode: dialog}; the chest GUI remains the default.
 */
public final class OeSettingsDialogManager {

    /** Feature key → display name, mirroring the chest menu's toggle grid. */
    private static final String[][] FEATURES = {
            {"chat", "Chat System"}, {"economy", "Economy"}, {"kits", "Kits"},
            {"trade", "Trading"}, {"tempfly", "Temporary Fly"}, {"playervaults", "Player Vaults"},
            {"rtp", "Random Teleport"}, {"bossbar", "Boss Bar"}, {"scoreboard", "Scoreboard"},
            {"tab", "Tab List"}, {"sit", "Sit Command"}, {"portals", "Custom Portals"},
            {"jumppads", "Jump Pads"}, {"clearlag", "Clear Lag"}, {"mobs", "Mob Features"},
            {"oreoholograms", "Holograms"}, {"playtime-rewards", "Playtime Rewards"},
            {"discord-moderation", "Discord Integration"}
    };
    private static final int GRID_COLUMNS = 3;

    private final OreoEssentials plugin;
    private final SettingsConfig settings;

    public OeSettingsDialogManager(OreoEssentials plugin) {
        this.plugin = plugin;
        this.settings = plugin.getSettingsConfig();
    }

    public void openSettings(Player player) {
        player.showDialog(settingsDialog());
    }

    private Dialog settingsDialog() {
        var buttons = list();
        for (String[] feature : FEATURES) {
            String key = feature[0];
            String name = feature[1];
            boolean enabled = settings.isEnabled(key);
            buttons.add(button((enabled ? "✔ " : "✘ ") + name,
                            enabled ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .tip(enabled ? "Enabled — click to disable" : "Disabled — click to enable")
                    .width(150)
                    .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                        toggleFeature(key);
                        p.showDialog(settingsDialog());
                    })));
        }

        buttons.add(button("Reload Plugin", NamedTextColor.YELLOW)
                .tip("Apply all changes (reloads OreoEssentials)")
                .width(150)
                .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                    p.closeInventory();
                    p.sendMessage("§aReloading OreoEssentials...");
                    fr.elias.oreoEssentials.util.OreScheduler.run(plugin, () -> {
                        plugin.onDisable();
                        org.bukkit.event.HandlerList.unregisterAll(plugin);
                        plugin.onEnable();
                        p.sendMessage("§aOreoEssentials reloaded successfully!");
                    });
                })));

        ActionButton close = button("Close", NamedTextColor.RED)
                .tip("Close")
                .click((rv, a) -> onlinePlayer(a).ifPresent(Player::closeInventory))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("OreoEssentials Settings", NamedTextColor.GOLD))
                        .body(List.of(DialogBody.plainMessage(
                                Component.text("Toggle features on or off, then reload to apply.",
                                        NamedTextColor.GRAY))))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons.build(), close, GRID_COLUMNS);
    }

    private void toggleFeature(String featureKey) {
        boolean current = settings.isEnabled(featureKey);
        settings.getRoot().set("features." + featureKey + ".enabled", !current);
        try {
            settings.getRoot().save(new java.io.File(plugin.getDataFolder(), "settings.yml"));
            plugin.getLogger().info("[Settings] Toggled " + featureKey + ": " + !current);
        } catch (Exception ex) {
            plugin.getLogger().severe("[Settings] Failed to save: " + ex.getMessage());
        }
    }

    private Optional<Player> onlinePlayer(Audience audience) {
        return audience instanceof Player p && p.isOnline() ? Optional.of(p) : Optional.empty();
    }
}
