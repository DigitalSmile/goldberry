package dev.goldberry.stats;

/// One frame of a window's loop, as a frame listener hears it.
///
/// ```java
/// host.window().onFrame(frame -> {
///     if (frame.intervalNanos() > 20_000_000) {
///         hitches.add(frame.copy());
///     }
/// });
/// ```
///
/// **The listener is handed the same object every frame**, a view over the
/// window's ring that reads the frame just presented, so listening allocates
/// nothing. It is valid only while the listener runs; [#copy] keeps one.
///
/// Every duration is in nanoseconds. Zero is "not measured", as it is on
/// [FrameStats]: the four present readings are zero for a frame presented
/// through the window surface, and the stages for a painter that does not time
/// them.
///
/// Read more: [Measuring](https://goldberry.dev/docs/performance/measuring.html#per-frame-timings-at-trace).
public sealed interface FrameSample permits FrameRecord, NewestFrame {

    /// Which frame this is: 1 for the window's first, as [FrameStats#count]
    /// counts them.
    long number();

    /// When the painter returned, in `System.nanoTime` units.
    long finishedNanos();

    /// From the frame before's end to this one's, or 0 for the first.
    long intervalNanos();

    /// The toolkit's own work on the frame.
    long paintNanos();

    /// Rebuilding widgets.
    long buildNanos();

    /// Resolving styles and building boxes.
    long styleNanos();

    /// Layout.
    long layoutNanos();

    /// Rasterizing.
    long rasterNanos();

    /// How many of the display's refreshes went by with this frame wanted and
    /// not yet seen.
    long lateRefreshes();

    /// Whether the frame was presented through the GPU.
    boolean composited();

    /// Uploading the frame's damage, for a composited frame.
    long uploadNanos();

    /// Waiting for the swapchain, for a composited frame.
    long acquireNanos();

    /// Recording and submitting the composite, for a composited frame.
    long submitNanos();

    /// Rendering the GPU layers that asked to be drawn again, for a composited
    /// frame: a `canvas3d`'s renderer recording its passes.
    long layerNanos();

    /// How many bytes of the frame went up, for a composited frame.
    long uploadBytes();

    /// This frame, kept: a record that stays as it is after the listener
    /// returns.
    default FrameRecord copy() {
        return new FrameRecord(
                number(),
                finishedNanos(),
                intervalNanos(),
                paintNanos(),
                buildNanos(),
                styleNanos(),
                layoutNanos(),
                rasterNanos(),
                lateRefreshes(),
                composited(),
                uploadNanos(),
                acquireNanos(),
                submitNanos(),
                layerNanos(),
                uploadBytes());
    }
}
