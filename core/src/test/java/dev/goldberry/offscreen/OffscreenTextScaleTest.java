package dev.goldberry.offscreen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.text.flow.TextFlow;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// A text-size setting, as an application's settings screen drives it: the
/// offscreen builder draws at a scale, and a session's host changes it from
/// the next pass, as a window's does.
@DisplayName("the text scale offscreen")
class OffscreenTextScaleTest {

    private static final String SHEET = """
            #root { width: 400px; height: 100px; align-items: flex-start; }
            #label { font-size: 20px; }
            """;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// A line of text whose box is as wide as the text: the smallest thing
    /// whose size says what the text was shaped at.
    private record Label(@Nullable String id, String content) implements Widget.Leaf, Styled, Paints {

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            var paragraph = context.paragraph(style, content);
            return Box.of().style(style).text(new Box.Text(paragraph, 0xFF000000, TextFlow.NORMAL));
        }
    }

    private record Root(List<Widget> children) implements Widget.Leaf, Styled, Paints {

        @Override
        public String id() {
            return "root";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().children(boxes.toArray(Box[]::new)).style(style);
        }
    }

    private static Widget scene() {
        return new Root(List.of(new Label("label", "Settings")));
    }

    private static float labelWidth(Session session) {
        return session.regions().stream()
                .filter(region -> region.owner() instanceof Element element && "label".equals(element.id()))
                .findFirst()
                .orElseThrow()
                .width();
    }

    @Test
    @DisplayName("draws text larger at a larger scale, and a session's host changes it while it runs")
    void scalesText() {
        var offscreen = Offscreen.of(400, 100).stylesheets(Scene.sheet(SHEET));
        float plain;
        try (var session = offscreen.session(scene())) {
            plain = labelWidth(session);
            assertEquals(1.0, session.host().textScale(), 1e-9, "1 by default");

            session.host().textScale(1.5);
            session.frame();
            assertEquals(1.5, session.host().textScale(), 1e-9);
            assertEquals(plain * 1.5, labelWidth(session), plain * 0.05, "the host's change reached the next pass");

            session.host().textScale(3);
            assertEquals(1.5, session.host().textScale(), 1e-9, "clamped to the design system's range");
            assertThrows(IllegalArgumentException.class, () -> session.host().textScale(Double.NaN));
        }

        try (var session = offscreen.textScale(1.25).session(scene())) {
            assertTrue(labelWidth(session) > plain * 1.15, "the builder's scale: " + labelWidth(session));
        }
        assertThrows(IllegalArgumentException.class, () -> offscreen.textScale(Double.POSITIVE_INFINITY));
    }
}
