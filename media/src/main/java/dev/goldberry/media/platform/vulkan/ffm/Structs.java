package dev.goldberry.media.platform.vulkan.ffm;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;

/// What filling Vulkan's structs takes, in one place: allocated zeroed in an
/// arena, `sType` written first, chains linked through `pNext`, which is the
/// eight bytes after it in every struct that has one.
public final class Structs {

    /// Where every chainable struct keeps `sType`.
    public static final long STYPE = 0;

    /// Where every chainable struct keeps `pNext`.
    public static final long PNEXT = 8;

    private Structs() {}

    /// A zeroed `layout` in `arena`.
    public static MemorySegment allocate(Arena arena, MemoryLayout layout) {
        return arena.allocate(layout);
    }

    /// A zeroed `layout` in `arena` with its `sType` set.
    public static MemorySegment allocate(Arena arena, MemoryLayout layout, int sType) {
        var segment = arena.allocate(layout);
        segment.set(JAVA_INT, STYPE, sType);
        return segment;
    }

    /// `count` zeroed `layout`s in a row, each with its `sType` set.
    public static MemorySegment array(Arena arena, MemoryLayout layout, int count, int sType) {
        var segment = arena.allocate(layout, Math.max(count, 1));
        for (var i = 0; i < count; i++) {
            segment.set(JAVA_INT, i * layout.byteSize() + STYPE, sType);
        }
        return segment;
    }

    /// Links `next` after `struct`: `struct.pNext = next`.
    public static MemorySegment chain(MemorySegment struct, MemorySegment next) {
        struct.set(ADDRESS, PNEXT, next);
        return struct;
    }

    /// Element `index` of an array of `layout`s.
    public static MemorySegment at(MemorySegment array, MemoryLayout layout, int index) {
        return array.asSlice(index * layout.byteSize(), layout.byteSize());
    }

    /// Sets bits `mask` of the 32-bit word at `offset` when `value`.
    public static void flag(MemorySegment struct, long offset, int mask, boolean value) {
        if (value) {
            struct.set(JAVA_INT, offset, struct.get(JAVA_INT, offset) | mask);
        }
    }

    /// Sets the field of `mask`, from bit `shift`, of the word at `offset` to
    /// `value`.
    public static void bits(MemorySegment struct, long offset, int shift, int mask, int value) {
        var word = struct.get(JAVA_INT, offset) & ~mask;
        struct.set(JAVA_INT, offset, word | ((value << shift) & mask));
    }
}
