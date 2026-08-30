package io.github.digitalsmile.goldberry.widgets.data.linechart;

/// What the last painted frame turned out to be, kept for the pointer that
/// arrives after it.
///
/// A plot's geometry is decided **in the painter**, because it depends on the
/// size and on the shaped axis labels, and neither is available when a pointer
/// event is handled: an event carries a rectangle
/// ([io.github.digitalsmile.goldberry.input.event.PointerEvent.Local]) and no
/// text stack. So the painter leaves its answer here and the pointer reads it.
///
/// **This is ADR-0054's
/// rule one level down.** The toolkit routes a pointer against a snapshot of the
/// frame that was painted, rather than against a fresh layout, for the reason
/// that the frame is what the user was looking at when they pointed at it. A
/// chart deciding *which point* they pointed at owes the same answer: against
/// the crosshair positions of the frame on screen, not against an arithmetic
/// that may have moved since.
///
/// It is a mutable holder rather than a value because it is written by a painter
/// and read by an event handler, which are two moments in the same frame's life
/// with an immutable widget rebuilt between them. The state owns one and hands
/// it to every rebuild, so it survives them.
///
/// Confined to the UI thread, like everything in the widget layer. Null until
/// the first paint, which is why every reader checks: a pointer cannot reach a
/// plot that has never been drawn, but a test can.
final class PaintedGeometry {

    private PlotGeometry geometry;

    /// The geometry of the last painted frame, or null before the first.
    PlotGeometry geometry() {
        return geometry;
    }

    /// Called by the painter, once per frame, with what it worked out.
    void paintedAs(PlotGeometry value) {
        this.geometry = value;
    }
}
