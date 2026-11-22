package dev.goldberry.render;

/// Where the time of a present through the GPU went, in nanoseconds.
///
/// Reported by a composited window for the frame it last presented, and banked
/// by the frame loop beside that frame's paint stages, which is what a `hud`
/// reads it from. A window presenting through its window surface reports
/// [#NONE]: its present is SDL's copy, which it does not time.
///
/// Read more: [What a frame costs](https://goldberry.dev/docs/performance/index.html#the-frame-loop).
///
/// @param uploadNanos  copying the frame's damage into staging memory and
///                     recording its upload; for a frame painted where the
///                     GPU copies from, recording and submitting that copy
/// @param acquireNanos waiting for the swapchain texture: the display's pacing,
///                     not work
/// @param submitNanos  recording the composite and submitting it
/// @param uploadBytes  how many bytes of the frame went up
/// @param composited   whether the frame went through the GPU at all
/// @param layerNanos   rendering the GPU layers that asked to be drawn again:
///                     a `canvas3d`'s renderer, a video's next picture
public record PresentTimings(
        long uploadNanos, long acquireNanos, long submitNanos, long uploadBytes, boolean composited, long layerNanos) {

    /// Not presented through the GPU.
    public static final PresentTimings NONE = new PresentTimings(0, 0, 0, 0, false, 0);

    /// A present that rendered no layer, or did not time them apart.
    public PresentTimings(long uploadNanos, long acquireNanos, long submitNanos, long uploadBytes, boolean composited) {
        this(uploadNanos, acquireNanos, submitNanos, uploadBytes, composited, 0);
    }
}
