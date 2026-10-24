package dev.goldberry.media.decoder.flac;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.codec.AudioFrame;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.SampleFormat;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.ffm.MediaLibrary;
import dev.goldberry.media.ffm.NativeConstants;
import dev.goldberry.media.ffm.NativeStructs;
import dev.goldberry.media.ffm.calls.FlacCalls;

/// A FLAC decoder over libFLAC's stream decoder: one packet in, one block of
/// planar integer samples out.
///
/// libFLAC pulls its bytes itself, through a read callback, and hands each
/// decoded block to a write callback; both are upcalls into this class. What it
/// pulls is the stream a `.flac` file would hold: the `fLaC` marker and the
/// metadata blocks first, then every frame in order. The header comes from the
/// container's record (see [#header]) and is fed before the first packet, one
/// `process_single` per metadata block until the decoder waits for a frame's
/// sync code. From then on each packet is one whole frame, which is what every
/// container stores: the packet's bytes go into the read queue, one
/// `process_single` decodes exactly one frame from them, and the write callback
/// copies the block out of libFLAC's buffers, which are valid only during the
/// callback, into planes this decoder owns. The read callback answers
/// end-of-stream only when the queue is empty, which a whole frame never
/// provokes; a packet that does provoke it was not a frame, and is an error.
///
/// Samples up to 16 bits wide come out as [SampleFormat#S16_PLANAR] as they
/// are; wider ones as [SampleFormat#S32_PLANAR], shifted up to the top of the
/// word so that the later conversion to float is one division by 2³¹ whatever
/// the depth.
public final class FlacDecoder implements Decoder {

    /// `FLAC__StreamDecoderReadStatus (*)(const FLAC__StreamDecoder *, FLAC__byte buffer[], size_t *bytes, void
    /// *client_data)`
    static final FunctionDescriptor READ = FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS);

    /// `FLAC__StreamDecoderWriteStatus (*)(const FLAC__StreamDecoder *, const FLAC__Frame *frame, const FLAC__int32
    /// *const buffer[], void *client_data)`
    static final FunctionDescriptor WRITE = FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS);

    /// `void (*)(const FLAC__StreamDecoder *, FLAC__StreamDecoderErrorStatus status, void *client_data)`
    static final FunctionDescriptor ERROR = FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS);

    /// Every descriptor an `upcallStub` here is made with, for the native-image
    /// metadata.
    public static final List<FunctionDescriptor> UPCALLS = List.of(READ, WRITE, ERROR);

    /// The size of a STREAMINFO block's body, the one metadata block every
    /// FLAC stream begins with.
    static final int STREAMINFO_SIZE = 34;

    private static final byte[] MARKER = "fLaC".getBytes(StandardCharsets.US_ASCII);

    /// What the error callback has not been told: no error since the last
    /// `process_single`.
    private static final int NO_ERROR = -1;

    private static final long BLOCKSIZE = NativeStructs.FLAC_FRAME_HEADER.byteOffset(groupElement("blocksize"));
    private static final long SAMPLE_RATE = NativeStructs.FLAC_FRAME_HEADER.byteOffset(groupElement("sample_rate"));
    private static final long CHANNELS = NativeStructs.FLAC_FRAME_HEADER.byteOffset(groupElement("channels"));
    private static final long BITS_PER_SAMPLE =
            NativeStructs.FLAC_FRAME_HEADER.byteOffset(groupElement("bits_per_sample"));

    private static final MethodHandle READ_HANDLE;
    private static final MethodHandle WRITE_HANDLE;
    private static final MethodHandle ERROR_HANDLE;

    static {
        try {
            var lookup = MethodHandles.lookup();
            READ_HANDLE = lookup.findVirtual(FlacDecoder.class, "read", READ.toMethodType());
            WRITE_HANDLE = lookup.findVirtual(FlacDecoder.class, "write", WRITE.toMethodType());
            ERROR_HANDLE = lookup.findVirtual(FlacDecoder.class, "error", ERROR.toMethodType());
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final FlacCalls flac;
    private final NativeConstants.Flac constants;
    private final Arena arena = Arena.ofShared();
    private final MemorySegment decoder;

    /// The bytes libFLAC has not pulled yet: `queue[from, to)`.
    private MemorySegment queue;

    private long queueFrom;
    private long queueTo;

    /// One plane per channel, each `planeBytes` long, holding the frame handed
    /// out last.
    private MemorySegment[] planes = new MemorySegment[0];

    private long planeBytes;

    /// The pts of the packet being decoded, for the frame the write callback
    /// makes of it.
    private long packetPts = Frame.NO_PTS;

    private @Nullable AudioFrame pending;
    private int errorStatus = NO_ERROR;
    private @Nullable Throwable failure;
    private boolean ended;
    private boolean closed;

    /// Opens a decoder for `request`, a FLAC track, and feeds it the stream
    /// header.
    ///
    /// @throws MediaException when the record is not a FLAC header, or libFLAC
    ///                        refuses the stream
    public FlacDecoder(MediaLibrary library, DecoderRequest request) {
        Objects.requireNonNull(request, "request");
        this.flac = library.flac();
        this.constants = library.constants().flac();
        var header = header(request);
        var created = flac.newDecoder().call();
        if (created.equals(MemorySegment.NULL)) {
            arena.close();
            throw new MediaException(new MediaError.InvalidData("FLAC__stream_decoder_new: out of memory"));
        }
        this.decoder = created;
        try {
            // A frame's planes, sized for the biggest block the header
            // promises; grown should a frame be bigger anyway.
            this.planeBytes = (long) maxBlockSize(header) * Integer.BYTES;
            this.queue = arena.allocate(Math.max(4096, header.length));
            flac.setMetadataIgnoreAll().call(decoder);
            flac.setMd5Checking().call(decoder, 0);
            var read = stub(READ_HANDLE, READ);
            var write = stub(WRITE_HANDLE, WRITE);
            var error = stub(ERROR_HANDLE, ERROR);
            var status = flac.initStream()
                    .call(
                            decoder,
                            read,
                            MemorySegment.NULL,
                            MemorySegment.NULL,
                            MemorySegment.NULL,
                            MemorySegment.NULL,
                            write,
                            MemorySegment.NULL,
                            error,
                            MemorySegment.NULL);
            if (status != constants.initStatusOk()) {
                throw new MediaException(
                        new MediaError.InvalidData("FLAC__stream_decoder_init_stream: init status " + status));
            }
            enqueue(MemorySegment.ofArray(header));
            // One process_single per metadata block, until libFLAC is through
            // them and waits for a frame. A header that runs out first leaves
            // it at the end of the stream, which is not where a header ends.
            while (flac.getState().call(decoder) != constants.stateSearchForFrameSync()) {
                var ok = processSingle();
                var state = flac.getState().call(decoder);
                if (!ok
                        || errorStatus != NO_ERROR
                        || state == constants.stateEndOfStream()
                        || state == constants.stateAborted()) {
                    throw new MediaException(new MediaError.InvalidData("not a FLAC stream header: " + describe()));
                }
            }
        } catch (RuntimeException | Error e) {
            destroy();
            throw e;
        }
    }

    /// `handle` bound to this decoder, as a function pointer that lives as
    /// long as the arena.
    @SuppressWarnings("restricted")
    private MemorySegment stub(MethodHandle handle, FunctionDescriptor descriptor) {
        return Linker.nativeLinker().upcallStub(handle.bindTo(this), descriptor, arena);
    }

    /// The stream header libFLAC reads first, from the container's record.
    ///
    /// Matroska's `CodecPrivate` is the whole thing, `fLaC` and the metadata
    /// blocks; MP4's `dfLa` and Ogg hand over the bare STREAMINFO body, which
    /// gets the marker and a block header saying it is the last block; blocks
    /// without the marker get the marker. A track with no record at all gets a
    /// STREAMINFO made up from its parameters, at 16 bits: a fallback, good
    /// only because every frame header states its own rate, channels and depth
    /// and the frame decoder consults STREAMINFO for none of them.
    static byte[] header(DecoderRequest request) {
        var extradata = request.extradata();
        var record = extradata.byteSize() == 0 ? new byte[0] : extradata.toArray(JAVA_BYTE);
        if (record.length == 0) {
            return synthesised(request.params());
        }
        if (startsWithMarker(record)) {
            return record;
        }
        if (record.length == STREAMINFO_SIZE) {
            var header = new byte[MARKER.length + 4 + STREAMINFO_SIZE];
            System.arraycopy(MARKER, 0, header, 0, MARKER.length);
            header[4] = (byte) 0x80;
            header[7] = STREAMINFO_SIZE;
            System.arraycopy(record, 0, header, 8, STREAMINFO_SIZE);
            return header;
        }
        if (record.length > 4 + STREAMINFO_SIZE
                && (record[0] & 0x7f) == 0
                && blockLength(record, 0) == STREAMINFO_SIZE) {
            var header = new byte[MARKER.length + record.length];
            System.arraycopy(MARKER, 0, header, 0, MARKER.length);
            System.arraycopy(record, 0, header, MARKER.length, record.length);
            return header;
        }
        throw new MediaException(
                new MediaError.InvalidData("a FLAC record of " + record.length + " bytes that is not a stream header"));
    }

    /// `fLaC`, a last STREAMINFO block, and in it `params`' rate and channels
    /// at 16 bits, block sizes anywhere in the legal range, frame sizes and
    /// length unknown, no MD5. Stereo at 44.1 kHz when the track does not say.
    private static byte[] synthesised(TrackParams params) {
        var rate = 44_100;
        var channels = 2;
        if (params instanceof TrackParams.Audio(var sampleRate, var count, var _, var _, var _)) {
            rate = sampleRate > 0 ? sampleRate : rate;
            channels = count > 0 ? count : channels;
        }
        var header = new byte[MARKER.length + 4 + STREAMINFO_SIZE];
        System.arraycopy(MARKER, 0, header, 0, MARKER.length);
        header[4] = (byte) 0x80;
        header[7] = STREAMINFO_SIZE;
        var info = 8;
        header[info + 1] = 16; // min block size
        header[info + 2] = (byte) 0xff; // max block size
        header[info + 3] = (byte) 0xff;
        // 20 bits of rate, 3 of channels - 1, 5 of bits - 1, 36 of total samples.
        var packed = ((long) rate << 44) | ((long) (channels - 1) << 41) | ((long) (16 - 1) << 36);
        for (var i = 0; i < 8; i++) {
            header[info + 10 + i] = (byte) (packed >>> (56 - 8 * i));
        }
        return header;
    }

    /// The biggest block `header`'s STREAMINFO promises, or FLAC's biggest
    /// when the header does not say.
    private static int maxBlockSize(byte[] header) {
        var info = MARKER.length + 4;
        if (header.length >= info + STREAMINFO_SIZE && startsWithMarker(header)) {
            var max = ((header[info + 2] & 0xff) << 8) | (header[info + 3] & 0xff);
            if (max >= 16) {
                return max;
            }
        }
        return 65_535;
    }

    private static boolean startsWithMarker(byte[] bytes) {
        return bytes.length >= MARKER.length && Arrays.equals(bytes, 0, MARKER.length, MARKER, 0, MARKER.length);
    }

    private static int blockLength(byte[] bytes, int at) {
        return ((bytes[at + 1] & 0xff) << 16) | ((bytes[at + 2] & 0xff) << 8) | (bytes[at + 3] & 0xff);
    }

    @Override
    public boolean send(Packet packet) {
        requireOpen();
        if (ended) {
            throw new IllegalStateException("a packet after the end");
        }
        if (pending != null) {
            return false;
        }
        var bytes = packet.data();
        if (bytes.byteSize() == 0) {
            return true;
        }
        enqueue(bytes);
        packetPts = packet.ptsNanos();
        try {
            if (!processSingle()) {
                throw new MediaException(
                        new MediaError.InvalidData("FLAC__stream_decoder_process_single: " + describe()));
            }
            if (pending == null) {
                // libFLAC read everything and decoded nothing: the packet was
                // not a whole frame.
                throw new MediaException(
                        new MediaError.InvalidData("a packet that is not one whole FLAC frame: " + describe()));
            }
        } catch (MediaException e) {
            // Whatever the packet left libFLAC in, the end of its stream or a
            // lost sync, the next packet starts a fresh sync search.
            queueFrom = queueTo = 0;
            flac.flush().call(decoder);
            throw e;
        }
        return true;
    }

    @Override
    public void sendEnd() {
        requireOpen();
        ended = true;
    }

    @Override
    public Received receive() {
        requireOpen();
        var frame = pending;
        if (frame != null) {
            pending = null;
            return new Received.Decoded(frame);
        }
        return ended ? Received.ENDED : Received.NEEDS_INPUT;
    }

    @Override
    public void flush() {
        requireOpen();
        pending = null;
        ended = false;
        queueFrom = queueTo = 0;
        // Back to searching for a frame's sync code, the metadata kept.
        if (flac.flush().call(decoder) == 0) {
            throw new MediaException(new MediaError.InvalidData("FLAC__stream_decoder_flush: " + describe()));
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        pending = null;
        destroy();
    }

    /// Frees libFLAC's decoder, then the arena its callbacks live in: the
    /// other order would leave a decoder able to call freed stubs.
    private void destroy() {
        flac.finish().call(decoder);
        flac.deleteDecoder().call(decoder);
        arena.close();
    }

    /// Appends `bytes` to what the read callback hands out, moving what is
    /// still queued to the front first.
    private void enqueue(MemorySegment bytes) {
        var length = bytes.byteSize();
        var queued = queueTo - queueFrom;
        if (queued == 0) {
            queueFrom = queueTo = 0;
        } else if (queueFrom > 0) {
            MemorySegment.copy(queue, queueFrom, queue, 0, queued);
            queueFrom = 0;
            queueTo = queued;
        }
        if (queue.byteSize() - queueTo < length) {
            var bigger = arena.allocate(Math.max(queueTo + length, queue.byteSize() * 2));
            MemorySegment.copy(queue, 0, bigger, 0, queueTo);
            queue = bigger;
        }
        MemorySegment.copy(bytes, 0, queue, queueTo, length);
        queueTo += length;
    }

    /// One `process_single`, with what the callbacks saw turned into
    /// exceptions: a failure of the Java side rethrown, an error libFLAC
    /// reported made a [MediaException].
    private boolean processSingle() {
        errorStatus = NO_ERROR;
        failure = null;
        var ok = flac.processSingle().call(decoder) != 0;
        var failed = failure;
        if (failed instanceof RuntimeException e) {
            throw e;
        }
        if (failed instanceof Error e) {
            throw e;
        }
        if (errorStatus != NO_ERROR) {
            throw new MediaException(
                    new MediaError.InvalidData("libFLAC: " + errorName(errorStatus) + " (" + describe() + ")"));
        }
        return ok;
    }

    /// libFLAC's read callback: as many queued bytes as fit, or the end of the
    /// stream once the queue is empty.
    @SuppressWarnings({"restricted", "unused"})
    private int read(MemorySegment flacDecoder, MemorySegment buffer, MemorySegment bytes, MemorySegment clientData) {
        try {
            var count = bytes.reinterpret(JAVA_LONG.byteSize());
            var wanted = count.get(JAVA_LONG, 0);
            var available = queueTo - queueFrom;
            if (available == 0 || wanted <= 0) {
                count.set(JAVA_LONG, 0, 0L);
                return constants.readStatusEndOfStream();
            }
            var length = Math.min(wanted, available);
            MemorySegment.copy(queue, queueFrom, buffer.reinterpret(length), 0, length);
            queueFrom += length;
            count.set(JAVA_LONG, 0, length);
            return constants.readStatusContinue();
        } catch (RuntimeException | Error e) {
            failure = e;
            return constants.readStatusAbort();
        }
    }

    /// libFLAC's write callback: the block copied into planes of this
    /// decoder's own, as the pending frame.
    @SuppressWarnings({"restricted", "unused"})
    private int write(MemorySegment flacDecoder, MemorySegment frame, MemorySegment buffer, MemorySegment clientData) {
        try {
            var header = frame.reinterpret(NativeStructs.FLAC_FRAME_HEADER.byteSize());
            var blocksize = header.get(JAVA_INT, BLOCKSIZE);
            var sampleRate = header.get(JAVA_INT, SAMPLE_RATE);
            var channels = header.get(JAVA_INT, CHANNELS);
            var bits = header.get(JAVA_INT, BITS_PER_SAMPLE);
            if (blocksize <= 0
                    || sampleRate <= 0
                    || channels <= 0
                    || channels > constants.maxChannels()
                    || bits <= 0
                    || bits > 32) {
                throw new MediaException(new MediaError.InvalidData("a FLAC frame of " + blocksize + " samples, "
                        + channels + " channels, " + bits + " bits at " + sampleRate + " Hz"));
            }
            var format = bits <= 16 ? SampleFormat.S16_PLANAR : SampleFormat.S32_PLANAR;
            var pointers = buffer.reinterpret(channels * ADDRESS.byteSize());
            var planeLength = format.planeSize(channels, blocksize);
            ensurePlanes(channels, (long) blocksize * Integer.BYTES);
            var shift = 32 - bits;
            var frames = new ArrayList<MemorySegment>(channels);
            for (var channel = 0; channel < channels; channel++) {
                var source = pointers.getAtIndex(ADDRESS, channel).reinterpret((long) blocksize * Integer.BYTES);
                var plane = planes[channel];
                if (format == SampleFormat.S16_PLANAR) {
                    for (var i = 0; i < blocksize; i++) {
                        plane.setAtIndex(JAVA_SHORT, i, (short) source.getAtIndex(JAVA_INT, i));
                    }
                } else {
                    for (var i = 0; i < blocksize; i++) {
                        plane.setAtIndex(JAVA_INT, i, source.getAtIndex(JAVA_INT, i) << shift);
                    }
                }
                frames.add(plane.asSlice(0, planeLength));
            }
            pending = new AudioFrame(format, sampleRate, channels, blocksize, frames, packetPts);
            return constants.writeStatusContinue();
        } catch (RuntimeException | Error e) {
            failure = e;
            return constants.writeStatusAbort();
        }
    }

    /// libFLAC's error callback: remembered, and thrown once `process_single`
    /// returns.
    @SuppressWarnings("unused")
    private void error(MemorySegment flacDecoder, int status, MemorySegment clientData) {
        if (errorStatus == NO_ERROR) {
            errorStatus = status;
        }
    }

    /// Planes for `channels` channels of `bytes` each, the ones there are when
    /// they are big enough. A grown plane leaves the old one to the arena;
    /// that happens at most a few times in a stream's life.
    private void ensurePlanes(int channels, long bytes) {
        if (channels <= planes.length && bytes <= planeBytes) {
            return;
        }
        var size = Math.max(bytes, planeBytes);
        var grown = Arrays.copyOf(planes, Math.max(channels, planes.length));
        for (var channel = 0; channel < grown.length; channel++) {
            if (grown[channel] == null || size > planeBytes) {
                grown[channel] = arena.allocate(size, 64);
            }
        }
        planes = grown;
        planeBytes = size;
    }

    /// `FLAC__StreamDecoderErrorStatus` by name, from `format.h`'s order.
    private static String errorName(int status) {
        return switch (status) {
            case 0 -> "lost sync";
            case 1 -> "bad frame header";
            case 2 -> "frame CRC mismatch";
            case 3 -> "unparseable stream";
            case 4 -> "bad metadata";
            case 5 -> "samples out of bounds";
            case 6 -> "missing frame";
            default -> "error status " + status;
        };
    }

    /// libFLAC's own words for the decoder's state.
    @SuppressWarnings("restricted")
    private String describe() {
        var text = flac.getResolvedStateString().call(decoder);
        return text.equals(MemorySegment.NULL)
                ? "state " + flac.getState().call(decoder)
                : text.reinterpret(256).getString(0);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("decoder closed");
        }
    }
}
