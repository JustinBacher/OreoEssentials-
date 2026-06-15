package fr.elias.oreoEssentials.modules.mail.dialog;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.mail.MailService;
import fr.elias.oreoEssentials.modules.mail.model.MailMessage;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.font.FontIconService;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static fr.elias.oreoEssentials.util.DialogButtons.button;
import static fr.elias.oreoEssentials.util.DialogButtons.list;
import static fr.elias.oreoEssentials.util.DialogButtons.multiAction;
import static fr.elias.oreoEssentials.util.DialogButtons.pagination;
import static fr.elias.oreoEssentials.util.DialogButtons.tooltip;

/**
 * Paper Dialog-API front-end for the mailbox. Mirrors the chest-GUI flow
 * ({@link fr.elias.oreoEssentials.modules.mail.gui.MailboxMenu}) but renders
 * through the 1.21.6+ Dialog API. Gated behind {@code mail.display-mode: dialog}
 * in the main config; the chest GUI remains the default and untouched.
 *
 * <p>All read/claim/delete logic is reused from {@link MailService}. As with the
 * other dialog menus, an open dialog is not live-refreshed — each mutating action
 * re-opens the list at the same page.
 */
public final class MailDialogManager {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private static final int PAGE_SIZE = 100;
    private static final int GRID_COLUMNS = 4;

    private final OreoEssentials plugin;
    private final MailService service;

    public MailDialogManager(OreoEssentials plugin, MailService service) {
        this.plugin = plugin;
        this.service = service;
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public void openMailbox(Player player) {
        showList(player, 0);
    }

    // ── List (paginated) ──────────────────────────────────────────────────────

    private void showList(Audience a, int offset) {
        onlinePlayer(a).ifPresent(p ->
                service.getMailbox(p.getUniqueId()).thenAccept(mails ->
                        OreScheduler.run(plugin, () -> p.showDialog(listDialog(p, mails, offset)))));
    }

    private Dialog listDialog(Player viewer, List<MailMessage> mails, int offset) {
        int total = mails.size();
        int from = clamp(offset, total);
        List<MailMessage> page = mails.subList(from, Math.min(from + PAGE_SIZE, total));

        var buttons = list();
        if (!mails.isEmpty()) {
            buttons.add(button("Delete All", NamedTextColor.RED)
                    .tip("Permanently clear your whole mailbox")
                    .width(140)
                    .click((rv, a) -> showConfirmClearAll(a)));
        }
        addPagination(buttons, from, total, newOff -> showList(viewer, newOff));

        FontIconService fontIcons = plugin.getFontIconService();
        for (MailMessage m : page) {
            ItemStack tpl = iconItem(m);
            Component icon = (fontIcons != null && tpl != null)
                    ? fontIcons.icon(tpl.getType()) : Component.empty();
            String id = m.getId();
            int backOffset = from;
            buttons.add(button(icon, mailName(m))
                    .tip(mailTooltip(m))
                    .click((rv, a) -> showDetail(a, id, backOffset)));
        }

        ActionButton close = button("Close", NamedTextColor.RED)
                .tip("Close your mailbox")
                .click((rv, a) -> onlinePlayer(a).ifPresent(Player::closeInventory))
                .build();

        List<DialogBody> body = total == 0
                ? List.of(DialogBody.plainMessage(
                        Component.text("Your mailbox is empty.", NamedTextColor.GRAY)))
                : List.of();

        return multiAction(
                DialogBase.builder(Component.text("Mailbox", NamedTextColor.GOLD))
                        .body(body)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons.build(), close, GRID_COLUMNS);
    }

    // ── Detail (read / claim / delete a single message) ───────────────────────

    private void showDetail(Audience a, String mailId, int backOffset) {
        onlinePlayer(a).ifPresent(p ->
                service.getMailbox(p.getUniqueId()).thenAccept(mails -> {
                    MailMessage m = mails.stream()
                            .filter(x -> x.getId().equals(mailId)).findFirst().orElse(null);
                    if (m == null) {
                        showList(p, backOffset);
                        return;
                    }
                    // Opening a message marks it read (parity with chest GUI).
                    if (!m.isRead()) service.markRead(p.getUniqueId(), mailId);
                    OreScheduler.run(plugin, () -> p.showDialog(detailDialog(m, backOffset)));
                }));
    }

    private Dialog detailDialog(MailMessage m, int backOffset) {
        String sender = m.getSenderName() != null ? m.getSenderName() : "Unknown";
        String id = m.getId();

        List<DialogBody> bodies = new ArrayList<>();
        if (m.hasItem() && !m.isItemClaimed()) {
            ItemStack item = MailService.deserializeItem(m.getItemData());
            if (item != null) bodies.add(DialogBody.item(item).showTooltip(true).build());
        }
        bodies.add(DialogBody.plainMessage(Component.text("From: " + sender, NamedTextColor.AQUA)));
        bodies.add(DialogBody.plainMessage(Component.text(
                DATE_FMT.format(new Date(m.getSentAt())), NamedTextColor.DARK_GRAY)));
        if (m.hasMessage()) {
            bodies.add(DialogBody.plainMessage(Component.text(m.getMessage(), NamedTextColor.WHITE)));
        }
        if (m.hasItem() && m.isItemClaimed()) {
            bodies.add(DialogBody.plainMessage(Component.text("Item already claimed.", NamedTextColor.DARK_GRAY)));
        }

        var buttons = list();
        if (m.hasItem() && !m.isItemClaimed()) {
            buttons.add(button("Claim Item", NamedTextColor.GREEN)
                    .tip("Take the attached item into your inventory")
                    .click((rv, a) -> onlinePlayer(a).ifPresent(p ->
                            service.claimItem(p, id).thenAccept(ok -> {
                                p.sendMessage(ok
                                        ? color("&a[Mail] Item claimed from &e" + sender + "&a!")
                                        : color("&c[Mail] Could not claim item (already claimed or invalid)."));
                                showList(p, backOffset);
                            }))));
        }
        buttons.add(button("Delete", NamedTextColor.RED)
                .tip("Permanently delete this message")
                .click((rv, a) -> onlinePlayer(a).ifPresent(p ->
                        service.deleteMail(p.getUniqueId(), id).thenRun(() -> showList(p, backOffset)))));
        buttons.add(button("Back", NamedTextColor.GRAY)
                .tip("Back to your mailbox")
                .click((rv, a) -> showList(a, backOffset)));

        return multiAction(
                DialogBase.builder(Component.text("Mail from " + sender, NamedTextColor.GOLD))
                        .body(bodies)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons.build(), null, 2);
    }

    // ── Confirm: clear all ────────────────────────────────────────────────────

    private void showConfirmClearAll(Audience a) {
        onlinePlayer(a).ifPresent(p -> p.showDialog(confirmClearAllDialog()));
    }

    private Dialog confirmClearAllDialog() {
        List<ActionButton> btns = list()
                .add(button("Yes, delete all", NamedTextColor.RED)
                        .tip("Permanently clear your mailbox")
                        .click((rv, a) -> onlinePlayer(a).ifPresent(p ->
                                service.clearMail(p.getUniqueId()).thenRun(() -> {
                                    p.sendMessage(color("&a[Mail] Your mailbox has been cleared."));
                                    showList(p, 0);
                                }))))
                .add(button("Cancel", NamedTextColor.GRAY)
                        .tip("Keep my mail")
                        .click((rv, a) -> showList(a, 0)))
                .build();

        return multiAction(
                DialogBase.builder(Component.text("Clear mailbox?", NamedTextColor.GOLD))
                        .body(List.of(DialogBody.plainMessage(
                                Component.text("This permanently deletes every message. Unclaimed items are lost.",
                                        NamedTextColor.GRAY))))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                btns, null, 2);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void addPagination(fr.elias.oreoEssentials.util.DialogButtons.Buttons buttons,
                               int offset, int total, java.util.function.IntConsumer reopen) {
        if (total > PAGE_SIZE) {
            for (ActionButton b : pagination(offset, PAGE_SIZE, total, reopen).build()) {
                buttons.add(b);
            }
        }
    }

    private static ItemStack iconItem(MailMessage m) {
        if (m.hasItem() && !m.isItemClaimed()) {
            ItemStack item = MailService.deserializeItem(m.getItemData());
            if (item != null) return item;
        }
        return new ItemStack(m.hasItem() ? Material.CHEST : Material.PAPER);
    }

    private Component mailName(MailMessage m) {
        String sender = m.getSenderName() != null ? m.getSenderName() : "Unknown";
        NamedTextColor color = m.isRead() ? NamedTextColor.GRAY : NamedTextColor.YELLOW;
        String prefix = m.isRead() ? "From: " : "✉ From: ";
        return Component.text(prefix + sender, color);
    }

    private Component mailTooltip(MailMessage m) {
        var tip = tooltip()
                .line(DATE_FMT.format(new Date(m.getSentAt())), NamedTextColor.DARK_GRAY)
                .lineIf(!m.isRead(), "Unread", NamedTextColor.GREEN);
        if (m.hasMessage()) {
            tip.line(m.getMessage(), NamedTextColor.WHITE);
        }
        if (m.hasItem()) {
            tip.line(m.isItemClaimed() ? "Item already claimed" : "Has an attached item",
                    m.isItemClaimed() ? NamedTextColor.DARK_GRAY : NamedTextColor.GOLD);
        }
        return tip.build();
    }

    private static int clamp(int offset, int total) {
        if (offset <= 0 || total <= 0) return 0;
        if (offset >= total) {
            return ((total - 1) / PAGE_SIZE) * PAGE_SIZE;
        }
        return offset;
    }

    private static Component color(String s) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }

    private Optional<Player> onlinePlayer(Audience audience) {
        return audience instanceof Player p && p.isOnline() ? Optional.of(p) : Optional.empty();
    }
}
