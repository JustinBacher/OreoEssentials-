package fr.elias.oreoEssentials.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.traqueur.commands.api.arguments.Arguments;
import fr.traqueur.commands.api.arguments.Infinite;
import org.bukkit.command.CommandSender;

/**
 * Bridges the existing {@link OreoCommand} interface with the CommandsAPI framework.
 *
 * All existing OreoCommand implementations stay unchanged. This adapter wraps one
 * and registers it via CommandsAPI's command injection pipeline (no plugin.yml
 * commands block required).
 *
 * Arguments are captured as a single optional Infinite String so that CommandsAPI
 * accepts any number of tokens, then reconstructed into String[] for OreoCommand.
 */
public final class OreoCommandAdapter extends fr.traqueur.commands.spigot.Command<OreoEssentials> {

    private final OreoCommand delegate;

    public OreoCommandAdapter(OreoEssentials plugin, OreoCommand delegate) {
        super(plugin, delegate.name());
        this.delegate = delegate;

        if (!delegate.aliases().isEmpty()) {
            this.addAlias(delegate.aliases().toArray(new String[0]));
        }

        String perm = delegate.permission();
        if (perm != null && !perm.isBlank()) {
            this.setPermission(perm);
        }

        String usage = delegate.usage();
        if (usage != null && !usage.isBlank()) {
            this.setUsage("/" + delegate.name() + " " + usage);
        }

        if (delegate.playerOnly()) {
            this.setGameOnly(true);
        }

        // Declare optional Infinite arg so CommandsAPI accepts 0..∞ raw tokens.
        // DefaultArgumentParser joins them with spaces; we split them back in execute().
        this.addOptionalArg("_args", Infinite.class);
    }

    @Override
    public void execute(CommandSender sender, Arguments arguments) {
        String raw = arguments.<String>getOptional("_args").orElse("");
        String[] args = raw.isBlank() ? new String[0] : raw.split(" ");

        try {
            boolean ok = delegate.execute(sender, delegate.name(), args);
            if (!ok) {
                String u = delegate.usage();
                sender.sendMessage("§eUsage: §7/" + delegate.name()
                        + (u == null || u.isBlank() ? "" : " " + u));
            }
        } catch (Throwable t) {
            getPlugin().getLogger().warning("[Commands] Exception in /" + delegate.name() + ": " + t.getMessage());
        }
    }

    public OreoCommand getDelegate() {
        return delegate;
    }
}
