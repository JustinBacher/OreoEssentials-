package fr.elias.oreoEssentials.modules.shop.dialog;

import fr.elias.oreoEssentials.modules.shop.ShopModule;
import fr.elias.oreoEssentials.modules.shop.models.Shop;
import fr.elias.oreoEssentials.modules.shop.models.ShopItem;
import fr.elias.oreoEssentials.modules.shop.util.ShopItemFilter;
import fr.elias.oreoEssentials.modules.shop.util.ShopUtils;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleConsumer;
import java.util.function.Function;

import static fr.elias.oreoEssentials.util.DialogButtons.button;
import static fr.elias.oreoEssentials.util.DialogButtons.list;
import static fr.elias.oreoEssentials.util.DialogButtons.multiAction;
import static fr.elias.oreoEssentials.util.DialogButtons.tooltip;

public final class ShopDialogManager {

    private static final int GRID_COLUMNS = 4;

    private final ShopModule module;

    public ShopDialogManager(ShopModule module) {
        this.module = module;
    }

    public void open(Player player, Shop shop) {
        showMain(player, shop, "", SortField.NAME, SortDir.ASC);
    }

    public void openAllShops(Player player) {
        showAllShops(player);
    }

    // ── Dialog builders ───────────────────────────────────────────────────────

    private Dialog mainDialog(
            Player player,
            Shop shop,
            String query,
            SortField sort,
            SortDir dir,
            double bal) {
        List<ShopItem> visible = ShopItemFilter.filter(player, shop, query, sort, dir, module);

        String sym = module.getShopConfig().getCurrencySymbol();
        SortField nextSort = sort.next();
        SortDir nextDir = dir.toggle();

        String searchLabel = query.isEmpty() ? "Search" : "Search: \"" + query + "\"";
        var buttons = list()
                .add(button("Back", NamedTextColor.GRAY)
                        .tip("Back to all shops")
                        .width(100)
                        .click((rv, a) -> showAllShops(a)))
                .add(button(searchLabel, NamedTextColor.YELLOW)
                        .tip("Click to search items")
                        .width(100)
                        .click((rv, a) -> showSearch(a, shop, query, sort, dir)))
                .add(button("Sort: " + sort.label(), NamedTextColor.AQUA)
                        .tip("Next: " + nextSort.label())
                        .width(100)
                        .click(toMain(shop, query, nextSort, dir)))
                .add(button(dir.label(), NamedTextColor.AQUA)
                        .tip("Switch to: " + nextDir.label())
                        .width(100)
                        .click(toMain(shop, query, sort, nextDir)));

        var fontIcons = module.getPlugin().getFontIconService();
        for (ShopItem item : visible) {
            String trend = module.getDynamicPricingManager().getTrendLore(item);
            // Resource-pack font glyph (empty when the font is disabled/unmapped,
            // in which case the button renders the name only).
            Component icon = fontIcons != null ? fontIcons.icon(item.getMaterial()) : Component.empty();
            buttons.add(button(icon, Lang.toComponent(item.getDisplayName()))
                    .tip(itemTooltip(item, buyPrice(player, item), sellPrice(player, item), sym, trend, shop,
                            item.canBuy(), item.canSell(), loreLines(item)))
                    .click((rv, a) -> showConfirm(a, shop, item, query, sort, dir)));
        }

        ActionButton closeBtn = button("Close", NamedTextColor.RED)
                .tip("Close the shop")
                .click((rv, a) -> onlinePlayer(a).ifPresent(Player::closeInventory))
                .build();

        Component title = Lang.toComponent(shop.getTitle());

        return multiAction(
                DialogBase.builder(title)
                        .body(List.of(balanceBody(shop, bal)))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons.build(), closeBtn, GRID_COLUMNS);
    }

    private Dialog searchDialog(
            Shop shop,
            String prevQuery,
            SortField sort,
            SortDir dir) {
        var queryInput = DialogInput.text("query", Component.text("Search items"))
                .initial(prevQuery)
                .maxLength(64)
                .width(200)
                .labelVisible(true)
                .build();

        List<ActionButton> buttons = list()
                .add(button("Apply", NamedTextColor.GREEN)
                        .tip("Filter items")
                        .click((response, a) -> {
                            String q = response != null ? response.getText("query") : "";
                            showMain(a, shop, q, sort, dir);
                        }))
                .add(button("Cancel", NamedTextColor.GRAY)
                        .tip("Keep current search")
                        .click(toMain(shop, prevQuery, sort, dir)))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Search Shop"))
                        .inputs(List.of(queryInput))
                        .canCloseWithEscape(false)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .build(),
                buttons, null, 2);
    }

    private Dialog allShopsDialog() {
        var buttons = list();
        for (Shop s : module.getShopManager().getAllShops().values()) {
            buttons.add(button(Lang.toComponent(s.getTitle()))
                    .click((rv, a) -> showMain(a, s, "", SortField.NAME, SortDir.ASC)));
        }

        // afterAction is NONE, so a null-action button won't auto-close — close
        // explicitly.
        ActionButton closeBtn = button("Close", NamedTextColor.RED)
                .tip("Close")
                .click((rv, a) -> onlinePlayer(a).ifPresent(Player::closeInventory))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Shops"))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons.build(), closeBtn, GRID_COLUMNS);
    }

    private Dialog confirmDialog(
            Player player,
            Shop shop,
            ShopItem item,
            String query,
            SortField sort,
            SortDir dir,
            double eBuy,
            int maxBuy,
            int initialQty,
            double bal) {
        String sym = module.getShopConfig().getCurrencySymbol();
        int safeQty = Math.max(1, Math.min(initialQty, maxBuy));

        List<DialogBody> bodies = new ArrayList<>();
        bodies.add(DialogBody.plainMessage(Lang.toComponent(item.getDisplayName())));
        bodies.add(balanceBody(shop, bal));
        if (maxBuy > 0) {
            bodies.add(DialogBody.plainMessage(Component.text(
                    "Buy total: " + ShopUtils.formatPrice(shop, module, sym, eBuy * safeQty),
                    NamedTextColor.GREEN)));
            if (initialQty > maxBuy) {
                bodies.add(DialogBody.plainMessage(
                        Component.text("You can only afford " + maxBuy + " ", NamedTextColor.RED)
                                .append(Lang.toComponent(item.getDisplayName()))));
            }
        } else {
            bodies.add(DialogBody.plainMessage(
                    Component.text("You can't afford this item.", NamedTextColor.RED)));
        }

        List<ActionButton> btns = list()
                .addIf(maxBuy > 0,
                        button("Update", NamedTextColor.AQUA)
                                .tip("Recalculate total for the entered quantity")
                                .click((rv, a) -> {
                                    int qty = readQty(rv);
                                    onlinePlayer(a).ifPresent(p -> OreScheduler.runLaterForEntity(
                                            module.getPlugin(), p,
                                            () -> showConfirm(p, shop, item, query, sort, dir, qty), 1L));
                                }))
                .addIf(maxBuy > 0,
                        button("Confirm Buy", NamedTextColor.GREEN)
                                .tip("Buy price: " + ShopUtils.formatPrice(shop, module, sym, eBuy))
                                .click((rv, a) -> onlinePlayer(a)
                                        .filter(p -> hasShopAccess(p, item))
                                        .ifPresent(p -> {
                                            int requested = readQty(rv);
                                            resolveBalance(p, shop, currentBal -> {
                                                double unit = buyPrice(p, item);
                                                int afford = unit > 0 ? (int) (currentBal / unit) : 9999;
                                                int qty = Math.min(requested, Math.max(1, afford));
                                                module.getTransactionProcessor().processBuy(p, item, qty);
                                                p.closeInventory();
                                            });
                                        })))
                .add(button("Cancel", NamedTextColor.GRAY)
                        .tip("Don't buy or sell")
                        .click(toMain(shop, query, sort, dir)))
                .build();

        var base = DialogBase.builder(Lang.toComponent(item.getDisplayName()))
                .body(bodies)
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE);
        if (maxBuy > 0) {
            base.inputs(List.of(DialogInput.text("qty", Component.text("Quantity"))
                    .initial(String.valueOf(safeQty))
                    .maxLength(9)
                    .width(200)
                    .labelVisible(true)
                    .build()));
        }

        return multiAction(base.build(), btns, null, Math.min(btns.size(), 3));
    }

    // ── Callback helpers ──────────────────────────────────────────────────────

    /** Re-opens the main shop dialog with the given view state. */
    private DialogActionCallback toMain(Shop shop, String query, SortField sort, SortDir dir) {
        return (rv, a) -> showMain(a, shop, query, sort, dir);
    }

    // ── Price helpers ─────────────────────────────────────────────────────────

    private double buyPrice(Player p, ShopItem item) {
        return module.getPriceModifierManager().getEffectiveBuyPrice(p.getUniqueId(), item);
    }

    /**
     * Resolves the player's spendable balance in the shop's economy, then runs
     * {@code callback} on the entity thread. Mirrors {@code TransactionProcessor}'s
     * Vault/custom-currency branch: a null currency id, or a configured id with no
     * {@code CurrencyService}, falls back to the Vault economy. The custom-currency
     * balance lookup is async, so a missing/errored balance resolves to 0.
     */
    private void resolveBalance(Player player, Shop shop, DoubleConsumer callback) {
        String currencyId = shop.getCurrencyId();
        var cs = (currencyId != null) ? module.getPlugin().getCurrencyService() : null;
        if (cs == null) {
            callback.accept(module.getEconomy().getBalance(player));
            return;
        }
        cs.getBalance(player.getUniqueId(), currencyId).whenComplete((bal, err) -> {
            double resolved = (err != null || bal == null) ? 0.0 : bal;
            OreScheduler.runForEntity(module.getPlugin(), player, () -> callback.accept(resolved));
        });
    }

    private double sellPrice(Player p, ShopItem item) {
        return module.getPriceModifierManager().getEffectiveSellPrice(p.getUniqueId(), item);
    }

    private DialogBody balanceBody(Shop shop, double bal) {
        return DialogBody.plainMessage(
                Component.text("Balance: " + formatBalance(shop, bal), NamedTextColor.GOLD));
    }

    private String formatBalance(Shop shop, double amount) {
        String currencyID = shop.getCurrencyId();

        if (currencyID == null) {
            return module.getEconomy().format(amount);
        }

        return module
                .getPlugin()
                .getCurrencyService()
                .formatBalance(currencyID, amount);
    }

    private void show(Audience audience, Function<Player, Dialog> build) {
        onlinePlayer(audience).ifPresent(p -> p.showDialog(build.apply(p)));
    }

    private void showMain(Audience audience, Shop shop, String query, SortField sort, SortDir dir) {
        onlinePlayer(audience)
                .ifPresent(p -> resolveBalance(
                        p,
                        shop,
                        bal -> p.showDialog(mainDialog(p, shop, query, sort, dir, bal))));
    }

    private void showAllShops(Audience audience) {
        onlinePlayer(audience).ifPresent(p -> p.showDialog(allShopsDialog()));
    }

    private void showSearch(
            Audience audience,
            Shop shop,
            String prevQuery,
            SortField sort,
            SortDir dir) {
        show(audience, p -> searchDialog(shop, prevQuery, sort, dir));
    }

    private void showConfirm(
            Audience audience,
            Shop shop,
            ShopItem item,
            String query,
            SortField sort,
            SortDir dir) {
        showConfirm(audience, shop, item, query, sort, dir, 1);
    }

    private void showConfirm(
            Audience audience,
            Shop shop,
            ShopItem item,
            String query,
            SortField sort,
            SortDir dir,
            int qty) {
        onlinePlayer(audience).ifPresent(player -> {
            double eBuy = buyPrice(player, item);

            resolveBalance(player, shop, bal -> {
                int maxBuy = eBuy > 0 ? (int) (bal / eBuy) : Integer.MAX_VALUE;
                player.showDialog(
                        confirmDialog(player, shop, item, query, sort, dir, eBuy, maxBuy, qty, bal));
            });
        });
    }

    private Component itemTooltip(
            ShopItem item,
            double eBuy,
            double eSell,
            String sym,
            String trend,
            Shop shop,
            boolean showBuy,
            boolean showSell,
            List<Component> lore) {
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
        String s = rv.getText("qty");
        if (s == null)
            return 1;
        try {
            return Math.max(1, Integer.parseInt(s.strip()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
