package dev.goldberry.widgets.text;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// One stretch of a [RichText]: words in a style of their own, in the middle of
/// a paragraph.
///
/// ```kdl
/// rich-text {
///     run "Order" class="keyword"
///     run ": Reset the power of a unit."
/// }
/// ```
///
/// In Java: `new Run("Order", Set.of("keyword"))`.
///
/// A node the cascade styles like any other, a child of its `rich-text`, so
/// `rich-text > run.keyword { color: …; font-weight: bold }` reaches it and
/// everything it does not set is inherited from the paragraph. What a run's
/// style decides is how its own words look: the font (`font-family`,
/// `font-size`, `font-weight`, `font-style`), the `color` and the
/// `text-decoration`. How the paragraph breaks, aligns and is boxed is the
/// `rich-text`'s, so a run's `white-space`, `text-align`, padding or background
/// are read by nothing.
///
/// Outside a `rich-text` a run draws as a `text` would, which is what it is.
///
/// Read more: [Text and links](https://goldberry.dev/docs/components/text.html#text).
///
/// @param text       the words, spaces included: runs are joined as written
/// @param attributes the classes the cascade styles it by, and an id
@Markup("run")
public record Run(String text, Attributes attributes) implements Widget.Leaf, Styled, Paints, Attributed<Run> {

    public Run {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(attributes, "attributes");
    }

    /// A run with no class: words in the paragraph's own style.
    public Run(String text) {
        this(text, Attributes.NONE);
    }

    /// A run styled by `classes`, which is how a document's spans map onto
    /// runs one to one: `<span class="keyword order">` is
    /// `new Run("Order", Set.of("keyword", "order"))`.
    public Run(String text, Set<String> classes) {
        this(text, new Attributes(null, Set.copyOf(classes), null));
    }

    @Override
    public Run withAttributes(Attributes attributes) {
        return new Run(text, attributes);
    }

    @Override
    public String cssType() {
        return "run";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// The run's words, shaped in its own resolved font, in its own colour and
    /// rules. Its `rich-text` reads these off the box and joins them; the box
    /// itself is drawn only when the run stands alone.
    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.text(context.paragraph(style, text), style.color()).style(style);
    }

    /// Builds a `run` node from markup: its argument is its words.
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Run(Wiring.label(node), Attributes.of(node));
    }
}
