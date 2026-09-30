package io.github.digitalsmile.goldberry.media.platform.windows;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/// A Windows `GUID`, in the byte order Windows keeps it in memory.
///
/// A GUID is written `{Data1-Data2-Data3-Data4[0..1]-Data4[2..7]}`, and stored as
/// `struct { uint32 Data1; uint16 Data2; uint16 Data3; uint8 Data4[8]; }` on a
/// little-endian machine: the first three fields' bytes are reversed from the
/// text, and the last eight are as written (`guiddef.h`). A FourCC media subtype
/// is `{FOURCC-0000-0010-8000-00AA00389B71}`, so its first four bytes in memory
/// are the four characters in order.
final class Guid {

    /// `GUID` (`guiddef.h`): 16 bytes, 4-byte aligned.
    static final StructLayout LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("Data1"),
            ValueLayout.JAVA_SHORT.withName("Data2"),
            ValueLayout.JAVA_SHORT.withName("Data3"),
            MemoryLayout.sequenceLayout(8, JAVA_BYTE).withName("Data4"));

    private static final Pattern TEXT = Pattern.compile(
            "\\{?([0-9A-Fa-f]{8})-([0-9A-Fa-f]{4})-([0-9A-Fa-f]{4})-([0-9A-Fa-f]{4})-([0-9A-Fa-f]{12})}?");

    private static final long DATA1 = LAYOUT.byteOffset(groupElement("Data1"));
    private static final long DATA2 = LAYOUT.byteOffset(groupElement("Data2"));
    private static final long DATA3 = LAYOUT.byteOffset(groupElement("Data3"));
    private static final long DATA4 = LAYOUT.byteOffset(groupElement("Data4"));

    private static final ValueLayout.OfInt U32 = JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
    private static final ValueLayout.OfShort U16 = JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);

    /// The 16 bytes, as they are in memory.
    private final byte[] bytes;

    /// The bytes in native memory, allocated the first time a call needs them.
    private volatile @Nullable MemorySegment segment;

    private Guid(byte[] bytes) {
        this.bytes = bytes;
    }

    /// The GUID written `text`: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`, with or
    /// without braces, in either case.
    ///
    /// @throws IllegalArgumentException when `text` is not a GUID
    static Guid parse(String text) {
        var matcher = TEXT.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("not a GUID: " + text);
        }
        var hex = HexFormat.of();
        var out = MemorySegment.ofArray(new byte[16]);
        out.set(U32, DATA1, (int) Long.parseLong(matcher.group(1), 16));
        out.set(U16, DATA2, (short) Integer.parseInt(matcher.group(2), 16));
        out.set(U16, DATA3, (short) Integer.parseInt(matcher.group(3), 16));
        var tail = hex.parseHex(matcher.group(4) + matcher.group(5));
        MemorySegment.copy(tail, 0, out, JAVA_BYTE, DATA4, tail.length);
        return new Guid(out.toArray(JAVA_BYTE));
    }

    /// The media subtype for the FourCC `code`, as `mfapi.h`'s
    /// `DEFINE_MEDIATYPE_GUID` makes one: `{FOURCC-0000-0010-8000-00AA00389B71}`.
    static Guid fourCc(String code) {
        if (code.length() != 4 || !code.chars().allMatch(c -> c >= 0x20 && c <= 0x7E)) {
            throw new IllegalArgumentException("a FourCC is four ASCII characters: '" + code + "'");
        }
        var value = 0;
        for (var i = 3; i >= 0; i--) {
            value = (value << 8) | code.charAt(i);
        }
        return parse(String.format(Locale.ROOT, "%08x-0000-0010-8000-00aa00389b71", value));
    }

    /// The GUID at `offset` in `segment`.
    static Guid read(MemorySegment segment, long offset) {
        return new Guid(segment.asSlice(offset, LAYOUT.byteSize()).toArray(JAVA_BYTE));
    }

    /// The 16 bytes as they are in memory.
    byte[] bytes() {
        return bytes.clone();
    }

    /// Writes the GUID at `offset` in `target`.
    void write(MemorySegment target, long offset) {
        MemorySegment.copy(bytes, 0, target, JAVA_BYTE, offset, bytes.length);
    }

    /// The GUID in native memory, for a `REFGUID` parameter. Allocated once and
    /// kept for the life of the process: the GUIDs passed so are [MfGuids]'
    /// constants.
    MemorySegment segment() {
        var existing = segment;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            var again = segment;
            if (again == null) {
                again = Arena.global().allocate(LAYOUT);
                write(again, 0);
                segment = again;
            }
            return again;
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Guid guid && Arrays.equals(bytes, guid.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    /// The GUID as the headers write it, lower case, without braces.
    @Override
    public String toString() {
        var in = MemorySegment.ofArray(bytes);
        var hex = HexFormat.of();
        return String.format(
                Locale.ROOT,
                "%08x-%04x-%04x-%s-%s",
                in.get(U32, DATA1),
                in.get(U16, DATA2) & 0xFFFF,
                in.get(U16, DATA3) & 0xFFFF,
                hex.formatHex(bytes, 8, 10),
                hex.formatHex(bytes, 10, 16));
    }
}
