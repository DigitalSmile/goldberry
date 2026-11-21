package dev.goldberry.natives.desktop.dragout.ole;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.MemorySegment;

/// The structures OLE's data transfer speaks in, 64-bit, and the codes it
/// answers with.
///
/// Read and written by offset rather than through `MemoryLayout`s: there are
/// two of them, each a handful of fields, and the offsets are the SDK's.
public final class OleStructs {

    /// `CF_UNICODETEXT`: UTF-16 text, NUL-terminated.
    public static final int CF_UNICODETEXT = 13;

    /// `CF_HDROP`: a `DROPFILES` and the paths after it.
    public static final int CF_HDROP = 15;

    /// `CF_DIBV5`: a `BITMAPV5HEADER` and the pixels after it.
    public static final int CF_DIBV5 = 17;

    /// `TYMED_HGLOBAL`.
    public static final int TYMED_HGLOBAL = 1;

    /// `TYMED_ISTREAM`.
    public static final int TYMED_ISTREAM = 4;

    /// `TYMED_ISTORAGE`.
    public static final int TYMED_ISTORAGE = 8;

    /// `DVASPECT_CONTENT`.
    public static final int DVASPECT_CONTENT = 1;

    /// `DATADIR_GET`.
    public static final int DATADIR_GET = 1;

    /// `DV_E_FORMATETC`: not a format this object has.
    public static final int DV_E_FORMATETC = 0x80040064;

    /// `DV_E_TYMED`: a format it has, but not in that medium.
    public static final int DV_E_TYMED = 0x80040069;

    /// `OLE_E_ADVISENOTSUPPORTED`.
    public static final int OLE_E_ADVISENOTSUPPORTED = 0x80040003;

    /// `DATA_S_SAMEFORMATETC`.
    public static final int DATA_S_SAMEFORMATETC = 0x00040130;

    /// `DRAGDROP_S_DROP`.
    public static final int DRAGDROP_S_DROP = 0x00040100;

    /// `DRAGDROP_S_CANCEL`.
    public static final int DRAGDROP_S_CANCEL = 0x00040101;

    /// `DRAGDROP_S_USEDEFAULTCURSORS`.
    public static final int DRAGDROP_S_USEDEFAULTCURSORS = 0x00040102;

    /// `DROPEFFECT_COPY`.
    public static final int DROPEFFECT_COPY = 1;

    /// `DROPEFFECT_MOVE`.
    public static final int DROPEFFECT_MOVE = 2;

    /// `DROPEFFECT_LINK`.
    public static final int DROPEFFECT_LINK = 4;

    /// `MK_LBUTTON`, `MK_RBUTTON` and `MK_MBUTTON`: the buttons a drag can be
    /// held by.
    public static final int MOUSE_BUTTONS = 0x1 | 0x2 | 0x10;

    /// `sizeof(FORMATETC)`: `cfFormat` (a `WORD`) at 0, `ptd` at 8, `dwAspect`
    /// at 16, `lindex` at 20, `tymed` at 24.
    public static final long FORMATETC_SIZE = 32;

    /// `sizeof(STGMEDIUM)`: `tymed` at 0, the handle or interface at 8,
    /// `pUnkForRelease` at 16.
    public static final long STGMEDIUM_SIZE = 24;

    private OleStructs() {}

    /// A `FORMATETC` as read from native memory.
    ///
    /// @param format the clipboard format, unsigned
    /// @param aspect `dwAspect`
    /// @param index  `lindex`
    /// @param tymed  the media the caller takes, as bits
    public record FormatEtc(int format, int aspect, int index, int tymed) {

        /// The one at `address`.
        @SuppressWarnings("restricted")
        public static FormatEtc read(MemorySegment address) {
            var at = address.reinterpret(FORMATETC_SIZE);
            return new FormatEtc(
                    Short.toUnsignedInt(at.get(JAVA_SHORT, 0)),
                    at.get(JAVA_INT, 16),
                    at.get(JAVA_INT, 20),
                    at.get(JAVA_INT, 24));
        }

        /// Writes `format` for content in `tymed` at `address`, as an
        /// enumerator hands one out.
        @SuppressWarnings("restricted")
        public static void write(MemorySegment address, int format, int tymed) {
            var at = address.reinterpret(FORMATETC_SIZE);
            at.fill((byte) 0);
            at.set(JAVA_SHORT, 0, (short) format);
            at.set(ADDRESS, 8, MemorySegment.NULL);
            at.set(JAVA_INT, 16, DVASPECT_CONTENT);
            at.set(JAVA_INT, 20, -1);
            at.set(JAVA_INT, 24, tymed);
        }
    }

    /// An `STGMEDIUM` as read from native memory.
    ///
    /// @param tymed   which medium it is
    /// @param handle  the `HGLOBAL` or interface pointer
    /// @param release `pUnkForRelease`
    public record Medium(int tymed, MemorySegment handle, MemorySegment release) {

        /// The one at `address`.
        @SuppressWarnings("restricted")
        public static Medium read(MemorySegment address) {
            var at = address.reinterpret(STGMEDIUM_SIZE);
            return new Medium(at.get(JAVA_INT, 0), at.get(ADDRESS, 8), at.get(ADDRESS, 16));
        }

        /// Writes a medium of `tymed` holding `handle`, which the receiver
        /// releases itself (`pUnkForRelease` null).
        @SuppressWarnings("restricted")
        public static void write(MemorySegment address, int tymed, MemorySegment handle) {
            var at = address.reinterpret(STGMEDIUM_SIZE);
            at.fill((byte) 0);
            at.set(JAVA_INT, 0, tymed);
            at.set(ADDRESS, 8, handle);
            at.set(ADDRESS, 16, MemorySegment.NULL);
        }
    }
}
