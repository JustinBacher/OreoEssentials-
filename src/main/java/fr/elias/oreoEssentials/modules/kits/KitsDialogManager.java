package fr.elias.oreoEssentials.modules.kits;

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
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static fr.elias.oreoEssentials.util.DialogButtons.button;
import static fr.elias.oreoEssentials.util.DialogButtons.list;
import static fr.elias.oreoEssentials.util.DialogButtons.multiAction;
import static fr.elias.oreoEssentials.util.DialogButtons.pagination;
import static fr.elias.oreoEssentials.util.DialogButtons.tooltip;

/**
 * Paper Dialog-API front-end for the Kits menu and per-kit preview. Mirrors
 * {@link KitsMenuSI} (claim a kit, preview its contents, admin enable/disable
 * toggle) but renders through the 1.21.6+ Dialog API. Gated behind
 * {@code menu.display-mode: dialog} in the kits config; the chest GUI remains the
 * default and untouched.
 */
public final class KitsDialogManager {

    private static final int PAGE_SIZE = 50;
    private static final int GRID_COLUMNS = 4;

    private final OreoEssentials plugin;
    private final KitsManager manager;

    public KitsDialogManager(OreoEssentials plugin, KitsManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void openKits(Player player) {
        showList(player, 0);
    }

    private void showList(Audience a, int offset) {
        onlinePlayer(a).ifPresent(p -> p.showDialog(listDialog(p, offset)));
    }

    private Dialog listDialog(Player viewer, int offset) {
        boolean featureOn = manager.isEnabled();
        List<Kit> kits = new ArrayList<>(manager.getKits().values());
        int total = kits.size();
        int from = clamp(offset, total);
        List<Kit> page = kits.subList(from, Math.min(from + PAGE_SIZE, total));

        var allButtons = list();
        if (viewer.hasPermission("oreo.kits.admin")) {
            allButtons.add(button(featureOn ? "Disable Kits" : "Enable Kits",
                            featureOn ? NamedTextColor.RED : NamedTextColor.GREEN)
                    .tip("Admin: toggle the feature")
                    .width(140)
                    .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                        boolean now = manager.toggleEnabled();
                        Lang.send(p, now ? "kits.toggle-enabled" : "kits.toggle-disabled",
                                now ? "<green>Kits feature is now <white>ENABLED</white>.</green>"
                                    : "<yellow>Kits feature is now <white>DISABLED</white>.</yellow>",
                                Map.of());
                        showList(p, from);
                    })).build());
        }
        for (ActionButton b : pagination(from, PAGE_SIZE, total, newOff -> showList(viewer, newOff)).build()) {
            if (total > PAGE_SIZE) allButtons.add(b);
        }

        FontIconService fontIcons = plugin.getFontIconService();
        for (Kit kit : page) {
            Material mat = kit.getIcon() != null ? kit.getIcon().getType() : Material.CHEST;
            Component icon = fontIcons != null ? fontIcons.icon(mat) : Component.empty();
            String id = kit.getId();
            long left = manager.getSecondsLeft(viewer, kit);
            var tip = tooltip();
            if (!featureOn) tip.line("Status: Disabled", NamedTextColor.RED);
            else if (left > 0) tip.line("On cooldown: " + Lang.timeHuman(left), NamedTextColor.YELLOW);
            else tip.line("Ready to claim", NamedTextColor.GREEN);
            tip.line("Click to view & claim", NamedTextColor.GRAY);
            allButtons.add(button(icon, Component.text(stripColor(kit.getDisplayName()),
                            featureOn ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY))
                    .tip(tip.build())
                    .click((rv, a) -> showKit(a, id, from)));
        }

        ActionButton close = button("Close", NamedTextColor.RED)
                .tip("Close")
                .click((rv, a) -> onlinePlayer(a).ifPresent(Player::closeInventory))
                .build();

        List<DialogBody> body = total == 0
                ? List.of(DialogBody.plainMessage(Component.text("No kits available.", NamedTextColor.GRAY)))
                : (featureOn ? List.of()
                        : List.of(DialogBody.plainMessage(
                                Component.text("Kits are currently disabled.", NamedTextColor.RED))));

        return multiAction(
                DialogBase.builder(Component.text(stripColor(manager.getMenuTitle()), NamedTextColor.GOLD))
                        .body(body)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                allButtons.build(), close, GRID_COLUMNS);
    }

    // ── Per-kit (claim + preview) ─────────────────────────────────────────────

    private void showKit(Audience a, String kitId, int backOffset) {
        onlinePlayer(a).ifPresent(p -> {
            Kit kit = manager.getKits().get(kitId);
            if (kit == null) { showList(p, backOffset); return; }
            p.showDialog(kitDialog(p, kit, backOffset));
        });
    }

    private Dialog kitDialog(Player viewer, Kit kit, int backOffset) {
        boolean featureOn = manager.isEnabled();
        long left = manager.getSecondsLeft(viewer, kit);
        String id = kit.getId();

        List<DialogBody> bodies = new ArrayList<>();
        bodies.add(DialogBody.plainMessage(Component.text(stripColor(kit.getDisplayName()), NamedTextColor.GOLD)));
        if (!featureOn) {
            bodies.add(DialogBody.plainMessage(Component.text("Kits are currently disabled.", NamedTextColor.RED)));
        } else if (left > 0) {
            bodies.add(DialogBody.plainMessage(
                    Component.text("On cooldown: " + Lang.timeHuman(left), NamedTextColor.YELLOW)));
        } else {
            bodies.add(DialogBody.plainMessage(Component.text("Ready to claim.", NamedTextColor.GREEN)));
        }

        var buttons = list();
        boolean claimable = featureOn && (left <= 0 || viewer.hasPermission("oreo.kit.bypasscooldown"));
        buttons.add(button("Claim", claimable ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)
                .tip(claimable ? "Claim this kit" : "Not available right now")
                .click((rv, a) -> onlinePlayer(a).ifPresent(p -> claim(p, id, backOffset))));
        buttons.add(button("Preview", NamedTextColor.AQUA)
                .tip("See what's inside this kit")
                .click((rv, a) -> showPreview(a, id, backOffset)));

        ActionButton back = button("Back", NamedTextColor.GRAY)
                .tip("Back to the kit list")
                .click((rv, a) -> showList(a, backOffset))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Kit: " + stripColor(kit.getDisplayName()), NamedTextColor.GOLD))
                        .body(bodies)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons.build(), back, 2);
    }

    private void claim(Player p, String kitId, int backOffset) {
        Kit kit = manager.getKits().get(kitId);
        if (kit == null) { showList(p, backOffset); return; }
        if (!manager.isEnabled()) {
            Lang.send(p, "kits.disabled", "<red>Kits are currently disabled.</red>", Map.of());
            return;
        }
        long cdLeft = manager.getSecondsLeft(p, kit);
        if (cdLeft > 0 && !p.hasPermission("oreo.kit.bypasscooldown")) {
            Lang.send(p, "kits.cooldown",
                    "<yellow>Kit <aqua>%kit_name%</aqua> is on cooldown. Wait <white>%cooldown_left%</white>.</yellow>",
                    Map.of("kit_name", kit.getDisplayName(),
                            "cooldown_left", Lang.timeHuman(cdLeft),
                            "cooldown_left_raw", String.valueOf(cdLeft)));
            return;
        }
        manager.claim(p, kit.getId());
        p.closeInventory();
    }

    // ── Preview ───────────────────────────────────────────────────────────────

    private void showPreview(Audience a, String kitId, int backOffset) {
        onlinePlayer(a).ifPresent(p -> {
            Kit kit = manager.getKits().get(kitId);
            if (kit == null) { showList(p, backOffset); return; }
            p.showDialog(previewDialog(kit, backOffset));
        });
    }

    private Dialog previewDialog(Kit kit, int backOffset) {
        String id = kit.getId();
        List<DialogBody> bodies = new ArrayList<>();
        bodies.add(DialogBody.plainMessage(
                Component.text("Preview: " + stripColor(kit.getDisplayName()), NamedTextColor.GOLD)));
        if (kit.getItems() != null) {
            for (ItemStack it : kit.getItems()) {
                if (it == null || it.getType().isAir()) continue;
                bodies.add(DialogBody.item(it.clone()).showTooltip(true).build());
            }
        }
        if (kit.getCommands() != null && !kit.getCommands().isEmpty()) {
            bodies.add(DialogBody.plainMessage(Component.text("This kit also runs:", NamedTextColor.AQUA)));
            for (String c : kit.getCommands()) {
                bodies.add(DialogBody.plainMessage(Component.text("• " + c, NamedTextColor.GRAY)));
            }
        }

        ActionButton back = button("Back", NamedTextColor.GRAY)
                .tip("Back to the kit")
                .click((rv, a) -> showKit(a, id, backOffset))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Kit Preview", NamedTextColor.GOLD))
                        .body(bodies)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                List.of(), back, 1);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int clamp(int offset, int total) {
        if (offset <= 0 || total <= 0) return 0;
        if (offset >= total) {
            return ((total - 1) / PAGE_SIZE) * PAGE_SIZE;
        }
        return offset;
    }

    private static String stripColor(String s) {
        if (s == null) return "";
        return s.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
    }

    private Optional<Player> onlinePlayer(Audience audience) {
        return audience instanceof Player p && p.isOnline() ? Optional.of(p) : Optional.empty();
    }
}
