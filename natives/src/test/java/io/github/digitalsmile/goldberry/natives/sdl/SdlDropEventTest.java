package io.github.digitalsmile.goldberry.natives.sdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.layout.Layouts;
import io.github.digitalsmile.goldberry.natives.sdl.event.SdlEventType;

/// Reading `SDL_DropEvent` out of the buffer — `docs/gaps.md` G35b, [ADR-0330].
///
/// [SdlEventBufferTest]'s method, and its argument: the event is fabricated here
/// because there is no display to drag a file onto, and it still tests what
/// breaks. The offsets come from [Layouts], which the layout probe has already
/// checked against the compiled C, so writing a field where the layout says it is
/// and reading it back through the accessor proves the accessor reads the arm it
/// claims to.
///
/// **The padding is the point.** `windowID`, `x` and `y` are three four-byte
/// fields, so the compiler inserts four bytes before `source` to align it. A
/// layout that counted by hand would read the dropped path out of the middle of a
/// pointer — which does not crash, it just hands back nonsense.
///
/// **No native library, and therefore no skip.** Every offset here is read off
/// the Java [Layouts] declaration and every byte is written into an arena, so
/// nothing in this file loads `libgoldberry`. It used to open with
/// `assumeTrue(NativeLibrary.isAvailable())` anyway, which skipped the whole
/// class on a machine with no superbuild and — because it was an assumption
/// rather than [io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement]
/// — skipped it silently in CI too (ADR-0016).
class SdlDropEventTest {

    private static final long TYPE = Layouts.SDL_COMMON_EVENT.offsetOf("type");
    private static final long DROP_X = Layouts.SDL_DROP_EVENT.offsetOf("x");
    private static final long DROP_Y = Layouts.SDL_DROP_EVENT.offsetOf("y");
    private static final long DROP_SOURCE = Layouts.SDL_DROP_EVENT.offsetOf("source");
    private static final long DROP_DATA = Layouts.SDL_DROP_EVENT.offsetOf("data");

    @Test
    @DisplayName("a dropped file's name and position read back exactly")
    void aDropReadsBack() {
        try (var arena = Arena.ofConfined();
                var buffer = new SdlEventBuffer()) {
            var name = arena.allocateFrom("/home/ds/pictures/wall.png");
            buffer.clear();
            var event = buffer.segment();
            event.set(ValueLayout.JAVA_INT, TYPE, SdlEventType.DROP_FILE.value());
            event.set(ValueLayout.JAVA_FLOAT, DROP_X, 128.5f);
            event.set(ValueLayout.JAVA_FLOAT, DROP_Y, 64.25f);
            event.set(ValueLayout.ADDRESS, DROP_DATA, name);

            assertEquals("/home/ds/pictures/wall.png", buffer.droppedPath());
            assertEquals(128.5f, buffer.dropX());
            assertEquals(64.25f, buffer.dropY());
        }
    }

    @Test
    @DisplayName("the events that carry no file answer with no file rather than with rubbish")
    void aNullNameIsEmpty() {
        try (var buffer = new SdlEventBuffer()) {
            buffer.clear();
            var event = buffer.segment();
            event.set(ValueLayout.JAVA_INT, TYPE, SdlEventType.DROP_COMPLETE.value());
            event.set(ValueLayout.ADDRESS, DROP_DATA, MemorySegment.NULL);

            assertEquals("", buffer.droppedPath());
        }
    }

    @Test
    @DisplayName("dropped text reads back out of the same `data` field")
    void droppedTextReadsBack() {
        try (var arena = Arena.ofConfined();
                var buffer = new SdlEventBuffer()) {
            // One line, because that is what one SDL_EVENT_DROP_TEXT carries: SDL
            // tokenises the payload on \r\n and sends one event per token
            // ([ADR-0408]).
            var text = arena.allocateFrom("https://example.com/x.png");
            buffer.clear();
            var event = buffer.segment();
            event.set(ValueLayout.JAVA_INT, TYPE, SdlEventType.DROP_TEXT.value());
            event.set(ValueLayout.JAVA_FLOAT, DROP_X, 12.5f);
            event.set(ValueLayout.JAVA_FLOAT, DROP_Y, 34.25f);
            event.set(ValueLayout.ADDRESS, DROP_DATA, text);

            assertEquals("https://example.com/x.png", buffer.droppedText());
            assertEquals(12.5f, buffer.dropX());
            assertEquals(34.25f, buffer.dropY());
        }
    }

    /// The two accessors are two names for `SDL_DropEvent.data`, which is what
    /// SDL's header says it is: the file name for one event type and the text for
    /// the other. The test is here so that a later change that gave text its own
    /// field would have to say so.
    @Test
    @DisplayName("`droppedText` and `droppedPath` read the same field")
    void textAndPathAreOneField() {
        try (var arena = Arena.ofConfined();
                var buffer = new SdlEventBuffer()) {
            var data = arena.allocateFrom("either-reading");
            buffer.clear();
            buffer.segment().set(ValueLayout.ADDRESS, DROP_DATA, data);

            assertEquals(buffer.droppedPath(), buffer.droppedText());
        }
    }

    /// `SDL_EVENT_DROP_TEXT` is 0x1001 — the second enumerator of SDL's drop
    /// block, wedged between `DROP_FILE` and `DROP_BEGIN`, which is exactly the
    /// arrangement that makes a hand-copied number worth checking. The compiled
    /// library's own answer is compared by `LayoutVerificationTest`; this only
    /// pins that nothing here renumbered the neighbours.
    @Test
    @DisplayName("the drop event numbers are SDL's, in SDL's order")
    void dropEventNumbers() {
        assertEquals(0x1000, SdlEventType.DROP_FILE.value());
        assertEquals(0x1001, SdlEventType.DROP_TEXT.value());
        assertEquals(0x1002, SdlEventType.DROP_BEGIN.value());
        assertEquals(0x1003, SdlEventType.DROP_COMPLETE.value());
        assertEquals(0x1004, SdlEventType.DROP_POSITION.value());
        assertEquals("SDL_EVENT_DROP_TEXT", SdlEventType.DROP_TEXT.nativeName());
    }

    /// `source` and `data` are two different pointers, four bytes of padding
    /// after two floats. This is the assertion that the padding is really there:
    /// a layout that had left it out would put `data` where `source` is.
    @Test
    @DisplayName("the path comes from `data` and not from `source`")
    void dataIsNotSource() {
        try (var arena = Arena.ofConfined();
                var buffer = new SdlEventBuffer()) {
            var from = arena.allocateFrom("some-other-application");
            var name = arena.allocateFrom("/tmp/dropped.qoi");
            buffer.clear();
            var event = buffer.segment();
            event.set(ValueLayout.JAVA_INT, TYPE, SdlEventType.DROP_FILE.value());
            event.set(ValueLayout.ADDRESS, DROP_SOURCE, from);
            event.set(ValueLayout.ADDRESS, DROP_DATA, name);

            assertNotEquals(DROP_SOURCE, DROP_DATA);
            assertEquals("/tmp/dropped.qoi", buffer.droppedPath());
        }
    }
}
