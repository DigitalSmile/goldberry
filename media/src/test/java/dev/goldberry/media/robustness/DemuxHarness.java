package dev.goldberry.media.robustness;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

import dev.goldberry.media.MediaException;
import dev.goldberry.media.Track;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.container.Container;
import dev.goldberry.media.container.Containers;
import dev.goldberry.media.io.MemoryIO;
import dev.goldberry.media.io.Source;

/// Opens a file with [Containers#open] and does to the container what the
/// Engine's demux thread does, holding it to its contract while the bytes
/// under it are damaged.
///
/// What a container may do with damaged bytes: read them, refuse them, or stop
/// at them with a [MediaException]. What it may not do: throw anything else,
/// read forever, hand out a packet whose memory cannot be read or that belongs
/// to a stream nobody selected, or fail to close. The reading ends: a packet
/// costs at least one byte of the file, so a container that hands out many more
/// packets than the file has bytes is taken to be stuck, and the test's
/// `@Timeout` catches one that stops handing out anything.
final class DemuxHarness {

    /// What reading a file gave: every packet of every stream, and whether
    /// the container refused or stopped.
    ///
    /// @param requests the tracks' requests, read while the container was open;
    ///                 their extradata copied out
    /// @param samples  each stream's packets, in file order
    /// @param failed   whether a [MediaException] ended it
    record Demuxed(Map<Integer, Request> requests, Map<Integer, List<DemuxedTrack.Sample>> samples, boolean failed) {}

    /// A track's request, with its extradata copied out of the container.
    ///
    /// @param track     the track
    /// @param request   the request, whose extradata is no longer valid
    /// @param extradata the extradata's bytes
    @SuppressWarnings("ArrayRecordComponent")
    record Request(Track track, DecoderRequest request, byte[] extradata) {}

    /// How the bytes reach the container.
    ///
    /// @param chunk     the most bytes one read returns
    /// @param seekable  whether the source seeks
    /// @param sizeKnown whether it says its size
    record Shape(int chunk, boolean seekable, boolean sizeKnown) {

        static final Shape PLAIN = new Shape(Integer.MAX_VALUE, true, true);

        /// A shape picked by `random`: mostly plain, sometimes short reads, an
        /// unknown size or a stream that cannot seek.
        static Shape any(SplittableRandom random) {
            return switch (random.nextInt(6)) {
                case 0 -> new Shape(1 + random.nextInt(97), true, true);
                case 1 -> new Shape(Integer.MAX_VALUE, true, false);
                case 2 -> new Shape(1 + random.nextInt(4096), false, false);
                default -> PLAIN;
            };
        }
    }

    private DemuxHarness() {}

    /// Opens `file`, reads every request, reads some packets, seeks around,
    /// and reads to the end, keeping what it read.
    static Demuxed demux(String name, byte[] file, Shape shape, SplittableRandom random, String context) {
        var io = new MemoryIO(file);
        io.chunk = shape.chunk();
        io.seekable = shape.seekable();
        io.sizeKnown = shape.sizeKnown();
        var requests = new LinkedHashMap<Integer, Request>();
        var samples = new LinkedHashMap<Integer, List<DemuxedTrack.Sample>>();
        Container container;
        try {
            container = Containers.open(Source.of(URI.create("mem:///" + name)), io);
        } catch (MediaException e) {
            return new Demuxed(requests, samples, true);
        } catch (RuntimeException e) {
            throw new AssertionError(context + ": opening threw " + e, e);
        }
        var failed = false;
        try {
            failed = !use(container, file.length, random, requests, samples, context);
        } catch (RuntimeException e) {
            throw new AssertionError(context + ": " + e, e);
        } finally {
            try {
                container.close();
                container.close();
            } catch (RuntimeException e) {
                throw new AssertionError(context + ": close threw " + e, e);
            }
        }
        return new Demuxed(requests, samples, failed);
    }

    /// @return false when a [MediaException] stopped it
    private static boolean use(
            Container container,
            int fileLength,
            SplittableRandom random,
            Map<Integer, Request> requests,
            Map<Integer, List<DemuxedTrack.Sample>> samples,
            String context) {
        try {
            var info = container.info();
            assertNotNull(info.tracks(), context + ": tracks");
            var streams = new HashSet<Integer>();
            for (var track : info.tracks()) {
                streams.add(track.index());
                samples.put(track.index(), new ArrayList<>());
                if (track.attachedPicture()) {
                    // Cover art is never decoded as a stream, so the Engine
                    // never asks for its request.
                    continue;
                }
                assertNotNull(container.timeBase(track.index()), context + ": time base");
                var request = container.request(track.index());
                requests.put(
                        track.index(),
                        new Request(track, request, request.extradata().toArray(JAVA_BYTE)));
            }
            container.select(streams);
            var budget = new int[] {16 * fileLength + 4096};
            // Some packets, then a seek or two, as a player that starts and
            // then jumps; then the rest.
            var before = random.nextInt(1 + fileLength / 256);
            if (!read(container, streams, before, budget, samples, context)) {
                return true;
            }
            if (info.seekable()) {
                var duration = info.duration().map(d -> d.toNanos()).orElse(10_000_000_000L);
                for (var seeks = random.nextInt(3); seeks > 0; seeks--) {
                    var target = switch (random.nextInt(4)) {
                        case 0 -> 0L;
                        case 1 -> duration;
                        case 2 -> duration + 1_000_000_000L;
                        default -> random.nextLong(Math.max(1, duration));
                    };
                    container.seek(target);
                    read(container, streams, 1 + random.nextInt(8), budget, samples, context);
                }
            }
            read(container, streams, Integer.MAX_VALUE, budget, samples, context);
            return true;
        } catch (MediaException e) {
            return false;
        }
    }

    /// Reads up to `count` packets.
    ///
    /// @return whether the end was not reached
    private static boolean read(
            Container container,
            Set<Integer> streams,
            int count,
            int[] budget,
            Map<Integer, List<DemuxedTrack.Sample>> samples,
            String context) {
        for (var i = 0; i < count; i++) {
            var packet = container.read();
            if (packet == null) {
                return false;
            }
            try (var read = packet) {
                assertTrue(
                        streams.contains(read.streamIndex()),
                        () -> context + ": a packet of stream " + read.streamIndex() + ", which was not selected");
                assertNotNull(read.timeBase(), context + ": a packet's time base");
                var data = read.data().toArray(JAVA_BYTE);
                var alpha = read.alpha().toArray(JAVA_BYTE);
                samples.computeIfAbsent(read.streamIndex(), _ -> new ArrayList<>())
                        .add(new DemuxedTrack.Sample(
                                data, alpha, read.pts(), read.dts(), read.duration(), read.keyframe()));
            }
            if (--budget[0] < 0) {
                throw new AssertionError(context + ": more packets than the file has bytes; the reading never ends");
            }
        }
        return true;
    }
}
