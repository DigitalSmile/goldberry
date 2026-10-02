package dev.goldberry.widget.attr;

import dev.goldberry.widget.Widget;

/// A widget that carries [Attributes] and can hand back a copy carrying
/// different ones: the chainable `id`, `styled`, `keyed`, `tooltip`,
/// `contextMenu`, `onPointerEnter` and `onPointerExit` steps.
///
/// ```java
/// new Row(
///         new Text("Goldberry").id("title"),
///         new Spacer(),
///         new Badge("3").styled("accent"))
///     .id("bar")
/// ```
///
/// Every widget needs these steps and a record cannot inherit them, so the
/// withers are the interface's, and a widget supplies the one thing only it can:
/// [#withAttributes], which rebuilds the record. `W` is the implementing type, so
/// the chain keeps its type: `new Badge("beta").styled("warning")` is a `Badge`
/// and not a `Widget`, and `new Slider(…).id("gain").resolved()` compiles.
///
/// Read more:
/// [Attributes and binding](https://goldberry.dev/docs/guide/writing-a-widget.html#attributes-and-binding).
///
/// @param <W> the implementing widget's own type
public interface Attributed<W extends Widget> extends Widget {

    /// This widget's `id`, classes and key.
    Attributes attributes();

    /// A copy of this widget carrying `attributes`: the one line a record has to
    /// write, because only it knows its own components.
    W withAttributes(Attributes attributes);

    /// This widget with a tooltip, the `tooltip="…"` of markup, which attaches to
    /// any widget and is shown by the toolkit after a delay, on hover **and** on
    /// keyboard focus.
    ///
    /// The widget does not draw it, does not own it and never sees it opened: it
    /// carries the text, and the window does the rest.
    default W tooltip(String text) {
        return withAttributes(attributes().tooltip(text));
    }

    /// This widget with the name of a menu a right-click on it should open, the
    /// `context-menu="menuId"` of markup.
    ///
    /// The widget carries a name and nothing else: what the name means is the
    /// menu registry's, and opening the menu is `Menus`'.
    default W contextMenu(String menuId) {
        return withAttributes(attributes().contextMenu(menuId));
    }

    /// This widget with an `id`, which is also its key; see [Attributes#id].
    default W id(String id) {
        return withAttributes(attributes().id(id));
    }

    /// This widget with its classes replaced: `button.primary` from Java.
    ///
    /// Replaced rather than added, because that is what a `class=` attribute does
    /// in markup and the two forms must agree. A widget built twice with
    /// different classes is two descriptions of the same node, not an
    /// accumulation.
    default W styled(String... classes) {
        return withAttributes(attributes().classes(classes));
    }

    /// This widget with a key that is not its id; see [Attributes#key].
    default W keyed(Object key) {
        return withAttributes(attributes().key(key));
    }

    /// This widget told when the pointer arrives anywhere in its subtree.
    ///
    /// It consumes nothing: a press that lands inside still belongs to whatever
    /// is inside. See [Attributes#onPointerEnter] for why this is an attribute
    /// rather than a widget.
    default W onPointerEnter(Runnable action) {
        return withAttributes(attributes().onPointerEnter(action));
    }

    /// This widget told when the pointer leaves its subtree altogether; see
    /// [Attributes#onPointerExit], including what happens when the node is
    /// unmounted under the pointer.
    default W onPointerExit(Runnable action) {
        return withAttributes(attributes().onPointerExit(action));
    }
}
