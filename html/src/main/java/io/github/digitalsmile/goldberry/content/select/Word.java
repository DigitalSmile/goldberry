package io.github.digitalsmile.goldberry.content.select;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.handler.Located;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// One word of a rendered document — `word`, a **part**.
///
/// What `text` was until documents needed to be selectable. It draws exactly what a
/// `text` widget draws, through the same measured-leaf box and the same paragraph
/// cache, and adds the two things a selection cannot work without:
///
/// - it is [Located], so it says **where** it was laid out, once a frame and only
///   when that changes;
/// - it hands its shaped [io.github.digitalsmile.goldberry.text.Paragraph] to the
///   geometry, which is what turns a pointer's **x** into a character offset —
///   `Paragraph.offsetAt`, the same arithmetic a caret in a `text-input` uses.
///
/// Neither is something `text` could be asked for. A callback per instance is not a
/// thing a shared catalog widget carries, and wrapping every word in a reporting node
/// would double the element count of a document — which is the cost ADR-0299 had just
/// finished paying down. So a document's words are this instead, and the *cost of the
/// change is nothing*: one widget per word either way (ADR-0301).
///
/// ## It can also carry something that is not text
///
/// A link is a `button` and an image is a `picture`, and a selection that skipped them
/// would copy "Read first." out of "Read the help first." So a `Word` may wrap a
/// child instead of drawing text: it contributes the child's **words** to what a
/// selection copies, and the child draws itself. The box it adds carries no padding,
/// no gap and no size of its own.
///
/// Such a word shapes nothing, so the geometry never hears from [WordGeometry#shaped]
/// about it: the label belongs to the control and is shaped in the control's style.
/// Where a caret inside one falls is then a proportion of the box it landed in, which
/// the geometry works out for itself — the ends of it are exact either way, and those
/// are what a double-click and a drag across a link are made of.
///
/// @param text what it says — the label of the child, when there is one
/// @param attributes the classes the fold put on it, which is what `markdown.css` and
///        `html.css` style
/// @param entry where this word reports to
/// @param child what to draw instead of the text, or null for an ordinary word
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
        // word appearing above it: the entries belong to the block (ADR-0389), so a
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
        // word costs a lookup rather than 56 µs of shaping (ADR-0037, ADR-0299).
        var paragraph = context.paragraph(style, text);
        WordGeometry.shaped(entry, paragraph);
        return Box.text(paragraph, style.color()).style(style);
    }
}
