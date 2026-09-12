package io.github.digitalsmile.goldberry.natives.sdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import io.github.digitalsmile.goldberry.natives.sdl.dialog.SdlDialogKind;
import io.github.digitalsmile.goldberry.natives.sdl.dialog.SdlFileDialogCallback;
import io.github.digitalsmile.goldberry.natives.sdl.dialog.SdlFileFilter;

/// File dialogs through the real `libgoldberry`.
///
/// **A dialog cannot be put up on a CI runner and answered by nobody**, so what
/// is exercised here is the path that does not need a human: SDL's own refusal.
/// Pointing `SDL_HINT_FILE_DIALOG_DRIVER` at a driver that does not exist makes
/// `SDL_ShowOpenFileDialog` fail *through the callback* — a NULL file list,
/// synchronously — which is the same upcall a real answer arrives on and the
/// only branch of it that can be driven without a desktop.
///
/// That covers the three things this wrapper is responsible for: the symbols are
/// reachable and the calling convention is right, NULL is translated to
/// [SdlFileDialogCallback#failed] rather than to an empty choice, and the arena
/// the filters live in is released when the answer arrives rather than leaking
/// one per export.
class SdlFileDialogsTest {

    /// `SDL_HINT_FILE_DIALOG_DRIVER`. The two values SDL accepts on Linux are
    /// `portal` and `zenity`; anything else makes it report that it cannot.
    private static final String DRIVER_HINT = "SDL_FILE_DIALOG_DRIVER";

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
    @DisplayName("binds every dialog symbol on the export list")
    void bindsItsSymbols() {
        // No SDL_Init: looking a symbol up is a link-time question, and this is
        // the test that fails first and most clearly when goldberry.symbols and
        // the binding disagree.
        assertNotNull(SdlFileDialogs.get());
    }

    @Test
    @DisplayName("translates a NULL file list into a failure, with SDL's own words")
    void reportsRefusalAsAFailure() {
        var dialogs = withNoDriver();
        var outcome = new AtomicReference<String>();

        dialogs.show(
                SdlDialogKind.OPEN_FILE,
                null,
                List.of(SdlFileFilter.of("Images", "png", "jpg")),
                null,
                false,
                record(outcome));

        assertNotNull(outcome.get(), "SDL refuses synchronously when it has no driver, so the answer is already in");
        assertTrue(outcome.get().startsWith("failed:"), "a NULL file list is an error, not a cancel: " + outcome.get());
        assertFalse(outcome.get().length() == "failed:".length(), "the failure should carry SDL's own message");
    }

    @Test
    @DisplayName("releases the arena a refused dialog was holding")
    void releasesTheArena() {
        var dialogs = withNoDriver();
        var outcome = new AtomicReference<String>();

        dialogs.show(
                SdlDialogKind.SAVE_FILE, null, List.of(SdlFileFilter.all("All files")), "/tmp", false, record(outcome));

        assertNotNull(outcome.get());
        assertEquals(0, dialogs.pendingRequests(), "a dialog that has answered must not still be holding its filters");
    }

    @Test
    @DisplayName("answers a folder dialog too, which takes no filters at all")
    void answersAFolderDialog() {
        var dialogs = withNoDriver();
        var outcome = new AtomicReference<String>();

        dialogs.show(SdlDialogKind.OPEN_FOLDER, null, List.of(), "/tmp", true, record(outcome));

        assertNotNull(outcome.get());
        assertEquals(0, dialogs.pendingRequests());
    }

    /// SDL with a file-dialog driver it cannot find.
    ///
    /// The hint is set before the first dialog is asked for, which matters: SDL
    /// caches the driver it detected and only re-reads the hint through a
    /// callback it installs on that first attempt.
    private static SdlFileDialogs withNoDriver() {
        assertTrue(Sdl.get().setHint(DRIVER_HINT, "goldberry-has-no-such-driver"));
        return SdlFileDialogs.get();
    }

    private static SdlFileDialogCallback record(AtomicReference<String> outcome) {
        return new SdlFileDialogCallback() {

            @Override
            public void chosen(List<String> paths, int filterIndex) {
                outcome.set("chosen:" + paths + "@" + filterIndex);
            }

            @Override
            public void cancelled() {
                outcome.set("cancelled");
            }

            @Override
            public void failed(String error) {
                outcome.set("failed:" + error);
            }
        };
    }
}
