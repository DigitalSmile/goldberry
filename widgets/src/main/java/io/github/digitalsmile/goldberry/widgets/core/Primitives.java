package io.github.digitalsmile.goldberry.widgets.core;

import io.github.digitalsmile.goldberry.bind.Bindings;
import io.github.digitalsmile.goldberry.kdl.KdlInflater;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.panel.Panel;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.util.List;
import java.util.Objects;

/// The KDL registry for `docs/core-widgets.md` §1, §2 and §5's structural
/// widgets: `text`, `row`, `column`, `panel`, `spacer`.
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

    /// An inflater that knows every structural widget, with nothing bound.
    ///
    /// §9: built-ins and application widgets register identically — this is just
    /// the first caller of [KdlInflater#register], not a privileged path.
    public static KdlInflater<Widget> inflater() {
        return inflater(Bindings.none());
    }

    /// An inflater whose `bind=` paths resolve against `bindings`.
    ///
    /// The `bind` half of §9, and the same shape as the `action` half: markup
    /// names a path, the registry says what it means, and a document reloaded at
    /// runtime re-resolves every path against the properties the application
    /// already holds (ADR-0062).
    public static KdlInflater<Widget> inflater(Bindings bindings) {
        Objects.requireNonNull(bindings, "bindings");
        var inflater = new KdlInflater<Widget>();
        inflater.register("text", (node, children) -> {
            var source = bindings.resolve(node.stringProperty("bind"));
            var literal = node.argument().map(v -> v.asString()).orElse("");
            // A bound node keeps its argument as the fallback rather than
            // refusing it: `text bind="user.name" "…"` is what a lenient registry
            // shows for a path nothing answers yet, and it is what a designer
            // laying out a screen wants to see.
            return source == null
                    ? new Text(literal, Attributes.of(node))
                    : new Text(literal, source, Attributes.of(node));
        });
        inflater.register("row", (node, children) -> new Row(children, Attributes.of(node)));
        inflater.register("column", (node, children) -> new Column(children, Attributes.of(node)));
        inflater.register("panel", (node, children) -> new Panel(children, Attributes.of(node)));
        inflater.register("spacer", (node, children) -> new Spacer(Attributes.of(node)));
        return inflater;
    }

    /// The CSS type names of every structural widget, which is what the parity
    /// test checks the other two forms against.
    public static List<String> builtInTypes() {
        return List.of("text", "row", "column", "panel", "spacer");
    }
}
