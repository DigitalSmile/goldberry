package io.github.digitalsmile.goldberry.widget;

import io.github.digitalsmile.goldberry.kdl.KdlNode;
import java.util.LinkedHashSet;
import java.util.Set;

/// `id`, `class` and the reconciler's key — what every widget carries and no
/// widget decides.
///
/// Shared by every widget in the toolkit: the three names §9 gives a node, in one
/// value. A record cannot extend a class, so this is the one piece of boilerplate
/// each widget repeats — three accessors — rather than a hierarchy they cannot
/// have.
///
/// ## Why it is here and not in the catalog
///
/// It stayed in `:core` when `text`, `row`, `column`, `panel` and `spacer` left
/// for `:widgets`
/// ([ADR-0092](../../../../../../book/src/adr/0092-a-primitive-is-a-widget-like-any-other.md)),
/// because it is not a widget — it is part of the widget **contract**. [Styled]
/// asks a widget for its `id` and its classes and the cascade matches on the
/// answers; [Widget#key()] is what the reconciler pairs two builds by. A widget
/// in an application's own module implements the same three methods, and it
/// should not have to depend on the catalog to hold them in a value.
///
/// [#of(KdlNode)] is here for the same reason: parsing `id` and `class` off a
/// markup node is the inflater's contract, and the inflater is `:core`'s.
public record Attributes(
        String id, Set<String> classes, Object key, String tooltip, String contextMenu) {

    /// No id, no classes, no key, no tooltip — what a widget built in Java gets
    /// unless it says otherwise.
    public static final Attributes NONE = new Attributes(null, Set.of(), null, null, null);

    public Attributes {
        classes = Set.copyOf(classes == null ? Set.of() : classes);
    }

    /// The three that every widget had before a tooltip was one of them.
    ///
    /// Kept because `new Attributes(id, classes, key)` appears in every widget in
    /// the catalog and in most of its tests, and because a fourth positional
    /// argument on all of them would be four hundred edits to say `null`
    /// ([ADR-0105](../../../../../../book/src/adr/0105-a-tooltip-is-an-attribute-not-a-widget.md)).
    public Attributes(String id, Set<String> classes, Object key) {
        this(id, classes, key, null, null);
    }

    /// This, with the text a tooltip would show — `docs/core-widgets.md` §7's
    /// `tooltip="…"`, which attaches to **any** widget.
    ///
    /// Here rather than on each widget because that is what "any widget" means:
    /// a tooltip is not a property of being a button, and a catalog where each
    /// control had to remember to carry one would have thirty chances to forget.
    public Attributes tooltip(String text) {
        return new Attributes(id, classes, key,
                text == null || text.isBlank() ? null : text, contextMenu);
    }

    /// This, with the name of the menu a right-click should open —
    /// `docs/core-widgets.md` §8's `context-menu="menuId"`, which like a tooltip
    /// attaches to **any** widget.
    ///
    /// A *name*, not a menu: what the name means is a registry's, exactly as it is
    /// for `press=` and `icon=`. A widget holding a menu would be a widget holding
    /// a thing that has to be opened, and opening needs a window
    /// ([ADR-0108](../../../../../../book/src/adr/0108-a-context-menu-is-a-name-on-a-widget.md)).
    public Attributes contextMenu(String menuId) {
        return new Attributes(id, classes, key, tooltip,
                menuId == null || menuId.isBlank() ? null : menuId);
    }

    /// This, with a different `id` — **and the same id as the key**.
    ///
    /// Keying by id is what lets the element tree match a rebuilt description to
    /// the element that already exists, so a focused button keeps its focus
    /// across a `setState` that replaced every widget in the window. An id that
    /// did not double as a key would be an id that looks like it identifies the
    /// node and does not, which is [#of(KdlNode)]'s rule too.
    public Attributes id(String id) {
        return new Attributes(id, classes, id, tooltip, contextMenu);
    }

    /// This, with a different set of classes.
    public Attributes classes(String... names) {
        return new Attributes(id, Set.of(names), key, tooltip, contextMenu);
    }

    /// This, with a key that is not the id — for a list item whose identity is a
    /// row of a model rather than a name in a document.
    public Attributes key(Object key) {
        return new Attributes(id, classes, key, tooltip, contextMenu);
    }

    /// Parses `id` and `class` off a KDL node, `class` being space-separated as
    /// in HTML.
    ///
    /// The id doubles as the key, which is what makes a node with an id survive a
    /// reorder: two builds of the same document pair their `#save` buttons by
    /// name rather than by position.
    public static Attributes of(KdlNode node) {
        var id = node.stringProperty("id");
        var classes = new LinkedHashSet<String>();
        var raw = node.stringProperty("class");
        if (raw != null) {
            for (var name : raw.trim().split("\\s+")) {
                if (!name.isEmpty()) {
                    classes.add(name);
                }
            }
        }
        return new Attributes(id, classes, id, node.stringProperty("tooltip"),
                node.stringProperty("context-menu"));
    }
}
