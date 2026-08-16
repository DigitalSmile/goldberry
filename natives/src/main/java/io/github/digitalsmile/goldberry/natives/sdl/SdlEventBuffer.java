package io.github.digitalsmile.goldberry.natives.sdl;

import io.github.digitalsmile.goldberry.natives.layout.Layouts;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/// Scratch space for one `SDL_Event`, reused for the life of the event loop.
///
/// SDL fills a caller-provided union. Allocating 128 bytes per event would put an
/// allocation in the hottest loop in the toolkit for a value that is dead by the
/// time the next event arrives, so there is one of these per loop and it is
/// overwritten in place.
///
/// Reading is by arm: [#type()] says which one is valid, and asking for
/// [#windowId()] on an event that is not a window event reads whatever else
/// happens to be at that offset. The backend switches on the type first.
///
/// Confined to the thread that created it, like everything else in the event
/// loop.
public final class SdlEventBuffer implements AutoCloseable {

    private static final long TYPE_OFFSET =
            Layouts.SDL_COMMON_EVENT.offsetOf("type");
    private static final long WINDOW_ID_OFFSET =
            Layouts.SDL_WINDOW_EVENT.offsetOf("windowID");
    private static final long DATA1_OFFSET =
            Layouts.SDL_WINDOW_EVENT.offsetOf("data1");
    private static final long MOTION_X_OFFSET =
            Layouts.SDL_MOUSE_MOTION_EVENT.offsetOf("x");
    private static final long MOTION_Y_OFFSET =
            Layouts.SDL_MOUSE_MOTION_EVENT.offsetOf("y");
    private static final long BUTTON_X_OFFSET =
            Layouts.SDL_MOUSE_BUTTON_EVENT.offsetOf("x");
    private static final long BUTTON_Y_OFFSET =
            Layouts.SDL_MOUSE_BUTTON_EVENT.offsetOf("y");
    private static final long BUTTON_INDEX_OFFSET =
            Layouts.SDL_MOUSE_BUTTON_EVENT.offsetOf("button");
    private static final long BUTTON_CLICKS_OFFSET =
            Layouts.SDL_MOUSE_BUTTON_EVENT.offsetOf("clicks");
    private static final long WHEEL_X_OFFSET =
            Layouts.SDL_MOUSE_WHEEL_EVENT.offsetOf("x");
    private static final long WHEEL_Y_OFFSET =
            Layouts.SDL_MOUSE_WHEEL_EVENT.offsetOf("y");
    private static final long WHEEL_DIRECTION_OFFSET =
            Layouts.SDL_MOUSE_WHEEL_EVENT.offsetOf("direction");
    private static final long WHEEL_MOUSE_X_OFFSET =
            Layouts.SDL_MOUSE_WHEEL_EVENT.offsetOf("mouse_x");
    private static final long WHEEL_MOUSE_Y_OFFSET =
            Layouts.SDL_MOUSE_WHEEL_EVENT.offsetOf("mouse_y");
    private static final long KEY_SCANCODE_OFFSET =
            Layouts.SDL_KEYBOARD_EVENT.offsetOf("scancode");
    private static final long KEY_KEYCODE_OFFSET =
            Layouts.SDL_KEYBOARD_EVENT.offsetOf("key");
    private static final long KEY_MOD_OFFSET =
            Layouts.SDL_KEYBOARD_EVENT.offsetOf("mod");
    private static final long KEY_REPEAT_OFFSET =
            Layouts.SDL_KEYBOARD_EVENT.offsetOf("repeat");
    private static final long TEXT_POINTER_OFFSET =
            Layouts.SDL_TEXT_INPUT_EVENT.offsetOf("text");
    private static final long DATA2_OFFSET =
            Layouts.SDL_WINDOW_EVENT.offsetOf("data2");

    private final Arena arena;
    private final MemorySegment event;

    public SdlEventBuffer() {
        this.arena = Arena.ofConfined();
        this.event = arena.allocate(Layouts.SDL_EVENT.layout());
    }

    /// The event type — an `SDL_EventType`, or a user event above
    /// [SdlEventType#USER].
    public int type() {
        return event.get(ValueLayout.JAVA_INT, TYPE_OFFSET);
    }

    /// The window this event concerns. Only meaningful for window events.
    public int windowId() {
        return event.get(ValueLayout.JAVA_INT, WINDOW_ID_OFFSET);
    }

    /// The first event-dependent field. For a resize, the new width.
    public int data1() {
        return event.get(ValueLayout.JAVA_INT, DATA1_OFFSET);
    }

    /// The second event-dependent field. For a resize, the new height.
    public int data2() {
        return event.get(ValueLayout.JAVA_INT, DATA2_OFFSET);
    }

    /// Zeroes the buffer. Not required by SDL, which overwrites what it fills,
    /// but it means a stale `windowID` cannot survive into an event type that
    /// does not set one.
    /// The pointer's window-relative x, for a mouse motion or button event.
    ///
    /// Motion and button events put `x` at different offsets, so which arm this
    /// is has to be decided by [#type()] first -- reading the wrong one is a
    /// pointer that lands somewhere plausible but wrong.
    public float pointerX() {
        return event.get(ValueLayout.JAVA_FLOAT, isMotion() ? MOTION_X_OFFSET : BUTTON_X_OFFSET);
    }

    /// The pointer's window-relative y.
    public float pointerY() {
        return event.get(ValueLayout.JAVA_FLOAT, isMotion() ? MOTION_Y_OFFSET : BUTTON_Y_OFFSET);
    }

    /// The mouse button index: SDL numbers them from 1, left first.
    public int mouseButton() {
        return event.get(ValueLayout.JAVA_BYTE, BUTTON_INDEX_OFFSET) & 0xFF;
    }

    /// 1 for a single click, 2 for a double, and so on -- SDL counts them so the
    /// toolkit does not have to keep a timer.
    public int clickCount() {
        return event.get(ValueLayout.JAVA_BYTE, BUTTON_CLICKS_OFFSET) & 0xFF;
    }

    private boolean isMotion() {
        return type() == SdlEventType.MOUSE_MOTION.value();
    }

    /// How far the wheel turned horizontally, **already un-flipped**.
    ///
    /// Positive is to the right. SDL's own value is negated first where
    /// `direction` says the platform inverted it, so callers never see the
    /// distinction — see [SdlWheelDirection].
    public float wheelX() {
        return direction() * event.get(ValueLayout.JAVA_FLOAT, WHEEL_X_OFFSET);
    }

    /// How far the wheel turned vertically, un-flipped.
    ///
    /// Positive is **away from the user**, which is SDL's convention and the
    /// opposite of the one a document scrolls in. The backend inverts it; this
    /// stays in SDL's terms, because a binding that silently redefines a field is
    /// a binding nobody can check against the header.
    public float wheelY() {
        return direction() * event.get(ValueLayout.JAVA_FLOAT, WHEEL_Y_OFFSET);
    }

    /// Where the pointer was when the wheel turned, window-relative.
    ///
    /// A wheel event carries its own position rather than reusing the last
    /// motion: scrolling with the pointer parked over a window that has never
    /// seen a move — which is what a fresh scroll after an alt-tab is — otherwise
    /// hits whatever was under the pointer last time.
    public float wheelPointerX() {
        return event.get(ValueLayout.JAVA_FLOAT, WHEEL_MOUSE_X_OFFSET);
    }

    /// Where the pointer was when the wheel turned.
    public float wheelPointerY() {
        return event.get(ValueLayout.JAVA_FLOAT, WHEEL_MOUSE_Y_OFFSET);
    }

    private float direction() {
        return SdlWheelDirection.sign(event.get(ValueLayout.JAVA_INT, WHEEL_DIRECTION_OFFSET));
    }

    /// The virtual keycode — what the layout says the key means.
    public int keycode() {
        return event.get(ValueLayout.JAVA_INT, KEY_KEYCODE_OFFSET);
    }

    /// The physical key position, independent of layout.
    public int scancode() {
        return event.get(ValueLayout.JAVA_INT, KEY_SCANCODE_OFFSET);
    }

    /// The modifier bitmask in force when the key was pressed.
    public int keyModifiers() {
        return event.get(ValueLayout.JAVA_SHORT, KEY_MOD_OFFSET) & 0xFFFF;
    }

    /// Whether this is the platform repeating a held key.
    public boolean isRepeat() {
        return event.get(ValueLayout.JAVA_BOOLEAN, KEY_REPEAT_OFFSET);
    }

    /// The committed text of a text-input event.
    ///
    /// **Copied immediately.** SDL owns the string and it is valid only until
    /// the next pump, so holding the pointer would be a use-after-free that
    /// shows up as mojibake rather than a crash.
    public String committedText() {
        var pointer = event.get(ValueLayout.ADDRESS, TEXT_POINTER_OFFSET);
        if (MemorySegment.NULL.equals(pointer)) {
            return "";
        }
        return readCString(pointer);
    }

    // Restricted: the string's extent is not known until it is walked, which is
    // what reinterpret with an unbounded size is for. SDL guarantees NUL
    // termination for this field.
    @SuppressWarnings("restricted")
    private static String readCString(MemorySegment pointer) {
        return pointer.reinterpret(Long.MAX_VALUE).getString(0);
    }

    public void clear() {
        event.fill((byte) 0);
    }

    MemorySegment segment() {
        return event;
    }

    @Override
    public void close() {
        arena.close();
    }

    /// The size SDL is entitled to write, for the assertion in [SdlVideo].
    static long byteSize() {
        return Layouts.SDL_EVENT.byteSize();
    }

    static MemoryLayout layout() {
        return Layouts.SDL_EVENT.layout();
    }
}
