package io.github.digitalsmile.goldberry.natives.harfbuzz.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// HarfBuzz's `hb_buffer_t` — the text going in and the glyphs coming out.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record BufferCalls(
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
        BufferGetGlyphPositions bufferGetGlyphPositions) {

    /// Binds every function above, failing if the library exports none of them.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static BufferCalls bind(SymbolLookup lookup) {
        return new BufferCalls(
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
                new BufferGetGlyphPositions(lookup));
    }

    /// Allocates an empty buffer.
    ///
    /// `void* hb_buffer_create(void)`
    public static final class BufferCreate {

        private static final MethodHandle FD_hb_buffer_create = Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        BufferCreate(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_buffer_create");
        }

        /// Calls `hb_buffer_create`.
        ///
        /// @return the new `hb_buffer_t*`
        public MemorySegment call() {
            try {
                return (MemorySegment) FD_hb_buffer_create.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_buffer_create", t);
            }
        }
    }

    /// Drops a reference to a buffer.
    ///
    /// `void hb_buffer_destroy(void*)`
    public static final class BufferDestroy {

        private static final MethodHandle FD_hb_buffer_destroy = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        BufferDestroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_buffer_destroy");
        }

        /// Calls `hb_buffer_destroy`.
        ///
        /// @param buffer the buffer to release
        public void call(MemorySegment buffer) {
            try {
                FD_hb_buffer_destroy.invokeExact(address, buffer);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_buffer_destroy", t);
            }
        }
    }

    /// Clears the buffer’s contents and properties, keeping the allocation.
    ///
    /// `void hb_buffer_reset(void*)`
    public static final class BufferReset {

        private static final MethodHandle FD_hb_buffer_reset = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        BufferReset(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_buffer_reset");
        }

        /// Calls `hb_buffer_reset`.
        ///
        /// @param buffer the buffer to empty
        public void call(MemorySegment buffer) {
            try {
                FD_hb_buffer_reset.invokeExact(address, buffer);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_buffer_reset", t);
            }
        }
    }

    /// Adds UTF-16 code units, with context either side of the part to shape.
    ///
    /// `itemOffset` and `itemLength` select what is *shaped*; everything else in
    /// `text` is context the shaper may look at for joining behaviour but does
    /// not produce glyphs for. That distinction is what makes shaping one word of
    /// a paragraph give the same result as shaping the paragraph.
    ///
    /// UTF-16 is why this is the natural entry point: a Java String already is
    /// UTF-16, so the text crosses without being transcoded.
    ///
    /// `void hb_buffer_add_utf16(void*, void*, int, int, int)`
    public static final class BufferAddUtf16 {

        private static final MethodHandle FD_hb_buffer_add_utf16 =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        BufferAddUtf16(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_buffer_add_utf16");
        }

        /// Calls `hb_buffer_add_utf16`.
        ///
        /// @param buffer the buffer to add to
        /// @param text UTF-16 code units
        /// @param textLength how many code units `text` holds, or -1 if NUL-terminated
        /// @param itemOffset where the part to shape starts
        /// @param itemLength how much of it to shape
        public void call(MemorySegment buffer, MemorySegment text, int textLength, int itemOffset, int itemLength) {
            try {
                FD_hb_buffer_add_utf16.invokeExact(address, buffer, text, textLength, itemOffset, itemLength);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_buffer_add_utf16", t);
            }
        }
    }

    /// Fills in direction, script and language from the text itself.
    ///
    /// A starting point, not an answer: a caller that knows better sets them.
    ///
    /// `void hb_buffer_guess_segment_properties(void*)`
    public static final class BufferGuessSegmentProperties {

        private static final MethodHandle FD_hb_buffer_guess_segment_properties =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        BufferGuessSegmentProperties(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_buffer_guess_segment_properties");
        }

        /// Calls `hb_buffer_guess_segment_properties`.
        ///
        /// @param buffer a buffer with text already added
        public void call(MemorySegment buffer) {
            try {
                FD_hb_buffer_guess_segment_properties.invokeExact(address, buffer);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_buffer_guess_segment_properties", t);
            }
        }
    }

    /// Sets which way the text runs.
    ///
    /// `void hb_buffer_set_direction(void*, int)`
    public static final class BufferSetDirection {

        private static final MethodHandle FD_hb_buffer_set_direction =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        BufferSetDirection(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_buffer_set_direction");
        }

        /// Calls `hb_buffer_set_direction`.
        ///
        /// @param direction an `hb_direction_t`
        public void call(MemorySegment buffer, int direction) {
            try {
                FD_hb_buffer_set_direction.invokeExact(address, buffer, direction);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_buffer_set_direction", t);
            }
        }
    }

    /// The direction in force.
    ///
    /// `int hb_buffer_get_direction(void*)`
    public static final class BufferGetDirection {

        private static final MethodHandle FD_hb_buffer_get_direction =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        BufferGetDirection(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_buffer_get_direction");
        }

        /// Calls `hb_buffer_get_direction`.
        ///
        /// @return an `hb_direction_t`
        public int call(MemorySegment buffer) {
            try {
                return (int) FD_hb_buffer_get_direction.invokeExact(address, buffer);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_buffer_get_direction", t);
            }
        }
    }

    /// Sets the script the text is shaped as.
    ///
    /// `void hb_buffer_set_script(void*, int)`
    public static final class BufferSetScript {

        private static final MethodHandle FD_hb_buffer_set_script =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        BufferSetScript(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_buffer_set_script");
        }

        /// Calls `hb_buffer_set_script`.
        ///
        /// @param script an `hb_script_t`, which is a packed four-character tag
        public void call(MemorySegment buffer, int script) {
            try {
                FD_hb_buffer_set_script.invokeExact(address, buffer, script);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_buffer_set_script", t);
            }
        }
    }

    /// Sets the language, which some fonts shape differently for.
    ///
    /// `void hb_buffer_set_language(void*, void*)`
    public static final class BufferSetLanguage {

        private static final MethodHandle FD_hb_buffer_set_language =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        BufferSetLanguage(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_buffer_set_language");
        }

        /// Calls `hb_buffer_set_language`.
        ///
        /// @param language an `hb_language_t`, which is an interned pointer
        public void call(MemorySegment buffer, MemorySegment language) {
            try {
                FD_hb_buffer_set_language.invokeExact(address, buffer, language);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_buffer_set_language", t);
            }
        }
    }

    /// How many items the buffer holds — code units before shaping, glyphs after.
    ///
    /// `int hb_buffer_get_length(void*)`
    public static final class BufferGetLength {

        private static final MethodHandle FD_hb_buffer_get_length =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        BufferGetLength(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_buffer_get_length");
        }

        /// Calls `hb_buffer_get_length`.
        ///
        /// @return the item count
        public int call(MemorySegment buffer) {
            try {
                return (int) FD_hb_buffer_get_length.invokeExact(address, buffer);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_buffer_get_length", t);
            }
        }
    }

    /// The glyph ids and clusters the shaper produced.
    ///
    /// Arrives as a bare pointer, which carries no extent — the caller resizes it
    /// against the count and the stride the layout table verified.
    ///
    /// `void* hb_buffer_get_glyph_infos(void*, void*)`
    public static final class BufferGetGlyphInfos {

        private static final MethodHandle FD_hb_buffer_get_glyph_infos =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        BufferGetGlyphInfos(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_buffer_get_glyph_infos");
        }

        /// Calls `hb_buffer_get_glyph_infos`.
        ///
        /// @param buffer a shaped buffer
        /// @param length a `unsigned*` to receive the count, or NULL
        /// @return an `hb_glyph_info_t*` array owned by the buffer
        public MemorySegment call(MemorySegment buffer, MemorySegment length) {
            try {
                return (MemorySegment) FD_hb_buffer_get_glyph_infos.invokeExact(address, buffer, length);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_buffer_get_glyph_infos", t);
            }
        }
    }

    /// The advances and offsets the shaper produced.
    ///
    /// `void* hb_buffer_get_glyph_positions(void*, void*)`
    public static final class BufferGetGlyphPositions {

        private static final MethodHandle FD_hb_buffer_get_glyph_positions =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        BufferGetGlyphPositions(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_buffer_get_glyph_positions");
        }

        /// Calls `hb_buffer_get_glyph_positions`.
        ///
        /// @param buffer a shaped buffer
        /// @param length a `unsigned*` to receive the count, or NULL
        /// @return an `hb_glyph_position_t*` array owned by the buffer
        public MemorySegment call(MemorySegment buffer, MemorySegment length) {
            try {
                return (MemorySegment) FD_hb_buffer_get_glyph_positions.invokeExact(address, buffer, length);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_buffer_get_glyph_positions", t);
            }
        }
    }
}
