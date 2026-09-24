package io.github.digitalsmile.goldberry.render.composite;

/// Where a composited present's time went, in nanoseconds.
///
/// @param uploadNanos  copying the damage into staging memory and recording its
///                     upload
/// @param acquireNanos waiting for the swapchain texture: the display's pacing,
///                     not work
/// @param submitNanos  recording the composite and submitting it
/// @param uploadBytes  how many bytes of the frame went up
/// @param shown        whether there was a swapchain texture to show it in;
///                     false for a minimised or occluded window
public record PresentTimings(long uploadNanos, long acquireNanos, long submitNanos, long uploadBytes, boolean shown) {

    /// Nothing presented yet.
    public static final PresentTimings NONE = new PresentTimings(0, 0, 0, 0, false);
}
