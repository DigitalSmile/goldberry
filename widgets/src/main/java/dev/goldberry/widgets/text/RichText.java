package dev.goldberry.widgets.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.paint.Box;
import dev.goldberry.text.Paragraph;
import dev.goldberry.text.SpanPaint;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// One paragraph made of [Run]s, each in a style of its own, wrapping as one
/// text.
///
/// ```kdl
/// rich-text {
///     run "Give it "
///     run "Bleeding" class="keyword"
///     run " equal to the amount of boost it lost."
/// }
/// ```
///
/// In Java: `new RichText(new Run("Give it "), new Run("Bleeding", Set.of("keyword")), …)`.
///
/// ```css
/// rich-text > run.keyword { color: var(--gb-warning); font-weight: bold }
/// ```
///
/// A keyword in gold and bold in the middle of a sentence, and the sentence
/// still breaks where it would without it. A `row` of `text`s does not do that:
/// each wraps inside its own box, so a sentence in three widgets wraps as three
/// columns. Here the runs are shaped one by one, each in its own font, and
/// joined into one paragraph that the line breaker sees whole, so a line may
/// end in the middle of a run and the next begin there. A line is as tall as the
/// tallest font on it.
///
/// Each run is a node the cascade styles, a child of this one, so everything a
/// run does not set it inherits from here. The paragraph's `white-space`,
/// `text-align`, `overflow-wrap`, padding and the rest are this node's. A run's
/// colour, font and `text-decoration` are its own.
///
/// What a screen reader is told is the words: the runs' text end to end, with
/// no trace of where one style stopped and the next began.
///
/// Read more: [Text and links](https://goldberry.dev/docs/components/text.html#text).
///
/// @param runs       the stretches, in reading order
/// @param attributes the paragraph's id, classes and key
@Markup("rich-text")
public record RichText(List<Run> runs, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Semantics, Attributed<RichText> {

    public RichText {
        runs = List.copyOf(Objects.requireNonNull(runs, "runs"));
        Objects.requireNonNull(attributes, "attributes");
    }

    public RichText(Run... runs) {
        this(List.of(runs), Attributes.NONE);
    }

    /// The words, every run's text end to end: what the paragraph says.
    public String text() {
        var text = new StringBuilder();
        for (var run : runs) {
            text.append(run.text());
        }
        return text.toString();
    }

    @Override
    public RichText withAttributes(Attributes attributes) {
        return new RichText(runs, attributes);
    }

    /// The runs, as the nodes the cascade styles.
    @Override
    public List<Widget> children() {
        return List.copyOf(runs);
    }

    @Override
    public String cssType() {
        return "rich-text";
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

    @Override
    public Role role() {
        return Role.TEXT;
    }

    /// The words, as [#text()] has them: a reader hears a sentence, not its
    /// styles.
    @Override
    public String accessibleName() {
        return text();
    }

    /// One measured leaf, as a `text` is.
    ///
    /// The runs have rendered already, each to a box holding its words shaped in
    /// its own font and its resolved colour and rules. Those boxes are read here
    /// and not drawn: their paragraphs are joined into one, and a run whose
    /// colour or rules differ from the paragraph's becomes a [SpanPaint] over its
    /// stretch. Runs that look like the paragraph add nothing, so a `rich-text`
    /// whose runs are all unstyled draws what a `text` with the same words draws.
    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var spans = new ArrayList<Paragraph>(children.size());
        var paints = new ArrayList<SpanPaint>();
        var decorations = style.textFlow().decorations();
        var offset = 0;
        for (var child : children) {
            var run = child.text();
            if (run == null) {
                continue;
            }
            var end = offset + run.paragraph().text().length();
            var ruled = run.flow().decorations();
            if (run.argb() != style.color() || !ruled.equals(decorations)) {
                paints.add(new SpanPaint(offset, end, run.argb(), ruled));
            }
            spans.add(run.paragraph());
            offset = end;
        }
        var paragraph = spans.isEmpty() ? context.paragraph(style, "") : context.join(spans);
        return Box.of()
                .text(new Box.Text(paragraph, style.color(), style.textFlow(), paints))
                .style(style);
    }

    /// Builds a `rich-text` node from markup: its children are its runs, and
    /// nothing else may be.
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        if (node.argument().isPresent()) {
            throw new IllegalArgumentException("a rich-text says its words in runs, not in an argument:"
                    + " `rich-text { run \"Order\" class=\"keyword\"; run \": Reset the power of a unit.\" }`");
        }
        var runs = new ArrayList<Run>(children.size());
        for (var child : children) {
            if (!(child instanceof Run run)) {
                var name = child instanceof Styled styled
                        ? styled.cssType()
                        : child.getClass().getSimpleName();
                throw new IllegalArgumentException("a rich-text holds runs, and this one holds a " + name
                        + ": `rich-text { run \"Order\" class=\"keyword\"; run \": Reset the power of a unit.\" }`");
            }
            runs.add(run);
        }
        return new RichText(runs, Attributes.of(node));
    }
}
