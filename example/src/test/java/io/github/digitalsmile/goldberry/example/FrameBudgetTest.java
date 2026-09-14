package io.github.digitalsmile.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.example.ui.AppMenu;
import io.github.digitalsmile.goldberry.example.ui.Screen;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.text.font.Font;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Icons;
import io.github.digitalsmile.goldberry.widgets.Widgets;

/// What a frame of the real application costs, stage by stage and resolution by
/// resolution — and a ceiling under each, so a regression fails the build
/// (ADR-0147).
///
/// ## Why this exists
///
/// The showcase spent a month painting at 10–15 ms with nothing moving, because
/// the style cache had stopped hitting the day `scroll` shipped (ADR-0142). Every
/// test passed throughout. `FrameBenchmark` in `:widgets` measured the engine on
/// a synthetic 15-node tree and reported 0.6 ms, which was true and told nobody
/// anything: the defect only appears in a tree with a `scroll` in it, which is to
/// say in a real application.
///
/// ## What it asserts, and what it deliberately does not
///
/// **Ceilings, not comparisons.** A test that compared against a stored number
/// would fail on a slower machine and pass on a faster one having regressed.
/// These budgets are set at roughly ten times the measured cost, which is useless
/// against a 20% drift and exactly right against what actually happens: the bug
/// this exists for was **34×**.
///
/// **Two structural claims that hold on any machine**, and they are the sharper
/// half:
///
/// - **Style and build do not scale with resolution.** The cascade runs per
///   element, and a 4K window has the same elements as an 800×600 one. A style
///   cost that grew with the pixel count would be a cache keyed on something it
///   should not be — which is one letter away from the defect that prompted all
///   this.
/// - **A settled frame re-resolves nothing.** Rendering an unchanged tree twice
///   costs what the second one costs, and that number is a small multiple of the
///   box-building alone. This is what the 10 ms failure would have tripped.
///
/// Run `./gradlew :example:test --tests '*FrameBudgetTest*' -i` to read the
/// table; it is printed whether or not the assertions hold.
class FrameBudgetTest {

    /// Resolutions worth having a number for: a small laptop, a common desktop,
    /// 1080p, both meanings of 2K, QHD, and 4K at 2x. The last is the one that
    /// separates a per-pixel cost from a per-element one — 17 times the pixels of
    /// the first row, and the same elements.
    private record Resolution(String name, int width, int height, float scale) {

        long pixels() {
            return (long) width * height;
        }
    }

    private static final List<Resolution> RESOLUTIONS = List.of(
            new Resolution("800x600", 800, 600, 1.0f),
            new Resolution("1280x800", 1280, 800, 1.0f),
            new Resolution("1920x1080 FHD", 1920, 1080, 1.0f),
            // Both of the things "2K" means, because they are 25% apart and a
            // table that picked one would be answering somebody else's question:
            // DCI 2K is 2048x1080 and the monitor aisle's 2K is 2560x1440.
            new Resolution("2048x1080 2K", 2048, 1080, 1.0f),
            new Resolution("2560x1440 QHD", 2560, 1440, 1.0f),
            new Resolution("3840x2160 @2x", 3840, 2160, 2.0f));

    // --- the ceilings -------------------------------------------------------
    //
    // Every one is roughly ten times what this machine measures, and every one is
    // written next to the number it is ten times of, so the next person can see
    // whether a failure is a regression or a slower runner.

    /// Widget rebuilds. Measured **0.000 ms** on a settled tree, at every
    /// resolution: no `setState` arrived, so nothing is rebuilt at all. A number
    /// here at all means something high in the tree is dirtying itself per frame.
    private static final double BUILD_BUDGET_MS = 1.0;

    /// The cascade and the box tree. Measured **0.15 ms** for the Basic screen,
    /// flat across every resolution because it runs per element.
    ///
    /// The defect this whole class exists for showed up here as **10 ms**
    /// (ADR-0142), so a budget of 1 ms is 20× the measurement and 160× under the
    /// failure. There is no useful middle.
    private static final double STYLE_BUDGET_MS = 1.0;

    /// Yoga over the retained render tree. Measured **0.005 ms** settled — a
    /// frame where nothing changed re-lays out nothing (ADR-0069).
    private static final double LAYOUT_BUDGET_MS = 1.0;

    /// Blend2D, whole frame, no damage, **one thread**. Measured 3.1 ms at 800×600
    /// (6.5 ms/Mpx) and 12.6 ms at 4K@2× (1.5 ms/Mpx) — the small end costs more
    /// per pixel, because a frame has a fixed cost that a small one cannot spread.
    ///
    /// **These numbers are four times what this constant used to be written
    /// against, and nothing regressed.** The old ones were measured on a screen
    /// that did not exist: every `measure` call named `"controls"`, which stopped
    /// being a gallery screen at ADR-0222, so `pickScreen` set a property no tab
    /// matched and the budgets were compared against a window with **nothing
    /// selected** (ADR-0299). Measuring the Basic screen instead is measuring a
    /// window with cards, charts and text in it.
    ///
    /// So the budget is per megapixel with a floor, and the floor is what covers
    /// the fixed cost a small frame cannot spread. Both are set roughly four times
    /// the measurement rather than ten: this file's own doctrine is that the defect
    /// worth catching is a **34×**, and a ceiling that trips when a CI runner is
    /// busy is a ceiling somebody will delete.
    private static final double RASTER_BUDGET_MS_PER_MEGAPIXEL = 8.0;

    /// The smallest raster budget, whatever the pixel count. See above.
    private static final double RASTER_BUDGET_FLOOR_MS = 12.0;

    /// The screen the budgets are measured against: a wall of cards, which is what
    /// most of this application is.
    private static final String WALL = "basic";

    /// And the one that is a **document** — one `text` widget per word, which is a
    /// different shape of tree and the one that found ADR-0299.
    private static final String DOCUMENT = "markdown";

    /// And the sheet of **1544 icons**, which is the biggest *model* in the
    /// application and — since [ADR-0316] — no longer the biggest tree.
    private static final String SHEET = "icons";

    /// What a settled frame of the whole sheet is allowed.
    ///
    /// **Two milliseconds, where it was eight.** The sheet was an un-virtualized
    /// masonry of 4709 elements until [ADR-0316] and the style pass is
    /// O(elements); it is a virtualized `list` now and builds the rows a reader
    /// can see, which is 711 elements and **0.72 ms** measured.
    ///
    /// Not the wall's 1 ms, and the difference is honest rather than a
    /// concession: a viewport full of icon tiles is 711 elements where a wall of
    /// cards is 267, the cascade runs per element, and a budget that sat 40%
    /// over the measurement would fail whenever the machine was busy. Three times
    /// the measurement, which is this file's own doctrine — the defect worth
    /// catching is a 34×.
    ///
    /// The raster is on the wall's footing exactly, and has been since
    /// [ADR-0313].
    private static final double SHEET_STYLE_BUDGET_MS = 2.0;

    /// And its layout, on the same argument: **0.20 ms** measured over 711
    /// elements, against a wall's 0.03 over 267.
    private static final double SHEET_LAYOUT_BUDGET_MS = 2.0;

    private Showcase showcase;
    private ShowcaseModel model;
    private ShowcaseModel.Actions actions;
    private Icon palette;
    private Icon plus;
    private Font font;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        showcase = new Showcase();
        model = modelOf(ShowcaseModel.class);
        actions = modelOf(ShowcaseModel.Actions.class);
        palette = Icon.bundled("palette", 16);
        plus = Icon.bundled("plus", 16);
        font = Font.bundled(BundledFont.UI, 13);
    }

    @AfterEach
    void tearDown() {
        for (var closeable : new AutoCloseable[] {palette, plus, font}) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    private <T> T modelOf(Class<T> type) {
        return showcase.models().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow();
    }

    /// One screen's tree, wired to the application's own models.
    ///
    /// **The name is checked against the gallery**, and that is not defensive
    /// programming — it is this file's own lesson applied to itself. Every
    /// measurement here named `"controls"`, which was a screen until the gallery
    /// was reorganised into questions rather than widget families (ADR-0222); after
    /// that `pickScreen` set a property no tab matched, so the budgets were
    /// measured against a window with **no screen selected at all** and reported
    /// numbers nobody could have used. A benchmark measuring the wrong tree is the
    /// exact failure the class comment describes, and it had it (ADR-0299).
    private ElementTree treeFor(String screen) {
        if (!Screen.GALLERY.contains(screen)) {
            throw new IllegalArgumentException("no screen is called \"" + screen
                    + "\"; measuring one would measure an empty gallery." + " The gallery is " + Screen.GALLERY);
        }
        actions.pickScreen(screen);
        var inflater = Widgets.inflater(
                Icons.strict().bind("palette", palette).bind("plus", plus),
                showcase.models().toArray());
        return new ElementTree(new Screen(
                model,
                actions,
                inflater,
                plus,
                () -> {},
                new AppMenu(actions, new AppMenu.Handlers(() -> {}, () -> {}, () -> {}, () -> {}), plus)));
    }

    private WidgetRenderer rendererFor() {
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK, model.density()));
        sheets.add(Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css"));
        return new WidgetRenderer(sheets, font).clock(io.github.digitalsmile.goldberry.motion.Clock.virtual());
    }

    /// The median of `runs` timings, in milliseconds.
    ///
    /// The median and not the mean, for the reason every benchmark in this
    /// repository uses one: a single GC pause in a hundred runs moves a mean and
    /// does not move a median, and a budget compared against a mean would be a
    /// budget that fails on somebody else's garbage.
    private static double medianMillis(int runs, Runnable work) {
        var samples = new long[runs];
        for (var i = 0; i < runs; i++) {
            var began = System.nanoTime();
            work.run();
            samples[i] = System.nanoTime() - began;
        }
        Arrays.sort(samples);
        return samples[runs / 2] / 1_000_000.0;
    }

    /// What one settled frame of `screen` costs at `resolution`, stage by stage.
    private record Cost(double build, double style, double layout, double raster) {}

    private Cost measure(String screen, Resolution resolution) {
        // **Zero workers.** A threaded Blend2D context queues its work and only
        // blocks when the frame ends, so a timing loop around `paint` on one of
        // those measures *submitting* a frame -- which is how the first run of
        // this test reported a 4K raster as cheaper than an 800x600 one.
        // `FrameBenchmark` pins it for the same reason.
        var target = TestFrames.of(resolution.width(), resolution.height(), resolution.scale(), 0);
        var tree = treeFor(screen);
        var renderer = rendererFor();
        try (var render = RenderTree.create()) {
            // Settle: the first frames build the element tree, resolve every
            // style and lay everything out. A budget measured over those would be
            // a budget for start-up.
            for (var i = 0; i < 10; i++) {
                tree.flush();
                render.update(target.frame(), renderer.render(tree));
                render.paint(target.frame());
            }

            var build = medianMillis(100, tree::flush);
            var style = medianMillis(100, () -> renderer.render(tree));
            var boxes = renderer.render(tree);
            var layout = medianMillis(100, () -> render.update(target.frame(), boxes));
            // **The whole frame, no damage.** A settled tree damages nothing, so
            // `paint(frame, damage)` would measure the empty case and report a
            // rasterizer that costs nothing at 4K. The number worth a ceiling is
            // the one a resize pays.
            var raster = medianMillis(resolution.pixels() > 4_000_000 ? 20 : 60, () -> render.paint(target.frame()));
            return new Cost(build, style, layout, raster);
        }
    }

    /// A throwaway sweep, so the numbers below are not the JIT warming up.
    ///
    /// Without it the first resolution measured is three times the cost of the
    /// last one whatever order they are in, which makes every ratio in this class
    /// a measurement of C2 rather than of the toolkit.
    private void warmUp() {
        measure(WALL, RESOLUTIONS.getFirst());
        measure(WALL, RESOLUTIONS.getLast());
    }

    @Test
    @DisplayName("every stage of a real frame is inside its budget, at every resolution")
    void withinBudget() {
        warmUp();
        var failures = new ArrayList<String>();
        System.out.printf("%n  %-18s %8s %8s %8s %8s%n", "resolution", "build", "style", "layout", "raster");
        for (var resolution : RESOLUTIONS) {
            var cost = measure(WALL, resolution);
            System.out.printf(
                    "  %-18s %7.3f %7.3f %7.3f %7.3f  (ms, median)%n",
                    resolution.name(), cost.build(), cost.style(), cost.layout(), cost.raster());

            var rasterBudget = Math.max(
                    RASTER_BUDGET_FLOOR_MS, RASTER_BUDGET_MS_PER_MEGAPIXEL * resolution.pixels() / 1_000_000.0);
            check(failures, resolution, "build", cost.build(), BUILD_BUDGET_MS);
            check(failures, resolution, "style", cost.style(), STYLE_BUDGET_MS);
            check(failures, resolution, "layout", cost.layout(), LAYOUT_BUDGET_MS);
            check(failures, resolution, "raster", cost.raster(), rasterBudget);
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    private static void check(List<String> into, Resolution resolution, String stage, double measured, double budget) {
        if (measured > budget) {
            into.add(String.format(
                    "%s at %s took %.3f ms, over its %.3f ms budget."
                            + " Either something regressed or this machine is slower than the"
                            + " one the budget was written on -- the table above says which.",
                    stage, resolution.name(), measured, budget));
        }
    }

    /// **The claim that holds on any machine.** The cascade runs per element and
    /// a 4K window has the same elements as an 800×600 one, so style and build
    /// must not follow the pixel count. A style cost that did would be a cache
    /// keyed on something it has no business being keyed on — which is one letter
    /// away from ADR-0142's defect.
    ///
    /// Three times rather than "equal", because a bigger frame does change the
    /// available width, so a paragraph may wrap differently and a few measure
    /// callbacks may do more work. Three is loose enough for that and nowhere
    /// near the 26× the pixel count moves by.
    @Test
    @DisplayName("style and build do not grow with the pixel count; raster does")
    void stagesDoNotScaleWithPixels() {
        warmUp();
        var small = measure(WALL, RESOLUTIONS.getFirst());
        var large = measure(WALL, RESOLUTIONS.getLast());
        var pixelRatio =
                (double) RESOLUTIONS.getLast().pixels() / RESOLUTIONS.getFirst().pixels();

        System.out.printf(
                "%n  pixels x%.1f -> style x%.2f, layout x%.2f, raster x%.2f%n",
                pixelRatio,
                large.style() / small.style(),
                large.layout() / small.layout(),
                large.raster() / small.raster());

        assertTrue(
                large.style() <= Math.max(small.style() * 3, STYLE_BUDGET_MS),
                () -> String.format(
                        "style went from %.3f ms at 800x600 to %.3f ms at 4K, and the cascade"
                                + " runs per element -- so something is keyed on the frame",
                        small.style(), large.style()));
        assertTrue(
                large.build() <= Math.max(small.build() * 3, BUILD_BUDGET_MS),
                () -> String.format(
                        "build went from %.3f ms to %.3f ms with the resolution", small.build(), large.build()));
    }

    /// **A settled frame re-resolves nothing** — the property ADR-0142 restored,
    /// asserted as a ratio so it holds on any machine.
    ///
    /// The first render of a screen resolves every element's style; the second
    /// should reuse all of them and cost what building the boxes costs. Before
    /// ADR-0142 the two were the same number, because the cache never hit —
    /// which is exactly what this ratio catches and what no ceiling would have.
    @Test
    @DisplayName("a second render of an unchanged tree is far cheaper than the first")
    void settledFrameIsCheap() {
        var target = TestFrames.of(1280, 800, 1.0f);
        var tree = treeFor(WALL);
        var renderer = rendererFor();
        try (var render = RenderTree.create()) {
            tree.flush();
            var cold = medianMillis(1, () -> renderer.render(tree));
            render.update(target.frame(), renderer.render(tree));
            var warm = medianMillis(200, () -> renderer.render(tree));

            System.out.printf("%n  first render %.3f ms, settled render %.3f ms (x%.1f)%n", cold, warm, cold / warm);

            // Forty, and the number is chosen against both outcomes rather than
            // picked: with the cache working this ratio is 450-520x, and with
            // ADR-0142's defect reintroduced it is 11x. Anywhere in between is a
            // threshold; 40 leaves an order of magnitude of headroom under the
            // good case and nearly four times over the bad one.
            assertTrue(
                    warm * 40 < cold,
                    () -> String.format(
                            "a settled render cost %.3f ms against a cold one's %.3f ms,"
                                    + " which is not the two orders of magnitude a working style cache"
                                    + " gives. They come within 11x of each other when it never hits,"
                                    + " which is what ADR-0142 was about",
                            warm, cold));
        }
    }

    /// **A settled document shapes nothing**, which is a count rather than a
    /// duration and therefore true on every machine.
    ///
    /// The defect (ADR-0299): `markdown-view` and `html-view` build one `text`
    /// widget per word, so a page asks the paragraph cache for ~600 distinct
    /// strings a frame against a cache that held 256 — and least-recently-used
    /// eviction then guarantees a **zero** hit rate rather than a lower one, because
    /// each lookup evicts the entry the walk is about to reach. It cost 287 shapes
    /// and 4 ms on a frame where nothing had changed, and every timing test in this
    /// file passed throughout, because they all measured a wall of cards.
    ///
    /// So this measures the document screen, and it asserts the invariant directly:
    /// render an unchanged tree twice, and the second render shapes no text.
    @Test
    @DisplayName("a settled document re-shapes no text at all, however many words it has")
    void aSettledDocumentShapesNothing() {
        var target = TestFrames.of(1280, 900, 1.0f);
        var tree = treeFor(DOCUMENT);
        var renderer = rendererFor();
        try (var render = RenderTree.create()) {
            // Two frames to settle: the first builds the tree, the second is the one
            // whose working set the cache has now been sized to hold.
            for (var i = 0; i < 2; i++) {
                tree.flush();
                render.update(target.frame(), renderer.render(tree));
            }
            var cache = renderer.paragraphs();
            assertNotNull(cache, "a render has happened, so there is a cache");
            var before = cache.misses();

            renderer.render(tree);

            // The document's own frame cost, printed rather than asserted: the budgets
            // above are the wall's, and what is worth watching here is that a document
            // stays in the same order of magnitude as one. It is also where a
            // regression in the selection geometry would show up — every word reports
            // where it landed, once a frame (ADR-0301).
            var style = medianMillis(50, () -> renderer.render(tree));
            var layout = medianMillis(50, () -> render.update(target.frame(), renderer.render(tree)));
            System.out.printf(
                    "%n  document: %d paragraphs in a frame, cache holds %d, %d shaped by a settled frame"
                            + "%n  document: style %.3f ms, layout %.3f ms%n",
                    cache.highWaterMark(), cache.capacity(), cache.misses() - before, style, layout);
            assertEquals(
                    before,
                    cache.misses(),
                    () -> "a frame that changed nothing shaped " + (cache.misses() - before)
                            + " paragraphs; the cache holds " + cache.capacity() + " and the frame asks for "
                            + cache.highWaterMark() + " (ADR-0299)");
            assertTrue(
                    cache.capacity() >= cache.highWaterMark(),
                    "a cache smaller than one frame's working set misses every lookup on the excess");
        }
    }

    /// A settled frame of the **whole** icon sheet: 1544 icons, and a tree the
    /// size of any other screen's ([ADR-0316]).
    ///
    /// This screen had a budget of its own for as long as it was an
    /// un-virtualized `masonry` — 4709 elements, 8 ms of style and 18 ms of
    /// raster, asserted against numbers nothing else in the gallery was allowed
    /// because it did not meet the wall's and pretending otherwise would have
    /// been a test that fails or a test that checks nothing.
    ///
    /// It is an ordinary screen's frame now, and that is the assertion — 711
    /// elements against 4709, and a raster on the wall's own budget. A grid of
    /// equal-height
    /// tiles is a list of equal-height rows, a list builds the rows a reader can
    /// see, and the painter skips what the viewport clips ([ADR-0313]) — so the
    /// screen with the biggest *model* in the application has an ordinary
    /// screen's *tree* and an ordinary screen's frame.
    ///
    /// **The element count is asserted too**, and it is the sharper half: the
    /// budgets are wall-clock and this is a ratio that holds on any machine. A
    /// sheet that stopped virtualizing would still pass a timing test on a fast
    /// enough runner.
    @Test
    @DisplayName("the whole icon sheet costs what any other screen costs")
    void theWholeIconSheetCosts() {
        warmUp();
        var cost = measureSheet("");

        System.out.printf(
                "%n  icons (all 1544): %d elements, opened in %.0f ms"
                        + "%n  icons (all 1544): build %.3f ms, style %.3f ms, layout %.3f ms,"
                        + " raster %.3f ms  (settled, median)%n",
                cost.elements(), cost.opened(), cost.build(), cost.style(), cost.layout(), cost.raster());

        assertTrue(
                cost.elements() < 1544,
                "the sheet built " + cost.elements() + " elements for 1544 icons, which is not a window onto"
                        + " the model — it has stopped virtualizing ([ADR-0316])");
        assertTrue(
                cost.style() < SHEET_STYLE_BUDGET_MS,
                "a settled frame of the whole sheet styles in " + cost.style() + " ms, over the "
                        + SHEET_STYLE_BUDGET_MS + " ms this screen is allowed. The style pass is O(elements)"
                        + " and there are " + cost.elements() + " of them, so this is a count that grew.");
        assertTrue(
                cost.layout() < SHEET_LAYOUT_BUDGET_MS,
                "a settled frame of the whole sheet lays out in " + cost.layout() + " ms, over "
                        + SHEET_LAYOUT_BUDGET_MS);

        // The rasterizer on the ordinary budget as well, because a viewport costs
        // what is *visible* ([ADR-0313]). This measured 18.0 ms before the culler.
        var pixels = 1280L * 900;
        var rasterBudget = Math.max(RASTER_BUDGET_FLOOR_MS, RASTER_BUDGET_MS_PER_MEGAPIXEL * pixels / 1_000_000.0);
        assertTrue(
                cost.raster() < rasterBudget,
                "a settled frame of the whole sheet rasterizes in " + cost.raster() + " ms, over the "
                        + rasterBudget + " ms any screen of this size is allowed. The painter is supposed"
                        + " to skip the tiles that are off screen, and has stopped.");
    }

    /// **And searching is a convenience again**, which is the sentence this
    /// screen's documentation used to have to avoid.
    ///
    /// While the sheet was a masonry, the search field was the performance story:
    /// two letters took it from 4709 elements to 1085 and the style pass with
    /// them, and that was the argument that made an un-virtualized wall
    /// defensible. It is no longer true and must not be asserted — a virtualized
    /// list builds its **window**, so a filtered sheet and a whole one are the
    /// same tree and the same frame.
    ///
    /// So what is asserted is the property that replaced it: filtering changes
    /// the model by a factor of ten and changes the **cost by nothing**. A sheet
    /// that had quietly stopped virtualizing would fail here rather than merely
    /// get slower ([ADR-0316]).
    @Test
    @DisplayName("and typing two letters changes the model, not the frame")
    void aFilteredSheetCostsTheSame() {
        warmUp();
        var whole = measureSheet("");
        var filtered = measureSheet("ar");

        System.out.printf(
                "%n  icons (\"ar\"): %d elements, build %.3f ms, style %.3f ms, layout %.3f ms," + " raster %.3f ms%n",
                filtered.elements(), filtered.build(), filtered.style(), filtered.layout(), filtered.raster());

        // Within a quarter, not equal: the two sheets hold different *rows*, and
        // a row of long names wraps to two caption lines where a row of short
        // ones does not.
        assertTrue(
                filtered.elements() * 4 > whole.elements() * 3,
                "a filtered sheet built " + filtered.elements() + " elements against the whole sheet's "
                        + whole.elements() + "; both are windows on the same viewport and should be"
                        + " about the same size");
        assertTrue(
                filtered.style() < SHEET_STYLE_BUDGET_MS,
                "a filtered sheet styles in " + filtered.style() + " ms, over the " + SHEET_STYLE_BUDGET_MS
                        + " ms this screen is allowed");
    }

    /// What a settled frame of the icon sheet costs, with `query` typed into it.
    private SheetCost measureSheet(String query) {
        var target = TestFrames.of(1280, 900, 1.0f, 0);
        actions.setIconQuery(query);
        var tree = treeFor(SHEET);
        var renderer = rendererFor();
        try (var render = RenderTree.create()) {
            var opening = System.nanoTime();
            tree.flush();
            render.update(target.frame(), renderer.render(tree));
            var opened = (System.nanoTime() - opening) / 1_000_000.0;

            // The rest of the settle: `Measured` reports the sheet's width, the
            // column count follows, and the second layout is what every later
            // frame looks like.
            for (var i = 0; i < 10; i++) {
                tree.flush();
                render.update(target.frame(), renderer.render(tree));
            }

            var boxes = renderer.render(tree);
            return new SheetCost(
                    count(tree.root()),
                    opened,
                    medianMillis(50, tree::flush),
                    medianMillis(50, () -> renderer.render(tree)),
                    medianMillis(50, () -> render.update(target.frame(), boxes)),
                    medianMillis(30, () -> render.paint(target.frame())));
        } finally {
            actions.setIconQuery("");
        }
    }

    private record SheetCost(int elements, double opened, double build, double style, double layout, double raster) {}

    private static int count(io.github.digitalsmile.goldberry.widget.Element element) {
        var total = 1;
        for (var child : element.children()) {
            total += count(child);
        }
        return total;
    }
}
