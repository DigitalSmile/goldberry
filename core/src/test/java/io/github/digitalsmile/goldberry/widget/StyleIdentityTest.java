package io.github.digitalsmile.goldberry.widget;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.text.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The style a node hands its children keeps its identity while it keeps its
/// value — [ADR-0142].
///
/// This is not a micro-optimisation with a test bolted on: the cache below it is
/// keyed on that identity, so without this **the cache does not work at all** for
/// any subtree under a widget that writes an inline value. In the showcase that
/// is every widget on the screen, because every screen is inside a `scroll` and
/// `ScrollContent.restyle` returns `resolved.flexShrink(0)` unconditionally — 56
/// of 72 elements re-resolving on a frame where nothing had changed, and 10ms of
/// cascade to arrive at last frame's answer.
///
/// The assertion is the mechanism itself rather than a timing: a child is handed
/// the **same instance** on the second frame, which is the fact the cache checks.
class StyleIdentityTest {

    /// A widget that writes an inline value, as `ScrollContent` and `Tab` do.
    ///
    /// `flexShrink(0)` allocates a new `ComputedStyle` every time it is called,
    /// which is the whole of what poisoned the cache.
    private record Poisoner(List<Widget> children) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "poisoner";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public ComputedStyle restyle(ComputedStyle resolved) {
            return resolved.flexShrink(0);
        }

        @Override
        public List<Widget> children() {
            return children;
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).children(children.toArray(Box[]::new));
        }
    }

    /// A leaf that records the exact style instance it was handed.
    private record Recorder(List<ComputedStyle> seen) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "recorder";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            seen.add(style);
            return Box.of().style(style);
        }
    }

    private Font font;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        font = Font.bundled(BundledFont.UI, 13);
    }

    @AfterEach
    void tearDown() {
        if (font != null) {
            font.close();
        }
    }

    private static WidgetRenderer renderer(Font font) {
        return new WidgetRenderer(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, """
                poisoner { color: #d8dee9; padding: 4px }
                recorder { width: 10px; height: 10px }
                """)), font);
    }

    @Test
    @DisplayName("a child is handed the same style instance while nothing has changed")
    void identityIsStable() {
        var seen = new ArrayList<ComputedStyle>();
        var tree = new ElementTree(new Poisoner(List.of(new Recorder(seen))));
        var renderer = renderer(font);

        renderer.render(tree);
        renderer.render(tree);

        assertSame(seen.get(0), seen.get(1),
                "the parent re-ran `restyle` and produced an equal style;"
                        + " handing down a new instance is what disabled the cache");
    }

    /// The other half, and the reason this cannot simply cache `restyle`'s
    /// output: when the value really moves, the instance must move with it, or
    /// the subtree would keep a style that no longer describes it.
    @Test
    @DisplayName("a style that actually changes is handed down as a new instance")
    void identityFollowsTheValue() {
        var seen = new ArrayList<ComputedStyle>();
        var tree = new ElementTree(new Poisoner(List.of(new Recorder(seen))));

        renderer(font).render(tree);
        // A different stylesheet is a different answer for the parent, so the
        // child has to see it.
        new WidgetRenderer(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, """
                poisoner { color: #bf616a; padding: 4px }
                recorder { width: 10px; height: 10px }
                """)), font).render(tree);

        assertNotSame(seen.get(0), seen.get(1));
        assertTrue(seen.get(0).color() != seen.get(1).color(), "and it is a different colour");
    }

}
