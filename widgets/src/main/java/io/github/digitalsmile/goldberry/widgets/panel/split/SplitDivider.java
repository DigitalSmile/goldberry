package io.github.digitalsmile.goldberry.widgets.panel.split;

import io.github.digitalsmile.goldberry.backend.Cursor;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/// The bar between a [SplitPane]'s two children — §5's "separator with value".
///
/// **The only tab stop in a split pane**, which is what §5's "keyboard-resizable
/// when focused" needs: a split whose *panes* were focusable would put a stop
/// between every pair of them, and one that was not focusable at all could not be
/// resized from the keyboard.
///
/// ## The drag anchors, and does not track
///
/// The pointer is somewhere inside a six-point bar. Mapping that position to a
/// fraction of the pane — a slider's arrangement
/// ([ADR-0079](../../../../../../../../book/src/adr/0079-a-slider-reads-the-pointer.md))
/// — would snap the divider so that its centre jumped under the finger on every
/// press, by up to three points, which is visible and feels broken.
///
/// So the gesture is a **translation**: this reports its current offset as a
/// [Handles#gestureAnchor()], the router hands that number back on every event of
/// the gesture as [PointerEvent#anchor()], and the new offset is
/// `anchor + dragX`. That is the knob's arrangement
/// ([ADR-0089](../../../../../../../../book/src/adr/0089-a-knobs-gesture-is-a-rate.md)),
/// and this is the second widget to want it — for a reason that is not the
/// knob's, which is worth noticing: a knob needs the anchor because its value has
/// *already moved* by the second frame; a divider needs it because the pointer's
/// position inside the grab is not the value.
///
/// @param axis     which way the split runs, which decides the cursor and which
///                 arrows do anything
/// @param position the fraction, for `.collapsed` and for a stylesheet
/// @param offset   where the divider is now, in logical pixels — read at press
///                 time to become the anchor
/// @param onDrag   a new offset in logical pixels
/// @param onNudge  a step along the axis, and whether it is a page
/// @param onCollapse what `Enter` asks for
record SplitDivider(
        SplitAxis axis, double position, DoubleSupplier offset, DoubleConsumer onDrag,
        SplitPaneView.Nudge onNudge, Runnable onCollapse)
        implements Widget.Leaf, Styled, Paints, Handles {

    @Override
    public String cssType() {
        return "split-divider";
    }

    /// The axis, so a stylesheet can make a vertical bar and a horizontal one
    /// look right without knowing which is which; and `.collapsed` at either end,
    /// which is the one state `:hover` and `:focus-visible` cannot express — a
    /// divider flush against an edge has no pane on one side of it and should not
    /// look like a divider in the middle that happens to be there.
    @Override
    public Set<String> classes() {
        if (position <= 0 || position >= 1) {
            return Set.of(axis.cssClass(), "collapsed");
        }
        return Set.of(axis.cssClass());
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    /// The offset the whole gesture is measured from — see the class note.
    ///
    /// Read once, on the press. It is a `double` because that is what the router
    /// carries, and it is in **pixels** rather than as a fraction because the
    /// thing being added to it is a pointer travel in pixels.
    @Override
    public double gestureAnchor() {
        return offset.getAsDouble();
    }

    @Override
    public void onPointer(PointerEvent event) {
        switch (event.kind()) {
            // Nothing moves on the press itself. The anchor has been taken by
            // now, and a divider that jumped on being grabbed is exactly what the
            // anchor exists to prevent -- `ScrollBar` makes the same choice for
            // its thumb for the same reason.
            case PRESSED -> {
                if (event.button() == PointerEvent.Button.PRIMARY) {
                    event.consume();
                }
            }
            // MOVED and not a DRAGGED of its own: while a button is held the
            // router keeps sending MOVED to the pressed element, and dragX is NaN
            // whenever it is not a drag -- which is the test below.
            case MOVED -> {
                var travel = axis.isVertical() ? event.dragY() : event.dragX();
                if (Double.isNaN(travel) || Double.isNaN(event.anchor())) {
                    // No button held, or a gesture that began somewhere else.
                    return;
                }
                onDrag.accept(event.anchor() + travel);
                event.consume();
            }
            default -> {
            }
        }
    }

    /// The arrows **along** the axis move the divider; the pair across it is left
    /// alone, because a horizontal split has nothing to say about `Up` and
    /// swallowing the key would stop it reaching whatever does.
    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED || !event.modifiers().none()) {
            return;
        }
        var vertical = axis.isVertical();
        switch (event.key()) {
            case LEFT -> nudge(event, !vertical, -1, false);
            case RIGHT -> nudge(event, !vertical, 1, false);
            case UP -> nudge(event, vertical, -1, false);
            case DOWN -> nudge(event, vertical, 1, false);
            case PAGE_UP -> nudge(event, true, -1, true);
            case PAGE_DOWN -> nudge(event, true, 1, true);
            // Not 0 and 1: `Home` and `End` go as far as the *minimums* allow,
            // which is what the state's clamp does with an out-of-range fraction.
            // Collapsing is `Enter`'s, and only when the split says it may.
            case HOME -> nudge(event, true, -1e6, false);
            case END -> nudge(event, true, 1e6, false);
            case ENTER, SPACE -> {
                if (!event.isRepeat()) {
                    onCollapse.run();
                }
                event.consume();
            }
            default -> {
            }
        }
    }

    private void nudge(KeyEvent event, boolean mine, double steps, boolean page) {
        if (!mine) {
            return;
        }
        onNudge.by(steps, page);
        event.consume();
    }

    /// The resize cursor for this axis, which is the only affordance a six-point
    /// bar has: it is too thin to carry a grip and §1.6 has no dot pattern for
    /// one.
    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style)
                .cursor(axis.isVertical() ? Cursor.NS_RESIZE : Cursor.EW_RESIZE)
                .size(axis.isVertical()
                                ? io.github.digitalsmile.goldberry.natives.yoga.StyleLength.UNDEFINED
                                : io.github.digitalsmile.goldberry.natives.yoga.StyleLength
                                        .points(SplitPaneView.DIVIDER),
                        axis.isVertical()
                                ? io.github.digitalsmile.goldberry.natives.yoga.StyleLength
                                        .points(SplitPaneView.DIVIDER)
                                : io.github.digitalsmile.goldberry.natives.yoga.StyleLength.UNDEFINED)
                .shrink(0)
                .grow(0)
                .children(children.toArray(Box[]::new));
    }
}
