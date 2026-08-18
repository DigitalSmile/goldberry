package io.github.digitalsmile.goldberry.widget;

/// A widget that carries [Attributes] and can hand back a copy carrying
/// different ones.
///
/// The chainable half of the widget API:
///
/// ```java
/// new Row(
///         new Text("Goldberry").id("title"),
///         new Spacer(),
///         new Badge("3").styled("accent"))
///     .id("bar")
/// ```
///
/// ## Why an interface and not three methods per record
///
/// Every widget in the catalog needs `id`, `styled` and `key`, and a record
/// cannot inherit them — which is the same problem [Attributes] itself solves for
/// the *fields*. Written out, that is three near-identical copies per widget and
/// fifty of them across the catalog: the kind of repetition where one of the
/// copies eventually forgets to preserve the key.
///
/// So the wither is the interface's, and a widget supplies the one thing only it
/// can — [#withAttributes], which rebuilds the record. `W` is the implementing
/// type, so the chain keeps its type: `new Badge("beta").styled("warning")` is a
/// `Badge` and not a `Widget`, and `new Slider(…).id("gain").resolved()` compiles.
///
/// @param <W> the implementing widget's own type
public interface Attributed<W extends Widget> extends Widget {

    /// This widget's `id`, classes and key.
    Attributes attributes();

    /// A copy of this widget carrying `attributes` — the one line a record has to
    /// write, because only it knows its own components.
    W withAttributes(Attributes attributes);

    /// This widget with a tooltip — `docs/core-widgets.md` §7's `tooltip="…"`,
    /// which attaches to any widget and is shown by the toolkit after a delay, on
    /// hover **and** on keyboard focus.
    ///
    /// The widget does not draw it, does not own it and never sees it opened: it
    /// carries the text, and the launcher does the rest (ADR-0105).
    default W tooltip(String text) {
        return withAttributes(attributes().tooltip(text));
    }

    /// This widget with the name of a menu a right-click on it should open —
    /// §8's `context-menu="menuId"`.
    ///
    /// The widget carries a name and nothing else: what the name means is a
    /// registry's, and opening the menu is `Menus`' (ADR-0108).
    default W contextMenu(String menuId) {
        return withAttributes(attributes().contextMenu(menuId));
    }

    /// This widget with an `id`, which is also its key — see [Attributes#id].
    default W id(String id) {
        return withAttributes(attributes().id(id));
    }

    /// This widget with its classes replaced — `button.primary` from Java.
    ///
    /// Replaced rather than added, because that is what a `class=` attribute does
    /// in markup and §11's parity invariant says the two forms must agree. A
    /// widget built twice with different classes is two descriptions of the same
    /// node, not an accumulation.
    default W styled(String... classes) {
        return withAttributes(attributes().classes(classes));
    }

    /// This widget with a key that is not its id — see [Attributes#key].
    default W keyed(Object key) {
        return withAttributes(attributes().key(key));
    }
}
