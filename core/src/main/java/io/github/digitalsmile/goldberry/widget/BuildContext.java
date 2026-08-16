package io.github.digitalsmile.goldberry.widget;

import java.util.Optional;

/// What a widget can ask about where it is in the tree.
///
/// Handed to every `build()`. Deliberately narrow: a build must be a pure
/// function of its widget, its state and this — so anything reachable through it
/// is something the framework can track and invalidate.
public interface BuildContext {

    /// The nearest enclosing widget of `type`, if any.
    ///
    /// How a control finds the thing it belongs to: a `radio` finding its
    /// `radio-group`, a `field` finding its `form`. Walks up the *element* tree,
    /// so it costs the depth of the tree and no allocation.
    <T extends Widget> Optional<T> findAncestor(Class<T> type);

    /// The depth of this element from the root. Mostly for diagnostics.
    int depth();
}
