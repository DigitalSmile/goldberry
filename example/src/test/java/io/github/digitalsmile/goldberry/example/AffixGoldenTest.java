package io.github.digitalsmile.goldberry.example;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.backend.LogicalRect;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.example.ui.Scrolling;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.input.HitTest;
import io.github.digitalsmile.goldberry.input.Modifiers;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.layout.RenderTree;
import io.github.digitalsmile.goldberry.text.Fonts;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Density;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// A pinned `affix`, as a picture.
///
/// `AffixTest` proves the header stops at the viewport's edge, which is a fact
/// about *positions* and was true throughout the whole time the header was being
/// painted underneath the rows sliding past it. Whether you can read it is a fact
/// about pixels and paint order, and only an image says so
/// ([ADR-0123](../../../../../../../book/src/adr/0123-a-pinned-box-paints-after-its-siblings.md)).
class AffixGoldenTest {

    @Test
    @DisplayName("a pinned header is not scrolled over by the rows below it")
    void pinned() {
        RendererRequirement.enforce();
        var sheets = new ArrayList<Stylesheet>(
                Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
        sheets.add(Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css"));

        try (var fonts = Fonts.bundled()) {
            var renderer = new WidgetRenderer(sheets, fonts);
            var tree = new ElementTree(new Scrolling(() -> { }));
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
                router.pointerWheel(200, 350, 0, 7, Modifiers.NONE);
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
