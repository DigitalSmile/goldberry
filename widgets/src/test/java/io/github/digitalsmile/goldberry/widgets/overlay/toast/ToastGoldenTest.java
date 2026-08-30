package io.github.digitalsmile.goldberry.widgets.overlay.toast;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.Overlay;
import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.root.WindowRoot;
import io.github.digitalsmile.goldberry.widget.style.Corner;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.panel.Described;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// What a stack of toasts looks like, and one caught sliding in.
///
/// The picture carries the thing §2's numbers cannot: three toasts of **one
/// width** with ragged text in them read as a stack, and three sized to their
/// contents read as a pile. That is why 360 is a width here and not a maximum.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class ToastGoldenTest {

    private Clock.Virtual clock;
    private TestHost host;
    private ToastController toasts;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        clock = Clock.virtual();
        host = new TestHost();
        toasts = new ToastController();
    }

    /// The stack alone on a background — which is what an overlay layer gives
    /// it: a corner of a window, and nothing else in the box.
    private static final String SCENE = """
            /* A background so the picture is legible. The real stack has none —
               it floats over whatever the window is showing — so this is the
               test's scene rather than the widget's, and it is in the
               application layer where a scene belongs. */
            toaster { padding: 16px; background: var(--gb-bg) }
            """;

    /// The scene for the one picture taken in a **window** rather than of a
    /// column on its own — see [#reflowing]. The background is the window's, and
    /// the stack floats over it with nothing behind it, which is what an overlay
    /// layer really gives it.
    private static final String WINDOW_SCENE = """
            window-root { background: var(--gb-bg); padding: 16px }
            """;

    private WidgetRenderer rendererFor(Theme theme) {
        return rendererFor(theme, SCENE);
    }

    private WidgetRenderer rendererFor(Theme theme, String scene) {
        return new WidgetRenderer(
                        List.of(
                                Controls.baseStylesheet(),
                                theme.load(),
                                Stylesheet.parse(CascadeLayer.APPLICATION, scene)),
                        TestFont.get())
                .clock(clock);
    }

    private ElementTree threeToasts(Corner corner) {
        var tree = new ElementTree(new Toaster(toasts, corner), host);
        // Timeouts long enough that none of them goes while the picture is being
        // taken -- a golden of a stack should not depend on how many frames it
        // took to get there.
        toasts.show(new Toast("Draft saved.").timeout(Duration.ofMinutes(1)));
        toasts.show(new Toast("Two devices are signed in to this account, which is one" + " more than usual.")
                .timeout(Duration.ofMinutes(1)));
        toasts.show(new Toast("Message sent.").action("Undo", () -> {}).timeout(Duration.ofMinutes(1)));
        tree.flush();
        return tree;
    }

    private void paintSettled(String name, Theme theme, Corner corner) {
        var tree = threeToasts(corner);
        var renderer = rendererFor(theme);

        renderer.render(tree);
        clock.advance(300);
        var settled = renderer.render(tree);
        renderer.render(tree);
        assertFalse(renderer.isAnimating(), "a toast that arrived 300ms ago is still moving");

        GoldenImage.assertMatches(name, 400, 240, 1.0f, frame -> BoxPainter.paint(frame, settled));
    }

    @Test
    @DisplayName("three toasts in a corner, dark")
    void dark() {
        paintSettled("toast-dark", Theme.NORD_DARK, Corner.BOTTOM_END);
    }

    @Test
    @DisplayName("three toasts in a corner, light")
    void light() {
        paintSettled("toast-light", Theme.NORD_LIGHT, Corner.BOTTOM_END);
    }

    /// The newest is nearest the corner, which for the top of the window is the
    /// **top** of the column — the one thing the corner decides that is not
    /// where the stack sits.
    @Test
    @DisplayName("a stack at the top grows downwards, newest first")
    void topStart() {
        paintSettled("toast-top-start", Theme.NORD_DARK, Corner.TOP_START);
    }

    /// §3's "siblings reflow via `translate`", caught in the middle of it.
    ///
    /// **In a real window**, which every other picture in this file can do
    /// without and this one cannot. Which toasts move when one goes is decided by
    /// where the column is *pinned*: a `toaster` is a corner overlay, so it is
    /// anchored by whichever end is against the corner — the newest toast — and
    /// everything on the far side of the hole comes in to close it. A `toaster`
    /// laid out as an ordinary top-aligned box, which is what a scene like
    /// [#SCENE] gives it, is anchored at the other end and would photograph the
    /// opposite toast moving
    /// ([ADR-0178](../../../../../../../../../book/src/adr/0178-a-stack-closes-its-own-hole.md)).
    /// This is [io.github.digitalsmile.goldberry.widgets.overlay.hud.HudGoldenTest]'s
    /// finding in a second place: overlay placement is not assertable as a number.
    ///
    /// What the image is evidence of: the older toast is **between** two places,
    /// aligned with neither — which is what the jump it replaces cannot look like
    /// — and the newest one has not moved at all.
    @Test
    @DisplayName("a stack caught closing the hole a dismissed toast left")
    void reflowing() {
        var overlays = Property.<List<Overlay>>of(List.of());
        var tree =
                new ElementTree(new WindowRoot(new Text("The window the toasts are floating over."), overlays), host);
        overlays.set(List.of(Overlay.of(new Toaster(toasts, Corner.BOTTOM_END), Corner.BOTTOM_END)));
        // Mounted before anything is raised: a controller with no stack attached
        // drops what it is given, which is the right answer for a background job
        // finishing late and the wrong order for a test.
        tree.flush();

        // `Duration.ZERO` is the toast that never goes on its own, and here it
        // buys something a long timeout does not: **no timer at all**. The exit
        // below is fired by running every pending timer, and a stack holding
        // three stays would answer that by dismissing all of them.
        toasts.show(new Toast("Draft saved.").timeout(Duration.ZERO));
        // The middle one is the one that goes, because a hole in the middle is
        // the only one with a far side.
        toasts.show(new Toast("Two devices are signed in to this account, which is one" + " more than usual.")
                .action("Dismiss", () -> {})
                .timeout(Duration.ZERO));
        toasts.show(new Toast("Message sent.").timeout(Duration.ZERO));
        tree.flush();

        var renderer = rendererFor(Theme.NORD_DARK, WINDOW_SCENE);
        renderer.render(tree);
        clock.advance(300);
        renderer.render(tree);
        renderer.render(tree);
        assertFalse(renderer.isAnimating(), "the stack has not settled yet");
        measure(tree, renderer);

        Described.first(tree, Button.class).onPress().run();
        tree.flush();
        // Its exit finishes, which is the frame the survivors are asked to move.
        host.tickAll();
        tree.flush();

        // The **first** frame is what starts the travel, and the advance has to
        // come after it: a `Phase` is stamped by the frame that first draws it
        // rather than by the moment it was made, so advancing the clock before
        // this render would move nothing and photograph the toast exactly where
        // it already was.
        renderer.render(tree);
        assertTrue(renderer.isAnimating(), "nothing is travelling");
        clock.advance(ToasterState.REFLOW_MILLIS / 2);
        var midway = renderer.render(tree);

        GoldenImage.assertMatches("toast-reflowing", 400, 240, 1.0f, frame -> BoxPainter.paint(frame, midway));
    }

    /// Tells every toast how tall it came out, which is what a window's pointer
    /// router does after each paint and what the stack banks against the day one
    /// of them is dismissed ([ADR-0117]).
    ///
    /// There is no router here, so the tree is laid out once and the numbers are
    /// read off the same hit-test capture the real one reads — real heights
    /// rather than plausible ones, because a golden of a stack closing a hole of
    /// the wrong size is a picture of a bug that passes.
    private void measure(ElementTree tree, WidgetRenderer renderer) {
        var target = TestFrames.of(400, 240, 1.0f, 0);
        try (var render = RenderTree.create()) {
            render.update(target.frame(), renderer.render(tree));
            for (var region : HitTest.capture(render)) {
                if (region.owner() instanceof Element element && element.widget() instanceof ToastBox box) {
                    var extent = new Extent(region.width(), region.height());
                    box.measured(extent, extent);
                }
            }
        }
    }

    /// §3: "in: slide 16px from edge + `opacity`, overlay". Halfway through, the
    /// three are part of the way in from the right and half visible.
    @Test
    @DisplayName("a stack caught halfway in")
    void arriving() {
        var tree = threeToasts(Corner.BOTTOM_END);
        var renderer = rendererFor(Theme.NORD_DARK);

        renderer.render(tree);
        assertTrue(renderer.isAnimating(), "nothing is arriving");
        clock.advance(120);
        var midway = renderer.render(tree);

        GoldenImage.assertMatches("toast-arriving", 400, 240, 1.0f, frame -> BoxPainter.paint(frame, midway));
    }
}
