package io.github.digitalsmile.goldberry.widgets.panel.masonry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;

/// What a [Masonry] remembers between frames: how tall each card came out, and —
/// for a responsive wall — how wide the wall itself came out.
///
/// The whole widget is those three numbers plus one greedy pass over them. A card
/// with no banked height counts as zero, which is what makes the first frame
/// round-robin — every column looks equally empty, so the cards go across — and
/// the second frame right.
///
/// It implements [MasonryBox.Ruler] itself rather than handing the box a lambda,
/// which is `TimeColumns`' arrangement and is worth the interface for one
/// reason: a lambda per build makes the `masonry` node a different value every
/// frame even when nothing about it changed, and a node that is never equal to
/// itself is a node no diff can ever skip. The cells pay that cost and have to —
/// their callback carries an index — but there is exactly one wall, and its
/// callback carries nothing.
final class MasonryState extends State<Masonry> implements MasonryBox.Ruler {

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

    /// The wall's own width, from last frame, or zero before there was one.
    ///
    /// Only a responsive wall reads it. A fixed one is told this number too and
    /// throws it away, which is cheaper than a second kind of `MasonryBox`.
    private double width;

    /// `masonry`'s resolved `gap`, banked from `render`.
    ///
    /// **No `setState`**, and not as an optimization: `render` is inside the
    /// frame this would dirty, so asking for a rebuild here is asking for the
    /// frame that is happening. It does not need one either — the gap arrives on
    /// the first `render`, which is strictly before the first width the router can
    /// deliver, so the very first count is already counted against the real
    /// pitch. A sheet that changed the gap without changing anything else reflows
    /// on the next width the wall is told, which is the next resize.
    private double gap;

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
    public void gap(double value) {
        gap = value;
    }

    @Override
    public void width(double value) {
        // The width is banked either way and the **count** is what decides
        // whether to build. A wall told it is one pixel wider has not changed
        // shape, and a wall that rebuilt on every pixel of a window drag would be
        // re-dealing a hundred cards per frame to place them all exactly where
        // they already are.
        var before = widget().columnsAt(width, gap);
        var after = widget().columnsAt(value, gap);
        if (before == after) {
            width = value;
            return;
        }
        setState(() -> width = value);
    }

    @Override
    public Widget build(BuildContext context) {
        var masonry = widget();
        var columns = masonry.columnsAt(width, gap);
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
            wrapped.add(new MasonryColumn(List.copyOf(bucket)));
        }
        return new MasonryBox(List.copyOf(wrapped), masonry.attributes(), this);
    }
}
