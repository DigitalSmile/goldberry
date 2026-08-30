package io.github.digitalsmile.goldberry.widgets.panel.masonry;

import java.util.List;

import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// Cards in columns, each card under the shortest one — a masonry.
///
/// ```kdl
/// masonry columns=3 gap=12 {
///     card { statistic label="Downloads" value="12,480" }
///     card { line-chart { … } }
/// }
/// ```
///
/// **Not in `core-widgets.md`.** §5's containers are `panel`, `card`, `group-box`,
/// `tabs`, `split-pane`, `accordion`, `collapse`, `carousel`, `skeleton`, and the
/// group is complete without this one. It is an addition, recorded in
/// `ARCHITECTURE.md` §17.1 rather than slipped in as though the canon had asked
/// for it ([ADR-0196](../../../../../../../../book/src/adr/0196-a-masonry-is-a-layout-that-reads-last-frame.md)).
/// The case it answers is real and has no other answer here: a wall of cards
/// whose heights differ — a chart beside a statistic beside a paragraph — laid
/// out in a `row` of `column`s by hand leaves whichever column got the tall ones
/// hanging off the bottom.
///
/// ## How it knows how tall a card is
///
/// It does not, on the first frame. Yoga is a flexbox engine and flexbox has no
/// masonry; nothing can tell a widget a child's height before that child is laid
/// out. So this **reads last frame**: every card reports what it came out as
/// through [io.github.digitalsmile.goldberry.input.handler.Measured], the state
/// banks it, and the next frame puts each card under the column that is currently
/// shortest. The first frame is therefore round-robin and the second is right,
/// which is one frame of settling nobody sees.
///
/// That is allowed here and is not allowed in general. `Measured`'s third rule is
/// that what it triggers must not change what it reports — a widget that resized
/// itself from its own measurement would be told a new size, resize, and never
/// settle. **Moving a card between columns does not change its height**, because
/// the columns are the same width, so the number being reported is stable under
/// the thing it causes. Change the columns to unequal widths and this becomes a
/// loop; that is why [#columns] is a count rather than a list of widths.
///
/// ## Order
///
/// Reading order is **down each column**, not across the row, which is what
/// masonry means and is also its one real cost: the third card is not
/// necessarily beside the second. Where that matters — a form, a ranked list —
/// the answer is a `column`, not this.
///
/// @param children   the cards
/// @param columns    how many columns; one is a plain column
/// @param attributes `id` and `class`, exactly as on the other containers
@Markup("masonry")
public record Masonry(List<Widget> children, int columns, Attributes attributes)
        implements Widget.Stateful, Attributed<Masonry> {

    /// What a `masonry` with no `columns=` gets. Three is what a dashboard of
    /// cards wants at a normal window width; a stylesheet cannot decide it,
    /// because §8's subset has no `column-count` and this is a count rather than
    /// a length.
    public static final int DEFAULT_COLUMNS = 3;

    public Masonry {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
        if (columns < 1) {
            throw new IllegalArgumentException("a masonry needs at least one column, and " + columns + " is not one");
        }
    }

    public Masonry(List<Widget> children) {
        this(children, DEFAULT_COLUMNS, Attributes.NONE);
    }

    /// This masonry in a different number of columns.
    public Masonry columns(int value) {
        return new Masonry(children, value, attributes);
    }

    // No `id()`: this widget styles nothing and carries no CSS type. The
    // document's id and classes travel on `attributes` and land on `MasonryBox`,
    // which is the node a stylesheet actually selects -- the arrangement every
    // stateful widget in the catalog uses.

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public Masonry withAttributes(Attributes value) {
        return new Masonry(children, columns, value);
    }

    @Override
    public State<?> createState() {
        return new MasonryState();
    }

    /// Builds a `masonry` from markup.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var columns = (int) node.numberProperty("columns", DEFAULT_COLUMNS);
        return new Masonry(children, Math.max(1, columns), Attributes.of(node));
    }
}
