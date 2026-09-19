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
/// masonry min-column-width=320 gap=12 {
///     card { statistic label="Downloads" value="12,480" }
///     card { line-chart { … } }
/// }
/// ```
///
/// §1's row, which it did not have until ADR-0436: `masonry` was named once in
/// `core-widgets.md`, as what the showcase's screens are made of, and specified
/// nowhere. The case it answers is real and has no other answer in the catalog —
/// a wall of cards whose heights differ, a chart beside a statistic beside a
/// paragraph, laid out in a `row` of `column`s by hand leaves whichever column
/// got the tall ones hanging off the bottom.
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
/// loop; that is why [#columns()] is a count rather than a list of widths.
///
/// ## Two ways to say how many columns, and they are exclusive
///
/// [#columns()] is a **fixed count**: two columns at 1200 are two columns at 720
/// as well, half as wide and twice as tall, which is a phone layout on a desktop.
/// [#minColumnWidth()] is *as many columns as fit at this width, at least one* —
/// the responsive wall, and what a screen that has to survive a window drag
/// wants.
///
/// A `masonry` given **both** is an [IllegalArgumentException] at construction
/// rather than a precedence rule nobody remembers, and one given **neither** is
/// responsive at [#DEFAULT_MIN_COLUMN_WIDTH]. The default moved there in
/// ADR-0436: a fixed three was the old default and it is the one setting that
/// cannot be right at two window sizes.
///
/// ## Reading its own width, and the one masonry that must not
///
/// The count comes from the wall's **own** width, banked last frame through the
/// same door the heights come through — [MasonryBox] carries the CSS type, so it
/// is the node whose rectangle *is* the masonry's, and it reports it.
///
/// `Measured`'s third rule holds for that too: a column count changes the wall's
/// **height** and not its width, so the number is stable under the thing it
/// causes. §1 hedges that — *"only for a `masonry` whose width comes from its
/// parent"* — and the hedge turns out to be unnecessary here, for a reason that
/// is worth knowing because it is not this widget's doing.
///
/// The feared loop is real arithmetic. A wall sized to its own content would be
/// `n` columns each as wide as the widest card in it, so a bigger `n` makes a
/// wider wall, which asks for a bigger `n`. It needs the columns to be as wide as
/// their cards — and since ADR-0373 they are not: a `masonry-column` is
/// `flex-basis: 0`, so it contributes **nothing** to its parent's content width,
/// and a masonry with no definite width of its own comes out at zero however many
/// columns it has. The count is therefore independent of itself by construction,
/// which is what rule 3 asks for, and the construction is a line of
/// `controls.css` rather than a promise about the parent.
///
/// So nothing is refused, because there is nothing to refuse — and what is left
/// is worse *documentation* rather than a worse layout. A `masonry` in a
/// shrink-to-fit box is zero pixels wide with its cards hanging out of it, and
/// has been since ADR-0373, with a fixed [#columns()] exactly as much as with
/// this. `MasonrySettleTest` pins both halves down: the stretched wall settles in
/// three layouts, and the shrink-to-fit one settles in one, with a fixed count
/// and a responsive one alike. Put a wall somewhere that gives it a width; the
/// mode is not what breaks in a box that does not.
///
/// ## Order
///
/// Reading order is **down each column**, not across the row, which is what
/// masonry means and is also its one real cost: the third card is not
/// necessarily beside the second. Where that matters — a form, a ranked list —
/// the answer is a `column`, not this.
///
/// @param children       the cards
/// @param columns        how many columns; one is a plain column, and [#UNSET]
///                       is "count them from the width"
/// @param minColumnWidth how narrow a column may get before there is one fewer
///                       of them, in logical pixels; [#UNSET] when a fixed
///                       [#columns()] was named
/// @param attributes     `id` and `class`, exactly as on the other containers
@Markup("masonry")
public record Masonry(List<Widget> children, int columns, int minColumnWidth, Attributes attributes)
        implements Widget.Stateful, Attributed<Masonry> {

    /// Neither a count nor a width: what the other one is when this one was
    /// named.
    ///
    /// Negative rather than zero so that `columns=0` is still the error it always
    /// was. Zero is a thing an author can write and a thing a spreadsheet can
    /// produce, and reading it as "I said nothing" would turn a typo into a
    /// silent change of layout mode.
    public static final int UNSET = -1;

    /// What a `masonry` that names neither `columns` nor `min-column-width` gets,
    /// from §3's row: a card that holds a line of prose at `body` without
    /// hyphenating.
    ///
    /// A *width* is the default rather than a count, which is the whole of
    /// ADR-0436 in one constant. A count cannot be right at two window sizes and
    /// a default is precisely the value nobody thought about, so the one that
    /// ships has to be the one that survives a resize.
    public static final int DEFAULT_MIN_COLUMN_WIDTH = 320;

    public Masonry {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
        if (columns != UNSET && minColumnWidth != UNSET) {
            throw new IllegalArgumentException("a masonry is a count of columns or a minimum column width and not"
                    + " both, and this one was given columns=" + columns + " and min-column-width=" + minColumnWidth);
        }
        if (columns == UNSET && minColumnWidth == UNSET) {
            minColumnWidth = DEFAULT_MIN_COLUMN_WIDTH;
        }
        if (columns != UNSET && columns < 1) {
            throw new IllegalArgumentException("a masonry needs at least one column, and " + columns + " is not one");
        }
        if (minColumnWidth != UNSET && minColumnWidth < 1) {
            throw new IllegalArgumentException(
                    "a masonry's min-column-width is a width in logical pixels, and " + minColumnWidth + " is not one");
        }
    }

    /// A responsive wall at [#DEFAULT_MIN_COLUMN_WIDTH].
    public Masonry(List<Widget> children) {
        this(children, UNSET, UNSET, Attributes.NONE);
    }

    /// A wall of a fixed number of columns.
    public Masonry(List<Widget> children, int columns, Attributes attributes) {
        this(children, columns, UNSET, attributes);
    }

    /// This masonry in a fixed number of columns, which drops any
    /// [#minColumnWidth()] it had — the two cannot both be set, so a chain that
    /// left the old one in place would throw halfway through itself.
    public Masonry columns(int value) {
        return new Masonry(children, value, UNSET, attributes);
    }

    /// This masonry with as many columns as fit at `value`, which drops any fixed
    /// [#columns()] it had, for [#columns(int)]'s reason.
    public Masonry minColumnWidth(int value) {
        return new Masonry(children, UNSET, value, attributes);
    }

    /// Whether this wall counts its own columns rather than being told.
    public boolean responsive() {
        return columns == UNSET;
    }

    /// How many columns this wall has at `width`, with `gap` between them.
    ///
    /// The arithmetic is the one thing worth writing down: `n` columns need
    /// `n` minimums **and `n - 1` gaps**, so the largest `n` that fits is
    /// `⌊(width + gap) / (minColumnWidth + gap)⌋` — add one gap to both sides and
    /// the fence-post goes away. Counting without the gaps over-counts, and an
    /// over-counted wall is one whose columns are each a few pixels under the
    /// minimum that was the point of asking.
    ///
    /// A width that has not been measured yet is not a width. The first frame has
    /// none — that is [io.github.digitalsmile.goldberry.input.handler.Measured]'s
    /// whole premise — and **one** column is what it gets, because a wall that
    /// guessed would be photographed mid-guess by anything that renders a fixed
    /// number of passes.
    ///
    /// @param width the wall's own width in logical pixels, or a non-positive
    ///              number when nothing has measured it yet
    /// @param gap   `masonry`'s resolved `gap`, which is the stylesheet's
    /// @return at least one, and [#columns()] unchanged when this wall was given
    ///         a fixed count
    public int columnsAt(double width, double gap) {
        if (!responsive()) {
            return columns;
        }
        if (!Double.isFinite(width) || width <= 0 || !Double.isFinite(gap) || gap < 0) {
            return 1;
        }
        return Math.max(1, (int) Math.floor((width + gap) / (minColumnWidth + gap)));
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
        return new Masonry(children, columns, minColumnWidth, value);
    }

    @Override
    public State<?> createState() {
        return new MasonryState();
    }

    /// Builds a `masonry` from markup.
    ///
    /// Neither property is clamped. `columns=0` was quietly read as one until
    /// ADR-0436 and is a build-time failure now, for the reason the two
    /// properties together are one: a layout that silently disagrees with the
    /// document is found by looking at a picture, and a document that throws is
    /// found by running anything.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var columns = (int) node.numberProperty("columns", UNSET);
        var minColumnWidth = (int) node.numberProperty("min-column-width", UNSET);
        return new Masonry(children, columns, minColumnWidth, Attributes.of(node));
    }
}
