package fr.elias.oreoEssentials.api;

import fr.elias.oreoEssentials.modules.mail.MailService;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * API for the Mail module — in-game mail between players.
 *
 * <p>Obtain via {@link OreoEssentialsAPI#mail()}. Returns {@code null} if disabled.
 */
public interface IMailAPI {

    /**
     * Sends a mail message to the recipient.
     *
     * @param recipient   recipient UUID
     * @param senderName  display name of the sender
     * @param senderUuid  UUID of the sender (may be {@code null} for system mail)
     * @param message     mail body
     */
    void sendMail(@NotNull UUID recipient, @NotNull String senderName, UUID senderUuid, @NotNull String message);

    /**
     * Returns an unmodifiable list of mail messages for the player.
     */
    @NotNull List<MailService.MailMessage> getMail(@NotNull UUID playerId);

    /**
     * Returns the number of unread messages for the player.
     */
    int unreadCount(@NotNull UUID playerId);

    /**
     * Marks all messages for the player as read.
     */
    void markAllRead(@NotNull UUID playerId);

    /**
     * Removes the message at {@code index} from the player's inbox.
     *
     * @return {@code true} if the index was valid and the message was removed
     */
    boolean deleteMail(@NotNull UUID playerId, int index);

    /**
     * Clears all mail for the player.
     */
    void clearMail(@NotNull UUID playerId);
}
