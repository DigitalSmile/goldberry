package dev.goldberry.media.platform.windows;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.media.codec.AudioFrame;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.SampleFormat;
import dev.goldberry.media.codec.TrackParams;

/// One AAC, AC-3 or E-AC-3 track decoded by a Media Foundation decoder MFT.
///
/// **Configuration:** AAC's input type carries the container's
/// `AudioSpecificConfig` behind the `HEAACWAVEINFO` fields
/// ([AudioFormats#aacUserData]), as raw access units (payload type 0). AC-3 and
/// E-AC-3 need nothing but the rate and channel count; every frame carries its
/// own header.
///
/// **Decoding:** one packet per [#send], copied into an input sample; the
/// output is interleaved 32-bit float, the float type the MFT offers with the
/// stream's channel count, or one made here when it offers none. Windows orders
/// a buffer's channels by their speaker bit, which is FFmpeg's order, so nothing
/// is remapped ([AudioFormats#channelMask]). [#sendEnd] drains the MFT.
///
/// **Timing:** by sample count from the first packet, re-anchored at a gap
/// ([SampleClock]), as the macOS decoder times its output.
final class MediaFoundationAudioDecoder implements Decoder {

    private static final Logger LOG = Logs.of(MediaFoundationAudioDecoder.class);

    /// The frames an output buffer holds at least, for an MFT that does not say
    /// what it needs: more than a packet of any of the three codecs decodes to
    /// (AAC 1024, HE-AAC 2048, AC-3 and E-AC-3 1536 at most).
    static final int CHUNK_FRAMES = 4096;

    /// Packets in a row the decoder may reject before it is given up on.
    static final int MAX_CONSECUTIVE_FAILURES = 30;

    /// Output type changes in a row without output before the decoder is taken
    /// to be going round in circles.
    private static final int MAX_TYPE_CHANGES = 4;

    private final MediaFoundation mf;
    private final String codecName;
    private final int inputRate;
    private final int inputChannels;
    private final Transform transform;

    private int sampleRate;
    private int channels;
    private SampleClock clock;

    private MemorySegment lentSample = MemorySegment.NULL;
    private MemorySegment lentBuffer = MemorySegment.NULL;
    private boolean lentLocked;
    /// Whether the MFT has asked for input since the last packet: nothing
    /// decoded is waiting to be timed by the clock's anchor.
    private boolean drained = true;
    private boolean ending;
    private boolean ended;
    private int consecutiveFailures;

    /// Opens a decoder for `request`, an AAC, AC-3 or E-AC-3 audio track.
    ///
    /// @throws IllegalStateException when the system has no decoder that takes
    ///                               the stream
    MediaFoundationAudioDecoder(MediaFoundation mf, DecoderRequest request) {
        this.mf = Objects.requireNonNull(mf, "mf");
        this.codecName = request.codecName();
        if (!(request.params() instanceof TrackParams.Audio audio)) {
            throw new IllegalArgumentException(request.codecName() + " is not an audio track");
        }
        var subtype = MediaFoundationAudioProvider.subtype(request.codec())
                .orElseThrow(() -> new IllegalArgumentException(codecName + " is not AAC, AC-3 or E-AC-3"));
        byte[] userData = null;
        var rate = audio.sampleRate();
        var count = audio.channels();
        if (request.codec() == CodecId.AAC) {
            var config = request.extradata().toArray(JAVA_BYTE);
            userData = AudioFormats.aacUserData(config);
            var fromConfig = AudioFormats.aacConfig(config);
            rate = rate > 0 ? rate : fromConfig.sampleRate();
            count = count > 0 ? count : fromConfig.channels();
        }
        if (rate <= 0 || count <= 0 || count > 8) {
            throw new IllegalArgumentException(codecName + " at " + rate + " Hz with " + count + " channels");
        }
        this.inputRate = rate;
        this.inputChannels = count;

        mf.enterThread();
        var inputType = mf.mfplat().createMediaType();
        try {
            MfAttributes.setGuid(inputType, MfGuids.MF_MT_MAJOR_TYPE, MfGuids.MFMediaType_Audio);
            MfAttributes.setGuid(inputType, MfGuids.MF_MT_SUBTYPE, subtype);
            MfAttributes.setUint32(inputType, MfGuids.MF_MT_AUDIO_SAMPLES_PER_SECOND, inputRate);
            MfAttributes.setUint32(inputType, MfGuids.MF_MT_AUDIO_NUM_CHANNELS, inputChannels);
            if (userData != null) {
                MfAttributes.setUint32(inputType, MfGuids.MF_MT_AAC_PAYLOAD_TYPE, 0);
                MfAttributes.setUint32(
                        inputType,
                        MfGuids.MF_MT_AAC_AUDIO_PROFILE_LEVEL_INDICATION,
                        AudioFormats.PROFILE_LEVEL_UNSPECIFIED);
                MfAttributes.setBlob(inputType, MfGuids.MF_MT_USER_DATA, userData);
            }
            this.transform = Transform.open(
                    mf,
                    MfGuids.MFT_CATEGORY_AUDIO_DECODER,
                    MfGuids.MFMediaType_Audio,
                    subtype,
                    inputType,
                    switch (request.codec()) {
                        case AAC -> "AAC";
                        case AC3 -> "AC-3";
                        default -> "E-AC-3";
                    });
        } finally {
            Com.release(inputType);
        }
        try {
            chooseOutputType();
            transform.begin();
        } catch (RuntimeException e) {
            transform.close();
            throw e;
        }
        this.clock = new SampleClock(sampleRate);
        LOG.debug("Media Foundation opened {} at {} Hz, {} channels", codecName, sampleRate, channels);
    }

    /// The rate the MFT decodes at: HE-AAC's full rate, which a container may
    /// report as its core's.
    int sampleRate() {
        return sampleRate;
    }

    /// The channels each frame has.
    int channels() {
        return channels;
    }

    @Override
    public boolean send(Packet packet) {
        mf.enterThread();
        releaseLent();
        var data = packet.data();
        var sample = transform.inputSample(
                data.byteSize(),
                target -> {
                    MemorySegment.copy(data, 0, target, 0, data.byteSize());
                    return data.byteSize();
                },
                packet.ptsNanos());
        int hr;
        try {
            hr = transform.input(sample);
        } finally {
            Com.release(sample);
        }
        if (hr == HResult.MF_E_NOTACCEPTING) {
            return false;
        }
        if (HResult.failed(hr)) {
            rejected(hr);
            return true;
        }
        consecutiveFailures = 0;
        if (clock.packet(packet.ptsNanos(), drained)) {
            LOG.debug("{} jumps; timing from the packet", codecName);
        }
        drained = false;
        return true;
    }

    @Override
    public void sendEnd() {
        mf.enterThread();
        releaseLent();
        if (!ending) {
            transform.message(MfTransform.MESSAGE_NOTIFY_END_OF_STREAM);
            transform.message(MfTransform.MESSAGE_COMMAND_DRAIN);
            ending = true;
        }
    }

    @Override
    public Received receive() {
        mf.enterThread();
        releaseLent();
        if (ended) {
            return Received.ENDED;
        }
        var frameBytes = JAVA_FLOAT.byteSize() * channels;
        for (var changes = 0; ; ) {
            switch (transform.output(CHUNK_FRAMES * frameBytes)) {
                case Transform.Output.Produced(var sample, var own) -> {
                    var frame = lend(sample, own);
                    if (frame.isPresent()) {
                        return new Received.Decoded(frame.get());
                    }
                    releaseLent();
                }
                case Transform.Output.NeedsInput _ -> {
                    drained = true;
                    if (ending) {
                        ended = true;
                        return Received.ENDED;
                    }
                    return Received.NEEDS_INPUT;
                }
                case Transform.Output.TypeChanged _ -> {
                    if (++changes > MAX_TYPE_CHANGES) {
                        throw new IllegalStateException("Media Foundation changed the output type of " + codecName + " "
                                + changes + " times without output");
                    }
                    chooseOutputType();
                    clock.rate(sampleRate);
                }
                case Transform.Output.Failed(var hr) -> {
                    if (ending) {
                        throw new HResult.Failure("IMFTransform::ProcessOutput draining " + codecName, hr);
                    }
                    rejected(hr);
                    return Received.NEEDS_INPUT;
                }
            }
        }
    }

    @Override
    public void flush() {
        mf.enterThread();
        releaseLent();
        transform.message(MfTransform.MESSAGE_COMMAND_FLUSH);
        clock.reset();
        drained = true;
        ending = false;
        ended = false;
        consecutiveFailures = 0;
    }

    @Override
    public void close() {
        mf.enterThread();
        try {
            releaseLent();
        } finally {
            transform.close();
        }
    }

    /// Picks interleaved float from the types the MFT offers, with the stream's
    /// channel count where one has it, and makes one where none is float; sets
    /// it, and reads the rate and channels it gives.
    private void chooseOutputType() {
        var type = transform
                .findOutputType(offered -> isFloat(offered)
                        && MfAttributes.getUint32(offered, MfGuids.MF_MT_AUDIO_NUM_CHANNELS)
                                        .orElse(0)
                                == inputChannels)
                .or(() -> transform.findOutputType(MediaFoundationAudioDecoder::isFloat))
                .orElseGet(this::floatType);
        try {
            transform.setOutputType(type);
        } finally {
            Com.release(type);
        }
        var current = transform.outputType();
        try {
            var rate = MfAttributes.getUint32(current, MfGuids.MF_MT_AUDIO_SAMPLES_PER_SECOND)
                    .orElse(inputRate);
            var count = MfAttributes.getUint32(current, MfGuids.MF_MT_AUDIO_NUM_CHANNELS)
                    .orElse(inputChannels);
            var bits = MfAttributes.getUint32(current, MfGuids.MF_MT_AUDIO_BITS_PER_SAMPLE)
                    .orElse(32);
            if (rate <= 0 || count <= 0 || count > 8 || bits != 32) {
                throw new IllegalStateException("Media Foundation decodes " + codecName + " to " + bits
                        + "-bit float at " + rate + " Hz with " + count + " channels");
            }
            sampleRate = rate;
            channels = count;
        } finally {
            Com.release(current);
        }
    }

    /// An interleaved float type of the stream's rate and channels, for an MFT
    /// that offers none.
    private MemorySegment floatType() {
        var type = mf.mfplat().createMediaType();
        try {
            var blockAlign = (int) JAVA_FLOAT.byteSize() * inputChannels;
            MfAttributes.setGuid(type, MfGuids.MF_MT_MAJOR_TYPE, MfGuids.MFMediaType_Audio);
            MfAttributes.setGuid(type, MfGuids.MF_MT_SUBTYPE, MfGuids.MFAudioFormat_Float);
            MfAttributes.setUint32(type, MfGuids.MF_MT_AUDIO_SAMPLES_PER_SECOND, inputRate);
            MfAttributes.setUint32(type, MfGuids.MF_MT_AUDIO_NUM_CHANNELS, inputChannels);
            MfAttributes.setUint32(type, MfGuids.MF_MT_AUDIO_BITS_PER_SAMPLE, 32);
            MfAttributes.setUint32(type, MfGuids.MF_MT_AUDIO_BLOCK_ALIGNMENT, blockAlign);
            MfAttributes.setUint32(type, MfGuids.MF_MT_AUDIO_AVG_BYTES_PER_SECOND, blockAlign * inputRate);
            var mask = AudioFormats.channelMask(inputChannels);
            if (mask != 0) {
                MfAttributes.setUint32(type, MfGuids.MF_MT_AUDIO_CHANNEL_MASK, mask);
            }
            LOG.debug("Media Foundation offers no float output for {}; asking for one", codecName);
            return type;
        } catch (RuntimeException e) {
            Com.release(type);
            throw e;
        }
    }

    /// Locks the output sample's buffer and describes its samples, or empty when
    /// it holds none. The sample is lent from here until [#releaseLent], either
    /// way.
    private Optional<AudioFrame> lend(MemorySegment sample, boolean own) {
        lentSample = own ? MemorySegment.NULL : sample;
        lentBuffer = MfSample.contiguousBuffer(sample);
        var locked = MfBuffer.lock(lentBuffer);
        lentLocked = true;
        var frameBytes = (int) JAVA_FLOAT.byteSize() * channels;
        var frames = locked.currentLength() / frameBytes;
        if (frames <= 0) {
            return Optional.empty();
        }
        var plane = locked.data().asSlice(0, SampleFormat.F32.planeSize(channels, frames));
        return Optional.of(
                new AudioFrame(SampleFormat.F32, sampleRate, channels, frames, List.of(plane), clock.advance(frames)));
    }

    private void releaseLent() {
        try {
            if (lentLocked) {
                lentLocked = false;
                MfBuffer.unlock(lentBuffer);
            }
        } finally {
            Com.release(lentBuffer);
            Com.release(lentSample);
            lentBuffer = MemorySegment.NULL;
            lentSample = MemorySegment.NULL;
        }
    }

    /// Counts a packet the decoder could not take, and gives up on a run of
    /// them.
    private void rejected(int hr) {
        if (++consecutiveFailures > MAX_CONSECUTIVE_FAILURES) {
            throw new HResult.Failure(
                    "Media Foundation rejected " + consecutiveFailures + " packets of " + codecName
                            + " in a row; the last",
                    hr);
        }
        LOG.debug("Media Foundation dropped a packet of {}: {}", codecName, HResult.describe(hr));
    }

    private static boolean isFloat(MemorySegment type) {
        return MfAttributes.getGuid(type, MfGuids.MF_MT_SUBTYPE)
                .filter(MfGuids.MFAudioFormat_Float::equals)
                .isPresent();
    }
}
