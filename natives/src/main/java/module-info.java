/// Goldberry native layer: hand-written FFM bindings for Blend2D, Yoga,
/// HarfBuzz, SDL3, and libxkbcommon (ADR-0010), plus the thin owning wrappers
/// around them.
///
/// This module exports nothing yet, and that is the point. Per
/// `docs/ARCHITECTURE.md` §3.1, raw `MemorySegment` must never escape this
/// module; the module graph -- not a naming convention -- is what enforces it.
/// When M0 lands, the exports added here will be the wrapper packages only, and
/// the binding packages will stay unexported.
///
/// This is also the module named in `--enable-native-access` (JEP 472): Java 25
/// warns on restricted native access from the unnamed module, and a later
/// release makes it an error.
module io.github.digitalsmile.goldberry.natives {

    // A facade, not an implementation: applications choose the backend, and one
    // that chooses none sees nothing at all (ADR-0023).
    requires transitive org.slf4j;

    // The wrapper packages, and only those. The `natives` package itself stays
    // unexported: NativeLibrary hands out a SymbolLookup, and a foreign type in
    // the public surface of this module is the boundary leaking by another name.
    // What is exported below traffics in Java types -- SdlWindowHandle wraps the
    // pointer, MeasureCallback wraps the stub, PixelBuffer arrives as a
    // ByteBuffer (ADR-0019).
    exports io.github.digitalsmile.goldberry.natives.blend2d;
    exports io.github.digitalsmile.goldberry.natives.harfbuzz;
    exports io.github.digitalsmile.goldberry.natives.sdl;
    exports io.github.digitalsmile.goldberry.natives.yoga;

    // Toolkit plumbing, not application surface.
    //
    // `exports ... to io.github.digitalsmile.goldberry.core` would say that
    // precisely and does not compile: :core depends on :natives, so :core is not
    // on the module path when this compiles, and javac warns that the target
    // module is not found -- which -Werror makes fatal. Adding :core here would
    // be a dependency cycle, and turning off the module lint to keep one
    // qualified export costs more than it buys.
    //
    // So it is a plain export with a docstring that says what it is for.
    exports io.github.digitalsmile.goldberry.natives.log;
}
