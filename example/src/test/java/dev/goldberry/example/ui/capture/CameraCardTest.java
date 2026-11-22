package dev.goldberry.example.ui.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import dev.goldberry.RendererRequirement;
import dev.goldberry.camera.CameraException;
import dev.goldberry.camera.CameraFormat;
import dev.goldberry.camera.CameraInfo;
import dev.goldberry.camera.CameraPosition;
import dev.goldberry.camera.CameraStatus;
import dev.goldberry.camera.Cameras;
import dev.goldberry.camera.synthetic.SyntheticCamera;
import dev.goldberry.camera.view.CameraView;
import dev.goldberry.camera.view.Mirror;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.junit.HeadlessRuntime;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.controls.select.Select;
import dev.goldberry.widgets.core.Column;

/// Under [HeadlessRuntime]: the widgets post their redraws, and the cards
/// their devices' news, to the UI thread, which needs a runtime to post to.
/// Without one the first post would start the desktop's.
@DisplayName("the Camera card")
@ExtendWith(HeadlessRuntime.class)
class CameraCardTest {

    private final AtomicInteger asked = new AtomicInteger();

    private final Supplier<Cameras> noCameras = () -> {
        asked.incrementAndGet();
        throw new CameraException("no camera driver");
    };

    @BeforeEach
    void renderer() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("opens nothing while it is built, and its view says there is no camera")
    void idle() {
        try (var card = new CardSession(new CameraCard(noCameras))) {
            assertEquals(1, asked.get(), "the system was not asked as the card mounted");
            assertEquals("No cameras listed: no camera driver. The test card still shows.", card.text("camera-note"));
            assertNull(card.widget("camera-picture", CameraView.class).camera());
            assertEquals("stopped", card.text("camera-status"));
            assertEquals("–", card.text("camera-mode"));
            assertTrue(card.widget("camera-stop", Button.class).disabled());
        }
    }

    @Test
    @DisplayName("shows the test card, taking pictures as it is advanced")
    void testCard() {
        try (var card = new CardSession(new CameraCard(noCameras))) {
            card.session.click("camera-start");

            var view = card.widget("camera-picture", CameraView.class);
            var camera = assertInstanceOf(SyntheticCamera.class, view.camera());
            assertEquals(new CameraStatus.Active(), camera.status());
            assertEquals("active", card.text("camera-status"));
            assertEquals("640×360 at 30.0 fps", card.text("camera-mode"));
            assertEquals("Test card, faces the user", card.text("camera-info"));
            assertEquals("2: 2 uncompressed, 0 Motion JPEG; up to 1280×720", card.text("camera-modes"));
            assertEquals("none: the test card is painted in Java", card.text("camera-driver"));

            card.session.advance(Duration.ofMillis(330));
            assertTrue(camera.frames() >= 9, "the ticker took " + camera.frames() + " pictures");
            assertTrue(camera.shownPicture().isPresent(), "no picture to show");
            assertEquals(1, asked.get());
        }
    }

    @Test
    @DisplayName("closes the camera on Stop, and the view goes back to no camera")
    void stop() {
        try (var card = new CardSession(new CameraCard(noCameras))) {
            card.session.click("camera-start");
            var camera = card.widget("camera-picture", CameraView.class).camera();
            card.session.frame();
            card.session.click("camera-stop");

            assertInstanceOf(CameraStatus.Closed.class, camera.status());
            assertNull(card.widget("camera-picture", CameraView.class).camera());
            assertEquals("Stopped.", card.text("camera-note"));
        }
    }

    @Test
    @DisplayName("mirrors as the picker says")
    void mirror() {
        try (var card = new CardSession(new CameraCard(noCameras))) {
            assertEquals(
                    Mirror.AUTO, card.widget("camera-picture", CameraView.class).mirror());

            Objects.requireNonNull(card.widget("camera-mirror", Select.class).onChange())
                    .accept("off");
            card.session.frame();

            assertEquals(
                    Mirror.OFF, card.widget("camera-picture", CameraView.class).mirror());
        }
    }

    @Test
    @DisplayName("says why when the system's cameras do not open, and the test card still shows")
    void noCameras() {
        try (var card = new CardSession(new CameraCard(noCameras))) {
            card.session.click("camera-list");

            assertEquals(2, asked.get());
            assertEquals("No cameras listed: no camera driver. The test card still shows.", card.text("camera-note"));

            card.session.click("camera-start");
            var state = card.state(CameraCard.ID, CameraCard.CameraState.class);
            assertInstanceOf(SyntheticCamera.class, state.camera());
            assertSame(
                    state.camera(),
                    card.widget("camera-picture", CameraView.class).camera());
        }
    }

    @Test
    @DisplayName("offers the camera's modes, and opens in the one picked")
    void modes() {
        try (var card = new CardSession(new CameraCard(noCameras))) {
            var picker = card.widget("camera-mode-picker", Select.class);
            assertEquals("call", picker.value());

            pick(card, "camera-mode-picker", "mode-1");
            card.session.click("camera-start");

            var camera = Objects.requireNonNull(
                    card.widget("camera-picture", CameraView.class).camera());
            assertEquals(CameraFormat.of(1280, 720, 30), camera.format());
            assertEquals(
                    "mode-1", card.widget("camera-mode-picker", Select.class).value());

            pick(card, "camera-mode-picker", "call");
            var reopened = Objects.requireNonNull(
                    card.widget("camera-picture", CameraView.class).camera());
            assertEquals(CameraFormat.of(640, 360, 30), reopened.format(), "a running camera reopens in the mode");
            assertInstanceOf(CameraStatus.Closed.class, camera.status());
        }
    }

    @Test
    @DisplayName("records a clip of the test card, plays it back to the end, and drops it on Discard")
    void clip() {
        try (var card = new CardSession(new CameraCard(noCameras))) {
            var state = card.state(CameraCard.ID, CameraCard.CameraState.class);
            assertTrue(card.widget("camera-clip-record", Button.class).disabled(), "a clip of no camera");

            card.session.click("camera-start");
            card.session.click("camera-clip-record");
            card.session.advance(Duration.ofMillis(330));
            card.session.click("camera-clip-stop");

            var clip = Objects.requireNonNull(state.clip());
            assertTrue(clip.pictures().size() >= 9, clip.pictures().size() + " pictures");
            assertTrue(card.text("camera-clip").contains("pictures of 640×360"), card.text("camera-clip"));
            assertSame(
                    clip.pictures().getFirst().picture(),
                    state.playback().shownPicture().orElseThrow());

            card.session.click("camera-clip-play");
            card.session.advance(Duration.ofMillis(150));
            var showing = state.playback().shownPicture().orElseThrow();
            assertSame(
                    clip.pictures()
                            .get(clip.indexAt(Duration.ofMillis(132).toNanos()))
                            .picture(),
                    showing);

            card.session.advance(Duration.ofSeconds(1));
            assertEquals("Played to the end.", state.clipMessage());
            assertSame(
                    clip.pictures().getLast().picture(),
                    state.playback().shownPicture().orElseThrow());

            card.session.click("camera-clip-discard");
            assertNull(state.clip());
            assertTrue(state.playback().shownPicture().isEmpty());
        }
    }

    @Test
    @DisplayName("draws the test card's modes, what is open, and a clip kept")
    void golden() {
        // The pictures themselves are drawn by the media natives, which this
        // module's tests pin away, so they are black here; the picture pins
        // the card around them.
        try (var scene = new ShowcaseScene();
                var fonts = Fonts.bundled()) {
            GoldenImage.assertMatchesAtOneScale("capture-camera-test-card", 640, 1500, 1.0f, (size, scale) -> {
                try (var session = Offscreen.of(size)
                        .scale(scale)
                        .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                        .fonts(fonts)
                        .session(new Column(List.of(new CameraCard(noCameras)), Attributes.NONE.id("root")))) {
                    session.frame();
                    Objects.requireNonNull(CardSession.widget(session, "camera-mode-picker", Select.class)
                                    .onChange())
                            .accept("mode-1");
                    session.frame();
                    session.click("camera-start");
                    session.click("camera-clip-record");
                    session.advance(Duration.ofMillis(500));
                    session.click("camera-clip-stop");
                    session.advance(Duration.ofMillis(400));
                    return session.frame();
                }
            });
        }
    }

    @Test
    @DisplayName("shows metrics of the open camera: how many pictures arrived, what they are, and none dropped")
    void metrics() {
        try (var card = new CardSession(new CameraCard(noCameras))) {
            assertEquals("–", card.texts("camera-metrics").get(1));

            card.session.click("camera-start");
            // Past the metrics' first tick, which is when the card reads them.
            card.session.advance(Duration.ofMillis(600));
            card.session.frame();

            var lines = card.texts("camera-metrics");
            assertTrue(lines.get(1).endsWith("pictures/s of 30.0 in the mode"), lines.get(1));
            assertTrue(lines.get(3).matches("1\\d shown, every .*"), lines.get(3));
            assertEquals("640×360 NV12 planes", lines.get(5));
            assertTrue(lines.get(7).endsWith("MB/s of pictures"), lines.get(7));
            assertEquals("0 frames that did not decode", lines.get(9));
        }
    }

    @Test
    @DisplayName("describes a camera's modes and where it faces, and reads a mode key back")
    void descriptions() {
        var formats = List.of(
                CameraFormat.of(640, 480, 30),
                new CameraFormat(1920, 1080, 30, 1, true),
                new CameraFormat(1280, 720, 30, 1, true));
        var info = new CameraInfo(3, "Desk", CameraPosition.UNKNOWN, formats);

        assertEquals("3: 1 uncompressed, 2 Motion JPEG; up to 1920×1080", CameraCard.CameraState.modesSummary(info));
        assertEquals("Desk, facing unstated", CameraCard.CameraState.describe(info));
        assertEquals(Optional.of(formats.get(2)), CameraCard.CameraState.modeFor(info, "mode-2"));
        assertEquals(Optional.empty(), CameraCard.CameraState.modeFor(info, "mode-3"));
        assertEquals(Optional.empty(), CameraCard.CameraState.modeFor(info, "call"));
        assertEquals(
                "none", CameraCard.CameraState.modesSummary(new CameraInfo(1, "None", CameraPosition.BACK, List.of())));
    }

    @Test
    @DisplayName("says what each driver is, and nothing more for one it does not know")
    void drivers() {
        assertTrue(CameraCard.CameraState.describeDriver("pipewire").contains("plugin serving it"));
        assertEquals(" (the kernel's video devices, directly)", CameraCard.CameraState.describeDriver("v4l2"));
        assertEquals("", CameraCard.CameraState.describeDriver("dummy"));
    }

    private static void pick(CardSession card, String id, String value) {
        Objects.requireNonNull(card.widget(id, Select.class).onChange()).accept(value);
        card.session.frame();
    }
}
