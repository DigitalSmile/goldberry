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
/// `@SuppressWarnings("module")` for the qualified exports below, and for nothing
/// else.
///
/// `exports … to io.github.digitalsmile.goldberry.core` names a module that is
/// **not on this module's compile path and cannot be** — `:core` requires
/// `:natives`, so the dependency runs the other way and javac compiles this one
/// first. It warns that the target module is not found, which under `-Werror` is
/// a build failure; the export still works, because the name is resolved at run
/// time and by the modules that read it.
///
/// The alternative was `-Xlint:-module` in the build file, which would switch the
/// lint off for every directive in this descriptor rather than for the twelve
/// that need it.
@SuppressWarnings("module")
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
    //
    // **Qualified, to `:core` and to nobody else** (ADR-0280). This is the
    // second half of the rule above, and the one that was prose until now: raw
    // foreign memory never leaves this module, *and* no type of this module
    // appears in a signature an application can read.
    //
    // Unqualified, these packages reached every application: `:core` required
    // this module `transitive`ly and `:widgets` requires `:core` the same way,
    // so an application that had never heard of `:natives` could name a
    // `BlendPath` or a `StyleLength` -- and did. `paint.Box` was typed on
    // thirteen of them, and a Box is what every custom widget returns.
    //
    // What replaced them: `paint.Path`, `Stroke`, `Gradient` (ADR-0277) and the
    // `layout` vocabulary (ADR-0279). The translation to what is below happens
    // in two package-private files in `:core`, and the compiler is what says so
    // now rather than a convention.
    //
    // **All three wrapped libraries are qualified now** (ADR-0290). Blend2D was
    // the last one open, because `Frame.drawGlyphs` took a `BlendFont` and a
    // `BlendGlyphBuffer` and was public; `paint.GlyphPen` owns them, that method
    // is package-private, and `:core` no longer requires this module
    // `transitive`ly. What is left unqualified is SDL's wrappers, which an
    // application legitimately names -- a `BackendWindow` handed to a popup, a
    // tray, a cursor.
    exports io.github.digitalsmile.goldberry.natives.blend2d to
            io.github.digitalsmile.goldberry.core;
    exports io.github.digitalsmile.goldberry.natives.blend2d.enums to
            io.github.digitalsmile.goldberry.core;
    exports io.github.digitalsmile.goldberry.natives.blend2d.error to
            io.github.digitalsmile.goldberry.core;
    exports io.github.digitalsmile.goldberry.natives.harfbuzz to
            io.github.digitalsmile.goldberry.core;
    exports io.github.digitalsmile.goldberry.natives.harfbuzz.enums to
            io.github.digitalsmile.goldberry.core;
    exports io.github.digitalsmile.goldberry.natives.sdl;
    exports io.github.digitalsmile.goldberry.natives.sdl.dialog;
    exports io.github.digitalsmile.goldberry.natives.sdl.event;
    exports io.github.digitalsmile.goldberry.natives.sdl.window;
    exports io.github.digitalsmile.goldberry.natives.sdl.desktop;

    /// What this build of the platform layer can actually do (ADR-0325).
    ///
    /// Qualified to `:core`, like Yoga's and Blend2D's wrappers and for the same
    /// reason: an application asks `Goldberry.capabilities()` and reads the
    /// toolkit's own `Capability`, so no type of this module appears in a
    /// signature it can name. What crosses here is an enum of five constants and
    /// an `int` behind it.
    exports io.github.digitalsmile.goldberry.natives.platform to
            io.github.digitalsmile.goldberry.core;
    /// md4c, exported to `:html` and to nobody else (ADR-0294).
    ///
    /// The second name on this seal, and the first that is not `:core`. Markdown is
    /// not part of the toolkit's own surface — it is `goldberry-html`'s dependency,
    /// quarantined by ADR-0190 into an optional module an application opts into — so
    /// the wrapper reaches that module and stops there. `:core` cannot see it either,
    /// which is the half worth saying: nothing in the widget catalog, the cascade or
    /// the text stack knows what Markdown is.
    ///
    /// What crosses is values: a `String` in, and records out that carry no foreign
    /// memory and no struct layout. The event stream is encoded in C and read once,
    /// so the seven detail structs md4c hands its callbacks are never modelled in
    /// Java at all.
    exports io.github.digitalsmile.goldberry.natives.md4c to
            io.github.digitalsmile.goldberry.html;
    exports io.github.digitalsmile.goldberry.natives.md4c.enums to
            io.github.digitalsmile.goldberry.html;
    exports io.github.digitalsmile.goldberry.natives.yoga to
            io.github.digitalsmile.goldberry.core;
    exports io.github.digitalsmile.goldberry.natives.yoga.style to
            io.github.digitalsmile.goldberry.core;
    exports io.github.digitalsmile.goldberry.natives.yoga.measure to
            io.github.digitalsmile.goldberry.core;
}
