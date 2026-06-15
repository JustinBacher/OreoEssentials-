package fr.elias.oreoEssentials.modules.playtime;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.font.FontIconService;
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
import static fr.elias.oreoEssentials.util.DialogButtons.tooltip;

/**
 * Paper Dialog-API front-end for the Playtime Rewards menu. Mirrors
 * {@link PrewardsMenu} (claim ready rewards, admin enable/disable toggle) but
 * renders through the 1.21.6+ Dialog API. Gated behind
 * {@code settings.gui.display-mode: dialog}; the chest GUI remains the default.
 */
public final class PrewardsDialogManager {

    private static final int GRID_COLUMNS = 4;

    private final PlaytimeRewardsService svc;

    public PrewardsDialogManager(PlaytimeRewardsService svc) {
        this.svc = svc;
    }

    public void openPrewards(Player player) {
        player.showDialog(prewardsDialog(player));
    }

    private Dialog prewardsDialog(Player viewer) {
        boolean featureOn = svc.isEnabled();
        OreoEssentials plugin = svc.getPlugin();
        FontIconService fontIcons = plugin.getFontIconService();

        var buttons = list();
        for (RewardEntry r : svc.rewards.values()) {
            if (!svc.hasPermission(viewer, r)) continue;
            PlaytimeRewardsService.State state = svc.stateOf(viewer, r);

            Component icon = fontIcons != null && r.iconMaterial != null
                    ? fontIcons.icon(r.iconMaterial) : Component.empty();
            String label = stripColor(r.iconName != null ? r.iconName : r.displayName);

            NamedTextColor color = !featureOn ? NamedTextColor.DARK_GRAY
                    : switch (state) {
                        case READY -> NamedTextColor.GREEN;
                        case CLAIMED -> NamedTextColor.AQUA;
                        case REPEATING -> NamedTextColor.LIGHT_PURPLE;
                        case LOCKED -> NamedTextColor.RED;
                    };

            var tip = tooltip();
            if (r.iconLore != null) {
                for (String line : r.iconLore) tip.line(stripColor(line), NamedTextColor.GRAY);
            }
            tip.line(featureOn ? "State: " + state.name() : "Status: DISABLED", color);

            String id = r.id;
            buttons.add(button(icon, Component.text(label, color))
                    .tip(tip.build())
                    .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                        if (!svc.isEnabled()) {
                            p.sendMessage(svc.color("&cPlaytime Rewards is disabled."));
                            return;
                        }
                        if (svc.stateOf(p, r) == PlaytimeRewardsService.State.READY) {
                            boolean ok = svc.claim(p, id, true);
                            if (!ok) p.sendMessage(svc.color("&cNot ready or invalid reward."));
                            showPrewards(p);
                        } else {
                            p.sendMessage(svc.color("&cNot ready. Keep playing!"));
                        }
                    })));
        }

        var allButtons = list();
        if (viewer.hasPermission("oreo.prewards.admin")) {
            allButtons.add(button(featureOn ? "Disable Playtime Rewards" : "Enable Playtime Rewards",
                            featureOn ? NamedTextColor.RED : NamedTextColor.GREEN)
                    .tip("Admin: toggle the feature")
                    .width(170)
                    .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                        boolean now = svc.toggleEnabled();
                        p.sendMessage(svc.color("&7Playtime Rewards is now " + (now ? "&aENABLED" : "&cDISABLED")));
                        showPrewards(p);
                    })).build());
        }
        for (ActionButton b : buttons.build()) allButtons.add(b);

        ActionButton close = button("Close", NamedTextColor.RED)
                .tip("Close")
                .click((rv, a) -> onlinePlayer(a).ifPresent(Player::closeInventory))
                .build();

        List<DialogBody> body = featureOn
                ? List.of()
                : List.of(DialogBody.plainMessage(
                        Component.text("Playtime Rewards is currently disabled.", NamedTextColor.RED)));

        return multiAction(
                DialogBase.builder(Component.text(stripColor(svc.skin.title), NamedTextColor.AQUA))
                        .body(body)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                allButtons.build(), close, GRID_COLUMNS);
    }

    private void showPrewards(Audience a) {
        onlinePlayer(a).ifPresent(p -> p.showDialog(prewardsDialog(p)));
    }

    private static String stripColor(String s) {
        if (s == null) return "";
        return s.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
    }

    private Optional<Player> onlinePlayer(Audience audience) {
        return audience instanceof Player p && p.isOnline() ? Optional.of(p) : Optional.empty();
    }
}
