package fr.elias.oreoEssentials.api.impl;

import fr.elias.oreoEssentials.api.ISellGuiAPI;
import fr.elias.oreoEssentials.modules.sellgui.manager.SellGuiManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SellGuiAPIImpl implements ISellGuiAPI {
    private final SellGuiManager mgr;
    public SellGuiAPIImpl(SellGuiManager mgr) { this.mgr = mgr; }

    @Override public void openSell(@NotNull Player p) { mgr.openSell(p); }
    @Override public void reload() { mgr.reload(); }
}
