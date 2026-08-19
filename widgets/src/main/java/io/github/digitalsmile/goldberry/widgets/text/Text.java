package io.github.digitalsmile.goldberry.widgets.text;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Bindable;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widgets.Wiring;
import io.github.digitalsmile.goldberry.widgets.Markup;

/// A run of text — `docs/core-widgets.md` §2's `text`, and the whole of that
/// package until `span` and `link` are built.
///
/// ```kdl
/// text "Hello"
/// text class="caption" bind="user.name"
/// ```
///
/// The content is either written down or bound. `source` is §9's `bind`: when it
/// is set, the text shown is whatever the property holds *now*, and `content` is
/// what it falls back to before anything is bound — which is what a lenient
/// inflater produces for a path nothing answers
/// ([ADR-0062](../../../../../../../book/src/adr/0062-bind-is-a-path-and-nothing-else.md)).
///
/// Everything visual is the stylesheet's. This sets no size, no weight and no
/// colour; `class="body"` and the rest of §1.4's scale are rules in
/// `controls.css`, which is why a `text` with no ancestor setting `color` is
/// still an open question rather than a default written here.
@Markup("text")
public record Text(String content, Observable<?> source, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Text>, Bindable<Text> {

    public Text(String content) {
        this(content, null, Attributes.NONE);
    }

    public Text(String content, Attributes attributes) {
        this(content, null, attributes);
    }

    /// Text that follows a property. Equivalent to `text bind="…"`.
    public static Text of(Observable<?> source) {
        return new Text("", Objects.requireNonNull(source, "source"), Attributes.NONE);
    }

    /// The same, with a fallback shown until the property has a value — what a
    /// lenient inflater produces for a path nothing answers yet (ADR-0062).
    public static Text of(String fallback, Observable<?> source) {
        return new Text(fallback, Objects.requireNonNull(source, "source"), Attributes.NONE);
    }

    public Text {
        Objects.requireNonNull(content, "content");
    }

    /// What this text says right now — the bound value, or the literal.
    ///
    /// Read at render rather than captured at build, so a change that arrives
    /// between a build and a frame is shown by that frame rather than the one
    /// after it. `null` in a property reads as the empty string: a value that has
    /// not loaded yet is nothing to draw, not the word "null".
    public String resolved() {
        if (source == null) {
            return content;
        }
        var value = source.get();
        return value == null ? "" : String.valueOf(value);
    }

    @Override
    public Text bound(Observable<?> source) {
        return new Text(content, source, attributes);
    }

    @Override
    public Text withAttributes(Attributes attributes) {
        return new Text(content, source, attributes);
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
        // A measured leaf: Yoga proposes a width, the paragraph wraps at it, and
        // the height that comes back is what sizes the box (ADR-0036).
        return Box.text(context.paragraph(style, resolved()), style.color()).style(style);
    }

    /// Builds a `text` node from markup.
    ///
    /// A bound node keeps its argument as the fallback rather than refusing it:
    /// `text bind="user.name" "…"` is what a lenient registry shows for a path
    /// nothing answers yet, and it is what a designer laying out a screen wants
    /// to see.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var source = wiring.bound(node);
        var literal = Wiring.label(node);
        return source == null
                ? new Text(literal, Attributes.of(node))
                : new Text(literal, source, Attributes.of(node));
    }
}
