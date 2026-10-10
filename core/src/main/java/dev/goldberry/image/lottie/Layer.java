package dev.goldberry.image.lottie;

import java.util.List;

import org.jspecify.annotations.Nullable;

/// One layer of a composition.
///
/// @param index      the layer's `ind`, which parents and mattes refer to it by
/// @param parent     the `ind` of the layer whose transform this one rides on,
///                   or null
/// @param content    what it draws
/// @param transform  its `ks`
/// @param inPoint    the first frame it is shown on
/// @param outPoint   the frame it stops being shown on
/// @param startTime  the frame its own time starts at
/// @param stretch    how much slower than the composition its time runs
/// @param matte      how the layer above it cuts this one out, or
///                   [Matte#NONE]
/// @param matteIndex the `ind` of the matte layer when the document names it
///                   (`tp`), or null for the layer directly above
/// @param isMatte    whether this layer is only a matte for another (`td`), and
///                   not drawn itself
/// @param masks      the masks cutting it out, in order
record Layer(
        int index,
        @Nullable Integer parent,
        Content content,
        Transform transform,
        double inPoint,
        double outPoint,
        double startTime,
        double stretch,
        Matte matte,
        @Nullable Integer matteIndex,
        boolean isMatte,
        List<Mask> masks) {

    Layer {
        masks = List.copyOf(masks);
    }

    /// Whether the layer is shown at `frame` of its composition.
    boolean showsAt(double frame) {
        return frame >= inPoint && frame < outPoint;
    }

    /// The layer's own time at `frame` of its composition, which its
    /// keyframes are written in.
    double localFrame(double frame) {
        return frame - startTime;
    }

    /// What a layer draws.
    sealed interface Content {}

    /// Shapes: the contents of a shape layer.
    record Shapes(List<Shape> items) implements Content {

        public Shapes {
            items = List.copyOf(items);
        }
    }

    /// Nothing: a null layer, there to be a parent.
    record Nothing() implements Content {}

    /// A rectangle of one colour, `width` by `height` from the origin.
    ///
    /// @param argb the colour as `0xAARRGGBB`
    record Solid(int argb, double width, double height) implements Content {}

    /// Another composition, from the document's assets.
    ///
    /// @param reference the asset's `id`
    /// @param width     the box it is clipped to
    /// @param height    the box it is clipped to
    /// @param timeRemap seconds into the asset at each frame, or null to run
    ///                  it on the layer's own time
    record Precomp(
            String reference,
            double width,
            double height,
            @Nullable Property timeRemap) implements Content {}

    /// How a matte layer cuts out the layer under it.
    enum Matte {
        /// No matte.
        NONE,
        /// Shown where the matte is opaque.
        ALPHA,
        /// Shown where the matte is transparent.
        ALPHA_INVERTED,
        /// Shown where the matte is light.
        LUMA,
        /// Shown where the matte is dark.
        LUMA_INVERTED
    }

    /// One mask.
    ///
    /// @param mode     how it combines with the masks before it
    /// @param inverted whether it is the outside of the path that counts
    /// @param path     the outline, in the layer's space
    /// @param opacity  0 to 100
    record Mask(Mode mode, boolean inverted, ShapeProperty path, Property opacity) {

        /// How a mask combines with the ones before it.
        enum Mode {
            /// Takes no part.
            NONE,
            /// Adds its area.
            ADD,
            /// Takes its area away.
            SUBTRACT,
            /// Keeps only what it shares.
            INTERSECT,
            /// The more opaque of the two.
            LIGHTEN,
            /// The less opaque of the two.
            DARKEN,
            /// How much the two differ.
            DIFFERENCE
        }
    }
}
