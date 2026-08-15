package io.github.digitalsmile.goldberry.natives;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;

/// What a test that needs a real `libgoldberry` does when there is not one.
///
/// Tests like [io.github.digitalsmile.goldberry.natives.layout.LayoutVerificationTest]
/// cannot run without a compiled library, and on a contributor's machine that is
/// an ordinary state of affairs — the superbuild needs a C/C++ toolchain and
/// twenty minutes, and a Java-only change should not demand either. So they skip.
///
/// In CI they must not. A verification job whose tests all skipped is a green
/// tick over an artifact nothing loaded, which is worse than a red one: it
/// reports a check that never happened. `-Dgoldberry.native.required=true` is how
/// the build says the library is not optional here. See ADR-0016.
///
/// The decision is [#decide] — a pure function of the two inputs, so it is unit
/// tested directly rather than by contorting system properties.
public sealed interface NativeLibraryRequirement {

    /// Set by the `test` task from `-Dgoldberry.native.required` or
    /// `-Pgoldberry.native.required`; see `natives/build.gradle`.
    String REQUIRED_PROPERTY = "goldberry.native.required";

    /// The library is there. Run the test.
    record Run() implements NativeLibraryRequirement {}

    /// Absent, and nobody promised otherwise. Abort with a reason.
    record Skip(String reason) implements NativeLibraryRequirement {}

    /// Absent where it was required. Fail.
    record Fail(String reason) implements NativeLibraryRequirement {}

    /// Decides from availability and whether the build declared the library
    /// mandatory.
    ///
    /// @param available what [NativeLibrary#isAvailable()] reports
    /// @param required  whether the library was declared mandatory for this run
    /// @param path      the configured library path, for the message; may be null
    static NativeLibraryRequirement decide(boolean available, boolean required, String path) {
        if (available) {
            return new Run();
        }
        var where = path == null || path.isBlank()
                ? "no path was configured"
                : "looked for it at " + path;
        return required
                ? new Fail("libgoldberry is required for this run but is not loadable — " + where
                        + ". The layout check is what the hand-written bindings rest on"
                        + " (ADR-0010), so skipping it here would report a check that never ran.")
                : new Skip("libgoldberry is not built for this platform — " + where
                        + ". Run :natives:cmakeBuild, or pass -D" + NativeLibrary.LIBRARY_PATH_PROPERTY
                        + "=<path> to verify one built elsewhere.");
    }

    /// Applies [#decide] to the running JVM: returns normally, aborts the test,
    /// or fails it.
    static void enforce() {
        var decision = decide(
                NativeLibrary.isAvailable(),
                Boolean.getBoolean(REQUIRED_PROPERTY),
                System.getProperty(NativeLibrary.LIBRARY_PATH_PROPERTY));

        switch (decision) {
            case Run ignored -> {
            }
            case Skip(var reason) -> Assumptions.abort(reason);
            case Fail(var reason) -> Assertions.fail(reason);
        }
    }
}
