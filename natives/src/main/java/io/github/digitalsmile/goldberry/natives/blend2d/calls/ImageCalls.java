package io.github.digitalsmile.goldberry.natives.blend2d.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// Blend2D’s `BLImage` — a rectangle of pixels, or a view over someone else’s.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record ImageCalls(
        ImageInitAsFromData imageInitAsFromData,
        ImageInit imageInit,
        ImageReadFromData imageReadFromData,
        ImageConvert imageConvert,
        ImageDestroy imageDestroy,
        ImageGetData imageGetData) {

    /// Binds every function above, failing if the library exports none of them.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static ImageCalls bind(SymbolLookup lookup) {
        return new ImageCalls(
                new ImageInitAsFromData(lookup),
                new ImageInit(lookup),
                new ImageReadFromData(lookup),
                new ImageConvert(lookup),
                new ImageDestroy(lookup),
                new ImageGetData(lookup));
    }

    /// Wraps memory the caller owns as an image, copying nothing.
    ///
    /// This is what makes a frame cost no blit: the rasterizer writes straight
    /// into the buffer that will be presented (ADR-0031).
    ///
    /// `int bl_image_init_as_from_data(void*, int, int, int, void*, int64_t, int, void*, void*)`
    ///
    /// @param image an uninitialised `BLImageCore` to take over
    /// @param width width in pixels
    /// @param height height in pixels
    /// @param format a `BLFormat`
    /// @param pixels the first pixel; the caller keeps ownership
    /// @param stride bytes per row, signed — negative means bottom-up, which this toolkit never
    ///        produces
    /// @param accessFlags a `BLDataAccessFlags`
    /// @param destroyFunc called when Blend2D releases the image; NULL to free nothing
    /// @param userData passed to `destroyFunc`
    public static final class ImageInitAsFromData {

        private static final MethodHandle FD_bl_image_init_as_from_data = Downcalls.link(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        ImageInitAsFromData(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_image_init_as_from_data");
        }

        public int call(
                MemorySegment image,
                int width,
                int height,
                int format,
                MemorySegment pixels,
                long stride,
                int accessFlags,
                MemorySegment destroyFunc,
                MemorySegment userData) {
            try {
                return (int) FD_bl_image_init_as_from_data.invokeExact(
                        address, image, width, height, format, pixels, stride, accessFlags, destroyFunc, userData);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_image_init_as_from_data", t);
            }
        }
    }

    /// Initialises an empty image — no pixels, no size, nothing allocated.
    ///
    /// The starting point for a *decode* rather than for a frame:
    /// [ImageReadFromData] is what gives it a size, because only the decoder
    /// knows one (ADR-0283).
    ///
    /// `int bl_image_init(void*)`
    ///
    /// @param image an uninitialised `BLImageCore` to take over
    public static final class ImageInit {

        private static final MethodHandle FD_bl_image_init = Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        ImageInit(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_image_init");
        }

        public int call(MemorySegment image) {
            try {
                return (int) FD_bl_image_init.invokeExact(address, image);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_image_init", t);
            }
        }
    }

    /// Decodes encoded bytes — a PNG, a JPEG, a QOI — into an image Blend2D
    /// allocates.
    ///
    /// **The one call here that allocates pixels.** Everything else in this
    /// module hands Blend2D memory Java already owns, and cannot: the size of a
    /// PNG is inside the PNG. The wrapper copies the result out and destroys the
    /// image on the same call, so the allocation does not outlive the decode
    /// (ADR-0283).
    ///
    /// `int bl_image_read_from_data(void*, const void*, size_t, const void*)`
    ///
    /// @param image an initialised `BLImageCore` to decode into
    /// @param data the first encoded byte
    /// @param size how many bytes there are
    /// @param codecs a `BLArray` of codecs to consider, or NULL for the built-in
    ///        ones — which is what this library is compiled with
    public static final class ImageReadFromData {

        private static final MethodHandle FD_bl_image_read_from_data =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS));

        private final MemorySegment address;

        ImageReadFromData(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_image_read_from_data");
        }

        public int call(MemorySegment image, MemorySegment data, long size, MemorySegment codecs) {
            try {
                return (int) FD_bl_image_read_from_data.invokeExact(address, image, data, size, codecs);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_image_read_from_data", t);
            }
        }
    }

    /// Converts an image to another pixel format, in place.
    ///
    /// What a decode needs after it: a PNG with no alpha channel arrives as
    /// `XRGB32` and every buffer in this toolkit is premultiplied BGRA, so the
    /// format is normalised once here rather than asked about at every blit.
    ///
    /// `int bl_image_convert(void*, int)`
    ///
    /// @param image the image to convert
    /// @param format a `BLFormat`
    public static final class ImageConvert {

        private static final MethodHandle FD_bl_image_convert =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        ImageConvert(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_image_convert");
        }

        public int call(MemorySegment image, int format) {
            try {
                return (int) FD_bl_image_convert.invokeExact(address, image, format);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_image_convert", t);
            }
        }
    }

    /// Releases Blend2D’s side of the image, leaving borrowed pixels untouched.
    ///
    /// `int bl_image_destroy(void*)`
    ///
    /// @param image the image to release
    public static final class ImageDestroy {

        private static final MethodHandle FD_bl_image_destroy =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        ImageDestroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_image_destroy");
        }

        public int call(MemorySegment image) {
            try {
                return (int) FD_bl_image_destroy.invokeExact(address, image);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_image_destroy", t);
            }
        }
    }

    /// Reads back where Blend2D thinks the pixels are, and at what stride.
    ///
    /// `int bl_image_get_data(void*, void*)`
    ///
    /// @param image the image to inspect
    /// @param out a caller-allocated `BLImageData` to fill in
    public static final class ImageGetData {

        private static final MethodHandle FD_bl_image_get_data =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        ImageGetData(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_image_get_data");
        }

        public int call(MemorySegment image, MemorySegment out) {
            try {
                return (int) FD_bl_image_get_data.invokeExact(address, image, out);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_image_get_data", t);
            }
        }
    }
}
