package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.backend.DisplayScale;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.WindowSpec;
import io.github.digitalsmile.goldberry.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.backend.headless.HeadlessWindow;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// What the frame loop tells the platform changed.
///
/// ADR-0046 established that the damage list is not advisory: SDL uploads the
/// enclosing span of it and nothing else, which is worth about a millisecond a
/// frame at 960×640. Nothing asserted on it before, so the frame loop could have
/// begun sending a wrong -- or an empty -- rectangle and every test would still
/// have passed while the window quietly stopped updating.
///
/// These pin the whole-window damage the loop sends today. When damage tracking
/// lands, they are the tests that have to change on purpose.
class WindowDamageTest {

    /// 150%: damage is in physical pixels, so a scale of 1 would hide a frame
    /// loop that reported logical ones.
    private static final DisplayScale SCALE = new DisplayScale(1.5f);

    private HeadlessBackend backend;

    @BeforeEach
    void installBackend() {
        backend = new HeadlessBackend(SCALE);
        GoldberryRuntime.install(backend);
    }

    @AfterEach
    void shutDown() {
        GoldberryRuntime.shutdown();
    }

    @Test
    @Timeout(10)
    @DisplayName("a painted frame reports the whole window as damaged, in physical pixels")
    void damageCoversTheWholeFrame() {
        var presented = runUntilPainted(LogicalSize.of(200f, 100f), 1, window -> {});

        var damage = presented.lastDamage();
        assertEquals(1, damage.size(), "one whole-window rectangle, not a list of pieces");

        var rect = damage.getFirst();
        assertEquals(0, rect.x());
        assertEquals(0, rect.y());
        // Spelled out rather than compared against physicalSize(), which would
        // pass just as happily if both were wrong: 200x100 logical at 150% is
        // 300x150, and a frame loop that forgot to scale would say 200x100.
        assertEquals(300, rect.width());
        assertEquals(150, rect.height());
    }

    @Test
    @Timeout(10)
    @DisplayName("the damage reported fits inside the frame that was presented")
    void damageFitsThePresentedFrame() {
        var presented = runUntilPainted(LogicalSize.of(320f, 240f), 1, window -> {});

        var frame = presented.lastFrame().orElseThrow();
        for (var rect : presented.lastDamage()) {
            assertTrue(
                    rect.fitsWithin(frame.size()),
                    () -> rect + " falls outside the presented " + frame.size());
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("damage follows a resize rather than staying at the old size")
    void damageFollowsAResize() {
        // Resize on the first frame, then let a second one paint against the new
        // size. The damage from that second frame is what is under test.
        var presented = runUntilPainted(
                LogicalSize.of(200f, 100f),
                2,
                window -> window.resizeTo(LogicalSize.of(400f, 300f)));

        // 400x300 logical at 150%. Spelled out so the assertion still means
        // something if the resize silently did not happen -- comparing against
        // physicalSize() would then match the stale 300x150 and pass.
        var rect = presented.lastDamage().getFirst();
        assertEquals(600, rect.width(), "damage kept the pre-resize width");
        assertEquals(450, rect.height(), "damage kept the pre-resize height");
    }

    /// Opens a window, runs the real event loop until it has painted `frames`
    /// times, and hands back the backend window holding what was presented.
    ///
    /// `onFirstFrame` runs inside the first paint, which is where a resize has to
    /// come from: the loop is single-threaded and the UI thread is inside the
    /// painter.
    private HeadlessWindow runUntilPainted(
            LogicalSize size, int frames, Consumer<HeadlessWindow> onFirstFrame) {
        var window = Window.open(WindowSpec.of("damage", size));
        var backendWindow = (HeadlessWindow) backend.windows().getFirst();
        var painted = new int[1];

        window.onPaint(frame -> {
            frame.fill(0xFF204060);
            painted[0]++;
            if (painted[0] == 1) {
                onFirstFrame.accept(backendWindow);
            }
            if (painted[0] >= frames) {
                Goldberry.stop();
            } else {
                window.repaint();
            }
        });

        Goldberry.run();

        assertTrue(painted[0] >= frames, () -> "painted only " + painted[0] + " frame(s)");
        assertTrue(backendWindow.presentCount() > 0, "the window never presented a frame");
        return backendWindow;
    }
}
