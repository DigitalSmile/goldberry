package io.github.digitalsmile.goldberry.widgets.core;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The whole stack in one image: KDL → widgets → element tree → cascade → boxes
/// → Blend2D. If any of the six breaks, this changes.
///
/// It lived in `:core`'s `GoldenImageTest` until [ADR-0092], which is where it
/// stopped being able to: that file tests the golden *harness* — the PNG compare,
/// the tolerance, the update mode — and this one method was the only thing in it
/// that needed a widget. `:core` has none now, so the harness test stays there
/// and its one widget-shaped case is here, with the image it asserts against.
class WidgetStackGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("a widget tree, inflated from KDL and styled by CSS")
    void widgetTreeFromKdl() {
        var markup = KdlParser.parse("""
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
        var widget = Primitives.inflater().inflate(markup.getFirst());

        var base = Stylesheet.parse(CascadeLayer.TOOLKIT_BASE, """
                panel.root { background: var(--gb-bg); padding: 8px }
                row { gap: 8px; flex-grow: 1 }
                panel.sidebar { background: var(--gb-surface); width: 48px }
                column.body { gap: 6px; flex-grow: 1 }
                panel.bar { background: var(--gb-surface-2); flex-grow: 1 }
                panel.bar.accent { background: var(--gb-accent) }
                """);

        var tree = new ElementTree(widget);
        var renderer = new WidgetRenderer(List.of(base, Theme.NORD_DARK.load()), TestFont.get());

        GoldenImage.assertMatches("widget-tree", 200, 90, 1.0f,
                frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }
}
