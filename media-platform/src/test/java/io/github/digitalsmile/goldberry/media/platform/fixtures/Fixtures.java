package io.github.digitalsmile.goldberry.media.platform.fixtures;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.MemorySegment;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import io.github.digitalsmile.goldberry.media.FfmpegRequirement;
import io.github.digitalsmile.goldberry.media.codec.Decoder;
import io.github.digitalsmile.goldberry.media.codec.DecoderRequest;
import io.github.digitalsmile.goldberry.media.codec.Frame;
import io.github.digitalsmile.goldberry.media.codec.MediaType;
import io.github.digitalsmile.goldberry.media.codec.PixelFormat;
import io.github.digitalsmile.goldberry.media.codec.Received;
import io.github.digitalsmile.goldberry.media.codec.VideoFrame;
import io.github.digitalsmile.goldberry.media.ffi.Demuxer;
import io.github.digitalsmile.goldberry.media.ffi.FfmpegLibraries;
import io.github.digitalsmile.goldberry.media.io.MemoryIO;
import io.github.digitalsmile.goldberry.media.io.Source;

/// The clips of `fixtures/` (made by `src/test/fixtures/make-fixtures.sh`), and
/// the loop that feeds one track of a clip to a decoder: `:media`'s FFmpeg
/// demuxes, and the decoder under test decodes.
public final class Fixtures {

    private static final String DIRECTORY = "/io/github/digitalsmile/goldberry/media/platform/fixtures/";

    private Fixtures() {}

    /// The bytes of the clip `name`.
    public static byte[] bytes(String name) {
        try (var in = Fixtures.class.getResourceAsStream(DIRECTORY + name)) {
            if (in == null) {
                throw new IllegalStateException("no fixture " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// One line of a `.framemd5` file: a picture's time in the stream's 1/25 s
    /// ticks, and the MD5 of its planes packed row after row.
    public record FrameHash(long ptsNanos, String md5) {}

    /// Every picture FFmpeg's own decoder made of `name`'s video, in order.
    public static List<FrameHash> frameHashes(String name) {
        var hashes = new ArrayList<FrameHash>();
        for (var line : new String(bytes(name + ".framemd5"), StandardCharsets.US_ASCII).split("\n")) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            var fields = line.split(",");
            // Every clip is 25 fps, and its framemd5 time base 1/25.
            var pts = Long.parseLong(fields[2].trim()) * 40_000_000L;
            hashes.add(new FrameHash(pts, fields[5].trim()));
        }
        return hashes;
    }

    /// The MD5 of `frame`'s visible picture, packed with no padding between rows:
    /// what FFmpeg's `framemd5` hashes, in the semi-planar layout the fixtures'
    /// hashes were made in (NV12, or P010 for 10-bit).
    ///
    /// A decoder that hands over planar pictures (I420, I010), as GStreamer's
    /// software decoders do, is hashed as if its chroma were interleaved, and a
    /// 10-bit sample as if it sat in the high bits. Both rearrangements lose
    /// nothing, so a picture that hashes the same carries the same samples.
    public static String md5(VideoFrame frame) {
        try {
            var digest = MessageDigest.getInstance("MD5");
            var format = frame.format();
            var shift = format == PixelFormat.I010 ? 6 : 0;
            var bytes = format.bytesPerSample();
            // Luma: the same in every layout, but for where a 10-bit sample sits.
            digest.update(row(frame, 0, frame.height(), shift));
            if (format.planes() == 2) {
                for (var y = 0; y < format.planeRows(1, frame.height()); y++) {
                    digest.update(rowAt(frame, 1, y));
                }
            } else {
                var chroma = format.planeRowBytes(1, frame.width());
                for (var y = 0; y < format.planeRows(1, frame.height()); y++) {
                    var u = shifted(rowAt(frame, 1, y), shift);
                    var v = shifted(rowAt(frame, 2, y), shift);
                    var interleaved = new byte[chroma * 2];
                    for (var x = 0; x < chroma; x += bytes) {
                        System.arraycopy(u, x, interleaved, 2 * x, bytes);
                        System.arraycopy(v, x, interleaved, 2 * x + bytes, bytes);
                    }
                    digest.update(interleaved);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /// Every row of `plane`, one after the other, each 16-bit sample moved up by
    /// `shift` bits.
    private static byte[] row(VideoFrame frame, int plane, int height, int shift) {
        var rows = frame.format().planeRows(plane, height);
        var rowBytes = frame.format().planeRowBytes(plane, frame.width());
        var packed = new byte[rowBytes * rows];
        for (var y = 0; y < rows; y++) {
            System.arraycopy(shifted(rowAt(frame, plane, y), shift), 0, packed, y * rowBytes, rowBytes);
        }
        return packed;
    }

    /// Row `y` of `plane`: the bytes that hold picture, without the stride's padding.
    private static byte[] rowAt(VideoFrame frame, int plane, int y) {
        var rowBytes = frame.format().planeRowBytes(plane, frame.width());
        var row = new byte[rowBytes];
        MemorySegment.copy(
                frame.planes().get(plane), JAVA_BYTE, (long) y * frame.strides().get(plane), row, 0, rowBytes);
        return row;
    }

    /// `row`'s little-endian 16-bit samples moved up by `shift` bits: I010's low
    /// bits to P010's high ones. Unchanged for a shift of 0.
    private static byte[] shifted(byte[] row, int shift) {
        if (shift == 0) {
            return row;
        }
        var out = new byte[row.length];
        for (var i = 0; i + 1 < row.length; i += 2) {
            var sample = ((row[i] & 0xff) | (row[i + 1] & 0xff) << 8) << shift;
            out[i] = (byte) sample;
            out[i + 1] = (byte) (sample >>> 8);
        }
        return out;
    }

    /// Opens `name` with `:media`'s demuxer, or skips without FFmpeg.
    public static Demuxer demux(String name) {
        FfmpegRequirement.enforce();
        return Demuxer.open(FfmpegLibraries.get(), Source.of(URI.create("mem:///" + name)), new MemoryIO(bytes(name)));
    }

    /// Decodes the default `type` track of `name` from start to end with the
    /// decoder `open` makes, handing each frame to `consumer` while it is still
    /// borrowed.
    public static void decode(
            String name, MediaType type, Function<DecoderRequest, Decoder> open, Consumer<Frame> consumer) {
        try (var demuxer = demux(name)) {
            var track = demuxer.info().defaultTrack(type).orElseThrow();
            demuxer.select(Set.of(track.index()));
            try (var decoder = open.apply(demuxer.request(track.index()))) {
                run(demuxer, decoder, consumer);
            }
        }
    }

    /// Runs the Engine's decode loop until the decoder ends.
    public static void run(Demuxer demuxer, Decoder decoder, Consumer<Frame> consumer) {
        var ended = false;
        while (!ended) {
            switch (decoder.receive()) {
                case Received.Decoded(var frame) -> consumer.accept(frame);
                case Received.NeedsInput _ -> {
                    try (var packet = demuxer.read()) {
                        if (packet == null) {
                            decoder.sendEnd();
                        } else {
                            assertTrue(decoder.send(packet), "a decoder that asked for input refused it");
                        }
                    }
                }
                case Received.Ended _ -> ended = true;
            }
        }
    }
}
