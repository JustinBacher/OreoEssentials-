package fr.elias.oreoEssentials.modules.portals.gui;

import fr.elias.oreoEssentials.modules.portals.PortalParticleConfig;
import fr.elias.oreoEssentials.modules.portals.PortalsManager;
import fr.elias.oreoEssentials.modules.portals.PortalsManager.Portal;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static fr.elias.oreoEssentials.util.DialogButtons.button;
import static fr.elias.oreoEssentials.util.DialogButtons.list;
import static fr.elias.oreoEssentials.util.DialogButtons.multiAction;
import static fr.elias.oreoEssentials.util.DialogButtons.pagination;
import static fr.elias.oreoEssentials.util.DialogButtons.tooltip;

/**
 * Paper Dialog-API front-end for the portal admin GUIs. Mirrors
 * {@link PortalListGUI} / {@link PortalEditGUI} / {@link PortalParticleGUI} but
 * renders through the 1.21.6+ Dialog API. Gated behind {@code display-mode: dialog}
 * in {@code portals/config.yml}; the chest GUIs remain the default.
 *
 * <p>The permission / destination-warp / destination-server fields, which the chest
 * editor sets through chat prompts, become inline text inputs here.
 */
public final class PortalsDialogManager {

    private static final String[] PRESET_TYPES = {
            "PORTAL", "FLAME", "SOUL_FIRE_FLAME", "END_ROD", "WITCH",
            "HAPPY_VILLAGER", "SMOKE_NORMAL", "SMOKE_LARGE", "SPELL_MOB",
            "EXPLOSION_NORMAL", "DRIP_LAVA", "ENCHANTMENT_TABLE", "TOTEM"
    };
    private static final int[] PRESET_COUNTS = {5, 10, 20, 40, 80};
    private static final int PAGE_SIZE = 50;
    private static final int GRID_COLUMNS = 4;

    private final PortalsManager manager;

    public PortalsDialogManager(PortalsManager manager) {
        this.manager = manager;
    }

    public void openList(Player player) {
        showList(player, 0);
    }

    // ── List ──────────────────────────────────────────────────────────────────

    private void showList(Audience a, int offset) {
        onlinePlayer(a).ifPresent(p -> p.showDialog(listDialog(p, offset)));
    }

    private Dialog listDialog(Player viewer, int offset) {
        List<String> names = new ArrayList<>(manager.listNames());
        int total = names.size();
        int from = clamp(offset, total);
        List<String> page = names.subList(from, Math.min(from + PAGE_SIZE, total));

        var buttons = list();
        if (total > PAGE_SIZE) {
            for (ActionButton b : pagination(from, PAGE_SIZE, total, newOff -> showList(viewer, newOff)).build()) {
                buttons.add(b);
            }
        }
        for (String name : page) {
            Portal portal = manager.get(name);
            if (portal == null) continue;
            String id = name;
            buttons.add(button(Component.text(portal.name, NamedTextColor.AQUA))
                    .tip(portalTooltip(portal))
                    .click((rv, a) -> showEdit(a, id)));
        }

        ActionButton close = button("Close", NamedTextColor.RED)
                .tip("Close")
                .click((rv, a) -> onlinePlayer(a).ifPresent(Player::closeInventory))
                .build();

        List<DialogBody> body = total == 0
                ? List.of(DialogBody.plainMessage(
                        Component.text("No portals configured.", NamedTextColor.GRAY)))
                : List.of();

        return multiAction(
                DialogBase.builder(Component.text("Portals", NamedTextColor.GOLD))
                        .body(body)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons.build(), close, GRID_COLUMNS);
    }

    // ── Edit ──────────────────────────────────────────────────────────────────

    private void showEdit(Audience a, String name) {
        onlinePlayer(a).ifPresent(p -> {
            Portal portal = manager.get(name);
            if (portal == null) { showList(p, 0); return; }
            p.showDialog(editDialog(portal));
        });
    }

    private Dialog editDialog(Portal portal) {
        String name = portal.name;
        boolean keep = portal.keepYawPitch;

        var buttons = list()
                .add(button("Set Destination Here", NamedTextColor.GREEN)
                        .tip("Move this portal's destination to where you stand")
                        .width(170)
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                            manager.updateDestination(name, p.getLocation());
                            p.sendMessage("§aDestination updated to your location.");
                            showEdit(p, name);
                        })))
                .add(button("Keep Yaw/Pitch: " + (keep ? "ON" : "OFF"),
                                keep ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                        .tip("Toggle whether players keep their look direction")
                        .width(150)
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                            manager.toggleKeepYawPitch(name);
                            showEdit(p, name);
                        })))
                .add(button("Edit Routing", NamedTextColor.AQUA)
                        .tip("Permission / dest warp / dest server")
                        .width(150)
                        .click((rv, a) -> showRouting(a, name)))
                .add(button("Particles", NamedTextColor.LIGHT_PURPLE)
                        .tip("Configure teleport & ambient particles")
                        .width(130)
                        .click((rv, a) -> showParticles(a, name)))
                .add(button("Delete", NamedTextColor.RED)
                        .tip("Permanently delete this portal")
                        .width(120)
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p ->
                                p.showDialog(confirmDeleteDialog(name)))));

        ActionButton back = button("Back", NamedTextColor.GRAY)
                .tip("Back to the portal list")
                .click((rv, a) -> showList(a, 0))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Edit: " + name, NamedTextColor.AQUA))
                        .body(List.of(DialogBody.plainMessage(portalSummary(portal))))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons.build(), back, 2);
    }

    private Dialog routingDialog(Portal portal) {
        String name = portal.name;
        var permInput = DialogInput.text("perm", Component.text("Permission ('none' to clear)"))
                .initial(portal.permission == null ? "" : portal.permission)
                .maxLength(64).width(220).labelVisible(true).build();
        var warpInput = DialogInput.text("warp", Component.text("Dest warp ('none' for coords)"))
                .initial(portal.destWarp == null ? "" : portal.destWarp)
                .maxLength(48).width(220).labelVisible(true).build();
        var serverInput = DialogInput.text("server", Component.text("Dest server ('none' for same)"))
                .initial(portal.destServer == null ? "" : portal.destServer)
                .maxLength(48).width(220).labelVisible(true).build();

        List<ActionButton> btns = list()
                .add(button("Save", NamedTextColor.GREEN)
                        .tip("Apply routing changes")
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                            manager.updatePermission(name, blankToNone(readText(rv, "perm", "")));
                            manager.updateDestWarp(name, blankToNone(readText(rv, "warp", "")));
                            manager.updateDestServer(name, blankToNone(readText(rv, "server", "")));
                            p.sendMessage("§aPortal routing updated.");
                            showEdit(p, name);
                        })))
                .add(button("Back", NamedTextColor.GRAY)
                        .tip("Discard and go back")
                        .click((rv, a) -> showEdit(a, name)))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Portal Routing", NamedTextColor.AQUA))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "Leave a field blank or type 'none' to clear it.", NamedTextColor.GRAY))))
                        .inputs(List.of(permInput, warpInput, serverInput))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .build(),
                btns, null, 2);
    }

    private void showRouting(Audience a, String name) {
        onlinePlayer(a).ifPresent(p -> {
            Portal portal = manager.get(name);
            if (portal == null) { showList(p, 0); return; }
            p.showDialog(routingDialog(portal));
        });
    }

    // ── Particles ─────────────────────────────────────────────────────────────

    private void showParticles(Audience a, String name) {
        onlinePlayer(a).ifPresent(p -> {
            Portal portal = manager.get(name);
            if (portal == null) { showList(p, 0); return; }
            p.showDialog(particlesDialog(portal));
        });
    }

    private Dialog particlesDialog(Portal portal) {
        String name = portal.name;
        PortalParticleConfig pc = portal.particles;

        var buttons = list();
        buttons.add(button("Reset Teleport → Default", NamedTextColor.RED)
                .tip("Revert teleport particle to the global default")
                .width(180)
                .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                    manager.updateParticleTeleportType(name, "");
                    manager.updateParticleTeleportCount(name, -1);
                    showParticles(p, name);
                })));
        buttons.add(button("Ambient: " + (pc.ambientEnabled ? "ON" : "OFF"),
                        pc.ambientEnabled ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                .tip("Toggle continuous ambient particles")
                .width(140)
                .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                    manager.toggleAmbientParticles(name);
                    showParticles(p, name);
                })));

        for (String type : PRESET_TYPES) {
            boolean active = type.equals(pc.teleportType);
            buttons.add(button((active ? "✔ " : "") + type,
                            active ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GRAY)
                    .tip("Set teleport particle type")
                    .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                        manager.updateParticleTeleportType(name, type);
                        showParticles(p, name);
                    })));
        }
        for (int count : PRESET_COUNTS) {
            boolean active = pc.teleportCount == count;
            buttons.add(button((active ? "✔ " : "") + "×" + count,
                            active ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                    .tip("Set teleport particle count")
                    .width(80)
                    .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                        manager.updateParticleTeleportCount(name, count);
                        showParticles(p, name);
                    })));
        }

        ActionButton back = button("Back", NamedTextColor.GRAY)
                .tip("Back to portal edit")
                .click((rv, a) -> showEdit(a, name))
                .build();

        String tType = pc.hasTeleportOverride() ? pc.teleportType : "(global default)";
        String tCount = pc.teleportCount > 0 ? String.valueOf(pc.teleportCount) : "(default)";
        List<DialogBody> bodies = List.of(
                DialogBody.plainMessage(Component.text("Teleport: " + tType + " ×" + tCount, NamedTextColor.LIGHT_PURPLE)),
                DialogBody.plainMessage(Component.text("Ambient: "
                        + (pc.ambientEnabled ? (pc.ambientType.isEmpty() ? "(global)" : pc.ambientType) : "off"),
                        NamedTextColor.DARK_PURPLE)));

        return multiAction(
                DialogBase.builder(Component.text("Particles: " + name, NamedTextColor.LIGHT_PURPLE))
                        .body(bodies)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons.build(), back, GRID_COLUMNS);
    }

    // ── Delete confirm ────────────────────────────────────────────────────────

    private Dialog confirmDeleteDialog(String name) {
        List<ActionButton> btns = list()
                .add(button("Yes, delete", NamedTextColor.RED)
                        .tip("Permanently delete this portal")
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                            manager.remove(name);
                            p.sendMessage("§cPortal §e" + name + " §cdeleted.");
                            showList(p, 0);
                        })))
                .add(button("Cancel", NamedTextColor.GRAY)
                        .tip("Keep this portal")
                        .click((rv, a) -> showEdit(a, name)))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Delete portal?", NamedTextColor.RED))
                        .body(List.of(DialogBody.plainMessage(
                                Component.text("Permanently delete portal \"" + name + "\"?", NamedTextColor.GRAY))))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                btns, null, 2);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Component portalTooltip(Portal portal) {
        var tip = tooltip().line("World: " + portal.world.getName(), NamedTextColor.GRAY);
        if (portal.destWarp != null && !portal.destWarp.isEmpty())
            tip.line("Dest warp: " + portal.destWarp, NamedTextColor.GREEN);
        else if (portal.destination != null)
            tip.line("Dest: " + shortLoc(portal.destination), NamedTextColor.GRAY);
        if (portal.destServer != null && !portal.destServer.isEmpty())
            tip.line("Server: " + portal.destServer, NamedTextColor.GREEN);
        if (portal.permission != null && !portal.permission.isEmpty())
            tip.line("Permission: " + portal.permission, NamedTextColor.YELLOW);
        tip.line("Click to edit", NamedTextColor.AQUA);
        return tip.build();
    }

    private Component portalSummary(Portal portal) {
        StringBuilder sb = new StringBuilder("World: ").append(portal.world.getName());
        if (portal.destWarp != null && !portal.destWarp.isEmpty())
            sb.append("  |  Dest warp: ").append(portal.destWarp);
        else if (portal.destination != null)
            sb.append("  |  Dest: ").append(shortLoc(portal.destination));
        if (portal.destServer != null && !portal.destServer.isEmpty())
            sb.append("  |  Server: ").append(portal.destServer);
        if (portal.permission != null && !portal.permission.isEmpty())
            sb.append("  |  Perm: ").append(portal.permission);
        return Component.text(sb.toString(), NamedTextColor.GRAY);
    }

    private static String blankToNone(String s) {
        return (s == null || s.trim().isEmpty()) ? "none" : s.trim();
    }

    private static String shortLoc(org.bukkit.Location l) {
        return l.getWorld().getName() + " ("
                + (int) l.getX() + ", " + (int) l.getY() + ", " + (int) l.getZ() + ")";
    }

    private static int clamp(int offset, int total) {
        if (offset <= 0 || total <= 0) return 0;
        if (offset >= total) {
            return ((total - 1) / PAGE_SIZE) * PAGE_SIZE;
        }
        return offset;
    }

    private static String readText(DialogResponseView rv, String key, String def) {
        if (rv == null) return def;
        String s = rv.getText(key);
        return s == null ? def : s;
    }

    private Optional<Player> onlinePlayer(Audience audience) {
        return audience instanceof Player p && p.isOnline() ? Optional.of(p) : Optional.empty();
    }
}
