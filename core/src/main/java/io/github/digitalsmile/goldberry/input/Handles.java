package io.github.digitalsmile.goldberry.input;

import io.github.digitalsmile.goldberry.widget.Widget;

/// A widget that reacts to the pointer.
///
/// Opt-in, like [io.github.digitalsmile.goldberry.widget.Paints]. A widget that
/// does not implement this is not asked, which keeps dispatch proportional to the
/// number of interested nodes rather than to the depth of the tree.
public interface Handles extends Widget {

    /// Called during the capture phase, root-first, before the target sees the
    /// event.
    ///
    /// Where a scroll view or a modal layer intercepts. Consuming here stops the
    /// target ever receiving it — which is the point of having a capture phase
    /// at all.
    default void onPointerCapture(PointerEvent event) {
    }

    /// Called on the target and then on each ancestor, deepest-first.
    ///
    /// The phase ordinary widgets want.
    default void onPointer(PointerEvent event) {
    }

    /// Called during the keyboard capture phase, root-first.
    ///
    /// Where a dialog swallows Escape before the thing inside it sees it.
    default void onKeyCapture(KeyEvent event) {
    }

    /// A key went down or came up, on the focused node and then its ancestors.
    default void onKey(KeyEvent event) {
    }

    /// Committed text reached the focused node.
    ///
    /// A widget that wants what the user typed wants this, not [#onKey]: one
    /// character can take several keys (§7.1).
    default void onText(TextEvent event) {
    }

    /// Whether this widget can take keyboard focus.
    ///
    /// False by default: most nodes are scenery, and a Tab traversal that
    /// stopped on every one of them would be unusable.
    default boolean isFocusable() {
        return false;
    }
}
