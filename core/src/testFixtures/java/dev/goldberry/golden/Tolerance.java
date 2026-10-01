package dev.goldberry.golden;

/// How far a rendering may stray from its golden and still be the same picture.
///
/// Three numbers, because two kinds of disagreement are honest and they are not
/// the same size. A **rounding** disagreement moves a pixel by a level or two and
/// may touch every antialiased edge; an **ownership** disagreement — which of two
/// triangles a pixel on their shared edge belongs to, or whether a pixel on a
/// silhouette is covered at all — moves a pixel by the whole contrast of the edge
/// and touches only a handful. [#RASTER] admits the first; [#GPU] admits a few of
/// the second as well, and the first everywhere.
///
/// @param channel   the largest per-channel difference treated as the same
///                  colour
/// @param differing the share of pixels allowed to differ by anything at all
/// @param stray     the share of pixels allowed to differ by **more** than
///                  `channel` — zero wherever the rasterizer is the toolkit's own
public record Tolerance(int channel, double differing, double stray) {

    /// Every golden Blend2D draws, and every golden before ADR-0503.
    ///
    /// Two levels out of 256: a rounding disagreement between two SIMD pipelines
    /// lands at one, and a colour that actually changed is nowhere near this
    /// close. At most 2% of the image may differ at all, because antialiased edges
    /// are where the pipelines disagree and an edge is a small fraction of a
    /// frame. No pixel may stray beyond that: Blend2D's coverage is analytic, so
    /// nothing flips.
    public static final Tolerance RASTER = new Tolerance(2, 0.02, 0);

    /// A picture a **GPU** rasterized and shaded, compared across drivers.
    ///
    /// Both halves of [#RASTER]'s reasoning fail here, in opposite directions.
    /// A fragment shader's arithmetic is not specified to the last bit, so a lit
    /// fill may round one level differently on **every** pixel: the cube blessed
    /// on Metal differs by one level on 52% of its pixels on NVIDIA's Vulkan
    /// driver. So any pixel may differ within the channel tolerance. And Vulkan
    /// leaves the tie-break for a sample exactly on an edge, and the sub-pixel
    /// precision a vertex snaps to, to the implementation, as do Metal and
    /// D3D12: an aliased pixel on a silhouette may be covered on one driver and
    /// not another, and when it flips it moves by the edge's whole contrast — two
    /// of the same cube's 32,000 pixels came out 157 levels apart on lavapipe. So
    /// one pixel in a thousand may do that.
    ///
    /// What still fails is what a golden is for: a colour that changed moves
    /// every pixel of a fill by tens of levels, and a vertex in the wrong place
    /// or a reversed winding moves a silhouette's several hundred.
    public static final Tolerance GPU = new Tolerance(2, 1, 0.001);

    public Tolerance {
        if (channel < 0 || channel > 255) {
            throw new IllegalArgumentException("channel " + channel + " is not a level");
        }
        if (!(differing >= 0 && differing <= 1) || !(stray >= 0 && stray <= differing)) {
            throw new IllegalArgumentException(
                    "shares must satisfy 0 <= stray <= differing <= 1, not " + stray + " and " + differing);
        }
    }

    /// Whether a comparison that found `differing` pixels different at all, and
    /// `strays` of them beyond [#channel()], out of `total`, is the same picture.
    public boolean admits(int differing, int strays, int total) {
        if (total <= 0 || differing < 0 || strays < 0 || strays > differing || differing > total) {
            throw new IllegalArgumentException(
                    "counts " + strays + " <= " + differing + " <= " + total + " do not describe an image");
        }
        return (double) differing / total <= this.differing && (double) strays / total <= stray;
    }
}
