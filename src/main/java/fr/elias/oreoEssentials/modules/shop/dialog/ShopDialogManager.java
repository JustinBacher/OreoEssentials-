package fr.elias.oreoEssentials.modules.shop.dialog;

import fr.elias.oreoEssentials.modules.shop.ShopModule;
import fr.elias.oreoEssentials.modules.shop.models.Shop;
import fr.elias.oreoEssentials.modules.shop.models.ShopItem;
import fr.elias.oreoEssentials.modules.shop.util.ShopItemFilter;
import fr.elias.oreoEssentials.modules.shop.util.ShopUtils;
import fr.elias.oreoEssentials.util.Lang;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static fr.elias.oreoEssentials.util.DialogButtons.button;
import static fr.elias.oreoEssentials.util.DialogButtons.list;
import static fr.elias.oreoEssentials.util.DialogButtons.multiAction;
import static fr.elias.oreoEssentials.util.DialogButtons.tooltip;

public final class ShopDialogManager {

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int PAGE_SIZE = 54;
    private static final int GRID_COLUMNS = 6;

    private final ShopModule module;

    public ShopDialogManager(ShopModule module) {
        this.module = module;
    }

    // ── Public entry point ────────────────────────────────────────────────────

    public void open(Player player, Shop shop) {
        player.showDialog(mainDialog(player, shop, "", 1, SortField.NAME, SortDir.ASC));
    }

    // ── Dialog builders ───────────────────────────────────────────────────────

    private Dialog mainDialog(
            Player player, Shop shop,
            String query, int page,
            SortField sort, SortDir dir) {
        List<ShopItem> visible = ShopItemFilter.filter(player, shop, query, sort, dir, module);
        int totalPages = Math.max(1, (int) Math.ceil((double) visible.size() / PAGE_SIZE));
        int safePage = Math.max(1, Math.min(page, totalPages));
        int pageIndex = (safePage - 1) * PAGE_SIZE;
        List<ShopItem> pageItems = visible.subList(pageIndex, Math.min(pageIndex + PAGE_SIZE, visible.size()));

        String sym = module.getShopConfig().getCurrencySymbol();
        SortField nextSort = sort.next();
        SortDir nextDir = dir.toggle();

        // ── Row 1: Search | Sort | Dir | (3 spacers to fill the row) ─────────
        String searchLabel = query.isEmpty() ? "Search" : "Search: \"" + query + "\"";
        var buttons = list()
                .add(button(searchLabel, NamedTextColor.YELLOW)
                        .tip("Click to search items")
                        .click((rv, a) -> showSearch(a, shop, query, safePage, sort, dir)))
                .add(button("Sort: " + sort.label(), NamedTextColor.AQUA)
                        .tip("Next: " + nextSort.label())
                        .click(toMain(shop, query, 1, nextSort, dir)))
                .add(button(dir.label(), NamedTextColor.AQUA)
                        .tip("Switch to: " + nextDir.label())
                        .click(toMain(shop, query, 1, sort, nextDir)))
                .spacers(3);

        // ── Item buttons ──────────────────────────────────────────────────────
        for (ShopItem item : pageItems) {
            String trend = module.getDynamicPricingManager().getTrendLore(item);
            buttons.add(button(Lang.toComponent(item.getDisplayName()))
                    .tip(itemTooltip(item, buyPrice(player, item), sellPrice(player, item), sym, trend, shop,
                            item.canBuy(), item.canSell(), loreLines(item)))
                    .click((rv, a) -> showConfirm(a, shop, item, query, safePage, sort, dir)));
        }

        // ── Pagination ────────────────────────────────────────────────────────
        buttons.addIf(safePage > 1,
                button("← Previous", NamedTextColor.YELLOW)
                        .tip("Page " + (safePage - 1))
                        .click(toMain(shop, query, safePage - 1, sort, dir)))
                .addIf(safePage < totalPages,
                        button("Next →", NamedTextColor.YELLOW)
                                .tip("Page " + (safePage + 1))
                                .click(toMain(shop, query, safePage + 1, sort, dir)));

        ActionButton closeBtn = button("Close", NamedTextColor.RED)
                .tip("Close the shop").build();

        String pageTag = totalPages > 1 ? " [" + safePage + "/" + totalPages + "]" : "";
        Component title = Lang.toComponent(shop.getTitle() + pageTag);

        return multiAction(
                DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons.build(), closeBtn, GRID_COLUMNS);
    }

    private Dialog searchDialog(Shop shop, String prevQuery,
            int page, SortField sort, SortDir dir) {
        var queryInput = DialogInput.text("query", Component.text("Search items"))
                .initial(prevQuery)
                .maxLength(64)
                .width(240)
                .labelVisible(true)
                .build();

        List<ActionButton> buttons = list()
                .add(button("Apply", NamedTextColor.GREEN)
                        .tip("Filter items")
                        .click((response, a) -> {
                            String q = response != null ? response.getText("query") : "";
                            showMain(a, shop, clean(q), 1, sort, dir);
                        }))
                .add(button("Cancel", NamedTextColor.GRAY)
                        .tip("Keep current search")
                        .click(toMain(shop, prevQuery, page, sort, dir)))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Search Shop"))
                        .inputs(List.of(queryInput))
                        .canCloseWithEscape(false)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .build(),
                buttons, null, 2);
    }

    private Dialog confirmDialog(Player player, Shop shop, ShopItem item,
            String query, int page, SortField sort, SortDir dir,
            double eBuy, double eSell, int maxBuy, int maxSell) {
        String sym = module.getShopConfig().getCurrencySymbol();
        int qtyMax = Math.max(Math.max(maxBuy, maxSell), 1);

        var qtyInput = DialogInput.numberRange("qty", Component.text("Quantity"), 1f, qtyMax)
                .step(1f).initial(1f).width(200).build();

        List<ActionButton> btns = list()
                .addIf(item.canBuy() && maxBuy > 0,
                        button("Confirm Buy", NamedTextColor.GREEN)
                                .tip("Buy price: " + ShopUtils.formatPrice(shop, module, sym, eBuy))
                                .click(txn(item,
                                        (p, qty) -> module.getTransactionProcessor().processBuy(p, item, qty),
                                        shop, query, page, sort, dir)))
                .addIf(item.canSell() && maxSell > 0,
                        button("Confirm Sell", NamedTextColor.RED)
                                .tip("Sell price: " + ShopUtils.formatPrice(shop, module, sym, eSell))
                                .click(txn(item,
                                        (p, qty) -> module.getTransactionProcessor().processSell(p, item, qty),
                                        shop, query, page, sort, dir)))
                .add(button("Cancel", NamedTextColor.GRAY)
                        .tip("Don't buy or sell")
                        .click(toMain(shop, query, page, sort, dir)))
                .build();

        return multiAction(
                DialogBase.builder(Lang.toComponent(item.getDisplayName()))
                        .inputs(List.of(qtyInput))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .build(),
                btns, null, Math.min(btns.size(), 3));
    }

    // ── Callback helpers ──────────────────────────────────────────────────────

    /** Re-opens the main shop dialog with the given view state. */
    private DialogActionCallback toMain(Shop shop, String query, int page, SortField sort, SortDir dir) {
        return (rv, a) -> showMain(a, shop, query, page, sort, dir);
    }

    /**
     * Runs a buy/sell op for an access-checked online player, then returns to main.
     */
    private DialogActionCallback txn(ShopItem item, BiConsumer<Player, Integer> op,
            Shop shop, String query, int page, SortField sort, SortDir dir) {
        return (rv, a) -> onlinePlayer(a)
                .filter(p -> hasShopAccess(p, item))
                .ifPresent(p -> {
                    op.accept(p, readQty(rv));
                    showMain(a, shop, query, page, sort, dir);
                });
    }

    // ── Price helpers ─────────────────────────────────────────────────────────

    private double buyPrice(Player p, ShopItem item) {
        return module.getPriceModifierManager().getEffectiveBuyPrice(p.getUniqueId(), item);
    }

    private double sellPrice(Player p, ShopItem item) {
        return module.getPriceModifierManager().getEffectiveSellPrice(p.getUniqueId(), item);
    }

    // ── Transition helpers ────────────────────────────────────────────────────

    private void show(Audience audience, Function<Player, Dialog> build) {
        onlinePlayer(audience).ifPresent(p -> p.showDialog(build.apply(p)));
    }

    private void showMain(Audience audience, Shop shop, String query, int page, SortField sort, SortDir dir) {
        show(audience, p -> mainDialog(p, shop, query, page, sort, dir));
    }

    private void showSearch(Audience audience, Shop shop, String prevQuery, int page, SortField sort, SortDir dir) {
        show(audience, p -> searchDialog(shop, prevQuery, page, sort, dir));
    }

    private void showConfirm(Audience audience, Shop shop, ShopItem item,
            String query, int page, SortField sort, SortDir dir) {
        onlinePlayer(audience).ifPresent(player -> {
            double eBuy = buyPrice(player, item);
            double eSell = sellPrice(player, item);

            int maxBuy = 0;
            if (item.canBuy()) {
                if (shop.getCurrencyId() == null) {
                    double bal = module.getEconomy().getBalance(player);
                    maxBuy = (eBuy > 0) ? Math.max(1, (int) (bal / eBuy)) : 1;
                } else {
                    maxBuy = 9999;
                }
            }
            int maxSell = item.canSell() ? ShopUtils.countOwned(player, item) : 0;

            if (maxBuy == 0 && maxSell == 0) {
                Lang.sendRaw(player, module.getShopConfig()
                        .getMessage("buy-not-enough-money").replace("{price}", "?"));
                showMain(audience, shop, query, page, sort, dir);
                return;
            }

            player.showDialog(confirmDialog(player, shop, item, query, page, sort, dir, eBuy, eSell, maxBuy, maxSell));
        });
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private Component itemTooltip(ShopItem item, double eBuy, double eSell, String sym,
            String trend, Shop shop, boolean showBuy, boolean showSell, List<Component> lore) {
        var tip = tooltip()
                .line("Amount: " + item.getAmount(), NamedTextColor.GRAY)
                .lineIf(showBuy, "Buy:  " + ShopUtils.formatPrice(shop, module, sym, eBuy), NamedTextColor.GREEN)
                .lineIf(showSell, "Sell: " + ShopUtils.formatPrice(shop, module, sym, eSell), NamedTextColor.RED)
                .lines(lore);
        if (trend != null && !trend.isEmpty()) {
            tip.line(Lang.toComponent(trend));
        }
        return tip.build();
    }

    /**
     * Item lore as components. Assumes ShopItem#getLore() returns MiniMessage
     * strings.
     */
    private List<Component> loreLines(ShopItem item) {
        List<String> lore = item.getLore();
        if (lore == null || lore.isEmpty()) {
            return List.of();
        }
        return lore.stream().map(Lang::toComponent).toList();
    }

    private Optional<Player> onlinePlayer(Audience audience) {
        return audience instanceof Player p && p.isOnline() ? Optional.of(p) : Optional.empty();
    }

    private boolean hasShopAccess(Player p, ShopItem item) {
        if (!p.hasPermission("oshopgui.shop"))
            return false;
        String perm = item.getPermission();
        return perm == null || perm.isEmpty() || p.hasPermission(perm);
    }

    private static int readQty(DialogResponseView rv) {
        if (rv == null)
            return 1;
        Float f = rv.getFloat("qty");
        return (f != null) ? Math.max(1, f.intValue()) : 1;
    }

    private static String clean(String s) {
        return (s == null) ? "" : s.strip();
    }
}
