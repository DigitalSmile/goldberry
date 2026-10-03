package dev.goldberry.example.ui.layout;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.example.Showcase;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.input.PointerRouter;
import dev.goldberry.input.hit.HitTest;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.paint.tree.RenderTree;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Density;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.text.Text;

/// A pinned `affix`, as a picture.
///
/// `AffixTest` proves the header stops at the viewport's edge, which is a fact
/// about *positions* and was true throughout the whole time the header was being
/// painted underneath the rows sliding past it. Whether you can read it is a fact
/// about pixels and paint order, and only an image says so: a pinned box paints
/// after its siblings.
///
/// The golden is the guide's picture of the card, taken when the card had a title
/// and a caption where it now has a head and a summary. The test frames the card's
/// jump bar and list the way the picture shows them, so the picture stays a
/// picture of the same pixels: what it is about is the list, not the card's head.
class AffixGoldenTest {

    /// What one turn of the wheel is worth, mirroring
    /// `ScrollViewport.LINES_PER_NOTCH`, which is package-private in `:widgets`
    /// and is a platform convention rather than an API.
    private static final float LINES_PER_NOTCH = 3;

    /// The card as the picture shows it: a title, a caption, then the jump bar
    /// and the list the Scrolling screen's card holds.
    private static Widget pictured(List<Widget> content) {
        var children = new ArrayList<Widget>();
        children.add(new Text("A viewport of its own", Attributes.NONE.classes("card-title")));
        children.add(new Text(
                "Its four headers are `affix`, so each one lifts and stays put"
                        + " while its section passes underneath. The buttons ask the list"
                        + " to bring a section into view, and it moves the least it can.",
                Attributes.NONE.classes("caption")));
        children.addAll(content);
        return new Card(List.copyOf(children), Attributes.NONE.id("scroll-card").classes("wall-card"));
    }

    @Test
    @DisplayName("a pinned header is not scrolled over by the rows below it")
    void pinned() {
        RendererRequirement.enforce();
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
        sheets.add(Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css"));

        try (var fonts = Fonts.bundled()) {
            var renderer = new WidgetRenderer(sheets, fonts);
            var tree = new ElementTree(new ScrollingCard(AffixGoldenTest::pictured));
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
                // A wheel event counts *notches* and a notch is three lines,
                // so seven lines is seven thirds of one: a fraction, which is
                // exactly what a trackpad sends and the only way an event that
                // counts detents can say "this far".
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
