package dev.goldberry.media.ffi;

import static java.lang.foreign.ValueLayout.ADDRESS;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/// What this FFmpeg build decodes and demuxes, read from the build itself
/// (`av_codec_iterate`, `av_demuxer_iterate`), not from a list kept here.
public final class FfmpegCapabilities {

    private static final Pattern COMMA = Pattern.compile(",");

    private FfmpegCapabilities() {}

    /// The *codec* names this build has a decoder for: `av1`, not `libdav1d`,
    /// which is the name `CodecId` maps.
    public static Set<String> decodableCodecs(Ffmpeg ffmpeg) {
        var names = new TreeSet<String>();
        try (var arena = Arena.ofConfined()) {
            var opaque = arena.allocate(ADDRESS);
            for (var codec = ffmpeg.codec().codecIterate().call(opaque);
                    !codec.equals(MemorySegment.NULL);
                    codec = ffmpeg.codec().codecIterate().call(opaque)) {
                if (ffmpeg.codec().isDecoder().call(codec) != 0) {
                    names.add(ffmpeg.codecName(AvCodecView.id(codec)));
                }
            }
        }
        return names;
    }

    /// The format names this build demuxes. A demuxer that answers to several
    /// (`mov,mp4,m4a,3gp,3g2,mj2`) contributes each.
    public static Set<String> demuxers(Ffmpeg ffmpeg) {
        var names = new TreeSet<String>();
        try (var arena = Arena.ofConfined()) {
            var opaque = arena.allocate(ADDRESS);
            for (var format = ffmpeg.format().demuxerIterate().call(opaque);
                    !format.equals(MemorySegment.NULL);
                    format = ffmpeg.format().demuxerIterate().call(opaque)) {
                COMMA.splitAsStream(AvCodecView.formatName(format))
                        .map(String::strip)
                        .filter(name -> !name.isEmpty())
                        .forEach(names::add);
            }
        }
        return names;
    }
}
