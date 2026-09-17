package io.github.digitalsmile.goldberry.input.handler;

import io.github.digitalsmile.goldberry.widget.Widget;

/// A widget that makes itself the subject of a context menu about to open over
/// it.
///
/// Every file manager selects the row you right-click before it opens the menu,
/// and until this existed that was the application's to do in its own handler —
/// because the toolkit had no notion of what "select" means for an arbitrary
/// widget. It still has none. What it has instead is a **widget that knows**: a
/// row in a list or a tree can say what selecting it means, and nothing else has
/// to (ADR-0224).
///
/// ## Who is asked
///
/// The launcher already walks up from what the gesture landed on to the nearest
/// widget that named a menu ([io.github.digitalsmile.goldberry.Host#onContextMenu]).
/// The **deepest** widget on that walk that implements this is asked, once, just
/// before the menu opens — so a right-click on a cell inside a row targets the
/// row, and a row inside a row targets the inner one.
///
/// Asked only when a menu is actually going to open. A right-click over a widget
/// that named none does nothing at all, and a selection that changed without a
/// menu to show for it would be a gesture with no visible cause.
///
/// The **same walk** serves the keyboard's menu key (ADR-0208), so `Menu` on a
/// focused-but-unselected row selects it too. That is the same rule seen from the
/// other input device — the menu acts on what it opened over — and a bar that
/// disagreed with the pointer about it would be worse than either behaviour.
///
/// ## The rule an implementation owes
///
/// **Do not disturb a selection this widget is already part of.** Right-clicking
/// one of five selected files must open a menu about the five, not collapse them
/// to one; right-clicking outside the selection replaces it. That distinction is
/// the whole reason this is a widget's method rather than a `Consumer` an
/// application supplies, and it is what
/// `ListView`'s rows do.
///
/// Like everything else that reports a gesture, this **asks** rather than
/// selects: the selection is the application's value, and what a row does here is
/// report through the same callback a click reports through (ADR-0063).
public interface Selects extends Widget {

    /// Asked to become the subject of a context menu that is about to open.
    ///
    /// A no-op is a perfectly good answer, and the common one: a row already in
    /// the selection, or a widget in a list that selects nothing, has nothing to
    /// do here.
    void selectForContextMenu();
}
