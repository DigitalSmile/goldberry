package dev.goldberry.media.platform.linux;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// `libgstapp-1.0`: `appsrc`, where the packets go in, and `appsink`, where the
/// decoded buffers come out.
///
/// Both ends are driven from the Engine's decode thread, with no callback: the
/// thread pushes a packet, and pulls whatever the pipeline's own threads have
/// decoded by then.
final class GstApp {

    /// `GST_FLOW_OK`.
    static final int FLOW_OK = 0;

    /// `GstFlowReturn gst_app_src_push_buffer(GstAppSrc *appsrc, GstBuffer *buffer)`
    private static final MethodHandle FD_gst_app_src_push_buffer =
            GstLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    /// `GstFlowReturn gst_app_src_end_of_stream(GstAppSrc *appsrc)`, and
    /// `gboolean gst_app_sink_is_eos(GstAppSink *appsink)`
    private static final MethodHandle FD_int_pointer = GstLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /// `guint64 gst_app_src_get_current_level_buffers(GstAppSrc *appsrc)`, since 1.20
    private static final MethodHandle FD_gst_app_src_get_current_level_buffers =
            GstLibrary.link(FunctionDescriptor.of(JAVA_LONG, ADDRESS));

    /// `GstSample *gst_app_sink_try_pull_sample(GstAppSink *appsink, GstClockTime timeout)`
    private static final MethodHandle FD_gst_app_sink_try_pull_sample =
            GstLibrary.link(FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG));

    private final MemorySegment pushBuffer;
    private final MemorySegment endOfStream;
    private final MemorySegment currentLevelBuffers;
    private final MemorySegment tryPullSample;
    private final MemorySegment isEos;

    GstApp(SymbolLookup lookup) {
        var l = GstLibrary.GST_APP;
        this.pushBuffer = l.symbol(lookup, "gst_app_src_push_buffer");
        this.endOfStream = l.symbol(lookup, "gst_app_src_end_of_stream");
        this.currentLevelBuffers = l.symbol(lookup, "gst_app_src_get_current_level_buffers");
        this.tryPullSample = l.symbol(lookup, "gst_app_sink_try_pull_sample");
        this.isEos = l.symbol(lookup, "gst_app_sink_is_eos");
    }

    /// Queues `buffer` on `appsrc`, which takes the caller's reference.
    ///
    /// @throws GstException when the source is no longer accepting buffers
    void push(MemorySegment appsrc, MemorySegment buffer) {
        int flow;
        try {
            flow = (int) FD_gst_app_src_push_buffer.invokeExact(pushBuffer, appsrc, buffer);
        } catch (Throwable t) {
            throw GstLibrary.failure("gst_app_src_push_buffer", t);
        }
        if (flow != FLOW_OK) {
            throw new GstException("appsrc refused a packet: GstFlowReturn " + flow);
        }
    }

    /// Tells `appsrc` that no more buffers are coming.
    void endOfStream(MemorySegment appsrc) {
        try {
            var flow = (int) FD_int_pointer.invokeExact(endOfStream, appsrc);
            if (flow != FLOW_OK) {
                throw new GstException("appsrc refused the end of the stream: GstFlowReturn " + flow);
            }
        } catch (Throwable t) {
            throw t instanceof RuntimeException e ? e : GstLibrary.failure("gst_app_src_end_of_stream", t);
        }
    }

    /// The buffers queued on `appsrc` that the pipeline has not taken yet.
    long queued(MemorySegment appsrc) {
        try {
            return (long) FD_gst_app_src_get_current_level_buffers.invokeExact(currentLevelBuffers, appsrc);
        } catch (Throwable t) {
            throw GstLibrary.failure("gst_app_src_get_current_level_buffers", t);
        }
    }

    /// The next decoded sample on `appsink`, waiting at most `timeoutNanos`; a
    /// null pointer when there is none by then, or at the end of the stream. The
    /// caller releases the sample.
    MemorySegment pull(MemorySegment appsink, long timeoutNanos) {
        try {
            return (MemorySegment) FD_gst_app_sink_try_pull_sample.invokeExact(tryPullSample, appsink, timeoutNanos);
        } catch (Throwable t) {
            throw GstLibrary.failure("gst_app_sink_try_pull_sample", t);
        }
    }

    /// Whether `appsink` has had the end of the stream and holds no more samples.
    boolean ended(MemorySegment appsink) {
        try {
            return (int) FD_int_pointer.invokeExact(isEos, appsink) != 0;
        } catch (Throwable t) {
            throw GstLibrary.failure("gst_app_sink_is_eos", t);
        }
    }
}
