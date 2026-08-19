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

    /// The nearest enclosing [State] of `type`, if any.
    ///
    /// [#findAncestor] finds an ancestor's *description*, which is what a `radio`
    /// wants of its group: the value, the name, the change handler are all on the
    /// widget. This finds the ancestor's **live half**, and exists for the case
    /// where the answer is not a value but an action — Flutter's
    /// `Scrollable.of(context)`, and here `scrollIntoView`
    /// ([ADR-0120](../../../../../../book/src/adr/0120-a-widget-scrolls-itself-into-view.md)).
    ///
    /// A scroll view's offset lives on its state and cannot live anywhere else: a
    /// widget is a value rebuilt every frame, so a descendant that reached the
    /// `Scroll` record would find a description with no position in it and
    /// nothing to ask.
    ///
    /// **Narrower than it looks.** A state reached this way is one an ancestor
    /// owns, and the only thing worth doing with it is calling a method the
    /// ancestor deliberately exposed. Reading another state's fields is how two
    /// widgets end up with one bug, and nothing here makes it convenient.
    <S extends State<?>> Optional<S> findAncestorState(Class<S> type);

    /// The depth of this element from the root. Mostly for diagnostics.
    int depth();
}
