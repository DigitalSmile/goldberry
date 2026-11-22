package dev.goldberry.vector.thorvg.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.List;

import dev.goldberry.vector.thorvg.Downcalls;

/// ThorVG's paints and pictures: a parsed document is a picture, and each draw
/// adds a duplicate of it to a canvas. Every function but the two that make a
/// paint answers a `Tvg_Result`.
public record PictureCalls(
        NewPicture newPicture,
        LoadData loadData,
        GetSize getSize,
        SetSize setSize,
        Duplicate duplicate,
        Release release,
        SetTransform setTransform) {

    /// The symbols this record binds, in the order of its components.
    public static final List<String> SYMBOLS = List.of(
            "tvg_picture_new",
            "tvg_picture_load_data",
            "tvg_picture_get_size",
            "tvg_picture_set_size",
            "tvg_paint_duplicate",
            "tvg_paint_rel",
            "tvg_paint_set_transform");

    /// Binds every function above.
    ///
    /// @param lookup the loaded library
    public static PictureCalls bind(SymbolLookup lookup) {
        return new PictureCalls(
                new NewPicture(lookup),
                new LoadData(lookup),
                new GetSize(lookup),
                new SetSize(lookup),
                new Duplicate(lookup),
                new Release(lookup),
                new SetTransform(lookup));
    }

    /// `Tvg_Paint tvg_picture_new(void)`
    public static final class NewPicture {

        private static final MethodHandle FD_tvg_picture_new = Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        NewPicture(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "tvg_picture_new");
        }

        public MemorySegment call() {
            try {
                return (MemorySegment) FD_tvg_picture_new.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("tvg_picture_new", t);
            }
        }
    }

    /// `Tvg_Result tvg_picture_load_data(Tvg_Paint picture, const char *data, uint32_t size, const char
    /// *mimetype, const char *rpath, bool copy)`
    ///
    /// `copy` true: ThorVG keeps its own copy of the bytes, so Java's may go.
    public static final class LoadData {

        private static final MethodHandle FD_tvg_picture_load_data = Downcalls.link(
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, JAVA_BOOLEAN));

        private final MemorySegment address;

        LoadData(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "tvg_picture_load_data");
        }

        public int call(
                MemorySegment picture,
                MemorySegment data,
                int size,
                MemorySegment mimetype,
                MemorySegment resourcePath,
                boolean copy) {
            try {
                return (int) FD_tvg_picture_load_data.invokeExact(
                        address, picture, data, size, mimetype, resourcePath, copy);
            } catch (Throwable t) {
                throw Downcalls.failure("tvg_picture_load_data", t);
            }
        }
    }

    /// `Tvg_Result tvg_picture_get_size(const Tvg_Paint picture, float *w, float *h)`
    public static final class GetSize {

        private static final MethodHandle FD_tvg_picture_get_size =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        GetSize(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "tvg_picture_get_size");
        }

        public int call(MemorySegment picture, MemorySegment width, MemorySegment height) {
            try {
                return (int) FD_tvg_picture_get_size.invokeExact(address, picture, width, height);
            } catch (Throwable t) {
                throw Downcalls.failure("tvg_picture_get_size", t);
            }
        }
    }

    /// `Tvg_Result tvg_picture_set_size(Tvg_Paint picture, float w, float h)`
    public static final class SetSize {

        private static final MethodHandle FD_tvg_picture_set_size =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_FLOAT, JAVA_FLOAT));

        private final MemorySegment address;

        SetSize(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "tvg_picture_set_size");
        }

        public int call(MemorySegment picture, float width, float height) {
            try {
                return (int) FD_tvg_picture_set_size.invokeExact(address, picture, width, height);
            } catch (Throwable t) {
                throw Downcalls.failure("tvg_picture_set_size", t);
            }
        }
    }

    /// `Tvg_Paint tvg_paint_duplicate(Tvg_Paint paint)`
    ///
    /// NULL when it cannot.
    public static final class Duplicate {

        private static final MethodHandle FD_tvg_paint_duplicate =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

        private final MemorySegment address;

        Duplicate(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "tvg_paint_duplicate");
        }

        public MemorySegment call(MemorySegment paint) {
            try {
                return (MemorySegment) FD_tvg_paint_duplicate.invokeExact(address, paint);
            } catch (Throwable t) {
                throw Downcalls.failure("tvg_paint_duplicate", t);
            }
        }
    }

    /// `Tvg_Result tvg_paint_rel(Tvg_Paint paint)`
    ///
    /// Frees a paint no canvas holds.
    public static final class Release {

        private static final MethodHandle FD_tvg_paint_rel = Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        Release(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "tvg_paint_rel");
        }

        public int call(MemorySegment paint) {
            try {
                return (int) FD_tvg_paint_rel.invokeExact(address, paint);
            } catch (Throwable t) {
                throw Downcalls.failure("tvg_paint_rel", t);
            }
        }
    }

    /// `Tvg_Result tvg_paint_set_transform(Tvg_Paint paint, const Tvg_Matrix *m)`
    ///
    /// Nine floats, row-major.
    public static final class SetTransform {

        private static final MethodHandle FD_tvg_paint_set_transform =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        SetTransform(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "tvg_paint_set_transform");
        }

        public int call(MemorySegment paint, MemorySegment matrix) {
            try {
                return (int) FD_tvg_paint_set_transform.invokeExact(address, paint, matrix);
            } catch (Throwable t) {
                throw Downcalls.failure("tvg_paint_set_transform", t);
            }
        }
    }
}
