package io.github.digitalsmile.goldberry.media.platform.linux;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// `libgstvideo-1.0`: what a decoded picture's caps say about its planes and
/// its colour, as a `GstVideoInfo` ([GstLayout]).
final class GstVideo {

    /// `gboolean gst_video_info_from_caps(GstVideoInfo *info, const GstCaps *caps)`, and
    /// `gboolean gst_video_colorimetry_from_string(GstVideoColorimetry *cinfo, const gchar *color)`
    private static final MethodHandle FD_int_pointer_pointer =
            GstLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    /// `gboolean gst_video_info_set_format(GstVideoInfo *info, GstVideoFormat format, guint width, guint height)`
    private static final MethodHandle FD_gst_video_info_set_format =
            GstLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));

    /// `GstVideoFormat gst_video_format_from_string(const gchar *format)`
    private static final MethodHandle FD_gst_video_format_from_string =
            GstLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /// `void gst_video_info_init(GstVideoInfo *info)`
    private static final MethodHandle FD_gst_video_info_init = GstLibrary.link(FunctionDescriptor.ofVoid(ADDRESS));

    private final MemorySegment infoFromCaps;
    private final MemorySegment infoSetFormat;
    private final MemorySegment infoInit;
    private final MemorySegment formatFromString;
    private final MemorySegment colorimetryFromString;

    GstVideo(SymbolLookup lookup) {
        var l = GstLibrary.GST_VIDEO;
        this.infoFromCaps = l.symbol(lookup, "gst_video_info_from_caps");
        this.infoSetFormat = l.symbol(lookup, "gst_video_info_set_format");
        this.infoInit = l.symbol(lookup, "gst_video_info_init");
        this.formatFromString = l.symbol(lookup, "gst_video_format_from_string");
        this.colorimetryFromString = l.symbol(lookup, "gst_video_colorimetry_from_string");
    }

    /// Fills `info`, a [GstLayout#VIDEO_INFO]-sized segment, from raw video `caps`.
    ///
    /// @throws GstException when the caps are not raw video
    void infoFromCaps(MemorySegment info, MemorySegment caps) {
        try {
            FD_gst_video_info_init.invokeExact(infoInit, info);
            if ((int) FD_int_pointer_pointer.invokeExact(infoFromCaps, info, caps) == 0) {
                throw new GstException("a decoded picture's caps are not raw video");
            }
        } catch (Throwable t) {
            throw t instanceof RuntimeException e ? e : GstLibrary.failure("gst_video_info_from_caps", t);
        }
    }

    /// Fills `info` for a `format` picture of `width` × `height` in the default
    /// layout. For [GstLayout]'s check.
    void infoForFormat(MemorySegment info, String format, int width, int height) {
        try (var arena = Arena.ofConfined()) {
            var id = (int) FD_gst_video_format_from_string.invokeExact(formatFromString, arena.allocateFrom(format));
            FD_gst_video_info_init.invokeExact(infoInit, info);
            if ((int) FD_gst_video_info_set_format.invokeExact(infoSetFormat, info, id, width, height) == 0) {
                throw new GstException("gst_video_info_set_format refused " + format);
            }
        } catch (Throwable t) {
            throw t instanceof RuntimeException e ? e : GstLibrary.failure("gst_video_info_set_format", t);
        }
    }

    /// Fills `colorimetry`, four `gint`s, from a colorimetry name such as
    /// `bt709`. For [GstLayout]'s check.
    void colorimetry(MemorySegment colorimetry, String name) {
        try (var arena = Arena.ofConfined()) {
            if ((int) FD_int_pointer_pointer.invokeExact(colorimetryFromString, colorimetry, arena.allocateFrom(name))
                    == 0) {
                throw new GstException("gst_video_colorimetry_from_string refused " + name);
            }
        } catch (Throwable t) {
            throw t instanceof RuntimeException e ? e : GstLibrary.failure("gst_video_colorimetry_from_string", t);
        }
    }
}
