package io.github.digitalsmile.goldberry.natives.harfbuzz.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// HarfBuzz's shaping functions, one holder each.
///
/// See [io.github.digitalsmile.goldberry.natives.calls] for why the holders
/// live in a package of their own.
public record HarfBuzzCalls(
        Version version,
        BlobCreate blobCreate,
        BlobDestroy blobDestroy,
        FaceCreate faceCreate,
        FaceDestroy faceDestroy,
        FaceGetEmpty faceGetEmpty,
        FaceGetUpem faceGetUpem,
        FontCreate fontCreate,
        FontDestroy fontDestroy,
        FontSetScale fontSetScale,
        BufferCreate bufferCreate,
        BufferDestroy bufferDestroy,
        BufferReset bufferReset,
        BufferAddUtf16 bufferAddUtf16,
        BufferGuessSegmentProperties bufferGuessSegmentProperties,
        BufferSetDirection bufferSetDirection,
        BufferGetDirection bufferGetDirection,
        BufferSetScript bufferSetScript,
        BufferSetLanguage bufferSetLanguage,
        BufferGetLength bufferGetLength,
        BufferGetGlyphInfos bufferGetGlyphInfos,
        BufferGetGlyphPositions bufferGetGlyphPositions,
        ScriptFromString scriptFromString,
        LanguageFromString languageFromString,
        Shape shape) {

    /// Binds every function above.
    public static HarfBuzzCalls bind(SymbolLookup lookup) {
        return new HarfBuzzCalls(
                new Version(lookup),
                new BlobCreate(lookup),
                new BlobDestroy(lookup),
                new FaceCreate(lookup),
                new FaceDestroy(lookup),
                new FaceGetEmpty(lookup),
                new FaceGetUpem(lookup),
                new FontCreate(lookup),
                new FontDestroy(lookup),
                new FontSetScale(lookup),
                new BufferCreate(lookup),
                new BufferDestroy(lookup),
                new BufferReset(lookup),
                new BufferAddUtf16(lookup),
                new BufferGuessSegmentProperties(lookup),
                new BufferSetDirection(lookup),
                new BufferGetDirection(lookup),
                new BufferSetScript(lookup),
                new BufferSetLanguage(lookup),
                new BufferGetLength(lookup),
                new BufferGetGlyphInfos(lookup),
                new BufferGetGlyphPositions(lookup),
                new ScriptFromString(lookup),
                new LanguageFromString(lookup),
                new Shape(lookup));
    }

    /// `void hb_version(void*, void*, void*)`
    public static final class Version {

        private static final MethodHandle FD_hb_version =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        Version(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_version");
        }

        public void call(MemorySegment a1, MemorySegment a2, MemorySegment a3) {
            try {
                FD_hb_version.invokeExact(address, a1, a2, a3);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_version", t);
            }
        }
    }

    /// `void* hb_blob_create(void*, int, int, void*, void*)`
    public static final class BlobCreate {

        private static final MethodHandle FD_hb_blob_create =
                Downcalls.link(FunctionDescriptor.of(
                        ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        BlobCreate(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_blob_create");
        }

        public MemorySegment call(
            MemorySegment a1, int a2, int a3, MemorySegment a4, MemorySegment a5) {
            try {
                return (MemorySegment) FD_hb_blob_create.invokeExact(address, a1, a2, a3, a4, a5);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_blob_create", t);
            }
        }
    }

    /// `void hb_blob_destroy(void*)`
    public static final class BlobDestroy {

        private static final MethodHandle FD_hb_blob_destroy =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        BlobDestroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_blob_destroy");
        }

        public void call(MemorySegment a1) {
            try {
                FD_hb_blob_destroy.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_blob_destroy", t);
            }
        }
    }

    /// `void* hb_face_create(void*, int)`
    public static final class FaceCreate {

        private static final MethodHandle FD_hb_face_create =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        FaceCreate(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_face_create");
        }

        public MemorySegment call(MemorySegment a1, int a2) {
            try {
                return (MemorySegment) FD_hb_face_create.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_face_create", t);
            }
        }
    }

    /// `void hb_face_destroy(void*)`
    public static final class FaceDestroy {

        private static final MethodHandle FD_hb_face_destroy =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        FaceDestroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_face_destroy");
        }

        public void call(MemorySegment a1) {
            try {
                FD_hb_face_destroy.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_face_destroy", t);
            }
        }
    }

    /// `void* hb_face_get_empty(void)`
    public static final class FaceGetEmpty {

        private static final MethodHandle FD_hb_face_get_empty =
                Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        FaceGetEmpty(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_face_get_empty");
        }

        public MemorySegment call() {
            try {
                return (MemorySegment) FD_hb_face_get_empty.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_face_get_empty", t);
            }
        }
    }

    /// `int hb_face_get_upem(void*)`
    public static final class FaceGetUpem {

        private static final MethodHandle FD_hb_face_get_upem =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        FaceGetUpem(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_face_get_upem");
        }

        public int call(MemorySegment a1) {
            try {
                return (int) FD_hb_face_get_upem.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_face_get_upem", t);
            }
        }
    }

    /// `void* hb_font_create(void*)`
    public static final class FontCreate {

        private static final MethodHandle FD_hb_font_create =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

        private final MemorySegment address;

        FontCreate(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_font_create");
        }

        public MemorySegment call(MemorySegment a1) {
            try {
                return (MemorySegment) FD_hb_font_create.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_font_create", t);
            }
        }
    }

    /// `void hb_font_destroy(void*)`
    public static final class FontDestroy {

        private static final MethodHandle FD_hb_font_destroy =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        FontDestroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_font_destroy");
        }

        public void call(MemorySegment a1) {
            try {
                FD_hb_font_destroy.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_font_destroy", t);
            }
        }
    }

    /// `void hb_font_set_scale(void*, int, int)`
    public static final class FontSetScale {

        private static final MethodHandle FD_hb_font_set_scale =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        FontSetScale(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_font_set_scale");
        }

        public void call(MemorySegment a1, int a2, int a3) {
            try {
                FD_hb_font_set_scale.invokeExact(address, a1, a2, a3);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_font_set_scale", t);
            }
        }
    }

    /// `void* hb_buffer_create(void)`
    public static final class BufferCreate {

        private static final MethodHandle FD_hb_buffer_create =
                Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        BufferCreate(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_buffer_create");
        }

        public MemorySegment call() {
            try {
                return (MemorySegment) FD_hb_buffer_create.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_buffer_create", t);
            }
        }
    }

    /// `void hb_buffer_destroy(void*)`
    public static final class BufferDestroy {

        private static final MethodHandle FD_hb_buffer_destroy =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        BufferDestroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_buffer_destroy");
        }

        public void call(MemorySegment a1) {
            try {
                FD_hb_buffer_destroy.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_buffer_destroy", t);
            }
        }
    }

    /// `void hb_buffer_reset(void*)`
    public static final class BufferReset {

        private static final MethodHandle FD_hb_buffer_reset =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        BufferReset(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_buffer_reset");
        }

        public void call(MemorySegment a1) {
            try {
                FD_hb_buffer_reset.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_buffer_reset", t);
            }
        }
    }

    /// `void hb_buffer_add_utf16(void*, void*, int, int, int)`
    public static final class BufferAddUtf16 {

        private static final MethodHandle FD_hb_buffer_add_utf16 =
                Downcalls.link(FunctionDescriptor.ofVoid(
                        ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        BufferAddUtf16(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_buffer_add_utf16");
        }

        public void call(MemorySegment a1, MemorySegment a2, int a3, int a4, int a5) {
            try {
                FD_hb_buffer_add_utf16.invokeExact(address, a1, a2, a3, a4, a5);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_buffer_add_utf16", t);
            }
        }
    }

    /// `void hb_buffer_guess_segment_properties(void*)`
    public static final class BufferGuessSegmentProperties {

        private static final MethodHandle FD_hb_buffer_guess_segment_properties =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        BufferGuessSegmentProperties(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_buffer_guess_segment_properties");
        }

        public void call(MemorySegment a1) {
            try {
                FD_hb_buffer_guess_segment_properties.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_buffer_guess_segment_properties", t);
            }
        }
    }

    /// `void hb_buffer_set_direction(void*, int)`
    public static final class BufferSetDirection {

        private static final MethodHandle FD_hb_buffer_set_direction =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        BufferSetDirection(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_buffer_set_direction");
        }

        public void call(MemorySegment a1, int a2) {
            try {
                FD_hb_buffer_set_direction.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_buffer_set_direction", t);
            }
        }
    }

    /// `int hb_buffer_get_direction(void*)`
    public static final class BufferGetDirection {

        private static final MethodHandle FD_hb_buffer_get_direction =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        BufferGetDirection(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_buffer_get_direction");
        }

        public int call(MemorySegment a1) {
            try {
                return (int) FD_hb_buffer_get_direction.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_buffer_get_direction", t);
            }
        }
    }

    /// `void hb_buffer_set_script(void*, int)`
    public static final class BufferSetScript {

        private static final MethodHandle FD_hb_buffer_set_script =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        BufferSetScript(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_buffer_set_script");
        }

        public void call(MemorySegment a1, int a2) {
            try {
                FD_hb_buffer_set_script.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_buffer_set_script", t);
            }
        }
    }

    /// `void hb_buffer_set_language(void*, void*)`
    public static final class BufferSetLanguage {

        private static final MethodHandle FD_hb_buffer_set_language =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        BufferSetLanguage(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_buffer_set_language");
        }

        public void call(MemorySegment a1, MemorySegment a2) {
            try {
                FD_hb_buffer_set_language.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_buffer_set_language", t);
            }
        }
    }

    /// `int hb_buffer_get_length(void*)`
    public static final class BufferGetLength {

        private static final MethodHandle FD_hb_buffer_get_length =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        BufferGetLength(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_buffer_get_length");
        }

        public int call(MemorySegment a1) {
            try {
                return (int) FD_hb_buffer_get_length.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_buffer_get_length", t);
            }
        }
    }

    /// `void* hb_buffer_get_glyph_infos(void*, void*)`
    public static final class BufferGetGlyphInfos {

        private static final MethodHandle FD_hb_buffer_get_glyph_infos =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        BufferGetGlyphInfos(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_buffer_get_glyph_infos");
        }

        public MemorySegment call(MemorySegment a1, MemorySegment a2) {
            try {
                return (MemorySegment) FD_hb_buffer_get_glyph_infos.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_buffer_get_glyph_infos", t);
            }
        }
    }

    /// `void* hb_buffer_get_glyph_positions(void*, void*)`
    public static final class BufferGetGlyphPositions {

        private static final MethodHandle FD_hb_buffer_get_glyph_positions =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        BufferGetGlyphPositions(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_buffer_get_glyph_positions");
        }

        public MemorySegment call(MemorySegment a1, MemorySegment a2) {
            try {
                return (MemorySegment) FD_hb_buffer_get_glyph_positions.invokeExact(
                        address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_buffer_get_glyph_positions", t);
            }
        }
    }

    /// `int hb_script_from_string(void*, int)`
    public static final class ScriptFromString {

        private static final MethodHandle FD_hb_script_from_string =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        ScriptFromString(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_script_from_string");
        }

        public int call(MemorySegment a1, int a2) {
            try {
                return (int) FD_hb_script_from_string.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_script_from_string", t);
            }
        }
    }

    /// `void* hb_language_from_string(void*, int)`
    public static final class LanguageFromString {

        private static final MethodHandle FD_hb_language_from_string =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        LanguageFromString(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_language_from_string");
        }

        public MemorySegment call(MemorySegment a1, int a2) {
            try {
                return (MemorySegment) FD_hb_language_from_string.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_language_from_string", t);
            }
        }
    }

    /// `void hb_shape(void*, void*, void*, int)`
    public static final class Shape {

        private static final MethodHandle FD_hb_shape =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        Shape(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_shape");
        }

        public void call(MemorySegment a1, MemorySegment a2, MemorySegment a3, int a4) {
            try {
                FD_hb_shape.invokeExact(address, a1, a2, a3, a4);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_shape", t);
            }
        }
    }
}
