package io.github.digitalsmile.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.example.ui.Screen;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.layout.RenderTree;
import io.github.digitalsmile.goldberry.text.Font;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Icons;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a frame of the real application costs, stage by stage and resolution by
/// resolution — and a ceiling under each, so a regression fails the build
/// ([ADR-0147](../../../../../../../book/src/adr/0147-a-frame-has-a-budget-and-the-build-checks-it.md)).
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
    /// 1080p and 4K. The last is the one that separates a per-pixel cost from a
    /// per-element one.
    private record Resolution(String name, int width, int height, float scale) {

        long pixels() {
            return (long) width * height;
        }
    }

    private static final List<Resolution> RESOLUTIONS = List.of(
            new Resolution("800x600", 800, 600, 1.0f),
            new Resolution("1280x800", 1280, 800, 1.0f),
            new Resolution("1920x1080", 1920, 1080, 1.0f),
            new Resolution("2560x1440", 2560, 1440, 1.0f),
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

    /// The cascade and the box tree. Measured **0.03–0.06 ms** for the Controls
    /// screen, flat across every resolution because it runs per element.
    ///
    /// The defect this whole class exists for showed up here as **10 ms**
    /// (ADR-0142), so a budget of 1 ms is 20× the measurement and 160× under the
    /// failure. There is no useful middle.
    private static final double STYLE_BUDGET_MS = 1.0;

    /// Yoga over the retained render tree. Measured **0.005 ms** settled — a
    /// frame where nothing changed re-lays out nothing (ADR-0069).
    private static final double LAYOUT_BUDGET_MS = 1.0;

    /// Blend2D, whole frame, no damage, **one thread**. Measured 0.62 ms at
    /// 800×600 (1.3 ms/Mpx) and 6.9 ms at 4K (0.83 ms/Mpx) — the small end costs
    /// more per pixel, because a frame has a fixed cost that a small one cannot
    /// spread.
    ///
    /// So the budget is per megapixel with a floor, and the floor is what makes
    /// the 800×600 row six times its measurement rather than twice.
    private static final double RASTER_BUDGET_MS_PER_MEGAPIXEL = 8.0;

    /// The smallest raster budget, whatever the pixel count. See above.
    private static final double RASTER_BUDGET_FLOOR_MS = 3.0;

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
                .filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    /// One screen's tree, wired to the application's own models.
    private ElementTree treeFor(String screen) {
        actions.pickScreen(screen);
        var inflater = Widgets.inflater(
                Icons.strict().bind("palette", palette).bind("plus", plus),
                showcase.models().toArray());
        return new ElementTree(new Screen(model, actions, inflater, plus, () -> { }));
    }

    private WidgetRenderer rendererFor() {
        var sheets = new ArrayList<Stylesheet>(
                Controls.stylesheets(Theme.NORD_DARK, model.density()));
        sheets.add(Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css"));
        return new WidgetRenderer(sheets, font)
                .clock(io.github.digitalsmile.goldberry.motion.Clock.virtual());
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
    private record Cost(double build, double style, double layout, double raster) {
    }

    private Cost measure(String screen, Resolution resolution) {
        // **Zero workers.** A threaded Blend2D context queues its work and only
        // blocks when the frame ends, so a timing loop around `paint` on one of
        // those measures *submitting* a frame -- which is how the first run of
        // this test reported a 4K raster as cheaper than an 800x600 one.
        // `FrameBenchmark` pins it for the same reason.
        var target = TestFrames.of(resolution.width(), resolution.height(),
                resolution.scale(), 0);
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
            var raster = medianMillis(resolution.pixels() > 4_000_000 ? 20 : 60,
                    () -> render.paint(target.frame()));
            return new Cost(build, style, layout, raster);
        }
    }

    /// A throwaway sweep, so the numbers below are not the JIT warming up.
    ///
    /// Without it the first resolution measured is three times the cost of the
    /// last one whatever order they are in, which makes every ratio in this class
    /// a measurement of C2 rather than of the toolkit.
    private void warmUp() {
        measure("controls", RESOLUTIONS.getFirst());
        measure("controls", RESOLUTIONS.getLast());
    }

    @Test
    @DisplayName("every stage of a real frame is inside its budget, at every resolution")
    void withinBudget() {
        warmUp();
        var failures = new ArrayList<String>();
        System.out.printf("%n  %-16s %8s %8s %8s %8s%n",
                "resolution", "build", "style", "layout", "raster");
        for (var resolution : RESOLUTIONS) {
            var cost = measure("controls", resolution);
            System.out.printf("  %-16s %7.3f %7.3f %7.3f %7.3f  (ms, median)%n",
                    resolution.name(), cost.build(), cost.style(), cost.layout(), cost.raster());

            var rasterBudget = Math.max(RASTER_BUDGET_FLOOR_MS,
                    RASTER_BUDGET_MS_PER_MEGAPIXEL * resolution.pixels() / 1_000_000.0);
            check(failures, resolution, "build", cost.build(), BUILD_BUDGET_MS);
            check(failures, resolution, "style", cost.style(), STYLE_BUDGET_MS);
            check(failures, resolution, "layout", cost.layout(), LAYOUT_BUDGET_MS);
            check(failures, resolution, "raster", cost.raster(), rasterBudget);
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    private static void check(List<String> into, Resolution resolution, String stage,
            double measured, double budget) {
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
        var small = measure("controls", RESOLUTIONS.getFirst());
        var large = measure("controls", RESOLUTIONS.getLast());
        var pixelRatio = (double) RESOLUTIONS.getLast().pixels() / RESOLUTIONS.getFirst().pixels();

        System.out.printf("%n  pixels x%.1f -> style x%.2f, layout x%.2f, raster x%.2f%n",
                pixelRatio, large.style() / small.style(),
                large.layout() / small.layout(), large.raster() / small.raster());

        assertTrue(large.style() <= Math.max(small.style() * 3, STYLE_BUDGET_MS),
                () -> String.format(
                        "style went from %.3f ms at 800x600 to %.3f ms at 4K, and the cascade"
                                + " runs per element -- so something is keyed on the frame",
                        small.style(), large.style()));
        assertTrue(large.build() <= Math.max(small.build() * 3, BUILD_BUDGET_MS),
                () -> String.format("build went from %.3f ms to %.3f ms with the resolution",
                        small.build(), large.build()));
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
        var tree = treeFor("controls");
        var renderer = rendererFor();
        try (var render = RenderTree.create()) {
            tree.flush();
            var cold = medianMillis(1, () -> renderer.render(tree));
            render.update(target.frame(), renderer.render(tree));
            var warm = medianMillis(200, () -> renderer.render(tree));

            System.out.printf("%n  first render %.3f ms, settled render %.3f ms (x%.1f)%n",
                    cold, warm, cold / warm);

            // Forty, and the number is chosen against both outcomes rather than
            // picked: with the cache working this ratio is 450-520x, and with
            // ADR-0142's defect reintroduced it is 11x. Anywhere in between is a
            // threshold; 40 leaves an order of magnitude of headroom under the
            // good case and nearly four times over the bad one.
            assertTrue(warm * 40 < cold, () -> String.format(
                    "a settled render cost %.3f ms against a cold one's %.3f ms,"
                            + " which is not the two orders of magnitude a working style cache"
                            + " gives. They come within 11x of each other when it never hits,"
                            + " which is what ADR-0142 was about",
                    warm, cold));
        }
    }
}
