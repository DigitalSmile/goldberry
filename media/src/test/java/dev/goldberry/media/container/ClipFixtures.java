package dev.goldberry.media.container;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.io.MemoryIO;
import dev.goldberry.media.io.Source;

/// The encoded clips under `src/test/resources/dev/goldberry/media/fixtures`,
/// and what `ffprobe` said of their packets, for the container tests.
public final class ClipFixtures {

    private static final String FIXTURES = "/dev/goldberry/media/fixtures/";
    private static final String EXPECTATIONS = "/dev/goldberry/media/container/";

    private ClipFixtures() {}

    /// The clip's bytes.
    public static byte[] bytes(String name) {
        return resource(FIXTURES + name);
    }

    /// The clip, in memory.
    public static MemoryIO io(String name) {
        return new MemoryIO(bytes(name));
    }

    /// A source naming the clip, for the probe's record.
    public static Source source(String name) {
        return Source.of(URI.create("mem:///" + name));
    }

    /// One packet as `ffprobe -show_packets` listed it: the stream, the
    /// presentation time and the duration in the stream's time base, the size,
    /// and whether it is a keyframe. A time `ffprobe` wrote as `N/A`, an
    /// attached picture's, is [Packet#NO_TIMESTAMP].
    public record Expected(int stream, long pts, long duration, long size, boolean keyframe) {}

    /// What `ffprobe` listed for `name`'s packets, in file order: the fixture
    /// `<stem>-<extension>.packets`, made by
    /// `ffprobe -show_entries packet=stream_index,pts,duration,size,flags -of csv=p=0`.
    public static List<Expected> packets(String name) {
        var dot = name.lastIndexOf('.');
        var text = new String(
                resource(EXPECTATIONS + name.substring(0, dot) + "-" + name.substring(dot + 1) + ".packets"),
                StandardCharsets.UTF_8);
        var packets = new ArrayList<Expected>();
        for (var line : text.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            var fields = line.split(",");
            packets.add(new Expected(
                    Integer.parseInt(fields[0]),
                    time(fields[1]),
                    time(fields[2]),
                    Long.parseLong(fields[3]),
                    fields[4].startsWith("K")));
        }
        return packets;
    }

    private static long time(String field) {
        return field.equals("N/A") ? Packet.NO_TIMESTAMP : Long.parseLong(field);
    }

    private static byte[] resource(String path) {
        try (var in = Objects.requireNonNull(ClipFixtures.class.getResourceAsStream(path), path)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
