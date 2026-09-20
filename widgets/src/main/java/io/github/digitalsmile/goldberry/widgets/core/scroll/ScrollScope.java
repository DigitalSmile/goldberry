package io.github.digitalsmile.goldberry.widgets.core.scroll;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.Element;

/// The viewport a widget is inside, found by walking up from the widget itself.
///
/// [ScrollController] is the handle an *owner* holds: created above a `scroll`,
/// handed down into it, and used by whoever wired it. This is the other
/// direction, and the one a caller has when all it holds is the target —
/// `docs/core-widgets.md` §5's `tour`, which names a widget by id and has to
/// scroll it into view without knowing anything about the tree around it:
///
/// ```java
/// host.anchor(targetId)
///     .filter(region -> region.owner() instanceof Element)
///     .flatMap(region -> ScrollScope.enclosing((Element) region.owner()))
///     .ifPresent(scope -> scope.reveal(region.painted(), clip));
/// ```
///
/// ## Why this is not the wall the TODO entry described
///
/// The entry said the tree offered no way to ask, because
/// [io.github.digitalsmile.goldberry.widget.BuildContext#findAncestorState] walks
/// up from the element being **built** and what is wanted is a walk up from the
/// element being **named**. Both halves of that are true and the conclusion does
/// not follow: an [Element] *is* a `BuildContext`, so the walk starts wherever
/// the caller points it, and a hit-test region already carries the element it
/// was painted for. ADR-0120 wrote down that `findAncestorState` "stays, because
/// it is how an application-level `scrollIntoView` from inside a scroll view
/// reaches the viewport" — which is exactly this call, two years of entries
/// later ([ADR-0439]).
///
/// ## The nearest one, and only the nearest one
///
/// A target inside nested viewports is revealed in the **innermost** one. That
/// is what a hand-wired [ScrollController] did — an application passes one
/// controller, not a chain — so this is the same behaviour with the wiring
/// removed rather than a new promise. It is also where it stops telling the
/// truth: revealing a row in an inner list can leave that whole list scrolled
/// out of the outer one, and nothing here notices. Walking the rest of the way
/// needs each viewport's own painted rectangle, which only the router holds and
/// only for nodes it has regions for.
public final class ScrollScope {

    private final ScrollState viewport;

    private ScrollScope(ScrollState viewport) {
        this.viewport = viewport;
    }

    /// The nearest `scroll` enclosing `target`, or empty when it is in none.
    ///
    /// Empty is an ordinary answer and not a failure: §5's tour describes plenty
    /// of targets that sit in no viewport at all, and a caller that treated this
    /// as an error would have to special-case the common shape.
    ///
    /// **The walk starts at the target's parent**, and a `scroll` named by its
    /// own `id` nevertheless answers with *itself*. Those are not in tension: an
    /// `id` written on a `scroll` lands on the [ScrollViewport] the widget
    /// builds — `scroll` as a CSS type is that node, not the stateful one above
    /// it — so the first parent of the element anybody can name **is** the
    /// viewport's own state. A tour stop naming a viewport therefore reveals the
    /// viewport inside itself, which costs one lookup and moves nothing.
    public static Optional<ScrollScope> enclosing(@Nullable Element target) {
        if (target == null) {
            return Optional.empty();
        }
        return target.findAncestorState(ScrollState.class).map(ScrollScope::new);
    }

    /// Which way the enclosing viewport moves.
    public ScrollAxis axis() {
        return viewport.axis();
    }

    /// Scrolls the least it can to bring `self` inside `clip`, on both axes.
    ///
    /// The same two rectangles [ScrollController#reveal] takes and the same
    /// arithmetic behind them — [ScrollState]'s, so that a reveal means one thing
    /// however it was reached.
    public void reveal(LogicalRect self, LogicalRect clip) {
        reveal(self, clip, ScrollAxis.BOTH);
    }

    /// The same, moving only along `axes`.
    public void reveal(LogicalRect self, LogicalRect clip, ScrollAxis axes) {
        viewport.reveal(self, clip, axes);
    }
}
