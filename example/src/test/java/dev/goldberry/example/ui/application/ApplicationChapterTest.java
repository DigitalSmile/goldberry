package dev.goldberry.example.ui.application;

import static dev.goldberry.example.ui.application.ChapterFixture.says;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.offscreen.Session;
import dev.goldberry.widgets.controls.badge.Badge;
import dev.goldberry.widgets.controls.button.Button;

/// The Application screen: every card it promises, and the demonstrations that
/// have behaviour of their own, driven through the input router.
@DisplayName("the application screen")
class ApplicationChapterTest {

    private ChapterFixture fixture;

    @BeforeEach
    void open() {
        RendererRequirement.enforce();
        fixture = new ChapterFixture();
    }

    @AfterEach
    void close() {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    @DisplayName("has a card for every section of both chapters")
    void cards() {
        try (var session = fixture.window("application")) {
            assertEquals(
                    Set.of(
                            "application-four-kinds",
                            "application-values",
                            "application-actions",
                            "application-views",
                            "application-view-may-not-write",
                            "application-widget-state",
                            "application-the-application",
                            "application-more-models",
                            "application-inflate",
                            "application-shipping-a-widget",
                            "application-where-does-it-go",
                            "application-package-layout",
                            "application-starting-fast",
                            "application-weaving",
                            "markup-syntax",
                            "markup-parsing",
                            "markup-four-registries",
                            "markup-strict",
                            "markup-bind-path",
                            "markup-every-node",
                            "markup-hot-reload",
                            "markup-cannot-say",
                            "markup-compose"),
                    Set.copyOf(ChapterFixture.cardIds(
                            session.byId("screen-application").orElseThrow())));
        }
    }

    @Test
    @DisplayName("re-inflates a document as it is typed, and keeps the last good tree when it breaks")
    void livePreview() {
        var preview = new LivePreview("strict", ApplicationChapter.STRICT_SOURCE, fixture.context());
        try (var session = fixture.session(preview, 480, 480)) {
            assertTrue(says(session, "strict-status").contains("app.clik"), says(session, "strict-status"));

            assertTrue(session.focus("strict-source"));
            session.key("Ctrl+A").type("button id=\"typed\" press=\"app.click\" \"March\"");

            assertAll(
                    () -> assertEquals("Inflated one node.", says(session, "strict-status")),
                    () -> assertInstanceOf(
                            Button.class, session.byId("typed").orElseThrow().widget()));

            session.click("typed");
            assertEquals(1, fixture.model().clicks(), "the typed button presses the application's action");

            assertTrue(session.focus("strict-source"));
            session.key("Ctrl+A").type("button \"unclosed");
            assertAll(
                    () -> assertTrue(says(session, "strict-status").contains("line 1"), says(session, "strict-status")),
                    () -> assertTrue(session.byId("typed").isPresent(), "the last good tree is still shown"));
        }
    }

    @Test
    @DisplayName("keeps one count in the card and one in the application")
    void twoCounters() {
        try (var session = fixture.session(new TwoCounters(fixture.context()), 480, 240)) {
            session.click("state-local-add").click("state-local-add");
            session.click("state-app-add");

            assertAll(
                    () -> assertEquals("2", says(session, "state-local")),
                    () -> assertEquals("1", says(session, "state-app")),
                    () -> assertEquals(1, fixture.model().clicks()));
        }
    }

    @Test
    @DisplayName("draws one mark per league, a loop that follows the value")
    void leagueMarks() {
        try (var session = fixture.session(new LeagueMarks(fixture.context()), 480, 240)) {
            assertTrue(isDisabled(session, "marks-undo"), "nothing to turn back from");

            session.click("marks-add").click("marks-add").click("marks-add");
            fixture.refresh(session);

            var marks = ChapterFixture.walk(session.byId("league-marks").orElseThrow())
                    .map(element -> element.widget())
                    .filter(Badge.class::isInstance)
                    .map(widget -> ((Badge) widget).resolved())
                    .toList();
            assertAll(
                    () -> assertEquals(List.of("1", "2", "3"), marks),
                    () -> assertTrue(!isDisabled(session, "marks-undo")));

            session.click("marks-undo");
            fixture.refresh(session);
            assertEquals(2, fixture.model().clicks());
        }
    }

    private static boolean isDisabled(Session session, String id) {
        return assertInstanceOf(Button.class, session.byId(id).orElseThrow().widget())
                .disabled();
    }
}
