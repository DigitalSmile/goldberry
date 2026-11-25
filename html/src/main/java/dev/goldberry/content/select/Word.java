package dev.goldberry.content.select;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.input.handler.Located;
import dev.goldberry.paint.Box;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// A selectable word or inline element. A paragraph uses its entry to map
/// shaped text back to the document. Standalone words, including list markers
/// and lines of preformatted text, report their own layout.
public record Word(
        String text,
        Attributes attributes,
        WordGeometry.Entry entry,
        @Nullable Widget child) implements Widget.Leaf, Styled, Paints, Located {

    /// An ordinary word.
    public Word(String text, Attributes attributes, WordGeometry.Entry entry) {
        this(text, attributes, entry, null);
    }

    @Override
    public String cssType() {
        return "word";
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
    public Object key() {
        // **Keyed by the entry it reports to**, which is the identity that survives a
        // word appearing above it: the entries belong to the block, so a
        // paragraph that did not change keeps the same ones however far down the page
        // it has moved. Keyed by position in the document, as it was until then, a
        // space typed into the first paragraph renumbered every word in the note and
        // the element tree matched each one to its neighbour's element -- which is a
        // re-measure and a re-layout of everything below the cursor.
        return entry;
    }

    @Override
    public List<Widget> children() {
        return child == null ? List.of() : List.of(child);
    }

    /// Where the last frame put this word, in the window's own coordinates —
    /// including the scroll it has been translated by, which is the whole point: a
    /// selection is about where a reader sees the words.
    @Override
    public void located(LogicalRect self, LogicalRect clip) {
        WordGeometry.placed(entry, self, clip);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        if (child != null) {
            return Box.of().style(style).children(children.toArray(Box[]::new));
        }
        // The same measured leaf `text` builds, and through the same cache -- so a
        // word costs a lookup rather than 56 µs of shaping.
        var paragraph = context.paragraph(style, text);
        WordGeometry.shaped(entry, paragraph);
        return Box.text(paragraph, style.color()).style(style);
    }
}
