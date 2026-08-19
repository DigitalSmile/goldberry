package io.github.digitalsmile.goldberry.input;

import io.github.digitalsmile.goldberry.backend.LogicalRect;
import io.github.digitalsmile.goldberry.widget.Widget;

/// A widget that wants to be told **where** it ended up, and what confines it.
///
/// The third and last of the geometry facilities, and the one that carries a
/// position rather than a size:
///
/// | | answers | arrives |
/// |---|---|---|
/// | [Extent] on an event | how big are these two boxes | when input asks |
/// | [Measured] | how big did I turn out | once a frame, on a change |
/// | [Located] | **where** am I, and what clips me | once a frame, on a change |
///
/// A scrollbar needs a size and nothing else, so [Measured] was enough for it.
/// `affix` is the first widget that needs a *position*: §1 asks it to pin its
/// child to an edge of the nearest `scroll` "once the child would have scrolled
/// past it", and that is a comparison between where this widget is and where the
/// viewport's edge is — two positions, neither of which any widget can compute
/// ([ADR-0119](../../../../../../book/src/adr/0119-a-widget-may-be-told-where-it-is.md)).
///
/// ## The rule that makes it terminate
///
/// **A widget told where it is must not move itself.** It is the same constraint
/// [Measured] carries and it bites harder here, because moving is exactly what
/// `affix` wants to do: a widget that translated itself in response to this
/// would be told a new position, translate again, and oscillate forever at the
/// frame rate.
///
/// The way out is structural rather than a rule anyone has to remember. `affix`
/// is two nodes — an outer one that is measured and never moves, and an inner one
/// that carries the translation. The outer node's position is a function of the
/// layout alone, so the inner node sliding under it changes nothing that is
/// reported, and the second frame reports what the first did.
///
/// That is also why [#located] is handed the rect **including** this widget's own
/// transform rather than excluding it: excluding it would make the contract safe
/// for a widget that breaks the rule, which is worse — it would work, one frame
/// late, and be impossible to reason about.
public interface Located extends Widget {

    /// Where the last frame put this widget, and what it was clipped to.
    ///
    /// Both in the window's logical coordinates, so they can be compared
    /// directly. `self` is this widget's border box as it was painted, which for
    /// a node inside a scroll view is where it has been *scrolled to* rather than
    /// where it was laid out — the whole point, since "has it scrolled past the
    /// top" is a question about the painted position.
    ///
    /// `clip` is the rectangle the nearest `overflow` above this widget confines
    /// it to ([ADR-0114](../../../../../../book/src/adr/0114-a-clip-is-a-rectangle-the-painter-carries.md)),
    /// which for anything inside a `scroll` is that viewport. **The window's own
    /// rectangle when nothing clips**, rather than null: an `affix` outside any
    /// scroll view is then pinned to the window, which is what a toolbar at the
    /// top of a page means and costs no branch to say.
    void located(LogicalRect self, LogicalRect clip);
}
