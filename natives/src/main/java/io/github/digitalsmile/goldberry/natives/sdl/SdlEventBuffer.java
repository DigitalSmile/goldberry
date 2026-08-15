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
