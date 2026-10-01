package dev.goldberry.media.platform.macos;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.media.codec.AudioFrame;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.SampleFormat;
import dev.goldberry.media.codec.TrackParams;

/// One AAC, AC-3 or E-AC-3 track decoded by an AudioToolbox audio converter.
///
/// **Configuration:** AAC is configured by a magic cookie, the `ES_Descriptor`
/// built around the container's `AudioSpecificConfig` ([AacCookie]), and decodes
/// to the best layer AudioToolbox lists for it: HE-AAC at its full rate, not its
/// core's. AC-3 and E-AC-3 need nothing but the rate and channel count; every
/// frame carries its own header.
///
/// **Decoding:** one packet per [#send], converted at once into interleaved
/// 32-bit float in FFmpeg's channel order ([ChannelLabels]). The converter pulls
/// its input through a callback, which hands over the packet and then answers
/// "not yet" so that the conversion returns with what it made. [#sendEnd] lets
/// the converter drain what it holds.
///
/// **Timing:** the first packet after an open or a flush anchors the clock, and
/// each chunk after it is timed by the samples before it. Timing by sample count
/// is exact, where a packet's timestamp would have to be matched to output that
/// the converter can delay. A packet whose timestamp strays from the count by
/// more than [#DISCONTINUITY_NANOS] re-anchors it: a gap in the stream.
final class AudioToolboxDecoder implements Decoder {

    private static final Logger LOG = Logs.of(AudioToolboxDecoder.class);

    /// The frames of one output chunk: enough for a packet of any of the three
    /// codecs (AAC 1024, HE-AAC 2048, AC-3 and E-AC-3 1536 at most).
    static final int CHUNK_FRAMES = 4096;

    /// How far a packet's timestamp may be from the sample count before it is
    /// taken as a gap in the stream.
    static final long DISCONTINUITY_NANOS = 200_000_000L;

    /// Packets in a row the converter may reject before it is given up on.
    static final int MAX_CONSECUTIVE_FAILURES = 30;

    /// What the input callback answers when it has handed over the packet and has
    /// no other: the conversion returns, with this status, and resumes on the
    /// next packet. Any non-zero code does; this one reads as what it means.
    static final int STATUS_WAITING_FOR_INPUT = OsStatus.code("gbIn");

    /// What the input callback answers when its own code failed.
    private static final int STATUS_CALLBACK_FAILED = OsStatus.code("gbEr");

    private static final long ASBD_SAMPLE_RATE = offset("mSampleRate");
    private static final long ASBD_FORMAT_ID = offset("mFormatID");
    private static final long ASBD_FORMAT_FLAGS = offset("mFormatFlags");
    private static final long ASBD_BYTES_PER_PACKET = offset("mBytesPerPacket");
    private static final long ASBD_FRAMES_PER_PACKET = offset("mFramesPerPacket");
    private static final long ASBD_BYTES_PER_FRAME = offset("mBytesPerFrame");
    private static final long ASBD_CHANNELS = offset("mChannelsPerFrame");
    private static final long ASBD_BITS = offset("mBitsPerChannel");

    private static final long LIST_BUFFERS = AudioToolbox.BUFFER_LIST.byteOffset(groupElement("mNumberBuffers"));
    private static final long LIST_CHANNELS = AudioToolbox.BUFFER_LIST.byteOffset(groupElement("mNumberChannels"));
    private static final long LIST_BYTES = AudioToolbox.BUFFER_LIST.byteOffset(groupElement("mDataByteSize"));
    private static final long LIST_DATA = AudioToolbox.BUFFER_LIST.byteOffset(groupElement("mData"));

    private static final long PACKET_BYTES = AudioToolbox.PACKET_DESCRIPTION.byteOffset(groupElement("mDataByteSize"));

    private static final MethodHandle INPUT;

    static {
        try {
            INPUT = MethodHandles.lookup()
                    .findVirtual(
                            AudioToolboxDecoder.class,
                            "input",
                            MethodType.methodType(
                                    int.class,
                                    MemorySegment.class,
                                    MemorySegment.class,
                                    MemorySegment.class,
                                    MemorySegment.class,
                                    MemorySegment.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /// Decoded samples: `frames` frames at the start of `buffer`, starting at
    /// `ptsNanos`.
    private record Chunk(MemorySegment buffer, int frames, long ptsNanos) {}

    private final Frameworks fw;
    private final String codecName;
    /// Shared: the decoder is opened on one thread and used on another.
    private final Arena arena = Arena.ofShared();

    private final int sampleRate;
    private final int channels;
    private final int inputChannels;
    private final long chunkBytes;
    private final MemorySegment inputProc;
    private final MemorySegment outputList;
    private final MemorySegment outputFrames;
    private final MemorySegment packetDescription;
    private final ArrayDeque<MemorySegment> freeBuffers = new ArrayDeque<>();
    private final ArrayDeque<Chunk> ready = new ArrayDeque<>();

    // NULL until the constructor creates it. A constructor that fails part-way
    // calls close(), which disposes of the converter only when it is not NULL.
    @SuppressWarnings("UnusedAssignment")
    private MemorySegment converter = MemorySegment.NULL;

    private MemorySegment input;
    private int inputBytes;
    private boolean inputPending;
    private boolean endOfStream;
    private @Nullable Chunk lent;
    private boolean ending;
    private boolean drained;
    private long anchorNanos = Frame.NO_PTS;
    private long samplesSinceAnchor;
    private int consecutiveFailures;
    private @Nullable Throwable callbackError;

    /// Opens a converter for `request`, an AAC, AC-3 or E-AC-3 audio track.
    ///
    /// @throws OsStatus.Failure when the system decodes no such stream
    @SuppressWarnings("restricted")
    AudioToolboxDecoder(Frameworks fw, DecoderRequest request) {
        this.fw = Objects.requireNonNull(fw, "fw");
        this.codecName = request.codecName();
        if (!(request.params() instanceof TrackParams.Audio audio)) {
            throw new IllegalArgumentException(request.codecName() + " is not an audio track");
        }
        try {
            var source = arena.allocate(AudioToolbox.STREAM_DESCRIPTION);
            var cookie = MemorySegment.NULL;
            if (request.codec() == CodecId.AAC) {
                var bytes = AacCookie.of(request.extradata().toArray(JAVA_BYTE));
                cookie = arena.allocateFrom(JAVA_BYTE, bytes);
                // The first layer listed is the best one: HE-AAC's, over its core.
                var best = fw.at().formatList(arena, AudioToolbox.FORMAT_AAC, cookie);
                MemorySegment.copy(best, 0, source, 0, AudioToolbox.STREAM_DESCRIPTION.byteSize());
            } else {
                source.set(JAVA_DOUBLE, ASBD_SAMPLE_RATE, audio.sampleRate());
                source.set(
                        JAVA_INT,
                        ASBD_FORMAT_ID,
                        request.codec() == CodecId.AC3 ? AudioToolbox.FORMAT_AC3 : AudioToolbox.FORMAT_EAC3);
                source.set(JAVA_INT, ASBD_FRAMES_PER_PACKET, 1536);
                source.set(JAVA_INT, ASBD_CHANNELS, audio.channels());
            }
            var rate = (int) Math.round(source.get(JAVA_DOUBLE, ASBD_SAMPLE_RATE));
            var count = source.get(JAVA_INT, ASBD_CHANNELS);
            this.sampleRate = rate > 0 ? rate : audio.sampleRate();
            this.inputChannels = count > 0 ? count : audio.channels();
            if (sampleRate <= 0 || inputChannels <= 0 || inputChannels > 8) {
                throw new IllegalArgumentException(
                        codecName + " at " + sampleRate + " Hz with " + inputChannels + " channels");
            }
            source.set(JAVA_DOUBLE, ASBD_SAMPLE_RATE, sampleRate);
            source.set(JAVA_INT, ASBD_CHANNELS, inputChannels);
            this.channels = inputChannels;

            var destination = arena.allocate(AudioToolbox.STREAM_DESCRIPTION);
            var frameBytes = (int) JAVA_FLOAT.byteSize() * channels;
            destination.set(JAVA_DOUBLE, ASBD_SAMPLE_RATE, sampleRate);
            destination.set(JAVA_INT, ASBD_FORMAT_ID, AudioToolbox.FORMAT_LINEAR_PCM);
            destination.set(JAVA_INT, ASBD_FORMAT_FLAGS, AudioToolbox.FLAGS_PACKED_FLOAT);
            destination.set(JAVA_INT, ASBD_BYTES_PER_PACKET, frameBytes);
            destination.set(JAVA_INT, ASBD_FRAMES_PER_PACKET, 1);
            destination.set(JAVA_INT, ASBD_BYTES_PER_FRAME, frameBytes);
            destination.set(JAVA_INT, ASBD_CHANNELS, channels);
            destination.set(JAVA_INT, ASBD_BITS, 32);

            this.converter = fw.at().newConverter(source, destination);
            if (!cookie.equals(MemorySegment.NULL)) {
                fw.at().setProperty(converter, AudioToolbox.PROPERTY_MAGIC_COOKIE, cookie);
            }
            orderChannels();

            this.chunkBytes = (long) CHUNK_FRAMES * frameBytes;
            this.inputProc = Linker.nativeLinker().upcallStub(INPUT.bindTo(this), AudioToolbox.INPUT_PROC, arena);
            this.outputList = arena.allocate(AudioToolbox.BUFFER_LIST);
            this.outputFrames = arena.allocate(JAVA_INT);
            this.packetDescription = arena.allocate(AudioToolbox.PACKET_DESCRIPTION);
            this.input = arena.allocate(4096);
        } catch (RuntimeException e) {
            close();
            throw e;
        }
        LOG.debug("AudioToolbox opened {} at {} Hz, {} channels", codecName, sampleRate, channels);
    }

    /// The rate the converter decodes at: HE-AAC's full rate, which a container
    /// may report as its core's.
    int sampleRate() {
        return sampleRate;
    }

    /// The channels each frame has.
    int channels() {
        return channels;
    }

    @Override
    public boolean send(Packet packet) {
        throwIfCallbackFailed();
        if (!ready.isEmpty()) {
            return false;
        }
        var data = packet.data();
        if (data.byteSize() > input.byteSize()) {
            input = arena.allocate(Math.max(data.byteSize(), input.byteSize() * 2));
        }
        MemorySegment.copy(data, 0, input, 0, data.byteSize());
        inputBytes = (int) data.byteSize();
        inputPending = true;
        anchor(packet.ptsNanos());
        convert(false);
        return true;
    }

    @Override
    public void sendEnd() {
        throwIfCallbackFailed();
        ending = true;
    }

    @Override
    public Received receive() {
        releaseLent();
        throwIfCallbackFailed();
        if (ready.isEmpty() && ending && !drained) {
            drained = true;
            convert(true);
        }
        var chunk = ready.poll();
        if (chunk != null) {
            lent = chunk;
            var plane = chunk.buffer().asSlice(0, SampleFormat.F32.planeSize(channels, chunk.frames()));
            return new Received.Decoded(new AudioFrame(
                    SampleFormat.F32, sampleRate, channels, chunk.frames(), List.of(plane), chunk.ptsNanos()));
        }
        return ending ? Received.ENDED : Received.NEEDS_INPUT;
    }

    @Override
    public void flush() {
        releaseLent();
        for (var chunk = ready.poll(); chunk != null; chunk = ready.poll()) {
            freeBuffers.add(chunk.buffer());
        }
        fw.at().reset(converter);
        inputPending = false;
        ending = false;
        drained = false;
        anchorNanos = Frame.NO_PTS;
        samplesSinceAnchor = 0;
        consecutiveFailures = 0;
    }

    @Override
    public void close() {
        lent = null;
        ready.clear();
        freeBuffers.clear();
        if (!converter.equals(MemorySegment.NULL)) {
            fw.at().dispose(converter);
            converter = MemorySegment.NULL;
        }
        if (arena.scope().isAlive()) {
            arena.close();
        }
    }

    /// The converter's input callback: the packet [#send] copied, once, then
    /// [#STATUS_WAITING_FOR_INPUT] — or, at the end of the stream, no packets and
    /// `noErr`, which tells the converter to drain. Nothing may be thrown back
    /// into native code.
    @SuppressWarnings({"unused", "restricted", "PMD.AvoidCatchingThrowable"}) // Called through INPUT.
    private int input(
            MemorySegment converterRef,
            MemorySegment ioPackets,
            MemorySegment ioData,
            MemorySegment outDescriptions,
            MemorySegment userData) {
        var packets = ioPackets.reinterpret(JAVA_INT.byteSize());
        try {
            var data = ioData.reinterpret(AudioToolbox.BUFFER_LIST.byteSize());
            if (!inputPending) {
                packets.set(JAVA_INT, 0, 0);
                data.set(JAVA_INT, LIST_BYTES, 0);
                data.set(ADDRESS, LIST_DATA, MemorySegment.NULL);
                return endOfStream ? OsStatus.OK : STATUS_WAITING_FOR_INPUT;
            }
            inputPending = false;
            packets.set(JAVA_INT, 0, 1);
            data.set(JAVA_INT, LIST_BUFFERS, 1);
            data.set(JAVA_INT, LIST_CHANNELS, inputChannels);
            data.set(JAVA_INT, LIST_BYTES, inputBytes);
            data.set(ADDRESS, LIST_DATA, input);
            if (!outDescriptions.equals(MemorySegment.NULL)) {
                packetDescription.fill((byte) 0);
                packetDescription.set(JAVA_INT, PACKET_BYTES, inputBytes);
                outDescriptions.reinterpret(ADDRESS.byteSize()).set(ADDRESS, 0, packetDescription);
            }
            return OsStatus.OK;
        } catch (Throwable t) {
            callbackError = t;
            packets.set(JAVA_INT, 0, 0);
            return STATUS_CALLBACK_FAILED;
        }
    }

    /// Runs the converter over what the input callback has, into chunks, until it
    /// asks for input that is not there. At the end of the stream, until it has
    /// nothing left.
    private void convert(boolean end) {
        endOfStream = end;
        while (true) {
            var buffer =
                    freeBuffers.isEmpty() ? arena.allocate(chunkBytes, JAVA_FLOAT.byteAlignment()) : freeBuffers.pop();
            outputList.set(JAVA_INT, LIST_BUFFERS, 1);
            outputList.set(JAVA_INT, LIST_CHANNELS, channels);
            outputList.set(JAVA_INT, LIST_BYTES, (int) chunkBytes);
            outputList.set(ADDRESS, LIST_DATA, buffer);
            outputFrames.set(JAVA_INT, 0, CHUNK_FRAMES);
            var status = fw.at().fill(converter, inputProc, outputFrames, outputList);
            throwIfCallbackFailed();
            var frames = outputFrames.get(JAVA_INT, 0);
            if (frames > 0) {
                ready.add(new Chunk(buffer, frames, ptsOfNext()));
                samplesSinceAnchor += frames;
            } else {
                freeBuffers.push(buffer);
            }
            if (status == OsStatus.OK && frames == CHUNK_FRAMES) {
                continue; // The chunk filled: there may be more.
            }
            if (status == OsStatus.OK || status == STATUS_WAITING_FOR_INPUT) {
                consecutiveFailures = 0;
            } else {
                // The packet was not decodable. Drop it, as FFmpeg's decoders drop
                // one, and give up only on a run of them.
                inputPending = false;
                if (++consecutiveFailures > MAX_CONSECUTIVE_FAILURES) {
                    throw new OsStatus.Failure(
                            "AudioToolbox rejected " + consecutiveFailures + " packets of " + codecName
                                    + " in a row; the last",
                            status);
                }
                LOG.debug("AudioToolbox dropped a packet of {}: {}", codecName, OsStatus.describe(status));
            }
            return;
        }
    }

    /// Anchors the sample clock at a packet's timestamp: the first packet's, or
    /// one that strays from the count.
    private void anchor(long packetNanos) {
        if (packetNanos == Frame.NO_PTS) {
            return;
        }
        if (anchorNanos == Frame.NO_PTS) {
            anchorNanos = packetNanos;
            samplesSinceAnchor = 0;
            return;
        }
        var expected = ptsOfNext();
        if (Math.abs(packetNanos - expected) > DISCONTINUITY_NANOS && ready.isEmpty()) {
            LOG.debug("{} jumps {} ms; timing from the packet", codecName, (packetNanos - expected) / 1_000_000);
            anchorNanos = packetNanos;
            samplesSinceAnchor = 0;
        }
    }

    /// When the next decoded sample plays.
    private long ptsOfNext() {
        return anchorNanos == Frame.NO_PTS
                ? Frame.NO_PTS
                : anchorNanos + samplesSinceAnchor * 1_000_000_000L / sampleRate;
    }

    /// Asks the converter for FFmpeg's channel order. A converter that cannot
    /// remap keeps its own, which for mono and stereo is the same.
    private void orderChannels() {
        var labels = ChannelLabels.forChannels(inputChannels);
        if (labels.length == 0) {
            return;
        }
        try (var temporary = Arena.ofConfined()) {
            var layout = temporary.allocate(
                    AudioToolbox.CHANNEL_LAYOUT_HEADER + AudioToolbox.CHANNEL_DESCRIPTION * labels.length, 4);
            layout.set(JAVA_INT, 0, AudioToolbox.LAYOUT_TAG_USE_DESCRIPTIONS);
            layout.set(JAVA_INT, 8, labels.length);
            for (var i = 0; i < labels.length; i++) {
                layout.set(
                        JAVA_INT, AudioToolbox.CHANNEL_LAYOUT_HEADER + AudioToolbox.CHANNEL_DESCRIPTION * i, labels[i]);
            }
            fw.at().setProperty(converter, AudioToolbox.PROPERTY_OUTPUT_CHANNEL_LAYOUT, layout);
        } catch (OsStatus.Failure e) {
            LOG.warn(
                    "AudioToolbox cannot put {}'s {} channels in FFmpeg's order; they play in the stream's",
                    codecName,
                    inputChannels,
                    e);
        }
    }

    private void releaseLent() {
        var chunk = lent;
        if (chunk != null) {
            lent = null;
            freeBuffers.push(chunk.buffer());
        }
    }

    private void throwIfCallbackFailed() {
        var error = callbackError;
        if (error != null) {
            throw new IllegalStateException("AudioToolbox's input callback failed for " + codecName, error);
        }
    }

    private static long offset(String field) {
        return AudioToolbox.STREAM_DESCRIPTION.byteOffset(groupElement(field));
    }
}
