package io.github.digitalsmile.goldberry.input;

/// A widget that wants to be told what size it came out as.
///
/// Opt-in, like [Handles], and for the same reason: most widgets do not care,
/// and a notification walked over every node in the tree would cost the depth of
/// it rather than the number of interested nodes.
///
/// ## Why this exists, when [Extent] on an event already did
///
/// A scroll view's *clamp* only needs geometry when something asks it to move,
/// so the extents on a [PointerEvent] were enough
/// ([ADR-0116](../../../../../../book/src/adr/0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md)).
/// A scroll**bar** is different: it has to be *drawn* in proportion to content
/// nobody has touched. A thumb whose length says how much of the document is
/// visible must be right on the first frame, before any input, or the widget
/// that exists to say "there is more below" says nothing until you have already
/// found out
/// ([ADR-0117](../../../../../../book/src/adr/0117-a-widget-may-be-told-what-it-measured.md)).
///
/// So this is the other direction: not "answer a question the input asked", but
/// "here is what you turned out to be".
///
/// ## The rules that keep it from being a layout engine
///
/// 1. **It is last frame's.** Called after a frame is laid out and painted, with
///    what that frame produced. A widget acting on it is one frame behind, which
///    is invisible for a thumb and would be wrong for anything load-bearing.
/// 2. **It fires only on a change.** An unchanging window notifies nothing, so
///    the idle frame loop stays idle (§1.7).
/// 3. **What it triggers must not change what it reports.** A widget that
///    resized itself from this would be told a new size, resize again, and never
///    settle. The one implementation obeys it by construction: a scrollbar is
///    absolutely positioned, so nothing it draws can change the rectangle it was
///    measured against.
public interface Measured extends io.github.digitalsmile.goldberry.widget.Widget {

    /// This widget's own box, and the part it names through
    /// [Handles#localPart()], as the last frame laid them out.
    ///
    /// The same pair a [PointerEvent] carries, so a widget reading geometry
    /// reads one shape however it arrived.
    ///
    /// @param bounds this widget's border box
    /// @param part   the named part's, or `bounds` again when none is named
    void measured(Extent bounds, Extent part);
}
