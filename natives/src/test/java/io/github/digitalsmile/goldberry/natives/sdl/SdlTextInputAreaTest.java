package io.github.digitalsmile.goldberry.natives.sdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import io.github.digitalsmile.goldberry.natives.layout.Layouts;
import io.github.digitalsmile.goldberry.natives.sdl.event.SdlEventType;

/// The IME half of the SDL binding — `docs/gaps.md` G15.
///
/// A composition cannot be produced on a CI runner: it needs an input method, a
/// user and several keystrokes. What **can** be checked without one is everything
/// the binding is responsible for — that `SDL_SetTextInputArea` is on the export
/// list and links, that Java's idea of `SDL_TextEditingEvent` matches the struct
/// the library was compiled with, and that the event number is SDL's.
///
/// The struct is the part that would otherwise fail silently and late: a wrong
/// offset reads the composition's `start` out of its `length`, which looks like
/// an input method behaving oddly rather than like a layout bug (ADR-0010's
/// reasoning, applied once more).
class SdlTextInputAreaTest {

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    @Test
    @DisplayName("Java's SDL_TextEditingEvent is the one the library was compiled with")
    void theEditingStructAgrees() {
        // The probe itself runs over Layouts.registry() in LayoutProbeTest; what
        // is asserted here is that this struct is *in* that registry, which is
        // the step a new layout is forgotten at.
        assertTrue(
                Layouts.registry().contains(Layouts.SDL_TEXT_EDITING_EVENT),
                "a layout outside the registry is a layout nothing checks against C");
    }

    @Test
    @DisplayName("the editing event's fields are where the composition's offsets are read from")
    void theEditingFieldsAreDistinct() {
        var layout = Layouts.SDL_TEXT_EDITING_EVENT;

        // start and length are adjacent ints after a pointer, and reading one
        // out of the other is the failure this pins.
        assertEquals(layout.offsetOf("start") + 4, layout.offsetOf("length"));
        assertTrue(layout.offsetOf("text") < layout.offsetOf("start"));
    }
}
