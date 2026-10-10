package dev.goldberry.media.ffi;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.MediaInfo;
import dev.goldberry.media.Track;
import dev.goldberry.media.bitstream.ParameterSets;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.FrameRate;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.Rational;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.io.MediaIO;
import dev.goldberry.media.io.Source;

/// An open demuxer over a [MediaIO]: what the Engine's demux thread holds.
///
/// Opening it reads the container's header and the first packets of each stream,
/// which is the whole of a probe ([MediaProbe][dev.goldberry.media.MediaProbe]
/// opens one and closes it). After that it hands out packets, seeks, and
/// describes each track for the decoders.
///
/// The native steps, and who frees what:
///
/// 1. [AvioBridge] allocates the I/O context. It is freed last, by the bridge.
/// 2. `avformat_alloc_context`, and `pb` is set on it.
/// 3. `avformat_open_input` probes the format. **On failure it frees the context
///    itself**, so the context is not freed again here.
/// 4. `avformat_find_stream_info` reads the first packets to fill in the rest.
/// 5. On close: `avformat_close_input`, then the bridge.
///
/// **Alpha.** A Matroska block may carry a BlockAdditional, which FFmpeg hands
/// over as packet side data: an 8-byte big-endian BlockAddID, then the bytes. ID
/// 1 is where WebM keeps a VP9 or VP8 picture's alpha, and a track that does
/// says so with `AlphaMode`, which FFmpeg reports as the stream's `alpha_mode`
/// metadata. [#read()] puts those bytes on the packet as [Packet#alpha()], and
/// the track's parameters say [TrackParams.Video#alpha()].
///
/// **One thread** reads, seeks and closes: the demux thread. [#abort()] is the
/// exception. It may be called from any thread, and it ends a read that is
/// blocked in the `MediaIO`.
public final class Demuxer implements AutoCloseable {

    /// The BlockAddID WebM keeps a picture's alpha under.
    static final long ALPHA_BLOCK_ADD_ID = 1;

    /// The big-endian BlockAddID FFmpeg writes before a BlockAdditional's bytes.
    private static final long BLOCK_ADD_ID_BYTES = 8;

    private static final ValueLayout.OfLong JAVA_LONG_BIG_ENDIAN =
            JAVA_LONG.withOrder(ByteOrder.BIG_ENDIAN).withByteAlignment(1);

    private final Ffmpeg ffmpeg;
    private final Source source;
    private final MediaIO io;
    private final AvioBridge bridge;
    private final Arena arena;
    private final MemorySegment holder;
    private final MemorySegment context;
    private final MediaInfo info;
    /// Where `av_packet_get_side_data` writes a size; the demux thread's alone.
    private final MemorySegment sideDataSize;
    private @Nullable Set<Integer> selected;
    private boolean closed;

    private Demuxer(
            Ffmpeg ffmpeg,
            Source source,
            MediaIO io,
            AvioBridge bridge,
            Arena arena,
            MemorySegment holder,
            MemorySegment context) {
        this.ffmpeg = ffmpeg;
        this.source = source;
        this.io = io;
        this.bridge = bridge;
        this.arena = arena;
        this.holder = holder;
        this.context = context;
        this.sideDataSize = arena.allocate(JAVA_LONG);
        this.info = describe();
    }

    /// Opens a demuxer over `io`, which `source` names. `io` is not closed by the
    /// demuxer; its opener closes it after the demuxer.
    ///
    /// @throws MediaException [MediaError.UnsupportedContainer] for a container this
    ///                        build has no demuxer for, recognised by its first
    ///                        bytes, [MediaError.InvalidData] for other bytes no
    ///                        demuxer reads,
    ///                        [MediaError.Io] when a read fails, [MediaError.Aborted]
    ///                        when `io` was closed during the open
    public static Demuxer open(Ffmpeg ffmpeg, Source source, MediaIO io) {
        var bridge = AvioBridge.open(ffmpeg, io);
        var arena = Arena.ofShared();
        try {
            var context = ffmpeg.format().allocContext().call();
            if (context.equals(MemorySegment.NULL)) {
                throw new OutOfMemoryError("avformat_alloc_context failed");
            }
            AvFormatContextView.pb(AvFormatContextView.of(context), bridge.context());
            var holder = arena.allocate(ADDRESS);
            holder.set(ADDRESS, 0, context);
            // The name is a hint for the formats whose content does not identify
            // them. No file is opened by it: `pb` is set, and there are no protocols.
            var name = arena.allocateFrom(source.fileName().orElse(""));

            var opened = ffmpeg.format().openInput().call(holder, name, MemorySegment.NULL, MemorySegment.NULL);
            if (opened < 0) {
                throw failure(ffmpeg, bridge.callbacks(), "avformat_open_input", opened);
            }
            var formatContext = AvFormatContextView.of(holder.get(ADDRESS, 0));
            var found = ffmpeg.format().findStreamInfo().call(formatContext, MemorySegment.NULL);
            if (found < 0) {
                ffmpeg.format().closeInput().call(holder);
                throw failure(ffmpeg, bridge.callbacks(), "avformat_find_stream_info", found);
            }
            return new Demuxer(ffmpeg, source, io, bridge, arena, holder, formatContext);
        } catch (RuntimeException | Error e) {
            arena.close();
            bridge.close();
            throw e;
        }
    }

    /// What the container holds.
    public MediaInfo info() {
        return info;
    }

    /// Hands over packets of `streams` only.
    ///
    /// Two halves, because `AVDISCARD_ALL` is a hint and not a filter. Matroska
    /// and MP4 honour it and never read a discarded stream's bytes. The generic
    /// path (WAV, raw formats) still returns its packets. So the demuxer is told,
    /// and [#read()] also drops what slips through.
    public void select(Set<Integer> streams) {
        selected = Set.copyOf(streams);
        var constants = ffmpeg.constants();
        for (var i = 0; i < AvFormatContextView.streamCount(context); i++) {
            AvStreamView.discard(
                    AvFormatContextView.stream(context, i),
                    streams.contains(i) ? constants.discardDefault() : constants.discardAll());
        }
    }

    /// The next packet of a selected stream, or null at the end of the source.
    ///
    /// The packet owns FFmpeg's memory and frees it when closed. It may be closed
    /// on any thread.
    ///
    /// @throws MediaException as [#open] does
    public @Nullable Packet read() {
        MemorySegment packet;
        MemorySegment view;
        int stream;
        do {
            packet = ffmpeg.codec().packetAlloc().call();
            if (packet.equals(MemorySegment.NULL)) {
                throw new OutOfMemoryError("av_packet_alloc failed");
            }
            var result = ffmpeg.format().readFrame().call(context, packet);
            if (result < 0) {
                free(packet);
                if (result == ffmpeg.constants().averrorEof()) {
                    return null;
                }
                throw failure(ffmpeg, bridge.callbacks(), "av_read_frame", result);
            }
            view = AvPacketView.of(packet);
            stream = AvPacketView.streamIndex(view);
            if (selected != null && !selected.contains(stream)) {
                free(packet);
                packet = MemorySegment.NULL;
            }
        } while (packet.equals(MemorySegment.NULL));
        var owned = packet;
        var size = AvPacketView.size(view);
        var data = size <= 0 ? MemorySegment.NULL : Pointers.array(AvPacketView.data(view), JAVA_BYTE, size);
        var read = Packet.owning(
                data,
                stream,
                timestamp(AvPacketView.pts(view)),
                timestamp(AvPacketView.dts(view)),
                AvPacketView.duration(view),
                (AvPacketView.flags(view) & ffmpeg.constants().pktFlagKey()) != 0,
                timeBase(stream),
                () -> free(owned));
        var alpha = alpha(owned);
        return alpha.equals(MemorySegment.NULL) ? read : read.withAlpha(alpha);
    }

    /// The BlockAdditional with BlockAddID 1 that the demuxer attached to
    /// `packet`, past its ID, or null when there is none: a WebM picture's alpha.
    private MemorySegment alpha(MemorySegment packet) {
        sideDataSize.set(JAVA_LONG, 0, 0);
        var side = ffmpeg.codec()
                .packetGetSideData()
                .call(packet, ffmpeg.constants().pktDataMatroskaBlockAdditional(), sideDataSize);
        var size = sideDataSize.get(JAVA_LONG, 0);
        if (side.equals(MemorySegment.NULL) || size <= BLOCK_ADD_ID_BYTES) {
            return MemorySegment.NULL;
        }
        var additional = Pointers.array(side, JAVA_BYTE, size);
        return additional.get(JAVA_LONG_BIG_ENDIAN, 0) == ALPHA_BLOCK_ADD_ID
                ? additional.asSlice(BLOCK_ADD_ID_BYTES, size - BLOCK_ADD_ID_BYTES)
                : MemorySegment.NULL;
    }

    /// Moves to the last keyframe at or before `positionNanos`, so that decoding
    /// from there reaches the position. The Engine discards what it decodes before
    /// the position for an accurate seek.
    ///
    /// @throws MediaException when the demuxer cannot seek, or the read it takes
    ///                        fails
    public void seek(long positionNanos) {
        var target = Rational.MICROSECONDS.fromNanos(Math.max(positionNanos, 0));
        var result = ffmpeg.format().seekFile().call(context, -1, Long.MIN_VALUE, target, target, 0);
        if (result < 0) {
            throw failure(ffmpeg, bridge.callbacks(), "avformat_seek_file", result);
        }
    }

    /// The unit of `stream`'s packet timestamps.
    public Rational timeBase(int stream) {
        var view = AvFormatContextView.stream(context, stream);
        var num = AvStreamView.timeBaseNum(view);
        var den = AvStreamView.timeBaseDen(view);
        return num > 0 && den > 0 ? new Rational(num, den) : Rational.MICROSECONDS;
    }

    /// `stream` described for a [dev.goldberry.media.codec.DecoderProvider].
    /// The extradata is valid while this demuxer is open.
    public DecoderRequest request(int stream) {
        var track = info.tracks().get(stream);
        return new DecoderRequest(
                track.codec(),
                track.codecName(),
                track.params(),
                AvCodecParametersView.extradata(codecParameters(stream)),
                timeBase(stream));
    }

    /// `stream`'s `AVCodecParameters`, for the built-in decoder.
    MemorySegment codecParameters(int stream) {
        return AvStreamView.codecParameters(AvFormatContextView.stream(context, stream));
    }

    /// Ends a read blocked in the `MediaIO`, from any thread. The demuxer answers
    /// [MediaError.Aborted] from then on. It still has to be closed, by the thread
    /// that reads it.
    public void abort() {
        bridge.callbacks().abort();
    }

    /// Frees the demuxer and its I/O context. Packets already handed out stay
    /// valid until they are closed.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            ffmpeg.format().closeInput().call(holder);
        } finally {
            arena.close();
            bridge.close();
        }
    }

    private long timestamp(long ffmpegTimestamp) {
        return ffmpegTimestamp == ffmpeg.constants().noPtsValue() ? Packet.NO_TIMESTAMP : ffmpegTimestamp;
    }

    private void free(MemorySegment packet) {
        Pointers.freeThrough(packet, ffmpeg.codec().packetFree()::call);
    }

    private MediaInfo describe() {
        var constants = ffmpeg.constants();
        var tracks = new ArrayList<Track>();
        for (var i = 0; i < AvFormatContextView.streamCount(context); i++) {
            tracks.add(track(AvFormatContextView.stream(context, i)));
        }
        var duration = Timestamps.toDuration(
                AvFormatContextView.duration(context),
                1,
                Math.toIntExact(constants.timeBase()),
                constants.noPtsValue());
        return new MediaInfo(source, duration, tracks, io.isSeekable(), io.isLive());
    }

    private Track track(MemorySegment stream) {
        var constants = ffmpeg.constants();
        var parameters = AvStreamView.codecParameters(stream);
        var codecName = ffmpeg.codecName(AvCodecParametersView.codecId(parameters));
        var disposition = AvStreamView.disposition(stream);
        var codec = CodecId.fromFfmpegName(codecName);
        var params = params(codec, stream, parameters);
        return new Track(
                AvStreamView.index(stream),
                codec,
                codecName,
                params,
                Timestamps.toDuration(
                        AvStreamView.duration(stream),
                        AvStreamView.timeBaseNum(stream),
                        AvStreamView.timeBaseDen(stream),
                        constants.noPtsValue()),
                (disposition & constants.dispositionDefault()) != 0,
                (disposition & constants.dispositionAttachedPic()) != 0,
                metadata(stream, "language").filter(language -> !language.equalsIgnoreCase("und")),
                metadata(stream, "title"),
                params instanceof TrackParams.Video ? frameCount(stream) : OptionalLong.empty());
    }

    /// How many pictures `stream` holds, when the container records it. Not
    /// asked of a sound track, where FFmpeg counts packets.
    private static OptionalLong frameCount(MemorySegment stream) {
        var frames = AvStreamView.frameCount(stream);
        return frames > 0 ? OptionalLong.of(frames) : OptionalLong.empty();
    }

    /// The stream's metadata entry named `key`, when it has one and it is not
    /// blank.
    private Optional<String> metadata(MemorySegment stream, String key) {
        var dictionary = AvStreamView.metadata(stream);
        if (dictionary.equals(MemorySegment.NULL)) {
            return Optional.empty();
        }
        try (var scratch = Arena.ofConfined()) {
            var entry = ffmpeg.util().dictGet().call(dictionary, scratch.allocateFrom(key), MemorySegment.NULL, 0);
            if (entry.equals(MemorySegment.NULL)) {
                return Optional.empty();
            }
            var value = Pointers.string(Pointers.struct(entry, FfmpegStructs.AV_DICTIONARY_ENTRY)
                    .get(ADDRESS, FfmpegStructs.AV_DICTIONARY_ENTRY.byteOffset(groupElement("value"))));
            return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.strip());
        }
    }

    private TrackParams params(CodecId codec, MemorySegment stream, MemorySegment parameters) {
        var constants = ffmpeg.constants();
        var type = AvCodecParametersView.codecType(parameters);
        var profile = known(AvCodecParametersView.profile(parameters), constants.profileUnknown());
        var bitRate = AvCodecParametersView.bitRate(parameters);
        var knownBitRate = bitRate > 0 ? OptionalLong.of(bitRate) : OptionalLong.empty();
        if (type == constants.mediaTypeVideo()) {
            return new TrackParams.Video(
                    Math.max(AvCodecParametersView.width(parameters), 0),
                    Math.max(AvCodecParametersView.height(parameters), 0),
                    ffmpeg.pixelFormatName(AvCodecParametersView.format(parameters))
                            .or(() -> pixelFormat(codec, parameters)),
                    profile,
                    known(AvCodecParametersView.level(parameters), constants.levelUnknown()),
                    knownBitRate,
                    frameRate(stream, parameters),
                    metadata(stream, "alpha_mode").filter("1"::equals).isPresent());
        }
        if (type == constants.mediaTypeAudio()) {
            return new TrackParams.Audio(
                    Math.max(AvCodecParametersView.sampleRate(parameters), 0),
                    Math.max(AvCodecParametersView.channels(parameters), 0),
                    ffmpeg.sampleFormatName(AvCodecParametersView.format(parameters)),
                    profile,
                    knownBitRate);
        }
        if (type == constants.mediaTypeSubtitle()) {
            return new TrackParams.Subtitle();
        }
        return new TrackParams.Other(type == constants.mediaTypeAttachment() ? MediaType.ATTACHMENT : MediaType.DATA);
    }

    /// The pixel format of a track FFmpeg has not named one for, from its
    /// decoder configuration record: an H.264 or HEVC track, which this build
    /// neither decodes nor parses.
    private static Optional<String> pixelFormat(CodecId codec, MemorySegment parameters) {
        var extradata = AvCodecParametersView.extradata(parameters);
        if (extradata.equals(MemorySegment.NULL)) {
            return Optional.empty();
        }
        return ParameterSets.pixelFormat(codec, extradata.toArray(JAVA_BYTE));
    }

    /// The average rate, else the one the codec's headers state, else the base
    /// rate FFmpeg finds the timestamps on.
    private static Optional<FrameRate> frameRate(MemorySegment stream, MemorySegment parameters) {
        return AvStreamView.averageFrameRate(stream)
                .or(() -> AvCodecParametersView.frameRate(parameters))
                .or(() -> AvStreamView.baseFrameRate(stream));
    }

    private static OptionalInt known(int value, int unknown) {
        return value == unknown ? OptionalInt.empty() : OptionalInt.of(value);
    }

    /// Turns a failed call into what the application should see.
    ///
    /// The callbacks know more than the return code: an abort that FFmpeg reports
    /// as a generic error is still an abort, and a failed read has the `MediaIO`'s
    /// own exception rather than "Input/output error".
    static MediaException failure(Ffmpeg ffmpeg, IoCallbacks callbacks, String function, int code) {
        var constants = ffmpeg.constants();
        var cause = new FfmpegException(function, code, ffmpeg.describe(code));
        if (callbacks.aborted() || code == constants.averrorExit()) {
            return new MediaException(new MediaError.Aborted(), cause);
        }
        var ioFailure = callbacks.failure();
        if (ioFailure != null) {
            var message = ioFailure.getMessage();
            return new MediaException(
                    new MediaError.Io(
                            message != null ? message : ioFailure.getClass().getSimpleName()),
                    ioFailure);
        }
        if (code == constants.averrorInvalidData()) {
            var container = ContainerSniffer.identify(callbacks.head(), FfmpegCapabilities.demuxers(ffmpeg));
            if (container.isPresent()) {
                return new MediaException(
                        new MediaError.UnsupportedContainer(container.get().name()), cause);
            }
        }
        return new MediaException(new MediaError.InvalidData(ffmpeg.describe(code)), cause);
    }
}
