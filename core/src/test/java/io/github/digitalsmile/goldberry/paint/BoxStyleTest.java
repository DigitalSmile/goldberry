package io.github.digitalsmile.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.cascade.StyleResolver;
import io.github.digitalsmile.goldberry.css.value.CssLength;
import io.github.digitalsmile.goldberry.layout.Align;
import io.github.digitalsmile.goldberry.layout.FlexDirection;
import io.github.digitalsmile.goldberry.layout.Insets;
import io.github.digitalsmile.goldberry.layout.Justify;
import io.github.digitalsmile.goldberry.layout.Length;

/// Where the CSS engine meets the box tree.
///
/// The seam §8 calls a design invariant: layout properties land on the fields
/// Yoga reads, paint properties on the ones Blend2D reads, and nothing in
/// between is a string.
class BoxStyleTest {

    /// A one-node stand-in for the element tree (ADR-0004), so the whole
    /// stylesheet-to-box path can be exercised without one.
    private record Node(String type, Set<String> classes) implements io.github.digitalsmile.goldberry.css.StyleElement {
        @Override
        public String id() {
            return null;
        }

        @Override
        public io.github.digitalsmile.goldberry.css.StyleElement parent() {
            return null;
        }

        @Override
        public boolean hasState(io.github.digitalsmile.goldberry.css.select.Selector.PseudoClass state) {
            return false;
        }
    }

    private static ComputedStyle styleFor(String css) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, css);
        var declarations = new StyleResolver(List.of(sheet)).resolve(new Node("panel", Set.of()));
        return ComputedStyle.of(declarations, CssLength.Context.DEFAULT);
    }

    @Test
    @DisplayName("a stylesheet drives every field of a box")
    void stylesheetDrivesTheBox() {
        var box = Box.of().style(styleFor("""
                panel {
                  background: #2e3440;
                  flex-direction: column;
                  justify-content: space-between;
                  align-items: center;
                  width: 320px;
                  height: 50%;
                  padding: 8px;
                  gap: 4px;
                  flex-grow: 2;
                }
                """));

        assertEquals(0xFF2E3440, box.background());
        assertEquals(FlexDirection.COLUMN, box.direction());
        assertEquals(Justify.SPACE_BETWEEN, box.justifyContent());
        assertEquals(Align.CENTER, box.alignItems());
        assertEquals(Length.points(320), box.width());
        assertEquals(Length.percent(50), box.height());
        assertEquals(Insets.all(Length.points(8)), box.padding());
        assertEquals(Length.points(4), box.gap());
        assertEquals(2.0, box.flexGrow());
    }

    @Test
    @DisplayName("a style does not replace what the box contains")
    void contentIsNotStyled() {
        // A stylesheet decides how a node looks, not what is in it.
        var child = Box.filled(0xFF000000);
        var parent = Box.of().children(child).style(styleFor("panel { background: red }"));

        assertEquals(1, parent.children().size());
        assertSame(child, parent.children().getFirst());
        assertNull(parent.text());
    }

    @Test
    @DisplayName("color reaches the text, because that is what color means")
    void colorReachesText() {
        // The only test here that needs a real font, and so the only one that
        // needs the native library: a Paragraph cannot exist without one.
        io.github.digitalsmile.goldberry.RendererRequirement.enforce();
        try (var font = io.github.digitalsmile.goldberry.text.font.Font.bundled(
                io.github.digitalsmile.goldberry.assets.BundledFont.UI, 14)) {

            var style = ComputedStyle.of(
                    Map.of(
                            "color",
                            Stylesheet.parse(CascadeLayer.APPLICATION, "a { color: #88c0d0 }")
                                    .rules()
                                    .getFirst()
                                    .declarations()
                                    .getFirst()
                                    .value()),
                    CssLength.Context.DEFAULT);

            var paragraph = io.github.digitalsmile.goldberry.text.Paragraph.of(font, "hello");
            var text = Box.text(paragraph, 0xFF000000).style(style);

            assertEquals(0xFF88C0D0, text.text().argb());
        }
    }
}
