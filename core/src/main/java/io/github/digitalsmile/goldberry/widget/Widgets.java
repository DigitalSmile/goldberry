package io.github.digitalsmile.goldberry.widget;

import io.github.digitalsmile.goldberry.bind.Bindings;
import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.kdl.KdlInflater;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.text.Paragraph;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// The primitives of §11, and the KDL registry that builds them.
///
/// Deliberately the smallest set that makes the parity invariant testable:
/// `text`, `row`, `column`, `panel`, `spacer`. Each is a Java record, a KDL node
/// and a CSS type — which is the invariant, and [WidgetParityTest] is what
/// enforces it.
///
/// Everything visual about them comes from CSS. `Row` sets no colour and no
/// padding; it sets `flex-direction`, because that is what makes it a row rather
/// than a column, and a stylesheet cannot be allowed to turn a `row` into a
/// `column` without the name lying.
public final class Widgets {

    private Widgets() {
    }

    /// Shared by every primitive: `id` and `class` behave as in HTML (§9).
    ///
    /// A record cannot extend a class, so this is the one piece of boilerplate
    /// each of them repeats — two accessors — rather than a hierarchy they cannot
    /// have.
    public record Attributes(String id, Set<String> classes, Object key) {

        public static final Attributes NONE = new Attributes(null, Set.of(), null);

        public Attributes {
            classes = Set.copyOf(classes == null ? Set.of() : classes);
        }

        /// Parses `id` and `class` off a KDL node, `class` being space-separated
        /// as in HTML.
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

    /// A run of text. `text "Hello"` in KDL.
    ///
    /// The content is either written down or bound. `source` is §9's `bind`: when
    /// it is set, the text shown is whatever the property holds *now*, and
    /// `content` is what it falls back to before anything is bound — which is what
    /// a lenient inflater produces for a path nothing answers
    /// ([ADR-0062]).
    public record Text(String content, Observable<?> source, Attributes attributes)
            implements Widget.Leaf, Styled, Paints {

        public Text(String content) {
            this(content, null, Attributes.NONE);
        }

        public Text(String content, Attributes attributes) {
            this(content, null, attributes);
        }

        /// Text that follows a property. Equivalent to `text bind="…"`.
        public static Text of(Observable<?> source, Attributes attributes) {
            return new Text("", Objects.requireNonNull(source, "source"), attributes);
        }

        public Text {
            Objects.requireNonNull(content, "content");
        }

        /// What this text says right now — the bound value, or the literal.
        ///
        /// Read at render rather than captured at build, so a change that arrives
        /// between a build and a frame is shown by that frame rather than the one
        /// after it. `null` in a property reads as the empty string: a value that
        /// has not loaded yet is nothing to draw, not the word "null".
        public String resolved() {
            if (source == null) {
                return content;
            }
            var value = source.get();
            return value == null ? "" : String.valueOf(value);
        }

        @Override
        public Observable<?> binding() {
            return source;
        }

        @Override
        public String cssType() {
            return "text";
        }

        @Override
        public String id() {
            return attributes.id();
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public Object key() {
            return attributes.key();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            // A measured leaf: Yoga proposes a width, the paragraph wraps at it,
            // and the height that comes back is what sizes the box (ADR-0036).
            return Box.text(context.paragraph(style, resolved()), style.color()).style(style);
        }
    }

    /// Children laid out along the main axis.
    public record Row(List<Widget> children, Attributes attributes)
            implements Widget.Leaf, Styled, Paints {

        public Row(Widget... kids) {
            this(List.of(kids), Attributes.NONE);
        }

        public Row {
            children = List.copyOf(children == null ? List.of() : children);
        }

        @Override
        public List<Widget> children() {
            return children;
        }

        @Override
        public String id() {
            return attributes.id();
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public Object key() {
            return attributes.key();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            // The direction is the widget's, not the stylesheet's: a `row` that a
            // stylesheet could turn into a column would be a name that lies.
            return Box.of().children(boxes.toArray(Box[]::new)).style(style)
                    .direction(io.github.digitalsmile.goldberry.natives.yoga.FlexDirection.ROW);
        }
    }

    /// Children laid out along the cross axis.
    public record Column(List<Widget> children, Attributes attributes)
            implements Widget.Leaf, Styled, Paints {

        public Column(Widget... kids) {
            this(List.of(kids), Attributes.NONE);
        }

        public Column {
            children = List.copyOf(children == null ? List.of() : children);
        }

        @Override
        public List<Widget> children() {
            return children;
        }

        @Override
        public String id() {
            return attributes.id();
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public Object key() {
            return attributes.key();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().children(boxes.toArray(Box[]::new)).style(style)
                    .direction(io.github.digitalsmile.goldberry.natives.yoga.FlexDirection.COLUMN);
        }
    }

    /// A surface: a container whose whole appearance is the stylesheet's.
    public record Panel(List<Widget> children, Attributes attributes)
            implements Widget.Leaf, Styled, Paints {

        public Panel(Widget... kids) {
            this(List.of(kids), Attributes.NONE);
        }

        public Panel {
            children = List.copyOf(children == null ? List.of() : children);
        }

        @Override
        public List<Widget> children() {
            return children;
        }

        @Override
        public String id() {
            return attributes.id();
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public Object key() {
            return attributes.key();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().children(boxes.toArray(Box[]::new)).style(style);
        }
    }

    /// Empty space that takes what is left over.
    public record Spacer(Attributes attributes) implements Widget.Leaf, Styled, Paints {

        public Spacer() {
            this(Attributes.NONE);
        }

        @Override
        public String id() {
            return attributes.id();
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public Object key() {
            return attributes.key();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            // grow(1) unless the stylesheet said otherwise: taking the free space
            // is what a spacer is for, and having to write `spacer { flex-grow: 1 }`
            // in every stylesheet would make the widget pointless.
            var box = Box.of().style(style);
            return style.flexGrow() == 0 ? box.grow(1) : box;
        }
    }

    /// An inflater that knows every primitive above, with nothing bound.
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
            // refusing it: `text bind="user.name" "…"` is what a lenient
            // registry shows for a path nothing answers yet, and it is what a
            // designer laying out a screen wants to see.
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

    /// The CSS type names of every built-in, which is what the parity test
    /// checks the other two forms against.
    public static List<String> builtInTypes() {
        return List.of("text", "row", "column", "panel", "spacer");
    }
}
