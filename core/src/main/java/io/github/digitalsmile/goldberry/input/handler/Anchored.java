package io.github.digitalsmile.goldberry.input.handler;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.widget.Widget;

/// A widget that wants to be told when the content under it **moved**, rather
/// than merely got bigger.
///
/// The fourth of the geometry facilities, and the only one that reports a
/// *difference* between two frames:
///
/// | | answers | arrives |
/// |---|---|---|
/// | `Extent` on an event | how big are these two boxes | when input asks |
/// | [Measured] | how big did I turn out | once a frame, on a change |
/// | [Located] | where am I, and what clips me | once a frame, on a change |
/// | [Anchored] | **how far did my content slide under me** | once a frame |
///
/// The first row is
/// [io.github.digitalsmile.goldberry.input.hit.Extent].
///
/// ## The question [Measured] cannot answer
///
/// A chat timeline that pages older messages in wants the reader's line to stay
/// where it is: the offset has to move down by exactly the height that was
/// inserted *above* the viewport. [Measured] reports that the content got
/// taller, and a widget that acted on that alone would also shift when a message
/// arrived at the **bottom**, which adds the same number of pixels and must move
/// nothing (`docs/gaps.md` G48).
///
/// Telling the two apart needs one fact nothing in the toolkit had: **where a
/// node that was already on screen has ended up inside its container**. That is
/// a difference between two frames rather than a property of either, and only
/// the thing holding both frames' rectangles can take it.
///
/// ## How the anchor is chosen
///
/// The router picks, from inside [#anchorPart()], the deepest node in document
/// order that begins at or after the viewport's leading corner — the reader's
/// first whole line — and remembers where it sits **inside** that part, in the
/// coordinates layout produced rather than the ones the frame was painted in.
/// A scroll is a transform on the part, so it cancels: the remembered number
/// moves only when something the part contains changed size or arrived.
///
/// It keeps that node as long as it keeps moving, and re-picks on the first
/// quiet frame. Re-picking while a shift is in flight would measure the
/// correction a second time and double it.
///
/// ## The two rules it inherits
///
/// 1. **It is last frame's**, exactly as [Measured] is. A widget acting on it
///    corrects on the frame *after* the insertion, which at frame rate is not a
///    jump anybody sees — and is the only moment the heights exist, since the
///    rows were not laid out when the build that added them ran.
/// 2. **What it triggers must not change what it reports.** A scroll offset
///    obeys this by construction: the offset is a transform on the part, and the
///    number remembered is the one underneath it.
///
/// ## Identity is the caller's, and a key is what it is made of
///
/// The anchor is an [io.github.digitalsmile.goldberry.widget.Element], so "the
/// same node as last frame" means what the reconciler means by it. Children
/// matched by position rather than by key are *not* the same node when a list is
/// prepended to — element 0 simply describes a different row — so nothing moved
/// and nothing is reported. That is honest rather than a limitation: give list
/// items a key, which [Widget#key()] has asked for since it was written.
public interface Anchored extends Widget {

    /// The CSS type of the box whose contents are anchored, or null to ask for
    /// nothing.
    ///
    /// **Nullable, and that is the switch.** Finding an anchor walks a subtree
    /// once per frame per widget that asked, which is the cost [Measured]'s own
    /// doc comment is careful about. A viewport whose author did not ask for the
    /// offset to be preserved returns null and the router skips it entirely.
    @Nullable
    String anchorPart();

    /// How far the anchored node moved inside the part since the last frame, in
    /// logical pixels.
    ///
    /// Positive means further down or further right — so content inserted above
    /// a vertical viewport reports a positive `dy`, and a viewport that adds it
    /// to its offset has not moved at all on screen.
    ///
    /// Never called with two zeroes, and never on the frame an anchor is first
    /// found: there is nothing to compare a first sighting with.
    void contentShifted(double dx, double dy);
}
