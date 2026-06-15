package fr.elias.oreoEssentials.modules.orders.dialog;

import fr.elias.oreoEssentials.modules.currency.Currency;
import fr.elias.oreoEssentials.modules.currency.CurrencyService;
import fr.elias.oreoEssentials.modules.orders.OrdersModule;
import fr.elias.oreoEssentials.modules.orders.gui.CreateOrderFlow;
import fr.elias.oreoEssentials.modules.orders.model.FillResult;
import fr.elias.oreoEssentials.modules.orders.model.Order;
import fr.elias.oreoEssentials.modules.orders.service.OrderService;
import fr.elias.oreoEssentials.util.OreScheduler;
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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static fr.elias.oreoEssentials.util.DialogButtons.button;
import static fr.elias.oreoEssentials.util.DialogButtons.list;
import static fr.elias.oreoEssentials.util.DialogButtons.multiAction;
import static fr.elias.oreoEssentials.util.DialogButtons.pagination;

/**
 * Paper Dialog-API front-end for the Orders/Market module. Mirrors the
 * chest-GUI
 * flow (browse → fill, my-orders → cancel, create) but renders through the
 * 1.21.6+ Dialog API. Gated behind {@code display-mode: dialog}; the chest GUI
 * remains the default and untouched.
 *
 * <p>
 * All escrow/fill/cancel logic is reused from {@link OrderService}
 * ({@code createOrder}, {@code fillOrder}, {@code cancelOrder}). Filling an
 * order
 * already performs the required main-thread atomic item take + rollback inside
 * the
 * service, so this class only builds dialogs and routes clicks.
 *
 * <p>
 * <b>Parity note:</b> unlike the chest GUI, open dialogs are <i>not</i> live
 * cross-server refreshed — there is no push into an open dialog. Players use
 * the
 * explicit <i>Refresh</i> button to re-pull the latest orders.
 */
public final class OrderDialogManager {

    /**
     * Browse/list dialogs paginate at this many entries per page (Goal&nbsp;1
     * helper).
     */
    private static final int PAGE_SIZE = 350;
    private static final int GRID_COLUMNS = 4;

    public enum OrderSortField {
        PRICE, NAME, REMAINING, NEWEST
    }

    private final OrdersModule module;

    public OrderDialogManager(OrdersModule module) {
        this.module = module;
    }

    // ── Entry points ──────────────────────────────────────────────────────────

    public void openBrowse(Player player) {
        showBrowse(player, 0, OrderSortField.NEWEST, false, "");
    }

    public void openManage(Player player) {
        showManage(player, 0);
    }

    public void openCreate(Player player) {
        CreateOrderFlow.openItemStep(module, player);
    }

    // ── Browse (all orders except the viewer's own) → fill ────────────────────

    private Dialog browseDialog(Player viewer, int offset, OrderSortField sort, boolean asc, String search) {
        Comparator<Order> baseCmp = switch (sort) {
            case PRICE -> Comparator.comparingDouble(Order::getUnitPrice);
            case NAME -> Comparator.comparing(Order::getDisplayItemName);
            case REMAINING -> Comparator.comparingInt(Order::getRemainingQty);
            case NEWEST -> Comparator.comparingLong(Order::getCreatedAt);
        };
        Comparator<Order> cmp = asc ? baseCmp : baseCmp.reversed();
        String searchLower = search.toLowerCase();

        List<Order> all = module.getService().getActiveOrders().stream()
                .filter(o -> !o.getRequesterUuid().equals(viewer.getUniqueId()))
                .filter(o -> o.getRemainingQty() > 0)
                .filter(o -> search.isEmpty() || o.getDisplayItemName().toLowerCase().contains(searchLower))
                .sorted(cmp)
                .toList();
        int total = all.size();
        int from = clamp(offset, total);
        List<Order> page = all.subList(from, Math.min(from + PAGE_SIZE, total));
        boolean hasPrev = from > 0;
        boolean hasNext = from + PAGE_SIZE < total;
        int currentPage = total == 0 ? 1 : (from / PAGE_SIZE) + 1;
        int totalPages = total == 0 ? 1 : (total + PAGE_SIZE - 1) / PAGE_SIZE;

        OrderSortField nextSort = switch (sort) {
            case PRICE -> OrderSortField.NAME;
            case NAME -> OrderSortField.REMAINING;
            case REMAINING -> OrderSortField.NEWEST;
            case NEWEST -> OrderSortField.PRICE;
        };
        String sortLabel = switch (sort) {
            case PRICE -> "Price";
            case NAME -> "Name";
            case REMAINING -> "Remaining";
            case NEWEST -> "Newest";
        };

        var buttons = list()
                .add(button("My Orders", NamedTextColor.AQUA)
                        .tip("View, cancel and create your own orders")
                        .width(120)
                        .click((rv, a) -> showManage(a, 0)))
                .add(button("Refresh", NamedTextColor.GREEN)
                        .tip("Reload the latest orders")
                        .width(120)
                        .click((rv, a) -> showBrowse(a, from, sort, asc, search)))
                .add(button("Sort: " + sortLabel, NamedTextColor.YELLOW)
                        .tip("Click to change sort field")
                        .width(110)
                        .click((rv, a) -> showBrowse(a, 0, nextSort, asc, search)))
                .add(button(asc ? "▲ Asc" : "▼ Desc", NamedTextColor.YELLOW)
                        .tip("Click to reverse order")
                        .width(80)
                        .click((rv, a) -> showBrowse(a, from, sort, !asc, search)))
                .add(button("Search", search.isEmpty() ? NamedTextColor.WHITE : NamedTextColor.GOLD)
                        .tip(search.isEmpty() ? "Filter orders by item name" : "Active filter: " + search)
                        .width(90)
                        .click((rv, a) -> showSearch(a, search, sort, asc)))
                .add(button("◀", hasPrev ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY)
                        .tip(hasPrev ? "Previous page" : "No previous page")
                        .width(60)
                        .click((rv, a) -> {
                            if (hasPrev)
                                showBrowse(a, from - PAGE_SIZE, sort, asc, search);
                        }))
                .add(button("Page " + currentPage + "/" + totalPages, NamedTextColor.GRAY)
                        .tip(totalPages > 1 ? "Click to jump to a specific page" : "Page 1 of 1")
                        .width(100)
                        .click((rv, a) -> {
                            if (totalPages > 1)
                                showGotoPage(a, currentPage, totalPages, sort, asc, search);
                        }))
                .add(button("▶", hasNext ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY)
                        .tip(hasNext ? "Next page" : "No next page")
                        .width(60)
                        .click((rv, a) -> {
                            if (hasNext)
                                showBrowse(a, from + PAGE_SIZE, sort, asc, search);
                        }));

        FontIconService fontIcons = module.getPlugin().getFontIconService();
        for (Order o : page) {
            ItemStack tpl = OrderService.deserializeItem(o.getItemData());
            Component icon = (fontIcons != null && tpl != null) ? fontIcons.icon(tpl.getType()) : Component.empty();
            String id = o.getId();
            int backOffset = from;
            buttons.add(button(icon, orderName(o, tpl))
                    .tip(orderTooltip(o))
                    .click((rv, a) -> showFill(a, id, backOffset, sort, asc, search)));
        }

        ActionButton close = button("Close", NamedTextColor.RED)
                .tip("Close the market")
                .click((rv, a) -> onlinePlayer(a).ifPresent(Player::closeInventory))
                .build();

        List<DialogBody> body = total == 0
                ? List.of(DialogBody.plainMessage(
                        Component.text("No orders available right now.", NamedTextColor.GRAY)))
                : List.of();

        return multiAction(
                DialogBase.builder(Component.text("Market Orders", NamedTextColor.GOLD))
                        .body(body)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons.build(), close, GRID_COLUMNS);
    }

    private Dialog fillDialog(Order o, int backOffset, OrderSortField sort, boolean asc, String search) {
        ItemStack tpl = OrderService.deserializeItem(o.getItemData());
        int remaining = o.getRemainingQty();

        List<DialogBody> bodies = new ArrayList<>();
        if (tpl != null)
            bodies.add(DialogBody.item(tpl).showTooltip(true).build());
        bodies.add(
                DialogBody.plainMessage(Component.text("Requested by: " + o.getRequesterName(), NamedTextColor.GRAY)));
        bodies.add(DialogBody.plainMessage(Component.text("Remaining: " + remaining, NamedTextColor.GRAY)));
        bodies.add(DialogBody.plainMessage(Component.text(
                "Unit price: " + module.getCurrency().format(o.getCurrencyId(), o.getUnitPrice()),
                NamedTextColor.GREEN)));

        var qtyInput = DialogInput.text("qty", Component.text("Deliver amount (max " + remaining + ")"))
                .initial(String.valueOf(remaining))
                .maxLength(9)
                .width(200)
                .labelVisible(true)
                .build();

        String id = o.getId();
        List<ActionButton> btns = list()
                .add(button("Confirm Fill", NamedTextColor.GREEN)
                        .tip("Deliver items and get paid")
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                            int qty = clampQty(readInt(rv, "qty", remaining), remaining);
                            p.closeInventory();
                            module.getService().fillOrder(p, id, qty)
                                    .thenAccept(result -> handleFillResult(p, o, qty, result));
                        })))
                .add(button("Back", NamedTextColor.GRAY)
                        .tip("Back to orders")
                        .click((rv, a) -> showBrowse(a, backOffset, sort, asc, search)))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Fill Order", NamedTextColor.GOLD))
                        .body(bodies)
                        .inputs(List.of(qtyInput))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .build(),
                btns, null, 2);
    }

    private void handleFillResult(Player p, Order o, int qty, FillResult result) {
        switch (result.getOutcome()) {
            case SUCCESS -> {
                double gross = result.getPaidToSeller();
                double fee = module.getConfig().feesEnabled()
                        ? gross * module.getConfig().fillFeePercent() / 100.0
                        : 0.0;
                double net = gross - fee;
                p.sendMessage(module.getConfig().msg("fill.success",
                        Map.of("qty", String.valueOf(result.getFilledQty()),
                                "item", o.getDisplayItemName(),
                                "paid", module.getCurrency().format(o.getCurrencyId(), net))));
            }
            case NOT_FOUND -> p.sendMessage(module.getConfig().msg("fill.order-gone"));
            case ALREADY_CLOSED -> p.sendMessage(module.getConfig().msg("fill.already-closed"));
            case INSUFFICIENT_QTY -> p.sendMessage(module.getConfig().msg("fill.stale-qty"));
            case ERROR -> p.sendMessage(module.getConfig().msg("fill.error"));
        }
    }

    // ── Manage (own orders + cancel + create) ─────────────────────────────────

    private Dialog manageDialog(Player owner, int offset) {
        List<Order> mine = module.getService().getActiveOrdersByPlayer(owner.getUniqueId());
        int total = mine.size();
        int from = clamp(offset, total);
        List<Order> page = mine.subList(from, Math.min(from + PAGE_SIZE, total));

        var buttons = list()
                .add(button("Create Order", NamedTextColor.GREEN)
                        .tip("Request an item from the market")
                        .width(120)
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p -> CreateOrderFlow.openItemStep(module, p))))
                .add(button("Refresh", NamedTextColor.GREEN)
                        .tip("Reload your orders")
                        .width(120)
                        .click((rv, a) -> showManage(a, from)))
                .add(button("Back", NamedTextColor.GRAY)
                        .tip("Back to the market")
                        .width(120)
                        .click((rv, a) -> showBrowse(a, 0, OrderSortField.NEWEST, false, "")));
        addPagination(buttons, from, total, newOff -> showManage(owner, newOff));

        FontIconService fontIcons = module.getPlugin().getFontIconService();
        for (Order o : page) {
            ItemStack tpl = OrderService.deserializeItem(o.getItemData());
            Component icon = (fontIcons != null && tpl != null) ? fontIcons.icon(tpl.getType()) : Component.empty();
            String id = o.getId();
            int backOffset = from;
            buttons.add(button(icon, orderName(o, tpl))
                    .tip(orderTooltip(o).append(Component.newline())
                            .append(Component.text("Click to cancel (refunds escrow)", NamedTextColor.RED)))
                    .click((rv, a) -> onlinePlayer(a).ifPresent(p -> cancel(p, id, backOffset))));
        }

        ActionButton close = button("Close", NamedTextColor.RED)
                .tip("Close")
                .click((rv, a) -> onlinePlayer(a).ifPresent(Player::closeInventory))
                .build();

        List<DialogBody> body = total == 0
                ? List.of(DialogBody.plainMessage(
                        Component.text("You have no active orders.", NamedTextColor.GRAY)))
                : List.of();

        return multiAction(
                DialogBase.builder(Component.text("My Orders", NamedTextColor.GOLD))
                        .body(body)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons.build(), close, GRID_COLUMNS);
    }

    private void cancel(Player p, String orderId, int backOffset) {
        double refund = module.getService().findOrder(orderId)
                .map(Order::getEscrowRemaining).orElse(0.0);
        String currencyId = module.getService().findOrder(orderId)
                .map(Order::getCurrencyId).orElse(null);
        module.getService().cancelOrder(p, orderId).thenAccept(ok -> {
            if (ok) {
                p.sendMessage(module.getConfig().msg("cancel.success",
                        Map.of("refund", module.getCurrency().format(currencyId, refund))));
            } else {
                p.sendMessage(module.getConfig().msg("cancel.failed"));
            }
            OreScheduler.run(module.getPlugin(), () -> showManage(p, backOffset));
        });
    }

    // ── Create flow: pick item → qty/price → currency → confirm ───────────────

    private Dialog pickItemDialog(Player player, int offset) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack it : player.getInventory().getContents()) {
            if (it != null && !it.getType().isAir()) {
                items.add(it.clone());
            }
        }
        int total = items.size();
        int from = clamp(offset, total);
        int to = Math.min(from + PAGE_SIZE, total);

        var buttons = list()
                .add(button("Back", NamedTextColor.GRAY)
                        .tip("Back to your orders")
                        .width(120)
                        .click((rv, a) -> showManage(a, 0)));
        addPagination(buttons, from, total, newOff -> showPickItem(player, newOff));

        FontIconService fontIcons = module.getPlugin().getFontIconService();
        for (int i = from; i < to; i++) {
            ItemStack it = items.get(i);
            Component icon = fontIcons != null ? fontIcons.icon(it.getType()) : Component.empty();
            buttons.add(button(icon, itemName(it))
                    .tip(Component.text("Request this item", NamedTextColor.GRAY))
                    .click((rv, a) -> showDetails(a, it)));
        }

        List<DialogBody> body = total == 0
                ? List.of(DialogBody.plainMessage(
                        Component.text("Hold or carry the item you want to request.", NamedTextColor.GRAY)))
                : List.of(DialogBody.plainMessage(
                        Component.text("Pick the item you want to buy.", NamedTextColor.GRAY)));

        return multiAction(
                DialogBase.builder(Component.text("Select an Item", NamedTextColor.GOLD))
                        .body(body)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons.build(), null, GRID_COLUMNS);
    }

    private Dialog detailsDialog(ItemStack template) {
        int maxQty = module.getConfig().maxQtyPerOrder();
        var qtyInput = DialogInput.text("qty", Component.text("Quantity (max " + maxQty + ")"))
                .initial("1")
                .maxLength(9)
                .width(200)
                .labelVisible(true)
                .build();
        var priceInput = DialogInput.text("price", Component.text("Unit price"))
                .initial("")
                .maxLength(16)
                .width(200)
                .labelVisible(true)
                .build();

        List<ActionButton> btns = list()
                .add(button("Next", NamedTextColor.GREEN)
                        .tip("Choose a currency")
                        .click((rv, a) -> {
                            int qty = readInt(rv, "qty", 0);
                            double price = readDouble(rv, "price", -1);
                            if (qty <= 0 || qty > maxQty) {
                                onlinePlayer(a).ifPresent(p -> {
                                    p.sendMessage(module.getConfig().msg("create.invalid-qty-range",
                                            Map.of("max", String.valueOf(maxQty))));
                                    showDetails(p, template);
                                });
                                return;
                            }
                            if (price < module.getConfig().minUnitPrice()) {
                                onlinePlayer(a).ifPresent(p -> {
                                    p.sendMessage(module.getConfig().msg("create.price-too-low",
                                            Map.of("min", String.format("%.2f", module.getConfig().minUnitPrice()))));
                                    showDetails(p, template);
                                });
                                return;
                            }
                            showCurrency(a, template, qty, price);
                        }))
                .add(button("Back", NamedTextColor.GRAY)
                        .tip("Pick a different item")
                        .click((rv, a) -> showPickItem(a, 0)))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Order Details", NamedTextColor.GOLD))
                        .body(List.of(DialogBody.item(template).showTooltip(true).build()))
                        .inputs(List.of(qtyInput, priceInput))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .build(),
                btns, null, 2);
    }

    private Dialog currencyDialog(ItemStack template, int qty, double price) {
        var buttons = list();
        if (module.getConfig().allowVault()) {
            buttons.add(button("Vault Money", NamedTextColor.GOLD)
                    .tip("Use the server's main economy")
                    .width(140)
                    .click((rv, a) -> showConfirm(a, template, qty, price, null)));
        }
        CurrencyService cs = module.getPlugin().getCurrencyService();
        if (module.getConfig().allowCustomCurrencies() && cs != null) {
            for (Currency cur : cs.getAllCurrencies()) {
                String cid = cur.getId();
                String label = (cur.getDisplayName() != null ? cur.getDisplayName() : cur.getName());
                buttons.add(button(label, NamedTextColor.YELLOW)
                        .tip("Currency: " + cid)
                        .width(140)
                        .click((rv, a) -> showConfirm(a, template, qty, price, cid)));
            }
        }
        buttons.add(button("Back", NamedTextColor.GRAY)
                .tip("Change quantity or price")
                .width(140)
                .click((rv, a) -> showDetails(a, template)));

        return multiAction(
                DialogBase.builder(Component.text("Select Currency", NamedTextColor.GOLD))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons.build(), null, 2);
    }

    private Dialog confirmDialog(ItemStack template, int qty, double price, String currencyId) {
        double total = price * qty;
        double fee = module.getConfig().feesEnabled()
                ? total * module.getConfig().createFeePercent() / 100.0
                : 0.0;
        double charge = total + fee;

        List<DialogBody> bodies = new ArrayList<>();
        bodies.add(DialogBody.item(template).showTooltip(true).build());
        bodies.add(DialogBody.plainMessage(Component.text("Quantity: " + qty, NamedTextColor.GRAY)));
        bodies.add(DialogBody.plainMessage(Component.text(
                "Unit price: " + module.getCurrency().format(currencyId, price), NamedTextColor.GREEN)));
        bodies.add(DialogBody.plainMessage(Component.text(
                "Total escrow: " + module.getCurrency().format(currencyId, total), NamedTextColor.YELLOW)));
        if (fee > 0) {
            bodies.add(DialogBody.plainMessage(Component.text(
                    "Creation fee: " + module.getCurrency().format(currencyId, fee), NamedTextColor.RED)));
        }
        bodies.add(DialogBody.plainMessage(Component.text(
                "You pay: " + module.getCurrency().format(currencyId, charge), NamedTextColor.GOLD)));

        List<ActionButton> btns = list()
                .add(button("Confirm & Pay", NamedTextColor.GREEN)
                        .tip("Create the order and reserve escrow")
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                            p.closeInventory();
                            module.getService().createOrder(p, template, qty, currencyId, price)
                                    .thenAccept(errKey -> {
                                        if (errKey == null) {
                                            p.sendMessage(module.getConfig().msg("create.success",
                                                    Map.of("qty", String.valueOf(qty),
                                                            "price", module.getCurrency().format(currencyId, price))));
                                            OreScheduler.run(module.getPlugin(), () -> showManage(p, 0));
                                        } else {
                                            p.sendMessage(module.getConfig().msg(errKey));
                                        }
                                    });
                        })))
                .add(button("Back", NamedTextColor.GRAY)
                        .tip("Change the currency")
                        .click((rv, a) -> showCurrency(a, template, qty, price)))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Confirm Order", NamedTextColor.GOLD))
                        .body(bodies)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                btns, null, 2);
    }

    // ── Show helpers (re-render per player) ───────────────────────────────────

    private void showBrowse(Audience a, int offset, OrderSortField sort, boolean asc, String search) {
        onlinePlayer(a).ifPresent(p -> p.showDialog(browseDialog(p, offset, sort, asc, search)));
    }

    private void showSearch(Audience a, String current, OrderSortField sort, boolean asc) {
        onlinePlayer(a).ifPresent(p -> p.showDialog(searchDialog(current, sort, asc)));
    }

    private void showGotoPage(Audience a, int currentPage, int totalPages, OrderSortField sort, boolean asc,
            String search) {
        onlinePlayer(a).ifPresent(p -> p.showDialog(gotoPageDialog(currentPage, totalPages, sort, asc, search)));
    }

    private Dialog gotoPageDialog(int currentPage, int totalPages, OrderSortField sort, boolean asc, String search) {
        var pageInput = DialogInput.text("page", Component.text("Page (1–" + totalPages + ")"))
                .initial(String.valueOf(currentPage)).maxLength(6).width(120).labelVisible(true).build();

        List<ActionButton> btns = list()
                .add(button("Go", NamedTextColor.GREEN)
                        .tip("Jump to page")
                        .click((rv, a) -> {
                            int p = readInt(rv, "page", currentPage);
                            int clamped = Math.max(1, Math.min(p, totalPages));
                            showBrowse(a, (clamped - 1) * PAGE_SIZE, sort, asc, search);
                        }))
                .add(button("Back", NamedTextColor.GRAY)
                        .tip("Cancel")
                        .click((rv, a) -> showBrowse(a, (currentPage - 1) * PAGE_SIZE, sort, asc, search)))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Go to Page", NamedTextColor.GOLD))
                        .inputs(List.of(pageInput))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .build(),
                btns, null, 2);
    }

    private Dialog searchDialog(String current, OrderSortField sort, boolean asc) {
        var queryInput = DialogInput.text("query", Component.text("Item name"))
                .initial(current).maxLength(64).width(240).labelVisible(true).build();

        List<ActionButton> btns = list()
                .add(button("Search", NamedTextColor.GREEN)
                        .tip("Apply filter")
                        .click((rv, a) -> {
                            String q = rv != null && rv.getText("query") != null ? rv.getText("query").strip() : "";
                            showBrowse(a, 0, sort, asc, q);
                        }))
                .add(button("Clear", NamedTextColor.GRAY)
                        .tip("Remove filter and show all")
                        .click((rv, a) -> showBrowse(a, 0, sort, asc, "")))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Search Market Orders", NamedTextColor.GOLD))
                        .inputs(List.of(queryInput))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .build(),
                btns, null, 2);
    }

    private void showManage(Audience a, int offset) {
        onlinePlayer(a).ifPresent(p -> p.showDialog(manageDialog(p, offset)));
    }

    private void showPickItem(Audience a, int offset) {
        onlinePlayer(a).ifPresent(p -> p.showDialog(pickItemDialog(p, offset)));
    }

    private void showDetails(Audience a, ItemStack template) {
        onlinePlayer(a).ifPresent(p -> p.showDialog(detailsDialog(template)));
    }

    private void showCurrency(Audience a, ItemStack template, int qty, double price) {
        onlinePlayer(a).ifPresent(p -> p.showDialog(currencyDialog(template, qty, price)));
    }

    private void showConfirm(Audience a, ItemStack template, int qty, double price, String currencyId) {
        onlinePlayer(a).ifPresent(p -> p.showDialog(confirmDialog(template, qty, price, currencyId)));
    }

    private void showFill(Audience a, String orderId, int backOffset, OrderSortField sort, boolean asc, String search) {
        Order o = module.getService().findOrder(orderId).orElse(null);
        if (o == null) {
            showBrowse(a, backOffset, sort, asc, search);
            return;
        }
        onlinePlayer(a).ifPresent(p -> p.showDialog(fillDialog(o, backOffset, sort, asc, search)));
    }

    // ── Small helpers ─────────────────────────────────────────────────────────

    private void addPagination(fr.elias.oreoEssentials.util.DialogButtons.Buttons buttons,
            int offset, int total, java.util.function.IntConsumer reopen) {
        if (total > PAGE_SIZE) {
            for (ActionButton b : pagination(offset, PAGE_SIZE, total, reopen).build()) {
                buttons.add(b);
            }
        }
    }

    private Component orderTooltip(Order o) {
        return Component.text("Requested by: " + o.getRequesterName(), NamedTextColor.GRAY)
                .append(Component.newline())
                .append(Component.text("Remaining: " + o.getRemainingQty() + "/" + o.getTotalQty(),
                        NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text(
                        "Unit price: " + module.getCurrency().format(o.getCurrencyId(), o.getUnitPrice()),
                        NamedTextColor.GREEN));
    }

    private Component orderName(Order o, ItemStack tpl) {
        if (tpl != null)
            return itemName(tpl);
        return Component.text(o.getDisplayItemName());
    }

    private Component itemName(ItemStack it) {
        if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) {
            Component name = it.getItemMeta().displayName();
            if (name != null)
                return name;
        }
        return Component.text(pretty(it.getType().name()));
    }

    private static String pretty(String materialName) {
        String[] words = materialName.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty())
                continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private static int clamp(int offset, int total) {
        if (offset <= 0 || total <= 0)
            return 0;
        if (offset >= total) {
            return ((total - 1) / PAGE_SIZE) * PAGE_SIZE;
        }
        return offset;
    }

    private static int clampQty(int qty, int max) {
        return Math.max(1, Math.min(qty, max));
    }

    private static double readDouble(DialogResponseView rv, String key, double def) {
        if (rv == null)
            return def;
        String s = rv.getText(key);
        if (s == null)
            return def;
        try {
            return Double.parseDouble(s.strip().replace(",", "."));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int readInt(DialogResponseView rv, String key, int def) {
        if (rv == null)
            return def;
        String s = rv.getText(key);
        if (s == null)
            return def;
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private Optional<Player> onlinePlayer(Audience audience) {
        return audience instanceof Player p && p.isOnline() ? Optional.of(p) : Optional.empty();
    }
}
