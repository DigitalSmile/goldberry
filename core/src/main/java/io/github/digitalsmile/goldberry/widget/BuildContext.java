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

    /// The window this element is being built into, if it has one.
    ///
    /// Flutter's `Overlay.of(context)`, and the door a control needs when the
    /// thing it has to do is not describable as a widget: a `select` opens a
    /// popup window under itself, and a popup is the platform's rather than the
    /// tree's ([ADR-0140](../../../../../../book/src/adr/0140-a-widget-may-reach-its-window.md)).
    ///
    /// **For acting, not for reading.** A build must stay a pure function of its
    /// widget, its state and this context, so what a build may do with a host is
    /// *capture* it for a handler that runs later. Reading anything off it —
    /// [io.github.digitalsmile.goldberry.Host#anchor], the placeable area — makes
    /// the build depend on the last frame, which nothing invalidates.
    ///
    /// **Empty is a normal answer**, and the reason this is an `Optional` rather
    /// than a nullable: a widget test builds an [ElementTree] with no window at
    /// all, and so does a golden image. A control that cannot open its popup
    /// should stay closed rather than throw, which is exactly what a still
    /// picture of it wants.
    Optional<io.github.digitalsmile.goldberry.Host> host();

    /// The depth of this element from the root. Mostly for diagnostics.
    int depth();
}
