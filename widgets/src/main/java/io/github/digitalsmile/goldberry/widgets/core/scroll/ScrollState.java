package io.github.digitalsmile.goldberry.widgets.core.scroll;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;

/// Where a [Scroll] is — the whole of what it remembers.
///
/// `docs/core-widgets.md` §1: "scroll position is retained state surviving
/// rebuilds". This is that state, and it needs no key and no application field to
/// survive one: the element tree keeps a state across every rebuild of the widget
/// that described it, which is what the element layer is for
/// ([ADR-0052](../../../../../../../../book/src/adr/0052-state-is-a-plain-object-and-setstate-defers.md)).
///
/// **Unlike a control's value, this is genuinely the widget's own.** ADR-0063
/// sends every *value* up to the application and reads it back down through
/// `bind`, and a scroll position is the exception that proves the rule: it is not
/// a value the application has an opinion about, it is where a rectangle happens
/// to be. An application that made a list scroll to the top would be doing so
/// through `scrollIntoView`, not by owning the offset.
///
/// Nothing here clamps. The clamp is [ScrollViewport]'s, because clamping needs
/// the two extents and only the viewport is handed them — this stores what it is
/// told ([ADR-0116]).
final class ScrollState extends State<Scroll> {

    private double offsetX;
    private double offsetY;

    @Override
    public Widget build(BuildContext context) {
        var scroll = widget();
        return new ScrollViewport(
                scroll.children(), scroll.axis(), offsetX, offsetY,
                this::moveTo, scroll.attributes());
    }

    /// Takes the offset the viewport arrived at and asks for a frame.
    ///
    /// `setState` rather than a plain assignment, so the move reaches the screen
    /// by the route every other change takes: the element is marked dirty and the
    /// tree rebuilds it once, however many wheel events arrived in one frame.
    private void moveTo(double x, double y) {
        if (x == offsetX && y == offsetY) {
            return;
        }
        setState(() -> {
            offsetX = x;
            offsetY = y;
        });
    }
}
