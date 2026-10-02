package dev.goldberry.widgets.overlay.dialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.value.Transform;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.input.key.Key;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.core.presence.Phase;
import dev.goldberry.widgets.core.scroll.Scroll;
import dev.goldberry.widgets.core.scroll.ScrollAxis;

/// The node a stylesheet calls `dialog`: the panel, its two keys, and the focus
/// trap.
///
/// [Dialog] is stateful and styles nothing, so this carries the CSS type, the
/// `id` and the classes — the arrangement every stateful widget in this catalog
/// uses.
///
/// @param title      the heading, or null
/// @param content    what the author wrote, minus the actions
/// @param buttons    the action bar's buttons, already wrapped so that pressing
///                   one closes the dialog first
/// @param onEscape   the dismissive action
/// @param onEnter    the affirmative action
/// @param onDismiss  what the title bar's × does, already wrapped so the dialog
///                   closes first, or null for a dialog with no ×
/// @param phase      the shared opening or closing
/// @param closing    whether input has stopped, which it does the instant
///                   closing starts
/// @param onMotion   told what each frame says about the motion preference
/// @param attributes the `id` and classes the document wrote
record DialogPanel(
        @Nullable String title,
        List<Widget> content,
        List<Widget> buttons,
        Runnable onEscape,
        Runnable onEnter,
        @Nullable Runnable onDismiss,
        Phase phase,
        boolean closing,
        Consumer<Boolean> onMotion,
        Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles, Semantics {

    /// The class on the `scroll` the body sits in, so a stylesheet can tell
    /// it from a `scroll` an author put inside the body.
    static final String SCROLL_CLASS = "dialog-scroll";

    /// A panel arrives on `opacity` and a `scale` from 0.96 to 1. Near enough to one that it reads
    /// as the panel settling rather than as something flying at the reader.
    private static final double FROM = 0.96;

    @Override
    public String cssType() {
        return "dialog";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// The focus trap, declared rather than installed — see
    /// [Handles#isModal()]. It stops being true when this element unmounts,
    /// which is the only way a dialog ever goes, so there is nothing to undo.
    ///
    /// **Still modal while closing.** The keyboard must not fall back into the
    /// window for the 160ms a dialog spends fading, or a `Tab` at the wrong
    /// moment lands somewhere the user cannot see.
    @Override
    public boolean isModal() {
        return true;
    }

    /// The panel takes no focus itself: the first control inside it does, which
    /// is what [dev.goldberry.Host#focus] resolves an id to
    /// when the node it names cannot be focused.
    @Override
    public boolean isFocusable() {
        return false;
    }

    /// A press inside the panel is **not** a press on the scrim.
    ///
    /// The scrim is this node's parent and dispatch bubbles, so without this
    /// every click on a dialog's own content would also be read as a click
    /// outside it and close the thing.
    @Override
    public void onPointer(PointerEvent event) {
        event.consume();
    }

    /// `Esc` presses the dismissive button and `Enter` the affirmative one.
    ///
    /// On the **bubble** phase, so a control inside that means something by
    /// either key keeps it by consuming it — `Enter` in a `text-area`, `Esc` in
    /// an open `select`. See [Dialog]'s note for why capture would be wrong.
    @Override
    public void onKey(KeyEvent event) {
        if (closing
                || event.kind() != KeyEvent.Kind.PRESSED
                || event.isRepeat()
                || !event.modifiers().none()) {
            return;
        }
        if (event.key() == Key.ESCAPE) {
            onEscape.run();
            event.consume();
        } else if (event.key() == Key.ENTER) {
            onEnter.run();
            event.consume();
        }
    }

    /// The title bar, the body, and the action bar.
    ///
    /// **The body is the part that scrolls.** The panel is capped at the
    /// window's height by its stylesheet, and when its content would make it
    /// taller the title and the actions keep their size and the body gives up
    /// the difference. The `scroll` it sits in is a Tab stop only while there
    /// is something to scroll, so a short dialog's Tab order is its fields and
    /// its buttons and nothing else.
    @Override
    public List<Widget> children() {
        var parts = new ArrayList<Widget>(3);
        if (onDismiss != null) {
            parts.add(new DialogHeader(title, onDismiss));
        } else if (title != null) {
            parts.add(new DialogTitle(title));
        }
        parts.add(
                new Scroll(List.of(new DialogBody(content)), ScrollAxis.VERTICAL, Attributes.NONE.classes(SCROLL_CLASS))
                        .tabStopOnlyWhenScrollable());
        if (!buttons.isEmpty()) {
            parts.add(new DialogActions(buttons));
        }
        return List.copyOf(parts);
    }

    /// See [DialogScrim#isAnimating()]: a closing phase never settles itself,
    /// and what ends it is the dialog describing nothing once the fade is over.
    @Override
    public boolean isAnimating() {
        return phase.isRunning();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        onMotion.accept(context.reducedMotion());
        var box = Box.of().style(style).children(boxes.toArray(Box[]::new));
        if (context.reducedMotion()) {
            phase.skip();
            return box;
        }
        var progress = phase.progressAt(context.nowMillis());
        var visible = phase.kind() == Phase.Kind.LEAVING ? 1 - progress : progress;
        if (visible >= 1) {
            return box;
        }
        // From 0.96 to 1 on both axes: a panel that scaled on one would look like a
        // door opening rather than a thing arriving.
        var scale = FROM + (1 - FROM) * visible;
        return box.opacity(visible).transform(Transform.of(new Transform.Function.Scale(scale, scale)));
    }

    /// The heading. A part, so a stylesheet can reach it and nothing can build
    /// one.
    record DialogTitle(String text) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "dialog-title";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).children(Box.text(context.paragraph(style, text), style.color()));
        }
    }

    /// The title and the × beside it, for a dialog that asked for one.
    ///
    /// A row of its own rather than a × inside `dialog-title`, so the heading
    /// is the same node with the same rule in every dialog and only a dialog
    /// with a way out in its title bar has the row.
    ///
    /// @param title     the heading, or null for a row with only the ×
    /// @param onDismiss what the × does
    record DialogHeader(@Nullable String title, Runnable onDismiss) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "dialog-header";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public List<Widget> children() {
            return title == null
                    ? List.of(new DialogDismiss(onDismiss))
                    : List.of(new DialogTitle(title), new DialogDismiss(onDismiss));
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().style(style).children(boxes.toArray(Box[]::new));
        }
    }

    /// The × at the end of the title bar.
    ///
    /// **Not a Tab stop**, for `tab-close`'s reason: the keyboard already has
    /// the same way out. In a dialog with a ×, `Esc` and a press on the scrim
    /// do what the × does, and a stop on it would put a Tab before every
    /// field in the dialog.
    ///
    /// @param onDismiss what to do, already wrapped so the dialog closes first
    record DialogDismiss(Runnable onDismiss) implements Widget.Leaf, Styled, Paints, Handles, Semantics {

        /// The same pen `message-dismiss` draws its × with.
        private static final double STROKE = 1.5;

        @Override
        public String cssType() {
            return "dialog-dismiss";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public void onPointer(PointerEvent event) {
            if (event.kind() == PointerEvent.Kind.CLICKED) {
                onDismiss.run();
            }
            // Every kind, as the panel around it does: a press on the × is not
            // a press on the scrim behind the panel.
            event.consume();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).mark(new Box.Mark(Box.Mark.Kind.CROSS, style.color(), STROKE));
        }

        @Override
        public Role role() {
            return Role.BUTTON;
        }

        @Override
        public String accessibleName() {
            return "Close";
        }
    }

    /// What the author wrote.
    record DialogBody(List<Widget> children) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "dialog-body";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public List<Widget> children() {
            return children;
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().style(style).children(boxes.toArray(Box[]::new));
        }
    }

    /// The action bar.
    ///
    /// ## Where the platform's button order lives
    ///
    /// The **platform's button order** — affirmative on the right on macOS and
    /// Linux, and the theme's to control — is applied by the action bar
    /// automatically, and the whole of it is one CSS declaration: the buttons are written in a
    /// canonical order — neutral, dismissive, affirmative — and a theme that
    /// wants Windows' order writes `dialog-actions { flex-direction: row-reverse }`.
    ///
    /// That is what theme-controlled has to mean here, because the order is not
    /// something this widget could decide: `children()` runs before style
    /// resolution and long before anything has asked the platform anything. A
    /// widget that read the operating system to lay itself out would also be a
    /// widget whose golden images differ per machine.
    record DialogActions(List<Widget> children) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "dialog-actions";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public List<Widget> children() {
            return children;
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().style(style).children(boxes.toArray(Box[]::new));
        }
    }

    @Override
    public Role role() {
        return Role.DIALOG;
    }

    @Override
    public @Nullable String accessibleName() {
        return title;
    }
}
