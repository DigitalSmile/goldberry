package dev.goldberry.natives.wayland;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

/// A request's arguments, as the `union wl_argument` array
/// `wl_proxy_marshal_array_flags` reads.
///
/// Each element is eight bytes on the 64-bit platforms Goldberry ships for:
/// an integer in the low four (little-endian), or a pointer in all eight.
/// libwayland copies what it needs before the call returns, so the array and
/// its strings live in the caller's arena only for the call.
///
/// ```java
/// try (var arena = Arena.ofConfined()) {
///     var args = new WaylandArguments(arena, 1).string("image/png");
///     client.marshal(source, OFFER, MemorySegment.NULL, version, 0, args.segment());
/// }
/// ```
public final class WaylandArguments {

    /// The size of one `union wl_argument`.
    public static final long ARGUMENT_BYTES = 8;

    private final Arena arena;
    private final MemorySegment segment;
    private final int count;
    private int next;

    /// Room for `count` arguments, zeroed, in `arena`.
    public WaylandArguments(Arena arena, int count) {
        this.arena = Objects.requireNonNull(arena, "arena");
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative: " + count);
        }
        this.count = count;
        this.segment = arena.allocate(Math.max(1, count) * ARGUMENT_BYTES, ARGUMENT_BYTES);
    }

    /// An `int` (`i`), a file descriptor (`h`) or a fixed-point value (`f`).
    public WaylandArguments integer(int value) {
        segment.set(JAVA_INT, slot(), value);
        return this;
    }

    /// A `uint` (`u`), its bits as given.
    public WaylandArguments unsigned(int value) {
        return integer(value);
    }

    /// A string (`s`), copied into the arena as UTF-8.
    public WaylandArguments string(String value) {
        segment.set(ADDRESS, slot(), arena.allocateFrom(value));
        return this;
    }

    /// An object (`o`), or `NULL` for a nullable one.
    public WaylandArguments object(MemorySegment proxy) {
        segment.set(ADDRESS, slot(), Objects.requireNonNull(proxy, "proxy"));
        return this;
    }

    /// The `new_id` (`n`) a request makes: left `NULL`, since libwayland fills
    /// it with the proxy it creates.
    public WaylandArguments newId() {
        segment.set(ADDRESS, slot(), MemorySegment.NULL);
        return this;
    }

    /// The array, to hand to `marshal`.
    public MemorySegment segment() {
        return segment;
    }

    private long slot() {
        if (next >= count) {
            throw new IllegalStateException("more arguments than the " + count + " made room for");
        }
        return next++ * ARGUMENT_BYTES;
    }
}
