package io.github.digitalsmile.goldberry.input;

/// How big a node was when it was last painted, in logical pixels.
///
/// The answer to the one question a widget cannot ask itself. `build` and
/// `render` both run **before** Yoga, so a widget describing itself has no idea
/// what size it came out as — which is
/// [ADR-0080](../../../../../../book/src/adr/0080-a-value-is-measured-along-a-part.md)'s
/// finding, and the reason a slider reads its position off the router rather
/// than computing one.
///
/// A scroll view is the first widget that needs this **outside** a pointer
/// event. Its whole job is the difference between two rectangles — how much
/// taller its content is than its viewport — and `PageDown` has to know that
/// difference just as much as the wheel does, while carrying no position at all
/// ([ADR-0116](../../../../../../book/src/adr/0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md)).
/// So this is a size and nothing else: [PointerEvent.Local] is where a
/// *position* within a rectangle lives, and a key event has none.
///
/// Both events carry two of these — the node's own box, and the part it names
/// through [Handles#localPart()]. A widget that names no part gets the same
/// rectangle twice.
///
/// @param width  the border box's width, or 0 if it has never been painted
/// @param height its height
public record Extent(float width, float height) {

    /// A node with no painted rectangle — one a test poked directly, or one
    /// built this frame and not yet drawn.
    ///
    /// Zero rather than null so that arithmetic on it is finite: a scroll view
    /// asking for its overflow before its first paint gets 0, which reads as
    /// "nothing to scroll" and is the right answer for a viewport nobody has
    /// seen yet.
    public static final Extent NONE = new Extent(0, 0);

    /// How much `content` exceeds this along the horizontal axis, never negative.
    ///
    /// The number a scroll view clamps against, and the reason this record has
    /// any behaviour at all: every caller wants the difference floored at zero —
    /// content shorter than its viewport does not scroll *backwards*.
    public float overflowX(Extent content) {
        return Math.max(0, content.width - width);
    }

    /// The same along the vertical axis.
    public float overflowY(Extent content) {
        return Math.max(0, content.height - height);
    }
}
