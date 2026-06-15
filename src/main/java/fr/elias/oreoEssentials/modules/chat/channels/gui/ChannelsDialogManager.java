package fr.elias.oreoEssentials.modules.chat.channels.gui;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.chat.channels.ChatChannel;
import fr.elias.oreoEssentials.modules.chat.channels.ChatChannelManager;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

import static fr.elias.oreoEssentials.util.DialogButtons.button;
import static fr.elias.oreoEssentials.util.DialogButtons.list;
import static fr.elias.oreoEssentials.util.DialogButtons.multiAction;
import static fr.elias.oreoEssentials.util.DialogButtons.tooltip;

/**
 * Paper Dialog-API front-end for the chat channel switcher. Mirrors
 * {@link ChannelsGUI} (join the default or any listed channel) but renders through
 * the 1.21.6+ Dialog API. Gated behind
 * {@code chat.channels.gui.display-mode: dialog}; the chest GUI remains the default.
 */
public final class ChannelsDialogManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int GRID_COLUMNS = 3;

    private final OreoEssentials plugin;
    private final ChatChannelManager channelManager;

    public ChannelsDialogManager(OreoEssentials plugin, ChatChannelManager channelManager) {
        this.plugin = plugin;
        this.channelManager = channelManager;
    }

    public void openChannels(Player player) {
        player.showDialog(channelsDialog(player));
    }

    private Dialog channelsDialog(Player viewer) {
        ChatChannel current = channelManager.getPlayerChannel(viewer);
        List<ChatChannel> channels = channelManager.getOrderedChannels();
        ChatChannel def = resolveDefault(channels);

        var buttons = list();

        if (def != null) {
            boolean inDefault = current != null && current.getId().equalsIgnoreCase(def.getId());
            String defId = def.getId();
            buttons.add(button(MM.deserialize((inDefault ? "✔ " : "") + def.getDisplayName()))
                    .tip(tooltip()
                            .line("Switch back to the default channel.", NamedTextColor.GRAY)
                            .line(inDefault ? "Status: Current" : "Click to switch",
                                    inDefault ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
                            .build())
                    .width(150)
                    .click((rv, a) -> join(a, defId)));
        }

        for (ChatChannel channel : channels) {
            String id = channel.getId();
            boolean isCurrent = current != null && current.getId().equals(channel.getId());
            String label = (isCurrent ? "✔ " : "") + channel.getDisplayName();
            buttons.add(button(MM.deserialize(label))
                    .tip(channelTooltip(viewer, channel, isCurrent))
                    .width(150)
                    .click((rv, a) -> join(a, id)));
        }

        ActionButton close = button("Close", NamedTextColor.RED)
                .tip("Close")
                .click((rv, a) -> onlinePlayer(a).ifPresent(Player::closeInventory))
                .build();

        List<DialogBody> body = channels.isEmpty()
                ? List.of(DialogBody.plainMessage(
                        Component.text("No channels are available.", NamedTextColor.GRAY)))
                : List.of();

        return multiAction(
                DialogBase.builder(Component.text("Chat Channels", NamedTextColor.GOLD))
                        .body(body)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons.build(), close, GRID_COLUMNS);
    }

    private void join(Audience a, String channelId) {
        onlinePlayer(a).ifPresent(player -> {
            ChatChannel channel = channelManager.getOrderedChannels().stream()
                    .filter(c -> c.getId().equals(channelId))
                    .findFirst().orElse(null);
            if (channel == null) {
                player.showDialog(channelsDialog(player));
                return;
            }
            if (!channel.isEnabled()) {
                player.sendMessage("§cThis channel is currently disabled.");
                return;
            }
            if (!channel.canJoin(player)) {
                player.sendMessage("§cYou don't have permission to join this channel.");
                return;
            }
            channelManager.setPlayerChannel(player, channel);
            player.sendMessage(MM.deserialize(
                    "<green>You are now chatting in " + channel.getDisplayName() + "</green>"));
            player.closeInventory();
        });
    }

    private Component channelTooltip(Player viewer, ChatChannel channel, boolean isCurrent) {
        boolean canJoin = channel.canJoin(viewer);
        boolean canTalk = channel.canTalk(viewer);
        var tip = tooltip().line(MM.deserialize(channel.getDescription()));
        if (isCurrent) tip.line("Status: Current channel", NamedTextColor.GREEN);
        else if (!channel.isEnabled()) tip.line("Status: Disabled", NamedTextColor.RED);
        else if (!canJoin) tip.line("Status: Locked", NamedTextColor.RED);
        else tip.line("Status: Available", NamedTextColor.GREEN);
        tip.line("Scope: " + scopeDisplay(channel), NamedTextColor.GRAY);
        if (channel.getScope() == ChatChannel.ChannelScope.RANGE) {
            tip.line("Range: " + channel.getRangeBlocks() + " blocks", NamedTextColor.GRAY);
        }
        if (!canJoin) tip.line("No permission", NamedTextColor.RED);
        else if (!canTalk) tip.line("View only", NamedTextColor.YELLOW);
        else tip.line("Click to join", NamedTextColor.GREEN);
        return tip.build();
    }

    private ChatChannel resolveDefault(List<ChatChannel> ordered) {
        try {
            ChatChannel def = channelManager.getDefaultChannel();
            if (def != null) return def;
        } catch (Throwable ignored) {
        }
        return (ordered == null || ordered.isEmpty()) ? null : ordered.get(0);
    }

    private String scopeDisplay(ChatChannel channel) {
        return switch (channel.getScope()) {
            case ALL -> "All Players";
            case WORLD -> "Same World";
            case RANGE -> "Local Area";
            case SERVER -> "This Server";
            case SHARD -> "This Shard";
        };
    }

    private Optional<Player> onlinePlayer(Audience audience) {
        return audience instanceof Player p && p.isOnline() ? Optional.of(p) : Optional.empty();
    }
}
