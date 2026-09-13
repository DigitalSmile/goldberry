package io.github.digitalsmile.goldberry.example.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.digitalsmile.goldberry.assets.BundledAssets;
import io.github.digitalsmile.goldberry.bind.Subscription;
import io.github.digitalsmile.goldberry.bind.runtime.Models;
import io.github.digitalsmile.goldberry.example.ShowcaseModel;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollAxis;
import io.github.digitalsmile.goldberry.widgets.form.textinput.TextInput;
import io.github.digitalsmile.goldberry.widgets.panel.masonry.Masonry;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The **Icons** screen: all 1544 of them, and a field to find one.
///
/// The eleventh screen, and the first one the gallery's `Ctrl+<n>` cannot reach —
/// there are ten digits. That is not an oversight being tolerated: `Ctrl+0` is
/// the tenth and an eleventh key does not exist, so the strip, the arrow keys and
/// Edit ▸ Go to are how this one is reached, and `GalleryOrderTest` already said
/// "ten digits, however many screens there are" before there were eleven
/// ([ADR-0307]).
///
/// ## Why it is a screen and not a card
///
/// Every other screen answers "what does this widget do". This one answers a
/// question a reader has while writing a *document*: `icon="…"` takes a name from
/// a set of 1544, and until now the only way to find one was to read Lucide's
/// website. A sheet of them beside the gallery is the difference between a
/// bundled asset and a usable one.
///
/// ## It reflows, and the column count is last frame's
///
/// The sheet is a `masonry` whose column count is **as many tiles as fit**,
/// divided out of the width the last frame reported through [Measured]. Dragging
/// the window wider adds a column; dragging it narrower takes one away, and the
/// last column is always a whole tile rather than a clipped one
/// ([ADR-0309]).
///
/// A masonry of **equal-height** tiles is a reflowing grid in reading order —
/// that widget's warning about reading down a column rather than across applies
/// to cards of differing heights, and alphabetical order here reads left to
/// right. See [IconsState#scrolledSheet()].
///
/// ## Everything is built, and it is not free
///
/// The `list` this replaced virtualized; a masonry cannot, because placing a card
/// under the shortest column is a decision about **every** card. So all 1544
/// tiles are in the tree, and `FrameBudgetTest` measures what that costs rather
/// than leaving it to a paragraph like this one:
///
/// | | elements | opens in | style | layout |
/// |---|---|---|---|---|
/// | the whole sheet | 4709 | 464 ms | 3.7 ms | 0.6 ms |
/// | after typing `ar` | 1085 | — | 0.9 ms | 0.2 ms |
///
/// Two honest readings of that. **Opening it is slow** — switching to this tab
/// builds 1544 tiles and shapes 1544 captions, and half a second is what that
/// costs. **A settled frame is dear but workable**: 4.3 ms of build, style and
/// layout is a quarter of a 60 Hz frame before anything is rasterized, where
/// every other screen in the gallery is under one.
///
/// Which makes the search field the performance story rather than a convenience:
/// two letters take about three quarters of the cost back, and looking for an
/// icon is the only reason to be here. Asserted as a **ratio** rather than
/// against another screen's budget — 1085 elements is still five times a wall's,
/// and the honest claim is that searching narrows it rather than that a narrowed
/// sheet is cheap ([ADR-0309]).
///
/// The `scroll` is around the masonry and not around the screen, so the field and
/// the count stay put while the icons move under them. [Screen] does not wrap this
/// screen in a viewport, because §2.4 bans a scroller inside a scroller.
///
/// The icons themselves are cached and built **lazily** — one `Icon` per name, on
/// the frame a tile first needs it, kept for the life of the screen. An icon is a
/// value since [ADR-0277], so the cache holds no native memory and needs no
/// closing.
///
/// @param model   where the query lives, so a `text-input` can bind to it
/// @param actions what the field reports to
public record IconsScreen(ShowcaseModel model, ShowcaseModel.Actions actions) implements Widget.Stateful {

    /// One tile's width, in logical pixels — the number the column count is
    /// divided out of, and the one `showcase.css` has to agree with.
    ///
    /// 152, because the widest names in the set — `square-arrow-out-down-right`,
    /// `circle-arrow-out-up-left` — need two caption lines at that width and
    /// three at anything narrower, and a third line does not fit the tile.
    static final double TILE_WIDTH = 152;

    /// The gap between tiles, which is `.icon-row`'s and `masonry`'s own.
    ///
    /// Stated here as well because the column count is arithmetic over both
    /// numbers, and a widget cannot read what a stylesheet resolved for a node it
    /// is about to describe.
    static final double TILE_GAP = 8;

    /// What to assume before the first frame has said how wide the sheet is.
    ///
    /// Seven, which is what the window opens at — so the first frame is right
    /// rather than merely legal, and the settling frame `Measured` costs is one
    /// nobody sees at the default size.
    public static final int DEFAULT_COLUMNS = 7;

    /// The size every icon on this sheet is built at.
    ///
    /// 20 rather than Lucide's design size of 24: a sheet is scanned rather than
    /// read, and 24 makes the columns too wide to fit the shell's content pane at
    /// the window's opening width.
    static final double ICON_SIZE = 20;

    /// How many tiles fit across `width`.
    ///
    /// `n` tiles and `n - 1` gaps, solved for `n` and floored — so the last
    /// column is a whole tile rather than a clipped one. At least one, because a
    /// window narrower than a single tile still has to draw something, and a
    /// masonry refuses a column count below one.
    ///
    /// Static and public because it is the whole of the layout rule, and
    /// `IconsScreenTest` is in the application's own package rather than in
    /// `…example.ui` — asserting it through a built element tree would be
    /// asserting it twice removed.
    public static int columnsFor(double width) {
        if (!Double.isFinite(width) || width <= 0) {
            return DEFAULT_COLUMNS;
        }
        return Math.max(1, (int) Math.floor((width + TILE_GAP) / (TILE_WIDTH + TILE_GAP)));
    }

    @Override
    public State<?> createState() {
        return new IconsState();
    }

    /// The query, the cache, and the rows.
    static final class IconsState extends State<IconsScreen> {

        /// One [Icon] per name, built the first frame a row needs it.
        ///
        /// A plain `HashMap` and not a bounded cache: 1544 icons at 20 points is
        /// a few hundred kilobytes of `double[]`, it is bounded by construction —
        /// there are only 1544 names — and a sheet a reader has scrolled to the
        /// bottom of has paid the whole cost once rather than once per visit.
        private final Map<String, Icon> icons = new HashMap<>(2048);

        /// Every bundled name, **sorted here**.
        ///
        /// `BundledAssets.iconNames()` is the key set of a `Map.copyOf`, which is
        /// a hash order — it promises no order and does not have one, and the
        /// first drawing of this sheet duly opened on `book-lock, calendar-off,
        /// badge, list-start`. A sheet a reader scans has to be alphabetical, and
        /// sorting 1544 strings once when the screen is created is the whole
        /// cost.
        private final List<String> all =
                BundledAssets.iconNames().stream().sorted().toList();

        /// The query the names were last filtered for, and what came out.
        ///
        /// Banked because filtering 1544 strings runs in `build` and `build` runs
        /// on every frame this screen is rebuilt for — which includes every
        /// keystroke *and* every unrelated `setState` in the window. Recomputing
        /// a stable answer is the cheapest kind of waste and also the easiest to
        /// leave in.
        private String filteredFor;

        private List<String> matching = List.of();

        /// How many columns the last frame's width allows.
        ///
        /// Banked rather than recomputed from a width field, so the guard in
        /// [#measured] compares the thing that actually matters: the sheet is a
        /// different tree only when the *count* changes, and a window dragged
        /// three pixels wider changes nothing.
        private int columns = DEFAULT_COLUMNS;

        private Subscription watching;

        @Override
        protected void initState() {
            // The query is a **structural** change to this screen: a different
            // query is a different number of rows, not a different value inside
            // one. So it is watched rather than bound, which is the same split
            // the gallery's `Screen` makes for its tab list (ADR-0109).
            watching = Models.observable(widget().model(), "app.icon-query").subscribe(value -> setState(() -> {}));
        }

        @Override
        protected void dispose() {
            if (watching != null) {
                watching.close();
                watching = null;
            }
        }

        @Override
        public Widget build(BuildContext context) {
            var query = widget().model().iconQuery();
            refilter(query);

            return new Column(List.of(header(query), scrolledSheet()), Attributes.NONE.id("icons-screen"));
        }

        /// The title, the field and the count.
        ///
        /// The count is prose rather than a `badge`, because it is a sentence
        /// about a search — "42 of 1544" — and a badge is a number beside the
        /// thing it counts.
        private Widget header(String query) {
            var found = matching.size();
            return new Column(
                    List.of(
                            new Text("Every bundled icon", Attributes.NONE.classes("screen-title")),
                            new Text(
                                    "Lucide's " + all.size() + " icons, compiled into one path table at build"
                                            + " time (ADR-0033). The name under each is what a document"
                                            + " writes in icon=\"…\" and what Icon.bundled(name, size) takes.",
                                    Attributes.NONE.classes("screen-note")),
                            new Row(
                                    List.of(
                                            TextInput.of(
                                                            Models.observable(widget().model(), "app.icon-query"),
                                                            widget().actions()::setIconQuery)
                                                    .placeholder("Search 1544 icons — try \"arrow\"")
                                                    .id("icon-search"),
                                            new Text(
                                                    query.isEmpty()
                                                            ? all.size() + " icons"
                                                            : found + " of " + all.size(),
                                                    Attributes.NONE
                                                            .id("icon-count")
                                                            .classes("caption"))),
                                    Attributes.NONE.id("icon-search-row"))),
                    Attributes.NONE.id("icons-header"));
        }

        /// The sheet: a **masonry that follows the window**, in a viewport.
        ///
        /// ## Why a masonry and not a row of rows
        ///
        /// A masonry is a count of equal-width columns and nothing else, which is
        /// exactly a reflowing grid — and the count is the one thing that has to
        /// change when the window does. The previous arrangement chunked the
        /// names into rows of a **fixed** seven, so a wide window left a band of
        /// empty space and a narrow one clipped the last column
        /// ([ADR-0309]).
        ///
        /// Its reading order is the right one here, which is not obvious and is
        /// worth stating: `Masonry` warns that it reads *down* each column rather
        /// than across, and that warning is about cards of **differing** heights.
        /// Every tile on this sheet is the same height, so "the shortest column,
        /// and the emptiest of the equally short" places them across the row —
        /// alphabetical order reads left to right, the way a reader scanning for
        /// `chevron-right` expects.
        ///
        /// ## The viewport is the sheet's, not the gallery's
        ///
        /// [Screen] does not wrap this screen in one, because §2.4 bans a scroller
        /// inside a scroller. So the `scroll` is here, around the masonry and
        /// **not** around the header — the search field and the count stay put
        /// while the icons move under them, which is the whole point of a field
        /// that filters a long list.
        private Widget scrolledSheet() {
            if (matching.isEmpty()) {
                return new Text(
                        "No icon has that in its name. Lucide's names are English and hyphenated —"
                                + " \"chevron-right\", \"square-pen\", \"file-text\".",
                        Attributes.NONE.id("icons-empty").classes("screen-note"));
            }
            var tiles = new ArrayList<Widget>(matching.size());
            for (var name : matching) {
                // Keyed by name, so filtering reconciles the tiles that survived
                // rather than rebuilding the sheet (ADR-0004).
                tiles.add(new IconTile(name, iconFor(name), Attributes.NONE.key(name)));
            }
            var wall = new Masonry(tiles, columns, Attributes.NONE.id("icon-wall"));
            return new Scroll(
                    List.of(new IconSheet(wall, this::measured)),
                    ScrollAxis.VERTICAL,
                    Attributes.NONE.id("icon-viewport"));
        }

        /// Told how wide the sheet came out, and asks for a rebuild only when
        /// that changes the **column count**.
        ///
        /// The guard is what keeps this from being a frame loop that never idles
        /// (§1.7): a window dragged three points wider reports a new width every
        /// frame of the drag and almost never a new count.
        private void measured(double width) {
            var next = columnsFor(width);
            if (next == columns) {
                return;
            }
            setState(() -> columns = next);
        }

        /// The icon for `name`, built once.
        private Icon iconFor(String name) {
            return icons.computeIfAbsent(name, key -> Icon.bundled(key, ICON_SIZE));
        }

        /// Rebuilds [#matching] when the query has changed, and not otherwise.
        private void refilter(String query) {
            if (query.equals(filteredFor)) {
                return;
            }
            filteredFor = query;
            var needle = query.trim().toLowerCase(Locale.ROOT);
            if (needle.isEmpty()) {
                matching = all;
                return;
            }
            var found = new ArrayList<String>(64);
            for (var name : all) {
                if (name.contains(needle)) {
                    found.add(name);
                }
            }
            matching = List.copyOf(found);
        }
    }
}
