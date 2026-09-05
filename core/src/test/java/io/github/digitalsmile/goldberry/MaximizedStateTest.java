package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessWindow;
import io.github.digitalsmile.goldberry.render.model.DisplayScale;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.render.window.WindowSpec;

/// A window's maximized state, in both directions ([ADR-0252]).
///
/// `Application.maximized()` was a creation flag and nothing else: it became
/// `SDL_WINDOW_MAXIMIZED` and after that nobody involved knew whether the window
/// still was one. There was no `maximize()`, no `restore()`, no `isMaximized()`,
/// and no event plumbed through — so an application could not find out that the
/// **user** maximized it, which is the half a "remember my window size"
/// preference actually needs.
class MaximizedStateTest {

    private HeadlessBackend backend;

    @BeforeEach
    void install() {
        backend = new HeadlessBackend(new DisplayScale(1f));
        GoldberryTestAccess.install(backend);
    }

    @AfterEach
    void shutdown() {
        Goldberry.shutdown();
    }

    private static Window open() {
        return Window.open(WindowSpec.of("maximized", LogicalSize.of(100f, 100f)));
    }

    @Test
    @Timeout(10)
    @DisplayName("a window opens un-maximized, whatever it was asked for later")
    void startsFalse() {
        var window = open();

        assertFalse(window.isMaximized(), "nothing has reported a maximized window yet");
    }

    /// **The decision, asserted.** `isMaximized` answers what the platform last
    /// *reported*, not what was last *asked* — so between the request and the
    /// event it is still false. Maximizing is a request a window manager may
    /// refuse, and a flag set on the way out would be a lie the moment one did.
    @Test
    @Timeout(10)
    @DisplayName("asking does not make it so; the event does")
    void theEventIsTheTruth() {
        var window = open();
        var backendWindow = (HeadlessWindow) backend.windows().getFirst();

        window.maximize();
        assertFalse(window.isMaximized(), "the ask was answered before the platform had agreed");

        backendWindow.requestClose();
        Goldberry.run();

        assertTrue(window.isMaximized(), "the headless window agreed and the state did not follow");
    }

    @Test
    @Timeout(10)
    @DisplayName("and restoring takes it back")
    void restoreTakesItBack() {
        var window = open();
        var backendWindow = (HeadlessWindow) backend.windows().getFirst();

        window.maximize();
        window.restore();
        backendWindow.requestClose();
        Goldberry.run();

        assertFalse(window.isMaximized());
    }

    /// The half that makes the feature worth having: every other route into this
    /// state starts with the application, and this is the one that does not.
    @Test
    @Timeout(10)
    @DisplayName("the user maximizing it is reported, with nobody having asked")
    void theUserCanDoIt() {
        var window = open();
        var backendWindow = (HeadlessWindow) backend.windows().getFirst();

        backendWindow.reportMaximized(true);
        backendWindow.requestClose();
        Goldberry.run();

        assertTrue(window.isMaximized(), "an application cannot remember a size it is never told about");
    }

    /// SDL reports `RESTORED` for un-maximizing *and* un-minimizing, and both
    /// mean the same thing to a window that tracks only the one state.
    @Test
    @Timeout(10)
    @DisplayName("and the user restoring it is too")
    void theUserCanUndoIt() {
        var window = open();
        var backendWindow = (HeadlessWindow) backend.windows().getFirst();

        backendWindow.reportMaximized(true);
        backendWindow.reportMaximized(false);
        backendWindow.requestClose();
        Goldberry.run();

        assertFalse(window.isMaximized());
    }

    @Test
    @Timeout(10)
    @DisplayName("a closed window is asked and does nothing, rather than throwing")
    void closedIsQuiet() {
        var window = open();
        var backendWindow = (HeadlessWindow) backend.windows().getFirst();
        backendWindow.requestClose();
        Goldberry.run();

        // A preference restored during shutdown is exactly this call, and a
        // window state is not worth a crash on the way out.
        window.maximize();
        window.restore();

        assertFalse(window.isMaximized());
    }
}
