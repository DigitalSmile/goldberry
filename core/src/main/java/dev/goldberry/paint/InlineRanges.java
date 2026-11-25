package dev.goldberry.paint;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.Decoration;
import dev.goldberry.render.Cursor;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.text.Paragraph;
import dev.goldberry.text.flow.TextFlow;

/// Logical ranges of a paragraph and the visual pieces into which wrapping,
/// justification and bidirectional layout divide them.
public final class InlineRanges {
    /// Source offsets in the joined paragraph and an optional event target.
    public record Range(int start, int end, @Nullable Object owner, Cursor cursor, Decoration decoration) {}

    /// Offsets relative to the owning range and bounds relative to the content box.
    public record Piece(int start, int end, LogicalRect bounds, boolean rtl, double gapAfter) {}

    private final List<Range> ranges;
    private final boolean hasOutline;
    private List<List<Piece>> pieces = List.of();
    private @Nullable Paragraph previous;
    private @Nullable TextFlow previousFlow;
    private double previousWidth;

    public InlineRanges(List<Range> ranges) {
        this.ranges = List.copyOf(ranges);
        hasOutline = this.ranges.stream().anyMatch(range -> range.decoration().hasOutline());
        int end = 0;
        for (var range : ranges) {
            if (range.start() < end || range.end() < range.start()) {
                throw new IllegalArgumentException("Inline ranges must be ordered and disjoint");
            }
            end = range.end();
        }
    }

    public boolean hasOutline() {
        return hasOutline;
    }

    public List<Range> ranges() {
        return ranges;
    }

    /// One list per logical range, split at visual lines, bidi runs and stretched gaps.
    public List<List<Piece>> place(Paragraph paragraph, TextFlow flow, double width) {
        if (previous == paragraph && flow.equals(previousFlow) && previousWidth == width) {
            return pieces;
        }
        var placed = new ArrayList<List<Piece>>(ranges.size());
        ranges.forEach(_ -> placed.add(new ArrayList<>()));
        paragraph.placeFragments(width, flow, (from, to, x, y, height, rtl, gapAfter) -> {
            for (int i = firstAfter(from); i < ranges.size(); i++) {
                var range = ranges.get(i);
                if (range.start() >= to) {
                    break;
                }
                var start = Math.max(from, range.start());
                var end = Math.min(to, range.end());
                if (start == end) {
                    continue;
                }
                var left = x + (rtl ? paragraph.widthBetween(end, to) : paragraph.widthBetween(from, start));
                var extra = end == to ? gapAfter : 0;
                var bounds = LogicalRect.of(
                        (float) (rtl ? left - extra : left),
                        (float) y,
                        (float) (paragraph.widthBetween(start, end) + extra),
                        (float) height);
                placed.get(i).add(new Piece(start - range.start(), end - range.start(), bounds, rtl, extra));
            }
        });
        pieces = placed.stream().map(List::copyOf).toList();
        previous = paragraph;
        previousFlow = flow;
        previousWidth = width;
        return pieces;
    }

    private int firstAfter(int offset) {
        int low = 0;
        int high = ranges.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (ranges.get(mid).end() <= offset) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }
}
