package io.github.digitalsmile.goldberry.widgets.overlay.toast;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.style.Corner;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    private WidgetRenderer rendererFor(Theme theme) {
        return new WidgetRenderer(
                List.of(Controls.baseStylesheet(), theme.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, SCENE)),
                TestFont.get()).clock(clock);
    }

    private ElementTree threeToasts(Corner corner) {
        var tree = new ElementTree(new Toaster(toasts, corner), host);
        // Timeouts long enough that none of them goes while the picture is being
        // taken -- a golden of a stack should not depend on how many frames it
        // took to get there.
        toasts.show(new Toast("Draft saved.").timeout(Duration.ofMinutes(1)));
        toasts.show(new Toast("Two devices are signed in to this account, which is one"
                + " more than usual.").timeout(Duration.ofMinutes(1)));
        toasts.show(new Toast("Message sent.")
                .action("Undo", () -> { }).timeout(Duration.ofMinutes(1)));
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

        GoldenImage.assertMatches(name, 400, 240, 1.0f,
                frame -> BoxPainter.paint(frame, settled));
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

        GoldenImage.assertMatches("toast-arriving", 400, 240, 1.0f,
                frame -> BoxPainter.paint(frame, midway));
    }
}
