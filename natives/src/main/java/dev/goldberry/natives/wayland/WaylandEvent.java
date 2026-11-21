package dev.goldberry.natives.wayland;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// One event, as libwayland hands it to a dispatcher: the opcode and the
/// `union wl_argument` array, valid for the length of the call.
///
/// A file descriptor (`h`) belongs to whoever hears it, which must close it.
///
/// @param opcode    the event's index in its interface
/// @param arguments the arguments, [WaylandArguments#ARGUMENT_BYTES] apart
public record WaylandEvent(int opcode, MemorySegment arguments) {

    public WaylandEvent {
        Objects.requireNonNull(arguments, "arguments");
    }

    /// Argument `index` as an `int`, `uint`, fixed-point value or descriptor.
    public int integer(int index) {
        return arguments.get(JAVA_INT, offset(index));
    }

    /// Argument `index` as a string, or null for a null one.
    // Restricted: libwayland's string, NUL-terminated and alive for the call.
    @SuppressWarnings("restricted")
    public @Nullable String string(int index) {
        var pointer = arguments.get(ADDRESS, offset(index));
        if (pointer.address() == 0) {
            return null;
        }
        return pointer.reinterpret(Integer.MAX_VALUE).getString(0);
    }

    /// Argument `index` as an object, `NULL` for none.
    public MemorySegment object(int index) {
        return arguments.get(ADDRESS, offset(index));
    }

    private long offset(int index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException(index);
        }
        return index * WaylandArguments.ARGUMENT_BYTES;
    }
}
