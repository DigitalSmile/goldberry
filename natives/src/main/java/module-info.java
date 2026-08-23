/// Goldberry native layer: hand-written FFM bindings for Blend2D, Yoga,
/// HarfBuzz and SDL3 (ADR-0010), plus the thin owning wrappers
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

    // Logging and the start-up timeline. Not `transitive`: nothing here puts a
    // type of :common in a signature, so a consumer of :natives is not made to
    // read it (ADR-0174).
    requires io.github.digitalsmile.goldberry.common;

    // The wrapper packages, and only those. The `natives` package itself stays
    // unexported: NativeLibrary hands out a SymbolLookup, and a foreign type in
    // the public surface of this module is the boundary leaking by another name.
    // What is exported below traffics in Java types -- SdlWindowHandle wraps the
    // pointer, MeasureCallback wraps the stub, PixelBuffer arrives as a
    // ByteBuffer (ADR-0019).
    //
    // Each library's wrappers are split by what they are, not by which library
    // they came from a second time (ADR-0172): the owning wrappers that hold a
    // handle stay in the library's own package, beside the binding class they
    // are the only callers of, and the enums and plain values -- which touch no
    // foreign memory at all -- get packages of their own.
    exports io.github.digitalsmile.goldberry.natives.blend2d;
    exports io.github.digitalsmile.goldberry.natives.blend2d.enums;
    exports io.github.digitalsmile.goldberry.natives.blend2d.error;
    exports io.github.digitalsmile.goldberry.natives.harfbuzz;
    exports io.github.digitalsmile.goldberry.natives.harfbuzz.enums;
    exports io.github.digitalsmile.goldberry.natives.sdl;
    exports io.github.digitalsmile.goldberry.natives.sdl.event;
    exports io.github.digitalsmile.goldberry.natives.sdl.window;
    exports io.github.digitalsmile.goldberry.natives.sdl.desktop;
    exports io.github.digitalsmile.goldberry.natives.yoga;
    exports io.github.digitalsmile.goldberry.natives.yoga.style;
    exports io.github.digitalsmile.goldberry.natives.yoga.measure;
}
