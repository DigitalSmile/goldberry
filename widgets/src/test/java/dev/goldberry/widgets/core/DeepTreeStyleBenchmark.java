package dev.goldberry.widgets.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.select.Selector;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.controls.TestFont;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.panel.Panel;
import dev.goldberry.widgets.text.Text;

/// What a **deep** tree's styles cost on the frames the style cache cannot
/// help: the first one, and the one after a middle ancestor's state reaches
/// every node under it.
///
/// The measurement taken before touching `customPropertiesFor`, and kept so
/// the next person can take it again (ADR-0502). That method walks to the root
/// at every node, so its cost is a product of depth and something — and
/// whether the something is a cascade per ancestor, as TODO.md feared, or a
/// cache probe per ancestor, as ADR-0152 left it, is the difference between a
/// first frame that grows with the square of the nesting and one that does
/// not. It was the probe; what the measurement found instead was a copy of the
/// root's custom properties at every node, which had nothing to do with depth.
///
/// The catalog's own sheets and the Nord theme, so the rule count and the
/// hundred-odd `--gb-*` properties at the root are the ones an application
/// actually ships with. The one sheet added is the hover rule that makes a
/// middle ancestor's state reach its subtree.
///
/// **Tagged `benchmark`, so `check` never runs it.** Nothing here asserts a
/// timing. Run with `./gradlew :widgets:benchmark --tests '*DeepTreeStyleBenchmark*'`.
@Tag("benchmark")
class DeepTreeStyleBenchmark {

    private static final int WARMUP = 30;
    private static final int RUNS = 200;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// `levels` of a panel holding a column holding a label, a button and the
    /// next level — two elements of nesting per level, four elements in all.
    private static Widget nested(int levels) {
        Widget inner = new Row(new Text("leaf"), new Button("leaf"));
        for (var level = levels; level > 0; level--) {
            var classes = level == levels / 2 ? Attributes.NONE.classes("mid") : Attributes.NONE;
            inner = new Panel(
                    List.of(new Column(new Text("level " + level), new Button("act " + level), inner)), classes);
        }
        return inner;
    }

    private static List<Stylesheet> sheets() {
        return Stream.concat(
                        Controls.stylesheets(Theme.NORD_DARK).stream(),
                        Stream.of(Stylesheet.parse(
                                CascadeLayer.APPLICATION, "panel.mid:hover text { color: var(--gb-accent) }")))
                .toList();
    }

    @Test
    @DisplayName("a deep tree's first frame, and a middle ancestor's hover, split by what the cascade spends")
    void deepTree() {
        for (var depth : new int[] {50, 100, 200}) {
            measure(depth);
        }
    }

    private void measure(int depth) {
        var tree = new ElementTree(nested(depth / 2));
        var all = new ArrayList<Element>();
        collect(tree.root(), all);
        // What the renderer resolves: a composition node passes its ancestor's
        // style through and is only ever asked for custom properties.
        var styled = all.stream()
                .filter(e -> e.widget() instanceof Styled || e.widget() instanceof Paints)
                .toList();
        System.out.printf(
                "depth %d: %d elements, %d styled, deepest at %d%n", depth, all.size(), styled.size(), deepest(all));

        // Two of everything, alternated, so every pass is a first frame: each
        // element caches against one resolver by identity, and the other one
        // misses it.
        var resolvers = new StyleResolver[] {new StyleResolver(sheets()), new StyleResolver(sheets())};
        var flip = new int[1];
        report("first frame: resolve() every styled node", () -> {
            var resolver = resolvers[flip[0]++ & 1];
            var total = 0L;
            for (var element : styled) {
                total += resolver.resolve(element).size();
            }
            return total;
        });

        // The half of that pass that is custom properties: a cascade per node
        // plus the walk to the root, without the substitution.
        report("first frame: customPropertiesFor() alone", () -> {
            var resolver = resolvers[flip[0]++ & 1];
            var total = 0L;
            for (var element : styled) {
                total += resolver.customPropertiesFor(element).size();
            }
            return total;
        });

        // The walk and nothing else: every level is a cache hit, so what is left
        // is the probe per ancestor that the recursion makes on the way up.
        var warm = resolvers[0];
        for (var element : styled) {
            warm.customPropertiesFor(element);
        }
        report("warm: customPropertiesFor() (the walk alone)", () -> {
            var total = 0L;
            for (var element : styled) {
                total += warm.customPropertiesFor(element).size();
            }
            return total;
        });

        // The real renderer, for the share: boxes and ComputedStyle.of included.
        var renderers = new WidgetRenderer[] {
            new WidgetRenderer(sheets(), TestFont.get()), new WidgetRenderer(sheets(), TestFont.get())
        };
        report(
                "first frame: render()",
                () -> renderers[flip[0]++ & 1].render(tree).children().size());

        // An invalidated subtree: hover on the middle panel reaches every text
        // below it, so every node under it drops its cache and re-resolves.
        var renderer = renderers[0];
        renderer.render(tree);
        var middle = all.stream()
                .filter(e -> e.classes().contains("mid"))
                .findFirst()
                .orElseThrow();
        var hovered = new boolean[1];
        report("hover on the middle panel: render()", () -> {
            hovered[0] = !hovered[0];
            middle.setPseudoClass(Selector.PseudoClass.HOVER, hovered[0]);
            return renderer.render(tree).children().size();
        });
        report("no change: render()", () -> renderer.render(tree).children().size());
    }

    private static void collect(Element element, List<Element> into) {
        into.add(element);
        for (var child : element.children()) {
            collect(child, into);
        }
    }

    private static int deepest(List<Element> elements) {
        var deepest = 0;
        for (var element : elements) {
            var depth = 0;
            for (var at = element.parent(); at != null; at = at.parent()) {
                depth++;
            }
            deepest = Math.max(deepest, depth);
        }
        return deepest;
    }

    // --- harness --------------------------------------------------------------
    //
    // FrameBenchmark's, median and mean both, for the reason given there.

    private static void report(String what, LongSupplier work) {
        var sink = 0L;
        for (var i = 0; i < WARMUP; i++) {
            sink += work.getAsLong();
        }
        var samples = new long[RUNS];
        for (var i = 0; i < RUNS; i++) {
            var start = System.nanoTime();
            sink += work.getAsLong();
            samples[i] = System.nanoTime() - start;
        }
        var total = 0L;
        for (var sample : samples) {
            total += sample;
        }
        Arrays.sort(samples);
        System.out.printf(
                "  %-46s median %9.1f us   mean %9.1f us   p95 %9.1f us   (n=%d, sink=%d)%n",
                what,
                samples[samples.length / 2] / 1000.0,
                total / (double) RUNS / 1000.0,
                samples[(int) (samples.length * 0.95)] / 1000.0,
                RUNS,
                sink);
    }
}
