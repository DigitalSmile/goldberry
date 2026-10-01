package dev.goldberry.widgets.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledAssets;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.paint.Box;
import dev.goldberry.text.Paragraph;
import dev.goldberry.text.font.FontSource;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;

/// `docs/gaps.md` G39 through a real `text`: a stylesheet naming a shipped family
/// reaches the paragraph a label is laid out and drawn with ([ADR-0349]).
///
/// The unit half is `ShippedFontsTest` in `:core`. This is the claim the gap was
/// written about, which is that a title set in a shipped face selects, wraps and
/// follows `font-size` like every other label, because it *is* every other label.
class ShippedFaceTest {

    @BeforeEach
    void requireRenderer() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("`font-family: Forum` on a text reaches its paragraph, at the size the cascade said")
    void aLabelIsDrawnInTheShippedFace() {
        var forum = FontSource.of(
                "Forum", BundledFont.Weight.REGULAR, BundledFont.Style.UPRIGHT, BundledAssets.font(BundledFont.CODE));
        var sheet =
                Stylesheet.parse(CascadeLayer.APPLICATION, "text.home-title { font-family: Forum; font-size: 32px }");
        try (var fonts = Fonts.bundled(List.of(forum))) {
            var renderer = new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(), sheet), fonts);
            var title = new Text("Tessera").withAttributes(Attributes.NONE.classes("home-title"));
            var plain = new Text("Tessera");

            var titleFont = textOf(renderer.render(new ElementTree(title))).font();
            var plainFont = textOf(renderer.render(new ElementTree(plain))).font();

            assertEquals("Forum", titleFont.face().name());
            assertEquals(32, titleFont.size(), 1e-9);
            assertEquals("Inter", plainFont.face().name(), "a label that names no family is still Inter");
        }
    }

    private static Paragraph textOf(Box box) {
        var found = find(box);
        if (found == null) {
            throw new AssertionError("no text box under " + box);
        }
        return found.paragraph();
    }

    private static Box.Text find(Box box) {
        if (box.text() != null) {
            return box.text();
        }
        for (var child : box.children()) {
            var found = find(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
