package io.github.digitalsmile.goldberry.widgets.overlay.toast;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.value.Transform;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.handler.Measured;
import io.github.digitalsmile.goldberry.input.hit.Extent;
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
import java.util.function.DoubleConsumer;

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
/// @param reflow   where it is on its way to because a sibling went, or null
/// @param onHover  told when the pointer arrives or leaves
/// @param onAction told when the action button is pressed
/// @param onHeight told how tall it came out — see [#measured]
record ToastBox(
        Toast toast, int number, Corner corner, Phase phase, boolean leaving,
        Reflow reflow, Consumer<Boolean> onHover, Runnable onAction,
        DoubleConsumer onHeight)
        implements Widget.Leaf, Styled, Paints, Handles, Measured {

    /// §3: "in: slide 16px from edge".
    private static final double TRAVEL = 16;

    /// A journey to where the stack now puts this toast — §3's "siblings reflow
    /// via `translate`", which is the one movement effect the design system
    /// sanctions.
    ///
    /// The distance is **unsigned**: how far, and not which way. Which way is the
    /// corner's, for the same reason the arrival's edge is
    /// ([ToasterState#closeTheGap]), and a signed distance would be the one
    /// number in this widget that had to be recomputed when a stack moved corner.
    ///
    /// [Phase.Kind#ENTERING] because a reflow **arrives** somewhere and is then
    /// over — it settles itself on the frame it finishes, which is what stops
    /// [#isAnimating] asking for frames forever.
    ///
    /// @param distance how far, in logical pixels
    /// @param phase    the travel, on the frame clock like every other
    record Reflow(double distance, Phase phase) {
    }

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

    /// Both clocks, and the second one is the bug [ADR-0176] filed. A toast that
    /// is settled but still travelling is one nobody asks to repaint, and a
    /// widget nobody repaints does not move: it would stand still for 160ms and
    /// then be somewhere else.
    @Override
    public boolean isAnimating() {
        return phase.isRunning() || (reflow != null && reflow.phase().isRunning());
    }

    /// How tall this toast came out, reported up to the stack.
    ///
    /// Banked against the day it is dismissed: closing the hole it leaves needs
    /// the height of the hole, and `render` runs before Yoga, so a widget cannot
    /// ask what size it came out as
    /// ([ADR-0117](../../../../../../../../book/src/adr/0117-a-widget-may-be-told-what-it-measured.md)).
    ///
    /// This is **last frame's**, which is exactly right here: a toast has to have
    /// been on screen to be dismissed, so by the time the number is wanted it has
    /// been reported. One dismissed before its first paint has no height, and the
    /// stack reads that as no hole to close.
    ///
    /// Obeys [Measured]'s third rule — nothing this triggers changes what it
    /// reports. A reflow is a `transform`, and §3 chose one for this reason: the
    /// box it moves is laid out where it always was.
    @Override
    public void measured(Extent bounds, Extent part) {
        onHeight.accept(bounds.height());
    }

    /// Two movements on two axes, and they are about different things.
    ///
    /// Across is this toast's own arrival; down is a **sibling's** departure. They
    /// compose rather than taking turns, because a toast can still be sliding in
    /// when the one beside it is dismissed, and one of the two effects being
    /// dropped for the length of the other is a hole in the stack that closes
    /// late.
    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        var box = Box.of().style(style).children(boxes.toArray(Box[]::new));
        if (context.reducedMotion()) {
            phase.skip();
            if (reflow != null) {
                reflow.phase().skip();
            }
            return box;
        }
        var now = context.nowMillis();
        var down = reflowAt(now);
        var progress = phase.progressAt(now);
        var going = phase.kind() == Phase.Kind.LEAVING;
        var visible = going ? 1 - progress : progress;
        // In from the nearest edge: a stack on the right slides in from the
        // right. The direction is the corner's, because the corner is where the
        // stack is and the edge it is against is the one it comes from.
        //
        // Arrivals only. §3: "out: `opacity` base" — and nothing else. A toast
        // that slid out as well would be moving while the stack under it is also
        // moving, which is two animations saying different things about one place.
        var across = going || visible >= 1 ? 0
                : (1 - visible)
                        * (corner == Corner.TOP_END || corner == Corner.BOTTOM_END
                                ? TRAVEL : -TRAVEL);
        if (visible < 1) {
            box = box.opacity(visible);
        }
        if (across == 0 && down == 0) {
            return box;
        }
        return box.transform(Transform.of(new Transform.Function.Translate(
                Transform.Length.px(across), Transform.Length.px(down))));
    }

    /// How far this toast is from where the layout has already put it, in
    /// logical pixels, positive downwards.
    ///
    /// **Backwards, and that is the whole trick.** Nothing here moves a toast to
    /// a new place: the sibling has gone, so Yoga has *already* put this one
    /// where it belongs. What the translate does is put it back where it was for
    /// one frame and then let go of it, which is why the offset shrinks to zero
    /// rather than growing from it.
    ///
    /// The sign is the corner's. A stack at the bottom closes a hole by moving
    /// its older toasts **down** toward the corner, so they are drawn `distance`
    /// *up* to begin with; a stack at the top does the opposite.
    private double reflowAt(double now) {
        if (reflow == null) {
            return 0;
        }
        var left = 1 - reflow.phase().progressAt(now);
        return left * (corner.isTop() ? reflow.distance() : -reflow.distance());
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
