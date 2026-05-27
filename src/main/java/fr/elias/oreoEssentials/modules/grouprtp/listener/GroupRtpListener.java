package fr.elias.oreoEssentials.modules.grouprtp.listener;

import fr.elias.oreoEssentials.modules.grouprtp.service.GroupRtpService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Feeds movement and quit events into {@link GroupRtpService}.
 *
 * PlayerMoveEvent is fired at MONITOR priority with block-change optimisation
 * (same pattern as {@code PortalsListener}) to minimise overhead.
 */
public final class GroupRtpListener implements Listener {

    private final GroupRtpService service;

    public GroupRtpListener(GroupRtpService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        // Only process when the player has moved to a different block
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }
        service.onPlayerMove(e.getPlayer(), e.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        service.onPlayerQuit(e.getPlayer());
    }
}
