package io.github.digitalsmile.goldberry.media.platform.windows;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.media.codec.Frame;

/// One synchronous decoder MFT, driven the way `mftransform.h` documents:
/// types set, streaming begun, then input and output in turn.
///
/// **Found** by `MFTEnumEx` in the decoder category for the input subtype, best
/// first; the first whose `SetInputType` takes the input type is kept, and every
/// activation object is released.
///
/// **Output** goes into a sample this class allocates and reuses when the MFT
/// neither provides its own nor can, as the Microsoft software decoders do; or
/// into the MFT's own sample, which the caller releases.
///
/// Every method is called with the thread in COM ([MediaFoundation#enterThread]),
/// which the decoders see to.
final class Transform implements AutoCloseable {

    private static final Logger LOG = Logs.of(Transform.class);

    /// What one `ProcessOutput` gave.
    sealed interface Output {

        /// A sample of output. `own` when it is this class's reused sample, which
        /// the caller must not release; the MFT's own otherwise, which it must.
        record Produced(MemorySegment sample, boolean own) implements Output {}

        /// Nothing until more input: `MF_E_TRANSFORM_NEED_MORE_INPUT`.
        record NeedsInput() implements Output {}

        /// The output type changed and has to be chosen again:
        /// `MF_E_TRANSFORM_STREAM_CHANGE`, or `MF_E_TRANSFORM_TYPE_NOT_SET`.
        record TypeChanged() implements Output {}

        /// Any other failure.
        record Failed(int hresult) implements Output {}
    }

    private final MediaFoundation mf;
    private final String description;
    /// Shared: the decoder is opened on one thread and used on another.
    private final Arena arena = Arena.ofShared();

    private final MemorySegment outputBuffer;
    private final MemorySegment outputStatus;

    private MemorySegment transform;
    private MfTransform.StreamInfo info = new MfTransform.StreamInfo(0, 0, 0);
    private MemorySegment ownSample = MemorySegment.NULL;
    private long ownSampleBytes;

    private Transform(MediaFoundation mf, MemorySegment transform, String description) {
        this.mf = mf;
        this.transform = transform;
        this.description = description;
        this.outputBuffer = arena.allocate(MfTransform.OUTPUT_DATA_BUFFER);
        this.outputStatus = arena.allocate(JAVA_INT);
    }

    /// The best decoder in `category` that takes `inputType`, whose major type
    /// and subtype are `major` and `subtype`, with the input type set.
    ///
    /// @throws IllegalStateException when the system has none that takes it
    static Transform open(
            MediaFoundation mf, Guid category, Guid major, Guid subtype, MemorySegment inputType, String description) {
        var activates = mf.mfplat().enumerate(category, major, subtype);
        var refusals = new ArrayList<String>();
        var found = MemorySegment.NULL;
        try {
            for (var activate : activates) {
                var candidate = MemorySegment.NULL;
                try {
                    candidate = MfTransform.activate(activate);
                    var hr = MfTransform.setInputType(candidate, inputType);
                    if (!HResult.failed(hr)) {
                        found = candidate;
                        candidate = MemorySegment.NULL;
                        break;
                    }
                    refusals.add(HResult.describe(hr));
                } catch (HResult.Failure e) {
                    refusals.add(e.getMessage());
                } finally {
                    Com.release(candidate);
                }
            }
        } finally {
            for (var activate : activates) {
                Com.release(activate);
            }
        }
        if (found.equals(MemorySegment.NULL)) {
            throw new IllegalStateException(
                    activates.isEmpty()
                            ? "Windows has no Media Foundation decoder for " + description
                            : "none of the " + activates.size() + " Media Foundation decoders for " + description
                                    + " took the stream: " + refusals);
        }
        LOG.debug(
                "Media Foundation found {} decoders for {}; using the first that took it",
                activates.size(),
                description);
        return new Transform(mf, found, description);
    }

    /// The first output type the MFT offers that `wanted` accepts, with a
    /// reference the caller releases.
    Optional<MemorySegment> findOutputType(Predicate<MemorySegment> wanted) {
        for (var index = 0; ; index++) {
            var type = MfTransform.outputAvailableType(transform, index);
            if (type.equals(MemorySegment.NULL)) {
                return Optional.empty();
            }
            var accepted = false;
            try {
                accepted = wanted.test(type);
            } finally {
                if (!accepted) {
                    Com.release(type);
                }
            }
            if (accepted) {
                return Optional.of(type);
            }
        }
    }

    /// Sets the output type, and reads what it asks of the output samples.
    void setOutputType(MemorySegment type) {
        MfTransform.setOutputType(transform, type);
        info = MfTransform.outputStreamInfo(transform);
    }

    /// The output type set, with a reference the caller releases.
    MemorySegment outputType() {
        return MfTransform.outputCurrentType(transform);
    }

    /// Tells the MFT streaming begins: after the types are set, before the first
    /// input.
    void begin() {
        message(MfTransform.MESSAGE_NOTIFY_BEGIN_STREAMING);
        message(MfTransform.MESSAGE_NOTIFY_START_OF_STREAM);
    }

    /// Sends the MFT `message`.
    void message(int message) {
        MfTransform.processMessage(transform, message);
    }

    /// A new input sample of up to `capacity` bytes, which `write` fills and
    /// answers the length of, timed at `ptsNanos`. The caller releases it.
    MemorySegment inputSample(long capacity, ToLongFunction<MemorySegment> write, long ptsNanos) {
        if (capacity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("a packet of " + capacity + " bytes");
        }
        var buffer = mf.mfplat().createMemoryBuffer((int) Math.max(capacity, 1));
        var sample = MemorySegment.NULL;
        try {
            var locked = MfBuffer.lock(buffer);
            long written;
            try {
                written = write.applyAsLong(locked.data().asSlice(0, capacity));
            } finally {
                MfBuffer.unlock(buffer);
            }
            MfBuffer.setCurrentLength(buffer, (int) written);
            sample = mf.mfplat().createSample();
            MfSample.addBuffer(sample, buffer);
            if (ptsNanos != Frame.NO_PTS) {
                MfSample.setTime(sample, toHundredNanos(ptsNanos));
            }
            var made = sample;
            sample = MemorySegment.NULL;
            return made;
        } finally {
            Com.release(sample);
            // The sample holds its own reference to the buffer.
            Com.release(buffer);
        }
    }

    /// Hands the MFT `sample`: what it answers.
    int input(MemorySegment sample) {
        return MfTransform.processInput(transform, sample);
    }

    /// Asks for one sample of output. When the MFT needs one allocated for it,
    /// the sample is this class's, with a buffer of the size the MFT asks for or
    /// `minimumBytes`, whichever is more.
    Output output(long minimumBytes) {
        outputBuffer.fill((byte) 0);
        outputBuffer.set(ADDRESS, MfTransform.OUTPUT_SAMPLE, MemorySegment.NULL);
        if (info.callerAllocates()) {
            outputBuffer.set(ADDRESS, MfTransform.OUTPUT_SAMPLE, ownSample(Math.max(info.size(), minimumBytes)));
        }
        var hr = MfTransform.processOutput(transform, outputBuffer, outputStatus);
        Com.release(outputBuffer.get(ADDRESS, MfTransform.OUTPUT_EVENTS));
        var sample = outputBuffer.get(ADDRESS, MfTransform.OUTPUT_SAMPLE);
        var own = !ownSample.equals(MemorySegment.NULL) && sample.equals(ownSample);
        if (HResult.failed(hr)) {
            if (!own) {
                Com.release(sample);
            }
            return switch (hr) {
                case HResult.MF_E_TRANSFORM_NEED_MORE_INPUT -> new Output.NeedsInput();
                case HResult.MF_E_TRANSFORM_STREAM_CHANGE, HResult.MF_E_TRANSFORM_TYPE_NOT_SET ->
                    new Output.TypeChanged();
                default -> new Output.Failed(hr);
            };
        }
        if (sample.equals(MemorySegment.NULL)) {
            return new Output.Failed(HResult.E_FAIL);
        }
        return new Output.Produced(sample, own);
    }

    /// What the decoder is, for messages.
    String description() {
        return description;
    }

    @Override
    public void close() {
        Com.release(ownSample);
        ownSample = MemorySegment.NULL;
        Com.release(transform);
        transform = MemorySegment.NULL;
        if (arena.scope().isAlive()) {
            arena.close();
        }
    }

    /// The reused output sample, with a buffer of at least `bytes`: made on
    /// first use, and again when the MFT asks for more.
    private MemorySegment ownSample(long bytes) {
        if (!ownSample.equals(MemorySegment.NULL) && ownSampleBytes >= bytes) {
            return ownSample;
        }
        if (bytes <= 0 || bytes > Integer.MAX_VALUE) {
            throw new IllegalStateException(description + " asked for output buffers of " + bytes + " bytes");
        }
        Com.release(ownSample);
        ownSample = MemorySegment.NULL;
        var buffer = mf.mfplat().createAlignedMemoryBuffer((int) bytes, info.alignment());
        try {
            var sample = mf.mfplat().createSample();
            try {
                MfSample.addBuffer(sample, buffer);
            } catch (RuntimeException e) {
                Com.release(sample);
                throw e;
            }
            ownSample = sample;
            ownSampleBytes = bytes;
        } finally {
            Com.release(buffer);
        }
        return ownSample;
    }

    /// `nanos` in Media Foundation's 100-nanosecond units, rounded down.
    static long toHundredNanos(long nanos) {
        return Math.floorDiv(nanos, 100L);
    }

    /// Media Foundation's 100-nanosecond units in nanoseconds.
    static long fromHundredNanos(long hundredNanos) {
        return Math.multiplyExact(hundredNanos, 100L);
    }

    @Override
    public String toString() {
        return "Transform[" + description + "]";
    }
}
