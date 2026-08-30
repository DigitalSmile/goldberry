package io.github.digitalsmile.goldberry.natives.blend2d;

import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.blend2d.calls.RuntimeCalls;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendRuntimeInfoType;
import io.github.digitalsmile.goldberry.natives.blend2d.error.BlendException;
import io.github.digitalsmile.goldberry.natives.layout.Layouts;

/// Blend2D's process-wide runtime query.
///
/// A class of its own because it belongs to no object: everything else here
/// is a call on an image, a context, a path or a font.
final class Blend2dRuntime {

    private static final long BUILD_INFO_SIZE = Layouts.BL_RUNTIME_BUILD_INFO.byteSize();

    private static final long MAJOR_OFFSET = Layouts.BL_RUNTIME_BUILD_INFO.offsetOf("major_version");

    private static final long MINOR_OFFSET = Layouts.BL_RUNTIME_BUILD_INFO.offsetOf("minor_version");

    private static final long PATCH_OFFSET = Layouts.BL_RUNTIME_BUILD_INFO.offsetOf("patch_version");

    private static final long COMPILER_OFFSET = Layouts.BL_RUNTIME_BUILD_INFO.offsetOf("compiler_info");

    private static final long COMPILER_SIZE = Layouts.BL_RUNTIME_BUILD_INFO.sizeOf("compiler_info");

    private static final class Holder {
        private static final Blend2dRuntime INSTANCE =
                new Blend2dRuntime(NativeLibrary.get().lookup());
    }

    private final RuntimeCalls calls;

    private Blend2dRuntime(SymbolLookup lookup) {
        this.calls = RuntimeCalls.bind(lookup);
    }

    static Blend2dRuntime get() {
        return Holder.INSTANCE;
    }

    /// The Blend2D that was statically linked into `libgoldberry`.
    BlendVersion version() {
        try (var arena = Arena.ofConfined()) {
            var info = arena.allocate(BUILD_INFO_SIZE, Layouts.BL_RUNTIME_BUILD_INFO.byteAlignment());
            // The type and the buffer must agree: asking for SYSTEM with a
            // BUILD-sized allocation writes past the end. Pairing them here is
            // what makes that unrepresentable rather than merely documented.
            check(
                    "bl_runtime_query_info",
                    calls.runtimeQueryInfo().call(BlendRuntimeInfoType.BUILD.nativeValue(), info));

            var compiler = info.asSlice(COMPILER_OFFSET, COMPILER_SIZE).toArray(ValueLayout.JAVA_BYTE);
            var end = 0;
            while (end < compiler.length && compiler[end] != 0) {
                end++;
            }

            return new BlendVersion(
                    info.get(ValueLayout.JAVA_INT, MAJOR_OFFSET),
                    info.get(ValueLayout.JAVA_INT, MINOR_OFFSET),
                    info.get(ValueLayout.JAVA_INT, PATCH_OFFSET),
                    new String(compiler, 0, end, StandardCharsets.UTF_8));
        }
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
