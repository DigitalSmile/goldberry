package io.github.digitalsmile.goldberry.widgets.panel.masonry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;

/// What a [Masonry] remembers between frames: how tall each card came out.
///
/// The whole widget is this table plus one greedy pass over it. A card with no
/// banked height counts as zero, which is what makes the first frame round-robin
/// — every column looks equally empty, so the cards go across — and the second
/// frame right.
final class MasonryState extends State<Masonry> {

    /// A hair, in logical pixels. A height that moved by less than this is the
    /// rasterizer rounding rather than the card changing, and reacting to it
    /// would be a frame loop that never idles (§1.7).
    private static final double SETTLED = 0.5;

    /// Banked heights, by the card's position in the description.
    ///
    /// By **position** rather than by identity, because a card is a value
    /// re-described every frame and there is nothing stable to key on. A list
    /// that reorders therefore reuses the previous card's height for one frame,
    /// which settles on the next — the same one-frame lag the first frame has.
    private final Map<Integer, Double> heights = new HashMap<>();

    /// Tells the state what a card came out as, and asks for a frame only if it
    /// changes the answer.
    private void measured(int index, double height) {
        var previous = heights.get(index);
        if (previous != null && Math.abs(previous - height) < SETTLED) {
            return;
        }
        // The assignment is recomputed in `build`, so this only has to make the
        // next build see a different table. Without the guard above it would
        // make one every frame, for ever.
        setState(() -> heights.put(index, height));
    }

    @Override
    public Widget build(BuildContext context) {
        var masonry = widget();
        var columns = Math.max(1, masonry.columns());
        var cards = masonry.children();

        var buckets = new ArrayList<List<Widget>>(columns);
        var totals = new double[columns];
        for (var i = 0; i < columns; i++) {
            buckets.add(new ArrayList<>());
        }

        for (var i = 0; i < cards.size(); i++) {
            // The shortest column, and **the emptiest of the equally short**.
            // The tiebreak is what makes an unmeasured first frame fill across
            // rather than stack every card in column one: before anything has
            // been measured every total is zero, so without it the comparison
            // never fires and a masonry's first frame is a single column.
            var shortest = 0;
            for (var c = 1; c < columns; c++) {
                var shorter = totals[c] < totals[shortest] - 1e-9;
                var equalButEmptier = Math.abs(totals[c] - totals[shortest]) <= 1e-9
                        && buckets.get(c).size() < buckets.get(shortest).size();
                if (shorter || equalButEmptier) {
                    shortest = c;
                }
            }
            var index = i;
            buckets.get(shortest).add(new MasonryCell(cards.get(i), i, height -> measured(index, height)));
            totals[shortest] += heights.getOrDefault(i, 0.0);
        }

        var wrapped = new ArrayList<Widget>(columns);
        for (var bucket : buckets) {
            wrapped.add(new MasonryColumn(List.copyOf(bucket), columns));
        }
        return new MasonryBox(List.copyOf(wrapped), masonry.attributes());
    }
}
