package io.github.digitalsmile.goldberry.natives.harfbuzz.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// HarfBuzz's blob, face and font — the three things a shaper needs.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record FontCalls(
        BlobCreate blobCreate,
        BlobDestroy blobDestroy,
        FaceCreate faceCreate,
        FaceDestroy faceDestroy,
        FaceGetEmpty faceGetEmpty,
        FaceGetUpem faceGetUpem,
        FontCreate fontCreate,
        FontDestroy fontDestroy,
        FontSetScale fontSetScale) {

    /// Binds every function above, failing if the library exports none of them.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static FontCalls bind(SymbolLookup lookup) {
        return new FontCalls(
                new BlobCreate(lookup),
                new BlobDestroy(lookup),
                new FaceCreate(lookup),
                new FaceDestroy(lookup),
                new FaceGetEmpty(lookup),
                new FaceGetUpem(lookup),
                new FontCreate(lookup),
                new FontDestroy(lookup),
                new FontSetScale(lookup));
    }

    /// Wraps bytes as a blob, copying them.
    ///
    /// `void* hb_blob_create(void*, int, int, void*, void*)`
    ///
    /// @param data the font file
    /// @param length how many bytes
    /// @param mode an `hb_memory_mode_t`; always DUPLICATE here, because nothing can
    ///        promise a Java array outlives the face
    /// @param userData passed to `destroy`
    /// @param destroy called when the blob is released; NULL to free nothing
    /// @return the new `hb_blob_t*`
    public static final class BlobCreate {

        private static final MethodHandle FD_hb_blob_create =
                Downcalls.link(FunctionDescriptor.of(
                        ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        BlobCreate(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_blob_create");
        }

        public MemorySegment call(
                MemorySegment data, int length, int mode, MemorySegment userData,
                MemorySegment destroy) {
            try {
                return (MemorySegment) FD_hb_blob_create.invokeExact(
                        address, data, length, mode, userData, destroy);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_blob_create", t);
            }
        }
    }

    /// Drops a reference to a blob.
    ///
    /// `void hb_blob_destroy(void*)`
    ///
    /// @param blob the blob to release
    public static final class BlobDestroy {

        private static final MethodHandle FD_hb_blob_destroy =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        BlobDestroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_blob_destroy");
        }

        public void call(MemorySegment blob) {
            try {
                FD_hb_blob_destroy.invokeExact(address, blob);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_blob_destroy", t);
            }
        }
    }

    /// Reads a face out of a blob.
    ///
    /// Nonsense bytes give a face with **no glyphs** rather than an error, which
    /// is why the checking around here is about arguments and not results.
    ///
    /// `void* hb_face_create(void*, int)`
    ///
    /// @param blob the font file
    /// @param index 0 for a plain font, the member index inside a collection
    /// @return the new `hb_face_t*`
    public static final class FaceCreate {

        private static final MethodHandle FD_hb_face_create =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        FaceCreate(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_face_create");
        }

        public MemorySegment call(MemorySegment blob, int index) {
            try {
                return (MemorySegment) FD_hb_face_create.invokeExact(address, blob, index);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_face_create", t);
            }
        }
    }

    /// Drops a reference to a face.
    ///
    /// `void hb_face_destroy(void*)`
    ///
    /// @param face the face to release
    public static final class FaceDestroy {

        private static final MethodHandle FD_hb_face_destroy =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        FaceDestroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_face_destroy");
        }

        public void call(MemorySegment face) {
            try {
                FD_hb_face_destroy.invokeExact(address, face);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_face_destroy", t);
            }
        }
    }

    /// HarfBuzz’s immortal empty face — a valid face with no glyphs.
    ///
    /// A singleton HarfBuzz owns, so it must **not** be destroyed. That is why
    /// [ShapedFont] tracks whether its face was borrowed.
    ///
    /// `void* hb_face_get_empty(void)`
    ///
    /// @return the shared empty `hb_face_t*`
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

    /// The face’s units per em — the grid its outlines are designed on.
    ///
    /// Also the scale HarfBuzz reports advances in when nothing has set one,
    /// which is what makes it the number the Blend2D side has to agree with
    /// (ADR-0034). Commonly 1000 for a PostScript-flavoured face and 2048 for a
    /// TrueType one, and free to be anything.
    ///
    /// `int hb_face_get_upem(void*)`
    ///
    /// @param face the face to ask
    /// @return units per em
    public static final class FaceGetUpem {

        private static final MethodHandle FD_hb_face_get_upem =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        FaceGetUpem(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_face_get_upem");
        }

        public int call(MemorySegment face) {
            try {
                return (int) FD_hb_face_get_upem.invokeExact(address, face);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_face_get_upem", t);
            }
        }
    }

    /// Creates a font from a face, at the face’s own upem until scaled.
    ///
    /// `void* hb_font_create(void*)`
    ///
    /// @param face the face to instantiate
    /// @return the new `hb_font_t*`
    public static final class FontCreate {

        private static final MethodHandle FD_hb_font_create =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

        private final MemorySegment address;

        FontCreate(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_font_create");
        }

        public MemorySegment call(MemorySegment face) {
            try {
                return (MemorySegment) FD_hb_font_create.invokeExact(address, face);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_font_create", t);
            }
        }
    }

    /// Drops a reference to a font.
    ///
    /// `void hb_font_destroy(void*)`
    ///
    /// @param font the font to release
    public static final class FontDestroy {

        private static final MethodHandle FD_hb_font_destroy =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        FontDestroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_font_destroy");
        }

        public void call(MemorySegment font) {
            try {
                FD_hb_font_destroy.invokeExact(address, font);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_font_destroy", t);
            }
        }
    }

    /// Sets the units advances come back in.
    ///
    /// `void hb_font_set_scale(void*, int, int)`
    ///
    /// @param font the font to scale
    /// @param xScale horizontal scale in 26.6 units
    /// @param yScale vertical scale in 26.6 units
    public static final class FontSetScale {

        private static final MethodHandle FD_hb_font_set_scale =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        FontSetScale(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_font_set_scale");
        }

        public void call(MemorySegment font, int xScale, int yScale) {
            try {
                FD_hb_font_set_scale.invokeExact(address, font, xScale, yScale);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_font_set_scale", t);
            }
        }
    }
}
