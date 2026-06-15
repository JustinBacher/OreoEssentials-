package fr.elias.oreoEssentials.modules.currency.gui;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.currency.Currency;
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

import java.util.List;
import java.util.Optional;

import static fr.elias.oreoEssentials.util.DialogButtons.button;
import static fr.elias.oreoEssentials.util.DialogButtons.list;
import static fr.elias.oreoEssentials.util.DialogButtons.multiAction;
import static fr.elias.oreoEssentials.util.DialogButtons.pagination;
import static fr.elias.oreoEssentials.util.DialogButtons.tooltip;

/**
 * Paper Dialog-API front-end for the currency admin GUIs. Mirrors
 * {@link CurrencyAdminGUI} / {@link CurrencyEditGUI} / {@link CurrencyCreateGUI}
 * (list, toggle flags, delete, create) but renders through the 1.21.6+ Dialog API.
 * Gated behind {@code display-mode: dialog} in {@code currency-config.yml}; the chest
 * GUIs remain the default.
 *
 * <p>The dialog form actually implements symbol/name editing and currency creation
 * with text inputs — the chest GUIs left these as "coming soon / use commands".
 */
public final class CurrencyDialogManager {

    private static final int PAGE_SIZE = 50;
    private static final int GRID_COLUMNS = 4;

    private final OreoEssentials plugin;

    public CurrencyDialogManager(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    public void openAdmin(Player player) {
        showList(player, 0);
    }

    // ── List ──────────────────────────────────────────────────────────────────

    private void showList(Audience a, int offset) {
        onlinePlayer(a).ifPresent(p -> p.showDialog(listDialog(p, offset)));
    }

    private Dialog listDialog(Player viewer, int offset) {
        List<Currency> all = plugin.getCurrencyService().getAllCurrencies();
        int total = all.size();
        int from = clamp(offset, total);
        List<Currency> page = all.subList(from, Math.min(from + PAGE_SIZE, total));

        var buttons = list()
                .add(button("Create Currency", NamedTextColor.GREEN)
                        .tip("Define a new currency")
                        .width(150)
                        .click((rv, a) -> showCreate(a)));
        if (total > PAGE_SIZE) {
            for (ActionButton b : pagination(from, PAGE_SIZE, total, newOff -> showList(viewer, newOff)).build()) {
                buttons.add(b);
            }
        }

        for (Currency c : page) {
            String id = c.getId();
            buttons.add(button(Component.text(c.getName() + " " + c.getSymbol(), NamedTextColor.YELLOW))
                    .tip(tooltip()
                            .line("ID: " + c.getId(), NamedTextColor.GRAY)
                            .line("Default: " + c.format(c.getDefaultBalance()), NamedTextColor.GRAY)
                            .line("Tradeable: " + (c.isTradeable() ? "yes" : "no"), NamedTextColor.GRAY)
                            .line("Cross-server: " + (c.isCrossServer() ? "yes" : "no"), NamedTextColor.GRAY)
                            .line("Click to edit", NamedTextColor.AQUA)
                            .build())
                    .click((rv, a) -> showEdit(a, id)));
        }

        ActionButton close = button("Close", NamedTextColor.RED)
                .tip("Close")
                .click((rv, a) -> onlinePlayer(a).ifPresent(Player::closeInventory))
                .build();

        List<DialogBody> body = total == 0
                ? List.of(DialogBody.plainMessage(
                        Component.text("No currencies defined yet.", NamedTextColor.GRAY)))
                : List.of(DialogBody.plainMessage(
                        Component.text("Total currencies: " + total, NamedTextColor.GRAY)));

        return multiAction(
                DialogBase.builder(Component.text("Currency Manager", NamedTextColor.GOLD))
                        .body(body)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons.build(), close, GRID_COLUMNS);
    }

    // ── Edit ──────────────────────────────────────────────────────────────────

    private void showEdit(Audience a, String currencyId) {
        onlinePlayer(a).ifPresent(p -> {
            Currency c = plugin.getCurrencyService().getCurrency(currencyId);
            if (c == null) { showList(p, 0); return; }
            p.showDialog(editDialog(c));
        });
    }

    private Dialog editDialog(Currency c) {
        String id = c.getId();

        var buttons = list()
                .add(button("Edit Name & Symbol", NamedTextColor.AQUA)
                        .tip("Change the display name and symbol")
                        .width(160)
                        .click((rv, a) -> showDetails(a, id)))
                .add(toggle("Tradeable", c.isTradeable(), id, b ->
                        copy(c).tradeable(b).build()))
                .add(toggle("Allow Negative", c.isAllowNegative(), id, b ->
                        copy(c).allowNegative(b).build()))
                .add(toggle("Whole Numbers", c.isWholeNumbers(), id, b ->
                        copy(c).wholeNumbers(b).build()))
                .add(button("Cross-Server: " + (c.isCrossServer() ? "ON" : "OFF"),
                                c.isCrossServer() ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                        .tip("Toggle cross-server sync (requires MongoDB)")
                        .width(150)
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                            if (!plugin.getCurrencyConfig().isCrossServerEnabled()) {
                                p.sendMessage("§c✖ Cross-server requires MongoDB! Enable it in currency-config.yml");
                                return;
                            }
                            Currency updated = copy(c).crossServer(!c.isCrossServer()).build();
                            plugin.getCurrencyService().updateCurrency(updated)
                                    .thenAccept(success -> runSync(() -> {
                                        if (!success) {
                                            p.sendMessage("§c✖ Failed to update cross-server setting.");
                                            return;
                                        }
                                        showEdit(p, id);
                                    }));
                        })))
                .add(button("Delete", NamedTextColor.RED)
                        .tip("Permanently delete this currency and all balances")
                        .width(120)
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p ->
                                p.showDialog(confirmDeleteDialog(id, c.getName())))));

        ActionButton back = button("Back", NamedTextColor.GRAY)
                .tip("Back to the currency list")
                .click((rv, a) -> showList(a, 0))
                .build();

        List<DialogBody> bodies = List.of(
                DialogBody.plainMessage(Component.text(c.getName() + " " + c.getSymbol(), NamedTextColor.GOLD)),
                DialogBody.plainMessage(Component.text("ID: " + c.getId()
                        + "  |  Default: " + c.format(c.getDefaultBalance()), NamedTextColor.GRAY)));

        return multiAction(
                DialogBase.builder(Component.text("Editing: " + c.getName(), NamedTextColor.GOLD))
                        .body(bodies)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons.build(), back, 2);
    }

    /** A boolean-flag toggle button that rebuilds the currency via {@code rebuild}. */
    private fr.elias.oreoEssentials.util.DialogButtons.Btn toggle(
            String label, boolean current, String id, java.util.function.Function<Boolean, Currency> rebuild) {
        return button(label + ": " + (current ? "ON" : "OFF"),
                        current ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                .tip("Click to toggle")
                .width(150)
                .click((rv, a) -> onlinePlayer(a).ifPresent(p ->
                        plugin.getCurrencyService().updateCurrency(rebuild.apply(!current))
                                .thenAccept(success -> runSync(() -> {
                                    if (!success) {
                                        p.sendMessage("§c✖ Failed to update currency.");
                                        return;
                                    }
                                    showEdit(p, id);
                                }))));
    }

    private Dialog detailsDialog(Currency c) {
        String id = c.getId();
        var nameInput = DialogInput.text("name", Component.text("Name"))
                .initial(c.getName()).maxLength(32).width(220).labelVisible(true).build();
        var symbolInput = DialogInput.text("symbol", Component.text("Symbol"))
                .initial(c.getSymbol()).maxLength(8).width(220).labelVisible(true).build();
        var displayInput = DialogInput.text("display", Component.text("Display name"))
                .initial(c.getDisplayName() == null ? "" : c.getDisplayName())
                .maxLength(48).width(220).labelVisible(true).build();
        var defaultInput = DialogInput.text("default", Component.text("Default balance"))
                .initial(String.valueOf(c.getDefaultBalance()))
                .maxLength(16).width(220).labelVisible(true).build();

        List<ActionButton> btns = list()
                .add(button("Save", NamedTextColor.GREEN)
                        .tip("Apply these changes")
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                            String name = readText(rv, "name", c.getName());
                            String symbol = readText(rv, "symbol", c.getSymbol());
                            String display = readText(rv, "display", c.getDisplayName());
                            double def = readDouble(rv, "default", c.getDefaultBalance());
                            Currency updated = copy(c)
                                    .name(name).symbol(symbol)
                                    .displayName(display == null || display.isEmpty() ? name : display)
                                    .defaultBalance(def).build();
                            plugin.getCurrencyService().updateCurrency(updated).thenAccept(success ->
                                    runSync(() -> {
                                        if (!success) {
                                            p.sendMessage("§c✖ Failed to update currency " + name);
                                            return;
                                        }
                                        p.sendMessage("§a✔ Updated currency " + name);
                                        showEdit(p, id);
                                    }));
                        })))
                .add(button("Back", NamedTextColor.GRAY)
                        .tip("Discard and go back")
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p ->
                                runSync(() -> showEdit(p, id)))))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Edit Currency", NamedTextColor.GOLD))
                        .inputs(List.of(nameInput, symbolInput, displayInput, defaultInput))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .build(),
                btns, null, 2);
    }

    private void showDetails(Audience a, String id) {
        onlinePlayer(a).ifPresent(p -> {
            Currency c = plugin.getCurrencyService().getCurrency(id);
            if (c == null) { showList(p, 0); return; }
            p.showDialog(detailsDialog(c));
        });
    }

    // ── Create ────────────────────────────────────────────────────────────────

    private void showCreate(Audience a) {
        onlinePlayer(a).ifPresent(p -> p.showDialog(createDialog()));
    }

    private Dialog createDialog() {
        var idInput = DialogInput.text("id", Component.text("ID (lowercase, no spaces)"))
                .maxLength(24).width(220).labelVisible(true).build();
        var nameInput = DialogInput.text("name", Component.text("Name"))
                .maxLength(32).width(220).labelVisible(true).build();
        var symbolInput = DialogInput.text("symbol", Component.text("Symbol"))
                .initial("$").maxLength(8).width(220).labelVisible(true).build();
        var defaultInput = DialogInput.text("default", Component.text("Default balance"))
                .initial("0").maxLength(16).width(220).labelVisible(true).build();

        List<ActionButton> btns = list()
                .add(button("Create", NamedTextColor.GREEN)
                        .tip("Create this currency")
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                            String id = readText(rv, "id", "").trim().toLowerCase().replace(" ", "_");
                            String name = readText(rv, "name", "").trim();
                            String symbol = readText(rv, "symbol", "$").trim();
                            double def = readDouble(rv, "default", 0);
                            if (id.isEmpty() || name.isEmpty()) {
                                p.sendMessage("§cID and name are required.");
                                runSync(() -> showCreate(p));
                                return;
                            }
                            if (plugin.getCurrencyService().getCurrency(id) != null) {
                                p.sendMessage("§cA currency with ID '" + id + "' already exists.");
                                runSync(() -> showCreate(p));
                                return;
                            }
                            Currency created = Currency.builder()
                                    .id(id).name(name).symbol(symbol)
                                    .displayName(name).defaultBalance(def)
                                    .tradeable(true).crossServer(false)
                                    .allowNegative(false).wholeNumbers(false).build();
                            plugin.getCurrencyService().createCurrency(created).thenAccept(success ->
                                    runSync(() -> {
                                        if (!success) {
                                            p.sendMessage("§c✖ Failed to create currency " + name + " (" + id + ")");
                                            showCreate(p);
                                            return;
                                        }
                                        p.sendMessage("§a✔ Created currency " + name + " (" + id + ")");
                                        showEdit(p, id);
                                    }));
                        })))
                .add(button("Back", NamedTextColor.GRAY)
                        .tip("Back to the list")
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p ->
                                runSync(() -> showList(p, 0)))))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Create Currency", NamedTextColor.GREEN))
                        .inputs(List.of(idInput, nameInput, symbolInput, defaultInput))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .build(),
                btns, null, 2);
    }

    // ── Delete confirm ────────────────────────────────────────────────────────

    private Dialog confirmDeleteDialog(String id, String name) {
        List<ActionButton> btns = list()
                .add(button("Yes, delete", NamedTextColor.RED)
                        .tip("Permanently delete this currency and ALL balances")
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p ->
                                plugin.getCurrencyService().deleteCurrency(id).thenAccept(success ->
                                        runSync(() -> {
                                            if (!success) {
                                                p.sendMessage("§c✖ Failed to delete currency: " + name);
                                                return;
                                            }
                                            p.sendMessage("§c✖ Deleted currency: " + name);
                                            showList(p, 0);
                                        })))))
                .add(button("Cancel", NamedTextColor.GRAY)
                        .tip("Keep this currency")
                        .click((rv, a) -> showEdit(a, id)))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Delete currency?", NamedTextColor.RED))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "This permanently deletes \"" + name + "\" and every balance. Irreversible.",
                                NamedTextColor.GRAY))))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                btns, null, 2);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Currency.Builder copy(Currency c) {
        return Currency.builder()
                .id(c.getId()).name(c.getName()).symbol(c.getSymbol())
                .displayName(c.getDisplayName()).defaultBalance(c.getDefaultBalance())
                .tradeable(c.isTradeable()).crossServer(c.isCrossServer())
                .allowNegative(c.isAllowNegative()).wholeNumbers(c.isWholeNumbers());
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

    private void runSync(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }
}
