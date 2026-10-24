package dev.goldberry.media.container;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import dev.goldberry.media.io.MemoryIO;
import dev.goldberry.media.io.Source;

/// What demuxing costs in Java, now that no native code reads a byte: every
/// packet of a clip read out of memory, through the Matroska and the MP4
/// readers, as packets a second. A clip is a second long, so the number is
/// how many times faster than real time the demuxer alone runs; the decoders
/// are the work, and this must stay a rounding error beside them.
///
/// Read by a person; `:media:benchmark` runs it. It needs no native library.
class DemuxBenchmark {

    private static final int WARMUP = 10;
    private static final int RUNS = 50;

    private static byte[] fixture(String name) {
        try (var in = DemuxBenchmark.class.getResourceAsStream("/dev/goldberry/media/fixtures/" + name)) {
            return Objects.requireNonNull(in, name).readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// Every packet of `bytes`, closed as it goes; answers how many there were.
    private static int demux(String name, byte[] bytes) {
        var count = 0;
        try (var container = Containers.open(Source.of(URI.create("mem:///" + name)), new MemoryIO(bytes))) {
            for (var packet = container.read(); packet != null; packet = container.read()) {
                packet.close();
                count++;
            }
        }
        return count;
    }

    @Test
    void packetsASecond() {
        System.out.printf("Demuxing a one-second clip from memory, median of %d runs%n", RUNS);
        for (var name : new String[] {"clip-vp9.webm", "clip-av1.mp4", "tone-opus.webm", "tone.flac", "tone.mp3"}) {
            var bytes = fixture(name);
            var packets = demux(name, bytes);
            for (var i = 0; i < WARMUP; i++) {
                demux(name, bytes);
            }
            var times = new double[RUNS];
            for (var i = 0; i < RUNS; i++) {
                var started = System.nanoTime();
                demux(name, bytes);
                times[i] = (System.nanoTime() - started) / 1e6;
            }
            Arrays.sort(times);
            var median = times[RUNS / 2];
            System.out.printf(
                    "  %-16s %3d packets, %6d bytes: %.3f ms a pass, %.0f packets/s%n",
                    name, packets, bytes.length, median, packets / median * 1000);
        }
    }
}
