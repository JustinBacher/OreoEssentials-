package fr.elias.oreoEssentials.modules.shop.util;

import fr.elias.oreoEssentials.modules.shop.ShopModule;
import fr.elias.oreoEssentials.modules.shop.dialog.SortDir;
import fr.elias.oreoEssentials.modules.shop.dialog.SortField;
import fr.elias.oreoEssentials.modules.shop.models.Shop;
import fr.elias.oreoEssentials.modules.shop.models.ShopItem;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class ShopItemFilter {

    private ShopItemFilter() {}

    /**
     * Returns all items in {@code shop} visible to {@code player} — rotation-active,
     * permission-cleared, assigned to a valid slot — optionally filtered by
     * {@code query} and sorted by {@code sort}/{@code dir}.
     */
    public static List<ShopItem> filter(Player player, Shop shop, String query,
                                         SortField sort, SortDir dir, ShopModule module) {
        Set<String> activeRotations = shop.isRotating()
                ? module.getRotationManager().getActiveItemIds(shop) : null;
        String lq = (query == null) ? "" : query.toLowerCase(Locale.ROOT);

        List<ShopItem> result = new ArrayList<>();
        for (ShopItem item : shop.getAllItems()) {
            if (activeRotations != null && !activeRotations.contains(item.getId())) continue;
            String perm = item.getPermission();
            if (perm != null && !perm.isEmpty() && !player.hasPermission(perm)) continue;
            if (item.getSlot() < 0) continue;
            if (!lq.isEmpty()) {
                String name = item.getDisplayName() == null ? "" : item.getDisplayName().toLowerCase(Locale.ROOT);
                String mat  = item.getMaterial().name().toLowerCase(Locale.ROOT);
                if (!name.contains(lq) && !mat.contains(lq)) continue;
            }
            result.add(item);
        }

        UUID uid = player.getUniqueId();
        Comparator<ShopItem> cmp;
        switch (sort) {
            case BUY_PRICE  -> cmp = Comparator.comparingDouble(
                    item -> module.getPriceModifierManager().getEffectiveBuyPrice(uid, item));
            case SELL_PRICE -> cmp = Comparator.comparingDouble(
                    item -> module.getPriceModifierManager().getEffectiveSellPrice(uid, item));
            default         -> cmp = Comparator.comparing(
                    item -> (item.getDisplayName() == null ? "" : item.getDisplayName())
                            .toLowerCase(Locale.ROOT));
        }
        if (dir == SortDir.DESC) cmp = cmp.reversed();
        result.sort(cmp);
        return result;
    }
}
