package dev.goldberry.media.ffm;

import java.lang.foreign.Linker;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/// The C types whose size is the target's, not the language's: `size_t` and
/// `long`. Every target here is 64-bit, so `size_t` is 8 bytes everywhere; C's
/// `long` is 8 on Linux and macOS (LP64) and 4 on Windows (LLP64), which is why
/// a struct holding one has two shapes in [NativeStructs] and a call taking one
/// is adapted in [Downcalls#cLongsAsLongs].
public final class NativeTypes {

    /// `size_t` on this target.
    public static final ValueLayout SIZE_T = canonical("size_t");

    /// C's `long` on this target.
    public static final ValueLayout C_LONG = canonical("long");

    /// The size of C's `long` on this target, in bytes.
    public static final long C_LONG_SIZE = C_LONG.byteSize();

    private NativeTypes() {}

    private static ValueLayout canonical(String name) {
        return (ValueLayout) Objects.requireNonNull(
                Linker.nativeLinker().canonicalLayouts().get(name), "the native linker has no C " + name);
    }
}
