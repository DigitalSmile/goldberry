package dev.goldberry.media.container.matroska;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.media.Chapter;
import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.MediaInfo;
import dev.goldberry.media.Track;
import dev.goldberry.media.bitstream.OpusPackets;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.Rational;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.container.ByteReader;
import dev.goldberry.media.container.Container;
import dev.goldberry.media.container.ebml.EbmlReader;
import dev.goldberry.media.container.ebml.EbmlReader.Element;
import dev.goldberry.media.io.MediaIO;
import dev.goldberry.media.io.Source;

/// Matroska and WebM, read in Java.
///
/// Opening reads the EBML header and the Segment's children up to the first
/// Cluster: Info for the timestamp scale and the duration, Tracks for what is
/// in the file, Cues, Attachments and Chapters where they come before the
/// clusters, and
/// the SeekHead for where they are when they come after, which a seekable
/// source then visits. Reading walks the clusters, one block at a time, and
/// hands each frame over as a packet whose bytes are native memory: a laced
/// block is several packets over one allocation, freed when the last of them
/// is closed.
///
/// What is honoured of the format: SimpleBlocks and BlockGroups, the three
/// lacings, unknown-size Segments and Clusters (a live stream's), header
/// stripping and zlib content encodings, BlockAdditions carrying a WebM alpha
/// plane, image attachments as an attached-picture track, Chapters, and Cues
/// for seeking. A file without Cues seeks by starting over, which is correct and
/// slow; the Engine decodes from the keyframe and drops what is before the
/// target either way.
public final class MatroskaContainer implements Container {

    private static final Logger LOG = Logs.of(MatroskaContainer.class);

    /// The first four bytes of every Matroska and WebM file: the EBML element's
    /// ID.
    private static final byte[] MAGIC = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};

    private static final long DEFAULT_TIMESTAMP_SCALE = 1_000_000;

    /// Whether `head`, a file's first bytes, begins a Matroska or WebM file.
    public static boolean isMagic(byte[] head) {
        return head.length >= MAGIC.length && java.util.Arrays.equals(head, 0, MAGIC.length, MAGIC, 0, MAGIC.length);
    }

    /// One track as the file declares it.
    @SuppressWarnings("ArrayRecordComponent")
    private record Entry(
            int index,
            long number,
            MediaType type,
            CodecId codec,
            String codecName,
            TrackParams params,
            byte[] codecPrivate,
            long defaultDurationNanos,
            long codecDelayNanos,
            byte[] stripped,
            boolean zlib,
            boolean isDefault,
            Optional<String> language,
            Optional<String> title) {}

    /// An attachment the probe reports as a picture, handed over as one packet.
    @SuppressWarnings("ArrayRecordComponent")
    private record Picture(int index, Track track, byte[] data) {}

    private final Source source;
    private final ByteReader in;
    private final EbmlReader ebml;
    private final Arena arena = Arena.ofShared();
    private final long segmentDataStart;
    /// Where the Segment's payload ends, or -1 when nobody knows.
    private final long segmentEnd;
    private final Rational timeBase;
    private final long timestampScale;
    private final Map<Long, Entry> entriesByNumber = new HashMap<>();
    private final List<Entry> entries = new ArrayList<>();
    private final List<Picture> pictures = new ArrayList<>();
    private final Map<Integer, MemorySegment> extradata = new HashMap<>();
    private final long firstClusterPosition;
    private final MediaInfo info;
    private final Deque<Packet> pending = new ArrayDeque<>();
    private Cues cues = Cues.NONE;
    private boolean cuesRead;
    private List<Chapter> chapters = List.of();
    private boolean chaptersRead;
    private @Nullable Set<Integer> selected;
    private boolean inCluster;
    private long clusterEnd = -1;
    private long clusterTimestamp;
    private boolean picturesSent;
    /// After a seek with no video selected: the time the audio packets before
    /// which are dropped, or -1. A cue names a cluster, which may start seconds
    /// before the target; FFmpeg's reader walked the blocks to the one at the
    /// target, and so does this.
    private long skipAudioBeforeTicks = -1;
    private boolean closed;

    private MatroskaContainer(Source source, MediaIO io) {
        this.source = source;
        this.in = new ByteReader(io);
        this.ebml = new EbmlReader(in);
        var header = ebml.readHeader();
        if (header.id() != MatroskaIds.EBML) {
            throw new MediaException(new MediaError.InvalidData("not an EBML file"));
        }
        readEbmlHeader(header);
        var segment = ebml.readHeader();
        if (segment.id() != MatroskaIds.SEGMENT) {
            throw new MediaException(new MediaError.InvalidData(
                    "the EBML header is not followed by a Segment but by 0x" + Long.toHexString(segment.id())));
        }
        this.segmentDataStart = segment.dataStart();
        this.segmentEnd = segment.sized() ? segment.dataEnd() : in.size().orElse(-1);

        var timestampScaleSeen = DEFAULT_TIMESTAMP_SCALE;
        var durationTicks = Double.NaN;
        var seekHead = new HashMap<Long, Long>();
        long firstCluster = -1;
        while (firstCluster < 0 && !atSegmentEnd()) {
            var element = ebml.readHeader();
            if (element.id() == MatroskaIds.CLUSTER) {
                firstCluster = element.headerStart();
                enterCluster(element);
            } else if (element.id() == MatroskaIds.SEEK_HEAD) {
                readSeekHead(element, seekHead);
            } else if (element.id() == MatroskaIds.INFO) {
                var end = element.dataEnd();
                while (in.position() < end) {
                    var child = ebml.readHeader();
                    if (child.id() == MatroskaIds.TIMESTAMP_SCALE) {
                        timestampScaleSeen = ebml.readUnsigned(child);
                    } else if (child.id() == MatroskaIds.DURATION) {
                        durationTicks = ebml.readFloat(child);
                    } else {
                        ebml.skip(child);
                    }
                }
            } else if (element.id() == MatroskaIds.TRACKS) {
                readTracks(element);
            } else if (element.id() == MatroskaIds.CUES) {
                readCues(element);
            } else if (element.id() == MatroskaIds.ATTACHMENTS) {
                readAttachments(element);
            } else if (element.id() == MatroskaIds.CHAPTERS) {
                readChapters(element);
            } else {
                ebml.skip(element);
            }
        }
        if (entries.isEmpty()) {
            throw new MediaException(new MediaError.InvalidData("a Matroska file with no Tracks before its Clusters"));
        }
        this.firstClusterPosition = firstCluster;
        this.timestampScale = timestampScaleSeen <= 0 ? DEFAULT_TIMESTAMP_SCALE : timestampScaleSeen;
        this.timeBase = timeBaseOf(timestampScale);

        // What the SeekHead points at after the clusters, for a source that can
        // go there and come back.
        if (in.isSeekable() && firstCluster >= 0) {
            visit(seekHead.get(MatroskaIds.CUES), MatroskaIds.CUES, !cuesRead);
            visit(seekHead.get(MatroskaIds.ATTACHMENTS), MatroskaIds.ATTACHMENTS, pictures.isEmpty());
            visit(seekHead.get(MatroskaIds.CHAPTERS), MatroskaIds.CHAPTERS, !chaptersRead);
        }

        var duration = Double.isNaN(durationTicks) || durationTicks <= 0
                ? Optional.<Duration>empty()
                : Optional.of(Duration.ofNanos(Math.round(durationTicks * this.timestampScale)));
        var tracks = new ArrayList<Track>();
        for (var entry : entries) {
            tracks.add(new Track(
                    entry.index(),
                    entry.codec(),
                    entry.codecName(),
                    entry.params(),
                    entry.type() == MediaType.SUBTITLE ? Optional.empty() : duration,
                    entry.isDefault(),
                    false,
                    entry.language(),
                    entry.title()));
        }
        for (var picture : pictures) {
            tracks.add(picture.track());
        }
        this.info = new MediaInfo(source, duration, tracks, io.isSeekable(), io.isLive(), chapters);
    }

    /// Opens a Matroska or WebM file over `io`, positioned at its start.
    ///
    /// @throws MediaException [MediaError.InvalidData] when the bytes are not a
    ///                        Matroska file, [MediaError.Io] when a read fails,
    ///                        [MediaError.Aborted] when `io` was closed during
    ///                        the open
    public static MatroskaContainer open(Source source, MediaIO io) {
        return new MatroskaContainer(source, io);
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
        return timeBase;
    }

    @Override
    public DecoderRequest request(int stream) {
        var entry = entry(stream);
        var record = extradata.computeIfAbsent(
                stream,
                _ -> entry.codecPrivate().length == 0
                        ? MemorySegment.NULL
                        : arena.allocateFrom(ValueLayout.JAVA_BYTE, entry.codecPrivate()));
        return new DecoderRequest(entry.codec(), entry.codecName(), entry.params(), record, timeBase);
    }

    /// The track at `stream`, which is not an attached picture.
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
        while (true) {
            while (pending.isEmpty()) {
                if (inCluster) {
                    if (!readInCluster()) {
                        return null;
                    }
                } else if (!readTopLevel()) {
                    return null;
                }
            }
            var packet = pending.poll();
            if (skipAudioBeforeTicks >= 0 && entries.get(packet.streamIndex()).type() == MediaType.AUDIO) {
                if (packet.pts() + Math.max(packet.duration(), 0) <= skipAudioBeforeTicks) {
                    packet.close();
                    continue;
                }
                skipAudioBeforeTicks = -1;
            }
            return packet;
        }
    }

    /// Reads one element at the Segment level: enters a Cluster, takes a late
    /// Cues or Attachments, skips the rest.
    ///
    /// @return false at the end of the Segment
    private boolean readTopLevel() {
        if (atSegmentEnd()) {
            return false;
        }
        var element = ebml.readHeader();
        topLevel(element);
        return true;
    }

    private void topLevel(Element element) {
        if (element.id() == MatroskaIds.CLUSTER) {
            enterCluster(element);
        } else if (element.id() == MatroskaIds.CUES) {
            readCues(element);
        } else if (element.id() == MatroskaIds.ATTACHMENTS && pictures.isEmpty()) {
            readAttachments(element);
        } else if (!element.sized()) {
            throw new MediaException(
                    new MediaError.InvalidData("an element of unknown size, 0x" + Long.toHexString(element.id())
                            + " at byte " + element.headerStart() + ", that is not a Cluster"));
        } else {
            ebml.skip(element);
        }
    }

    private void enterCluster(Element cluster) {
        inCluster = true;
        clusterEnd = cluster.sized() ? cluster.dataEnd() : -1;
        clusterTimestamp = 0;
    }

    /// Reads one element inside the current Cluster.
    ///
    /// @return false at the end of the Segment
    private boolean readInCluster() {
        if ((clusterEnd >= 0 && in.position() >= clusterEnd) || atSegmentEnd()) {
            inCluster = false;
            return !atSegmentEnd();
        }
        var element = ebml.readHeader();
        var id = element.id();
        if (id == MatroskaIds.TIMESTAMP) {
            clusterTimestamp = ebml.readUnsigned(element);
        } else if (id == MatroskaIds.SIMPLE_BLOCK) {
            readBlock(element, true, 0, false, List.of());
        } else if (id == MatroskaIds.BLOCK_GROUP) {
            readBlockGroup(element);
        } else if (MatroskaIds.isTopLevel(id)) {
            // A Cluster of unknown size ends where the next top-level element
            // starts.
            inCluster = false;
            topLevel(element);
        } else {
            ebml.skip(element);
        }
        return true;
    }

    /// A BlockGroup: the Block's frames are read first, since a stream is read
    /// forward, and what the group says of them — the duration, the alpha, a
    /// reference that makes them inter frames — comes after.
    private void readBlockGroup(Element group) {
        var end = group.dataEnd();
        List<Read> frames = List.of();
        var duration = 0L;
        var referenced = false;
        var additions = new ArrayList<Addition>();
        while (in.position() < end) {
            var child = ebml.readHeader();
            var id = child.id();
            if (id == MatroskaIds.BLOCK) {
                frames = readFrames(child);
            } else if (id == MatroskaIds.BLOCK_DURATION) {
                duration = ebml.readUnsigned(child);
            } else if (id == MatroskaIds.REFERENCE_BLOCK) {
                ebml.readSigned(child);
                referenced = true;
            } else if (id == MatroskaIds.BLOCK_ADDITIONS) {
                readBlockAdditions(child, additions);
            } else {
                ebml.skip(child);
            }
        }
        emit(frames, false, duration, referenced, additions);
    }

    private void readBlockAdditions(Element additions, List<Addition> into) {
        var end = additions.dataEnd();
        while (in.position() < end) {
            var more = ebml.readHeader();
            if (more.id() != MatroskaIds.BLOCK_MORE) {
                ebml.skip(more);
                continue;
            }
            var moreEnd = more.dataEnd();
            var addId = MatroskaIds.BLOCK_ADD_ID_ALPHA;
            byte[] data = new byte[0];
            while (in.position() < moreEnd) {
                var child = ebml.readHeader();
                if (child.id() == MatroskaIds.BLOCK_ADD_ID) {
                    addId = ebml.readUnsigned(child);
                } else if (child.id() == MatroskaIds.BLOCK_ADDITIONAL) {
                    data = ebml.readBytes(child);
                } else {
                    ebml.skip(child);
                }
            }
            into.add(new Addition(addId, data));
        }
    }

    /// A SimpleBlock, or a Block whose group has been read.
    private void readBlock(Element block, boolean simple, long duration, boolean referenced, List<Addition> additions) {
        emit(readFrames(block), simple, duration, referenced, additions);
    }

    /// One frame as read from a block: its bytes, before any content encoding
    /// is undone, and whether the block's flags call it a keyframe.
    @SuppressWarnings("ArrayRecordComponent")
    private record Read(Entry entry, byte[] bytes, long timestampTicks, boolean keyframe) {}

    /// One BlockMore.
    @SuppressWarnings("ArrayRecordComponent")
    private record Addition(long id, byte[] data) {}

    /// Reads a block's header and its frames into heap memory.
    private List<Read> readFrames(Element block) {
        var end = block.dataEnd();
        var number = ebml.readSize();
        var timecode = in.signedBigEndian(2);
        var flags = in.u8();
        var entry = entriesByNumber.get(number);
        if (entry == null || !isSelected(entry.index())) {
            in.seek(end);
            return List.of();
        }
        var sizes = Lacing.read(in, Lacing.of(flags), end);
        var frames = new ArrayList<Read>(sizes.length);
        var keyframe = (flags & 0x80) != 0;
        for (var i = 0; i < sizes.length; i++) {
            if (sizes[i] > Integer.MAX_VALUE) {
                throw new MediaException(new MediaError.InvalidData("a frame of " + sizes[i] + " bytes"));
            }
            var bytes = in.bytes((int) sizes[i]);
            frames.add(new Read(entry, bytes, clusterTimestamp + timecode, keyframe));
        }
        return frames;
    }

    /// Turns read frames into packets: the content encoding undone, the time of
    /// each lace counted on from the block's, the duration shared out, and the
    /// alpha attached to a single frame.
    private void emit(
            List<Read> frames, boolean simple, long blockDuration, boolean referenced, List<Addition> additions) {
        if (frames.isEmpty()) {
            return;
        }
        var entry = frames.getFirst().entry();
        var laces = frames.size();
        var defaultTicks = entry.defaultDurationNanos() > 0 ? entry.defaultDurationNanos() / timestampScale : 0;
        var laceTicks = blockDuration > 0 ? blockDuration / laces : defaultTicks;
        var keyframe = simple ? frames.getFirst().keyframe() : !referenced;
        var payloads = new ArrayList<byte[]>(laces);
        var total = 0L;
        for (var frame : frames) {
            var payload = decode(entry, frame.bytes());
            payloads.add(payload);
            total += payload.length;
        }
        byte[] alpha = null;
        for (var addition : additions) {
            if (addition.id() == MatroskaIds.BLOCK_ADD_ID_ALPHA && laces == 1) {
                alpha = addition.data();
            }
        }
        // One allocation for the block, shared by its packets: freed when the
        // last of them is closed, on whatever thread that is.
        @SuppressWarnings("MustBeClosedChecker")
        var shared = Arena.ofShared();
        var remaining = new AtomicInteger(laces);
        Runnable release = () -> {
            if (remaining.decrementAndGet() == 0) {
                shared.close();
            }
        };
        var block = shared.allocate(total + (alpha == null ? 0 : alpha.length));
        var offset = 0L;
        for (var i = 0; i < laces; i++) {
            var payload = payloads.get(i);
            MemorySegment.copy(payload, 0, block, ValueLayout.JAVA_BYTE, offset, payload.length);
            var data = block.asSlice(offset, payload.length);
            offset += payload.length;
            var pts = delayed(frames.get(i).timestampTicks() + i * laceTicks, entry.codecDelayNanos());
            var duration = laceTicks > 0 ? laceTicks : stated(entry, payload);
            var packet = Packet.owning(data, entry.index(), pts, pts, duration, keyframe, timeBase, release);
            if (alpha != null) {
                MemorySegment.copy(alpha, 0, block, ValueLayout.JAVA_BYTE, offset, alpha.length);
                packet = packet.withAlpha(block.asSlice(offset, alpha.length));
            }
            pending.add(packet);
        }
    }

    /// The length of a packet whose block states none, where the codec's own
    /// bytes say: an Opus packet's TOC. 0 otherwise.
    private long stated(Entry entry, byte[] payload) {
        if (entry.codec() != CodecId.OPUS) {
            return 0;
        }
        var samples = OpusPackets.samples(payload);
        var nanos = samples * 1_000_000_000L / OpusPackets.SAMPLE_RATE;
        return (nanos + timestampScale / 2) / timestampScale;
    }

    /// `bytes` with the track's content encoding undone.
    private byte[] decode(Entry entry, byte[] bytes) {
        if (entry.stripped().length > 0) {
            var out = new byte[entry.stripped().length + bytes.length];
            System.arraycopy(entry.stripped(), 0, out, 0, entry.stripped().length);
            System.arraycopy(bytes, 0, out, entry.stripped().length, bytes.length);
            return out;
        }
        if (entry.zlib()) {
            var inflater = new Inflater();
            try {
                inflater.setInput(bytes);
                var out = new java.io.ByteArrayOutputStream(bytes.length * 4);
                var chunk = new byte[8192];
                while (!inflater.finished()) {
                    var n = inflater.inflate(chunk);
                    if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                        break;
                    }
                    out.write(chunk, 0, n);
                }
                return out.toByteArray();
            } catch (DataFormatException e) {
                throw new MediaException(
                        new MediaError.InvalidData("a zlib-compressed frame that does not inflate"), e);
            } finally {
                inflater.end();
            }
        }
        return bytes;
    }

    private Packet picturePacket(Picture picture) {
        @SuppressWarnings("MustBeClosedChecker")
        var owned = Arena.ofShared();
        var data = owned.allocateFrom(ValueLayout.JAVA_BYTE, picture.data());
        return Packet.owning(data, picture.index(), 0, 0, 0, true, timeBase, owned::close);
    }

    @Override
    public void seek(long positionNanos) {
        requireOpen();
        if (!in.isSeekable()) {
            throw new MediaException(new MediaError.Io("the source cannot seek"));
        }
        if (firstClusterPosition < 0) {
            return;
        }
        var ticks = timeBase.fromNanos(Math.max(positionNanos, 0));
        var video = entries.stream()
                .filter(entry -> entry.type() == MediaType.VIDEO && isSelected(entry.index()))
                .findFirst();
        var trackNumber = video.map(Entry::number)
                .or(() -> entries.stream()
                        .filter(entry -> isSelected(entry.index()))
                        .findFirst()
                        .map(Entry::number))
                .orElse(-1L);
        var target = cues.at(ticks, trackNumber)
                .map(point -> segmentDataStart + point.clusterPosition())
                .orElse(firstClusterPosition);
        if (cues.isEmpty() && positionNanos > 0) {
            LOG.debug("{} has no Cues; a seek starts over from the first cluster", source.uri());
        }
        pending.forEach(Packet::close);
        pending.clear();
        in.seek(target);
        inCluster = false;
        clusterEnd = -1;
        skipAudioBeforeTicks = video.isEmpty() ? ticks : -1;
        if (positionNanos <= 0) {
            picturesSent = false;
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

    // ---- the header elements -------------------------------------------------

    private void readEbmlHeader(Element header) {
        var end = header.dataEnd();
        while (in.position() < end) {
            var child = ebml.readHeader();
            if (child.id() == MatroskaIds.DOC_TYPE) {
                var docType = ebml.readString(child);
                if (!docType.equals("matroska") && !docType.equals("webm")) {
                    throw new MediaException(new MediaError.UnsupportedContainer(docType));
                }
            } else if (child.id() == MatroskaIds.EBML_READ_VERSION || child.id() == MatroskaIds.DOC_TYPE_READ_VERSION) {
                var version = ebml.readUnsigned(child);
                if (version > 4 && child.id() == MatroskaIds.DOC_TYPE_READ_VERSION) {
                    LOG.debug("{} asks for Matroska version {}; reading it as version 4", source.uri(), version);
                }
            } else {
                ebml.skip(child);
            }
        }
    }

    private void readSeekHead(Element seekHead, Map<Long, Long> into) {
        var end = seekHead.dataEnd();
        while (in.position() < end) {
            var seek = ebml.readHeader();
            if (seek.id() != MatroskaIds.SEEK) {
                ebml.skip(seek);
                continue;
            }
            var seekEnd = seek.dataEnd();
            var id = -1L;
            var position = -1L;
            while (in.position() < seekEnd) {
                var child = ebml.readHeader();
                if (child.id() == MatroskaIds.SEEK_ID) {
                    var bytes = ebml.readBytes(child);
                    id = 0;
                    for (var b : bytes) {
                        id = (id << 8) | (b & 0xFF);
                    }
                } else if (child.id() == MatroskaIds.SEEK_POSITION) {
                    position = ebml.readUnsigned(child);
                } else {
                    ebml.skip(child);
                }
            }
            if (id >= 0 && position >= 0) {
                into.putIfAbsent(id, position);
            }
        }
    }

    /// Goes to `relativePosition` from the Segment's payload, reads the element
    /// there if it is `expectedId`, and comes back. For a source that seeks.
    private void visit(@Nullable Long relativePosition, long expectedId, boolean wanted) {
        if (relativePosition == null || !wanted) {
            return;
        }
        var back = in.position();
        var wasInCluster = inCluster;
        var savedClusterEnd = clusterEnd;
        try {
            in.seek(segmentDataStart + relativePosition);
            var element = ebml.readHeader();
            if (element.id() == expectedId) {
                if (expectedId == MatroskaIds.CUES) {
                    readCues(element);
                } else if (expectedId == MatroskaIds.ATTACHMENTS) {
                    readAttachments(element);
                } else if (expectedId == MatroskaIds.CHAPTERS) {
                    readChapters(element);
                }
            }
        } catch (MediaException e) {
            LOG.debug(
                    "{}: the SeekHead's 0x{} could not be read: {}",
                    source.uri(),
                    Long.toHexString(expectedId),
                    e.getMessage());
        } finally {
            in.seek(back);
            inCluster = wasInCluster;
            clusterEnd = savedClusterEnd;
        }
    }

    private void readCues(Element cuesElement) {
        var points = new ArrayList<Cues.Point>();
        var end = cuesElement.dataEnd();
        while (in.position() < end) {
            var point = ebml.readHeader();
            if (point.id() != MatroskaIds.CUE_POINT) {
                ebml.skip(point);
                continue;
            }
            var pointEnd = point.dataEnd();
            var time = -1L;
            var positions = new ArrayList<long[]>();
            while (in.position() < pointEnd) {
                var child = ebml.readHeader();
                if (child.id() == MatroskaIds.CUE_TIME) {
                    time = ebml.readUnsigned(child);
                } else if (child.id() == MatroskaIds.CUE_TRACK_POSITIONS) {
                    var childEnd = child.dataEnd();
                    var track = -1L;
                    var cluster = -1L;
                    while (in.position() < childEnd) {
                        var leaf = ebml.readHeader();
                        if (leaf.id() == MatroskaIds.CUE_TRACK) {
                            track = ebml.readUnsigned(leaf);
                        } else if (leaf.id() == MatroskaIds.CUE_CLUSTER_POSITION) {
                            cluster = ebml.readUnsigned(leaf);
                        } else {
                            ebml.skip(leaf);
                        }
                    }
                    if (track >= 0 && cluster >= 0) {
                        positions.add(new long[] {track, cluster});
                    }
                } else {
                    ebml.skip(child);
                }
            }
            if (time >= 0) {
                for (var position : positions) {
                    points.add(new Cues.Point(time, position[0], position[1]));
                }
            }
        }
        cues = new Cues(points);
        cuesRead = true;
    }

    private void readChapters(Element element) {
        if (!chaptersRead) {
            chapters = MatroskaChapters.read(ebml, in, element);
            chaptersRead = true;
        } else {
            ebml.skip(element);
        }
    }

    private void readAttachments(Element attachments) {
        var end = attachments.dataEnd();
        while (in.position() < end) {
            var file = ebml.readHeader();
            if (file.id() != MatroskaIds.ATTACHED_FILE) {
                ebml.skip(file);
                continue;
            }
            var fileEnd = file.dataEnd();
            var name = "";
            var mediaType = "";
            byte[] data = new byte[0];
            while (in.position() < fileEnd) {
                var child = ebml.readHeader();
                if (child.id() == MatroskaIds.FILE_NAME) {
                    name = ebml.readString(child);
                } else if (child.id() == MatroskaIds.FILE_MEDIA_TYPE) {
                    mediaType = ebml.readString(child);
                } else if (child.id() == MatroskaIds.FILE_DATA) {
                    data = ebml.readBytes(child);
                } else {
                    ebml.skip(child);
                }
            }
            var codec = MatroskaCodecs.pictureCodec(mediaType, name);
            if (codec.isPresent() && data.length > 0) {
                var index = entries.size() + pictures.size();
                var track = new Track(
                        index,
                        codec.get(),
                        codec.get().ffmpegName(),
                        new TrackParams.Video(
                                0,
                                0,
                                Optional.empty(),
                                java.util.OptionalInt.empty(),
                                java.util.OptionalInt.empty(),
                                java.util.OptionalLong.empty()),
                        Optional.empty(),
                        false,
                        true,
                        Optional.empty(),
                        name.isEmpty() ? Optional.empty() : Optional.of(name));
                pictures.add(new Picture(index, track, data));
            }
        }
    }

    private void readTracks(Element tracks) {
        var end = tracks.dataEnd();
        while (in.position() < end) {
            var element = ebml.readHeader();
            if (element.id() != MatroskaIds.TRACK_ENTRY) {
                ebml.skip(element);
                continue;
            }
            var entry = readTrackEntry(element);
            if (entry != null) {
                entries.add(entry);
                entriesByNumber.put(entry.number(), entry);
            }
        }
    }

    private @Nullable Entry readTrackEntry(Element trackEntry) {
        var end = trackEntry.dataEnd();
        var number = -1L;
        var type = -1L;
        var isDefault = true;
        var defaultDuration = 0L;
        var codecDelay = 0L;
        var codecId = "";
        var codecPrivate = new byte[0];
        var language = "eng";
        String bcp47 = null;
        String name = null;
        var width = 0L;
        var height = 0L;
        var alpha = false;
        var samplingFrequency = 8000d;
        var channels = 1L;
        var bitDepth = 0L;
        var stripped = new byte[0];
        var zlib = false;
        while (in.position() < end) {
            var child = ebml.readHeader();
            var id = child.id();
            if (id == MatroskaIds.TRACK_NUMBER) {
                number = ebml.readUnsigned(child);
            } else if (id == MatroskaIds.TRACK_TYPE) {
                type = ebml.readUnsigned(child);
            } else if (id == MatroskaIds.FLAG_DEFAULT) {
                isDefault = ebml.readUnsigned(child) != 0;
            } else if (id == MatroskaIds.DEFAULT_DURATION) {
                defaultDuration = ebml.readUnsigned(child);
            } else if (id == MatroskaIds.CODEC_DELAY) {
                codecDelay = ebml.readUnsigned(child);
            } else if (id == MatroskaIds.CODEC_ID) {
                codecId = ebml.readString(child);
            } else if (id == MatroskaIds.CODEC_PRIVATE) {
                codecPrivate = ebml.readBytes(child);
            } else if (id == MatroskaIds.LANGUAGE) {
                language = ebml.readString(child);
            } else if (id == MatroskaIds.LANGUAGE_BCP47) {
                bcp47 = ebml.readString(child);
            } else if (id == MatroskaIds.NAME) {
                name = ebml.readString(child);
            } else if (id == MatroskaIds.VIDEO) {
                var videoEnd = child.dataEnd();
                while (in.position() < videoEnd) {
                    var leaf = ebml.readHeader();
                    if (leaf.id() == MatroskaIds.PIXEL_WIDTH) {
                        width = ebml.readUnsigned(leaf);
                    } else if (leaf.id() == MatroskaIds.PIXEL_HEIGHT) {
                        height = ebml.readUnsigned(leaf);
                    } else if (leaf.id() == MatroskaIds.ALPHA_MODE) {
                        alpha = ebml.readUnsigned(leaf) == 1;
                    } else {
                        ebml.skip(leaf);
                    }
                }
            } else if (id == MatroskaIds.AUDIO) {
                var audioEnd = child.dataEnd();
                while (in.position() < audioEnd) {
                    var leaf = ebml.readHeader();
                    if (leaf.id() == MatroskaIds.SAMPLING_FREQUENCY) {
                        samplingFrequency = ebml.readFloat(leaf);
                    } else if (leaf.id() == MatroskaIds.CHANNELS) {
                        channels = ebml.readUnsigned(leaf);
                    } else if (leaf.id() == MatroskaIds.BIT_DEPTH) {
                        bitDepth = ebml.readUnsigned(leaf);
                    } else {
                        ebml.skip(leaf);
                    }
                }
            } else if (id == MatroskaIds.CONTENT_ENCODINGS) {
                var encodingsEnd = child.dataEnd();
                while (in.position() < encodingsEnd) {
                    var encoding = ebml.readHeader();
                    if (encoding.id() != MatroskaIds.CONTENT_ENCODING) {
                        ebml.skip(encoding);
                        continue;
                    }
                    var encodingEnd = encoding.dataEnd();
                    var encodingType = 0L;
                    var algo = -1L;
                    byte[] settings = new byte[0];
                    while (in.position() < encodingEnd) {
                        var leaf = ebml.readHeader();
                        if (leaf.id() == MatroskaIds.CONTENT_ENCODING_TYPE) {
                            encodingType = ebml.readUnsigned(leaf);
                        } else if (leaf.id() == MatroskaIds.CONTENT_COMPRESSION) {
                            var compressionEnd = leaf.dataEnd();
                            while (in.position() < compressionEnd) {
                                var part = ebml.readHeader();
                                if (part.id() == MatroskaIds.CONTENT_COMP_ALGO) {
                                    algo = ebml.readUnsigned(part);
                                } else if (part.id() == MatroskaIds.CONTENT_COMP_SETTINGS) {
                                    settings = ebml.readBytes(part);
                                } else {
                                    ebml.skip(part);
                                }
                            }
                        } else {
                            ebml.skip(leaf);
                        }
                    }
                    if (encodingType != 0) {
                        throw new MediaException(new MediaError.InvalidData(
                                "track " + number + " of " + source.uri() + " is encrypted, which is not read"));
                    }
                    if (algo == MatroskaIds.COMP_ALGO_HEADER_STRIPPING) {
                        stripped = settings;
                    } else if (algo == MatroskaIds.COMP_ALGO_ZLIB) {
                        zlib = true;
                    } else if (algo >= 0) {
                        throw new MediaException(new MediaError.InvalidData("track " + number + " of " + source.uri()
                                + " is compressed with ContentCompAlgo " + algo + ", which is not read"));
                    }
                }
            } else {
                ebml.skip(child);
            }
        }
        var mediaType = MatroskaCodecs.mediaType(type);
        if (number < 0 || mediaType.isEmpty()) {
            return null;
        }
        var named = MatroskaCodecs.isPcm(codecId)
                ? MatroskaCodecs.pcm(codecId, intOrUnknown(bitDepth))
                : MatroskaCodecs.of(codecId);
        var params = switch (mediaType.get()) {
            case VIDEO ->
                MatroskaCodecs.video(
                        named.codec(), intOrUnknown(width), intOrUnknown(height), codecPrivate, defaultDuration, alpha);
            case AUDIO ->
                MatroskaCodecs.audio(named.codec(), samplingFrequency, intOrUnknown(channels), intOrUnknown(bitDepth));
            default -> new TrackParams.Subtitle();
        };
        var spoken = bcp47 != null && !bcp47.isEmpty() ? bcp47 : language;
        return new Entry(
                entries.size(),
                number,
                mediaType.get(),
                named.codec(),
                named.name(),
                params,
                codecPrivate,
                defaultDuration,
                codecDelay,
                stripped,
                zlib,
                isDefault,
                spoken.isEmpty() || spoken.equals("und") ? Optional.empty() : Optional.of(spoken),
                name == null || name.isEmpty() ? Optional.empty() : Optional.of(name));
    }

    // ---- helpers -------------------------------------------------------------

    /// `ticks` less the track's `CodecDelay`: the time the first decoded
    /// sample plays, with the codec's priming samples before it. The delay is
    /// rounded to whole ticks, half away from zero, before it is taken off,
    /// which is how FFmpeg's reader timed these packets: a 6.5 ms delay is 7,
    /// and blocks at 0, 21, 41 become -7, 14, 34.
    private long delayed(long ticks, long codecDelayNanos) {
        if (codecDelayNanos <= 0) {
            return ticks;
        }
        var delayTicks = (codecDelayNanos + timestampScale / 2) / timestampScale;
        return ticks - delayTicks;
    }

    private boolean isSelected(int index) {
        var set = selected;
        return set == null || set.contains(index);
    }

    private boolean atSegmentEnd() {
        return (segmentEnd >= 0 && in.position() >= segmentEnd) || in.atEnd();
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("container closed");
        }
    }

    /// The time base for a `TimestampScale` of `scale` nanoseconds: the fraction
    /// `scale / 10⁹`, reduced.
    static Rational timeBaseOf(long scale) {
        var num = scale;
        var den = 1_000_000_000L;
        var gcd = gcd(num, den);
        num /= gcd;
        den /= gcd;
        if (num > Integer.MAX_VALUE || den > Integer.MAX_VALUE) {
            throw new MediaException(new MediaError.InvalidData("a TimestampScale of " + scale + " ns"));
        }
        return new Rational((int) num, (int) den);
    }

    /// An unsigned element's value as an `int`, or 0, which a track's
    /// parameters read as not stated, when it does not fit one: a size, a
    /// channel count or a depth that no file states and a damaged one can.
    static int intOrUnknown(long value) {
        return value >= 0 && value <= Integer.MAX_VALUE ? (int) value : 0;
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            var t = a % b;
            a = b;
            b = t;
        }
        return a;
    }
}
