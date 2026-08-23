package io.github.digitalsmile.goldberry.natives.blend2d.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// Blend2D’s `BLImage` — a rectangle of pixels, or a view over someone else’s.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See
/// [io.github.digitalsmile.goldberry.natives.calls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record ImageCalls(
        ImageInitAsFromData imageInitAsFromData,
        ImageDestroy imageDestroy,
        ImageGetData imageGetData) {

    /// Binds every function above, failing if the library exports none of them.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static ImageCalls bind(SymbolLookup lookup) {
        return new ImageCalls(
                new ImageInitAsFromData(lookup),
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

        private static final MethodHandle FD_bl_image_init_as_from_data =
                Downcalls.link(FunctionDescriptor.of(
                        JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, JAVA_LONG,
                        JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        ImageInitAsFromData(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_image_init_as_from_data");
        }

        public int call(
        MemorySegment image, int width, int height, int format, MemorySegment pixels, long stride,
                int accessFlags, MemorySegment destroyFunc, MemorySegment userData) {
            try {
                return (int) FD_bl_image_init_as_from_data.invokeExact(
                address, image, width, height, format, pixels, stride, accessFlags, destroyFunc,
                        userData);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_image_init_as_from_data", t);
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
