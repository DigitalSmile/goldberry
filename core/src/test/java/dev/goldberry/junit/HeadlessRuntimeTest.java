package dev.goldberry.junit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import dev.goldberry.Goldberry;
import dev.goldberry.GoldberryTestAccess;
import dev.goldberry.render.backend.headless.HeadlessBackend;

/// Two tests rather than one on purpose: the second can only install its runtime
/// if the first's was shut down, so running both is what checks the teardown.
@DisplayName("a test under HeadlessRuntime")
@ExtendWith(HeadlessRuntime.class)
class HeadlessRuntimeTest {

    @Test
    @DisplayName("finds a runtime up, on this thread, with no window opened and no library loaded")
    void aRuntimeIsUp() {
        assertRuntimeUp();
    }

    @Test
    @DisplayName("and the next test finds its own, so the last one's was taken down")
    void andTheNextOneToo() {
        assertRuntimeUp();
    }

    private static void assertRuntimeUp() {
        // False with no runtime: `isUiThread` asks whether one is started before
        // it asks anything of it.
        assertTrue(Goldberry.isUiThread(), "the installing thread is the UI thread");
        var queued = new AtomicBoolean();
        Goldberry.ui().execute(() -> queued.set(true));
        assertFalse(queued.get(), "nothing pumps the loop here, so a task is queued and not run");
        assertThrows(
                IllegalStateException.class,
                () -> GoldberryTestAccess.install(new HeadlessBackend()),
                "a second installation is refused while the extension's runtime is up");
    }
}
