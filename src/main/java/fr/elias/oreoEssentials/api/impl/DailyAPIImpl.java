package fr.elias.oreoEssentials.api.impl;

import fr.elias.oreoEssentials.api.IDailyAPI;
import fr.elias.oreoEssentials.modules.daily.DailyService;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class DailyAPIImpl implements IDailyAPI {
    private final DailyService svc;
    public DailyAPIImpl(DailyService svc) { this.svc = svc; }

    @Override public boolean isEnabled() { return svc.isEnabled(); }
    @Override public boolean canClaimToday(@NotNull Player p) { return svc.canClaimToday(p); }
    @Override public int getStreak(@NotNull UUID id) { return svc.getStreak(id); }
    @Override public boolean claim(@NotNull Player p) { return svc.claim(p); }
}
