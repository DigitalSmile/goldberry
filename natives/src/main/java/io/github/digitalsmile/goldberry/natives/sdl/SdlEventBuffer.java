package io.github.digitalsmile.goldberry.natives.sdl;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

import io.github.digitalsmile.goldberry.natives.layout.Layouts;
import io.github.digitalsmile.goldberry.natives.sdl.event.SdlEventType;
import io.github.digitalsmile.goldberry.natives.sdl.event.SdlWheelDirection;

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

    private static final long TYPE_OFFSET = Layouts.SDL_COMMON_EVENT.offsetOf("type");
    private static final long WINDOW_ID_OFFSET = Layouts.SDL_WINDOW_EVENT.offsetOf("windowID");
    private static final long DATA1_OFFSET = Layouts.SDL_WINDOW_EVENT.offsetOf("data1");
    private static final long MOTION_X_OFFSET = Layouts.SDL_MOUSE_MOTION_EVENT.offsetOf("x");
    private static final long MOTION_Y_OFFSET = Layouts.SDL_MOUSE_MOTION_EVENT.offsetOf("y");
    private static final long BUTTON_X_OFFSET = Layouts.SDL_MOUSE_BUTTON_EVENT.offsetOf("x");
    private static final long BUTTON_Y_OFFSET = Layouts.SDL_MOUSE_BUTTON_EVENT.offsetOf("y");
    private static final long BUTTON_INDEX_OFFSET = Layouts.SDL_MOUSE_BUTTON_EVENT.offsetOf("button");
    private static final long BUTTON_CLICKS_OFFSET = Layouts.SDL_MOUSE_BUTTON_EVENT.offsetOf("clicks");
    /// The motion and button arms' own `windowID`, for [WHEEL_WINDOW_ID_OFFSET]'s
    /// reason: the offset coincides with the window arm's because every arm starts
    /// with the same three fields, and taking it from the layout that applies means
    /// the coincidence is not what holds it up.
    private static final long MOTION_WINDOW_ID_OFFSET = Layouts.SDL_MOUSE_MOTION_EVENT.offsetOf("windowID");
    private static final long BUTTON_WINDOW_ID_OFFSET = Layouts.SDL_MOUSE_BUTTON_EVENT.offsetOf("windowID");
    private static final long WHEEL_X_OFFSET = Layouts.SDL_MOUSE_WHEEL_EVENT.offsetOf("x");
    private static final long WHEEL_Y_OFFSET = Layouts.SDL_MOUSE_WHEEL_EVENT.offsetOf("y");
    private static final long WHEEL_DIRECTION_OFFSET = Layouts.SDL_MOUSE_WHEEL_EVENT.offsetOf("direction");
    private static final long WHEEL_INTEGER_X_OFFSET = Layouts.SDL_MOUSE_WHEEL_EVENT.offsetOf("integer_x");
    private static final long WHEEL_INTEGER_Y_OFFSET = Layouts.SDL_MOUSE_WHEEL_EVENT.offsetOf("integer_y");
    private static final long WHEEL_MOUSE_X_OFFSET = Layouts.SDL_MOUSE_WHEEL_EVENT.offsetOf("mouse_x");
    private static final long WHEEL_MOUSE_Y_OFFSET = Layouts.SDL_MOUSE_WHEEL_EVENT.offsetOf("mouse_y");
    /// The wheel arm's own `windowID`. At the same offset as the window arm's,
    /// because both structs start with the same three fields — but taken from the
    /// layout that actually applies, so the coincidence is not what holds it up.
    private static final long WHEEL_WINDOW_ID_OFFSET = Layouts.SDL_MOUSE_WHEEL_EVENT.offsetOf("windowID");
    private static final long KEY_SCANCODE_OFFSET = Layouts.SDL_KEYBOARD_EVENT.offsetOf("scancode");
    private static final long KEY_KEYCODE_OFFSET = Layouts.SDL_KEYBOARD_EVENT.offsetOf("key");
    private static final long KEY_MOD_OFFSET = Layouts.SDL_KEYBOARD_EVENT.offsetOf("mod");
    private static final long KEY_REPEAT_OFFSET = Layouts.SDL_KEYBOARD_EVENT.offsetOf("repeat");
    private static final long TEXT_POINTER_OFFSET = Layouts.SDL_TEXT_INPUT_EVENT.offsetOf("text");
    private static final long EDIT_POINTER_OFFSET = Layouts.SDL_TEXT_EDITING_EVENT.offsetOf("text");
    private static final long EDIT_START_OFFSET = Layouts.SDL_TEXT_EDITING_EVENT.offsetOf("start");
    private static final long EDIT_LENGTH_OFFSET = Layouts.SDL_TEXT_EDITING_EVENT.offsetOf("length");
    private static final long DATA2_OFFSET = Layouts.SDL_WINDOW_EVENT.offsetOf("data2");
    private static final long DROP_DATA_OFFSET = Layouts.SDL_DROP_EVENT.offsetOf("data");
    private static final long DROP_X_OFFSET = Layouts.SDL_DROP_EVENT.offsetOf("x");
    private static final long DROP_Y_OFFSET = Layouts.SDL_DROP_EVENT.offsetOf("y");

    /// Null for a borrowed view — see [#borrowing].
    private final Arena arena;
    private final MemorySegment event;

    public SdlEventBuffer() {
        this.arena = Arena.ofConfined();
        this.event = arena.allocate(Layouts.SDL_EVENT.layout());
    }

    private SdlEventBuffer(Arena arena, MemorySegment event) {
        this.arena = arena;
        this.event = event;
    }

    /// A read-only view over an event SDL owns.
    ///
    /// What [SdlEventWatch] hands its callback: SDL passes a pointer to the event
    /// it is about to queue, and that memory belongs to SDL for the duration of
    /// the call and no longer. Nothing here copies it, so a view must not outlive
    /// the callback it was handed to.
    ///
    /// Restricted: the pointer arrives with no size, and the extent given is the
    /// one the layout probe has already checked `SDL_Event` against.
    @SuppressWarnings("restricted")
    static SdlEventBuffer borrowing(MemorySegment event) {
        return new SdlEventBuffer(null, event.reinterpret(byteSize()));
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

    /// The same turn, accumulated by SDL into **whole detents** — `integer_x`.
    ///
    /// Not a rounding of [#wheelX()]. SDL keeps the running fraction itself and
    /// emits a whole click when it crosses one, so a touchpad dragged slowly
    /// reports a long run of zeroes here and then a single 1 — where rounding
    /// each fractional event separately would report nothing at all, forever.
    /// That is the difference between a detent counter that works on a trackpad
    /// and one that does not
    /// (ADR-0115).
    ///
    /// Un-flipped like the floats, and for the same reason.
    public int wheelTicksX() {
        return (int) (direction() * event.get(ValueLayout.JAVA_INT, WHEEL_INTEGER_X_OFFSET));
    }

    /// The vertical turn in whole detents — `integer_y`, in SDL's sign.
    public int wheelTicksY() {
        return (int) (direction() * event.get(ValueLayout.JAVA_INT, WHEEL_INTEGER_Y_OFFSET));
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

    /// The dropped file's name, or `""` — `SDL_DropEvent.data`.
    ///
    /// Copied out here rather than handed on as a pointer, for
    /// [#committedText]'s reason: SDL owns the string and frees it at the next
    /// pump (`docs/gaps.md` G35b, [ADR-0330]).
    ///
    /// Empty for `DROP_BEGIN`, `DROP_POSITION` and `DROP_COMPLETE`, which carry
    /// a NULL there and say so in SDL's own header.
    public String droppedPath() {
        return droppedData();
    }

    /// One line of the dropped **text**, or `""` — the same
    /// `SDL_DropEvent.data`, read for `DROP_TEXT` ([ADR-0408]).
    ///
    /// A second name for one field rather than a second field: SDL's header says
    /// `data` is "the text for `SDL_EVENT_DROP_TEXT` and the file name for
    /// `SDL_EVENT_DROP_FILE`". The names are separate so that the arm reading it
    /// says which event it is on — a `droppedPath()` in the text arm would read
    /// as a bug every time somebody looked at it.
    public String droppedText() {
        return droppedData();
    }

    private String droppedData() {
        var pointer = event.get(ValueLayout.ADDRESS, DROP_DATA_OFFSET);
        if (MemorySegment.NULL.equals(pointer)) {
            return "";
        }
        return readCString(pointer);
    }

    /// Where in the window the drop event happened, in SDL's window coordinates.
    ///
    /// Zero on `DROP_BEGIN`, which SDL's header says carries no position.
    public float dropX() {
        return event.get(ValueLayout.JAVA_FLOAT, DROP_X_OFFSET);
    }

    public float dropY() {
        return event.get(ValueLayout.JAVA_FLOAT, DROP_Y_OFFSET);
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

    /// The composition an input method is assembling, for a [SdlEventType#TEXT_EDITING].
    ///
    /// Empty when the composition has ended, which is how SDL says "the candidate
    /// window has closed" — with or without a [#committedText] before it.
    ///
    /// Copied out here for [#committedText]'s reason: the pointer is into SDL's
    /// own storage and is valid until the next pump.
    public String editingText() {
        var pointer = event.get(ValueLayout.ADDRESS, EDIT_POINTER_OFFSET);
        if (MemorySegment.NULL.equals(pointer)) {
            return "";
        }
        return readCString(pointer);
    }

    /// Where the selection inside the composition starts, as a **byte** offset
    /// into [#editingText], or `-1` when the platform does not report one.
    ///
    /// Bytes rather than characters because that is what SDL hands over, and
    /// translating it is a decision about the text stack rather than about the
    /// event — see `io.github.digitalsmile.goldberry.input.event.PreeditEvent`.
    public int editingStart() {
        return event.get(ValueLayout.JAVA_INT, EDIT_START_OFFSET);
    }

    /// How many bytes of [#editingText] are selected, or `-1` when the platform
    /// does not report a selection.
    public int editingLength() {
        return event.get(ValueLayout.JAVA_INT, EDIT_LENGTH_OFFSET);
    }

    // Restricted: the string's extent is not known until it is walked, which is
    // what reinterpret with an unbounded size is for. SDL guarantees NUL
    // termination for this field.
    @SuppressWarnings("restricted")
    private static String readCString(MemorySegment pointer) {
        return pointer.reinterpret(Long.MAX_VALUE).getString(0);
    }

    /// Zeroes the buffer. Not required by SDL, which overwrites what it fills,
    /// but it means a stale `windowID` cannot survive into an event type that
    /// does not set one.
    public void clear() {
        event.fill((byte) 0);
    }

    /// Fills this buffer with a mouse-wheel event, the way SDL fills one.
    ///
    /// Synthesizing input is what `SDL_PushEvent` is for, and here it is the only
    /// way to reach a code path a test cannot otherwise run: nothing in a test
    /// suite can turn a wheel. Pushed with [SdlVideo#push], the event comes back
    /// out of the ordinary pump and takes the ordinary route, so what runs is the
    /// shipping translation rather than a copy of it (ADR-0061).
    ///
    /// `direction` is SDL's own, un-inverted: the point of pushing a flipped event
    /// is to check that [#wheelY()] un-flips it.
    ///
    /// @param x        horizontal detents, positive to the right
    /// @param y        vertical detents, positive **away from the user** — SDL's
    ///                 sign, not the toolkit's
    /// @param pointerX where the pointer was, window-relative
    public void writeWheel(
            int windowId, float x, float y, SdlWheelDirection direction, float pointerX, float pointerY) {

        // The detents SDL would have accumulated for a turn this size. Truncation
        // and not rounding, because that is what an accumulator crossing whole
        // numbers does: 0.5 has not reached one click yet.
        writeWheel(windowId, x, y, (int) x, (int) y, direction, pointerX, pointerY);
    }

    /// The same, stating the accumulated detents rather than deriving them.
    ///
    /// What a test needs to reach the case the `integer_*` pair exists for: a
    /// touchpad reporting a long run of fractions and SDL emitting a whole click
    /// part-way through, which no function of one event's floats can produce
    /// (ADR-0115).
    public void writeWheel(
            int windowId,
            float x,
            float y,
            int ticksX,
            int ticksY,
            SdlWheelDirection direction,
            float pointerX,
            float pointerY) {

        Objects.requireNonNull(direction, "direction");
        clear();
        event.set(ValueLayout.JAVA_INT, TYPE_OFFSET, SdlEventType.MOUSE_WHEEL.value());
        event.set(ValueLayout.JAVA_INT, WHEEL_WINDOW_ID_OFFSET, windowId);
        event.set(ValueLayout.JAVA_FLOAT, WHEEL_X_OFFSET, x);
        event.set(ValueLayout.JAVA_FLOAT, WHEEL_Y_OFFSET, y);
        event.set(ValueLayout.JAVA_INT, WHEEL_INTEGER_X_OFFSET, ticksX);
        event.set(ValueLayout.JAVA_INT, WHEEL_INTEGER_Y_OFFSET, ticksY);
        event.set(ValueLayout.JAVA_INT, WHEEL_DIRECTION_OFFSET, direction.value());
        event.set(ValueLayout.JAVA_FLOAT, WHEEL_MOUSE_X_OFFSET, pointerX);
        event.set(ValueLayout.JAVA_FLOAT, WHEEL_MOUSE_Y_OFFSET, pointerY);
    }

    /// Fills this buffer with a mouse-motion event.
    ///
    /// [#writeWheel]'s reason, for a different unreachable case: a test cannot
    /// move a pointer, and the coordinates a motion arrives with are the subject
    /// of ADR-0211
    /// — a window and a coordinate that disagree about which space they are in.
    /// Pushing one is the only way to state that disagreement on purpose.
    ///
    /// @param x window-relative, and deliberately allowed to be outside the window
    public void writeMouseMotion(int windowId, float x, float y) {
        clear();
        event.set(ValueLayout.JAVA_INT, TYPE_OFFSET, SdlEventType.MOUSE_MOTION.value());
        event.set(ValueLayout.JAVA_INT, MOTION_WINDOW_ID_OFFSET, windowId);
        event.set(ValueLayout.JAVA_FLOAT, MOTION_X_OFFSET, x);
        event.set(ValueLayout.JAVA_FLOAT, MOTION_Y_OFFSET, y);
    }

    /// Fills this buffer with a mouse-button event, down or up.
    ///
    /// The pair that reproduces the macOS popup case: a press whose coordinates
    /// are in the popup's space and a release whose coordinates are in the
    /// owner's, both carrying the popup's id (ADR-0211).
    ///
    /// @param type   [SdlEventType#MOUSE_BUTTON_DOWN] or [SdlEventType#MOUSE_BUTTON_UP]
    /// @param button SDL's index, numbered from 1 with left first
    public void writeMouseButton(SdlEventType type, int windowId, float x, float y, int button, int clicks) {

        Objects.requireNonNull(type, "type");
        clear();
        event.set(ValueLayout.JAVA_INT, TYPE_OFFSET, type.value());
        event.set(ValueLayout.JAVA_INT, BUTTON_WINDOW_ID_OFFSET, windowId);
        event.set(ValueLayout.JAVA_FLOAT, BUTTON_X_OFFSET, x);
        event.set(ValueLayout.JAVA_FLOAT, BUTTON_Y_OFFSET, y);
        event.set(ValueLayout.JAVA_BYTE, BUTTON_INDEX_OFFSET, (byte) button);
        event.set(ValueLayout.JAVA_BYTE, BUTTON_CLICKS_OFFSET, (byte) clicks);
    }

    /// Fills this buffer with a window event — a resize, an expose, a close
    /// request. The counterpart of [#writeWheel] for the events a modal resize
    /// loop delivers, which a test cannot produce by dragging anything either.
    ///
    /// `data1` and `data2` are the arm's two payload fields; for a resize they are
    /// the new width and height, which Goldberry reads back off the window rather
    /// than out of the event.
    public void writeWindowEvent(SdlEventType type, int windowId, int data1, int data2) {
        Objects.requireNonNull(type, "type");
        clear();
        event.set(ValueLayout.JAVA_INT, TYPE_OFFSET, type.value());
        event.set(ValueLayout.JAVA_INT, WINDOW_ID_OFFSET, windowId);
        event.set(ValueLayout.JAVA_INT, DATA1_OFFSET, data1);
        event.set(ValueLayout.JAVA_INT, DATA2_OFFSET, data2);
    }

    MemorySegment segment() {
        return event;
    }

    /// Releases the buffer's memory. A no-op for a borrowed view, whose memory
    /// belongs to SDL.
    @Override
    public void close() {
        if (arena != null) {
            arena.close();
        }
    }

    /// The size SDL is entitled to write, for the assertion in [SdlVideo].
    static long byteSize() {
        return Layouts.SDL_EVENT.byteSize();
    }
}
