package io.github.digitalsmile.goldberry.natives.blend2d.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// Blend2D’s three font objects: the bytes, the face, and the sized font.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record FontCalls(
        FontDataInit fontDataInit,
        FontDataCreateFromData fontDataCreateFromData,
        FontDataDestroy fontDataDestroy,
        FontFaceInit fontFaceInit,
        FontFaceCreateFromData fontFaceCreateFromData,
        FontFaceDestroy fontFaceDestroy,
        FontInit fontInit,
        FontCreateFromFace fontCreateFromFace,
        FontDestroy fontDestroy,
        FontGetMetrics fontGetMetrics) {

    /// Binds every function above, failing if the library exports none of them.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static FontCalls bind(SymbolLookup lookup) {
        return new FontCalls(
                new FontDataInit(lookup),
                new FontDataCreateFromData(lookup),
                new FontDataDestroy(lookup),
                new FontFaceInit(lookup),
                new FontFaceCreateFromData(lookup),
                new FontFaceDestroy(lookup),
                new FontInit(lookup),
                new FontCreateFromFace(lookup),
                new FontDestroy(lookup),
                new FontGetMetrics(lookup));
    }

    /// Initialises an empty font data handle.
    ///
    /// Each `create` **replaces** what a handle holds, so each handle has to be
    /// `init`ed first: Blend2D releases the previous instance, and releasing an
    /// uninitialised one reads a pointer that was never written.
    ///
    /// `int bl_font_data_init(void*)`
    ///
    /// @param fontData an uninitialised `BLFontDataCore` to take over
    public static final class FontDataInit {

        private static final MethodHandle FD_bl_font_data_init =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        FontDataInit(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_font_data_init");
        }

        public int call(MemorySegment fontData) {
            try {
                return (int) FD_bl_font_data_init.invokeExact(address, fontData);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_font_data_init", t);
            }
        }
    }

    /// Points a font data handle at bytes the caller owns.
    ///
    /// `int bl_font_data_create_from_data(void*, void*, int64_t, void*, void*)`
    ///
    /// @param fontData an initialised handle to replace
    /// @param bytes the font file; the caller keeps ownership
    /// @param length how many bytes
    /// @param destroyFunc called when Blend2D releases the data; NULL to free nothing
    /// @param userData passed to `destroyFunc`
    public static final class FontDataCreateFromData {

        private static final MethodHandle FD_bl_font_data_create_from_data =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS));

        private final MemorySegment address;

        FontDataCreateFromData(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_font_data_create_from_data");
        }

        public int call(
                MemorySegment fontData,
                MemorySegment bytes,
                long length,
                MemorySegment destroyFunc,
                MemorySegment userData) {
            try {
                return (int) FD_bl_font_data_create_from_data.invokeExact(
                        address, fontData, bytes, length, destroyFunc, userData);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_font_data_create_from_data", t);
            }
        }
    }

    /// Releases the font data handle.
    ///
    /// `int bl_font_data_destroy(void*)`
    ///
    /// @param fontData the handle to release
    public static final class FontDataDestroy {

        private static final MethodHandle FD_bl_font_data_destroy =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        FontDataDestroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_font_data_destroy");
        }

        public int call(MemorySegment fontData) {
            try {
                return (int) FD_bl_font_data_destroy.invokeExact(address, fontData);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_font_data_destroy", t);
            }
        }
    }

    /// Initialises an empty font face handle.
    ///
    /// `int bl_font_face_init(void*)`
    ///
    /// @param face an uninitialised `BLFontFaceCore` to take over
    public static final class FontFaceInit {

        private static final MethodHandle FD_bl_font_face_init =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        FontFaceInit(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_font_face_init");
        }

        public int call(MemorySegment face) {
            try {
                return (int) FD_bl_font_face_init.invokeExact(address, face);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_font_face_init", t);
            }
        }
    }

    /// Reads one face out of font data.
    ///
    /// `int bl_font_face_create_from_data(void*, void*, int)`
    ///
    /// @param face an initialised handle to replace
    /// @param fontData the font data to read
    /// @param faceIndex 0 for a plain font, the member index inside a collection
    public static final class FontFaceCreateFromData {

        private static final MethodHandle FD_bl_font_face_create_from_data =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        FontFaceCreateFromData(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_font_face_create_from_data");
        }

        public int call(MemorySegment face, MemorySegment fontData, int faceIndex) {
            try {
                return (int) FD_bl_font_face_create_from_data.invokeExact(address, face, fontData, faceIndex);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_font_face_create_from_data", t);
            }
        }
    }

    /// Releases the font face handle.
    ///
    /// `int bl_font_face_destroy(void*)`
    ///
    /// @param face the handle to release
    public static final class FontFaceDestroy {

        private static final MethodHandle FD_bl_font_face_destroy =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        FontFaceDestroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_font_face_destroy");
        }

        public int call(MemorySegment face) {
            try {
                return (int) FD_bl_font_face_destroy.invokeExact(address, face);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_font_face_destroy", t);
            }
        }
    }

    /// Initialises an empty font handle.
    ///
    /// `int bl_font_init(void*)`
    ///
    /// @param font an uninitialised `BLFontCore` to take over
    public static final class FontInit {

        private static final MethodHandle FD_bl_font_init = Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        FontInit(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_font_init");
        }

        public int call(MemorySegment font) {
            try {
                return (int) FD_bl_font_init.invokeExact(address, font);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_font_init", t);
            }
        }
    }

    /// Sizes a face into a font.
    ///
    /// `int bl_font_create_from_face(void*, void*, float)`
    ///
    /// @param font an initialised handle to replace
    /// @param face the face to size
    /// @param size em size — a `float`, not a double: Blend2D’s own choice, and the one
    ///        place in the paint path where a coordinate narrows
    public static final class FontCreateFromFace {

        private static final MethodHandle FD_bl_font_create_from_face =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_FLOAT));

        private final MemorySegment address;

        FontCreateFromFace(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_font_create_from_face");
        }

        public int call(MemorySegment font, MemorySegment face, float size) {
            try {
                return (int) FD_bl_font_create_from_face.invokeExact(address, font, face, size);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_font_create_from_face", t);
            }
        }
    }

    /// Releases the font handle.
    ///
    /// `int bl_font_destroy(void*)`
    ///
    /// @param font the handle to release
    public static final class FontDestroy {

        private static final MethodHandle FD_bl_font_destroy = Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        FontDestroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_font_destroy");
        }

        public int call(MemorySegment font) {
            try {
                return (int) FD_bl_font_destroy.invokeExact(address, font);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_font_destroy", t);
            }
        }
    }

    /// Reads the font’s ascent, descent and line gap at its size.
    ///
    /// `int bl_font_get_metrics(void*, void*)`
    ///
    /// @param font the font to measure
    /// @param out a caller-allocated `BLFontMetrics` to fill in
    public static final class FontGetMetrics {

        private static final MethodHandle FD_bl_font_get_metrics =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        FontGetMetrics(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_font_get_metrics");
        }

        public int call(MemorySegment font, MemorySegment out) {
            try {
                return (int) FD_bl_font_get_metrics.invokeExact(address, font, out);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_font_get_metrics", t);
            }
        }
    }
}
