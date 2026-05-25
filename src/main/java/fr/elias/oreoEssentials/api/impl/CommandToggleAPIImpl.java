package fr.elias.oreoEssentials.api.impl;

import fr.elias.oreoEssentials.api.ICommandToggleAPI;
import fr.elias.oreoEssentials.modules.commandtoggle.CommandToggleService;

public class CommandToggleAPIImpl implements ICommandToggleAPI {
    private final CommandToggleService svc;
    public CommandToggleAPIImpl(CommandToggleService svc) { this.svc = svc; }

    @Override public void applyToggles() { svc.applyToggles(); }
    @Override public void reload() { svc.reload(); }
}
