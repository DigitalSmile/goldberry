package io.github.digitalsmile.goldberry.widgets.overlay.dialog;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.core.Phase;

import java.util.List;
import java.util.Set;

/// The veil over the window, and the half of a dialog's modality that is
/// geometry rather than a rule.
///
/// It fills the window — [io.github.digitalsmile.goldberry.Overlay#filling] —
/// and it is **opaque to the pointer everywhere**, so nothing behind it can be
/// clicked. `tour`'s veil discovered that
/// ([ADR-0121](../../../../../../../../book/src/adr/0121-a-tour-is-a-veil-and-a-sequence.md)):
/// a filling overlay takes the pointer wherever it draws, which makes a thing
/// modal without any code saying so. The keyboard has no position and cannot be
/// handled this way, which is what [DialogPanel]'s
/// [Handles#isModal] is for.
///
/// A press on it is `Esc` — see [Dialog]'s note.
///
/// @param panel   the dialog itself, centred in the window
/// @param onPress what a press on the veil means
/// @param phase   the shared opening or closing, so the veil and the panel move
///                on one clock rather than two that agree by construction
/// @param closing whether input has stopped, per §1.7's overlay lifecycle
/// @param closed  whether the closing animation has run out, after which there
///                is nothing left to draw and nothing left to ask frames for
record DialogScrim(Widget panel, Runnable onPress, Phase phase, boolean closing, boolean closed)
        implements Widget.Leaf, Styled, Paints, Handles {

    @Override
    public String cssType() {
        return "dialog-scrim";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        return closed ? List.of() : List.of(panel);
    }

    /// §1.7: "input is disabled the instant closing starts (no ghost clicks)".
    ///
    /// A dialog fading out is still on the screen and still covers the window, so
    /// without this a press in the last 160ms would answer a question that has
    /// already been answered.
    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() != PointerEvent.Kind.CLICKED) {
            // Consumed whatever it is, which is the modality: a press, a release,
            // a wheel and a move all stop here rather than reaching the window.
            event.consume();
            return;
        }
        event.consume();
        if (!closing) {
            onPress.run();
        }
    }

    /// **Not `!closing`.** A dialog that stopped asking for frames the moment it
    /// began closing would not fade: it would stand still for 160ms and vanish.
    /// `closing` turns input off; only [#closed] turns the animation off, and a
    /// `LEAVING` phase needs something to turn it off because it never settles
    /// itself.
    @Override
    public boolean isAnimating() {
        return !closed && phase.isRunning();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        if (closed) {
            // Not `style`: a veil that has gone keeps no background, or the
            // window stays dimmed under a dialog nobody can see.
            return Box.of();
        }
        var box = Box.of().style(style).children(children.toArray(Box[]::new));
        if (context.reducedMotion()) {
            phase.skip();
            return box;
        }
        // §3: the veil animates `opacity` and nothing else. It is already the
        // size of the window, and a veil that scaled would show the window's
        // corners through it while it moved.
        var progress = phase.progressAt(context.nowMillis());
        var visible = phase.kind() == Phase.Kind.LEAVING ? 1 - progress : progress;
        return visible >= 1 ? box : box.opacity(visible);
    }
}
