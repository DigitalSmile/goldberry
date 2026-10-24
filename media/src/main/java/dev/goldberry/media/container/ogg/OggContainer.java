package dev.goldberry.media.container.ogg;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.MediaInfo;
import dev.goldberry.media.Track;
import dev.goldberry.media.chapter.VorbisComments;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.Rational;
import dev.goldberry.media.container.ByteReader;
import dev.goldberry.media.container.Container;
import dev.goldberry.media.io.MediaIO;
import dev.goldberry.media.io.Source;

/// Ogg (RFC 3533), read in Java: Opus (RFC 7845), Vorbis and FLAC in it.
///
/// Opening reads the BOS pages, one per logical stream, and names each
/// stream by its first packet; then the header pages, until the first page
/// that carries audio, which is where a seek to the start comes back to.
/// Reading walks the pages, joins the segments into packets across page
/// boundaries, and hands each audio packet over with a timestamp in samples
/// of its stream's rate: a page's granule position is the sample count at
/// the end of the last packet it completes, so the packets of the first page
/// read are counted back from it by their lengths, and every packet after
/// that counts on from the one before. An Opus stream's pre-skip is taken off
/// so that the sound starts at 0, and a stream's last page trims its last
/// packet to the granule, as FFmpeg's reader times the same files.
///
/// The duration is the last page's granule, found by reading the end of a
/// seekable source; a seek bisects the pages by their granule positions and
/// starts reading at the last page that ends at or before the target, so the
/// first packet out is at or before it, within a page or two, and a Vorbis
/// decoder gets the packet it needs to overlap with. A source that cannot
/// seek has neither.
///
/// A stream of a codec this build does not name is a track with
/// [CodecId#UNKNOWN], named by its first bytes, whose packets are not handed
/// over; a Skeleton stream is not a track at all. One chain only: a BOS page
/// after the data, a radio stream's next song, is read past.
public final class OggContainer implements Container {

    private static final Logger LOG = Logs.of(OggContainer.class);

    /// How much of a source's end is read for the last page of each stream:
    /// a page is at most 65 307 bytes, so the last one starts in here.
    private static final int TAIL = 256 * 1024;

    /// Whether `head`, a file's first bytes, begins an Ogg page.
    public static boolean isMagic(byte[] head) {
        return head.length >= OggPage.CAPTURE.length
                && Arrays.equals(head, 0, OggPage.CAPTURE.length, OggPage.CAPTURE, 0, OggPage.CAPTURE.length);
    }

    private final Source source;
    private final ByteReader in;
    private final Arena arena = Arena.ofShared();
    private final Map<Long, OggStream> bySerial = new HashMap<>();
    /// The streams that are tracks, by index.
    private final List<OggStream> streams = new ArrayList<>();
    private final Map<Integer, MemorySegment> extradata = new HashMap<>();
    private final Deque<Packet> pending = new ArrayDeque<>();
    private final Set<Long> unknownSerials = new HashSet<>();
    private final long dataStart;
    private final MediaInfo info;
    private @Nullable Set<Integer> selected;
    private boolean dataSeen;
    private boolean closed;

    private OggContainer(Source source, MediaIO io) {
        this.source = source;
        this.in = new ByteReader(io);
        var start = -1L;
        while (start < 0) {
            var pageStart = in.position();
            var page = OggPage.read(in);
            if (page == null) {
                if (bySerial.isEmpty()) {
                    throw new MediaException(new MediaError.InvalidData("not an Ogg file: no page in " + source.uri()));
                }
                start = in.position();
                break;
            }
            if (page.isFirst() && !bySerial.containsKey(page.serial())) {
                begin(page);
                continue;
            }
            if (streams.stream().noneMatch(stream -> stream.codec != CodecId.UNKNOWN)) {
                // Nothing here is decoded, so nothing is waited for: the page is
                // read again, and skipped, by the first read.
                in.seek(pageStart);
                start = pageStart;
                break;
            }
            process(page);
            if (dataSeen) {
                start = pageStart;
            }
        }
        this.dataStart = start;
        var lastGranules = io.isSeekable() ? lastGranules() : Map.<Long, Long>of();
        var tracks = new ArrayList<Track>();
        Optional<Duration> longest = Optional.empty();
        var durations = new HashMap<Integer, Duration>();
        for (var stream : streams) {
            var granule = lastGranules.get(stream.serial);
            if (granule != null && stream.codec != CodecId.UNKNOWN) {
                var duration = stream.timeBase.toDuration(Math.max(stream.pts(granule), 0));
                durations.put(stream.index, duration);
                if (longest.isEmpty() || duration.compareTo(longest.get()) > 0) {
                    longest = Optional.of(duration);
                }
            }
        }
        for (var stream : streams) {
            var duration = durations.containsKey(stream.index) ? Optional.of(durations.get(stream.index)) : longest;
            tracks.add(new Track(
                    stream.index, stream.codec, stream.name, stream.params, duration, stream.index == 0, false));
        }
        var chapters = streams.stream()
                .map(stream -> VorbisComments.chapters(stream.comments()))
                .filter(stated -> !stated.isEmpty())
                .findFirst()
                .orElse(List.of());
        this.info = new MediaInfo(source, longest, tracks, io.isSeekable(), io.isLive(), chapters);
    }

    /// Opens an Ogg file over `io`, positioned at its start.
    ///
    /// @throws MediaException [MediaError.InvalidData] when the bytes are not an
    ///                        Ogg file, or a known codec's headers do not parse,
    ///                        [MediaError.Io] when a read fails,
    ///                        [MediaError.Aborted] when `io` was closed during
    ///                        the open
    public static OggContainer open(Source source, MediaIO io) {
        return new OggContainer(source, io);
    }

    @Override
    public MediaInfo info() {
        return info;
    }

    @Override
    public void select(Set<Integer> streams) {
        selected = Set.copyOf(streams);
    }

    @Override
    public Rational timeBase(int stream) {
        return stream(stream).timeBase;
    }

    @Override
    public DecoderRequest request(int stream) {
        var entry = stream(stream);
        var record = extradata.computeIfAbsent(
                stream,
                _ -> entry.extradata().length == 0
                        ? MemorySegment.NULL
                        : arena.allocateFrom(ValueLayout.JAVA_BYTE, entry.extradata()));
        return new DecoderRequest(entry.codec, entry.name, entry.params, record, entry.timeBase);
    }

    private OggStream stream(int index) {
        if (index < 0 || index >= streams.size()) {
            throw new IllegalArgumentException("no track " + index + " in " + source.uri());
        }
        return streams.get(index);
    }

    @Override
    public @Nullable Packet read() {
        requireOpen();
        while (true) {
            var next = pending.poll();
            if (next != null) {
                if (isSelected(next.streamIndex())) {
                    return next;
                }
                // Read ahead by the open, before anyone chose.
                next.close();
                continue;
            }
            var page = OggPage.read(in);
            if (page == null) {
                return null;
            }
            process(page);
        }
    }

    @Override
    public void seek(long positionNanos) {
        requireOpen();
        if (!in.isSeekable()) {
            throw new MediaException(new MediaError.Io("the source cannot seek"));
        }
        pending.forEach(Packet::close);
        pending.clear();
        var target = dataStart;
        if (positionNanos > 0) {
            var stream = streams.stream()
                    .filter(candidate -> candidate.codec != CodecId.UNKNOWN && isSelected(candidate.index))
                    .findFirst()
                    .or(() -> streams.stream()
                            .filter(candidate -> candidate.codec != CodecId.UNKNOWN)
                            .findFirst());
            if (stream.isPresent()) {
                var granule = stream.get().timeBase.fromNanos(positionNanos) + stream.get().preSkip;
                target = bisect(stream.get(), granule);
            }
        }
        in.seek(target);
        bySerial.values().forEach(OggStream::reset);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        pending.forEach(Packet::close);
        pending.clear();
        arena.close();
    }

    // ---- pages -----------------------------------------------------------------

    /// A BOS page: the stream it begins, named by its first packet.
    private void begin(OggPage page) {
        var split = page.split();
        if (split.completed().isEmpty()) {
            throw new MediaException(new MediaError.InvalidData(
                    "the first page of stream " + page.serial() + " does not complete its first packet"));
        }
        var first = split.completed().getFirst();
        var stream = OggStream.of(page.serial(), streams.size(), first);
        bySerial.put(page.serial(), stream);
        if (!stream.ignored) {
            streams.add(stream);
        }
        if (stream.codec == CodecId.UNKNOWN && !stream.ignored) {
            LOG.debug("{}: an Ogg stream of {} is not decoded", source.uri(), stream.name);
        }
        // Anything else on the page, which no known codec's first page carries.
        for (var packet : split.completed().subList(1, split.completed().size())) {
            if (!stream.header(packet)) {
                LOG.debug("{}: a packet beside the first of stream {}", source.uri(), page.serial());
            }
        }
        stream.fragment = split.tail().length > 0 ? split.tail() : null;
    }

    /// Reads a page's packets: joins the one that continues from the page
    /// before, takes the headers, times the audio and queues it.
    private void process(OggPage page) {
        var stream = bySerial.get(page.serial());
        if (stream == null) {
            if (unknownSerials.add(page.serial())) {
                LOG.debug("{}: pages of stream {}, which began after the data; read past", source.uri(), page.serial());
            }
            return;
        }
        var split = page.split();
        var packets = new ArrayList<>(split.completed());
        var tail = split.tail();
        if (page.isContinued()) {
            var fragment = stream.fragment;
            if (fragment == null) {
                // The start of this packet was before where reading began.
                if (packets.isEmpty()) {
                    tail = new byte[0];
                } else {
                    packets.removeFirst();
                }
            } else if (packets.isEmpty()) {
                tail = join(fragment, tail);
            } else {
                packets.set(0, join(fragment, packets.getFirst()));
            }
        }
        stream.fragment = tail.length > 0 ? tail : null;

        var audio = new ArrayList<byte[]>(packets.size());
        for (var packet : packets) {
            if (!stream.header(packet)) {
                audio.add(packet);
            }
        }
        if (audio.isEmpty()) {
            return;
        }
        if (stream.codec != CodecId.UNKNOWN) {
            dataSeen = true;
        }
        var durations = new long[audio.size()];
        var total = 0L;
        for (var i = 0; i < audio.size(); i++) {
            durations[i] = stream.timing().duration(audio.get(i));
            total += durations[i];
        }
        if (stream.next == OggStream.UNKNOWN && page.granule() >= 0) {
            stream.next = page.granule() - total;
        }
        var timed = stream.next != OggStream.UNKNOWN;
        if (timed && page.isLast() && page.granule() >= 0) {
            // The stream ends at the granule, whatever the last packet's own
            // length says: an encoder's padding.
            var at = stream.next;
            for (var i = 0; i < durations.length; i++) {
                if (at + durations[i] > page.granule()) {
                    durations[i] = Math.max(page.granule() - at, 0);
                }
                at += durations[i];
            }
        }
        var emit = !stream.ignored && stream.codec != CodecId.UNKNOWN && isSelected(stream.index);
        if (!emit) {
            if (timed) {
                stream.next += Arrays.stream(durations).sum();
            }
            return;
        }
        // One allocation for the page, shared by its packets: freed when the
        // last of them is closed, on whatever thread that is.
        @SuppressWarnings("MustBeClosedChecker")
        var shared = Arena.ofShared();
        var remaining = new AtomicInteger(audio.size());
        Runnable release = () -> {
            if (remaining.decrementAndGet() == 0) {
                shared.close();
            }
        };
        var size = 0L;
        for (var packet : audio) {
            size += packet.length;
        }
        var block = shared.allocate(Math.max(size, 1));
        var offset = 0L;
        for (var i = 0; i < audio.size(); i++) {
            var payload = audio.get(i);
            MemorySegment.copy(payload, 0, block, ValueLayout.JAVA_BYTE, offset, payload.length);
            var data = block.asSlice(offset, payload.length);
            offset += payload.length;
            var pts = timed ? stream.pts(stream.next) : Packet.NO_TIMESTAMP;
            if (timed) {
                stream.next += durations[i];
            }
            pending.add(Packet.owning(data, stream.index, pts, pts, durations[i], true, stream.timeBase, release));
        }
    }

    private static byte[] join(byte[] head, byte[] tail) {
        var out = Arrays.copyOf(head, head.length + tail.length);
        System.arraycopy(tail, 0, out, head.length, tail.length);
        return out;
    }

    // ---- the end, and seeking --------------------------------------------------

    /// The last granule position of each stream, from the pages at the end of
    /// the source, by serial. For a source that seeks.
    private Map<Long, Long> lastGranules() {
        var size = in.size();
        var found = new HashMap<Long, Long>();
        if (size.isEmpty() || size.getAsLong() <= 0) {
            return found;
        }
        var back = in.position();
        try {
            var length = (int) Math.min(size.getAsLong(), TAIL);
            var from = size.getAsLong() - length;
            in.seek(from);
            var tail = in.bytes(length);
            for (var at = length - OggPage.HEADER_SIZE; at >= 0 && found.size() < bySerial.size(); at--) {
                if (tail[at] != 'O'
                        || tail[at + 1] != 'g'
                        || tail[at + 2] != 'g'
                        || tail[at + 3] != 'S'
                        || tail[at + 4] != 0) {
                    continue;
                }
                var serial = (tail[at + 14] & 0xFFL)
                        | (tail[at + 15] & 0xFFL) << 8
                        | (tail[at + 16] & 0xFFL) << 16
                        | (tail[at + 17] & 0xFFL) << 24;
                if (!bySerial.containsKey(serial) || found.containsKey(serial)) {
                    continue;
                }
                var count = tail[at + 26] & 0xFF;
                if (at + OggPage.HEADER_SIZE + count > length) {
                    continue;
                }
                var body = 0;
                for (var i = 0; i < count; i++) {
                    body += tail[at + OggPage.HEADER_SIZE + i] & 0xFF;
                }
                if (at + OggPage.HEADER_SIZE + count + body > length) {
                    continue;
                }
                var granule = OggPage.littleEndian(Arrays.copyOfRange(tail, at + 6, at + 14));
                if (granule >= 0) {
                    found.put(serial, granule);
                }
            }
        } catch (MediaException e) {
            LOG.debug("{}: the end could not be read for the duration: {}", source.uri(), e.getMessage());
        } finally {
            in.seek(back);
        }
        return found;
    }

    /// The position of the last page of `stream` whose granule position is at
    /// or before `granule`, or the first data page when none is. Bisects the
    /// bytes between the two, reading the first page of the stream after each
    /// midpoint.
    private long bisect(OggStream stream, long granule) {
        var size = in.size();
        if (size.isEmpty()) {
            return dataStart;
        }
        var best = dataStart;
        var low = dataStart;
        var high = size.getAsLong();
        OggPage landing = null;
        for (var i = 0; i < 64 && low < high; i++) {
            var mid = low + (high - low) / 2;
            in.seek(mid);
            var page = pageOf(stream, high);
            if (page == null) {
                high = mid;
            } else if (page.granule() <= granule) {
                best = page.position();
                landing = page;
                low = page.end();
            } else {
                high = page.position();
            }
        }
        // A stream's last page trims its last packet to the granule, which
        // counting back from the granule cannot know: the packets of a last
        // page read first would all be early by the trim. Start one page sooner
        // and count forward over it instead.
        if (landing != null && landing.isLast() && best > dataStart) {
            return pageBefore(stream, best);
        }
        return best;
    }

    /// The position of the last page of `stream` that starts before `position`
    /// and has a granule position, or the first data page when none has.
    private long pageBefore(OggStream stream, long position) {
        var step = 2L * TAIL;
        var from = Math.max(dataStart, position - step);
        while (true) {
            in.seek(from);
            var found = -1L;
            for (var page = OggPage.read(in); page != null && page.position() < position; page = OggPage.read(in)) {
                if (page.serial() == stream.serial && page.granule() >= 0) {
                    found = page.position();
                }
            }
            if (found >= 0) {
                return found;
            }
            if (from <= dataStart) {
                return dataStart;
            }
            step *= 2;
            from = Math.max(dataStart, from - step);
        }
    }

    /// The first page of `stream` with a granule position, at or after the
    /// current position and before `limit`, or null when there is none.
    private @Nullable OggPage pageOf(OggStream stream, long limit) {
        while (in.position() < limit) {
            var page = OggPage.read(in);
            if (page == null || page.position() >= limit) {
                return null;
            }
            if (page.serial() == stream.serial && page.granule() >= 0) {
                return page;
            }
        }
        return null;
    }

    // ---- helpers -----------------------------------------------------------------

    private boolean isSelected(int index) {
        var set = selected;
        return set == null || set.contains(index);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("container closed");
        }
    }
}
