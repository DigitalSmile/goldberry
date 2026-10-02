package dev.goldberry.widget;

import java.util.List;

/// A widget that describes **nothing at all**: no box, no space, no selector.
///
/// Every `build` has to return a widget, and a widget with nothing to show had
/// two choices, both wrong. An empty box takes no room of its own but is still a
/// child, so a `column` with `gap: 12px` puts twelve pixels round it and the
/// banner that vanished leaves a hole. Having the parent leave it out is correct
/// but moves the decision one level up, so a `message` bound to an empty string
/// could only be described away by the application, which is what `bind=`
/// exists to spare.
///
/// ```java
/// public Widget build(BuildContext context) {
///     return text.isBlank() ? Widget.nothing() : new MessageBox(…);
/// }
/// ```
///
/// A `Nothing` is neither [dev.goldberry.widget.style.Styled] nor
/// [dev.goldberry.widget.style.Paints] and has no children, so the renderer's
/// rule for a composition node applies unchanged: it contributes zero boxes to
/// its parent, and the layout engine never hears about it.
///
/// ## It is still an element
///
/// The element stays in the tree, holding its state, keeping its place in the
/// reconciler, and subscribed to its binding. A widget that describes nothing
/// *this* frame and something the next is one node with a value that changed,
/// not a node that was destroyed and rebuilt. A banner whose text empties and
/// fills again keeps its arrival phase, and a `bind=` that goes blank does not
/// unsubscribe itself.
///
/// ## Not a replacement for `display: none`
///
/// A stylesheet still cannot take a node out of a layout. What a widget decides
/// about its own content it may say here; what a *rule* decides is unchanged.
///
/// A singleton, reachable as [Widget#nothing()]. It carries no state and two of
/// them are indistinguishable, so a second instance would only give the
/// reconciler something to tell apart.
record Nothing() implements Widget.Leaf {

    /// The one of these there is; [Widget#nothing()] is the door.
    static final Widget INSTANCE = new Nothing();

    @Override
    public List<Widget> children() {
        return List.of();
    }

    @Override
    public String toString() {
        return "Widget.nothing()";
    }
}
