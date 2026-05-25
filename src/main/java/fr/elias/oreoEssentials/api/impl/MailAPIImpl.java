package fr.elias.oreoEssentials.api.impl;

import fr.elias.oreoEssentials.api.IMailAPI;
import fr.elias.oreoEssentials.modules.mail.MailService;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public class MailAPIImpl implements IMailAPI {
    private final MailService svc;
    public MailAPIImpl(MailService svc) { this.svc = svc; }

    @Override public void sendMail(@NotNull UUID recipient, @NotNull String senderName, UUID senderUuid, @NotNull String message) { svc.sendMail(recipient, senderName, senderUuid, message); }
    @Override public @NotNull List<MailService.MailMessage> getMail(@NotNull UUID playerId) { return svc.getMail(playerId); }
    @Override public int unreadCount(@NotNull UUID playerId) { return svc.unreadCount(playerId); }
    @Override public void markAllRead(@NotNull UUID playerId) { svc.markAllRead(playerId); }
    @Override public boolean deleteMail(@NotNull UUID playerId, int index) { return svc.deleteMail(playerId, index); }
    @Override public void clearMail(@NotNull UUID playerId) { svc.clearMail(playerId); }
}
