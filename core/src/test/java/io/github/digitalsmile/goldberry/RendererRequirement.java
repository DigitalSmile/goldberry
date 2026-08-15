package io.github.digitalsmile.goldberry;

import io.github.digitalsmile.goldberry.natives.blend2d.BlendImage;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Assumptions;

/// What a `:core` test that actually paints does when there is no rasterizer.
///
/// [io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement] does this
/// job in `:natives`, and cannot be reused: it lives in that module's *test*
/// sources, and the class it asks — `NativeLibrary` — is in the one package
/// `:natives` deliberately does not export (ADR-0007). So the question is asked
/// the only way an outside module can ask it: by trying.
///
/// The distinction that matters is between "no library" and "a broken library".
/// A missing library is an ordinary state on a contributor's machine and skips;
/// anything else is a real failure and is left to propagate.
public final class RendererRequirement {

    private RendererRequirement() {
    }

    /// Returns normally when Blend2D can rasterize, and aborts the test when the
    /// native library is simply not there.
    public static void enforce() {
        try {
            // Two by two, thrown away. Enough to force the library to load and
            // the binding's class initialiser to run, which is where a missing
            // symbol or a mismatched ABI surfaces.
            BlendImage.wrapping(ByteBuffer.allocateDirect(16), 2, 2, 8).close();
        } catch (UnsatisfiedLinkError | NoClassDefFoundError | ExceptionInInitializerError e) {
            Assumptions.abort(
                    "libgoldberry is not loadable from :core's tests, so nothing can rasterize: "
                            + e
                            + ". Pass -Dgoldberry.native.library=<path> to point at one"
                            + " — see core/build.gradle.");
        }
    }
}
