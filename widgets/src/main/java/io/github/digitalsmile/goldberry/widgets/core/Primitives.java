package io.github.digitalsmile.goldberry.widgets.core;















import java.util.List;


/// The KDL registry for `docs/core-widgets.md` §1, §2 and §5's structural
/// widgets: `text`, `row`, `column`, `panel`, `spacer`, `scroll`, `affix`.
///
/// These used to live in `:core` as nested records inside a `Widgets` class,
/// because the engines needed *something* to prove the widget tree against before
/// there was a catalog to prove it with. They are ordinary widgets now, in the
/// packages `core-widgets.md` gives them, and `:core` has no widget of its own
/// ([ADR-0092](../../../../../../../book/src/adr/0092-a-primitive-is-a-widget-like-any-other.md)).
///
/// Kept separate from [io.github.digitalsmile.goldberry.widgets.Controls] rather
/// than folded into it, so that the sentence "the catalog is what `:widgets` adds
/// to the primitives" stays true — the primitives simply moved modules. An
/// application that wants a layout and no controls can register these alone.
public final class Primitives {

    private Primitives() {
    }


    // The inflater used to be here. The structural widgets carry `@Markup` like
    // every other widget now, so the build collects them into the same generated
    // catalog -- which is what §9's "built-ins and application widgets register
    // identically" was always claiming and is now literally true (ADR-0131).

    /// The CSS type names of every structural widget, which is what the parity
    /// test checks the other two forms against.
    public static List<String> builtInTypes() {
        // `scroll` and not `scroll-content`: the parity test checks the names a
        // document may write, and the content node is one this widget builds
        // for itself.
        return List.of("text", "row", "column", "panel", "spacer", "scroll", "affix");
    }
}
