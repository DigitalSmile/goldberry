package dev.goldberry.example.ui.overlays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.core.Column;

/// The message cards, driven rather than photographed.
///
/// A golden can show four banners sitting there. It cannot show what the cards
/// are about: a banner arrives when the application describes one and goes when
/// it stops. So this presses the buttons.
@DisplayName("the message cards")
class MessagesTest {

    /// Tall enough that every banner this test spawns is laid out and painted: a
    /// node clipped out of the frame has no hit region.
    private static final int HEIGHT = 1400;

    /// The five the cards always show: four kinds and the summary.
    private static final long RESIDENT = 5;

    private ShowcaseScene scene;
    private Fonts fonts;
    private Session session;

    @BeforeEach
    void open() {
        RendererRequirement.enforce();
        scene = new ShowcaseScene();
        fonts = Fonts.bundled();
        // The three cards as the wall offers them, in a plain column: what is
        // asserted is which banners are described, and a wall would put them in
        // three columns without changing one of those answers.
        session = Offscreen.of(900, HEIGHT)
                .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                .fonts(fonts)
                .session(new Column(Messages.cards(), Attributes.NONE.id("messages")));
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

    private static Stream<Element> walk(Element element) {
        return Stream.concat(Stream.of(element), element.children().stream().flatMap(MessagesTest::walk));
    }

    private static boolean is(Element element, String cssType) {
        return element.widget() instanceof Styled styled && cssType.equals(styled.cssType());
    }

    /// How many banners the cards are describing right now.
    private long banners() {
        return walk(session.byId("messages").orElseThrow())
                .filter(element -> is(element, "message"))
                .count();
    }

    /// The × of the banner with this id, which is a part and has no id of its own.
    private void dismiss(String bannerId) {
        var banner = session.byId(bannerId).orElseThrow(() -> new AssertionError("no banner with id " + bannerId));
        var cross = walk(banner)
                .filter(element -> is(element, "message-dismiss"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(bannerId + " has no ×"));
        session.click(cross);
        // Past the banner's exit, which runs before the handler does.
        session.advance(Duration.ofMillis(600));
    }

    @Test
    @DisplayName("show the four kinds, a summary, and nothing spawned")
    void structure() {
        assertEquals(RESIDENT, banners());
        assertTrue(session.byId("notice-summary").isPresent(), "no error summary");
        assertTrue(session.byId("notice-bar").isPresent(), "no spawning bar");
        assertTrue(session.byId("notice-empty").isPresent(), "no empty state");
    }

    @Test
    @DisplayName("a button spawns a banner, and the empty line goes")
    void spawning() {
        session.click("spawn-warning");

        assertEquals(RESIDENT + 1, banners(), "the button described no banner");
        assertTrue(session.byId("notice-1").isPresent(), "the spawned banner has no element");
        assertTrue(session.byId("notice-empty").isEmpty(), "the empty line is still there with something there");
    }

    @Test
    @DisplayName("the × takes the banner away, because the list is what described it")
    void dismissing() {
        session.click("spawn-info");
        assertEquals(RESIDENT + 1, banners());

        dismiss("notice-1");

        assertEquals(RESIDENT, banners(), "the × did not remove it");
        assertTrue(session.byId("notice-1").isEmpty());
        assertTrue(session.byId("notice-empty").isPresent(), "the empty line did not come back");
    }

    /// Dismissing from the middle must not hand the removed element to the banner
    /// below it, which is what reconciliation by position would do.
    @Test
    @DisplayName("dismissing the middle of three leaves the other two, by key")
    void dismissingFromTheMiddle() {
        session.click("spawn-info");
        session.click("spawn-warning");
        session.click("spawn-danger");
        assertEquals(RESIDENT + 3, banners());

        dismiss("notice-2");

        assertEquals(RESIDENT + 2, banners());
        assertTrue(session.byId("notice-1").isPresent(), "the first went instead");
        assertTrue(session.byId("notice-2").isEmpty(), "the middle one is still here");
        assertTrue(session.byId("notice-3").isPresent(), "the last went instead");
    }

    @Test
    @DisplayName("Reset empties the list, and the numbers do not come round again")
    void clearing() {
        session.click("spawn-info");
        session.click("spawn-success");

        session.click("clear-notices");
        assertEquals(RESIDENT, banners());

        // A counter and not the list's size: nothing inherits the key of a banner
        // that has been dismissed.
        session.click("spawn-info");
        assertTrue(session.byId("notice-3").isPresent(), "the numbering restarted");
        assertEquals(RESIDENT + 1, banners());
    }

    @Test
    @DisplayName("the danger banner's × takes it off the kinds card")
    void dismissingAKind() {
        dismiss("kind-danger");

        assertEquals(RESIDENT - 1, banners());
        assertTrue(session.byId("kind-danger").isEmpty());
    }
}
