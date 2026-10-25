package dev.goldberry.gpu.video;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;

import dev.goldberry.gpu.TextureFormat;

/// A picture a [VideoLayer] shows: [Planes] it converts on the GPU, [Staged]
/// planes already in its upload memory, or [Bgra] it draws as it is.
///
/// **Compared by identity**, on purpose: a layer uploads a picture it has not
/// uploaded before, and asking whether two 4K pictures hold the same bytes
/// would cost what the upload does. A caller makes one image per picture and
/// hands the same one over for as long as that picture is shown.
///
/// The bytes are read when the layer renders, on the frame the image was shown
/// in, and not kept afterwards.
public sealed interface VideoImage {

    /// Width in pixels.
    int width();

    /// Height in pixels.
    int height();

    /// Y'CbCr planes, with the matrix and range they were encoded with, and
    /// the [ColorVolume] their R'G'B' is in.
    final class Planes implements VideoImage {

        private final PlaneLayout layout;
        private final int width;
        private final int height;
        private final List<ByteBuffer> planes;
        private final List<Integer> strides;
        private final ColorMatrix matrix;
        private final boolean fullRange;
        private final ColorVolume colour;

        /// Planes of `layout` in SDR, BT.709 primaries, each read from its
        /// position zero.
        ///
        /// @param strides   bytes from one row of each plane to the next
        /// @param fullRange whether luma spans the whole code range
        /// @throws IllegalArgumentException when the size is not positive, the
        ///                                  planes or strides are not as many as
        ///                                  `layout` has, or a stride is shorter
        ///                                  than its plane's row
        public Planes(
                PlaneLayout layout,
                int width,
                int height,
                List<ByteBuffer> planes,
                List<Integer> strides,
                ColorMatrix matrix,
                boolean fullRange) {
            this(layout, width, height, planes, strides, matrix, fullRange, ColorVolume.SDR);
        }

        /// Planes of `layout` whose R'G'B' is in `colour`, each read from its
        /// position zero.
        ///
        /// @throws IllegalArgumentException as the other constructor
        public Planes(
                PlaneLayout layout,
                int width,
                int height,
                List<ByteBuffer> planes,
                List<Integer> strides,
                ColorMatrix matrix,
                boolean fullRange,
                ColorVolume colour) {
            this.layout = Objects.requireNonNull(layout, "layout");
            this.matrix = Objects.requireNonNull(matrix, "matrix");
            this.colour = Objects.requireNonNull(colour, "colour");
            requireSize(width, height);
            this.planes = List.copyOf(planes);
            this.strides = List.copyOf(strides);
            if (this.planes.size() != layout.planes() || this.strides.size() != layout.planes()) {
                throw new IllegalArgumentException(layout + " has " + layout.planes() + " planes, not "
                        + this.planes.size() + " planes and " + this.strides.size() + " strides");
            }
            for (var plane = 0; plane < layout.planes(); plane++) {
                var rowBytes = layout.planeWidth(plane, width)
                        * layout.textureFormats().get(plane).bytesPerPixel();
                if (this.strides.get(plane) < rowBytes) {
                    throw new IllegalArgumentException("plane " + plane + " has a stride of " + this.strides.get(plane)
                            + ", less than its " + rowBytes + " bytes a row");
                }
            }
            this.width = width;
            this.height = height;
            this.fullRange = fullRange;
        }

        /// How the planes are laid out.
        public PlaneLayout layout() {
            return layout;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        /// The planes, luma first.
        public List<ByteBuffer> planes() {
            return planes;
        }

        /// Bytes from one row of each plane to the next.
        public List<Integer> strides() {
            return strides;
        }

        /// The matrix the picture was encoded with.
        public ColorMatrix matrix() {
            return matrix;
        }

        /// Whether luma spans the whole code range.
        public boolean fullRange() {
            return fullRange;
        }

        /// The primaries, transfer function and peak the R'G'B' is in.
        public ColorVolume colour() {
            return colour;
        }

        @Override
        public String toString() {
            return "VideoImage.Planes[" + layout + " " + width + "×" + height + " " + matrix
                    + (fullRange ? " full" : " limited")
                    + (colour.isSdr() ? "" : " " + colour.primaries() + " " + colour.transfer()) + "]";
        }
    }

    /// Y'CbCr planes already in the layer's upload memory ([PlaneStaging]),
    /// with the matrix, range and colour volume they were encoded with: uploaded
    /// with no copy on the layer's thread.
    ///
    /// @param planes    the staged planes
    /// @param matrix    the matrix the picture was encoded with
    /// @param fullRange whether luma spans the whole code range
    /// @param colour    the primaries, transfer function and peak
    record Staged(StagedPlanes planes, ColorMatrix matrix, boolean fullRange, ColorVolume colour)
            implements VideoImage {

        public Staged {
            Objects.requireNonNull(planes, "planes");
            Objects.requireNonNull(matrix, "matrix");
            Objects.requireNonNull(colour, "colour");
        }

        @Override
        public int width() {
            return planes.width();
        }

        @Override
        public int height() {
            return planes.height();
        }

        /// How the planes are laid out.
        public PlaneLayout layout() {
            return planes.layout();
        }
    }

    /// Premultiplied BGRA, opaque: a picture already converted on the CPU,
    /// drawn with no conversion of its own ([TextureFormat#B8G8R8A8_UNORM]).
    final class Bgra implements VideoImage {

        private final int width;
        private final int height;
        private final int stride;
        private final ByteBuffer pixels;

        /// `pixels`, read from position zero, rows `stride` bytes apart.
        ///
        /// @throws IllegalArgumentException when the size is not positive, or
        ///                                  the stride is shorter than a row
        public Bgra(int width, int height, int stride, ByteBuffer pixels) {
            requireSize(width, height);
            if (stride < width * 4) {
                throw new IllegalArgumentException("a stride of " + stride + " cannot hold " + width + " pixels");
            }
            this.width = width;
            this.height = height;
            this.stride = stride;
            this.pixels = Objects.requireNonNull(pixels, "pixels");
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        /// Bytes from one row to the next.
        public int stride() {
            return stride;
        }

        /// The pixels.
        public ByteBuffer pixels() {
            return pixels;
        }

        @Override
        public String toString() {
            return "VideoImage.Bgra[" + width + "×" + height + "]";
        }
    }

    private static void requireSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("size " + width + "×" + height);
        }
    }
}
