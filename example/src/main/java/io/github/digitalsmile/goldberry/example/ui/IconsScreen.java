package io.github.digitalsmile.goldberry.example.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Overlay;
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
import io.github.digitalsmile.goldberry.widgets.overlay.dialog.Dialogs;
import io.github.digitalsmile.goldberry.widgets.panel.list.ListView;
import io.github.digitalsmile.goldberry.widgets.panel.list.Selection;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The **Icons** screen: all 1544 of them, grouped by category, a row of chips
/// to choose one, a field to find one, and a dialog of any of them at five sizes.
///
/// ## Grouped by Lucide's own categories
///
/// Lucide files every icon under one or more of 43 categories — `arrows`,
/// `devices`, `food-beverage` — in the metadata beside each SVG, and the build
/// compiles that into a table ([Catalogs]). The sheet shows each category under
/// a heading, and a chip per category narrows it to one; **All** puts them back.
/// An icon in three categories is under three headings, which is what Lucide's
/// own site does and what a reader looking under "Arrows" for an arrow that is
/// also a navigation icon expects. The headings are rows of the same pitch as the
/// tiles, so the sheet is still one virtualized list ([CategorySheet]).
///
/// ## Pressing a tile opens its sizes
///
/// A tile is a [PressableTile]: a click, `Space` or `Enter` opens a dialog of
/// the icon at 16, 24, 32, 48 and 64 points ([Specimens]) — which is the
/// question a reader choosing an icon actually has, and one a sheet at a single
/// size cannot answer.
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
/// ## The sheet is a window on the model
///
/// This screen was a `masonry` of all 1544 tiles between [ADR-0309] and
/// [ADR-0316], and it is a **virtualized `list` of rows** now — a grid of
/// equal-height tiles is a list of equal-height rows, and a list builds the rows
/// a reader can see. `FrameBudgetTest` measures what that is worth rather than
/// leaving it to a paragraph like this one:
///
/// | | elements | opens in | style | layout | raster |
/// |---|---|---|---|---|---|
/// | the masonry | 4709 | 414 ms | 3.5 ms | 0.7 ms | 18.0 ms |
/// | the grid | 711 | 106 ms | 0.7 ms | 0.2 ms | 4.2 ms |
///
/// The reflow is unchanged and was never the masonry's: [#columnsFor] is the
/// whole layout rule and it reads a width this screen measures itself. What the
/// masonry was doing is the chunking, which is [IconsState#rows()].
///
/// **Searching is a convenience again**, which is a sentence this comment used to
/// have to avoid: while every tile was built, two letters took three quarters of
/// the frame back and that was the argument that made an un-virtualized wall
/// defensible. A virtualized list builds its window, so a filtered sheet and a
/// whole one are the same tree and the same frame.
///
/// The `scroll` is around the grid and not around the screen, so the field and
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

    /// A tile's height, and the sheet's row pitch with the gap added.
    ///
    /// Stated here for [#TILE_GAP]'s reason and one more: a **virtualized** list
    /// is told how tall a row is, because that is the one number it cannot find
    /// out — §8's subset resolves the height for the cascade and no widget can
    /// read back what it resolved ([ADR-0213]). `showcase.css` pins
    /// `#icon-wall list-row` to this number, and `ListRow` complains if the two
    /// ever disagree.
    static final double TILE_HEIGHT = 68;

    /// How far apart two rows of tiles sit, top to top.
    ///
    /// Public for [#columnsFor]'s reason: it is half of the layout rule, and
    /// `IconsScreenTest` is in the application's own package rather than in
    /// `…example.ui` — a virtualized sheet that is as tall as its **model** is
    /// the property the scrollbar rests on, and asserting it means multiplying
    /// this number by a row count.
    public static final double ROW_PITCH = TILE_HEIGHT + TILE_GAP;

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

    /// Every bundled icon in its categories — the sheet's groups before any
    /// chip or search narrows them.
    ///
    /// An icon the category table does not know — a table from another Lucide
    /// version, or a build that skipped the step — is filed under "other" rather
    /// than left off the sheet: every name `BundledAssets` has is on it somewhere.
    private static final class Groups {
        private static final List<CategorySheet.Group<String>> ALL = build();

        private static List<CategorySheet.Group<String>> build() {
            var table = Catalogs.iconCategories();
            var filed = new java.util.TreeMap<String, List<String>>();
            for (var name : BundledAssets.iconNames()) {
                filed.put(name, table.getOrDefault(name, List.of("other")));
            }
            return Catalogs.iconGroups(filed);
        }
    }

    /// How many rows the sheet has for `category` and `query` at `columns` —
    /// headings included.
    ///
    /// Static and public for [#columnsFor]'s reason: a virtualized sheet is as
    /// tall as its **model**, and `IconsScreenTest` asserts that by multiplying
    /// this by [#ROW_PITCH].
    ///
    /// @param category a category's name, or `""` for all of them
    public static int rowsFor(String category, String query, int columns) {
        return CategorySheet.rows(CategorySheet.narrow(Groups.ALL, category, matcher(query)), columns, name -> name)
                .size();
    }

    /// The category names, in the order their chips and headings appear.
    public static List<String> categories() {
        return Groups.ALL.stream().map(CategorySheet.Group::name).toList();
    }

    /// What a search keeps: names containing the query, lower-cased and trimmed.
    private static java.util.function.Predicate<String> matcher(String query) {
        var needle = query.trim().toLowerCase(Locale.ROOT);
        return needle.isEmpty() ? name -> true : name -> name.contains(needle);
    }

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

        /// How many icons there are, for the count — each counted once, though
        /// most are under several headings.
        private final int total = BundledAssets.iconNames().size();

        /// The chosen category, or [CategorySheet#ALL].
        ///
        /// The screen's own state rather than the model's: unlike the query, no
        /// field binds to it, and a chip row that reports to the screen that
        /// drew it is the whole of the wiring.
        private String category = CategorySheet.ALL;

        /// The query and category the groups were last narrowed for, and what
        /// came out.
        ///
        /// Banked because narrowing runs in `build` and `build` runs on every
        /// frame this screen is rebuilt for — which includes every keystroke
        /// *and* every unrelated `setState` in the window. Recomputing a stable
        /// answer is the cheapest kind of waste and also the easiest to leave in.
        private @Nullable String filteredFor;

        private @Nullable String filteredCategory;

        private List<CategorySheet.Group<String>> matching = List.of();

        /// The window, for the specimen dialog — read off the build context,
        /// because a dialog is put on a host and not in this tree.
        private @Nullable Host host;

        /// The open specimen dialog, or null.
        private @Nullable Overlay specimen;

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
            // Takes the dialog with the screen, so switching tabs does not leave
            // a modal over a sheet nobody is looking at — WebScreen's rule.
            closeSpecimen();
        }

        @Override
        public Widget build(BuildContext context) {
            host = context.host().orElse(null);
            var query = widget().model().iconQuery();
            refilter(query);

            return new Column(List.of(header(query), scrolledSheet()), Attributes.NONE.id("icons-screen"));
        }

        /// The title, the chips, the field and the count.
        ///
        /// The count is prose rather than a `badge`, because it is a sentence
        /// about a search — "42 of 1544" — and a badge is a number beside the
        /// thing it counts. It counts **icons**, each once, not tiles: an icon
        /// under three headings is one icon.
        private Widget header(String query) {
            var found = CategorySheet.distinct(matching);
            return new Column(
                    List.of(
                            new Text("Every bundled icon", Attributes.NONE.classes("screen-title")),
                            new Text(
                                    "Lucide's " + total + " icons in " + Groups.ALL.size() + " categories,"
                                            + " compiled into one path table at build time (ADR-0033). The name"
                                            + " under each is what a document writes in icon=\"…\"; press one to"
                                            + " see it at five sizes.",
                                    Attributes.NONE.classes("screen-note")),
                            CategorySheet.chips(Groups.ALL, category, this::choose, "icon-category"),
                            new Row(
                                    List.of(
                                            TextInput.of(
                                                            Models.observable(widget().model(), "app.icon-query"),
                                                            widget().actions()::setIconQuery)
                                                    .placeholder("Search 1544 icons — try \"arrow\"")
                                                    .id("icon-search"),
                                            new Text(
                                                    count(query, found),
                                                    Attributes.NONE
                                                            .id("icon-count")
                                                            .classes("caption"))),
                                    Attributes.NONE.id("icon-search-row"))),
                    Attributes.NONE.id("icons-header"));
        }

        /// The sheet: a **virtualized grid that follows the window**, in a
        /// viewport.
        ///
        /// ## A grid is a list of rows, and a list already virtualizes
        ///
        /// This was a `masonry` between [ADR-0309] and [ADR-0316], on an argument
        /// that was right about reflow and wrong about what reflow costs. A
        /// masonry is a count of equal-width columns, which is exactly a
        /// reflowing grid — and it cannot virtualize, because placing a card
        /// under the shortest column is a decision about **every** card. So all
        /// 1544 tiles were in the tree, and every frame paid 4709 elements'
        /// worth of box-building, layout and hit-test snapshot whether or not a
        /// reader could see them.
        ///
        /// The masonry was never needed. Its own record says so: *"a masonry of
        /// equal-height tiles is a reflowing grid in reading order"* — and a grid
        /// of equal-height rows is a **list**, which §10's `list` virtualizes on
        /// exactly the argument this screen needed ([ADR-0213]). The reflow is
        /// [#columns], which this screen has always computed itself from
        /// [Measured]; the masonry was only ever doing the chunking.
        ///
        /// So the names are chunked into rows of [#columns] here and the `list`
        /// builds the rows a reader can see. Reading order is left to right down
        /// the page, which is what it always was and is now true by construction
        /// rather than by an argument about which column is shortest.
        ///
        /// ## The last row is padded rather than stretched
        ///
        /// A tile is `flex-grow: 1` over a 152pt basis, so a full row divides the
        /// width evenly and has no ragged right edge — which is what the masonry's
        /// equal columns gave. A part-full last row would divide the *same* width
        /// between fewer tiles and draw them half again as wide, so it is padded
        /// out with empty cells that grow and draw nothing.
        ///
        /// ## The viewport is the sheet's, not the gallery's
        ///
        /// [Screen] does not wrap this screen in one, because §2.4 bans a scroller
        /// inside a scroller. So the `scroll` is here, around the grid and **not**
        /// around the header — the search field and the count stay put while the
        /// icons move under them, which is the whole point of a field that filters
        /// a long list. It is also what the `list` reads its window from: a
        /// virtualized list is told where it was painted and what clips it, and
        /// the clip is this viewport ([ADR-0119]).
        private Widget scrolledSheet() {
            if (matching.isEmpty()) {
                return new Text(
                        "No icon has that in its name. Lucide's names are English and hyphenated —"
                                + " \"chevron-right\", \"square-pen\", \"file-text\".",
                        Attributes.NONE.id("icons-empty").classes("screen-note"));
            }
            var grid = new ListView<>(
                            CategorySheet.rows(matching, columns, name -> name),
                            CategorySheet.SheetRow::id,
                            this::rowOf)
                    .selection(Selection.NONE)
                    .virtualized(ROW_PITCH)
                    .id("icon-wall");
            return new Scroll(
                    List.of(new IconSheet(grid, this::measured)),
                    ScrollAxis.VERTICAL,
                    Attributes.NONE.id("icon-viewport"));
        }

        /// "1544 icons", "200 in Arrows", or "42 of 1544" — what the sheet is
        /// showing, in the words a reader would use.
        private String count(String query, int found) {
            if (!query.isBlank()) {
                return found + " of " + total;
            }
            return category.equals(CategorySheet.ALL)
                    ? total + " icons"
                    : found + " in " + CategorySheet.label(category);
        }

        /// A heading, or a row of tiles padded to [#columns] so it divides the
        /// width the way a full row does.
        private Widget rowOf(CategorySheet.SheetRow<String> row) {
            return switch (row) {
                case CategorySheet.Heading<String> heading ->
                    new CategorySheet.SheetHeading(
                            CategorySheet.label(heading.group()),
                            heading.count(),
                            Attributes.NONE.classes("icon-heading"));
                case CategorySheet.Tiles<String> tiles -> {
                    var cells = new ArrayList<Widget>(columns);
                    for (var name : tiles.items()) {
                        // Keyed by name, so filtering and reflowing reconcile the
                        // tiles that survived rather than rebuilding the row
                        // (ADR-0004).
                        cells.add(new IconTile(name, iconFor(name), () -> open(name), Attributes.NONE.key(name)));
                    }
                    CategorySheet.pad(cells, columns);
                    yield new Row(cells, Attributes.NONE.classes("icon-row"));
                }
            };
        }

        /// A chip was pressed: show `chosen`, or every category for
        /// [CategorySheet#ALL].
        private void choose(String chosen) {
            if (!chosen.equals(category)) {
                setState(() -> category = chosen);
            }
        }

        /// Opens the specimen dialog for `name`, replacing one already open.
        ///
        /// Does nothing without a host — a tree built for a test with no window
        /// has nowhere to put a modal — which is the same answer `WebScreen`
        /// gives.
        private void open(String name) {
            if (host == null) {
                return;
            }
            closeSpecimen();
            var categories = Catalogs.iconCategories().getOrDefault(name, List.of("other"));
            specimen = Dialogs.show(host, Specimens.icon(name, categories, this::closeSpecimen));
        }

        private void closeSpecimen() {
            if (specimen != null) {
                specimen.remove();
                specimen = null;
            }
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

        /// Rebuilds [#matching] when the query or the category has changed, and
        /// not otherwise.
        private void refilter(String query) {
            if (query.equals(filteredFor) && category.equals(filteredCategory)) {
                return;
            }
            filteredFor = query;
            filteredCategory = category;
            matching = CategorySheet.narrow(Groups.ALL, category, matcher(query));
        }
    }
}
