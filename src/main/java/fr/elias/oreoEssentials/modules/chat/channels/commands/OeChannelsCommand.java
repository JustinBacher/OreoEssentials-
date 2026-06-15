package fr.elias.oreoEssentials.modules.chat.channels.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.chat.channels.ChatChannelManager;
import fr.elias.oreoEssentials.modules.chat.channels.gui.ChannelsDialogManager;
import fr.elias.oreoEssentials.modules.chat.channels.gui.ChannelsGUI;
import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class OeChannelsCommand implements OreoCommand {

    private final OreoEssentials plugin;
    private final ChatChannelManager channelManager;
    private final ChannelsDialogManager dialogManager;

    public OeChannelsCommand(OreoEssentials plugin, ChatChannelManager channelManager) {
        this.plugin = plugin;
        this.channelManager = channelManager;
        this.dialogManager = new ChannelsDialogManager(plugin, channelManager);
    }

    /** True when the channel switcher should render through the Paper Dialog API. */
    private boolean useDialogMode() {
        return fr.elias.oreoEssentials.util.DisplayMode.isDialog(plugin.getChatConfig()
                .getCustomConfig().getString("chat.channels.gui.display-mode", null));
    }

    @Override
    public String name() {
        return "oechannels";
    }

    @Override
    public List<String> aliases() {
        return List.of("channels", "ch");
    }

    @Override
    public String permission() {
        return "oreo.chat.channels";
    }

    @Override
    public String usage() {
        return "";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (!channelManager.isEnabled()) {
            player.sendMessage("§cChat channels are currently disabled.");
            return true;
        }

        if (useDialogMode()) {
            dialogManager.openChannels(player);
        } else {
            ChannelsGUI.getInventory(plugin, channelManager).open(player);
        }
        return true;
    }

}