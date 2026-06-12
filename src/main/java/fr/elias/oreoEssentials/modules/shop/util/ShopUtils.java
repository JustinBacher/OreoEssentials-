package fr.elias.oreoEssentials.modules.shop.util;

import fr.elias.oreoEssentials.modules.shop.ShopModule;
import fr.elias.oreoEssentials.modules.shop.models.Shop;
import fr.elias.oreoEssentials.modules.shop.models.ShopItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ShopUtils {

    private ShopUtils() {}

    /** Counts how many items in {@code player}'s inventory match {@code shopItem}. */
    public static int countOwned(Player player, ShopItem shopItem) {
        int count = 0;
        for (ItemStack slot : player.getInventory().getContents()) {
            if (slot == null || slot.getType() == Material.AIR) continue;
            if (shopItem.matches(slot)) count += slot.getAmount();
        }
        return count;
    }

    /**
     * Formats {@code price} using the shop's custom currency (if set) or falls
     * back to {@code sym + "%.2f"}.
     */
    public static String formatPrice(Shop shop, ShopModule module, String sym, double price) {
        if (shop != null && shop.getCurrencyId() != null && module != null) {
            var cs = module.getPlugin().getCurrencyService();
            if (cs != null) return cs.formatBalance(shop.getCurrencyId(), price);
        }
        return sym + String.format("%.2f", price);
    }
}
