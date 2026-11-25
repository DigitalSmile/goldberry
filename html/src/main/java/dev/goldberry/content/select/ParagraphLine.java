package dev.goldberry.content.select;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.content.image.Picture;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Decoration;
import dev.goldberry.css.value.CssColor;
import dev.goldberry.input.handler.Located;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.BoxEdges;
import dev.goldberry.paint.InlineRanges;
import dev.goldberry.render.Cursor;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.text.Paragraph;
import dev.goldberry.text.SpanPaint;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Row;

/// A document paragraph measured and painted by the text stack. The child
/// elements supply their cascaded faces and inks. Links keep their button
/// elements as the targets of the corresponding ranges.
public final class ParagraphLine implements Widget.Leaf, Styled, Paints, Located {
    private final List<Widget> runs;
    private final Attributes attributes;

    private record Placement(Word word, boolean separator) {}

    private List<Placement> placements = List.of();
    private @Nullable Box rendered;
    private @Nullable LogicalRect bounds;
    private @Nullable LogicalRect clip;

    private ParagraphLine(List<Word> words, Attributes attributes) {
        var children = new ArrayList<Widget>();
        var group = new ArrayList<Word>();
        for (var word : words) {
            if (!group.isEmpty()
                    && (word.child() != null || !group.getFirst().attributes().equals(word.attributes()))) {
                children.add(StyledWords.of(group));
                group.clear();
            }
            if (word.child() == null) {
                group.add(word);
            } else {
                children.add(word);
            }
        }
        if (!group.isEmpty()) {
            children.add(StyledWords.of(group));
        }
        runs = List.copyOf(children);
        this.attributes = attributes;
    }

    public static Widget of(List<Word> words, Attributes attributes) {
        return words.isEmpty() ? new Row(List.of(), attributes) : new ParagraphLine(words, attributes);
    }

    @Override
    public String cssType() {
        return "paragraph";
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public List<Widget> children() {
        return runs;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var parts = new ArrayList<Paragraph>();
        var paints = new ArrayList<SpanPaint>();
        var ranges = new ArrayList<InlineRanges.Range>();
        var visible = new ArrayList<Placement>();
        var offset = 0;
        for (var wrapper : children) {
            if (!(wrapper.owner() instanceof Element element)) {
                continue;
            }
            var group = StyledWords.ofWidget(element.widget());
            if (group.isEmpty()) {
                continue;
            }
            var first = group.getFirst();
            var button = first.child() instanceof Button;
            var embedded = first.child() != null;
            if (embedded && wrapper.children().isEmpty()) {
                continue;
            }
            var source = embedded ? wrapper.children().getFirst() : wrapper;
            var text = source.text();
            if (text == null && button && !source.children().isEmpty()) {
                text = source.children().getFirst().text();
            }
            if (text == null) {
                continue;
            }
            if (!parts.isEmpty() && first.entry().spaceBefore()) {
                ranges.add(new InlineRanges.Range(offset, offset + 1, null, Cursor.DEFAULT, Decoration.NONE));
                visible.add(new Placement(first, true));
                parts.add(context.paragraph(style, " "));
                offset++;
            }
            var end = offset + text.paragraph().text().length();
            parts.add(text.paragraph());
            addPaint(paints, offset, end, style, source, text, first.child() instanceof Picture);
            addRanges(group, offset, end, source, text.paragraph(), ranges, visible);
            offset = end;
        }
        var moved = !placements.equals(visible);
        if (moved) {
            for (var placement : placements) {
                WordGeometry.hidden(placement.word().entry());
            }
        }
        placements = List.copyOf(visible);
        var paragraph = parts.isEmpty() ? context.paragraph(style, "") : context.join(parts);
        var previous = rendered == null ? null : rendered.text();
        var inline = previous == null ? null : previous.ranges();
        if (inline == null || !inline.ranges().equals(ranges)) {
            inline = new InlineRanges(ranges);
        }
        var box = Box.of()
                .style(style)
                .text(new Box.Text(
                        paragraph,
                        style.color(),
                        style.textFlow(),
                        paints,
                        style.typography().lettering().shadows(),
                        inline));
        if (!moved && box.equals(rendered)) {
            return box;
        }
        rendered = box;
        if (bounds != null && clip != null) {
            located(bounds, clip);
        }
        return box;
    }

    private static void addPaint(
            List<SpanPaint> paints,
            int offset,
            int end,
            ComputedStyle style,
            Box source,
            Box.Text text,
            boolean picture) {
        var alpha = source.presence().isVisible() ? source.opacity() : 0;
        var ink = CssColor.fade(picture ? 0xFFFFFFFF : text.argb(), alpha);
        var background = CssColor.fade(source.background(), alpha);
        if (ink != style.color()
                || !text.flow().textDecorations().equals(style.textFlow().textDecorations())
                || background != 0) {
            paints.add(new SpanPaint(
                    offset,
                    end,
                    ink,
                    text.flow().decorations(),
                    background,
                    text.flow().decorationPaint()));
        }
    }

    private static void addRanges(
            List<Word> group,
            int offset,
            int end,
            Box source,
            Paragraph paragraph,
            List<InlineRanges.Range> ranges,
            List<Placement> visible) {
        var first = group.getFirst();
        var button = first.child() instanceof Button;
        var within = 0;
        for (var word : group) {
            if (within > 0 && word.entry().spaceBefore()) {
                var space = offset + within;
                ranges.add(new InlineRanges.Range(space, space + 1, null, Cursor.DEFAULT, Decoration.NONE));
                visible.add(new Placement(word, true));
                within++;
            }
            int start = offset + within;
            ranges.add(new InlineRanges.Range(
                    start,
                    first.child() instanceof Picture ? end : start + word.text().length(),
                    button && source.presence().isHit() ? source.owner() : null,
                    source.cursor(),
                    button ? source.decoration() : Decoration.NONE));
            WordGeometry.shaped(word.entry(), paragraph, within);
            visible.add(new Placement(word, false));
            within += word.text().length();
        }
    }

    @Override
    public void located(LogicalRect self, LogicalRect clipping) {
        bounds = self;
        clip = clipping;
        var box = rendered;
        if (box == null) {
            return;
        }
        var edges = BoxEdges.content(box, self.width(), self.height());
        var text = box.text();
        var ranges = text == null ? null : text.ranges();
        if (text == null || ranges == null) {
            return;
        }
        var pieces = ranges.place(text.paragraph(), text.flow(), Math.max(0, self.width() - edges.horizontal()));
        var left = self.left() + (float) edges.left();
        var top = self.top() + (float) edges.top();
        for (int i = 0; i < placements.size(); i++) {
            var placement = placements.get(i);
            if (placement.separator()) {
                WordGeometry.separator(placement.word().entry(), pieces.get(i), left, top);
            } else {
                WordGeometry.placed(placement.word().entry(), pieces.get(i), left, top, clipping);
            }
        }
    }
}
