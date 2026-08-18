package io.github.digitalsmile.goldberry.widgets.core.scroll;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.Extent;
import io.github.digitalsmile.goldberry.input.Handles;
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
        ScrollTarget onScroll, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles {

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
        return List.of(new ScrollContent(children, axis, offsetX, offsetY));
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

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().children(boxes.toArray(Box[]::new)).style(style)
                .direction(FlexDirection.COLUMN)
                // The one property a stylesheet must not be able to take back.
                // Yoga reads it for sizing -- a child may exceed this box without
                // it growing -- and the painter reads it as a clip (ADR-0114).
                .overflow(Overflow.HIDDEN);
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
