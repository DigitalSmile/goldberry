package io.github.digitalsmile.goldberry.media.platform.macos;

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
import io.github.digitalsmile.goldberry.media.codec.Received;
import io.github.digitalsmile.goldberry.media.codec.VideoFrame;
import io.github.digitalsmile.goldberry.media.ffi.Demuxer;
import io.github.digitalsmile.goldberry.media.ffi.FfmpegLibraries;
import io.github.digitalsmile.goldberry.media.io.MemoryIO;
import io.github.digitalsmile.goldberry.media.io.Source;

/// The clips of `fixtures/` (made by `src/test/fixtures/make-fixtures.sh`), and
/// the loop that feeds one track of a clip to a decoder: `:media`'s FFmpeg
/// demuxes, and the decoder under test decodes.
final class Fixtures {

    private static final String DIRECTORY = "/io/github/digitalsmile/goldberry/media/platform/fixtures/";

    private Fixtures() {}

    /// The bytes of the clip `name`.
    static byte[] bytes(String name) {
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
    record FrameHash(long ptsNanos, String md5) {}

    /// Every picture FFmpeg's own decoder made of `name`'s video, in order.
    static List<FrameHash> frameHashes(String name) {
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
    /// what FFmpeg's `framemd5` hashes.
    static String md5(VideoFrame frame) {
        try {
            var digest = MessageDigest.getInstance("MD5");
            for (var plane = 0; plane < frame.format().planes(); plane++) {
                var rowBytes = frame.format().planeRowBytes(plane, frame.width());
                var rows = frame.format().planeRows(plane, frame.height());
                var stride = frame.strides().get(plane);
                var data = frame.planes().get(plane);
                var row = new byte[rowBytes];
                for (var y = 0; y < rows; y++) {
                    MemorySegment.copy(data, JAVA_BYTE, (long) y * stride, row, 0, rowBytes);
                    digest.update(row);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /// Opens `name` with `:media`'s demuxer, or skips without FFmpeg.
    static Demuxer demux(String name) {
        FfmpegRequirement.enforce();
        return Demuxer.open(FfmpegLibraries.get(), Source.of(URI.create("mem:///" + name)), new MemoryIO(bytes(name)));
    }

    /// Decodes the default `type` track of `name` from start to end with the
    /// decoder `open` makes, handing each frame to `consumer` while it is still
    /// borrowed.
    static void decode(String name, MediaType type, Function<DecoderRequest, Decoder> open, Consumer<Frame> consumer) {
        try (var demuxer = demux(name)) {
            var track = demuxer.info().defaultTrack(type).orElseThrow();
            demuxer.select(Set.of(track.index()));
            try (var decoder = open.apply(demuxer.request(track.index()))) {
                run(demuxer, decoder, consumer);
            }
        }
    }

    /// Runs the Engine's decode loop until the decoder ends.
    static void run(Demuxer demuxer, Decoder decoder, Consumer<Frame> consumer) {
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
