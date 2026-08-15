package io.github.digitalsmile.goldberry.natives.layout;

import io.github.digitalsmile.goldberry.natives.GoldberryShim;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

/// Reads the layout table out of the loaded `libgoldberry`.
///
/// The table is `sizeof`, `_Alignof`, and `offsetof` as the C compiler computed
/// them for this exact target — the ground truth the hand-written layouts in
/// [Layouts] are checked against.
public final class LayoutProbe {

    /// Sanity bound on the table size. The count is read before anything is
    /// dereferenced, so a garbage value must be rejected here rather than
    /// turning into a wild read.
    private static final int MAX_PLAUSIBLE_ENTRIES = 10_000;

    /// Sanity bound when reading a C string from the table.
    private static final int MAX_NAME_LENGTH = 256;

    private static final long ENTRY_SIZE = Layouts.LAYOUT_ENTRY.byteSize();

    private static final long STRUCT_NAME_OFFSET = offsetOf("struct_name");
    private static final long FIELD_NAME_OFFSET = offsetOf("field_name");
    private static final long SIZE_OFFSET = offsetOf("size");
    private static final long OFFSET_OFFSET = offsetOf("offset");
    private static final long ALIGNMENT_OFFSET = offsetOf("alignment");

    private LayoutProbe() {
    }

    /// Reads the whole table.
    ///
    /// @throws IllegalStateException if the table is implausible, which most
    ///         likely means [Layouts#LAYOUT_ENTRY] does not match the library
    // Restricted: the table pointer comes back zero-length and has to be resized
    // to be read. The count is validated above before any resize happens.
    @SuppressWarnings("restricted")
    public static List<LayoutEntry> read() {
        var shim = GoldberryShim.get();
        var count = shim.layoutCount();
        if (count <= 0 || count > MAX_PLAUSIBLE_ENTRIES) {
            throw new IllegalStateException(
                    "libgoldberry reports " + count + " layout entries, which is not plausible."
                            + " The layout-entry struct is probably modelled incorrectly.");
        }

        var table = shim.layoutTable().reinterpret(count * ENTRY_SIZE);
        var entries = new ArrayList<LayoutEntry>(count);
        for (var i = 0; i < count; i++) {
            var base = i * ENTRY_SIZE;
            var structName = readString(table, base + STRUCT_NAME_OFFSET);
            if (structName == null || structName.isBlank()) {
                throw new IllegalStateException(
                        "layout entry " + i + " has no struct name; the table is not being read correctly");
            }
            entries.add(new LayoutEntry(
                    structName,
                    readString(table, base + FIELD_NAME_OFFSET),
                    table.get(ValueLayout.JAVA_INT, base + SIZE_OFFSET),
                    table.get(ValueLayout.JAVA_INT, base + OFFSET_OFFSET),
                    table.get(ValueLayout.JAVA_INT, base + ALIGNMENT_OFFSET)));
        }
        return List.copyOf(entries);
    }

    // Restricted: resizing a char* to a bounded window before reading it. The
    // bound is what stops a corrupt pointer from becoming an unbounded read.
    @SuppressWarnings("restricted")
    private static String readString(MemorySegment table, long offset) {
        var pointer = table.get(ValueLayout.ADDRESS, offset);
        if (MemorySegment.NULL.equals(pointer)) {
            return null;
        }
        return pointer.reinterpret(MAX_NAME_LENGTH).getString(0);
    }

    private static long offsetOf(String field) {
        return Layouts.LAYOUT_ENTRY.byteOffset(MemoryLayout.PathElement.groupElement(field));
    }
}
