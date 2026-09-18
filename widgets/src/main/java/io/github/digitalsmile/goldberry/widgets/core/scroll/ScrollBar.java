package io.github.digitalsmile.goldberry.widgets.core.scroll;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.layout.FlexDirection;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// One axis's scrollbar — §2.4's overlay bar, its track and the thumb in it.
///
/// ## The arithmetic, in one place
///
/// A bar is three numbers and they all come from the two extents the viewport was
/// measured at (ADR-0117):
///
/// ```
/// track      = the viewport's length along this axis
/// thumb      = track × (viewport / content), floored at MIN_LENGTH
/// travel     = (offset / overflow) × (track − thumb)
/// ```
///
/// The floor is why the thumb is not simply proportional. A document a hundred
/// screens long would give a 1px thumb on a 100px track — proportionally honest
/// and impossible to grab — so it stops at 24 and the proportion stops being
/// exact for very long documents. Every scrollbar ever written makes this trade.
///
/// ## Dragging is a rate, and the rate is not 1:1
///
/// A thumb dragged one pixel moves the content by `overflow / (track − thumb)`
/// pixels, which is more than one. That is not a violation of §1.7's "drags track
/// the pointer 1:1" — the *thumb* tracks the pointer exactly, and the content is
/// what it is pointing at.
///
/// What the thumb tracks is the pointer's **travel**, so the offset the press
/// began at is this bar's [Handles#gestureAnchor()] and every move adds the
/// distance dragged since (ADR-0089). A slider needs none of that, because its
/// value is where the pointer is along the track and a slider's thumb has no
/// length worth speaking of (ADR-0079) — a scrollbar's has, and reading the
/// position here instead gives the same answer wherever on the thumb the grab
/// landed. That answer is "the thumb's middle is under the pointer", so a thumb
/// grabbed near its edge jumps by up to half its length on the first move.
///
/// ## A click on the track pages
///
/// §2.4: "track-click pages". Which way is decided by whether the click landed
/// before or after the thumb, which is the only thing that could be meant.
///
/// @param vertical  whether this bar runs down the viewport's right edge
/// @param viewport  how long the viewport is along this axis
/// @param content   how long the content is
/// @param offset    where the viewport currently is
/// @param onScroll  where an absolute offset along this axis is reported
/// @param dragging  whether this bar is being dragged
/// @param onDrag    told when a drag starts and ends, so the state can hold the
///                  bars open for its duration
///
/// ## Not only `scroll`'s
///
/// Public so that a control that scrolls its own content — `text-area`, which
/// cannot sit in a `scroll` because the two would both decide its height — draws
/// §2.4's bar rather than a second implementation of it (ADR-0362). It is still a
/// **part**: nothing in markup names one.
public record ScrollBar(
        boolean vertical,
        double viewport,
        double content,
        double offset,
        java.util.function.DoubleConsumer onScroll,
        boolean dragging,
        java.util.function.Consumer<Boolean> onDrag)
        implements Widget.Leaf, Styled, Paints, Handles {

    /// The shortest a thumb may get, in logical pixels.
    ///
    /// Below this it is not a target any pointer can reliably hit, and §13's
    /// "hit targets ≥ 32" is about the *hit* area rather than the paint — a 6px
    /// bar is already below it and is the design system's own number, so what is
    /// defended here is the length rather than the width.
    static final double MIN_LENGTH = 24;

    @Override
    public String cssType() {
        return "scrollbar";
    }

    @Override
    public Set<String> classes() {
        return Set.of(vertical ? "vertical" : "horizontal");
    }

    /// How much there is to scroll — 0 when the content fits, which is when this
    /// bar draws nothing at all.
    double overflow() {
        return Math.max(0, content - viewport);
    }

    /// How long the thumb is, floored so it stays grabbable.
    double thumbLength() {
        if (content <= 0 || viewport <= 0) {
            return 0;
        }
        return Math.max(MIN_LENGTH, Math.min(viewport, viewport * (viewport / content)));
    }

    /// How far along the track the thumb sits.
    private double thumbOffset() {
        var travel = viewport - thumbLength();
        var range = overflow();
        return range <= 0 || travel <= 0 ? 0 : offset / range * travel;
    }

    @Override
    public List<Widget> children() {
        return overflow() <= 0
                // Nothing to scroll, so no thumb. The bar itself stays in the
                // tree: it is absolutely positioned and paints nothing without a
                // thumb, and removing it would churn the element tree every time
                // a window crossed the threshold where its content fits.
                ? List.of()
                : List.of(new ScrollThumb(vertical, thumbLength(), thumbOffset(), dragging));
    }

    @Override
    public void onPointer(PointerEvent event) {
        if (overflow() <= 0) {
            return;
        }
        switch (event.kind()) {
            case PRESSED -> {
                if (event.button() != PointerEvent.Button.PRIMARY) {
                    return;
                }
                var along = along(event);
                var thumbStart = thumbOffset();
                if (along >= thumbStart && along <= thumbStart + thumbLength()) {
                    // On the thumb: start a drag and move nothing yet, so a click
                    // that grabs it does not also jump it under the finger.
                    onDrag.accept(true);
                } else {
                    // §2.4's track-click paging. A whole viewport, not a page
                    // less an overlap: the user pointed at a place rather than
                    // asking to read on, so the intent is "go roughly there".
                    onScroll.accept(along < thumbStart ? offset - viewport : offset + viewport);
                }
                event.consume();
            }
            case MOVED -> {
                var travelled = vertical ? event.dragY() : event.dragX();
                if (!dragging || Double.isNaN(travelled) || Double.isNaN(event.anchor())) {
                    return;
                }
                // **How far the pointer has come since the grab**, mapped through
                // the travel rather than through the whole bar -- the same
                // correction ADR-0080 made for a slider's track. A *position*
                // read off the track cannot say where on the thumb the grab
                // landed, so it recentres the thumb under the pointer and throws
                // a thumb grabbed near its edge by half its length.
                var travel = viewport - thumbLength();
                if (travel > 0) {
                    onScroll.accept(event.anchor() + travelled / travel * overflow());
                }
                event.consume();
            }
            case RELEASED -> {
                if (dragging) {
                    onDrag.accept(false);
                    event.consume();
                }
            }
            default -> {}
        }
    }

    /// The offset the viewport was at when the gesture began.
    ///
    /// Asked on every press on this bar, a track click included, and harmless
    /// there: a click that pages does not start a drag, so nothing reads it.
    @Override
    public double gestureAnchor() {
        return offset;
    }

    /// Where `event` landed along this bar, in logical pixels from its start.
    private double along(PointerEvent event) {
        return vertical ? event.local().y() : event.local().x();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of()
                .children(children.toArray(Box[]::new))
                .style(style)
                .direction(vertical ? FlexDirection.COLUMN : FlexDirection.ROW);
    }
}
