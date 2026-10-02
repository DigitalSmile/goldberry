package dev.goldberry.natives.sdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.NativeLibraryRequirement;
import dev.goldberry.natives.sdl.window.SdlFlashOperation;
import dev.goldberry.natives.sdl.window.SdlWindowFlag;

/// The displays, attention and ownership calls through the real `libgoldberry`.
///
/// What a display looks like depends on the machine, so what is asserted is the
/// binding: the symbols are exported and link, the display list SDL allocates is
/// read and freed, and each call answers rather than throws. A run with no video
/// subsystem skips itself, as [SdlClipboardTest] does.
class SdlDisplayTest {

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    @AfterEach
    void shutDownSdl() {
        Sdl.get().quit();
        Sdl.get().clearError();
    }

    @Test
    @DisplayName("lists at least one display, each with bounds, usable bounds inside them and a scale")
    void listsDisplays() {
        requireVideo();

        var displays = SdlVideo.get().displays();

        assertFalse(displays.isEmpty(), "a video subsystem with no display at all");
        for (var display : displays) {
            assertNotNull(display.name());
            assertTrue(display.bounds().width() > 0 && display.bounds().height() > 0, display::toString);
            assertTrue(display.usableBounds().width() <= display.bounds().width(), display::toString);
            assertTrue(display.scale() > 0, display::toString);
        }
        // Read twice: the first read freed SDL's array, and a double free or a
        // use-after-free shows here rather than at some later allocation.
        assertEquals(displays, SdlVideo.get().displays());
    }

    @Test
    @DisplayName("a hidden window is on a listed display, and asking for attention answers rather than throws")
    void windowCalls() {
        requireVideo();
        var video = SdlVideo.get();
        var window = video.createWindow("display test", 200, 100, EnumSet.of(SdlWindowFlag.HIDDEN));
        try {
            var id = video.displayForWindow(window);
            if (id != 0) {
                assertTrue(
                        video.displays().stream().anyMatch(display -> display.id() == id),
                        "the window's display is not one SDL lists");
            }
            // Answers on every driver: true where the platform flashes, false
            // where SDL says it cannot.
            video.flashWindow(window, SdlFlashOperation.BRIEFLY);
            video.flashWindow(window, SdlFlashOperation.CANCEL);
            video.moveWindow(window, 10, 10);
            video.raiseWindow(window);
        } finally {
            video.destroyWindow(window);
        }
    }

    @Test
    @DisplayName("a window can be made to belong to another, made modal, and let go of again")
    void parentAndModal() {
        requireVideo();
        var driver = Sdl.get().videoDriver();
        Assumptions.assumeTrue(
                Set.of("x11", "wayland", "windows", "cocoa").contains(driver),
                () -> driver + " has no window parenting to test");
        var video = SdlVideo.get();
        var parent = video.createWindow("parent", 200, 100, EnumSet.of(SdlWindowFlag.HIDDEN));
        var child = video.createWindow("child", 100, 50, EnumSet.of(SdlWindowFlag.HIDDEN));
        try {
            assertTrue(video.setWindowParent(child, parent), Sdl.get().lastError());
            assertTrue(video.setWindowModal(child, true), Sdl.get().lastError());
            // SDL refuses to re-parent a modal window, so the order of the
            // undoing is part of what is pinned.
            assertTrue(video.setWindowModal(child, false), Sdl.get().lastError());
            assertTrue(video.setWindowParent(child, null), Sdl.get().lastError());
        } finally {
            video.destroyWindow(child);
            video.destroyWindow(parent);
        }
    }

    @Test
    @DisplayName("a window with no parent cannot be made modal")
    void modalNeedsAParent() {
        requireVideo();
        var video = SdlVideo.get();
        var window = video.createWindow("orphan", 100, 50, EnumSet.of(SdlWindowFlag.HIDDEN));
        try {
            assertFalse(video.setWindowModal(window, true));
        } finally {
            video.destroyWindow(window);
        }
    }

    @Test
    @DisplayName("the flash operations are SDL's numbers")
    void flashNumbers() {
        assertEquals(0, SdlFlashOperation.CANCEL.value());
        assertEquals(1, SdlFlashOperation.BRIEFLY.value());
        assertEquals(2, SdlFlashOperation.UNTIL_FOCUSED.value());
    }

    /// Starts SDL's video subsystem, or aborts the test with the reason it could
    /// not.
    private static void requireVideo() {
        try {
            Sdl.get().initialize(Set.of(SdlSubsystem.VIDEO));
        } catch (SdlException e) {
            Assumptions.abort("SDL has no video subsystem on this machine: " + e.getMessage());
        }
    }
}
