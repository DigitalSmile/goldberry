package dev.goldberry.example.ui.lookalike;

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
import dev.goldberry.example.ui.application.ChapterFixture;
import dev.goldberry.example.ui.lookalike.library.Library;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Element;
import dev.goldberry.widgets.controls.pressable.Pressable;

/// The Lookalike screen in the window's own wiring: that its controls reach
/// the player, and that a playing track moves on the window's clock.
///
/// The golden image is of the screen as it opens, paused, and cannot show any
/// of this; [dev.goldberry.example.ui.lookalike.player.SessionTest] has the
/// rules, and this has the wires.
@DisplayName("the lookalike screen")
class LookalikeChapterTest {

    private ShowcaseScene scene;
    private Fonts fonts;
    private Session session;

    @BeforeEach
    void open() {
        RendererRequirement.enforce();
        scene = new ShowcaseScene();
        fonts = Fonts.bundled();
        session = Offscreen.of(1440, 960)
                .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                .fonts(fonts)
                .session(scene.root("lookalike"));
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

    private String says(String id) {
        return ChapterFixture.says(session, id);
    }

    private void settle() {
        session.advance(Duration.ofMillis(20));
    }

    private boolean playing() {
        var toggle = session.byId("stream-toggle").orElseThrow().widget();
        return toggle instanceof Pressable pressable && pressable.name().equals("Pause");
    }

    @Test
    @DisplayName("opens paused on the track and position it promises")
    void opensPaused() {
        assertEquals("Midnight Arcade", says("stream-now-title"));
        assertEquals("1:16", says("stream-position"));
        assertEquals("Night Drive", says("stream-title"));
        assertFalse(playing());
        assertTrue(session.byId("screen-lookalike").isPresent());
    }

    @Test
    @DisplayName("plays a track pressed in the list, and the bar follows")
    void pressARow() {
        session.click("track-1");
        settle();

        assertEquals("Neon Tide", says("stream-now-title"));
        assertEquals("0:00", says("stream-position"));
        assertTrue(playing());
    }

    @Test
    @DisplayName("moves a playing track on with the window's clock, and stops when paused")
    void theClockRuns() {
        session.click("stream-toggle");
        settle();
        session.advance(Duration.ofMillis(3_050));

        assertEquals("1:19", says("stream-position"));

        session.click("stream-toggle");
        settle();
        session.advance(Duration.ofSeconds(5));

        assertEquals("1:19", says("stream-position"), "a paused track's clock moved");
    }

    @Test
    @DisplayName("next moves the bar to the next track")
    void next() {
        session.click("stream-next");
        settle();

        assertEquals("Glasshouse", says("stream-now-title"));
    }

    @Test
    @DisplayName("opens a playlist from the sidebar without changing what plays")
    void openFromTheSidebar() {
        session.click("playlist-deep-focus");
        settle();

        assertEquals("Deep Focus", says("stream-title"));
        assertEquals("Midnight Arcade", says("stream-now-title"));
    }

    @Test
    @DisplayName("a heart in the bar adds the song to Liked Songs, and pressing it again takes it out")
    void heart() {
        session.click("playlist-" + Library.LIKED);
        settle();
        var before = rows();

        session.click("playlist-weekly-finds");
        settle();
        session.click("track-1");
        settle();
        session.click(barHeart());
        settle();
        session.click("playlist-" + Library.LIKED);
        settle();

        assertEquals(before + 1, rows(), "Salt and Cedar was liked from the bar");

        session.click(barHeart());
        settle();

        assertEquals(before, rows(), "and unliked again");
    }

    @Test
    @DisplayName("the now-playing panel opens from the bar and closes again")
    void panel() {
        assertTrue(session.byId("stream-now-panel").isEmpty());

        session.click("stream-panel-toggle");
        settle();
        assertTrue(session.byId("stream-now-panel").isPresent());

        session.click("stream-panel-toggle");
        settle();
        assertTrue(session.byId("stream-now-panel").isEmpty());
    }

    /// How many track rows the open playlist shows.
    private int rows() {
        var count = 0;
        while (session.byId("track-" + (count + 1)).isPresent()) {
            count++;
        }
        return count;
    }

    /// The heart beside the title in the player bar.
    private Element barHeart() {
        return ChapterFixture.walk(session.byId("stream-bar").orElseThrow())
                .filter(element -> element.widget() instanceof Pressable pressable
                        && pressable.attributes().classes().contains("bar-heart"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the bar has no heart"));
    }
}
