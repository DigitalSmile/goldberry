package dev.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.bind.Subscription;
import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.backend.headless.HeadlessWindow;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.window.WindowSpec;

/// A window's fullscreen state, in both directions ([ADR-0473]).
///
/// Maximizing's rules ([ADR-0252]) applied to the other window state a platform
/// owns: the ask is a request, the event is the truth, the user's own button is
/// heard, and the listeners that follow it are told of changes only.
class FullscreenStateTest {

    private HeadlessBackend backend;

    @BeforeEach
    void install() {
        // A headless window still paints into a Blend2D image.
        RendererRequirement.enforce();
        backend = new HeadlessBackend(new DisplayScale(1f));
        GoldberryTestAccess.install(backend);
    }

    @AfterEach
    void shutdown() {
        Goldberry.shutdown();
    }

    private static Window open() {
        return Window.open(WindowSpec.of("fullscreen", LogicalSize.of(100f, 100f)));
    }

    private HeadlessWindow backendWindow() {
        return (HeadlessWindow) backend.windows().getFirst();
    }

    /// Queues a close and runs the loop until it has drained, which delivers
    /// every event queued before it.
    private void drain() {
        backendWindow().requestClose();
        Goldberry.run();
    }

    @Test
    @Timeout(10)
    @DisplayName("a window opens as a window")
    void startsFalse() {
        assertFalse(open().isFullscreen());
    }

    /// The decision, asserted: between the ask and the event the state is still
    /// the old one. On macOS that gap is an animation, several frames long.
    @Test
    @Timeout(10)
    @DisplayName("asking does not make it so; the event does")
    void theEventIsTheTruth() {
        var window = open();

        window.setFullscreen(true);
        assertFalse(window.isFullscreen(), "answered before the platform had agreed");

        drain();
        assertTrue(window.isFullscreen(), "the headless window agreed and the state did not follow");
    }

    @Test
    @Timeout(10)
    @DisplayName("and asking to leave takes it back")
    void leaving() {
        var window = open();

        window.setFullscreen(true);
        window.setFullscreen(false);
        drain();

        assertFalse(window.isFullscreen());
    }

    /// The green button on macOS, a window manager's key on Linux: a change
    /// nobody in this process asked for, which a player's fullscreen overlay has
    /// to follow or it covers a window that is no longer fullscreen.
    @Test
    @Timeout(10)
    @DisplayName("the user's own way in and out is reported, with nobody having asked")
    void theUserCanDoIt() {
        var window = open();
        var heard = new ArrayList<Boolean>();
        window.onFullscreenChanged(heard::add);

        backendWindow().reportFullscreen(true);
        backendWindow().reportFullscreen(false);
        drain();

        assertEquals(List.of(true, false), heard);
        assertFalse(window.isFullscreen());
    }

    @Test
    @Timeout(10)
    @DisplayName("listeners hear changes only, every one of them, until they close")
    void listeners() {
        var window = open();
        var first = new ArrayList<Boolean>();
        var second = new ArrayList<Boolean>();
        var subscription = window.onFullscreenChanged(first::add);
        window.onFullscreenChanged(second::add);

        backendWindow().reportFullscreen(true);
        // Reported twice, as a platform may after a display change: once news.
        backendWindow().reportFullscreen(true);
        drain();

        assertEquals(List.of(true), first);
        assertEquals(List.of(true), second, "a second listener must not replace the first");

        subscription.close();
        subscription.close();
        window.handleFullscreenChanged(false);
        assertEquals(List.of(true), first, "closed, and still told");
        assertEquals(List.of(true, false), second);
    }

    /// A player leaves fullscreen by closing its own subscription from inside
    /// the call that told it the window left.
    @Test
    @Timeout(10)
    @DisplayName("a listener may close its own subscription while being told")
    void closeWhileTold() {
        var window = open();
        var heard = new ArrayList<Boolean>();
        var own = new Subscription[1];
        own[0] = window.onFullscreenChanged(full -> {
            heard.add(full);
            own[0].close();
        });

        window.handleFullscreenChanged(true);
        window.handleFullscreenChanged(false);

        assertEquals(List.of(true), heard);
    }

    @Test
    @Timeout(10)
    @DisplayName("a closed window is asked and does nothing, rather than throwing")
    void closedIsQuiet() {
        var window = open();
        drain();

        window.setFullscreen(true);

        assertFalse(window.isFullscreen());
    }
}
