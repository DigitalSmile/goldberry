package dev.goldberry.example.ui.diagnostics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.example.ui.application.ChapterFixture;
import dev.goldberry.platform.Capability;
import dev.goldberry.widgets.controls.badge.Badge;
import dev.goldberry.widgets.overlay.hud.Hud;
import dev.goldberry.widgets.text.Text;

/// The Diagnostics screen: every card it promises, and the readings it takes.
@DisplayName("the diagnostics screen")
class DiagnosticsChapterTest {

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
    @DisplayName("has a card for every section of the chapter")
    void cards() {
        try (var session = fixture.window("diagnostics")) {
            assertAll(
                    () -> assertEquals(
                            Set.of(
                                    "diagnostics-logback",
                                    "diagnostics-startup",
                                    "diagnostics-frames",
                                    "diagnostics-native",
                                    "diagnostics-presentation",
                                    "diagnostics-capabilities",
                                    "diagnostics-properties",
                                    "diagnostics-failures"),
                            Set.copyOf(ChapterFixture.cardIds(
                                    session.byId("screen-diagnostics").orElseThrow()))),
                    () -> assertInstanceOf(
                            Hud.class,
                            session.byId("diagnostics-hud").orElseThrow().widget()),
                    () -> assertTrue(session.byId("logback-levels").isPresent()),
                    () -> assertTrue(session.byId("native-levels").isPresent()));
        }
    }

    @Test
    @DisplayName("binds the start-up and presentation readings the window publishes")
    void bindsTheWindowsReadings() {
        try (var session = fixture.window("diagnostics")) {
            var startup = assertInstanceOf(
                    Text.class,
                    session.byId("diagnostics-startup-time").orElseThrow().widget());
            var presentation = assertInstanceOf(
                    Badge.class,
                    session.byId("diagnostics-presentation-badge").orElseThrow().widget());
            assertAll(
                    () -> assertTrue(startup.source() != null, "the start-up time is bound"),
                    () -> assertTrue(presentation.source() != null, "the presentation is bound"));
        }
    }

    @Test
    @DisplayName("says yes or no for every capability")
    void capabilities() {
        var only = new BuildCapabilities(Set.of(Capability.WAYLAND));
        try (var session = fixture.session(only, 480, 400)) {
            assertAll(
                    () -> assertEquals("yes", ChapterFixture.says(session, "capability-wayland")),
                    () -> assertEquals("no", ChapterFixture.says(session, "capability-system_theme")),
                    () -> assertEquals("system theme", BuildCapabilities.label(Capability.SYSTEM_THEME)));
        }
    }

    @Test
    @DisplayName("shows what this run set each property to")
    void properties() {
        var set = Map.of("goldberry.gpu", "off");
        var table = new RunProperties(RunProperties.LISTED, set::get);
        try (var session = fixture.session(table, 480, 900)) {
            assertAll(
                    () -> assertEquals("off", ChapterFixture.says(session, "property-goldberry.gpu")),
                    () -> assertEquals("not set", ChapterFixture.says(session, "property-goldberry.trace.input")));
        }
    }
}
