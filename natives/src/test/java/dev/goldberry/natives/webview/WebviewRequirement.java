package dev.goldberry.natives.webview;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;

/// What a test that needs `libgoldberry-webview` does when there is not one.
///
/// The web view library may be missing from a local build — a Linux machine
/// without WebKitGTK's headers makes only `libgoldberry`, and that is a
/// supported build — so by default these tests skip. A published library may
/// not be missing it: CI builds the web view for every target.
/// `-Dgoldberry.webview.required=true` is how each verify job says so, the way
/// `goldberry.native.required` does for `libgoldberry`.
///
/// The decision is [#decide], a pure function of its inputs, so it is unit tested
/// directly rather than by contorting system properties.
///
/// Read more:
/// [With and without the library](https://goldberry.dev/docs/contributing/testing.html#with-and-without-the-library).
public sealed interface WebviewRequirement {

    /// Set by the `test` task from `-Dgoldberry.webview.required` or
    /// `-Pgoldberry.webview.required`; see `natives/build.gradle`.
    String REQUIRED_PROPERTY = "goldberry.webview.required";

    /// The shim is loaded and bound. Run the test.
    record Run() implements WebviewRequirement {}

    /// Absent, and nobody promised otherwise. Abort with a reason.
    ///
    /// @param reason why the test did not run
    record Skip(String reason) implements WebviewRequirement {}

    /// Absent where it was required. Fail.
    ///
    /// @param reason what was missing and where it was looked for
    record Fail(String reason) implements WebviewRequirement {}

    /// Decides from availability and whether the build declared the web view
    /// mandatory.
    ///
    /// @param available what [Webview#isAvailable()] reports
    /// @param required  whether the web view was declared mandatory for this run
    /// @param path      the file the library was loaded from, if one was; may be null
    static WebviewRequirement decide(boolean available, boolean required, @Nullable String path) {
        if (available) {
            return new Run();
        }
        var where = path == null || path.isBlank()
                ? "no " + WebviewLibrary.LIBRARY_STEM + " was found beside libgoldberry or in a classifier jar"
                : path + " loaded but did not bind";
        return required
                ? new Fail("the web view is required for this run but is not usable — " + where
                        + ". A natives jar built from this would ship without web-view.")
                : new Skip("no web view in this build — " + where + ".");
    }

    /// Applies [#decide] to the running JVM: returns normally, aborts the test,
    /// or fails it.
    static void enforce() {
        var decision = decide(
                Webview.isAvailable(),
                Boolean.getBoolean(REQUIRED_PROPERTY),
                WebviewLibrary.get().map(library -> library.path().toString()).orElse(null));

        switch (decision) {
            case Run _ -> {}
            case Skip(var reason) -> Assumptions.abort(reason);
            case Fail(var reason) -> Assertions.fail(reason);
        }
    }
}
