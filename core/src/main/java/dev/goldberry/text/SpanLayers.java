package dev.goldberry.text;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

/// Lays decoration spans over a paragraph's own spans: a misspelled word's
/// wavy line over a word that is also bold, coloured or highlighted.
///
/// ```java
/// var drawn = SpanLayers.over(markSpans, misspellingSpans);
/// ```
///
/// A paragraph draws each character with the first span that covers it, so
/// two spans over one word cannot both be drawn. This cuts both lists at every
/// edge and makes one span per piece, in which the layer above gives the lines
/// and how they are drawn, and the layer below keeps its colour and what is
/// behind it. A layer's colour is used only where nothing is below it, and its
/// background only where it has one.
///
/// Each list is expected not to overlap itself, which is what a field's marks
/// and its misspellings are.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#paragraphs).
public final class SpanLayers {

    private SpanLayers() {}

    /// `below` with `above` laid over it. Either list may be empty, and then
    /// the other is the answer.
    public static List<SpanPaint> over(List<SpanPaint> below, List<SpanPaint> above) {
        if (above.isEmpty()) {
            return below;
        }
        if (below.isEmpty()) {
            return above;
        }
        var edges = new TreeSet<Integer>();
        for (var span : below) {
            edges.add(span.start());
            edges.add(span.end());
        }
        for (var span : above) {
            edges.add(span.start());
            edges.add(span.end());
        }
        var merged = new ArrayList<SpanPaint>();
        Integer from = null;
        for (var to : edges) {
            if (from != null && to > from) {
                var piece = piece(from, to, at(below, from), at(above, from));
                if (piece != null) {
                    merged.add(piece);
                }
            }
            from = to;
        }
        return List.copyOf(merged);
    }

    private static @Nullable SpanPaint piece(int from, int to, @Nullable SpanPaint below, @Nullable SpanPaint above) {
        if (above == null) {
            return below == null ? null : cut(below, from, to);
        }
        if (below == null) {
            return cut(above, from, to);
        }
        return new SpanPaint(
                from,
                to,
                below.argb(),
                above.decorations(),
                above.hasBackground() ? above.background() : below.background(),
                above.decorationPaint());
    }

    private static SpanPaint cut(SpanPaint span, int from, int to) {
        return span.start() == from && span.end() == to
                ? span
                : new SpanPaint(from, to, span.argb(), span.decorations(), span.background(), span.decorationPaint());
    }

    private static @Nullable SpanPaint at(List<SpanPaint> spans, int offset) {
        for (var span : spans) {
            if (span.covers(offset)) {
                return span;
            }
        }
        return null;
    }
}
