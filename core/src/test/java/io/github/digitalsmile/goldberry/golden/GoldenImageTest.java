package io.github.digitalsmile.goldberry.golden;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.CssLength;
import io.github.digitalsmile.goldberry.css.Selector;
import io.github.digitalsmile.goldberry.css.StyleElement;
import io.github.digitalsmile.goldberry.css.StyleResolver;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.text.Font;
import io.github.digitalsmile.goldberry.text.Paragraph;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The rendered output, pinned.
///
/// Every other test in the suite asserts about one stage — a token, a cascade, a
/// glyph advance. These assert about the pixels, which is the only place the
/// stages are all wrong together or all right together. §14 asks for exactly
/// this, on all three platforms.
///
/// The scenes are driven through CSS rather than by building `Box`es directly,
/// deliberately: a golden that goes stylesheet → cascade → `ComputedStyle` →
/// `Box` → Blend2D is one image that fails if any of those five break.
class GoldenImageTest {

    /// A node in a hand-built tree, standing in for the element tree of
    /// ADR-0004. The same shape `TestElement` uses in the css tests, kept
    /// separate because this one also carries the box content.
    private static final class Node implements StyleElement {
        private final String type;
        private final Set<String> classes = new LinkedHashSet<>();
        private final List<Node> children = new ArrayList<>();
        private Node parent;
        private String text;

        Node(String type, String... classNames) {
            this.type = type;
            classes.addAll(List.of(classNames));
        }

        Node with(Node... kids) {
            for (var kid : kids) {
                kid.parent = this;
                children.add(kid);
            }
            return this;
        }

        Node text(String value) {
            this.text = value;
            return this;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public String id() {
            return null;
        }

        @Override
        public Set<String> classes() {
            return classes;
        }

        @Override
        public StyleElement parent() {
            return parent;
        }

        @Override
        public boolean hasState(Selector.PseudoClass state) {
            return false;
        }
    }

    private Font font;

    @BeforeEach
    void openFont() {
        // Embedded Inter, pinned by checksum at build time (ADR-0033). That is
        // what makes a golden reproducible: nothing here reads a system font.
        RendererRequirement.enforce();
        font = Font.bundled(BundledFont.UI, 14);
    }

    @AfterEach
    void closeFont() {
        if (font != null) {
            font.close();
        }
    }

    /// Styles a node tree and turns it into the box tree that gets painted.
    private Box build(Node node, List<Stylesheet> sheets) {
        var resolver = new StyleResolver(sheets);
        return toBox(node, resolver);
    }

    private Box toBox(Node node, StyleResolver resolver) {
        var style = ComputedStyle.of(resolver.resolve(node), CssLength.Context.DEFAULT);
        if (node.text != null) {
            return Box.text(Paragraph.of(font, node.text), 0xFF000000).style(style);
        }
        var kids = node.children.stream().map(child -> toBox(child, resolver)).toArray(Box[]::new);
        return Box.of().children(kids).style(style);
    }

    @Test
    @DisplayName("a styled flexbox row")
    void flexRow() {
        var css = Stylesheet.parse(CascadeLayer.APPLICATION, """
                root {
                  background: #eceff4;
                  flex-direction: row;
                  padding: 12px;
                  gap: 8px;
                }
                panel { background: #5e81ac; flex-grow: 1 }
                panel.wide { background: #bf616a; flex-grow: 2 }
                """);
        var tree = new Node("root").with(
                new Node("panel"),
                new Node("panel", "wide"),
                new Node("panel"));

        GoldenImage.assertMatches("flex-row", 240, 80, 1.0f,
                frame -> BoxPainter.paint(frame, build(tree, List.of(css))));
    }

    @Test
    @DisplayName("a nested column, so the cascade and the layout both have depth")
    void nestedColumn() {
        var css = Stylesheet.parse(CascadeLayer.APPLICATION, """
                root { background: #2e3440; flex-direction: column; padding: 8px; gap: 6px }
                row { flex-direction: row; gap: 6px; height: 24px }
                cell { background: #88c0d0; flex-grow: 1 }
                row > cell.accent { background: #ebcb8b }
                """);
        var tree = new Node("root").with(
                new Node("row").with(new Node("cell"), new Node("cell", "accent")),
                new Node("row").with(new Node("cell", "accent"), new Node("cell")));

        GoldenImage.assertMatches("nested-column", 200, 80, 1.0f,
                frame -> BoxPainter.paint(frame, build(tree, List.of(css))));
    }

    @Test
    @DisplayName("the same tree under nord-light and nord-dark")
    void nordLight() {
        GoldenImage.assertMatches("nord-light", 200, 60, 1.0f,
                frame -> BoxPainter.paint(frame, themedTree(Theme.NORD_LIGHT)));
    }

    @Test
    @DisplayName("nord-dark differs from nord-light by nothing but the theme sheet")
    void nordDark() {
        GoldenImage.assertMatches("nord-dark", 200, 60, 1.0f,
                frame -> BoxPainter.paint(frame, themedTree(Theme.NORD_DARK)));
    }

    /// One widget stylesheet, two themes. Neither of these goldens can be right
    /// unless custom properties inherit and the theme layer wins (§10).
    private Box themedTree(Theme theme) {
        var base = Stylesheet.parse(CascadeLayer.TOOLKIT_BASE, """
                root { background: var(--gb-bg); padding: 10px; gap: 8px }
                panel { background: var(--gb-surface); flex-grow: 1 }
                panel.accent { background: var(--gb-accent) }
                """);
        var tree = new Node("root").with(new Node("panel"), new Node("panel", "accent"));
        return build(tree, List.of(base, theme.load()));
    }

    @Test
    @DisplayName("text, at a fractional display scale")
    void textAtFractionalScale() {
        var css = Stylesheet.parse(CascadeLayer.APPLICATION, """
                root { background: #eceff4; padding: 6px }
                label { color: #2e3440 }
                """);
        var tree = new Node("root").with(new Node("label").text("Goldberry"));

        // 1.5, not 1.0: every HiDPI bug hides at 100%, and text is where a
        // scale that is applied twice -- or not at all -- shows first.
        GoldenImage.assertMatches("text-fractional-scale", 180, 48, 1.5f,
                frame -> BoxPainter.paint(frame, build(tree, List.of(css))));
    }

    @Test
    @DisplayName("a translucent fill composites against what is under it")
    void translucentFill() {
        var css = Stylesheet.parse(CascadeLayer.APPLICATION, """
                root { background: #eceff4; padding: 10px }
                panel { background: #88c0d04d; flex-grow: 1 }
                """);
        var tree = new Node("root").with(new Node("panel"));

        // The selection colour's shape (§10): alpha in a hex literal has to
        // survive parsing, packing, premultiplication and the blend.
        GoldenImage.assertMatches("translucent-fill", 120, 60, 1.0f,
                frame -> BoxPainter.paint(frame, build(tree, List.of(css))));
    }

    @Test
    @DisplayName("a widget tree, inflated from KDL and styled by CSS")
    void widgetTreeFromKdl() {
        // The whole stack in one image: KDL -> widgets -> element tree ->
        // cascade -> boxes -> Blend2D. If any of the six breaks, this changes.
        var markup = io.github.digitalsmile.goldberry.kdl.KdlParser.parse("""
                panel class="root" {
                  row {
                    panel class="sidebar"
                    column class="body" {
                      panel class="bar"
                      panel class="bar accent"
                    }
                  }
                }
                """);
        var widget = io.github.digitalsmile.goldberry.widget.Widgets.inflater()
                .inflate(markup.getFirst());

        var base = Stylesheet.parse(CascadeLayer.TOOLKIT_BASE, """
                panel.root { background: var(--gb-bg); padding: 8px }
                row { gap: 8px; flex-grow: 1 }
                panel.sidebar { background: var(--gb-surface); width: 48px }
                column.body { gap: 6px; flex-grow: 1 }
                panel.bar { background: var(--gb-surface-2); flex-grow: 1 }
                panel.bar.accent { background: var(--gb-accent) }
                """);

        var tree = new io.github.digitalsmile.goldberry.widget.ElementTree(widget);
        var renderer = new io.github.digitalsmile.goldberry.widget.WidgetRenderer(
                List.of(base, Theme.NORD_DARK.load()), font);

        GoldenImage.assertMatches("widget-tree", 200, 90, 1.0f,
                frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }
}
