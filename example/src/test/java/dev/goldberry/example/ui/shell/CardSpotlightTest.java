package dev.goldberry.example.ui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.example.ui.Screen;
import dev.goldberry.example.ui.search.SearchIndex;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widgets.controls.pressable.Pressable;

/// The jump bar and the ring it draws, in the window the showcase builds.
@DisplayName("jumping to a card")
class CardSpotlightTest {

    private ShowcaseScene scene;
    private Fonts fonts;
    private Session session;

    @BeforeEach
    void open() {
        RendererRequirement.enforce();
        scene = new ShowcaseScene();
        fonts = Fonts.bundled();
        session = Offscreen.of(1280, 800)
                .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                .fonts(fonts)
                .session(scene.root("buttons"));
    }

    @AfterEach
    void close() {
        if (session != null) {
            session.close();
        }
        if (fonts != null) {
            fonts.close();
        }
        if (scene != null) {
            scene.close();
        }
    }

    private boolean ringed() {
        return session.overlays().stream().anyMatch(overlay -> overlay.widget() instanceof Spotlight);
    }

    @Test
    @DisplayName("a chip scrolls its card to the top of the screen and rings it for a moment")
    void aChipJumps() {
        var last = SearchIndex.shipped().cardsOn("buttons").getLast();
        var page = session.host().anchor(Screen.PAGE).orElseThrow().painted();
        var before = session.host().anchor(last.id()).orElseThrow().painted().top();
        assertTrue(before > page.top() + page.size().height(), "the last card starts below the fold");

        // Pressed rather than clicked: the last chip is off the end of the bar,
        // which scrolls sideways, and what is under test is where it goes.
        var chip = session.byId(JumpBar.chipId(last.id())).orElseThrow();
        ((Pressable) chip.widget()).onPress().run();
        session.advance(Duration.ofMillis(600));

        // The last card cannot reach the top -- the page ends under it -- so
        // what is asserted is that it moved up and is now on screen.
        var after = session.host().anchor(last.id()).orElseThrow().painted().top();
        assertTrue(after < before, () -> "the page did not move: " + before + " then " + after);
        assertTrue(
                after >= page.top() && after < page.top() + page.size().height(),
                () -> "the card is at " + after + ", outside a page at " + page);
        assertTrue(ringed(), "the card is ringed once the page has glided there");

        session.advance(Duration.ofMillis(1600));
        assertFalse(ringed(), "and the ring goes again");
    }

    @Test
    @DisplayName("a card the screen did not build is not found, and nothing is drawn")
    void aMissingCard() {
        var spotlight = new CardSpotlight();

        assertFalse(spotlight.show(session.host(), "no-such-card"));
        session.advance(Duration.ofMillis(600));
        assertEquals(false, ringed());
    }
}
