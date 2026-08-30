package io.github.digitalsmile.goldberry.natives.harfbuzz.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// HarfBuzz's shaper, and the two tag lookups that feed it.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record ShapingCalls(Shape shape, ScriptFromString scriptFromString, LanguageFromString languageFromString) {

    /// Binds every function above, failing if the library exports none of them.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static ShapingCalls bind(SymbolLookup lookup) {
        return new ShapingCalls(new Shape(lookup), new ScriptFromString(lookup), new LanguageFromString(lookup));
    }

    /// Turns the buffer’s text into positioned glyphs.
    ///
    /// Features are NULL/0 until the CSS layer has `font-feature-settings` to
    /// compile into them.
    ///
    /// `void hb_shape(void*, void*, void*, int)`
    ///
    /// @param font the font to shape with
    /// @param buffer a buffer with text and properties set; replaced by its glyphs
    /// @param features an `hb_feature_t*` array, or NULL
    /// @param numFeatures how many features, or 0
    public static final class Shape {

        private static final MethodHandle FD_hb_shape =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        Shape(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_shape");
        }

        public void call(MemorySegment font, MemorySegment buffer, MemorySegment features, int numFeatures) {
            try {
                FD_hb_shape.invokeExact(address, font, buffer, features, numFeatures);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_shape", t);
            }
        }
    }

    /// Packs a four-character script tag.
    ///
    /// `int hb_script_from_string(void*, int)`
    ///
    /// @param name an ISO 15924 tag such as `Latn`
    /// @param length how many bytes, or -1 if NUL-terminated
    /// @return an `hb_script_t`
    public static final class ScriptFromString {

        private static final MethodHandle FD_hb_script_from_string =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        ScriptFromString(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_script_from_string");
        }

        public int call(MemorySegment name, int length) {
            try {
                return (int) FD_hb_script_from_string.invokeExact(address, name, length);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_script_from_string", t);
            }
        }
    }

    /// Interns a language tag.
    ///
    /// The returned pointer is HarfBuzz’s and lives as long as the process, so it
    /// is never freed.
    ///
    /// `void* hb_language_from_string(void*, int)`
    ///
    /// @param name a BCP 47 tag such as `en-GB`
    /// @param length how many bytes, or -1 if NUL-terminated
    /// @return an `hb_language_t`
    public static final class LanguageFromString {

        private static final MethodHandle FD_hb_language_from_string =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        LanguageFromString(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_language_from_string");
        }

        public MemorySegment call(MemorySegment name, int length) {
            try {
                return (MemorySegment) FD_hb_language_from_string.invokeExact(address, name, length);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_language_from_string", t);
            }
        }
    }
}
