package fr.elias.oreoEssentials.api.impl;

import fr.elias.oreoEssentials.api.IBossBarAPI;
import fr.elias.oreoEssentials.modules.bossbar.BossBarService;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BossBarAPIImpl implements IBossBarAPI {
    private final BossBarService svc;
    public BossBarAPIImpl(BossBarService svc) { this.svc = svc; }

    @Override public boolean isShown(@NotNull Player p) { return svc.isShown(p); }
    @Override public void show(@NotNull Player p) { svc.show(p); }
    @Override public void hide(@NotNull Player p) { svc.hide(p); }
    @Override public void reload() { svc.reload(); }
}
