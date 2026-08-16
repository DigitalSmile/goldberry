package io.github.digitalsmile.goldberry.widget;

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
    public record Text(String content, Attributes attributes)
            implements Widget.Leaf, Styled, Paints {

        public Text(String content) {
            this(content, Attributes.NONE);
        }

        public Text {
            Objects.requireNonNull(content, "content");
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
            return Box.text(Paragraph.of(context.font(), content), style.color()).style(style);
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

    /// An inflater that knows every primitive above.
    ///
    /// §9: built-ins and application widgets register identically — this is just
    /// the first caller of [KdlInflater#register], not a privileged path.
    public static KdlInflater<Widget> inflater() {
        var inflater = new KdlInflater<Widget>();
        inflater.register("text", (node, children) ->
                new Text(node.argument().map(v -> v.asString()).orElse(""), Attributes.of(node)));
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
