package dev.goldberry.widgets.overlay.dialog;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.core.presence.Phase;

/// The veil over the window, and the half of a dialog's modality that is
/// geometry rather than a rule.
///
/// It fills the window — [dev.goldberry.Overlay#filling] —
/// and it is **opaque to the pointer everywhere**, so nothing behind it can be
/// clicked. A filling overlay takes the pointer wherever it draws, which makes a
/// thing modal without any code saying so. The keyboard has no position and cannot be
/// handled this way, which is what [DialogPanel]'s
/// [Handles#isModal] is for.
///
/// A press on it is `Esc` — see [Dialog]'s note.
///
/// ## There is no closed scrim
///
/// A scrim takes every press wherever it is, so one left in the tree after its
/// dialog has gone would lock the window with nothing on screen to say why.
/// It used to be: a scrim that had finished fading drew nothing and still
/// filled the window. Now a dialog whose fade is over describes no scrim at
/// all, and takes its overlay off the window as well, so nothing is left to
/// hit and nothing is left to consume.
///
/// @param panel   the dialog itself, centred in the window
/// @param onPress what a press on the veil means
/// @param phase   the shared opening or closing, so the veil and the panel move
///                on one clock rather than two that agree by construction
/// @param closing whether input has stopped, which it does the instant closing
///                starts
record DialogScrim(Widget panel, Runnable onPress, Phase phase, boolean closing)
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
        return List.of(panel);
    }

    /// Input is disabled the instant closing starts, so there are no ghost clicks.
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
    /// `closing` turns input off. A `LEAVING` phase never settles itself, and
    /// what turns the animation off is the scrim leaving the tree when the fade
    /// is over — see the class note.
    @Override
    public boolean isAnimating() {
        return phase.isRunning();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var box = Box.of().style(style).children(children.toArray(Box[]::new));
        if (context.reducedMotion()) {
            phase.skip();
            return box;
        }
        // The veil animates `opacity` and nothing else. It is already the
        // size of the window, and a veil that scaled would show the window's
        // corners through it while it moved.
        var progress = phase.progressAt(context.nowMillis());
        var visible = phase.kind() == Phase.Kind.LEAVING ? 1 - progress : progress;
        return visible >= 1 ? box : box.opacity(visible);
    }
}
