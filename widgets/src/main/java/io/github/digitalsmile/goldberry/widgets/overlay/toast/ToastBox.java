package io.github.digitalsmile.goldberry.widgets.overlay.toast;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.value.Transform;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Corner;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.core.Phase;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/// One toast on screen — the node a stylesheet calls `toast`.
///
/// ## It hears the pointer for one reason
///
/// §7's "timeout with hover-pause". A toast takes no press of its own — the only
/// thing on it that can be pressed is its action button — but it has to know
/// when the pointer is over it, because a notification that vanishes while
/// somebody is reading it is a notification they did not read.
///
/// The report goes **up to the stack** rather than being handled here, because
/// the clock belongs to the queue: this node is rebuilt whenever anything in the
/// stack changes and could not hold a countdown across that.
///
/// @param toast    what it says
/// @param number   its identity, which is its key: a toast dismissed from the
///                 middle of the stack must not hand its element — and its
///                 half-finished arrival — to the one below it
/// @param corner   which corner the stack is in, and therefore which edge this
///                 slides in from
/// @param phase    where it is in its arrival or its departure
/// @param leaving  whether it has been dismissed, after which it takes no input
/// @param onHover  told when the pointer arrives or leaves
/// @param onAction told when the action button is pressed
record ToastBox(
        Toast toast, int number, Corner corner, Phase phase, boolean leaving,
        Consumer<Boolean> onHover, Runnable onAction)
        implements Widget.Leaf, Styled, Paints, Handles {

    /// §3: "in: slide 16px from edge".
    private static final double TRAVEL = 16;

    @Override
    public String cssType() {
        return "toast";
    }

    /// Its number, so the element tree pairs a toast with the same toast across
    /// a rebuild rather than by position — see [#number].
    @Override
    public Object key() {
        return number;
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        var parts = new ArrayList<Widget>(2);
        parts.add(new ToastText(toast.text()));
        if (toast.hasAction()) {
            // A `ghost` button, which is what an action link is everywhere else
            // in the catalog: a toast is not a place to put a filled button, and
            // the only thing on a toast that can be pressed does not need to
            // shout to be found.
            parts.add(new Button(toast.label(), onAction)
                    .withAttributes(Attributes.NONE.classes("ghost")));
        }
        return List.copyOf(parts);
    }

    /// §7's hover-pause, reported rather than handled — see the class note.
    ///
    /// A leaving toast reports nothing: its clock has already stopped, and the
    /// pointer arriving during the last 160ms must not restart anything.
    @Override
    public void onPointer(PointerEvent event) {
        if (leaving) {
            return;
        }
        switch (event.kind()) {
            case ENTERED -> onHover.accept(true);
            case EXITED -> onHover.accept(false);
            default -> {
            }
        }
    }

    @Override
    public boolean isAnimating() {
        return phase.isRunning();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        var box = Box.of().style(style).children(boxes.toArray(Box[]::new));
        if (context.reducedMotion()) {
            phase.skip();
            return box;
        }
        var progress = phase.progressAt(context.nowMillis());
        var going = phase.kind() == Phase.Kind.LEAVING;
        var visible = going ? 1 - progress : progress;
        if (visible >= 1) {
            return box;
        }
        if (going) {
            // §3: "out: `opacity` base" — and nothing else. A toast that slid out
            // as well would be moving while the stack under it is also moving,
            // which is two animations saying different things about one place.
            return box.opacity(visible);
        }
        // In from the nearest edge: a stack on the right slides in from the
        // right. The direction is the corner's, because the corner is where the
        // stack is and the edge it is against is the one it comes from.
        var from = corner == Corner.TOP_END || corner == Corner.BOTTOM_END ? TRAVEL : -TRAVEL;
        return box.opacity(visible)
                .transform(Transform.of(new Transform.Function.Translate(
                        Transform.Length.px((1 - visible) * from), Transform.Length.ZERO)));
    }

    /// What it says. A part, so a stylesheet can reach it and nothing can build
    /// one ([ADR-0065]) — and a child box rather than text on the toast's own
    /// node, because a box with text is a measured leaf and Yoga never lays a
    /// measured node's children out, which would leave nowhere for the button.
    record ToastText(String text) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "toast-text";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style)
                    .children(Box.text(context.paragraph(style, text), style.color()));
        }
    }
}
