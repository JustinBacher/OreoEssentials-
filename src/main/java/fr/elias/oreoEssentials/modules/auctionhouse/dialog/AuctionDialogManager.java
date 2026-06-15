package fr.elias.oreoEssentials.modules.auctionhouse.dialog;

import fr.elias.oreoEssentials.modules.auctionhouse.AuctionHouseModule;
import fr.elias.oreoEssentials.modules.auctionhouse.gui.CurrencyPickerGUI;
import fr.elias.oreoEssentials.modules.auctionhouse.models.Auction;
import fr.elias.oreoEssentials.modules.auctionhouse.models.AuctionCategory;
import fr.elias.oreoEssentials.modules.currency.Currency;
import fr.elias.oreoEssentials.modules.currency.CurrencyService;
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
import java.util.Optional;

import static fr.elias.oreoEssentials.util.DialogButtons.button;
import static fr.elias.oreoEssentials.util.DialogButtons.list;
import static fr.elias.oreoEssentials.util.DialogButtons.multiAction;
import static fr.elias.oreoEssentials.util.DialogButtons.pagination;

/**
 * Paper Dialog-API front-end for the auction house. Mirrors the chest-GUI flow
 * (browse → buy, manage → cancel/reclaim, sell) but renders through the 1.21.6+
 * Dialog API. Gated behind {@code display-mode: dialog}; the chest GUI remains
 * the
 * default and untouched.
 *
 * <p>
 * All economy/listing logic is reused from {@link AuctionHouseModule}
 * ({@code purchaseAuction}, {@code cancelAuction}, {@code reclaimExpired},
 * {@code createListingFromInventory}). This class only builds dialogs and
 * routes
 * clicks.
 *
 * <p>
 * <b>Parity note:</b> unlike the chest GUI, open dialogs are <i>not</i> live
 * cross-server refreshed — there is no push into an open dialog. Players use
 * the
 * explicit <i>Refresh</i> button to re-pull the latest listings.
 */
public final class AuctionDialogManager {

    /**
     * Browse/list dialogs paginate at this many entries per page (Goal&nbsp;1
     * helper).
     */
    private static final int PAGE_SIZE = 350;
    private static final int GRID_COLUMNS = 4;

    public enum AuctionSortField {
        PRICE, NAME, NEWEST
    }

    private final AuctionHouseModule module;

    public AuctionDialogManager(AuctionHouseModule module) {
        this.module = module;
    }

    // ── Entry points ──────────────────────────────────────────────────────────

    public void openBrowse(Player player) {
        showBrowse(player, 0, AuctionSortField.NEWEST, false, "");
    }

    public void openManage(Player player) {
        showManage(player, 0);
    }

    public void openAddListing(Player player) {
        openInventorySellFlow(player);
    }

    private Dialog browseDialog(Player viewer, int offset, AuctionSortField sort, boolean asc, String search) {
        Comparator<Auction> baseCmp = switch (sort) {
            case PRICE -> Comparator.comparingDouble(Auction::getPrice);
            case NAME -> Comparator.comparing(a -> a.getItem().getType().name());
            case NEWEST -> Comparator.comparingLong(Auction::getListedTime);
        };
        Comparator<Auction> cmp = asc ? baseCmp : baseCmp.reversed();
        String searchLower = search.toLowerCase();

        List<Auction> all = module.getAllActiveAuctions().stream()
                .filter(a -> !a.getSeller().equals(viewer.getUniqueId()))
                .filter(a -> !a.isExpired())
                .filter(a -> {
                    if (search.isEmpty())
                        return true;
                    ItemStack it = a.getItem();
                    if (pretty(it.getType().name()).toLowerCase().contains(searchLower))
                        return true;
                    if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) {
                        Component dn = it.getItemMeta().displayName();
                        if (dn != null) {
                            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                    .plainText().serialize(dn);
                            if (plain.toLowerCase().contains(searchLower))
                                return true;
                        }
                    }
                    return false;
                })
                .sorted(cmp)
                .toList();
        int total = all.size();
        int from = clamp(offset, total);
        List<Auction> page = all.subList(from, Math.min(from + PAGE_SIZE, total));
        boolean hasPrev = from > 0;
        boolean hasNext = from + PAGE_SIZE < total;
        int currentPage = total == 0 ? 1 : (from / PAGE_SIZE) + 1;
        int totalPages = total == 0 ? 1 : (total + PAGE_SIZE - 1) / PAGE_SIZE;

        AuctionSortField nextSort = switch (sort) {
            case PRICE -> AuctionSortField.NAME;
            case NAME -> AuctionSortField.NEWEST;
            case NEWEST -> AuctionSortField.PRICE;
        };
        String sortLabel = switch (sort) {
            case PRICE -> "Price";
            case NAME -> "Name";
            case NEWEST -> "Newest";
        };

        var buttons = list()
                .add(button("My Listings", NamedTextColor.AQUA)
                        .tip("View, cancel and create your own listings")
                        .width(100)
                        .click((rv, a) -> showManage(a, 0)))
                .add(button("Refresh", NamedTextColor.GREEN)
                        .tip("Reload the latest listings")
                        .width(100)
                        .click((rv, a) -> showBrowse(a, from, sort, asc, search)))
                .add(button("Sort: " + sortLabel, NamedTextColor.YELLOW)
                        .tip("Click to change sort field")
                        .width(100)
                        .click((rv, a) -> showBrowse(a, 0, nextSort, asc, search)))
                .add(button(asc ? "▲ Asc" : "▼ Desc", NamedTextColor.YELLOW)
                        .tip("Click to reverse order")
                        .width(80)
                        .click((rv, a) -> showBrowse(a, from, sort, !asc, search)))
                .add(button("Search", search.isEmpty() ? NamedTextColor.WHITE : NamedTextColor.GOLD)
                        .tip(search.isEmpty() ? "Filter listings by name" : "Active filter: " + search)
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

        for (Auction au : page) {
            ItemStack it = au.getItem();
            Component icon = fontIcons != null ? fontIcons.icon(it.getType()) : Component.empty();
            String id = au.getId();
            int backOffset = from;
            buttons.add(button(icon, itemName(it))
                    .tip(auctionTooltip(au))
                    .click((rv, a) -> showBuyConfirm(a, id, backOffset, sort, asc, search)));
        }

        ActionButton close = button("Close", NamedTextColor.RED)
                .tip("Close the auction house")
                .click((rv, a) -> onlinePlayer(a).ifPresent(Player::closeInventory))
                .build();

        List<DialogBody> body = total == 0
                ? List.of(DialogBody.plainMessage(
                        Component.text("No listings available right now.", NamedTextColor.GRAY)))
                : List.of();

        return multiAction(
                DialogBase.builder(Component.text("Auction House", NamedTextColor.GOLD))
                        .body(body)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons.build(), close, GRID_COLUMNS);
    }

    private Dialog buyConfirmDialog(Auction au, int backOffset, AuctionSortField sort, boolean asc, String search) {
        ItemStack it = au.getItem();
        List<DialogBody> bodies = new ArrayList<>();
        bodies.add(DialogBody.item(it).showTooltip(true).build());
        bodies.add(DialogBody.plainMessage(Component.text("Seller: " + au.getSellerName(), NamedTextColor.GRAY)));
        bodies.add(DialogBody.plainMessage(Component.text(
                "Price: " + module.formatMoney(au.getPrice(), au.getCurrencyId()), NamedTextColor.GREEN)));

        String id = au.getId();
        List<ActionButton> btns = list()
                .add(button("Confirm Buy", NamedTextColor.GREEN)
                        .tip("Purchase this listing")
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                            module.purchaseAuction(p, id);
                            p.closeInventory();
                        })))
                .add(button("Back", NamedTextColor.GRAY)
                        .tip("Back to listings")
                        .click((rv, a) -> showBrowse(a, backOffset, sort, asc, search)))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Confirm Purchase", NamedTextColor.GOLD))
                        .body(bodies)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                btns, null, 2);
    }

    // ── Manage (own active listings + expired reclaim + add) ──────────────────

    private Dialog manageDialog(Player owner, int offset) {
        List<Auction> active = module.getPlayerActiveListings(owner.getUniqueId());
        List<Auction> expired = module.getPlayerExpired(owner.getUniqueId());
        List<Auction> combined = new ArrayList<>(active);
        combined.addAll(expired);
        int boundary = active.size();
        int total = combined.size();
        int from = clamp(offset, total);
        int to = Math.min(from + PAGE_SIZE, total);

        var buttons = list()
                .add(button("Add Listing", NamedTextColor.GREEN)
                        .tip("List an item from your inventory")
                        .width(120)
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p -> openInventorySellFlow(p))))
                .add(button("Refresh", NamedTextColor.GREEN)
                        .tip("Reload your listings")
                        .width(120)
                        .click((rv, a) -> showManage(a, from)))
                .add(button("Back", NamedTextColor.GRAY)
                        .tip("Back to the auction house")
                        .width(120)
                        .click((rv, a) -> showBrowse(a, 0, AuctionSortField.NEWEST, false, "")));
        addPagination(buttons, from, total, newOff -> showManage(owner, newOff));

        FontIconService fontIcons = module.getPlugin().getFontIconService();
        for (int i = from; i < to; i++) {
            Auction au = combined.get(i);
            boolean isExpired = i >= boundary;
            ItemStack it = au.getItem();
            Component icon = fontIcons != null ? fontIcons.icon(it.getType()) : Component.empty();
            String id = au.getId();
            int backOffset = from;
            if (isExpired) {
                buttons.add(button(icon, itemName(it))
                        .tip(auctionTooltip(au).append(Component.newline())
                                .append(Component.text("Click to reclaim", NamedTextColor.YELLOW)))
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                            module.reclaimExpired(p, id);
                            showManage(p, backOffset);
                        })));
            } else {
                buttons.add(button(icon, itemName(it))
                        .tip(auctionTooltip(au).append(Component.newline())
                                .append(Component.text("Click to cancel & reclaim", NamedTextColor.RED)))
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p -> {
                            module.cancelAuction(p, id);
                            showManage(p, backOffset);
                        })));
            }
        }

        ActionButton close = button("Close", NamedTextColor.RED)
                .tip("Close")
                .click((rv, a) -> onlinePlayer(a).ifPresent(Player::closeInventory))
                .build();

        List<DialogBody> body = total == 0
                ? List.of(DialogBody.plainMessage(
                        Component.text("You have no active or expired listings.", NamedTextColor.GRAY)))
                : List.of();

        return multiAction(
                DialogBase.builder(Component.text("My Listings", NamedTextColor.GOLD))
                        .body(body)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons.build(), close, GRID_COLUMNS);
    }

    // ── Add flow: pick item → price/qty → currency → confirm ──────────────────

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
                        .tip("Back to your listings")
                        .width(120)
                        .click((rv, a) -> showManage(a, 0)));
        addPagination(buttons, from, total, newOff -> showPickItem(player, newOff));

        FontIconService fontIcons = module.getPlugin().getFontIconService();
        for (int i = from; i < to; i++) {
            ItemStack it = items.get(i);
            Component icon = fontIcons != null ? fontIcons.icon(it.getType()) : Component.empty();
            buttons.add(button(icon, itemName(it))
                    .tip(Component.text("Amount: " + it.getAmount(), NamedTextColor.GRAY))
                    .click((rv, a) -> showDetails(a, it, it.getAmount())));
        }

        List<DialogBody> body = total == 0
                ? List.of(DialogBody.plainMessage(
                        Component.text("Your inventory is empty.", NamedTextColor.GRAY)))
                : List.of(DialogBody.plainMessage(
                        Component.text("Pick an item to list.", NamedTextColor.GRAY)));

        return multiAction(
                DialogBase.builder(Component.text("Select an Item", NamedTextColor.GOLD))
                        .body(body)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons.build(), null, GRID_COLUMNS);
    }

    private Dialog detailsDialog(ItemStack template, int available) {
        var priceInput = DialogInput.text("price", Component.text("Price"))
                .initial("")
                .maxLength(16)
                .width(200)
                .labelVisible(true)
                .build();
        var qtyInput = DialogInput.text("qty", Component.text("Quantity (max " + available + ")"))
                .initial(String.valueOf(available))
                .maxLength(9)
                .width(200)
                .labelVisible(true)
                .build();

        List<ActionButton> btns = list()
                .add(button("Next", NamedTextColor.GREEN)
                        .tip("Choose a currency")
                        .click((rv, a) -> {
                            double price = readDouble(rv, "price", -1);
                            int qty = clampQty(readInt(rv, "qty", available), available);
                            if (price <= 0) {
                                onlinePlayer(a).ifPresent(p -> {
                                    p.sendMessage("§cEnter a price greater than 0.");
                                    showDetails(p, template, available);
                                });
                                return;
                            }
                            showCurrency(a, template, available, price, qty);
                        }))
                .add(button("Back", NamedTextColor.GRAY)
                        .tip("Pick a different item")
                        .click((rv, a) -> showPickItem(a, 0)))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Listing Details", NamedTextColor.GOLD))
                        .body(List.of(DialogBody.item(withAmount(template, available)).showTooltip(true).build()))
                        .inputs(List.of(priceInput, qtyInput))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .build(),
                btns, null, 2);
    }

    private Dialog currencyDialog(ItemStack template, int available, double price, int qty) {
        var buttons = list();
        if (module.getEconomy() != null) {
            buttons.add(button(module.getEconomy().getName() + " (Vault)", NamedTextColor.GOLD)
                    .tip("Use the server's main economy")
                    .width(140)
                    .click((rv, a) -> showConfirm(a, template, available, price, qty, null)));
        }
        CurrencyService cs = module.getPlugin().getCurrencyService();
        if (cs != null) {
            for (Currency cur : cs.getAllCurrencies()) {
                String cid = cur.getId();
                String label = (cur.getDisplayName() != null ? cur.getDisplayName() : cur.getName());
                buttons.add(button(label, NamedTextColor.YELLOW)
                        .tip("Currency: " + cid)
                        .width(140)
                        .click((rv, a) -> showConfirm(a, template, available, price, qty, cid)));
            }
        }
        buttons.add(button("Back", NamedTextColor.GRAY)
                .tip("Change price or quantity")
                .width(140)
                .click((rv, a) -> showDetails(a, template, available)));

        return multiAction(
                DialogBase.builder(Component.text("Select Currency", NamedTextColor.GOLD))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons.build(), null, 2);
    }

    private Dialog confirmDialog(ItemStack template, int available, double price, int qty, String currencyId) {
        double fee = (price * module.getConfig().listingFeePercent() / 100.0)
                + module.getConfig().listingFeeFlat();

        List<DialogBody> bodies = new ArrayList<>();
        bodies.add(DialogBody.item(withAmount(template, qty)).showTooltip(true).build());
        bodies.add(DialogBody.plainMessage(Component.text("Quantity: " + qty, NamedTextColor.GRAY)));
        bodies.add(DialogBody.plainMessage(Component.text(
                "Price: " + module.formatMoney(price, currencyId), NamedTextColor.GREEN)));
        if (fee > 0) {
            bodies.add(DialogBody.plainMessage(Component.text(
                    "Listing fee: " + module.formatMoney(fee, currencyId), NamedTextColor.RED)));
        }

        List<ActionButton> btns = list()
                .add(button("Confirm Listing", NamedTextColor.GREEN)
                        .tip("Create the listing")
                        .click((rv, a) -> onlinePlayer(a)
                                .ifPresent(p -> module.createListingFromInventory(p, template, qty, price,
                                        module.getConfig().defaultDurationHours(),
                                        AuctionCategory.fromItem(template), currencyId, msg -> {
                                            if (msg != null)
                                                p.sendMessage(msg);
                                            p.closeInventory();
                                        }))))
                .add(button("Back", NamedTextColor.GRAY)
                        .tip("Change the currency")
                        .click((rv, a) -> showCurrency(a, template, available, price, qty)))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Confirm Listing", NamedTextColor.GOLD))
                        .body(bodies)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                btns, null, 2);
    }

    // ── Sell bridge: inventory currency picker → dialog price/qty ─────────────

    /**
     * Called by
     * {@link fr.elias.oreoEssentials.modules.auctionhouse.gui.CurrencyPickerGUI}
     * in dialog mode after the player picks a currency from the inventory UI.
     * Opens a dialog to collect price and quantity, then flows into the existing
     * confirm dialog.
     */
    public void openSellPriceDialog(Player player, ItemStack item, String currencyId, long durationHours) {
        player.showDialog(sellPriceDialog(item, item.getAmount(), currencyId, durationHours));
    }

    private Dialog sellPriceDialog(ItemStack template, int available, String currencyId, long durationHours) {
        var priceInput = DialogInput.text("price", Component.text("Price"))
                .initial("").maxLength(16).width(200).labelVisible(true).build();
        var qtyInput = DialogInput.text("qty", Component.text("Quantity (max " + available + ")"))
                .initial(String.valueOf(available)).maxLength(9).width(200).labelVisible(true).build();

        List<ActionButton> btns = list()
                .add(button("Next", NamedTextColor.GREEN)
                        .tip("Review listing details")
                        .click((rv, a) -> {
                            double price = readDouble(rv, "price", -1);
                            int qty = clampQty(readInt(rv, "qty", available), available);
                            if (price <= 0) {
                                onlinePlayer(a).ifPresent(p -> {
                                    p.sendMessage("§cEnter a price greater than 0.");
                                    p.showDialog(sellPriceDialog(template, available, currencyId, durationHours));
                                });
                                return;
                            }
                            showConfirm(a, template, available, price, qty, currencyId);
                        }))
                .add(button("Back", NamedTextColor.GRAY)
                        .tip("Change currency")
                        .click((rv, a) -> onlinePlayer(a).ifPresent(
                                p -> CurrencyPickerGUI.getInventory(module, template, durationHours).open(p))))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Listing Details", NamedTextColor.GOLD))
                        .body(List.of(DialogBody.item(withAmount(template, available)).showTooltip(true).build()))
                        .inputs(List.of(priceInput, qtyInput))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .build(),
                btns, null, 2);
    }

    // ── Show helpers (re-render per player) ───────────────────────────────────

    private void showBrowse(Audience a, int offset, AuctionSortField sort, boolean asc, String search) {
        onlinePlayer(a).ifPresent(p -> p.showDialog(browseDialog(p, offset, sort, asc, search)));
    }

    private void showSearch(Audience a, String current, AuctionSortField sort, boolean asc) {
        onlinePlayer(a).ifPresent(p -> p.showDialog(searchDialog(current, sort, asc)));
    }

    private void showGotoPage(Audience a, int currentPage, int totalPages, AuctionSortField sort, boolean asc,
            String search) {
        onlinePlayer(a).ifPresent(p -> p.showDialog(gotoPageDialog(currentPage, totalPages, sort, asc, search)));
    }

    private Dialog gotoPageDialog(int currentPage, int totalPages, AuctionSortField sort, boolean asc, String search) {
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

    private Dialog searchDialog(String current, AuctionSortField sort, boolean asc) {
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
                DialogBase.builder(Component.text("Search Auction House", NamedTextColor.GOLD))
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

    private void showDetails(Audience a, ItemStack template, int available) {
        onlinePlayer(a).ifPresent(p -> p.showDialog(detailsDialog(template, available)));
    }

    private void showCurrency(Audience a, ItemStack template, int available, double price, int qty) {
        onlinePlayer(a).ifPresent(p -> p.showDialog(currencyDialog(template, available, price, qty)));
    }

    private void showConfirm(Audience a, ItemStack template, int available, double price, int qty, String currencyId) {
        onlinePlayer(a).ifPresent(p -> p.showDialog(confirmDialog(template, available, price, qty, currencyId)));
    }

    private void showBuyConfirm(Audience a, String auctionId, int backOffset, AuctionSortField sort, boolean asc,
            String search) {
        Auction au = module.getAllActiveAuctions().stream()
                .filter(x -> x.getId().equals(auctionId))
                .findFirst().orElse(null);
        if (au == null) {
            showBrowse(a, backOffset, sort, asc, search);
            return;
        }
        onlinePlayer(a).ifPresent(p -> p.showDialog(buyConfirmDialog(au, backOffset, sort, asc, search)));
    }

    // ── Inventory-based sell flow (used even when browsing via dialogs) ───────

    private void openInventorySellFlow(Player p) {
        p.closeInventory();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() == org.bukkit.Material.AIR) {
            p.sendMessage(module.getConfig().getMessage("errors.no-item-in-hand"));
            return;
        }
        long duration = module.getConfig().defaultDurationHours();
        CurrencyPickerGUI.getInventory(module, item, duration).open(p);
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

    private Component auctionTooltip(Auction au) {
        return Component.text("Seller: " + au.getSellerName(), NamedTextColor.GRAY)
                .append(Component.newline())
                .append(Component.text("Price: " + module.formatMoney(au.getPrice(), au.getCurrencyId()),
                        NamedTextColor.GREEN))
                .append(Component.newline())
                .append(Component.text("Amount: " + au.getItem().getAmount(), NamedTextColor.GRAY));
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

    private static ItemStack withAmount(ItemStack template, int amount) {
        ItemStack s = template.clone();
        s.setAmount(Math.max(1, Math.min(amount, s.getMaxStackSize())));
        return s;
    }

    private static int clamp(int offset, int total) {
        if (offset <= 0 || total <= 0)
            return 0;
        if (offset >= total) {
            // Snap back to the last page boundary.
            return ((total - 1) / PAGE_SIZE) * PAGE_SIZE;
        }
        return offset;
    }

    private static int clampQty(int qty, int available) {
        return Math.max(1, Math.min(qty, available));
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
