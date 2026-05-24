package fr.elias.oreoEssentials.api.impl;

import fr.elias.oreoEssentials.api.IClearLagAPI;
import fr.elias.oreoEssentials.modules.clearlag.ClearLagManager;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ClearLagAPIImpl implements IClearLagAPI {
    private final ClearLagManager mgr;
    public ClearLagAPIImpl(ClearLagManager mgr) { this.mgr = mgr; }

    @Override public int clearItems(@NotNull CommandSender s) { return mgr.commandClear(s); }
    @Override public int killMobs(@NotNull CommandSender s) { return mgr.commandKillMobs(s); }
    @Override public void reload() { mgr.reload(); }
}
