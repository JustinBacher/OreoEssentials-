package fr.elias.oreoEssentials.util;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.List;

import static fr.elias.oreoEssentials.util.DialogButtons.button;
import static fr.elias.oreoEssentials.util.DialogButtons.list;
import static fr.elias.oreoEssentials.util.DialogButtons.multiAction;

/**
 * Shared yes/no confirmation rendered through the Paper 1.21.6+ Dialog API. The
 * trivial chest-GUI confirmations (home/warp delete, etc.) reuse this instead of
 * each rebuilding a two-button dialog. Gated by the caller on {@code display-mode}
 * — when off, callers fall back to their classic chest confirmation.
 */
public final class ConfirmDialog {

    private ConfirmDialog() {
    }

    /** True when confirmations should render through the Dialog API (global default). */
    public static boolean dialogMode() {
        return DisplayMode.globalIsDialog();
    }

    /**
     * Shows a confirm/cancel dialog. {@code onConfirm}/{@code onCancel} run on click
     * (the player should already be on the main thread inside a dialog callback).
     */
    public static void show(Player player, Component title, Component message,
                            String confirmLabel, String cancelLabel,
                            Runnable onConfirm, Runnable onCancel) {
        List<ActionButton> buttons = list()
                .add(button(confirmLabel, NamedTextColor.GREEN)
                        .tip("Confirm")
                        .click((rv, a) -> {
                            player.closeInventory();
                            if (onConfirm != null) onConfirm.run();
                        }))
                .add(button(cancelLabel, NamedTextColor.GRAY)
                        .tip("Cancel")
                        .click((rv, a) -> {
                            player.closeInventory();
                            if (onCancel != null) onCancel.run();
                        }))
                .build();

        Dialog dialog = multiAction(
                DialogBase.builder(title)
                        .body(List.of(DialogBody.plainMessage(message)))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build(),
                buttons, null, 2);

        player.showDialog(dialog);
    }
}
