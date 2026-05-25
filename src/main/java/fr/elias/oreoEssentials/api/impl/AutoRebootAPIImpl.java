package fr.elias.oreoEssentials.api.impl;

import fr.elias.oreoEssentials.api.IAutoRebootAPI;
import fr.elias.oreoEssentials.modules.autoreboot.AutoRebootService;

public class AutoRebootAPIImpl implements IAutoRebootAPI {
    private final AutoRebootService svc;
    public AutoRebootAPIImpl(AutoRebootService svc) { this.svc = svc; }

    @Override public void stop() { svc.stop(); }
    @Override public void reload() { svc.reload(); }
}
