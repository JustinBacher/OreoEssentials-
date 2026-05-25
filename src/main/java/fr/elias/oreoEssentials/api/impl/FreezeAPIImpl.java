package fr.elias.oreoEssentials.api.impl;

import fr.elias.oreoEssentials.api.IFreezeAPI;
import fr.elias.oreoEssentials.services.FreezeService;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class FreezeAPIImpl implements IFreezeAPI {
    private final FreezeService svc;
    public FreezeAPIImpl(FreezeService svc) { this.svc = svc; }

    @Override public boolean isFrozen(@NotNull UUID playerId) { return svc.isFrozen(playerId); }
    @Override public void setFrozen(@NotNull UUID playerId, boolean frozen) { svc.set(playerId, frozen); }
}
