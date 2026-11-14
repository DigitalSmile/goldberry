package dev.goldberry.widgets.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.kdl.KdlNode;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
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
/// ## Pressing, selecting, revealing
///
/// A run given a handler with [Run#onPress(Runnable)] is pressed where its
/// words are drawn, on any line they reach, and the pointer over it takes the
/// run's `cursor` (`pointer`, from the toolkit's stylesheet). A [Run#spoiler(boolean)]
/// is covered until pressed, and stays revealed for the life of this widget.
/// A `rich-text` made [#selectable(boolean)] is dragged across to select, `Ctrl+A`
/// selects it all and `Ctrl+C` copies what is selected; the highlight is the
/// `rich-text-selection` part's `background`. The selection is this
/// paragraph's alone, and a press anywhere in it starts a new one.
///
/// Read more: [Text and links](https://goldberry.dev/docs/components/text.html#text).
///
/// @param runs       the stretches, in reading order
/// @param selectable whether the words can be selected and copied
/// @param attributes the paragraph's id, classes and key
@Markup("rich-text")
public record RichText(List<Run> runs, boolean selectable, Attributes attributes)
        implements Widget.Stateful, Attributed<RichText> {

    public RichText {
        runs = List.copyOf(Objects.requireNonNull(runs, "runs"));
        Objects.requireNonNull(attributes, "attributes");
    }

    /// A paragraph of `runs` that cannot be selected.
    public RichText(List<Run> runs, Attributes attributes) {
        this(runs, false, attributes);
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

    /// This paragraph, selectable and copyable, or not.
    public RichText selectable(boolean value) {
        return new RichText(runs, value, attributes);
    }

    @Override
    public RichText withAttributes(Attributes attributes) {
        return new RichText(runs, selectable, attributes);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new RichTextState();
    }

    /// Builds a `rich-text` node from markup: its children are its runs, and
    /// nothing else may be. `selectable=#true` makes it selectable.
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
        return new RichText(runs, node.booleanProperty("selectable"), Attributes.of(node));
    }
}
