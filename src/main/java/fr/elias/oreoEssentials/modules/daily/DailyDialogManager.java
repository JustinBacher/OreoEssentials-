package fr.elias.oreoEssentials.modules.daily;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.font.FontIconService;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static fr.elias.oreoEssentials.util.DialogButtons.button;
import static fr.elias.oreoEssentials.util.DialogButtons.list;
import static fr.elias.oreoEssentials.util.DialogButtons.multiAction;
import static fr.elias.oreoEssentials.util.DialogButtons.pagination;
import static fr.elias.oreoEssentials.util.DialogButtons.tooltip;

/**
 * Paper Dialog-API front-end for the Daily Rewards menu. Mirrors {@link DailyMenu}
 * (claim today's reward, browse streak days, admin enable/disable toggle) but renders
 * through the 1.21.6+ Dialog API. Gated behind {@code GUI.DisplayMode: dialog} in
 * {@code dailyrewards.yml}; the chest GUI remains the default and untouched.
 */
public final class DailyDialogManager {

    private static final int PAGE_SIZE = 30;
    private static final int GRID_COLUMNS = 5;

    private final OreoEssentials plugin;
    private final DailyConfig cfg;
    private final DailyService svc;
    private final RewardsConfig rewards;

    public DailyDialogManager(OreoEssentials plugin, DailyConfig cfg, DailyService svc, RewardsConfig rewards) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.svc = svc;
        this.rewards = rewards;
    }

    public void openDaily(Player player) {
        showDaily(player, 0);
    }

    private void showDaily(Audience a, int offset) {
        onlinePlayer(a).ifPresent(p -> p.showDialog(dailyDialog(p, offset)));
    }

    private Dialog dailyDialog(Player viewer, int offset) {
        List<RewardsConfig.DayDef> defs = new ArrayList<>(rewards.all());
        defs.sort(Comparator.comparingInt(d -> d.day));

        int total = Math.max(rewards.maxDay(), defs.size());
        int from = clamp(offset, total);
        int to = Math.min(from + PAGE_SIZE, total);

        boolean featureOn = svc.isEnabled();
        int currentStreak = svc.getStreak(viewer.getUniqueId());
        int todayIndex = svc.nextDayIndex(currentStreak);

        var buttons = list();
        addPagination(buttons, from, total, newOff -> showDaily(viewer, newOff));

        FontIconService fontIcons = plugin.getFontIconService();
        for (int day = from + 1; day <= to; day++) {
            RewardsConfig.DayDef def = rewards.day(day);
            Material mat = (def != null && def.icon != null) ? def.icon : Material.SUNFLOWER;
            Component icon = fontIcons != null ? fontIcons.icon(mat) : Component.empty();

            String rawName = (def != null && def.name != null && !def.name.isEmpty())
                    ? def.name : "Day " + day;
            boolean isReadyToday = (day == todayIndex) && svc.canClaimToday(viewer);

            NamedTextColor color;
            String status;
            if (!featureOn) { color = NamedTextColor.DARK_GRAY; status = "Disabled"; }
            else if (day < todayIndex) { color = NamedTextColor.AQUA; status = "Claimed"; }
            else if (day == todayIndex) {
                if (isReadyToday) { color = NamedTextColor.GREEN; status = "Click to claim!"; }
                else { color = NamedTextColor.RED; status = "Not ready"; }
            } else { color = NamedTextColor.GRAY; status = "Future"; }

            int dayNum = day;
            buttons.add(button(icon, Component.text(stripColor(rawName), color))
                    .tip(tooltip()
                            .line("Day " + day, NamedTextColor.GRAY)
                            .line("Status: " + status, color)
                            .build())
                    .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                        if (!svc.isEnabled()) {
                            Lang.send(p, "daily.gui.disabled",
                                    "<red>Daily Rewards is disabled.</red>", Map.of());
                            return;
                        }
                        if (dayNum == svc.nextDayIndex(svc.getStreak(p.getUniqueId()))
                                && svc.canClaimToday(p)) {
                            boolean ok = svc.claim(p);
                            if (ok && cfg.closeOnClaim) {
                                p.closeInventory();
                                return;
                            }
                            showDaily(p, from);
                        }
                    })));
        }

        var bodies = new ArrayList<DialogBody>();
        bodies.add(DialogBody.plainMessage(Component.text(
                viewer.getName() + " — Streak: " + currentStreak + ", Today: Day " + todayIndex,
                NamedTextColor.AQUA)));
        if (!featureOn) {
            bodies.add(DialogBody.plainMessage(
                    Component.text("Daily Rewards is currently disabled.", NamedTextColor.RED)));
        }

        var menu = list();
        if (viewer.hasPermission("oreo.daily.admin")) {
            menu.add(button(featureOn ? "Disable Daily Rewards" : "Enable Daily Rewards",
                            featureOn ? NamedTextColor.RED : NamedTextColor.GREEN)
                    .tip("Admin: toggle the feature")
                    .width(160)
                    .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                        boolean now = svc.toggleEnabled();
                        cfg.setEnabled(now);
                        try { cfg.save(); } catch (Throwable ignored) {}
                        Lang.send(p, "daily.gui.toggled",
                                "<yellow>Daily Rewards is now %state%</yellow>",
                                Map.of("state", now ? "<green>ENABLED</green>" : "<red>DISABLED</red>"));
                        showDaily(p, from);
                    })));
        }
        // Prepend the admin row ahead of the day grid.
        var allButtons = list();
        for (ActionButton b : menu.build()) allButtons.add(b);
        for (ActionButton b : buttons.build()) allButtons.add(b);

        ActionButton close = button("Close", NamedTextColor.RED)
                .tip("Close")
                .click((rv, a) -> onlinePlayer(a).ifPresent(Player::closeInventory))
                .build();

        return multiAction(
                DialogBase.builder(Component.text(stripColor(cfg.guiTitle), NamedTextColor.GOLD))
                        .body(bodies)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                allButtons.build(), close, GRID_COLUMNS);
    }

    private void addPagination(fr.elias.oreoEssentials.util.DialogButtons.Buttons buttons,
                               int offset, int total, java.util.function.IntConsumer reopen) {
        if (total > PAGE_SIZE) {
            for (ActionButton b : pagination(offset, PAGE_SIZE, total, reopen).build()) {
                buttons.add(b);
            }
        }
    }

    private static int clamp(int offset, int total) {
        if (offset <= 0 || total <= 0) return 0;
        if (offset >= total) {
            return ((total - 1) / PAGE_SIZE) * PAGE_SIZE;
        }
        return offset;
    }

    /** Drops legacy {@code &}/{@code §} colour codes so labels render cleanly in dialogs. */
    private static String stripColor(String s) {
        if (s == null) return "";
        return s.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
    }

    private Optional<Player> onlinePlayer(Audience audience) {
        return audience instanceof Player p && p.isOnline() ? Optional.of(p) : Optional.empty();
    }
}
