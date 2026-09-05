package io.github.digitalsmile.goldberry.widget;

import java.util.List;

/// A widget that describes **nothing at all** — no box, no space, no selector.
///
/// The word the element tree did not have (ADR-0227). Every `build` has to return
/// a widget, so a widget with nothing to show had two choices and both were
/// wrong:
///
/// - **Describe an empty box.** It takes no room of its own, and it is still a
///   child: a `column` with `gap: 12px` puts twelve pixels round it, so the
///   banner that vanished leaves a hole. `MessageBox` did exactly this and its
///   own documentation recorded the hole as somebody else's number.
/// - **Have the parent leave it out.** Correct, and it moves the decision one
///   level up — so a `message` bound to an empty string can only be *described
///   away* by whoever placed it, which is the application, which is the thing
///   §9's `bind=` exists to spare.
///
/// A `Nothing` is neither
/// [io.github.digitalsmile.goldberry.widget.style.Styled] nor
/// [io.github.digitalsmile.goldberry.widget.style.Paints] and has no children, so
/// the renderer's existing rule for a composition node applies unchanged: it
/// contributes zero boxes to its parent, and Yoga never hears about it. No new
/// branch anywhere — the tree could always express this, and nothing could say
/// it.
///
/// ```java
/// public Widget build(BuildContext context) {
///     return text.isBlank() ? Widget.nothing() : new MessageBox(…);
/// }
/// ```
///
/// ## It is still an element
///
/// The element stays in the tree, holding its state, keeping its place in the
/// reconciler, and subscribed to its binding. That is the point: a widget that
/// describes nothing *this* frame and something the next is one node with a
/// value that changed, not a node that was destroyed and rebuilt. A banner whose
/// text empties and fills again keeps its arrival phase, and a `bind=` that goes
/// blank does not unsubscribe itself.
///
/// ## Not a replacement for `display: none`
///
/// §8's subset has no `display`, and this is not it arriving by another door: a
/// stylesheet still cannot take a node out of a layout. What a widget decides
/// about its own content it may now say; what a *rule* decides remains outside
/// the subset.
///
/// A singleton, reachable as [Widget#nothing()]. It carries no state and two of
/// them are indistinguishable, so a second instance would only give the
/// reconciler something to tell apart.
record Nothing() implements Widget.Leaf {

    /// The one of these there is — see [Widget#nothing()], which is the door.
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
