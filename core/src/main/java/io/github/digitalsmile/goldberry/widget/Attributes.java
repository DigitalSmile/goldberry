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
public record Attributes(String id, Set<String> classes, Object key) {

    /// No id, no classes, no key — what a widget built in Java gets unless it
    /// says otherwise.
    public static final Attributes NONE = new Attributes(null, Set.of(), null);

    public Attributes {
        classes = Set.copyOf(classes == null ? Set.of() : classes);
    }

    /// This, with a different `id` — **and the same id as the key**.
    ///
    /// Keying by id is what lets the element tree match a rebuilt description to
    /// the element that already exists, so a focused button keeps its focus
    /// across a `setState` that replaced every widget in the window. An id that
    /// did not double as a key would be an id that looks like it identifies the
    /// node and does not, which is [#of(KdlNode)]'s rule too.
    public Attributes id(String id) {
        return new Attributes(id, classes, id);
    }

    /// This, with a different set of classes.
    public Attributes classes(String... names) {
        return new Attributes(id, Set.of(names), key);
    }

    /// This, with a key that is not the id — for a list item whose identity is a
    /// row of a model rather than a name in a document.
    public Attributes key(Object key) {
        return new Attributes(id, classes, key);
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
        return new Attributes(id, classes, id);
    }
}
