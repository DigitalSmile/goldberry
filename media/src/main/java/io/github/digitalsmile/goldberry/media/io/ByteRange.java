package io.github.digitalsmile.goldberry.media.io;

/// A run of bytes of a resource, from `start` up to but not including `end`.
///
/// What [MediaIO#buffered()] reports: the parts of a resource that are at hand
/// without another request. Half-open, so two ranges that touch share a boundary
/// and neither counts a byte twice.
///
/// @param start the first byte's offset
/// @param end   the offset just past the last byte, not before `start`
public record ByteRange(long start, long end) {

    public ByteRange {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("not a byte range: [" + start + ", " + end + ")");
        }
    }

    /// How many bytes the range holds.
    public long length() {
        return end - start;
    }

    /// Whether the byte at `position` is in the range.
    public boolean contains(long position) {
        return position >= start && position < end;
    }
}
