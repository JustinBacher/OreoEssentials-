package fr.elias.oreoEssentials.util;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiConsumer;

import static fr.elias.oreoEssentials.util.DialogButtons.button;
import static fr.elias.oreoEssentials.util.DialogButtons.list;
import static fr.elias.oreoEssentials.util.DialogButtons.multiAction;

/**
 * Shared single-field text input dialog that can be launched independently from
 * a feature's list/display mode. Callers are expected to keep their own legacy
 * fallback when dialogs are unavailable on the current runtime.
 */
public final class TextInputDialog {

    private TextInputDialog() {
    }

    public static boolean supported(Player player) {
        if (player == null || !player.isOnline()) return false;
        try {
            Method showDialog = player.getClass().getMethod("showDialog", Dialog.class);
            return showDialog != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean show(Player player,
                               Component title,
                               Component message,
                               String key,
                               String label,
                               String initialValue,
                               int maxLength,
                               String submitLabel,
                               String cancelLabel,
                               BiConsumer<Player, String> onSubmit,
                               Runnable onCancel) {
        if (!supported(player)) return false;

        var input = DialogInput.text(key, Component.text(label))
                .initial(initialValue == null ? "" : initialValue)
                .maxLength(maxLength)
                .width(220)
                .labelVisible(true)
                .build();

        List<ActionButton> buttons = list()
                .add(button(submitLabel, NamedTextColor.GREEN)
                        .tip("Submit")
                        .click((rv, a) -> {
                            if (onSubmit != null && player.isOnline()) {
                                onSubmit.accept(player, readText(rv, key));
                            }
                        }))
                .add(button(cancelLabel, NamedTextColor.GRAY)
                        .tip("Cancel")
                        .click((rv, a) -> {
                            if (onCancel != null) onCancel.run();
                        }))
                .build();

        Dialog dialog = multiAction(
                DialogBase.builder(title)
                        .body(message == null ? List.of() : List.of(DialogBody.plainMessage(message)))
                        .inputs(List.of(input))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .build(),
                buttons, null, 2);

        player.showDialog(dialog);
        return true;
    }

    private static String readText(io.papermc.paper.dialog.DialogResponseView rv, String key) {
        if (rv == null) return "";
        String text = rv.getText(key);
        return text == null ? "" : text.trim();
    }
}
