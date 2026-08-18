package io.github.digitalsmile.goldberry.widgets.core.scroll;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.Extent;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.Measured;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.natives.yoga.Overflow;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The rectangle a [Scroll] shows through: the CSS type `scroll`, the node that
/// clips, and the node that takes the wheel and the keys.
///
/// ## Three facts, and a widget can only reach them here
///
/// Scrolling is arithmetic on two rectangles — the viewport's and the content's —
/// and a widget cannot measure either. `build` and `render` both run before Yoga,
/// which is [ADR-0080](../../../../../../../../book/src/adr/0080-a-value-is-measured-along-a-part.md)'s
/// finding. So the two extents arrive **on the event**, resolved by the router
/// out of the snapshot the last paint left behind: [PointerEvent#bounds()] is this
/// viewport and [PointerEvent#part()] is the `scroll-content` named by
/// [#localPart()]
/// ([ADR-0116](../../../../../../../../book/src/adr/0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md)).
///
/// That is also why the keyboard works. A key event carries no position and
/// `PageDown` needs no position — but it needs both extents exactly as the wheel
/// does, and before this widget nothing put a size on a [KeyEvent].
///
/// ## The clip is the widget's, not the stylesheet's
///
/// `overflow: hidden` is applied after the cascade, the way `row` and `column`
/// apply their direction: a `scroll` a stylesheet could un-clip would be a name
/// that lies, and the clip is the whole of what distinguishes this from a
/// `column` that overflows.
///
/// ## At the edge it lets go
///
/// A wheel is consumed only when it actually moved something. At the top of a
/// list a further scroll up is left unconsumed and bubbles, which is §2.4's
/// "inner scroller consumes until its edge, then chains to the ancestor" — got
/// for free by the router's ordinary bubble path rather than by anything here
/// knowing an ancestor exists.
record ScrollViewport(
        List<Widget> children, ScrollAxis axis, double offsetX, double offsetY,
        Extent viewport, Extent content, ScrollFade fade, ScrollTarget onScroll,
        Boolean draggingVertical, java.util.function.BiConsumer<Boolean, Boolean> onDrag,
        java.util.function.BiConsumer<Extent, Extent> onMeasured, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles, Measured {

    /// What one wheel line moves, in logical pixels.
    ///
    /// Three of these is the conventional notch — around 60px, which is what
    /// every other application on the machine does. Deriving it from a row height
    /// would be worse: a list that scrolled exactly one row per notch is markedly
    /// slower than the rest of the desktop.
    ///
    /// A constant and **not** a token, which is a gap rather than a decision.
    /// §3 says metrics ship as component tokens an application may override, and
    /// nothing lets a widget read a resolved custom property — so a
    /// `--gb-scroll-line` would be a number an author could set and this could
    /// not see. Shipping the number here at least means it is where its only
    /// consumer is (ADR-0116).
    static final double LINE = 20;

    /// How much of a viewport `PageUp` and `PageDown` leave behind.
    ///
    /// A page that moved a whole viewport would leave nothing on screen that was
    /// there before, and the reader would have no idea whether they had missed a
    /// line. Every document reader overlaps by a little for that reason.
    static final double PAGE_OVERLAP = 24;

    /// What an arrow key moves. A line, because that is what an arrow means in
    /// every list — and `scroll` has no rows to step through, so a line is the
    /// only unit it has.
    static final double ARROW = LINE;

    @Override
    public String cssType() {
        return "scroll";
    }

    @Override
    public String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public List<Widget> children() {
        var nodes = new java.util.ArrayList<Widget>(3);
        nodes.add(new ScrollContent(children, axis, offsetX, offsetY));
        // The bars are **last**, so they paint over the content -- §2.4 calls
        // them overlay scrollbars, and paint order is the whole of what makes
        // them one. They are absolutely positioned by the stylesheet, so being
        // in the flow costs the content nothing.
        if (axis.isVertical()) {
            nodes.add(bar(true, viewport.height(), content.height(), offsetY));
        }
        if (axis.isHorizontal()) {
            nodes.add(bar(false, viewport.width(), content.width(), offsetX));
        }
        return List.copyOf(nodes);
    }

    private ScrollBar bar(boolean vertical, double along, double contentAlong, double offset) {
        return new ScrollBar(vertical, along, contentAlong, offset,
                value -> {
                    // A bar reports one axis; the other keeps what it had.
                    if (vertical) {
                        scrollTo(offsetX, value, viewport, content);
                    } else {
                        scrollTo(value, offsetY, viewport, content);
                    }
                },
                Boolean.valueOf(vertical).equals(draggingVertical),
                active -> onDrag.accept(vertical, active));
    }

    /// Told what the last frame laid this out as, so the bars can be drawn in
    /// proportion to content nobody has touched ([ADR-0117]).
    @Override
    public void measured(Extent bounds, Extent part) {
        onMeasured.accept(bounds, part);
    }

    /// The content, so [PointerEvent#part()] measures the thing being moved.
    @Override
    public String localPart() {
        return "scroll-content";
    }

    /// §1: "keyboard (PgUp/PgDn/Home/End/arrows **when focused**)".
    @Override
    public boolean isFocusable() {
        return true;
    }

    /// While the bars are on their way out — §1.7's idle loop would otherwise
    /// paint them once at whatever opacity it caught and stop ([ADR-0081]).
    @Override
    public boolean isAnimating() {
        return fade.isAnimating();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        // The only place a widget is handed a clock, and therefore the only place
        // "when did this move" can be answered.
        fade.stamp(context.nowMillis());
        var opacity = fade.opacity();
        return Box.of().children(fadeBars(boxes, opacity).toArray(Box[]::new)).style(style)
                .direction(FlexDirection.COLUMN)
                // The one property a stylesheet must not be able to take back.
                // Yoga reads it for sizing -- a child may exceed this box without
                // it growing -- and the painter reads it as a clip (ADR-0114).
                .overflow(Overflow.HIDDEN);
    }

    /// The children with the bars faded to `opacity` and the content left alone.
    ///
    /// Applied to the boxes rather than through the cascade because the opacity
    /// is a function of the clock and no selector can express *when* — the same
    /// reason `spinner` draws itself rather than declaring a transition.
    private List<Box> fadeBars(List<Box> boxes, double opacity) {
        if (opacity >= 1) {
            return boxes;
        }
        var out = new java.util.ArrayList<Box>(boxes.size());
        for (var i = 0; i < boxes.size(); i++) {
            // The first child is the content; every one after it is a bar.
            out.add(i == 0 ? boxes.get(i) : boxes.get(i).opacity(opacity));
        }
        return out;
    }

    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() != PointerEvent.Kind.WHEEL) {
            return;
        }
        // The fraction, not the detents: this is a distance, and a trackpad's
        // eighths are what stop it moving in jerks (ADR-0115).
        var moved = scrollBy(event.deltaX() * LINE, event.deltaY() * LINE,
                event.bounds(), event.part());
        if (moved) {
            event.consume();
        }
    }

    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED) {
            return;
        }
        var viewport = event.bounds();
        var content = event.part();
        var page = Math.max(LINE, viewport.height() - PAGE_OVERLAP);
        var moved = switch (event.key()) {
            case PAGE_DOWN -> scrollBy(0, page, viewport, content);
            case PAGE_UP -> scrollBy(0, -page, viewport, content);
            case DOWN -> scrollBy(0, ARROW, viewport, content);
            case UP -> scrollBy(0, -ARROW, viewport, content);
            case RIGHT -> scrollBy(ARROW, 0, viewport, content);
            case LEFT -> scrollBy(-ARROW, 0, viewport, content);
            // Absolute rather than a large relative move, so Home reaches the top
            // of a document of any length in one press.
            case HOME -> scrollTo(0, 0, viewport, content);
            case END -> scrollTo(viewport.overflowX(content), viewport.overflowY(content),
                    viewport, content);
            default -> false;
        };
        if (moved) {
            event.consume();
        }
    }

    /// Asks for a move of `dx`, `dy` from where it is now.
    private boolean scrollBy(double dx, double dy, Extent viewport, Extent content) {
        return scrollTo(offsetX + dx, offsetY + dy, viewport, content);
    }

    /// Asks for an absolute offset, clamped to what there is to show.
    ///
    /// **Hard edges, no overscroll bounce** (§2.4), which is what the clamp is:
    /// there is no state for "past the end" because nothing may be there.
    ///
    /// Returns whether anything actually moved — which is what the caller turns
    /// into consuming the event, and therefore what decides whether an ancestor
    /// scroller gets a turn.
    private boolean scrollTo(double x, double y, Extent viewport, Extent content) {
        var wantX = axis.isHorizontal() ? clamp(x, viewport.overflowX(content)) : offsetX;
        var wantY = axis.isVertical() ? clamp(y, viewport.overflowY(content)) : offsetY;
        if (wantX == offsetX && wantY == offsetY) {
            return false;
        }
        onScroll.moveTo(wantX, wantY);
        return true;
    }

    private static double clamp(double value, double max) {
        return value < 0 ? 0 : value > max ? max : value;
    }
}
