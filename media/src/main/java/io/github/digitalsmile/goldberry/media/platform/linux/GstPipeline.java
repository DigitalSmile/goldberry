package io.github.digitalsmile.goldberry.media.platform.linux;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.MemorySegment;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.github.digitalsmile.goldberry.media.codec.Frame;

/// One decoding pipeline, playing: packets pushed into its `appsrc`, decoded
/// buffers pulled from its `appsink`, errors read off its bus.
///
/// Built from a [GstCodec#pipeline] description. The pipeline decodes on
/// threads of its own; this class is used from one thread, the Engine's decode
/// thread, and never blocks longer than a pull's timeout.
final class GstPipeline implements AutoCloseable {

    /// What every time is moved by on its way into GStreamer, and back on its way
    /// out. A container starts AAC before zero to cover the encoder's priming
    /// (Matroska's first packet is at -21 ms), and a `GstClockTime` is unsigned,
    /// so a time before zero is not one GStreamer can hold. An hour later, every
    /// time a stream can start at is.
    static final long TIME_OFFSET_NANOS = TimeUnit.HOURS.toNanos(1);

    private final GStreamer gs;
    private final String decoder;
    private MemorySegment pipeline;
    private MemorySegment source = MemorySegment.NULL;
    private MemorySegment sink = MemorySegment.NULL;
    private MemorySegment bus = MemorySegment.NULL;

    private GstPipeline(GStreamer gs, String decoder, MemorySegment pipeline) {
        this.gs = gs;
        this.decoder = decoder;
        this.pipeline = pipeline;
    }

    /// Builds `description`, whose decoder element is `decoder`, and starts it.
    ///
    /// @throws GstException when it does not build, or will not start: the
    ///                      decoder cannot open on this system
    static GstPipeline start(GStreamer gs, String description, String decoder) {
        Objects.requireNonNull(gs, "gs");
        var started = new GstPipeline(gs, decoder, gs.gst().parseLaunch(description));
        try {
            started.source = gs.gst().byName(started.pipeline, "src");
            started.sink = gs.gst().byName(started.pipeline, "sink");
            started.bus = gs.gst().bus(started.pipeline);
            if (gs.gst().setState(started.pipeline, Gst.STATE_PLAYING) == Gst.STATE_CHANGE_FAILURE) {
                started.throwIfFailed();
                throw new GstException(decoder + " would not start");
            }
            return started;
        } catch (RuntimeException e) {
            started.close();
            throw e;
        }
    }

    /// The decoder element this pipeline decodes with.
    String decoder() {
        return decoder;
    }

    /// Copies `data` into a buffer, with its times and whether it can be decoded
    /// alone, and queues it.
    ///
    /// @param data          one packet's bytes
    /// @param ptsNanos      when it is presented, or [Frame#NO_PTS]
    /// @param durationNanos how long it plays, or 0 when unknown
    /// @param keyframe      whether decoding can start at it
    @SuppressWarnings("restricted")
    void push(MemorySegment data, long ptsNanos, long durationNanos, boolean keyframe) {
        var buffer = gs.gst().newBuffer(data.byteSize());
        try {
            gs.gst().fill(buffer, data);
            var fields = buffer.reinterpret(GstLayout.BUFFER);
            fields.set(JAVA_LONG, GstLayout.BUFFER_PTS, time(ptsNanos));
            fields.set(JAVA_LONG, GstLayout.BUFFER_DURATION, durationNanos > 0 ? durationNanos : Gst.CLOCK_TIME_NONE);
            if (!keyframe) {
                var flags = fields.get(JAVA_INT, GstLayout.MINI_OBJECT_FLAGS);
                fields.set(JAVA_INT, GstLayout.MINI_OBJECT_FLAGS, flags | GstLayout.BUFFER_FLAG_DELTA_UNIT);
            }
        } catch (RuntimeException e) {
            gs.gst().unrefMini(buffer);
            throw e;
        }
        // The push takes the buffer, whether or not it succeeds.
        gs.app().push(source, buffer);
    }

    /// Says no more packets are coming: the decoder gives up what it holds.
    void end() {
        gs.app().endOfStream(source);
    }

    /// Packets pushed that the pipeline has not taken yet.
    long queued() {
        return gs.app().queued(source);
    }

    /// The next decoded sample, waiting at most `timeoutNanos`, or a null
    /// pointer. The caller releases it.
    MemorySegment pull(long timeoutNanos) {
        return gs.app().pull(sink, timeoutNanos);
    }

    /// Whether every decoded sample has been pulled after [#end].
    boolean ended() {
        return gs.app().ended(sink);
    }

    /// Throws the first error an element of the pipeline posted.
    ///
    /// @throws GstException with the element's message
    void throwIfFailed() {
        if (bus.equals(MemorySegment.NULL)) {
            return;
        }
        var error = gs.gst().popError(bus);
        if (error.isPresent()) {
            throw new GstException("the " + decoder + " pipeline", error.get());
        }
    }

    /// Stops the pipeline and frees it. Idempotent.
    @Override
    public void close() {
        if (pipeline.equals(MemorySegment.NULL)) {
            return;
        }
        var gst = gs.gst();
        // Stopping is synchronous: no thread of the pipeline runs after this.
        gst.setState(pipeline, Gst.STATE_NULL);
        for (var object : new MemorySegment[] {bus, sink, source, pipeline}) {
            if (!object.equals(MemorySegment.NULL)) {
                gst.unref(object);
            }
        }
        bus = MemorySegment.NULL;
        sink = MemorySegment.NULL;
        source = MemorySegment.NULL;
        pipeline = MemorySegment.NULL;
    }

    /// A time in nanoseconds as GStreamer writes it, [#TIME_OFFSET_NANOS] later:
    /// [Gst#CLOCK_TIME_NONE] for none.
    static long time(long nanos) {
        return nanos == Frame.NO_PTS ? Gst.CLOCK_TIME_NONE : nanos + TIME_OFFSET_NANOS;
    }

    /// A buffer's time as the Engine writes it, [#TIME_OFFSET_NANOS] earlier:
    /// [Frame#NO_PTS] for none.
    static long nanos(long time) {
        return time == Gst.CLOCK_TIME_NONE ? Frame.NO_PTS : time - TIME_OFFSET_NANOS;
    }
}
