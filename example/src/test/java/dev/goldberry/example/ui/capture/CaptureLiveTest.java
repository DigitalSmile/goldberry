package dev.goldberry.example.ui.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.Goldberry;
import dev.goldberry.GoldberryTestAccess;
import dev.goldberry.RendererRequirement;
import dev.goldberry.camera.CameraException;
import dev.goldberry.camera.CameraStatus;
import dev.goldberry.camera.Cameras;
import dev.goldberry.media.PlaybackState;
import dev.goldberry.mic.MicrophoneException;
import dev.goldberry.mic.Microphones;
import dev.goldberry.mic.Recording;
import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.event.UiExecutor;
import dev.goldberry.widgets.controls.select.Select;

/// The Capture screen's cards over the system's own devices, where there are
/// any: listed, chosen, started, heard or seen, and stopped, as a person would
/// in the window. Skips on a machine with no microphone or no camera, which CI
/// is, and on macOS, where opening either waits for a person to allow it.
///
/// The runtime is installed in the test body rather than by the extension,
/// because the test runs on a thread of its own so that a device that hangs
/// on close fails it rather than the build, and the UI thread is the one that
/// installed the runtime.
@DisplayName("the Capture screen, on this machine's devices")
class CaptureLiveTest {

    @BeforeEach
    void notOnMacOs() {
        RendererRequirement.enforce();
        assumeFalse(
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac"),
                "macOS asks the user first");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @DisplayName("records the system's default microphone, keeps and plays a clip of it, and stops")
    void microphone() {
        GoldberryTestAccess.install(new HeadlessBackend());
        try (var card = new CardSession(new MicrophoneCard(Microphones::open))) {
            var state = card.state(MicrophoneCard.ID, MicrophoneCard.MicrophoneState.class);
            assumeFalse(state.devices().isEmpty(), state.message());

            pick(card, "microphone-source", "device-0");
            card.session.click("microphone-start");
            assumeTrue(state.source() instanceof Recording, state.message());
            var recording = (Recording) state.source();
            until(() -> recording.position() >= 4_800);

            card.session.frame();
            assertTrue(card.text("microphone-status").startsWith("recording"), card.text("microphone-status"));

            card.session.click("microphone-clip-record");
            var from = recording.position();
            until(() -> recording.position() - from >= 24_000);
            card.session.click("microphone-clip-stop");
            var clip = Objects.requireNonNull(state.clip(), state.clipMessage());
            assertTrue(clip.duration().toMillis() >= 400, clip.duration().toString());

            card.session.click("microphone-clip-play");
            var player = Objects.requireNonNull(state.player(), state.clipMessage());
            until(() -> player.status().state() == PlaybackState.PLAYING
                    || player.status().state() == PlaybackState.ENDED);
            assertTrue(
                    player.status().error().isEmpty(),
                    () -> player.status().error().toString());

            card.session.click("microphone-stop");
            assertEquals("stopped", card.text("microphone-status"));
        } catch (MicrophoneException e) {
            assumeTrue(false, "no microphone opens here: " + e.getMessage());
        } finally {
            GoldberryTestAccess.shutdown();
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @DisplayName("opens the first camera for a call, keeps and plays a clip of it, and closes it")
    void camera() {
        GoldberryTestAccess.install(new HeadlessBackend());
        try (var card = new CardSession(new CameraCard(Cameras::open))) {
            var state = card.state(CameraCard.ID, CameraCard.CameraState.class);
            assumeFalse(state.devices().isEmpty(), state.message());

            pick(card, "camera-source", "device-0");
            card.session.click("camera-start");
            var camera = state.camera();
            assumeTrue(camera != null, state.message());
            until(() -> camera.status() instanceof CameraStatus.Active
                    && camera.shownPicture().isPresent());

            card.session.frame();
            assertEquals("active", card.text("camera-status"));
            assertFalse(card.text("camera-driver").startsWith("not named"), card.text("camera-driver"));
            assertEquals(camera.format().toString(), card.text("camera-mode"));

            card.session.click("camera-clip-record");
            var started = System.nanoTime();
            until(() -> System.nanoTime() - started >= 1_000_000_000L);
            card.session.click("camera-clip-stop");
            var clip = Objects.requireNonNull(state.clip(), state.clipMessage());
            assertTrue(clip.pictures().size() >= 5, clip.pictures().size() + " pictures in a second");

            card.session.click("camera-clip-play");
            card.session.advance(clip.duration().plusMillis(100));
            assertEquals("Played to the end.", state.clipMessage());

            card.session.click("camera-stop");
            assertTrue(
                    camera.status() instanceof CameraStatus.Closed,
                    camera.status().toString());
        } catch (CameraException e) {
            assumeTrue(false, "no camera opens here: " + e.getMessage());
        } finally {
            GoldberryTestAccess.shutdown();
        }
    }

    /// Chooses `value` in the picker with this id, as a pick in the window does.
    private static void pick(CardSession card, String id, String value) {
        Objects.requireNonNull(card.widget(id, Select.class).onChange()).accept(value);
        card.session.frame();
    }

    /// Runs what the devices posted to the UI thread until `done`; the test's
    /// timeout bounds the wait.
    private static void until(BooleanSupplier done) {
        while (!done.getAsBoolean()) {
            ((UiExecutor) Goldberry.ui()).drain();
            LockSupport.parkNanos(Duration.ofMillis(20).toNanos());
        }
        ((UiExecutor) Goldberry.ui()).drain();
    }
}
