package dev.goldberry.stats;

/// One of the numbers a frame is made of, for [FrameStats#percentile]: the
/// interval between frames, the paint and its four stages, and the four parts
/// of a composited present.
///
/// The present's four are taken over composited frames alone, as their spans
/// are, because a frame presented through the window surface did not pay them.
///
/// Read more: [Measuring](https://goldberry.dev/docs/performance/measuring.html#per-frame-timings-at-trace).
public enum FrameStage {
    /// From one frame's end to the next one's: what the loop achieved,
    /// everything the platform did in between included.
    INTERVAL,
    /// The toolkit's own work on a frame.
    PAINT,
    /// Rebuilding widgets.
    BUILD,
    /// Resolving styles and building boxes.
    STYLE,
    /// Layout.
    LAYOUT,
    /// Rasterizing.
    RASTER,
    /// Uploading a composited frame's damage.
    UPLOAD,
    /// Waiting for the swapchain.
    ACQUIRE,
    /// Recording and submitting the composite.
    SUBMIT,
    /// Rendering the GPU layers that asked to be drawn again: a `canvas3d`'s
    /// renderer, a video's next picture.
    LAYERS;

    /// Whether this is measured on composited frames only.
    boolean composited() {
        return switch (this) {
            case UPLOAD, ACQUIRE, SUBMIT, LAYERS -> true;
            case INTERVAL, PAINT, BUILD, STYLE, LAYOUT, RASTER -> false;
        };
    }
}
