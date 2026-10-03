package dev.goldberry.example.ui.gallery;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.Goldberry;
import dev.goldberry.RendererRequirement;
import dev.goldberry.example.ui.application.ChapterFixture;
import dev.goldberry.platform.Capability;

/// What a screen is told this build can do: the library's answer in the window,
/// and whatever a picture of the screen pins.
@DisplayName("a gallery context")
class GalleryContextTest {

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
    @DisplayName("reports the loaded library's capabilities unless it is given others")
    void defaultsToTheLibrary() {
        var context = fixture.context();
        assertEquals(Goldberry.capabilities(), context.capabilities());
    }

    @Test
    @DisplayName("keeps a copy of the capabilities it was given, and refuses to change it")
    void keepsACopy() {
        var given = EnumSet.of(Capability.WAYLAND);
        var base = fixture.context();
        var context = new GalleryContext(
                base.model(), base.actions(), base.documents(), base.plus(), base.startTour(), given);
        given.add(Capability.SYSTEM_THEME);
        assertAll(
                () -> assertEquals(Set.of(Capability.WAYLAND), context.capabilities()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> context.capabilities().add(Capability.WEB_VIEW)));
    }
}
