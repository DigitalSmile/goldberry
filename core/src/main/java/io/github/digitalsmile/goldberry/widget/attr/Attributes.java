package io.github.digitalsmile.goldberry.widget.attr;

import java.util.LinkedHashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Styled;

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
/// (ADR-0092),
/// because it is not a widget — it is part of the widget **contract**. [Styled]
/// asks a widget for its `id` and its classes and the cascade matches on the
/// answers; [Widget#key()] is what the reconciler pairs two builds by. A widget
/// in an application's own module implements the same three methods, and it
/// should not have to depend on the catalog to hold them in a value.
///
/// [#of(KdlNode)] is here for the same reason: parsing `id` and `class` off a
/// markup node is the inflater's contract, and the inflater is `:core`'s.
public record Attributes(
        @Nullable String id, Set<String> classes, Object key, String tooltip, String contextMenu, String name) {

    /// No id, no classes, no key, no tooltip, no name — what a widget built in
    /// Java gets unless it says otherwise.
    public static final Attributes NONE = new Attributes(null, Set.of(), null, null, null, null);

    public Attributes {
        classes = Set.copyOf(classes == null ? Set.of() : classes);
    }

    /// The three that every widget had before a tooltip was one of them.
    ///
    /// Kept because `new Attributes(id, classes, key)` appears in every widget in
    /// the catalog and in most of its tests, and because a fourth positional
    /// argument on all of them would be four hundred edits to say `null`
    /// (ADR-0105).
    public Attributes(String id, Set<String> classes, Object key) {
        this(id, classes, key, null, null, null);
    }

    /// The five that every widget had before an accessible name was one of them,
    /// kept for the reason the three-argument form is (ADR-0260).
    public Attributes(String id, Set<String> classes, Object key, String tooltip, String contextMenu) {
        this(id, classes, key, tooltip, contextMenu, null);
    }

    /// This, with the text a tooltip would show — `docs/core-widgets.md` §7's
    /// `tooltip="…"`, which attaches to **any** widget.
    ///
    /// Here rather than on each widget because that is what "any widget" means:
    /// a tooltip is not a property of being a button, and a catalog where each
    /// control had to remember to carry one would have thirty chances to forget.
    public Attributes tooltip(String text) {
        return new Attributes(id, classes, key, text == null || text.isBlank() ? null : text, contextMenu, name);
    }

    /// This, with the name of the menu a right-click should open —
    /// `docs/core-widgets.md` §8's `context-menu="menuId"`, which like a tooltip
    /// attaches to **any** widget.
    ///
    /// A *name*, not a menu: what the name means is a registry's, exactly as it is
    /// for `press=` and `icon=`. A widget holding a menu would be a widget holding
    /// a thing that has to be opened, and opening needs a window
    /// (ADR-0108).
    public Attributes contextMenu(String menuId) {
        return new Attributes(id, classes, key, tooltip, menuId == null || menuId.isBlank() ? null : menuId, name);
    }

    /// This, with a different `id` — **and the same id as the key**.
    ///
    /// Keying by id is what lets the element tree match a rebuilt description to
    /// the element that already exists, so a focused button keeps its focus
    /// across a `setState` that replaced every widget in the window. An id that
    /// did not double as a key would be an id that looks like it identifies the
    /// node and does not, which is [#of(KdlNode)]'s rule too.
    public Attributes id(String id) {
        return new Attributes(id, classes, id, tooltip, contextMenu, name);
    }

    /// This, with a different set of classes.
    public Attributes classes(String... names) {
        return new Attributes(id, Set.of(names), key, tooltip, contextMenu, name);
    }

    /// This, with a key that is not the id — for a list item whose identity is a
    /// row of a model rather than a name in a document.
    public Attributes key(Object key) {
        return new Attributes(id, classes, key, tooltip, contextMenu, name);
    }

    /// This, with the text a reader should announce it as — `docs/core-widgets.md`
    /// §3's `name="…"`, which like a tooltip attaches to **any** widget.
    ///
    /// Here rather than on each widget for the reason a tooltip is here: §13 asks
    /// for a role *and a name* on everything, and a catalog where each control
    /// remembered its own would have thirty chances to forget. It is also the
    /// only way to name the thing that most needs it — an **icon-only** control,
    /// whose label is the empty string precisely because there is nothing on
    /// screen to read ([ADR-0260]).
    ///
    /// A widget that derives a name from what it is showing keeps doing so; this
    /// wins when it is set, because an author writing one has said something the
    /// widget could not work out.
    public Attributes name(String text) {
        return new Attributes(id, classes, key, tooltip, contextMenu, text == null || text.isBlank() ? null : text);
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
            // Split on runs of whitespace after a trim, so there is no trailing
            // empty to drop -- and the loop skips one regardless.
            @SuppressWarnings("StringSplitter")
            var names = raw.trim().split("\\s+");
            for (var name : names) {
                if (!name.isEmpty()) {
                    classes.add(name);
                }
            }
        }
        return new Attributes(
                id,
                classes,
                id,
                node.stringProperty("tooltip"),
                node.stringProperty("context-menu"),
                node.stringProperty("name"));
    }
}
