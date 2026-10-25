package dev.goldberry.media.container.mp4;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.media.Chapter;
import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.MediaInfo;
import dev.goldberry.media.Track;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.FrameRate;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.Rational;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.container.ByteReader;
import dev.goldberry.media.container.Container;
import dev.goldberry.media.container.mp4.BoxReader.Box;
import dev.goldberry.media.container.mp4.SampleEntries.SampleEntry;
import dev.goldberry.media.io.MediaIO;
import dev.goldberry.media.io.Source;

/// MP4, the ISO base media file format (ISO/IEC 14496-12) and the QuickTime
/// file format it grew out of, read in Java.
///
/// Opening walks the top-level boxes. The `moov` box is the whole index: a
/// `trak` per track with its header, its edit list, its media header and
/// handler, and the `stbl` sample tables, which are flattened into a
/// [SampleTable] of one row per sample; `udta/meta/ilst/covr` is the cover
/// art, reported as an attached-picture track after the others. The chapters
/// are a chapter track's or `udta/chpl`'s ([Mp4Chapters]); a chapter track is
/// not reported as a track. A `moov` that
/// comes after the `mdat`, as every file that was not written with
/// `faststart` has it, is seeked to; a source that cannot seek is refused
/// then. A fragmented file's `moof` boxes are read as well, each `trun`
/// appended to its track's table, on a source that seeks.
///
/// Reading hands the samples over by decode time across the selected tracks,
/// and where two tracks' next samples are within a second of each other by
/// file position, which reads a file forward and is the order FFmpeg's reader
/// hands them over in. Each packet's bytes are read from the sample's offset
/// into native memory of its own. Seeking goes to the last sync sample at or
/// before the target in the selected video track, else the first; the other
/// tracks go to their sample at or before that time.
///
/// What is honoured of the format: 32- and 64-bit box sizes and the size that
/// runs to the end; `stts`, `ctts` (version 1's signed offsets, and version
/// 0's read as signed too, as FFmpeg reads them), `stsc`, `stsz` and `stz2`,
/// `stco` and `co64`, `stss` (absent, every sample is a sync sample); the two
/// edit-list shapes [EditList] describes; `moof/traf/tfhd/tfdt/trun` with the
/// `trex` defaults, where the data offsets are relative to the `moof`. Not
/// read: a second sample description (a track is read as its first), later
/// edits, a `trun` whose base is the previous `traf`'s end, encrypted tracks,
/// and fragments on a source that cannot seek.
public final class Mp4Container implements Container {

    private static final Logger LOG = Logs.of(Mp4Container.class);

    /// How far apart two tracks' decode times may be for the one earlier in
    /// the file to go first.
    private static final long INTERLEAVE_WINDOW_NANOS = 1_000_000_000L;

    /// The most of a chapter track's sample read for its title: a 16-bit
    /// length and the text, and none of the styling boxes after it.
    private static final int MAX_CHAPTER_SAMPLE = 2 + 0xFFFF;

    /// The types a file's fifth to eighth bytes may be: the first box after its
    /// size field.
    private static final int[] FIRST_BOXES = {
        BoxTypes.FTYP, BoxTypes.MOOV, BoxTypes.MDAT, BoxTypes.WIDE, BoxTypes.FREE, BoxTypes.SKIP
    };

    /// Whether `head`, a file's first bytes, begins an MP4 or QuickTime file:
    /// the first box is one of the types a file starts with.
    public static boolean isMagic(byte[] head) {
        if (head.length < 8) {
            return false;
        }
        var type = (head[4] & 0xFF) << 24 | (head[5] & 0xFF) << 16 | (head[6] & 0xFF) << 8 | (head[7] & 0xFF);
        return Arrays.stream(FIRST_BOXES).anyMatch(first -> first == type);
    }

    /// One track as the file declares it.
    ///
    /// @param shift what the edit list adds to every timestamp, in the
    ///              track's timescale
    @SuppressWarnings("ArrayRecordComponent")
    private record Entry(
            int index,
            long trackId,
            MediaType type,
            CodecId codec,
            String codecName,
            TrackParams params,
            byte[] extradata,
            long timescale,
            Rational timeBase,
            long shift,
            boolean enabled,
            Optional<String> language,
            Optional<Duration> duration) {}

    /// The cover art, handed over as one packet.
    @SuppressWarnings("ArrayRecordComponent")
    private record Picture(Track track, byte[] data) {

        int index() {
            return track.index();
        }
    }

    /// A `trex` box: a track's defaults for its fragments.
    private record Defaults(long duration, long size, long flags) {}

    /// A `trak` as it is read, before its entry is made.
    private static final class TrackBoxes {
        long trackId = -1;
        boolean enabled = true;
        long tkhdDuration;
        long timescale;
        long mdhdDuration;
        Optional<String> language = Optional.empty();
        int handler;
        /// The tracks a `tref/chap` names as this one's chapters.
        final List<Long> chapterTracks = new ArrayList<>();

        @Nullable
        SampleEntry entry;

        final SampleTable.Builder stbl = new SampleTable.Builder();
        SampleTable table = SampleTable.EMPTY;
        EditList edits = EditList.NONE;
        /// Where the next fragment's samples decode from, when it has no `tfdt`.
        long nextDecodeTime;
    }

    private final Source source;
    private final ByteReader in;
    private final BoxReader boxes;
    private final Arena arena = Arena.ofShared();
    private final List<Entry> entries = new ArrayList<>();
    private final List<SampleTable> tables = new ArrayList<>();
    private final List<Picture> pictures = new ArrayList<>();
    private final Map<Integer, MemorySegment> extradata = new HashMap<>();
    private final Deque<Packet> pending = new ArrayDeque<>();
    private final MediaInfo info;
    private final int[] cursors;
    private @Nullable Set<Integer> selected;
    private boolean picturesSent;
    private boolean closed;

    // State while the moov and the fragments are read.
    private long movieTimescale = 1;
    private long movieDuration;
    private final List<TrackBoxes> trackBoxes = new ArrayList<>();
    private final Map<Long, Defaults> defaults = new HashMap<>();
    private List<Chapter> neroChapters = List.of();

    private Mp4Container(Source source, MediaIO io) {
        this.source = source;
        this.in = new ByteReader(io);
        this.boxes = new BoxReader(in);
        var moovSeen = false;
        var moofSeen = false;
        while (boxes.hasNext(Long.MAX_VALUE)) {
            var box = boxes.next(Long.MAX_VALUE);
            if (box.type() == BoxTypes.MOOV) {
                readMoov(box);
                moovSeen = true;
            } else if (box.type() == BoxTypes.MDAT) {
                if (!moovSeen && !in.isSeekable()) {
                    throw new MediaException(
                            new MediaError.InvalidData("moov after mdat on a source that cannot seek"));
                }
                if (moovSeen && !in.isSeekable()) {
                    // The samples start here, and will be read forward.
                    break;
                }
                boxes.skip(box);
            } else if (box.type() == BoxTypes.MOOF) {
                moofSeen = true;
                if (moovSeen && in.isSeekable()) {
                    readMoof(box);
                } else {
                    boxes.skip(box);
                }
            } else {
                boxes.skip(box);
            }
        }
        if (!moovSeen) {
            throw new MediaException(new MediaError.InvalidData("an MP4 with no moov box: " + source.uri()));
        }
        var anySample = trackBoxes.stream().anyMatch(track -> track.table.count() > 0);
        if (!anySample && moofSeen) {
            throw new MediaException(new MediaError.InvalidData(
                    in.isSeekable()
                            ? "a fragmented MP4 whose fragments name no track of its moov: " + source.uri()
                            : "a fragmented MP4 is not read from a source that cannot seek: " + source.uri()));
        }
        var chapterTrackIds = trackBoxes.stream()
                .flatMap(track -> track.chapterTracks.stream())
                .collect(Collectors.toUnmodifiableSet());
        var chapters = neroChapters;
        for (var track : trackBoxes) {
            if (chapterTrackIds.contains(track.trackId)) {
                var read = chapterTrack(track);
                if (!read.isEmpty()) {
                    chapters = read;
                }
            } else {
                addEntry(track);
            }
        }
        if (entries.isEmpty()) {
            throw new MediaException(
                    new MediaError.InvalidData("an MP4 with no video, audio or subtitle track: " + source.uri()));
        }
        // The cover art is indexed after every track.
        for (var i = 0; i < pictures.size(); i++) {
            var picture = pictures.get(i);
            pictures.set(i, new Picture(pictureTrack(picture.track().codec(), entries.size() + i), picture.data()));
        }
        this.cursors = new int[entries.size()];
        // The movie's length, or where the mvhd says nothing (a fragmented
        // file's does), the longest track's.
        var duration = movieDuration > 0 && movieTimescale > 0 && movieTimescale <= Integer.MAX_VALUE
                ? Optional.of(new Rational(1, (int) movieTimescale).toDuration(movieDuration))
                : entries.stream().flatMap(entry -> entry.duration().stream()).max(Duration::compareTo);
        var tracks = new ArrayList<Track>();
        for (var entry : entries) {
            tracks.add(new Track(
                    entry.index(),
                    entry.codec(),
                    entry.codecName(),
                    entry.params(),
                    entry.duration(),
                    entry.enabled(),
                    false,
                    entry.language(),
                    Optional.empty(),
                    entry.type() == MediaType.VIDEO
                            ? OptionalLong.of(tables.get(entry.index()).count())
                            : OptionalLong.empty()));
        }
        for (var picture : pictures) {
            tracks.add(picture.track());
        }
        this.info = new MediaInfo(source, duration, tracks, io.isSeekable(), io.isLive(), chapters);
    }

    /// Opens an MP4 or QuickTime file over `io`, positioned at its start.
    ///
    /// @throws MediaException [MediaError.InvalidData] when the bytes are not an
    ///                        MP4 file, or its index is after its data on a
    ///                        source that cannot seek, [MediaError.Io] when a
    ///                        read fails, [MediaError.Aborted] when `io` was
    ///                        closed during the open
    public static Mp4Container open(Source source, MediaIO io) {
        return new Mp4Container(source, io);
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
        return entry(stream).timeBase();
    }

    @Override
    public DecoderRequest request(int stream) {
        var entry = entry(stream);
        var record = extradata.computeIfAbsent(
                stream,
                _ -> entry.extradata().length == 0
                        ? MemorySegment.NULL
                        : arena.allocateFrom(ValueLayout.JAVA_BYTE, entry.extradata()));
        return new DecoderRequest(entry.codec(), entry.codecName(), entry.params(), record, entry.timeBase());
    }

    /// The track at `stream`, which is not the cover art.
    private Entry entry(int stream) {
        if (stream < 0 || stream >= entries.size()) {
            throw new IllegalArgumentException("no track " + stream + " in " + source.uri());
        }
        return entries.get(stream);
    }

    @Override
    public @Nullable Packet read() {
        requireOpen();
        if (!picturesSent) {
            picturesSent = true;
            for (var picture : pictures) {
                if (isSelected(picture.index())) {
                    pending.add(picturePacket(picture));
                }
            }
        }
        if (!pending.isEmpty()) {
            return pending.poll();
        }
        Entry next = null;
        var nextDecodeNanos = 0L;
        var nextOffset = 0L;
        for (var entry : entries) {
            var table = tables.get(entry.index());
            var cursor = cursors[entry.index()];
            if (!isSelected(entry.index()) || cursor >= table.count()) {
                continue;
            }
            var decodeNanos = entry.timeBase().toNanos(table.decodeTime(cursor) + entry.shift());
            var offset = table.offset(cursor);
            var earlier = next == null
                    || (Math.abs(decodeNanos - nextDecodeNanos) <= INTERLEAVE_WINDOW_NANOS
                            ? offset < nextOffset
                            : decodeNanos < nextDecodeNanos);
            if (earlier) {
                next = entry;
                nextDecodeNanos = decodeNanos;
                nextOffset = offset;
            }
        }
        if (next == null) {
            return null;
        }
        var table = tables.get(next.index());
        var sample = cursors[next.index()]++;
        goTo(table.offset(sample));
        @SuppressWarnings({"MustBeClosedChecker", "resource"})
        var owned = Arena.ofShared();
        var data = in.segment(owned, table.size(sample));
        var dts = table.decodeTime(sample) + next.shift();
        var pts = table.presentationTime(sample) + next.shift();
        return Packet.owning(
                data,
                next.index(),
                pts,
                dts,
                table.duration(sample),
                table.isSync(sample),
                next.timeBase(),
                owned::close);
    }

    /// Moves to `offset`: a seek where the source seeks, a skip forward where
    /// it does not.
    private void goTo(long offset) {
        if (in.isSeekable()) {
            in.seek(offset);
            return;
        }
        if (offset < in.position()) {
            throw new MediaException(new MediaError.InvalidData("a sample at byte " + offset + " behind byte "
                    + in.position() + " on a source that cannot seek: " + source.uri()));
        }
        boxes.seekTo(offset);
    }

    private Packet picturePacket(Picture picture) {
        @SuppressWarnings({"MustBeClosedChecker", "resource"})
        var owned = Arena.ofShared();
        var data = owned.allocateFrom(ValueLayout.JAVA_BYTE, picture.data());
        return Packet.owning(
                data, picture.index(), 0, 0, 0, true, entries.getFirst().timeBase(), owned::close);
    }

    @Override
    public void seek(long positionNanos) {
        requireOpen();
        if (!in.isSeekable()) {
            throw new MediaException(new MediaError.Io("the source cannot seek"));
        }
        pending.forEach(Packet::close);
        pending.clear();
        if (positionNanos <= 0) {
            picturesSent = false;
        }
        var target = Math.max(positionNanos, 0);
        var leader = entries.stream()
                .filter(entry -> entry.type() == MediaType.VIDEO && isSelected(entry.index()))
                .findFirst()
                .or(() -> entries.stream()
                        .filter(entry -> isSelected(entry.index()))
                        .findFirst());
        if (leader.isEmpty()) {
            Arrays.fill(cursors, 0);
            return;
        }
        var lead = leader.get();
        var leadTable = tables.get(lead.index());
        var sample = leadTable.lastSyncAtOrBefore(lead.timeBase().fromNanos(target) - lead.shift());
        cursors[lead.index()] = sample;
        var landedNanos =
                leadTable.count() == 0 ? 0 : lead.timeBase().toNanos(leadTable.presentationTime(sample) + lead.shift());
        for (var entry : entries) {
            if (entry == lead) {
                continue;
            }
            var table = tables.get(entry.index());
            cursors[entry.index()] = table.lastAtOrBefore(entry.timeBase().fromNanos(landedNanos) - entry.shift());
        }
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

    // ---- the moov box ----------------------------------------------------------

    private void readMoov(Box moov) {
        while (boxes.hasNext(moov.end())) {
            var box = boxes.next(moov.end());
            if (box.type() == BoxTypes.MVHD) {
                var full = boxes.fullBox();
                in.skip(full.version() == 1 ? 16 : 8);
                movieTimescale = in.u32();
                movieDuration = full.version() == 1 ? in.unsignedBigEndian(8) : in.u32();
                if (movieDuration == 0xFFFFFFFFL) {
                    movieDuration = 0;
                }
                boxes.skip(box);
            } else if (box.type() == BoxTypes.TRAK) {
                readTrak(box);
            } else if (box.type() == BoxTypes.UDTA) {
                readUdta(box);
            } else if (box.type() == BoxTypes.MVEX) {
                readMvex(box);
            } else {
                boxes.skip(box);
            }
        }
        boxes.seekTo(moov.end());
    }

    private void readTrak(Box trak) {
        var track = new TrackBoxes();
        while (boxes.hasNext(trak.end())) {
            var box = boxes.next(trak.end());
            if (box.type() == BoxTypes.TKHD) {
                var full = boxes.fullBox();
                track.enabled = (full.flags() & 0x1) != 0;
                in.skip(full.version() == 1 ? 16 : 8);
                track.trackId = in.u32();
                in.skip(4);
                track.tkhdDuration = full.version() == 1 ? in.unsignedBigEndian(8) : in.u32();
                if (track.tkhdDuration == 0xFFFFFFFFL) {
                    track.tkhdDuration = 0;
                }
                boxes.skip(box);
            } else if (box.type() == BoxTypes.TREF) {
                readTref(box, track);
            } else if (box.type() == BoxTypes.EDTS) {
                readEdts(box, track);
            } else if (box.type() == BoxTypes.MDIA) {
                readMdia(box, track);
            } else {
                boxes.skip(box);
            }
        }
        boxes.seekTo(trak.end());
        track.table = track.stbl.build();
        track.nextDecodeTime = track.table.totalDuration();
        trackBoxes.add(track);
    }

    private void readTref(Box tref, TrackBoxes track) {
        while (boxes.hasNext(tref.end())) {
            var box = boxes.next(tref.end());
            if (box.type() == BoxTypes.CHAP) {
                while (box.end() - in.position() >= 4) {
                    track.chapterTracks.add(in.u32());
                }
            }
            boxes.skip(box);
        }
        boxes.seekTo(tref.end());
    }

    /// The chapters a chapter track's samples state, one a sample, timed by its
    /// table. Read only where the source seeks: the samples are in the `mdat`,
    /// which a source read forward has not reached when the moov is read.
    private List<Chapter> chapterTrack(TrackBoxes track) {
        var table = track.table;
        if (!in.isSeekable() || track.timescale <= 0 || track.timescale > Integer.MAX_VALUE) {
            return List.of();
        }
        var timeBase = new Rational(1, (int) track.timescale);
        var shift = track.edits.shift(track.timescale, movieTimescale);
        var back = in.position();
        var chapters = new ArrayList<Chapter>(table.count());
        try {
            for (var i = 0; i < table.count(); i++) {
                in.seek(table.offset(i));
                var title = Mp4Chapters.text(in.bytes(Math.min(table.size(i), MAX_CHAPTER_SAMPLE)));
                var start = table.presentationTime(i) + shift;
                if (start < 0) {
                    continue;
                }
                var end = table.duration(i) > 0
                        ? Optional.of(timeBase.toDuration(start + table.duration(i)))
                        : Optional.<Duration>empty();
                chapters.add(new Chapter(timeBase.toDuration(start), end, title));
            }
        } catch (MediaException e) {
            LOG.debug("{}: the chapter track {} could not be read: {}", source.uri(), track.trackId, e.getMessage());
            return List.of();
        } finally {
            in.seek(back);
        }
        return List.copyOf(chapters);
    }

    private void readEdts(Box edts, TrackBoxes track) {
        while (boxes.hasNext(edts.end())) {
            var box = boxes.next(edts.end());
            if (box.type() == BoxTypes.ELST) {
                var full = boxes.fullBox();
                var count = in.u32();
                var edits = new ArrayList<EditList.Entry>();
                for (var i = 0L; i < count && in.position() < box.end(); i++) {
                    long segmentDuration;
                    long mediaTime;
                    if (full.version() == 1) {
                        segmentDuration = in.unsignedBigEndian(8);
                        mediaTime = in.signedBigEndian(8);
                    } else {
                        segmentDuration = in.u32();
                        mediaTime = in.signedBigEndian(4);
                    }
                    in.skip(4); // media_rate_integer and media_rate_fraction
                    edits.add(new EditList.Entry(segmentDuration, mediaTime));
                }
                track.edits = new EditList(edits);
            }
            boxes.skip(box);
        }
        boxes.seekTo(edts.end());
    }

    private void readMdia(Box mdia, TrackBoxes track) {
        while (boxes.hasNext(mdia.end())) {
            var box = boxes.next(mdia.end());
            if (box.type() == BoxTypes.MDHD) {
                var full = boxes.fullBox();
                in.skip(full.version() == 1 ? 16 : 8);
                track.timescale = in.u32();
                track.mdhdDuration = full.version() == 1 ? in.unsignedBigEndian(8) : in.u32();
                if (track.mdhdDuration == 0xFFFFFFFFL) {
                    track.mdhdDuration = 0;
                }
                track.language = language(in.u16());
                boxes.skip(box);
            } else if (box.type() == BoxTypes.HDLR) {
                boxes.fullBox();
                in.skip(4); // pre_defined, or QuickTime's component type
                track.handler = (int) in.u32();
                boxes.skip(box);
            } else if (box.type() == BoxTypes.MINF) {
                readMinf(box, track);
            } else {
                boxes.skip(box);
            }
        }
        boxes.seekTo(mdia.end());
    }

    private void readMinf(Box minf, TrackBoxes track) {
        while (boxes.hasNext(minf.end())) {
            var box = boxes.next(minf.end());
            if (box.type() == BoxTypes.STBL) {
                readStbl(box, track);
            } else {
                boxes.skip(box);
            }
        }
        boxes.seekTo(minf.end());
    }

    private void readStbl(Box stbl, TrackBoxes track) {
        while (boxes.hasNext(stbl.end())) {
            var box = boxes.next(stbl.end());
            var type = box.type();
            if (type == BoxTypes.STSD) {
                var mediaType = mediaType(track.handler);
                if (mediaType.isPresent()) {
                    track.entry = SampleEntries.read(boxes, box.end(), mediaType.get());
                }
            } else if (type == BoxTypes.STTS) {
                boxes.fullBox();
                var count = in.u32();
                for (var i = 0L; i < count && in.position() + 8 <= box.end(); i++) {
                    track.stbl.timeRun(in.u32(), in.u32());
                }
            } else if (type == BoxTypes.CTTS) {
                boxes.fullBox();
                var count = in.u32();
                for (var i = 0L; i < count && in.position() + 8 <= box.end(); i++) {
                    track.stbl.offsetRun(in.u32(), (int) in.u32());
                }
            } else if (type == BoxTypes.STSC) {
                boxes.fullBox();
                var count = in.u32();
                for (var i = 0L; i < count && in.position() + 12 <= box.end(); i++) {
                    var firstChunk = in.u32();
                    var samplesPerChunk = in.u32();
                    in.skip(4); // sample_description_index
                    track.stbl.chunkRun(firstChunk, samplesPerChunk);
                }
            } else if (type == BoxTypes.STSZ) {
                boxes.fullBox();
                var sampleSize = in.u32();
                // With one size for every sample the box holds no table, so its
                // size says nothing about the count; the file's does, since every
                // sample is that many of its bytes. Without that bound a damaged
                // count of two billion asks for the tables of two billion samples.
                var count = sampleSize != 0
                        ? checkedCount(in.u32(), uniformCountBound(checkedSize(sampleSize, box)), box)
                        : checkedCount(in.u32(), box);
                var sizes = new int[count];
                if (sampleSize != 0) {
                    Arrays.fill(sizes, checkedSize(sampleSize, box));
                } else {
                    for (var i = 0; i < count; i++) {
                        sizes[i] = checkedSize(in.u32(), box);
                    }
                }
                track.stbl.sizes(sizes);
            } else if (type == BoxTypes.STZ2) {
                boxes.fullBox();
                var fieldSize = (int) (in.u32() & 0xFF);
                var count = checkedCount(in.u32(), box);
                track.stbl.sizes(readStz2(fieldSize, count, box));
            } else if (type == BoxTypes.STCO || type == BoxTypes.CO64) {
                boxes.fullBox();
                var count = checkedCount(in.u32(), box);
                var offsets = new long[count];
                for (var i = 0; i < count; i++) {
                    offsets[i] = type == BoxTypes.STCO ? in.u32() : in.unsignedBigEndian(8);
                }
                track.stbl.chunkOffsets(offsets);
            } else if (type == BoxTypes.STSS) {
                boxes.fullBox();
                var count = checkedCount(in.u32(), box);
                var samples = new long[count];
                for (var i = 0; i < count; i++) {
                    samples[i] = in.u32();
                }
                track.stbl.syncSamples(samples);
            }
            boxes.skip(box);
        }
        boxes.seekTo(stbl.end());
    }

    /// The sizes of an `stz2` box: 4, 8 or 16 bits each, packed.
    private int[] readStz2(int fieldSize, int count, Box box) {
        var sizes = new int[count];
        switch (fieldSize) {
            case 4 -> {
                for (var i = 0; i < count; i += 2) {
                    var pair = in.u8();
                    sizes[i] = pair >>> 4;
                    if (i + 1 < count) {
                        sizes[i + 1] = pair & 0x0F;
                    }
                }
            }
            case 8 -> {
                for (var i = 0; i < count; i++) {
                    sizes[i] = in.u8();
                }
            }
            case 16 -> {
                for (var i = 0; i < count; i++) {
                    sizes[i] = in.u16();
                }
            }
            default ->
                throw new MediaException(new MediaError.InvalidData(
                        "an stz2 box with " + fieldSize + "-bit sizes at byte " + box.headerStart()));
        }
        return sizes;
    }

    /// `count` as an array length: a count the box's own size cannot hold is a
    /// damaged file, not an allocation.
    private static int checkedCount(long count, Box box) {
        return checkedCount(count, box.size(), box);
    }

    private static int checkedCount(long count, long most, Box box) {
        if (count < 0 || count > most) {
            throw new MediaException(new MediaError.InvalidData(
                    "a '" + box.name() + "' box of " + box.size() + " bytes that claims " + count + " entries"));
        }
        return (int) count;
    }

    /// The most samples of `sampleSize` bytes each the source can hold: its
    /// size over theirs, or as many as a table can index when the size is not
    /// known.
    private long uniformCountBound(int sampleSize) {
        var size = in.size();
        return size.isPresent() && sampleSize > 0 ? size.getAsLong() / sampleSize : Integer.MAX_VALUE;
    }

    private static int checkedSize(long size, Box box) {
        if (size > Integer.MAX_VALUE) {
            throw new MediaException(new MediaError.InvalidData(
                    "a sample of " + size + " bytes in the '" + box.name() + "' box at byte " + box.headerStart()));
        }
        return (int) size;
    }

    private void readUdta(Box udta) {
        while (boxes.hasNext(udta.end())) {
            var box = boxes.next(udta.end());
            if (box.type() == BoxTypes.META) {
                readMeta(box);
            } else if (box.type() == BoxTypes.CHPL) {
                neroChapters = Mp4Chapters.chpl(boxes.payload(box));
                boxes.skip(box);
            } else {
                boxes.skip(box);
            }
        }
        boxes.seekTo(udta.end());
    }

    /// `meta` is a full box in ISO files and a plain one in QuickTime's: the
    /// first four bytes are a zero version and flags, or the first child's
    /// size, which is never zero.
    private void readMeta(Box meta) {
        var mark = in.position();
        if (in.u32() != 0) {
            in.seek(mark);
        }
        while (boxes.hasNext(meta.end())) {
            var box = boxes.next(meta.end());
            if (box.type() == BoxTypes.ILST) {
                readIlst(box);
            } else {
                boxes.skip(box);
            }
        }
        boxes.seekTo(meta.end());
    }

    private void readIlst(Box ilst) {
        while (boxes.hasNext(ilst.end())) {
            var box = boxes.next(ilst.end());
            if (box.type() == BoxTypes.COVR && pictures.isEmpty()) {
                readCovr(box);
            } else {
                boxes.skip(box);
            }
        }
        boxes.seekTo(ilst.end());
    }

    /// The cover art: a `data` box whose type, in the full-box flags, says
    /// JPEG (13) or PNG (14).
    private void readCovr(Box covr) {
        while (boxes.hasNext(covr.end())) {
            var box = boxes.next(covr.end());
            if (box.type() == BoxTypes.DATA && pictures.isEmpty()) {
                var full = boxes.fullBox();
                in.skip(4); // locale
                var codec = switch (full.flags()) {
                    case 13 -> CodecId.MJPEG;
                    case 14 -> CodecId.PNG;
                    default -> null;
                };
                if (codec != null && box.end() - in.position() > 0) {
                    var data = boxes.payload(box);
                    pictures.add(new Picture(pictureTrack(codec, 0), data));
                }
            }
            boxes.skip(box);
        }
        boxes.seekTo(covr.end());
    }

    private static Track pictureTrack(CodecId codec, int index) {
        return new Track(
                index,
                codec,
                codec.ffmpegName(),
                new TrackParams.Video(
                        0, 0, Optional.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalLong.empty()),
                Optional.empty(),
                false,
                true,
                Optional.empty(),
                Optional.empty());
    }

    private void readMvex(Box mvex) {
        while (boxes.hasNext(mvex.end())) {
            var box = boxes.next(mvex.end());
            if (box.type() == BoxTypes.TREX) {
                boxes.fullBox();
                var trackId = in.u32();
                in.skip(4); // default_sample_description_index
                var duration = in.u32();
                var size = in.u32();
                var flags = in.u32();
                defaults.put(trackId, new Defaults(duration, size, flags));
            }
            boxes.skip(box);
        }
        boxes.seekTo(mvex.end());
    }

    /// Makes `track`'s entry, once its tables and fragments are complete.
    private void addEntry(TrackBoxes track) {
        var mediaType = mediaType(track.handler);
        var sampleEntry = track.entry;
        if (mediaType.isEmpty() || sampleEntry == null || track.timescale <= 0) {
            LOG.debug(
                    "{}: track {} with handler '{}' is not played",
                    source.uri(),
                    track.trackId,
                    BoxTypes.name(track.handler));
            return;
        }
        if (track.timescale > Integer.MAX_VALUE) {
            throw new MediaException(new MediaError.InvalidData("a track timescale of " + track.timescale));
        }
        var timeBase = new Rational(1, (int) track.timescale);
        var table = track.table;
        var described = mediaType.get() == MediaType.VIDEO
                ? sampleEntry.withFrameRate(frameRate(table, track.timescale))
                : sampleEntry;
        if (track.edits.hasIgnoredEdits()) {
            LOG.debug(
                    "{}: track {} has an edit list of {} entries; only its first is honoured",
                    source.uri(),
                    track.trackId,
                    track.edits.entries().size());
        }
        var shift = track.edits.shift(track.timescale, movieTimescale);
        Optional<Duration> duration;
        if (track.tkhdDuration > 0 && movieTimescale > 0 && movieTimescale <= Integer.MAX_VALUE) {
            duration = Optional.of(new Rational(1, (int) movieTimescale).toDuration(track.tkhdDuration));
        } else if (track.mdhdDuration > 0) {
            duration = Optional.of(timeBase.toDuration(track.mdhdDuration));
        } else if (table.count() > 0) {
            duration = Optional.of(timeBase.toDuration(table.totalDuration()));
        } else {
            duration = Optional.empty();
        }
        var index = entries.size();
        entries.add(new Entry(
                index,
                track.trackId,
                mediaType.get(),
                described.codec(),
                described.codecName(),
                described.params(),
                described.extradata(),
                track.timescale,
                timeBase,
                shift,
                track.enabled,
                track.language,
                duration));
        tables.add(table);
    }

    // ---- fragments ---------------------------------------------------------------

    private void readMoof(Box moof) {
        while (boxes.hasNext(moof.end())) {
            var box = boxes.next(moof.end());
            if (box.type() == BoxTypes.TRAF) {
                readTraf(moof, box);
            } else {
                boxes.skip(box);
            }
        }
        boxes.seekTo(moof.end());
    }

    /// One `traf`: its `tfhd` names the track and the defaults, its `tfdt`
    /// the decode time the fragment starts at, and each `trun` lays out a run
    /// of samples whose data is at the `moof`'s start plus the run's offset.
    private void readTraf(Box moof, Box traf) {
        TrackBoxes track = null;
        var baseOffset = moof.headerStart();
        var defaultDuration = 0L;
        var defaultSize = 0L;
        var defaultFlags = 0L;
        var decodeTime = -1L;
        var nextData = -1L;
        var fragment = SampleTable.EMPTY;
        while (boxes.hasNext(traf.end())) {
            var box = boxes.next(traf.end());
            var type = box.type();
            if (type == BoxTypes.TFHD) {
                var full = boxes.fullBox();
                var trackId = in.u32();
                track = trackBoxes.stream()
                        .filter(t -> t.trackId == trackId)
                        .findFirst()
                        .orElse(null);
                var trex = defaults.getOrDefault(trackId, new Defaults(0, 0, 0));
                defaultDuration = trex.duration();
                defaultSize = trex.size();
                defaultFlags = trex.flags();
                if ((full.flags() & 0x1) != 0) {
                    baseOffset = in.unsignedBigEndian(8);
                }
                if ((full.flags() & 0x2) != 0) {
                    in.skip(4);
                }
                if ((full.flags() & 0x8) != 0) {
                    defaultDuration = in.u32();
                }
                if ((full.flags() & 0x10) != 0) {
                    defaultSize = in.u32();
                }
                if ((full.flags() & 0x20) != 0) {
                    defaultFlags = in.u32();
                }
            } else if (type == BoxTypes.TFDT) {
                var full = boxes.fullBox();
                decodeTime = full.version() == 1 ? in.unsignedBigEndian(8) : in.u32();
            } else if (type == BoxTypes.TRUN && track != null) {
                if (decodeTime < 0) {
                    decodeTime = track.nextDecodeTime;
                }
                if (nextData < 0) {
                    nextData = baseOffset;
                }
                var full = boxes.fullBox();
                var count = checkedCount(in.u32(), box);
                var dataStart = nextData;
                if ((full.flags() & 0x1) != 0) {
                    dataStart = baseOffset + in.signedBigEndian(4);
                }
                var firstFlags = (full.flags() & 0x4) != 0 ? in.u32() : -1;
                var offsets = new long[count];
                var sizes = new int[count];
                var decodeTimes = new long[count];
                var compositionOffsets = new int[count];
                var durations = new int[count];
                var sync = new BitSet(count);
                var position = dataStart;
                var audio = mediaType(track.handler).orElse(MediaType.DATA) == MediaType.AUDIO;
                for (var i = 0; i < count; i++) {
                    var duration = (full.flags() & 0x100) != 0 ? in.u32() : defaultDuration;
                    var size = (full.flags() & 0x200) != 0 ? in.u32() : defaultSize;
                    var flags = (full.flags() & 0x400) != 0
                            ? in.u32()
                            : (i == 0 && firstFlags >= 0 ? firstFlags : defaultFlags);
                    var composition = (full.flags() & 0x800) != 0 ? (int) in.u32() : 0;
                    offsets[i] = position;
                    sizes[i] = checkedSize(size, box);
                    decodeTimes[i] = decodeTime;
                    compositionOffsets[i] = composition;
                    durations[i] = (int) Math.min(duration, Integer.MAX_VALUE);
                    // Bit 16 says the sample is not a sync sample; bits 24-25
                    // say whether it depends on others. Audio is sync throughout.
                    var nonSync = (flags & 0x10000) != 0 || (flags & 0x03000000) == 0x01000000;
                    if (audio || !nonSync) {
                        sync.set(i);
                    }
                    position += size;
                    decodeTime += duration;
                }
                nextData = position;
                fragment = fragment.append(
                        SampleTable.ofRows(offsets, sizes, decodeTimes, compositionOffsets, durations, sync));
            }
            boxes.skip(box);
        }
        boxes.seekTo(traf.end());
        if (track != null && fragment.count() > 0) {
            track.table = track.table.append(fragment);
            track.nextDecodeTime = Math.max(track.nextDecodeTime, decodeTime);
        }
    }

    // ---- helpers -------------------------------------------------------------------

    /// The kind of track a handler type is, for the ones that are played.
    private static Optional<MediaType> mediaType(int handler) {
        if (handler == BoxTypes.VIDE) {
            return Optional.of(MediaType.VIDEO);
        }
        if (handler == BoxTypes.SOUN) {
            return Optional.of(MediaType.AUDIO);
        }
        if (handler == BoxTypes.SUBT || handler == BoxTypes.SBTL || handler == BoxTypes.TEXT) {
            return Optional.of(MediaType.SUBTITLE);
        }
        return Optional.empty();
    }

    /// The ISO 639-2 code `mdhd` packs into fifteen bits, three letters of
    /// five bits each; empty for `und`, for no language, and for the Macintosh
    /// language codes QuickTime wrote before.
    static Optional<String> language(int packed) {
        if (packed == 0 || packed < 0x400 || packed == 0x7FFF) {
            return Optional.empty();
        }
        var letters = new byte[] {
            (byte) (0x60 + ((packed >>> 10) & 0x1F)),
            (byte) (0x60 + ((packed >>> 5) & 0x1F)),
            (byte) (0x60 + (packed & 0x1F))
        };
        var code = new String(letters, StandardCharsets.US_ASCII);
        return code.equals("und") ? Optional.empty() : Optional.of(code);
    }

    /// The frame rate a sample table states: its count of samples over the
    /// time they take, reduced.
    static Optional<FrameRate> frameRate(SampleTable table, long timescale) {
        var count = table.count();
        var total = table.totalDuration();
        if (count <= 0 || total <= 0 || timescale <= 0) {
            return Optional.empty();
        }
        var num = count * timescale;
        var den = total;
        var gcd = gcd(num, den);
        num /= gcd;
        den /= gcd;
        if (num > Integer.MAX_VALUE || den > Integer.MAX_VALUE) {
            return Optional.empty();
        }
        return FrameRate.known((int) num, (int) den);
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            var t = a % b;
            a = b;
            b = t;
        }
        return a;
    }

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
