package io.github.digitalsmile.goldberry.natives.md4c.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// The five functions `libgoldberry` exports for Markdown.
///
/// None of them is md4c's own. `md_parse` is a SAX parser and binding it directly
/// would mean five upcall stubs and a detail struct per block; what crosses here is
/// one encoded buffer per document (ADR-0294), which is the rule every content
/// module shares — the hot path does not cross FFM (ADR-0190).
///
/// See [Downcalls] for why each handle is a `static final` constant and why these
/// live in a package of their own.
public record MarkdownCalls(Parse parse, Data data, Size size, Free free, Entity entity) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static MarkdownCalls bind(SymbolLookup lookup) {
        return new MarkdownCalls(
                new Parse(lookup), new Data(lookup), new Size(lookup), new Free(lookup), new Entity(lookup));
    }

    /// Parses a document into an encoded event stream.
    ///
    /// `void* goldberry_md_parse(const char* text, uint32_t size, uint32_t flags)`
    ///
    /// @return an opaque stream handle, or [MemorySegment#NULL] when the document
    ///         could not be turned into one — a runtime error in md4c, or a buffer
    ///         that could not grow. Never "this was not Markdown", which is not a
    ///         thing a document can fail to be
    public static final class Parse {

        private static final MethodHandle FD_goldberry_md_parse =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        Parse(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_md_parse");
        }

        public MemorySegment call(MemorySegment text, int size, int flags) {
            try {
                return (MemorySegment) FD_goldberry_md_parse.invokeExact(address, text, size, flags);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_md_parse", t);
            }
        }
    }

    /// The first byte of the encoded events.
    ///
    /// The segment is zero-length — a bare pointer carries no extent — so the
    /// caller resizes it against [Size], exactly as the layout table is read.
    ///
    /// `const void* goldberry_md_data(void* handle)`
    public static final class Data {

        private static final MethodHandle FD_goldberry_md_data =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

        private final MemorySegment address;

        Data(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_md_data");
        }

        public MemorySegment call(MemorySegment handle) {
            try {
                return (MemorySegment) FD_goldberry_md_data.invokeExact(address, handle);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_md_data", t);
            }
        }
    }

    /// How many bytes of events there are.
    ///
    /// `uint32_t goldberry_md_size(void* handle)`
    public static final class Size {

        private static final MethodHandle FD_goldberry_md_size =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        Size(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_md_size");
        }

        public int call(MemorySegment handle) {
            try {
                return (int) FD_goldberry_md_size.invokeExact(address, handle);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_md_size", t);
            }
        }
    }

    /// Releases a stream. Idempotent only in the sense that NULL is accepted; a
    /// handle is freed exactly once, by the wrapper that made it.
    ///
    /// `void goldberry_md_free(void* handle)`
    public static final class Free {

        private static final MethodHandle FD_goldberry_md_free = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        Free(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_md_free");
        }

        public void call(MemorySegment handle) {
            try {
                FD_goldberry_md_free.invokeExact(address, handle);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_md_free", t);
            }
        }
    }

    /// Resolves a named entity into its one or two codepoints.
    ///
    /// `int goldberry_md_entity(const char* name, uint32_t size, uint32_t* out)`
    ///
    /// @return 1 when the name is an entity and `out` has been written, 0 when it is
    ///         not one
    public static final class Entity {

        private static final MethodHandle FD_goldberry_md_entity =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));

        private final MemorySegment address;

        Entity(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_md_entity");
        }

        public int call(MemorySegment name, int size, MemorySegment out) {
            try {
                return (int) FD_goldberry_md_entity.invokeExact(address, name, size, out);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_md_entity", t);
            }
        }
    }
}
