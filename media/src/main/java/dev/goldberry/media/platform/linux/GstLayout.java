package dev.goldberry.media.platform.linux;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/// Where the fields the providers read and write sit in GStreamer's public
/// structs, on a 64-bit system, and the check that they are there.
///
/// GStreamer defines these fields in its headers and as macros, with no
/// function to reach them, so a binding has to know the offsets. They have been
/// ABI since 1.0. They were derived from the 1.x headers, not measured with a
/// compiler: no machine that ran this had the development headers. So
/// [#check] measures them against the library that is loaded, before any
/// decoder is opened. GStreamer is made to write values it defines, and the
/// check reads them back here. A library that disagrees leaves the providers
/// unavailable, with the difference as the reason, rather than decoding
/// through wrong offsets.
final class GstLayout {

    /// `GstMiniObject.refcount`.
    static final long MINI_OBJECT_REFCOUNT = 8;
    /// `GstMiniObject.flags`, which a buffer's flags are.
    static final long MINI_OBJECT_FLAGS = 16;

    /// `GstBuffer.pool`, after the 64-byte `GstMiniObject`.
    static final long BUFFER_POOL = 64;
    /// `GstBuffer.pts`, in nanoseconds.
    static final long BUFFER_PTS = 72;
    /// `GstBuffer.dts`.
    static final long BUFFER_DTS = 80;
    /// `GstBuffer.duration`.
    static final long BUFFER_DURATION = 88;
    /// `GstBuffer.offset`.
    static final long BUFFER_OFFSET = 96;
    /// `GstBuffer.offset_end`, the last field.
    static final long BUFFER_OFFSET_END = 104;
    /// The bytes of `GstBuffer` up to the end of its last field.
    static final long BUFFER = 112;

    /// `GST_BUFFER_FLAG_DELTA_UNIT`: a buffer that cannot be decoded alone.
    static final int BUFFER_FLAG_DELTA_UNIT = 1 << 13;

    /// `GstMapInfo.data`.
    static final long MAP_DATA = 16;
    /// `GstMapInfo.size`.
    static final long MAP_SIZE = 24;
    /// `GstMapInfo.maxsize`.
    static final long MAP_MAXSIZE = 32;
    /// `sizeof(GstMapInfo)`, 104, rounded up: what is allocated for one.
    static final long MAP_INFO = 128;

    /// `GstVideoInfo.width`.
    static final long VIDEO_INFO_WIDTH = 16;
    /// `GstVideoInfo.height`.
    static final long VIDEO_INFO_HEIGHT = 20;
    /// `GstVideoInfo.size`: the bytes of a picture in the default layout.
    static final long VIDEO_INFO_SIZE = 24;
    /// `GstVideoInfo.colorimetry.range`.
    static final long VIDEO_INFO_RANGE = 40;
    /// `GstVideoInfo.colorimetry.matrix`.
    static final long VIDEO_INFO_MATRIX = 44;
    /// `GstVideoInfo.offset[0]`: four `gsize`s.
    static final long VIDEO_INFO_OFFSET = 72;
    /// `GstVideoInfo.stride[0]`: four `gint`s.
    static final long VIDEO_INFO_STRIDE = 104;
    /// `sizeof(GstVideoInfo)`, 152, rounded up: what is allocated for one.
    static final long VIDEO_INFO = 256;

    /// `GstVideoColorimetry.matrix`, after `range`.
    static final long COLORIMETRY_MATRIX = 4;

    /// `GST_VIDEO_COLOR_RANGE_0_255`: full range.
    static final int RANGE_FULL = 1;
    /// `GST_VIDEO_COLOR_RANGE_16_235`: limited range.
    static final int RANGE_LIMITED = 2;
    /// `GST_VIDEO_COLOR_MATRIX_BT709`.
    static final int MATRIX_BT709 = 3;
    /// `GST_VIDEO_COLOR_MATRIX_BT601`.
    static final int MATRIX_BT601 = 4;
    /// `GST_VIDEO_COLOR_MATRIX_BT2020`.
    static final int MATRIX_BT2020 = 6;

    private GstLayout() {}

    /// Measures every offset and enumeration value above against the loaded
    /// library.
    ///
    /// @return what disagrees, one line each; empty when everything agrees
    @SuppressWarnings("restricted")
    static List<String> check(Gst gst, GstVideo video) {
        var problems = new ArrayList<String>();

        // A new buffer: one reference, no pool, and every time and offset NONE.
        var buffer = gst.newBuffer(16);
        try (var arena = Arena.ofConfined()) {
            var fields = buffer.reinterpret(BUFFER);
            expect(problems, "GstBuffer refcount", 1, fields.get(JAVA_INT, MINI_OBJECT_REFCOUNT));
            expect(problems, "GstBuffer pool", 0, fields.get(JAVA_LONG, BUFFER_POOL));
            for (var offset : new long[] {BUFFER_PTS, BUFFER_DTS, BUFFER_DURATION, BUFFER_OFFSET, BUFFER_OFFSET_END}) {
                expect(problems, "GstBuffer field at " + offset, -1, fields.get(JAVA_LONG, offset));
            }

            var map = arena.allocate(MAP_INFO);
            gst.mapRead(buffer, map);
            try {
                expect(problems, "GstMapInfo size", 16, map.get(JAVA_LONG, MAP_SIZE));
                if (map.get(ADDRESS, MAP_DATA).equals(MemorySegment.NULL) || map.get(JAVA_LONG, MAP_MAXSIZE) < 16) {
                    problems.add("GstMapInfo data and maxsize are not where they should be");
                }
            } finally {
                gst.unmap(buffer, map);
            }

            // NV12 at 160×90 in the default layout: rows of 160, chroma after 90 of
            // them, and BT.601 limited range, the default below 576 rows.
            var info = arena.allocate(VIDEO_INFO);
            video.infoForFormat(info, "NV12", 160, 90);
            expect(problems, "GstVideoInfo width", 160, info.get(JAVA_INT, VIDEO_INFO_WIDTH));
            expect(problems, "GstVideoInfo height", 90, info.get(JAVA_INT, VIDEO_INFO_HEIGHT));
            expect(problems, "GstVideoInfo size", 160 * 90 * 3 / 2, info.get(JAVA_LONG, VIDEO_INFO_SIZE));
            expect(problems, "GstVideoInfo offset[1]", 160 * 90, info.get(JAVA_LONG, VIDEO_INFO_OFFSET + 8));
            expect(problems, "GstVideoInfo stride[1]", 160, info.get(JAVA_INT, VIDEO_INFO_STRIDE + 4));
            expect(problems, "GstVideoInfo range", RANGE_LIMITED, info.get(JAVA_INT, VIDEO_INFO_RANGE));
            expect(problems, "GstVideoInfo matrix at 90 rows", MATRIX_BT601, info.get(JAVA_INT, VIDEO_INFO_MATRIX));

            // I420 at 1280×720: three planes, and BT.709 from 720 rows.
            video.infoForFormat(info, "I420", 1280, 720);
            expect(problems, "GstVideoInfo stride[1]", 640, info.get(JAVA_INT, VIDEO_INFO_STRIDE + 4));
            expect(
                    problems,
                    "GstVideoInfo offset[2]",
                    1280 * 720 + 640 * 360,
                    info.get(JAVA_LONG, VIDEO_INFO_OFFSET + 16));
            expect(problems, "GstVideoInfo matrix at 720 rows", MATRIX_BT709, info.get(JAVA_INT, VIDEO_INFO_MATRIX));

            // The colorimetry names, for the enumeration values.
            var colorimetry = arena.allocate(16);
            video.colorimetry(colorimetry, "bt2020");
            expect(
                    problems,
                    "GST_VIDEO_COLOR_MATRIX_BT2020",
                    MATRIX_BT2020,
                    colorimetry.get(JAVA_INT, COLORIMETRY_MATRIX));
            video.colorimetry(colorimetry, "bt709");
            expect(
                    problems,
                    "GST_VIDEO_COLOR_MATRIX_BT709",
                    MATRIX_BT709,
                    colorimetry.get(JAVA_INT, COLORIMETRY_MATRIX));
        } finally {
            gst.unrefMini(buffer);
        }

        // An error GStreamer raises, for GError's message.
        try {
            gst.unref(gst.parseLaunch("goldberry-no-such-element"));
            problems.add("gst_parse_launch accepted an element that does not exist");
        } catch (GstException e) {
            if (e.getMessage() == null || e.getMessage().endsWith(": ")) {
                problems.add("GError's message is not where it should be");
            }
        }
        return List.copyOf(problems);
    }

    private static void expect(List<String> problems, String what, long expected, long actual) {
        if (expected != actual) {
            problems.add(what + " is " + actual + ", not " + expected);
        }
    }
}
