package io.github.digitalsmile.goldberry.natives.sdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.layout.Layouts;
import java.lang.foreign.ValueLayout;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/// Reading an event out of the buffer SDL writes into.
///
/// The event is **fabricated here** rather than pumped out of SDL, which is the
/// only way to test this without a display — and it still tests the thing that
/// breaks: the offsets come from [Layouts], which the layout probe has already
/// checked against the compiled C, so writing a field at the offset the layout
/// names and reading it back through the accessor proves the accessor reads the
/// arm it claims to.
class SdlEventBufferTest {

    private static final long WHEEL_X = Layouts.SDL_MOUSE_WHEEL_EVENT.offsetOf("x");
    private static final long WHEEL_Y = Layouts.SDL_MOUSE_WHEEL_EVENT.offsetOf("y");
    private static final long WHEEL_DIRECTION = Layouts.SDL_MOUSE_WHEEL_EVENT.offsetOf("direction");
    private static final long WHEEL_MOUSE_X = Layouts.SDL_MOUSE_WHEEL_EVENT.offsetOf("mouse_x");
    private static final long WHEEL_MOUSE_Y = Layouts.SDL_MOUSE_WHEEL_EVENT.offsetOf("mouse_y");
    private static final long TYPE = Layouts.SDL_COMMON_EVENT.offsetOf("type");

    @BeforeAll
    static void requireLibrary() {
        Assumptions.assumeTrue(NativeLibrary.isAvailable(),
                "no libgoldberry to read struct offsets from");
    }

    /// Fills the buffer with a wheel event and hands it back.
    private static void writeWheel(SdlEventBuffer buffer, float x, float y, int direction) {
        buffer.clear();
        var event = buffer.segment();
        event.set(ValueLayout.JAVA_INT, TYPE, SdlEventType.MOUSE_WHEEL.value());
        event.set(ValueLayout.JAVA_FLOAT, WHEEL_X, x);
        event.set(ValueLayout.JAVA_FLOAT, WHEEL_Y, y);
        event.set(ValueLayout.JAVA_INT, WHEEL_DIRECTION, direction);
        event.set(ValueLayout.JAVA_FLOAT, WHEEL_MOUSE_X, 120f);
        event.set(ValueLayout.JAVA_FLOAT, WHEEL_MOUSE_Y, 64f);
    }

    @Test
    @DisplayName("a normal wheel event reads back exactly what SDL wrote")
    void normalDirection() {
        try (var buffer = new SdlEventBuffer()) {
            writeWheel(buffer, -1.5f, 3f, SdlWheelDirection.NORMAL.value());

            assertEquals(-1.5f, buffer.wheelX());
            assertEquals(3f, buffer.wheelY());
            assertEquals(120f, buffer.wheelPointerX());
            assertEquals(64f, buffer.wheelPointerY());
        }
    }

    @Test
    @DisplayName("a flipped wheel event is un-flipped on the way out")
    void flippedDirection() {
        try (var buffer = new SdlEventBuffer()) {
            writeWheel(buffer, -1.5f, 3f, SdlWheelDirection.FLIPPED.value());

            // "Natural scrolling" is a platform preference, and a toolkit whose
            // scroll direction depended on it would be broken for exactly the
            // users who turned it on.
            assertEquals(1.5f, buffer.wheelX());
            assertEquals(-3f, buffer.wheelY());

            // The position is not a delta and is never inverted.
            assertEquals(120f, buffer.wheelPointerX());
            assertEquals(64f, buffer.wheelPointerY());
        }
    }

    @Test
    @DisplayName("a fractional delta survives, because a touchpad only sends those")
    void fractionalDeltas() {
        try (var buffer = new SdlEventBuffer()) {
            writeWheel(buffer, 0f, 0.125f, SdlWheelDirection.NORMAL.value());
            assertEquals(0.125f, buffer.wheelY());
        }
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "1, -1", "7, 1"})
    @DisplayName("an unrecognized direction is treated as normal rather than inverted")
    void unknownDirectionIsNormal(int direction, float expectedSign) {
        assertEquals(expectedSign, SdlWheelDirection.sign(direction));
    }

    @Test
    @DisplayName("a wheel event's position is at neither offset the other arms use")
    void armsAreDistinct() {
        // This is the whole reason wheelPointerX exists rather than reusing
        // pointerX. On a wheel event, the offset motion keeps its position at
        // holds the *delta*, and the offset the button arm uses holds nothing --
        // so reading a wheel's position through either accessor gives a plausible
        // float that is not a coordinate.
        var motionX = Layouts.SDL_MOUSE_MOTION_EVENT.offsetOf("x");
        var buttonX = Layouts.SDL_MOUSE_BUTTON_EVENT.offsetOf("x");
        assertTrue(WHEEL_MOUSE_X != motionX && WHEEL_MOUSE_X != buttonX,
                "mouse_x at " + WHEEL_MOUSE_X + " would be shadowed by motion's " + motionX
                        + " or the button arm's " + buttonX);
        assertEquals(WHEEL_Y, motionX,
                "SDL's layout puts the wheel's vertical delta exactly where motion"
                        + " puts its x, which is the confusion this accessor avoids");
    }
}
