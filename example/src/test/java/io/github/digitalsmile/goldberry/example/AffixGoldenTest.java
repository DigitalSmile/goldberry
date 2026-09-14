package io.github.digitalsmile.goldberry.example;

import java.util.ArrayList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.example.ui.Scrolling;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Density;

/// A pinned `affix`, as a picture.
///
/// `AffixTest` proves the header stops at the viewport's edge, which is a fact
/// about *positions* and was true throughout the whole time the header was being
/// painted underneath the rows sliding past it. Whether you can read it is a fact
/// about pixels and paint order, and only an image says so
/// (ADR-0123).
class AffixGoldenTest {

    /// What one turn of the wheel is worth, mirroring
    /// `ScrollViewport.LINES_PER_NOTCH` — which is package-private in `:widgets`
    /// and is a platform convention rather than an API.
    private static final float LINES_PER_NOTCH = 3;

    @Test
    @DisplayName("a pinned header is not scrolled over by the rows below it")
    void pinned() {
        RendererRequirement.enforce();
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
        sheets.add(Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css"));

        try (var fonts = Fonts.bundled()) {
            var renderer = new WidgetRenderer(sheets, fonts);
            var tree = new ElementTree(new Scrolling());
            var router = new PointerRouter();
            router.focusRoot(tree.root());
            router.windowBounds(LogicalRect.of(0, 0, 900, 560));

            // Enough to carry the first header to the top and put four rows of
            // its own section over the line it now sits on.
            var warm = TestFrames.of(900, 560, 1.0f, 0);
            try (var render = RenderTree.create()) {
                for (var i = 0; i < 4; i++) {
                    tree.flush();
                    render.update(warm.frame(), renderer.render(tree));
                    router.updateRegions(HitTest.capture(render));
                }
                // **Seven lines**, which is the distance this picture is of.
                // A wheel event counts *notches* and a notch is three lines
                // ([ADR-0314]), so seven lines is seven thirds of one — a
                // fraction, which is exactly what a trackpad sends and the only
                // way an event that counts detents can say "this far".
                router.pointerWheel(200, 350, 0, 7f / LINES_PER_NOTCH, Modifiers.NONE);
                for (var i = 0; i < 4; i++) {
                    tree.flush();
                    render.update(warm.frame(), renderer.render(tree));
                    router.updateRegions(HitTest.capture(render));
                }
            } finally {
                warm.end();
            }

            GoldenImage.assertMatches("affix-pinned", 900, 560, 1.0f, frame -> {
                try (var render = RenderTree.create()) {
                    tree.flush();
                    render.update(frame, renderer.render(tree));
                    render.paint(frame);
                }
            });
        }
    }
}
