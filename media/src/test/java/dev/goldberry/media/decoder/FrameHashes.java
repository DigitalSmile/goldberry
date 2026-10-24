package dev.goldberry.media.decoder;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.container.ClipFixtures;
import dev.goldberry.media.container.Container;
import dev.goldberry.media.container.matroska.MatroskaContainer;

/// What FFmpeg decoded from the same clips, as `ffmpeg -f framemd5` wrote it:
/// one MD5 per picture over its planes, rows packed without stride padding, Y
/// then U then V. The software decoders are bit-exact by specification, so a
/// decoder here is held to those sums picture for picture.
public final class FrameHashes {

    private static final String EXPECTATIONS = "/dev/goldberry/media/container/";

    private FrameHashes() {}

    /// FFmpeg's sums for `name`'s first video track, in order: the fixture
    /// `<stem>-<extension>.framemd5`, made by
    /// `ffmpeg -i <clip> -map 0:v:0 -f framemd5 -`.
    public static List<String> expected(String name) {
        var dot = name.lastIndexOf('.');
        var path = EXPECTATIONS + name.substring(0, dot) + "-" + name.substring(dot + 1) + ".framemd5";
        String text;
        try (var in = Objects.requireNonNull(FrameHashes.class.getResourceAsStream(path), path)) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var sums = new ArrayList<String>();
        for (var line : text.split("\n")) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            var fields = line.split(",");
            sums.add(fields[fields.length - 1].strip());
        }
        return sums;
    }

    /// The MD5 of `frame`'s planes as `framemd5` sums them.
    public static String md5(VideoFrame frame) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        for (var plane = 0; plane < frame.format().planes(); plane++) {
            var rows = frame.format().planeRows(plane, frame.height());
            var rowBytes = frame.format().planeRowBytes(plane, frame.width());
            var stride = frame.strides().get(plane);
            var data = frame.planes().get(plane);
            var row = new byte[rowBytes];
            for (var y = 0; y < rows; y++) {
                MemorySegment.copy(data, JAVA_BYTE, (long) y * stride, row, 0, rowBytes);
                digest.update(row);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /// Decodes every picture of `name`'s first video track through `provider`,
    /// handing each to `consumer` while it is still borrowed.
    public static void decodeVideo(String name, DecoderProvider provider, Consumer<VideoFrame> consumer) {
        try (var container = MatroskaContainer.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            var track = container.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            container.select(Set.of(track.index()));
            var request = container.request(track.index());
            assertTrue(provider.supports(request), provider.name() + " supports " + request.codecName());
            try (var decoder = provider.open(request)) {
                drain(container, decoder, frame -> consumer.accept(assertInstanceOf(VideoFrame.class, frame)));
            }
        }
    }

    /// Runs the SPI's loop to the end.
    public static void drain(Container container, Decoder decoder, Consumer<Frame> consumer) {
        var ended = false;
        while (!ended) {
            switch (decoder.receive()) {
                case Received.Decoded(var frame) -> consumer.accept(frame);
                case Received.NeedsInput _ -> {
                    try (var packet = container.read()) {
                        if (packet == null) {
                            decoder.sendEnd();
                        } else {
                            assertTrue(decoder.send(packet), "a decoder that asked for input takes the packet");
                        }
                    }
                }
                case Received.Ended _ -> ended = true;
            }
        }
    }

    /// Asserts that `provider` decodes `name`'s pictures to FFmpeg's sums, in
    /// order, and answers how many there were.
    public static int assertMatchesFfmpeg(String name, DecoderProvider provider) {
        var expected = expected(name);
        var actual = new ArrayList<String>();
        decodeVideo(name, provider, frame -> actual.add(md5(frame)));
        assertEquals(expected.size(), actual.size(), name + ": picture count");
        for (var i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), actual.get(i), name + ": picture " + i);
        }
        return actual.size();
    }
}
