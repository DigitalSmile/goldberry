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
/// @param phase      the shared opening or closing
/// @param closing    whether input has stopped, which it does the instant
///                   closing starts
/// @param closed     whether the closing animation has run out
/// @param onMotion   told what each frame says about the motion preference
/// @param attributes the `id` and classes the document wrote
record DialogPanel(
        @Nullable String title,
        List<Widget> content,
        List<Widget> buttons,
        Runnable onEscape,
        Runnable onEnter,
        Phase phase,
        boolean closing,
        boolean closed,
        Consumer<Boolean> onMotion,
        Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles, Semantics {

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

    @Override
    public List<Widget> children() {
        if (closed) {
            return List.of();
        }
        var parts = new ArrayList<Widget>(3);
        if (title != null) {
            parts.add(new DialogTitle(title));
        }
        parts.add(new DialogBody(content));
        if (!buttons.isEmpty()) {
            parts.add(new DialogActions(buttons));
        }
        return List.copyOf(parts);
    }

    /// See [DialogScrim#isAnimating()] — `closing` is about input and [#closed]
    /// is about drawing, and conflating them is why a dialog used not to fade.
    @Override
    public boolean isAnimating() {
        return !closed && phase.isRunning();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        onMotion.accept(context.reducedMotion());
        if (closed) {
            return Box.of();
        }
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
