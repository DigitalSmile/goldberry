package io.github.digitalsmile.goldberry.natives.blend2d;

import io.github.digitalsmile.goldberry.natives.blend2d.calls.ImageCalls;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendFormat;
import io.github.digitalsmile.goldberry.natives.layout.Layouts;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendDataAccess;
import io.github.digitalsmile.goldberry.natives.blend2d.error.BlendException;

/// Blend2D's image calls, behind [BlendImage].
///
/// Package-private, like every binding class here: [BlendImage] owns the
/// handle and is the only way in, so nothing can reach `bl_image_destroy`
/// without the wrapper that knows whether the image is still alive.
final class Blend2dImage {

    private static final long IMAGE_DATA_PIXELS = Layouts.BL_IMAGE_DATA.offsetOf("pixel_data");

    private static final long IMAGE_DATA_STRIDE = Layouts.BL_IMAGE_DATA.offsetOf("stride");

    private static final long IMAGE_DATA_FORMAT = Layouts.BL_IMAGE_DATA.offsetOf("format");

    private static final class Holder {
        private static final Blend2dImage INSTANCE = new Blend2dImage(NativeLibrary.get().lookup());
    }

    private final ImageCalls calls;

    private Blend2dImage(SymbolLookup lookup) {
        this.calls = ImageCalls.bind(lookup);
    }

    static Blend2dImage get() {
        return Holder.INSTANCE;
    }

    /// Initialises `image` as a view over pixels Blend2D does not own.
    ///
    /// The destroy callback and its user data are both NULL: the buffer's
    /// lifetime is Java's, and telling Blend2D to free it would be handing it
    /// memory it did not allocate.
    /// `BLResult bl_image_init_as_from_data(BLImageCore*, int w, int h, BLFormat,`
    /// `void* pixel_data, intptr_t stride, BLDataAccessFlags,`
    /// `BLDestroyExternalDataFunc, void* user_data)`
    ///
    /// `intptr_t` is 8 bytes on every target here — the "pointer" scalar row is
    /// what says so. It is signed: a negative stride means the image starts at
    /// the bottom-left, which Goldberry never produces but must not silently
    /// reinterpret.
    void imageInitFromData(
            MemorySegment image, int width, int height, BlendFormat format,
            MemorySegment pixels, long stride) {

        int result;
        result = calls.imageInitAsFromData().call(image, width, height, format.nativeValue(),
                pixels, stride, BlendDataAccess.READ_WRITE.nativeValue(), MemorySegment.NULL,
                MemorySegment.NULL);
        check("bl_image_init_as_from_data", result);
    }

    void imageDestroy(MemorySegment image) {
        check("bl_image_destroy", calls.imageDestroy().call(image));
    }

    /// Reads back where Blend2D thinks the pixels are.
    ///
    /// Used by the tests rather than by the paint path: it is how "the image
    /// really is a view over the buffer we passed" becomes an assertion about
    /// an address rather than a claim in a comment.
    ImageData imageData(MemorySegment image) {
        try (var arena = Arena.ofConfined()) {
            var data = arena.allocate(Layouts.BL_IMAGE_DATA.layout());
            check("bl_image_get_data", calls.imageGetData().call(image, data));
            return new ImageData(
                    data.get(ValueLayout.ADDRESS, IMAGE_DATA_PIXELS).address(),
                    data.get(ValueLayout.JAVA_LONG, IMAGE_DATA_STRIDE),
                    BlendFormat.of(data.get(ValueLayout.JAVA_INT, IMAGE_DATA_FORMAT)));
        }
    }

    /// What `bl_image_get_data` reported. Addresses as `long`, because a raw
    /// [MemorySegment] may not leave this module and a test only needs to
    /// compare the number.
    record ImageData(long pixels, long stride, BlendFormat format) {
    }

    /// A `BLResult` that is not `BL_SUCCESS` is the call reporting a problem, not
    /// the crossing failing -- so it is raised as a [BlendException] naming the
    /// operation, and not as the [IllegalStateException] a holder raises.
    private static void check(String operation, int result) {
        if (result != 0) {
            throw new BlendException(operation, result);
        }
    }
}
