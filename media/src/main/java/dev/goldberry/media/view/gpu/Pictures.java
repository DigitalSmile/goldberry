package dev.goldberry.media.view.gpu;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import dev.goldberry.gpu.video.ColorMatrix;
import dev.goldberry.gpu.video.PlaneLayout;
import dev.goldberry.gpu.video.VideoImage;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.picture.Picture;
import dev.goldberry.media.picture.VideoPicture;
import dev.goldberry.media.picture.VideoPlanes;

/// A [Picture] in the words of `:gpu`'s video layer: a [VideoImage] over the
/// picture's own bytes.
///
/// Nothing is copied. The image borrows the picture's planes or pixels, and so
/// lives under the picture's rule: until two more pictures have been handed
/// out after it. Planes become [VideoImage.Planes], with the pixel format's
/// [PlaneLayout] and the picture's [ColorMatrix] and range. A converted picture
/// becomes [VideoImage.Bgra].
///
/// Public so that `…media.gpu` can map with it too, and in a package that is
/// not exported, so that no application names it.
public final class Pictures {

    private Pictures() {}

    /// `picture` as the layer's vocabulary has it, borrowing its bytes.
    public static VideoImage image(Picture picture) {
        return switch (picture) {
            case VideoPicture bgra -> new VideoImage.Bgra(bgra.width(), bgra.height(), bgra.stride(), bgra.pixels());
            case VideoPlanes planes -> {
                var count = planes.format().planes();
                var buffers = new ArrayList<ByteBuffer>(count);
                var strides = new ArrayList<Integer>(count);
                for (var plane = 0; plane < count; plane++) {
                    buffers.add(planes.plane(plane));
                    strides.add(planes.stride(plane));
                }
                yield new VideoImage.Planes(
                        layout(planes.format()),
                        planes.width(),
                        planes.height(),
                        buffers,
                        strides,
                        matrix(planes.matrix()),
                        planes.fullRange());
            }
        };
    }

    /// The layer's layout for a decoder's pixel format.
    ///
    /// @throws IllegalArgumentException for [PixelFormat#I420A]: the layer draws
    ///         opaque planes, and a picture with alpha is converted instead
    static PlaneLayout layout(PixelFormat format) {
        return switch (format) {
            case NV12 -> PlaneLayout.NV12;
            case I420 -> PlaneLayout.I420;
            case P010 -> PlaneLayout.P010;
            case I010 -> PlaneLayout.I010;
            case I420A ->
                throw new IllegalArgumentException(
                        "the video layer draws no alpha; an I420A picture is converted and drawn on the CPU");
        };
    }

    /// The layer's matrix for a decoder's.
    static ColorMatrix matrix(VideoFrame.ColorMatrix matrix) {
        return switch (matrix) {
            case BT601 -> ColorMatrix.BT601;
            case BT709 -> ColorMatrix.BT709;
            case BT2020 -> ColorMatrix.BT2020;
        };
    }
}
