package fr.elias.oreoEssentials.util;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent helpers for assembling {@link ActionButton} lists, tooltips, and
 * multi-action {@link Dialog}s for Paper dialogs. Folds the repeated
 * {@code width} + {@code customClick(.., CB_OPTS)}, multi-line tooltip joins,
 * and {@code Dialog.create(..)} boilerplate into chainable builders.
 */
public final class DialogButtons {

    /** Default button width, in dialog grid units. */
    public static final int DEFAULT_WIDTH = 130;

    /** Shared callback options: unlimited uses, default lifetime. */
    public static final ClickCallback.Options CB_OPTS = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES)
            .build();

    private DialogButtons() {
    }

    // ── Factories ─────────────────────────────────────────────────────────────

    public static Btn button(String text, NamedTextColor color) {
        return button(Component.text(text, color));
    }

    public static Btn button(Component label) {
        return new Btn(label);
    }

    /**
     * Icon-prefixed button: splices a (possibly empty) font-glyph icon ahead of
     * the label. When {@code icon} is null or {@link Component#empty()} the
     * label is used verbatim — no glyph, no leading space — so vanilla clients
     * without the resource-pack font see clean text only.
     */
    public static Btn button(Component icon, Component label) {
        return new Btn(prefixIcon(icon, label));
    }

    public static Btn button(Component icon, String text, NamedTextColor color) {
        return button(icon, Component.text(text, color));
    }

    /** Prepends {@code icon + space} to {@code label}, or returns the label as-is. */
    public static Component prefixIcon(Component icon, Component label) {
        if (icon == null || Component.empty().equals(icon)) {
            return label;
        }
        return Component.empty().append(icon).append(Component.space()).append(label);
    }

    public static Buttons list() {
        return new Buttons();
    }

    public static Tip tooltip() {
        return new Tip();
    }

    /** Starts a tooltip seeded with prebuilt lines (e.g. config-driven lore). */
    public static Tip tooltip(List<Component> lines) {
        return new Tip().lines(lines);
    }

    /** A no-op decorative button used to pad rows. */
    public static ActionButton spacer() {
        return ActionButton.create(
                Component.text("·", NamedTextColor.DARK_GRAY),
                null, DEFAULT_WIDTH,
                DialogAction.customClick((rv, a) -> {
                }, CB_OPTS));
    }

    /** Assembles a multi-action dialog from a prebuilt base and button list. */
    public static Dialog multiAction(DialogBase base, List<ActionButton> buttons,
            ActionButton close, int columns) {
        return Dialog.create(f -> f.empty()
                .base(base)
                .type(DialogType.multiAction(buttons, close, columns)));
    }

    /**
     * Builds a Prev / "Page x/y" / Next button row for a paged dialog. The
     * current {@code offset} is carried as closure state: each nav button hands
     * the new offset to {@code reopen}, which re-renders the dialog at that page
     * (capturing whatever other view state the caller needs). Prev/Next are
     * omitted at the bounds; the page indicator is a no-op button. Returns a
     * {@link Buttons} so callers can fold it into a larger list.
     */
    public static Buttons pagination(int offset, int pageSize, int total,
            java.util.function.IntConsumer reopen) {
        int safeSize = Math.max(1, pageSize);
        int pages = Math.max(1, (total + safeSize - 1) / safeSize);
        int page = Math.min(pages, (offset / safeSize) + 1);
        boolean hasPrev = offset > 0;
        boolean hasNext = offset + safeSize < total;

        return list()
                .addIf(hasPrev, button("« Prev", NamedTextColor.YELLOW)
                        .tip("Previous page")
                        .width(90)
                        .click((rv, a) -> reopen.accept(Math.max(0, offset - safeSize))))
                .add(button("Page " + page + "/" + pages, NamedTextColor.GRAY)
                        .width(90)
                        .click((rv, a) -> {
                        }))
                .addIf(hasNext, button("Next »", NamedTextColor.YELLOW)
                        .tip("Next page")
                        .width(90)
                        .click((rv, a) -> reopen.accept(offset + safeSize)));
    }

    // ── Single-button builder ─────────────────────────────────────────────────

    public static final class Btn {
        private final Component label;
        private Component tooltip;
        private int width = DEFAULT_WIDTH;
        private DialogAction action;

        private Btn(Component label) {
            this.label = label;
        }

        public Btn tip(String s) {
            this.tooltip = Component.text(s);
            return this;
        }

        public Btn tip(Component c) {
            this.tooltip = c;
            return this;
        }

        public Btn tip(Tip t) {
            this.tooltip = t.build();
            return this;
        }

        /** Custom lore: any prebuilt list of lines (config, dynamic, etc.). */
        public Btn tip(List<Component> lines) {
            this.tooltip = tooltip(lines).build();
            return this;
        }

        public Btn width(int w) {
            this.width = w;
            return this;
        }

        public Btn click(DialogActionCallback cb) {
            this.action = DialogAction.customClick(cb, CB_OPTS);
            return this;
        }

        public ActionButton build() {
            return ActionButton.create(label, tooltip, width, action);
        }
    }

    // ── Multi-line tooltip builder ────────────────────────────────────────────

    public static final class Tip {
        private final List<Component> lines = new ArrayList<>();

        private Tip() {
        }

        public Tip line(String text, NamedTextColor color) {
            lines.add(Component.text(text, color));
            return this;
        }

        /** Adds a line with no explicit color (inherits the dialog default). */
        public Tip line(String text) {
            lines.add(Component.text(text));
            return this;
        }

        public Tip line(Component c) {
            lines.add(c);
            return this;
        }

        public Tip lineIf(boolean condition, String text, NamedTextColor color) {
            if (condition) {
                lines.add(Component.text(text, color));
            }
            return this;
        }

        public Tip lineIf(boolean condition, Component c) {
            if (condition) {
                lines.add(c);
            }
            return this;
        }

        public Tip lines(Iterable<Component> cs) {
            if (cs != null) {
                cs.forEach(lines::add);
            }
            return this;
        }

        public Tip lines(Component... cs) {
            for (Component c : cs) {
                lines.add(c);
            }
            return this;
        }

        public Component build() {
            return Component.join(JoinConfiguration.newlines(), lines);
        }
    }

    // ── Button-list builder ───────────────────────────────────────────────────

    public static final class Buttons {
        private final List<ActionButton> list = new ArrayList<>();

        private Buttons() {
        }

        public Buttons add(Btn b) {
            list.add(b.build());
            return this;
        }

        public Buttons add(ActionButton b) {
            list.add(b);
            return this;
        }

        public Buttons addIf(boolean condition, Btn b) {
            if (condition) {
                list.add(b.build());
            }
            return this;
        }

        public Buttons spacers(int n) {
            for (int i = 0; i < n; i++) {
                list.add(spacer());
            }
            return this;
        }

        public List<ActionButton> build() {
            return list;
        }
    }
}
