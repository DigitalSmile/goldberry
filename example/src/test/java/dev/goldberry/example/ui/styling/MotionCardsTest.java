package dev.goldberry.example.ui.styling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.example.ShowcaseStyles;
import dev.goldberry.motion.Clock;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.paint.Box;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.core.Column;

/// The three motion cards through the showcase's real stylesheets: the
/// keyframes they name exist, the cards keep the loop awake only for what moves,
/// and an entry the button adds is there.
class MotionCardsTest {

    private List<Stylesheet> sheets;

    private Fonts fonts;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        fonts = Fonts.bundled();
        sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.addAll(ShowcaseStyles.sheets());
    }

    @AfterEach
    void tearDown() {
        if (fonts != null) {
            fonts.close();
        }
    }

    private static Widget cards() {
        return new Column(new MotionCards.Settling(), MotionCards.keyframes(), new MotionCards.Entering());
    }

    @Test
    @DisplayName("every block the cards' rules name is declared, so nothing is silently still")
    void everyNamedBlockExists() {
        var resolver = new StyleResolver(sheets);

        for (var name : List.of("motion-breathe", "motion-turn", "motion-glaze", "styling-march")) {
            assertNotNull(resolver.keyframes(name), name);
        }
        assertTrue(resolver.hasStartingStyles());
    }

    @Test
    @DisplayName("the cards animate, and a swatch is somewhere else a moment later")
    void itMoves() {
        var clock = Clock.virtual();
        var renderer = new WidgetRenderer(sheets, fonts).clock(clock);
        var tree = new ElementTree(cards());

        var first = find(renderer.render(tree), "motion-turn");
        assertNotNull(first, "no motion-turn was drawn");
        assertTrue(renderer.isAnimating());
        clock.advance(450);
        tree.flush();
        var later = find(renderer.render(tree), "motion-turn");
        assertNotNull(later, "the motion-turn went away");

        assertNotEquals(first.transform(), later.transform(), "a quarter of the way round");
    }

    @Test
    @DisplayName("with reduced motion the keyframes stop and the floor is still, so the loop can idle")
    void reducedMotionIdles() {
        var renderer = new WidgetRenderer(sheets, fonts).clock(Clock.virtual()).reducedMotion(true);

        var turn = find(renderer.render(new ElementTree(cards())), "motion-turn");

        assertNotNull(turn, "no motion-turn was drawn");
        assertTrue(turn.transform().isNone());
        assertFalse(renderer.isAnimating());
    }

    @Test
    @DisplayName("Add an entry adds one, and Clear takes them all away")
    void entries() {
        try (var session =
                Offscreen.of(640, 480).stylesheets(sheets).fonts(fonts).session(new MotionCards.Entering())) {
            session.click("motion-add");
            session.click("motion-add");
            assertEquals(
                    2, session.byId("motion-entries").orElseThrow().children().size());

            session.click("motion-clear");
            assertEquals(
                    0, session.byId("motion-entries").orElseThrow().children().size());
        }
    }

    /// The first box whose owner's widget carries `className`.
    private static @Nullable Box find(Box box, String className) {
        if (box.owner() instanceof Element element
                && element.widget() instanceof Styled styled
                && styled.classes().contains(className)) {
            return box;
        }
        for (var child : box.children()) {
            var found = find(child, className);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
