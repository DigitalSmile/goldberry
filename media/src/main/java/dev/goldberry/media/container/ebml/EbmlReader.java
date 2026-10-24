package dev.goldberry.media.container.ebml;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.container.ByteReader;

/// Reads EBML (RFC 8794), the binary markup Matroska and WebM are written in:
/// elements that are an ID, a size and a payload, where a payload is either
/// bytes of a known type or more elements.
///
/// This is the reader; what the IDs mean is Matroska's business
/// ([dev.goldberry.media.container.matroska.MatroskaIds]). It knows the one
/// EBML-level fact Matroska leans on, that a size can be unknown
/// ([Element#UNKNOWN_SIZE]), which a live stream's Segment and Clusters are
/// written with.
public final class EbmlReader {

    /// One element's header, read from the stream.
    ///
    /// @param id        the element ID with its length-marker bits, as the
    ///                  specification writes it: `0x1A45DFA3`, `0xA3`
    /// @param size      the payload's size in bytes, or [#UNKNOWN_SIZE]
    /// @param dataStart where the payload starts, in bytes from the stream's start
    /// @param headerStart where the header started
    public record Element(long id, long size, long dataStart, long headerStart) {

        /// The size of an element whose size the writer did not know: all the
        /// size's value bits set.
        public static final long UNKNOWN_SIZE = -1;

        /// Whether the size is known.
        public boolean sized() {
            return size != UNKNOWN_SIZE;
        }

        /// Where the payload ends, for a sized element.
        ///
        /// @throws MediaException [MediaError.InvalidData] for an element of
        ///                        unknown size: a reader asks this only of the
        ///                        elements that must have one, so the bytes
        ///                        are not what the container promised
        public long dataEnd() {
            if (!sized()) {
                throw unsized(this);
            }
            return dataStart + size;
        }
    }

    private final ByteReader in;

    public EbmlReader(ByteReader in) {
        this.in = in;
    }

    /// The bytes under this reader.
    public ByteReader bytes() {
        return in;
    }

    /// Where the next header starts.
    public long position() {
        return in.position();
    }

    /// Whether the stream has no more elements.
    public boolean atEnd() {
        return in.atEnd();
    }

    /// Reads the next element's header; the stream is then at its payload.
    ///
    /// @throws MediaException when the bytes are not a header
    public Element readHeader() {
        var start = in.position();
        var id = readId();
        var size = readSize();
        return new Element(id, size, in.position(), start);
    }

    /// An element ID: a variable-length integer kept with its marker bits, one
    /// to four bytes.
    public long readId() {
        var first = in.u8();
        var length = lengthOf(first);
        if (length == 0 || length > 4) {
            throw new MediaException(new MediaError.InvalidData(
                    "not an EBML element ID at byte " + (in.position() - 1) + ": 0x" + Integer.toHexString(first)));
        }
        var id = (long) first;
        for (var i = 1; i < length; i++) {
            id = (id << 8) | in.u8();
        }
        return id;
    }

    /// An element size: a variable-length integer with its marker removed, one
    /// to eight bytes, or [Element#UNKNOWN_SIZE] when every value bit is set.
    public long readSize() {
        var first = in.u8();
        var length = lengthOf(first);
        if (length == 0) {
            throw new MediaException(new MediaError.InvalidData(
                    "not an EBML size at byte " + (in.position() - 1) + ": 0x" + Integer.toHexString(first)));
        }
        // A long: a size is up to 56 bits, and an int would drop the high ones.
        var value = (long) (first & (0xFF >>> length));
        var allOnes = value == (0xFF >>> length);
        for (var i = 1; i < length; i++) {
            var next = in.u8();
            allOnes &= next == 0xFF;
            value = (value << 8) | next;
        }
        return allOnes ? Element.UNKNOWN_SIZE : value;
    }

    /// The length a variable-length integer's first byte announces, 1–8, or 0
    /// for a byte that announces none (0x00).
    public static int lengthOf(int first) {
        if (first == 0) {
            return 0;
        }
        return Integer.numberOfLeadingZeros(first) - 23;
    }

    /// The payload of `element` as an unsigned integer, 0–8 bytes.
    public long readUnsigned(Element element) {
        checkScalar(element, 8);
        return in.unsignedBigEndian((int) element.size());
    }

    /// The payload of `element` as a signed integer, 0–8 bytes.
    public long readSigned(Element element) {
        checkScalar(element, 8);
        return in.signedBigEndian((int) element.size());
    }

    /// The payload of `element` as a float: 0, 4 or 8 bytes.
    public double readFloat(Element element) {
        checkScalar(element, 8);
        return switch ((int) element.size()) {
            case 0 -> 0;
            case 4 -> Float.intBitsToFloat((int) in.u32());
            case 8 -> Double.longBitsToDouble(in.unsignedBigEndian(8));
            default ->
                throw new MediaException(new MediaError.InvalidData(
                        "an EBML float of " + element.size() + " bytes at byte " + element.dataStart()));
        };
    }

    /// The payload of `element` as a UTF-8 or ASCII string.
    public String readString(Element element) {
        checkScalar(element, Integer.MAX_VALUE);
        return in.utf8((int) element.size());
    }

    /// The payload of `element`, copied to the heap.
    public byte[] readBytes(Element element) {
        checkScalar(element, Integer.MAX_VALUE);
        return in.bytes((int) element.size());
    }

    /// The payload of `element`, copied into native memory of `arena`.
    public MemorySegment readSegment(Element element, Arena arena) {
        if (!element.sized()) {
            throw unsized(element);
        }
        return in.segment(arena, element.size());
    }

    /// Passes over `element`'s payload.
    public void skip(Element element) {
        if (!element.sized()) {
            throw unsized(element);
        }
        in.skip(element.size());
    }

    private void checkScalar(Element element, long limit) {
        if (!element.sized()) {
            throw unsized(element);
        }
        if (element.size() > limit) {
            throw new MediaException(
                    new MediaError.InvalidData("EBML element 0x" + Long.toHexString(element.id()) + " at byte "
                            + element.headerStart() + " is " + element.size() + " bytes, more than its kind allows"));
        }
    }

    private static MediaException unsized(Element element) {
        return new MediaException(new MediaError.InvalidData("EBML element 0x" + Long.toHexString(element.id())
                + " at byte " + element.headerStart() + " has an unknown size, which only a master element may"));
    }
}
