package io.github.digitalsmile.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.cascade.StyleResolver;
import io.github.digitalsmile.goldberry.example.ui.MotionScreen;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.Controls;

/// The Motion screen through the showcase's real stylesheets ([ADR-0354]): the
/// keyframes it names exist, and the screen keeps the loop awake only for what
/// moves.
class MotionScreenTest {

    private List<Stylesheet> sheets;

    private Fonts fonts;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        fonts = Fonts.bundled();
        sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.add(Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css"));
    }

    @AfterEach
    void tearDown() {
        if (fonts != null) {
            fonts.close();
        }
    }

    @Test
    @DisplayName("every block the screen's rules name is declared, so nothing is silently still")
    void everyNamedBlockExists() {
        var resolver = new StyleResolver(sheets);

        for (var name : List.of("motion-breathe", "motion-turn", "motion-glaze")) {
            assertNotNull(resolver.keyframes(name), name);
        }
        assertTrue(resolver.hasStartingStyles());
    }

    @Test
    @DisplayName("the screen animates, and a swatch is somewhere else a moment later")
    void itMoves() {
        var clock = Clock.virtual();
        var renderer = new WidgetRenderer(sheets, fonts).clock(clock);
        var tree = new ElementTree(new MotionScreen());

        var first = find(renderer.render(tree), "motion-turn");
        assertTrue(renderer.isAnimating());
        clock.advance(450);
        tree.flush();
        var later = find(renderer.render(tree), "motion-turn");

        assertNotEquals(first.transform(), later.transform(), "a quarter of the way round");
    }

    @Test
    @DisplayName("with reduced motion the keyframes stop and the floor is still, so the loop can idle")
    void reducedMotionIdles() {
        var renderer = new WidgetRenderer(sheets, fonts).clock(Clock.virtual()).reducedMotion(true);

        var turn = find(renderer.render(new ElementTree(new MotionScreen())), "motion-turn");

        assertTrue(turn.transform().isNone());
        assertFalse(renderer.isAnimating());
    }

    /// The first box whose owner's widget carries `className`.
    private static Box find(Box box, String className) {
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
