package dev.goldberry.media.platform.linux;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.Received;

/// One track decoded by a GStreamer pipeline (`GstCodec`): the Decoder SPI's
/// send and receive over a pipeline that decodes on threads of its own.
///
/// **In:** each packet is copied into a buffer and queued on `appsrc`. At most
/// [#MAX_QUEUED] wait there; past that, [#send] refuses and [#receive] waits for
/// the pipeline to take them, which it does as the decoder works through them.
///
/// **Out:** [#receive] takes whatever the pipeline has decoded by then, and
/// answers "needs input" otherwise, so decoding runs ahead of the Engine
/// without it waiting on each packet. Pictures leave GStreamer's decoders in
/// presentation order, with their times: nothing is reordered here.
///
/// **Lent, not copied:** the frame handed out is the decoded buffer itself,
/// mapped for reading, until the decoder's next call.
///
/// **Choosing the decoder:** the candidates come ranked, as `decodebin` ranks
/// them, and the first that starts is used. A hardware decoder can start and
/// still not decode the stream: a device without the profile, or a picture
/// smaller than it decodes. So until the first frame comes out, every packet
/// is also kept, and an error then moves on to the next candidate, which is
/// given the same packets again. After the first frame, the decoder is the one.
///
/// **Failures:** an error an element posts once decoding has begun is thrown on
/// the next call, and the Engine walks its fallback ladder. GStreamer's
/// decoders already drop a packet they cannot decode, and post an error only
/// after a run of them. A flush tears the pipeline down and builds it again,
/// which costs milliseconds and is what a seek is.
abstract sealed class GstDecoder implements Decoder permits GstVideoDecoder, GstAudioDecoder {

    private static final Logger LOG = Logs.of(GstDecoder.class);

    /// Packets queued on `appsrc` before [#send] refuses another.
    static final int MAX_QUEUED = 8;

    /// How long one wait for a decoded buffer lasts before the pipeline's bus is
    /// looked at again.
    static final long POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(10);

    /// How long the pipeline may take nothing and give nothing before it is taken
    /// to be stuck.
    static final long STALL_NANOS = TimeUnit.SECONDS.toNanos(10);

    /// A packet kept until the first frame, to give to the next candidate.
    private record Kept(MemorySegment data, long ptsNanos, long durationNanos, boolean keyframe) {}

    protected final GStreamer gs;
    protected final String codecName;
    private final GstCodec codec;
    private final DecoderRequest request;
    private final List<Gst.Candidate> candidates;
    /// Shared: the decoder is opened on one thread and used on another.
    protected final Arena arena = Arena.ofShared();

    private final MemorySegment map;
    private GstPipeline pipeline;
    private int candidate;
    /// What was sent since the pipeline started, while no frame has come out;
    /// null once one has.
    private @Nullable List<Kept> kept = new ArrayList<>();
    private @Nullable Arena keptArena = Arena.ofShared();
    private MemorySegment lentSample = MemorySegment.NULL;
    private MemorySegment lentBuffer = MemorySegment.NULL;
    private boolean ending;

    /// Opens a pipeline for `request` with the first of `candidates` that starts.
    ///
    /// @throws GstException when none of them starts
    GstDecoder(GStreamer gs, GstCodec codec, DecoderRequest request, List<Gst.Candidate> candidates) {
        this.gs = Objects.requireNonNull(gs, "gs");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.codecName = request.codecName();
        this.candidates = List.copyOf(candidates);
        // The request's extradata is valid only while the provider opens: a copy is
        // kept, for the pipelines a flush or a fallback builds later.
        var extradata = request.extradata();
        this.request = new DecoderRequest(
                request.codec(),
                request.codecName(),
                request.params(),
                arena.allocate(Math.max(extradata.byteSize(), 1))
                        .asSlice(0, extradata.byteSize())
                        .copyFrom(extradata),
                request.timeBase());
        this.map = arena.allocate(GstLayout.MAP_INFO);
        try {
            this.pipeline = startFrom(0, null);
        } catch (RuntimeException e) {
            closeKept();
            arena.close();
            throw e;
        }
    }

    /// The decoder element the pipeline decodes with: `avdec_h264`, `faad`.
    String decoder() {
        return pipeline.decoder();
    }

    @Override
    public boolean send(Packet packet) {
        check();
        if (pipeline.queued() >= MAX_QUEUED) {
            return false;
        }
        var data = packet.data();
        if (data.byteSize() == 0) {
            return true;
        }
        var durationNanos = packet.duration() > 0 ? packet.timeBase().toNanos(packet.duration()) : 0;
        var keeping = kept;
        var keepingArena = keptArena;
        if (keeping != null && keepingArena != null) {
            keeping.add(new Kept(
                    keepingArena.allocate(data.byteSize()).copyFrom(data),
                    packet.ptsNanos(),
                    durationNanos,
                    packet.keyframe()));
        }
        pipeline.push(data, packet.ptsNanos(), durationNanos, packet.keyframe());
        return true;
    }

    @Override
    public void sendEnd() {
        check();
        pipeline.end();
        ending = true;
    }

    @Override
    public Received receive() {
        releaseLent();
        check();
        var sample = pipeline.pull(0);
        if (sample.equals(MemorySegment.NULL) && ending) {
            sample = awaitEnd();
            if (sample.equals(MemorySegment.NULL)) {
                return Received.ENDED;
            }
        } else if (sample.equals(MemorySegment.NULL) && pipeline.queued() >= MAX_QUEUED) {
            sample = awaitRoom();
        }
        return sample.equals(MemorySegment.NULL) ? Received.NEEDS_INPUT : new Received.Decoded(lend(sample));
    }

    @Override
    public void flush() {
        releaseLent();
        var element = pipeline.decoder();
        pipeline.close();
        ending = false;
        flushed();
        pipeline = GstPipeline.start(gs, codec.pipeline(request, element), element);
    }

    /// What a subclass forgets when the decoder is flushed. Nothing, by default.
    void flushed() {
        // Nothing kept between frames.
    }

    @Override
    public void close() {
        releaseLent();
        pipeline.close();
        closeKept();
        if (arena.scope().isAlive()) {
            arena.close();
        }
    }

    /// Describes the decoded `buffer`, of `sample`, mapped at `data`, as a frame.
    /// The mapping lasts until the decoder's next call.
    abstract Frame describe(MemorySegment sample, MemorySegment buffer, MemorySegment data);

    /// The time on `buffer`, in nanoseconds, or [Frame#NO_PTS].
    @SuppressWarnings("restricted")
    static long ptsNanos(MemorySegment buffer) {
        return GstPipeline.nanos(buffer.reinterpret(GstLayout.BUFFER).get(JAVA_LONG, GstLayout.BUFFER_PTS));
    }

    /// The first candidate from `from` on that starts. `failure` is why the one
    /// before it was given up, for the message when none is left.
    ///
    /// @throws GstException when none of them starts
    private GstPipeline startFrom(int from, @Nullable GstException failure) {
        var last = failure;
        for (var i = from; i < candidates.size(); i++) {
            var name = candidates.get(i).name();
            try {
                var started = GstPipeline.start(gs, codec.pipeline(request, name), name);
                candidate = i;
                LOG.debug("GStreamer decodes {} with {}", codecName, name);
                return started;
            } catch (GstException e) {
                LOG.debug("GStreamer's {} does not start for {}: {}", name, codecName, e.getMessage());
                last = e;
            }
        }
        throw last != null
                ? new GstException("no GStreamer decoder for " + codecName + " works; the last: " + last.getMessage())
                : new GstException("GStreamer has no decoder for " + codecName);
    }

    /// Throws what the pipeline posted, unless no frame has come out yet and
    /// another candidate is left: that one then takes over, with every packet
    /// sent so far.
    private void check() {
        try {
            pipeline.throwIfFailed();
        } catch (GstException e) {
            var keeping = kept;
            if (keeping == null || candidate + 1 >= candidates.size()) {
                throw e;
            }
            LOG.debug(
                    "GStreamer's {} failed on {} before its first frame; trying the next: {}",
                    pipeline.decoder(),
                    codecName,
                    e.getMessage());
            pipeline.close();
            pipeline = startFrom(candidate + 1, e);
            for (var packet : keeping) {
                pipeline.push(packet.data(), packet.ptsNanos(), packet.durationNanos(), packet.keyframe());
            }
            if (ending) {
                pipeline.end();
            }
        }
    }

    /// Waits for the pipeline to give up what it holds after the end: a sample,
    /// or a null pointer once it has none left.
    private MemorySegment awaitEnd() {
        var deadline = System.nanoTime() + STALL_NANOS;
        while (true) {
            var sample = pipeline.pull(POLL_NANOS);
            if (!sample.equals(MemorySegment.NULL) || pipeline.ended()) {
                return sample;
            }
            check();
            stalledPast(deadline, "draining");
        }
    }

    /// Waits while `appsrc` is full: for a sample, or for the pipeline to take a
    /// packet so that [#send] has room again.
    private MemorySegment awaitRoom() {
        var deadline = System.nanoTime() + STALL_NANOS;
        while (true) {
            var sample = pipeline.pull(POLL_NANOS);
            if (!sample.equals(MemorySegment.NULL) || pipeline.queued() < MAX_QUEUED) {
                return sample;
            }
            check();
            stalledPast(deadline, "taking packets");
        }
    }

    private void stalledPast(long deadline, String what) {
        if (System.nanoTime() > deadline) {
            throw new GstException("GStreamer's " + pipeline.decoder() + " stopped " + what + " for "
                    + TimeUnit.NANOSECONDS.toSeconds(STALL_NANOS) + " s on " + codecName);
        }
    }

    /// Maps `sample`'s buffer and describes it. The sample is lent from here until
    /// [#releaseLent], whether or not describing it succeeds. The first frame
    /// settles the decoder: the packets kept for another candidate go.
    @SuppressWarnings("restricted")
    private Frame lend(MemorySegment sample) {
        lentSample = sample;
        closeKept();
        var buffer = gs.gst().sampleBuffer(sample);
        if (buffer.equals(MemorySegment.NULL)) {
            throw new GstException("GStreamer's " + pipeline.decoder() + " gave a sample with no buffer");
        }
        gs.gst().mapRead(buffer, map);
        lentBuffer = buffer;
        var data = map.get(ADDRESS, GstLayout.MAP_DATA).reinterpret(map.get(JAVA_LONG, GstLayout.MAP_SIZE));
        return describe(sample, buffer, data);
    }

    private void releaseLent() {
        if (!lentBuffer.equals(MemorySegment.NULL)) {
            gs.gst().unmap(lentBuffer, map);
            lentBuffer = MemorySegment.NULL;
        }
        if (!lentSample.equals(MemorySegment.NULL)) {
            gs.gst().unrefMini(lentSample);
            lentSample = MemorySegment.NULL;
        }
    }

    private void closeKept() {
        kept = null;
        var keepingArena = keptArena;
        keptArena = null;
        if (keepingArena != null) {
            keepingArena.close();
        }
    }
}
