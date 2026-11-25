package dev.goldberry;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;

import dev.goldberry.natives.blend2d.BlendImage;

/// What a `:core` test that actually paints does when there is no rasterizer.
///
/// `NativeLibraryRequirement` does this job in `:natives`, and is not the
/// answer here: the class it asks — `NativeLibrary` — is in the one package
/// `:natives` deliberately does not export, and a `:core` test that paints
/// wants to know whether *Blend2D* answers, not whether a file loaded. So the
/// question is asked the only way an outside module can ask it: by trying.
///
/// An unavailable binding skips an optional renderer test. A job that sets
/// `goldberry.native.required` fails with the original linkage error.
///
/// Read more: [Skipping without
/// the native library](https://goldberry.dev/docs/guide/testing.html#skipping-without-the-native-library).
public final class RendererRequirement {

    private RendererRequirement() {}

    /// Probes the same Blend2D image binding used for rendering.
    public static void enforce() {
        enforce(
                () -> BlendImage.wrapping(ByteBuffer.allocateDirect(16), 2, 2, 8)
                        .close(),
                Boolean.getBoolean("goldberry.native.required"));
    }

    static void enforce(Runnable probe, boolean required) {
        try {
            probe.run();
        } catch (UnsatisfiedLinkError | NoClassDefFoundError | ExceptionInInitializerError e) {
            var message = "Cannot load the Blend2D renderer: " + e
                    + ". Set -Dgoldberry.native.library=<path> to its libgoldberry.";
            if (required) {
                Assertions.fail(message, e);
            }
            Assumptions.abort(message);
        }
    }
}
