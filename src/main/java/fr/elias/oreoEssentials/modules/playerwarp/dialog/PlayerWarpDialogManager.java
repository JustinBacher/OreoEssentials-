package fr.elias.oreoEssentials.modules.playerwarp.dialog;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.playerwarp.PlayerWarp;
import fr.elias.oreoEssentials.modules.playerwarp.PlayerWarpService;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.font.FontIconService;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static fr.elias.oreoEssentials.util.DialogButtons.button;
import static fr.elias.oreoEssentials.util.DialogButtons.list;
import static fr.elias.oreoEssentials.util.DialogButtons.multiAction;
import static fr.elias.oreoEssentials.util.DialogButtons.pagination;
import static fr.elias.oreoEssentials.util.DialogButtons.tooltip;

/**
 * Paper Dialog-API front-end for player warps. Mirrors the three chest GUIs
 * ({@link fr.elias.oreoEssentials.modules.playerwarp.gui.PlayerWarpBrowseMenu},
 * {@link fr.elias.oreoEssentials.modules.playerwarp.gui.MyPlayerWarpsMenu},
 * {@link fr.elias.oreoEssentials.modules.playerwarp.gui.PlayerWarpEditMenu}) but
 * renders through the 1.21.6+ Dialog API. Gated behind {@code display-mode: dialog}
 * in {@code playerwarps/config.yml}; the chest GUIs remain the default and untouched.
 *
 * <p>Reuses all of {@link PlayerWarpService} for teleport/save/delete/rename. The
 * password chat-prompt of the chest browser is replaced here by a proper text-input
 * field, and the command-driven rename/desc/category/cost edits become an inline
 * input form.
 */
public final class PlayerWarpDialogManager {

    private static final int PAGE_SIZE = 100;
    private static final int GRID_COLUMNS = 4;

    private final PlayerWarpService service;

    public PlayerWarpDialogManager(PlayerWarpService service) {
        this.service = service;
    }

    private OreoEssentials plugin() {
        return OreoEssentials.get();
    }

    // ── Entry points ──────────────────────────────────────────────────────────

    public void openBrowse(Player player) {
        showBrowse(player, 0);
    }

    public void openMyWarps(Player player) {
        showMyWarps(player, null, 0);
    }

    // ── Browse (all warps) → teleport ─────────────────────────────────────────

    private void showBrowse(Audience a, int offset) {
        onlinePlayer(a).ifPresent(p -> p.showDialog(browseDialog(p, offset)));
    }

    private Dialog browseDialog(Player viewer, int offset) {
        List<PlayerWarp> all = service.listAll().stream()
                .sorted(Comparator.comparing(PlayerWarp::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int total = all.size();
        int from = clamp(offset, total);
        List<PlayerWarp> page = all.subList(from, Math.min(from + PAGE_SIZE, total));

        var buttons = list()
                .add(button("My Warps", NamedTextColor.AQUA)
                        .tip("Manage your own warps")
                        .width(120)
                        .click((rv, a) -> showMyWarps(a, null, 0)))
                .add(button("Refresh", NamedTextColor.GREEN)
                        .tip("Reload the warp list")
                        .width(120)
                        .click((rv, a) -> showBrowse(a, from)));
        addPagination(buttons, from, total, newOff -> showBrowse(viewer, newOff));

        FontIconService fontIcons = plugin().getFontIconService();
        for (PlayerWarp warp : page) {
            Component icon = warpIcon(fontIcons, warp);
            UUID owner = warp.getOwner();
            String name = warp.getName();
            buttons.add(button(icon, Component.text(warp.getName(), NamedTextColor.GREEN))
                    .tip(browseTooltip(viewer, warp))
                    .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                        if (isPasswordProtectedFor(p, warp)) {
                            p.showDialog(passwordPromptDialog(name, from));
                        } else {
                            teleport(p, owner, name);
                        }
                    })));
        }

        ActionButton close = button("Close", NamedTextColor.RED)
                .tip("Close")
                .click((rv, a) -> onlinePlayer(a).ifPresent(Player::closeInventory))
                .build();

        List<DialogBody> body = total == 0
                ? List.of(DialogBody.plainMessage(
                        Component.text("There are no player warps yet.", NamedTextColor.GRAY)))
                : List.of();

        return multiAction(
                DialogBase.builder(Component.text("Player Warps", NamedTextColor.AQUA))
                        .body(body)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons.build(), close, GRID_COLUMNS);
    }

    private Dialog passwordPromptDialog(String warpName, int backOffset) {
        var pwInput = DialogInput.text("pw", Component.text("Password for " + warpName))
                .maxLength(64)
                .width(220)
                .labelVisible(true)
                .build();

        List<ActionButton> btns = list()
                .add(button("Teleport", NamedTextColor.GREEN)
                        .tip("Submit the password and teleport")
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                            String pw = readText(rv, "pw", "");
                            p.closeInventory();
                            // Reuse the /pw use password verification + teleport path.
                            p.performCommand("pw use " + warpName + " " + pw);
                        })))
                .add(button("Back", NamedTextColor.GRAY)
                        .tip("Back to the warp list")
                        .click((rv, a) -> showBrowse(a, backOffset)))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Locked Warp", NamedTextColor.GOLD))
                        .body(List.of(DialogBody.plainMessage(
                                Component.text("This warp is password protected.", NamedTextColor.GRAY))))
                        .inputs(List.of(pwInput))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .build(),
                btns, null, 2);
    }

    // ── My Warps (own warps + category filter) → edit ─────────────────────────

    private void showMyWarps(Audience a, String categoryFilter, int offset) {
        onlinePlayer(a).ifPresent(p -> p.showDialog(myWarpsDialog(p, categoryFilter, offset)));
    }

    private Dialog myWarpsDialog(Player owner, String categoryFilter, int offset) {
        List<PlayerWarp> mine = service.listByOwner(owner.getUniqueId());
        var categories = mine.stream()
                .map(PlayerWarp::getCategory)
                .filter(c -> c != null && !c.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        List<PlayerWarp> filtered = (categoryFilter == null || categoryFilter.isEmpty())
                ? mine
                : mine.stream()
                        .filter(w -> categoryFilter.equalsIgnoreCase(
                                w.getCategory() == null ? "" : w.getCategory()))
                        .collect(Collectors.toList());
        filtered = filtered.stream()
                .sorted(Comparator.comparing(PlayerWarp::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int total = filtered.size();
        int from = clamp(offset, total);
        List<PlayerWarp> page = filtered.subList(from, Math.min(from + PAGE_SIZE, total));

        var buttons = list()
                .add(button("Back", NamedTextColor.GRAY)
                        .tip("Back to all warps")
                        .width(110)
                        .click((rv, a) -> showBrowse(a, 0)))
                .add(button("All", categoryFilter == null ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
                        .tip("Show every category")
                        .width(90)
                        .click((rv, a) -> showMyWarps(a, null, 0)));
        for (String cat : categories) {
            boolean selected = cat.equalsIgnoreCase(categoryFilter);
            buttons.add(button(cat, selected ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
                    .tip(selected ? "Currently selected" : "Filter by this category")
                    .width(90)
                    .click((rv, a) -> showMyWarps(a, cat, 0)));
        }
        addPagination(buttons, from, total, newOff -> showMyWarps(owner, categoryFilter, newOff));

        FontIconService fontIcons = plugin().getFontIconService();
        for (PlayerWarp warp : page) {
            Component icon = warpIcon(fontIcons, warp);
            String id = warp.getId();
            String cf = categoryFilter;
            buttons.add(button(icon, Component.text(warp.getName(), NamedTextColor.GREEN))
                    .tip(myWarpTooltip(warp))
                    .click((rv, a) -> showEdit(a, id, cf)));
        }

        ActionButton close = button("Close", NamedTextColor.RED)
                .tip("Close")
                .click((rv, a) -> onlinePlayer(a).ifPresent(Player::closeInventory))
                .build();

        List<DialogBody> body = total == 0
                ? List.of(DialogBody.plainMessage(
                        Component.text("You have no warps in this view.", NamedTextColor.GRAY)))
                : List.of();

        return multiAction(
                DialogBase.builder(Component.text("My Warps", NamedTextColor.AQUA))
                        .body(body)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons.build(), close, GRID_COLUMNS);
    }

    // ── Edit a single warp ────────────────────────────────────────────────────

    private void showEdit(Audience a, String warpId, String categoryFilter) {
        onlinePlayer(a).ifPresent(p -> {
            PlayerWarp warp = findById(p.getUniqueId(), warpId);
            if (warp == null) {
                showMyWarps(p, categoryFilter, 0);
                return;
            }
            p.showDialog(editDialog(warp, categoryFilter));
        });
    }

    private Dialog editDialog(PlayerWarp warp, String categoryFilter) {
        String id = warp.getId();
        UUID owner = warp.getOwner();
        String name = warp.getName();
        boolean locked = warp.isLocked();
        boolean wl = warp.isWhitelistEnabled();
        boolean hasPwd = warp.getPassword() != null && !warp.getPassword().isEmpty();

        OfflinePlayer ownerOff = Bukkit.getOfflinePlayer(owner);
        String ownerName = ownerOff.getName() != null ? ownerOff.getName() : owner.toString();

        var info = tooltip()
                .line("Owner: " + ownerName, NamedTextColor.GRAY)
                .lineIf(warp.getCategory() != null && !warp.getCategory().isEmpty(),
                        "Category: " + warp.getCategory(), NamedTextColor.AQUA)
                .line("Cost: " + (warp.getCost() > 0 ? String.valueOf(warp.getCost()) : "free"),
                        NamedTextColor.YELLOW)
                .line("Locked: " + (locked ? "yes" : "no"), locked ? NamedTextColor.RED : NamedTextColor.GREEN)
                .line("Whitelist: " + (wl ? "enabled" : "disabled"), wl ? NamedTextColor.YELLOW : NamedTextColor.GRAY)
                .lineIf(hasPwd, "Password: enabled", NamedTextColor.GOLD);

        List<DialogBody> bodies = List.of(
                DialogBody.plainMessage(Component.text("Warp: " + name, NamedTextColor.GREEN)),
                DialogBody.plainMessage(info.build()));

        var buttons = list()
                .add(button("Teleport", NamedTextColor.GREEN)
                        .tip("Teleport to this warp")
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                            p.closeInventory();
                            teleport(p, owner, name);
                        })))
                .add(button("Reset Location", NamedTextColor.YELLOW)
                        .tip("Move this warp to where you stand")
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                            PlayerWarp w = findById(owner, id);
                            if (w == null) { showMyWarps(p, categoryFilter, 0); return; }
                            w.setLocation(p.getLocation().clone());
                            service.saveWarp(w);
                            Lang.send(p, "pw.reset-success",
                                    "<green>Reset warp <aqua>%name%</aqua> to your current location.</green>",
                                    Map.of("name", name));
                            showEdit(p, id, categoryFilter);
                        })))
                .add(button(locked ? "Unlock" : "Lock", locked ? NamedTextColor.GREEN : NamedTextColor.RED)
                        .tip("Toggle the locked state")
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                            PlayerWarp w = findById(owner, id);
                            if (w == null) { showMyWarps(p, categoryFilter, 0); return; }
                            boolean ns = !w.isLocked();
                            w.setLocked(ns);
                            service.saveWarp(w);
                            Lang.send(p, ns ? "pw.locked" : "pw.unlocked",
                                    ns ? "<green>Locked warp <aqua>%warp%</aqua>.</green>"
                                       : "<green>Unlocked warp <aqua>%warp%</aqua>.</green>",
                                    Map.of("warp", name));
                            showEdit(p, id, categoryFilter);
                        })))
                .add(button(wl ? "Disable Whitelist" : "Enable Whitelist",
                                wl ? NamedTextColor.RED : NamedTextColor.GREEN)
                        .tip("Toggle the warp whitelist")
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                            PlayerWarp w = findById(owner, id);
                            if (w == null) { showMyWarps(p, categoryFilter, 0); return; }
                            boolean ns = !w.isWhitelistEnabled();
                            w.setWhitelistEnabled(ns);
                            service.saveWarp(w);
                            Lang.send(p, ns ? "pw.whitelist-enabled" : "pw.whitelist-disabled",
                                    ns ? "<green>Whitelist enabled for <white>%warp%</white>.</green>"
                                       : "<yellow>Whitelist disabled for <white>%warp%</white>.</yellow>",
                                    Map.of("warp", name));
                            showEdit(p, id, categoryFilter);
                        })))
                .add(button("Set Icon", NamedTextColor.YELLOW)
                        .tip("Use the item in your hand as the icon")
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                            PlayerWarp w = findById(owner, id);
                            if (w == null) { showMyWarps(p, categoryFilter, 0); return; }
                            var hand = p.getInventory().getItemInMainHand();
                            if (hand == null || hand.getType().isAir()) {
                                Lang.send(p, "pw.icon-no-item",
                                        "<red>You must hold an item in your main hand.</red>", Map.of());
                            } else {
                                w.setIcon(hand.clone());
                                service.saveWarp(w);
                                Lang.send(p, "pw.icon-set",
                                        "<green>Set icon for <aqua>%warp%</aqua>.</green>",
                                        Map.of("warp", name));
                            }
                            showEdit(p, id, categoryFilter);
                        })))
                .add(button("Edit Details", NamedTextColor.AQUA)
                        .tip("Rename / description / category / cost")
                        .click((rv, a) -> showDetails(a, id, categoryFilter)))
                .add(button(hasPwd ? "Clear Password" : "Set Password",
                                hasPwd ? NamedTextColor.RED : NamedTextColor.GOLD)
                        .tip("Manage the warp password")
                        .click((rv, a) -> showPassword(a, id, categoryFilter)))
                .add(button("Delete", NamedTextColor.RED)
                        .tip("Permanently delete this warp")
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p -> p.showDialog(
                                confirmDeleteDialog(id, name, categoryFilter)))));

        ActionButton back = button("Back", NamedTextColor.GRAY)
                .tip("Back to my warps")
                .click((rv, a) -> showMyWarps(a, categoryFilter, 0))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Edit: " + name, NamedTextColor.AQUA))
                        .body(bodies)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons.build(), back, 2);
    }

    // ── Edit details (rename / desc / category / cost as inputs) ──────────────

    private void showDetails(Audience a, String warpId, String categoryFilter) {
        onlinePlayer(a).ifPresent(p -> {
            PlayerWarp warp = findById(p.getUniqueId(), warpId);
            if (warp == null) { showMyWarps(p, categoryFilter, 0); return; }
            p.showDialog(detailsDialog(warp, categoryFilter));
        });
    }

    private Dialog detailsDialog(PlayerWarp warp, String categoryFilter) {
        String id = warp.getId();
        UUID owner = warp.getOwner();
        String name = warp.getName();

        var nameInput = DialogInput.text("name", Component.text("Name"))
                .initial(name).maxLength(32).width(220).labelVisible(true).build();
        var descInput = DialogInput.text("desc", Component.text("Description"))
                .initial(warp.getDescription() == null ? "" : warp.getDescription())
                .maxLength(128).width(220).labelVisible(true).build();
        var catInput = DialogInput.text("cat", Component.text("Category"))
                .initial(warp.getCategory() == null ? "" : warp.getCategory())
                .maxLength(32).width(220).labelVisible(true).build();
        var costInput = DialogInput.text("cost", Component.text("Cost"))
                .initial(String.valueOf(warp.getCost()))
                .maxLength(12).width(220).labelVisible(true).build();

        List<ActionButton> btns = list()
                .add(button("Save", NamedTextColor.GREEN)
                        .tip("Apply these changes")
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                            PlayerWarp w = findById(owner, id);
                            if (w == null) { showMyWarps(p, categoryFilter, 0); return; }

                            String desc = readText(rv, "desc", w.getDescription());
                            String cat = readText(rv, "cat", w.getCategory());
                            double cost = readDouble(rv, "cost", w.getCost());
                            w.setDescription(desc == null || desc.isEmpty() ? null : desc);
                            w.setCategory(cat == null ? "" : cat.trim());
                            w.setCost(Math.max(0, cost));
                            service.saveWarp(w);

                            String newName = readText(rv, "name", name).trim();
                            String targetId = id;
                            if (!newName.isEmpty() && !newName.equalsIgnoreCase(name)) {
                                PlayerWarp renamed = service.renameWarp(w, newName);
                                if (renamed == null) {
                                    Lang.send(p, "pw.rename-exists",
                                            "<red>A warp named <yellow>%name%</yellow> already exists.</red>",
                                            Map.of("name", newName));
                                } else {
                                    targetId = renamed.getId();
                                    Lang.send(p, "pw.details-saved",
                                            "<green>Updated warp <aqua>%name%</aqua>.</green>",
                                            Map.of("name", newName));
                                }
                            } else {
                                Lang.send(p, "pw.details-saved",
                                        "<green>Updated warp <aqua>%name%</aqua>.</green>",
                                        Map.of("name", name));
                            }
                            showEdit(p, targetId, categoryFilter);
                        })))
                .add(button("Back", NamedTextColor.GRAY)
                        .tip("Discard and go back")
                        .click((rv, a) -> showEdit(a, id, categoryFilter)))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Edit Details", NamedTextColor.AQUA))
                        .body(List.of(DialogBody.plainMessage(
                                Component.text("Blank description/category clears it.", NamedTextColor.GRAY))))
                        .inputs(List.of(nameInput, descInput, catInput, costInput))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .build(),
                btns, null, 2);
    }

    // ── Password management (as input) ────────────────────────────────────────

    private void showPassword(Audience a, String warpId, String categoryFilter) {
        onlinePlayer(a).ifPresent(p -> {
            PlayerWarp warp = findById(p.getUniqueId(), warpId);
            if (warp == null) { showMyWarps(p, categoryFilter, 0); return; }
            p.showDialog(passwordEditDialog(warp, categoryFilter));
        });
    }

    private Dialog passwordEditDialog(PlayerWarp warp, String categoryFilter) {
        String id = warp.getId();
        UUID owner = warp.getOwner();
        boolean hasPwd = warp.getPassword() != null && !warp.getPassword().isEmpty();

        var pwInput = DialogInput.text("pw", Component.text("New password"))
                .maxLength(64).width(220).labelVisible(true).build();

        var buttons = list()
                .add(button("Set Password", NamedTextColor.GREEN)
                        .tip("Set or change the password")
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                            PlayerWarp w = findById(owner, id);
                            if (w == null) { showMyWarps(p, categoryFilter, 0); return; }
                            String pw = readText(rv, "pw", "").trim();
                            if (pw.isEmpty()) {
                                Lang.send(p, "pw.password-empty",
                                        "<red>Enter a password to set.</red>", Map.of());
                                showPassword(p, id, categoryFilter);
                                return;
                            }
                            w.setPassword(pw);
                            service.saveWarp(w);
                            Lang.send(p, "pw.password-placeholder-set",
                                    "<green>Set password for <aqua>%warp%</aqua>.</green>",
                                    Map.of("warp", w.getName(), "password", pw));
                            showEdit(p, id, categoryFilter);
                        })));
        if (hasPwd) {
            buttons.add(button("Clear Password", NamedTextColor.RED)
                    .tip("Remove password protection")
                    .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                        PlayerWarp w = findById(owner, id);
                        if (w == null) { showMyWarps(p, categoryFilter, 0); return; }
                        w.setPassword(null);
                        service.saveWarp(w);
                        Lang.send(p, "pw.password-cleared",
                                "<green>Cleared password for <aqua>%warp%</aqua>.</green>",
                                Map.of("warp", w.getName()));
                        showEdit(p, id, categoryFilter);
                    })));
        }
        buttons.add(button("Back", NamedTextColor.GRAY)
                .tip("Back to edit")
                .click((rv, a) -> showEdit(a, id, categoryFilter)));

        return multiAction(
                DialogBase.builder(Component.text("Warp Password", NamedTextColor.GOLD))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                hasPwd ? "This warp is currently password protected."
                                       : "This warp has no password.", NamedTextColor.GRAY))))
                        .inputs(List.of(pwInput))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .build(),
                buttons.build(), null, 2);
    }

    // ── Delete confirm ────────────────────────────────────────────────────────

    private Dialog confirmDeleteDialog(String warpId, String name, String categoryFilter) {
        List<ActionButton> btns = list()
                .add(button("Yes, delete", NamedTextColor.RED)
                        .tip("Permanently delete this warp")
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                            PlayerWarp w = findById(p.getUniqueId(), warpId);
                            if (w != null) {
                                service.deleteWarp(w);
                                Lang.send(p, "pw.remove-success",
                                        "<green>Removed warp <aqua>%name%</aqua>.</green>",
                                        Map.of("name", name));
                            }
                            showMyWarps(p, categoryFilter, 0);
                        })))
                .add(button("Cancel", NamedTextColor.GRAY)
                        .tip("Keep this warp")
                        .click((rv, a) -> showEdit(a, warpId, categoryFilter)))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Delete warp?", NamedTextColor.GOLD))
                        .body(List.of(DialogBody.plainMessage(
                                Component.text("Delete \"" + name + "\" permanently?", NamedTextColor.GRAY))))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                btns, null, 2);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void teleport(Player p, UUID owner, String name) {
        boolean ok = service.teleportToPlayerWarp(p, owner, name);
        if (!ok) {
            Lang.send(p, "pw.teleport-failed",
                    "<red>Teleportation failed.</red>", Map.of("error", "unknown"));
        }
    }

    private boolean isPasswordProtectedFor(Player viewer, PlayerWarp warp) {
        String pwd = warp.getPassword();
        if (pwd == null || pwd.isEmpty()) return false;
        UUID uuid = viewer.getUniqueId();
        if (warp.getOwner().equals(uuid)) return false;
        if (warp.getManagers() != null && warp.getManagers().contains(uuid)) return false;
        if (viewer.hasPermission("oe.pw.bypass.password")) return false;
        return true;
    }

    private PlayerWarp findById(UUID owner, String warpId) {
        return service.listByOwner(owner).stream()
                .filter(w -> w.getId().equals(warpId))
                .findFirst()
                .orElseGet(() -> service.listAll().stream()
                        .filter(w -> w.getId().equals(warpId))
                        .findFirst().orElse(null));
    }

    private Component warpIcon(FontIconService fontIcons, PlayerWarp warp) {
        if (fontIcons == null) return Component.empty();
        Material mat = warp.getIcon() != null ? warp.getIcon().getType() : Material.ENDER_PEARL;
        return fontIcons.icon(mat);
    }

    private Component browseTooltip(Player viewer, PlayerWarp warp) {
        OfflinePlayer ownerOff = Bukkit.getOfflinePlayer(warp.getOwner());
        String ownerName = ownerOff.getName() != null ? ownerOff.getName() : warp.getOwner().toString();
        var tip = tooltip()
                .line("Owner: " + ownerName, NamedTextColor.GRAY)
                .lineIf(warp.getCategory() != null && !warp.getCategory().isEmpty(),
                        "Category: " + warp.getCategory(), NamedTextColor.AQUA)
                .lineIf(warp.getCost() > 0, "Cost: " + warp.getCost(), NamedTextColor.YELLOW);
        if (warp.isLocked()) tip.line("Status: Locked", NamedTextColor.RED);
        else if (warp.isWhitelistEnabled()) tip.line("Status: Whitelist", NamedTextColor.YELLOW);
        else tip.line("Status: Public", NamedTextColor.GREEN);
        if (warp.getPassword() != null && !warp.getPassword().isEmpty()) {
            tip.line("Password protected", NamedTextColor.GOLD);
        }
        tip.line("Click to teleport", NamedTextColor.GRAY);
        return tip.build();
    }

    private Component myWarpTooltip(PlayerWarp warp) {
        var tip = tooltip()
                .lineIf(warp.getCategory() != null && !warp.getCategory().isEmpty(),
                        "Category: " + warp.getCategory(), NamedTextColor.AQUA)
                .lineIf(warp.getCost() > 0, "Cost: " + warp.getCost(), NamedTextColor.YELLOW);
        if (warp.isLocked()) tip.line("Status: Locked", NamedTextColor.RED);
        else if (warp.isWhitelistEnabled()) tip.line("Status: Whitelist", NamedTextColor.YELLOW);
        else tip.line("Status: Public", NamedTextColor.GREEN);
        tip.line("Click to edit", NamedTextColor.GRAY);
        return tip.build();
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

    private static String readText(DialogResponseView rv, String key, String def) {
        if (rv == null) return def;
        String s = rv.getText(key);
        return s == null ? def : s;
    }

    private static double readDouble(DialogResponseView rv, String key, double def) {
        if (rv == null) return def;
        String s = rv.getText(key);
        if (s == null) return def;
        try {
            return Double.parseDouble(s.strip().replace(",", "."));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private Optional<Player> onlinePlayer(Audience audience) {
        return audience instanceof Player p && p.isOnline() ? Optional.of(p) : Optional.empty();
    }
}
