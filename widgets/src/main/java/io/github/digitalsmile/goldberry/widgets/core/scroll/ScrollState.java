package io.github.digitalsmile.goldberry.widgets.core.scroll;

import io.github.digitalsmile.goldberry.input.Extent;
import io.github.digitalsmile.goldberry.input.Measured;
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

    /// What the last frame laid this viewport and its content out as.
    ///
    /// The clamp does not need these — the extents arrive on the event that asks
    /// to move (ADR-0116) — but a **scrollbar** does: a thumb whose length says
    /// how much of the document is visible has to be right before anything has
    /// been touched. They arrive through [Measured], which is the other direction
    /// ([ADR-0117]).
    private Extent viewport = Extent.NONE;
    private Extent content = Extent.NONE;

    /// When the bars were last woken, and whether something is holding them open.
    private final ScrollFade fade = new ScrollFade();

    /// Which bar the pointer is dragging, or null.
    private Boolean draggingVertical;

    @Override
    public Widget build(BuildContext context) {
        var scroll = widget();
        return new ScrollViewport(
                scroll.children(), scroll.axis(), offsetX, offsetY,
                viewport, content, fade, this::moveTo,
                draggingVertical, this::drag, this::measured, scroll.attributes());
    }

    /// Told what the last frame produced, by the router that holds the painted
    /// rectangles.
    ///
    /// ## Why this rebuilds, and why that terminates
    ///
    /// A thumb's length is drawn from these, so they have to reach a `build` —
    /// and nothing else would take them there. The obvious worry is the loop:
    /// a measurement causes a rebuild, which causes a frame, which produces a
    /// measurement.
    ///
    /// It terminates because **nothing this rebuild draws can change what was
    /// measured**. The bars are absolutely positioned, so they take no space from
    /// the content and none from the viewport; the second frame measures exactly
    /// what the first did, the router sees no change and notifies nobody
    /// ([ADR-0117]). One extra frame when a window resizes, and none after it.
    ///
    /// The guard here is belt to the router's braces. It is cheap, and the thing
    /// it protects against — a scroll view repainting forever — is expensive
    /// enough to be worth two comparisons.
    private void measured(Extent bounds, Extent part) {
        if (bounds.equals(viewport) && part.equals(content)) {
            return;
        }
        setState(() -> {
            viewport = bounds;
            content = part;
        });
    }

    /// Starts or ends a thumb drag, holding the bars open for its duration.
    private void drag(Boolean vertical, Boolean active) {
        setState(() -> {
            draggingVertical = active ? vertical : null;
            fade.hold(active);
        });
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
            // The bars wake on any movement, whatever caused it -- a wheel, a
            // key, a drag or a track click. `moveTo` is the one place all four
            // arrive, which is why the wake is here rather than in each handler.
            fade.woken();
        });
    }
}
