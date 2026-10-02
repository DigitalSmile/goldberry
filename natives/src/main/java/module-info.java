/// Goldberry's native layer: hand-written FFM bindings for Blend2D, Yoga,
/// HarfBuzz, SDL3, md4c and libwebp, plus the thin owning wrappers around them.
///
/// This module exports the wrapper packages and nothing else, which is the
/// point. A raw `MemorySegment` never escapes this module, and the module graph
/// -- not a naming convention -- is what enforces it. Every `…calls` package stays
/// unexported, most wrapper packages are exported to one named reader each, and
/// `ExportedSurfaceTest` holds the descriptor below to that description rather
/// than to this comment.
///
/// This is also the module named in `--enable-native-access` (JEP 472): Java 25
/// warns on restricted native access from the unnamed module, and a later
/// release makes it an error.
///
/// `@SuppressWarnings("module")` is for the qualified exports below, and for
/// nothing else.
///
/// `exports … to dev.goldberry.core` names a module that is
/// **not on this module's compile path and cannot be** — `:core` requires
/// `:natives`, so the dependency runs the other way and javac compiles this one
/// first. It warns that the target module is not found, which under `-Werror` is
/// a build failure; the export still works, because the name is resolved at run
/// time and by the modules that read it.
///
/// The alternative was `-Xlint:-module` in the build file, which would switch the
/// lint off for every directive in this descriptor rather than for the ones
/// that need it.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@SuppressWarnings("module")
module dev.goldberry.natives {

    // A facade, not an implementation: applications choose the logging backend,
    // and one that chooses none sees nothing at all.
    requires transitive org.slf4j;

    // Logging and the start-up timeline. Not `transitive`: nothing here puts a
    // type of :common in a signature, so a consumer of :natives is not made to
    // read it.
    requires dev.goldberry.common;

    // JSpecify's nullness annotations, which appear on exported signatures --
    // `@Nullable` where a parameter takes null -- so a consumer compiling
    // against them has to be able to read them, as `-Xlint:exports` requires.
    // `static`: nothing needs them at run time. This module runs no Error Prone
    // (see its build script), so the annotations are its contract for the
    // modules that do: `:core`'s NullAway reads them.
    requires transitive static org.jspecify;

    // The wrapper packages, and only those. The `natives` package itself stays
    // unexported: NativeLibrary hands out a SymbolLookup, and a foreign type in
    // the public surface of this module is the boundary leaking by another name.
    // What is exported below traffics in Java types -- SdlWindowHandle wraps the
    // pointer, MeasureCallback wraps the stub, a pixel buffer arrives as a
    // ByteBuffer.
    //
    // Each library's wrappers are split by what they are: the owning wrappers
    // that hold a handle stay in the library's own package, beside the binding
    // class they are the only callers of, and the enums and plain values --
    // which touch no foreign memory at all -- get packages of their own.
    //
    // **Qualified, to `:core` and to nobody else.** Raw foreign memory never
    // leaves this module, *and* no type of this module appears in a signature an
    // application can read: an application paints with `paint.Path`, `Stroke`
    // and `Gradient` and lays out with the `layout` vocabulary, and the
    // translation to what is below happens in `:core`. The compiler says so,
    // rather than a convention.
    //
    // What is left unqualified is SDL's wrappers, which an application
    // legitimately names -- a `BackendWindow` handed to a popup, a tray, a
    // cursor.
    exports dev.goldberry.natives.blend2d to
            dev.goldberry.core;
    exports dev.goldberry.natives.blend2d.enums to
            dev.goldberry.core;
    exports dev.goldberry.natives.blend2d.error to
            dev.goldberry.core;
    exports dev.goldberry.natives.harfbuzz to
            dev.goldberry.core;
    exports dev.goldberry.natives.harfbuzz.enums to
            dev.goldberry.core;
    exports dev.goldberry.natives.sdl;
    exports dev.goldberry.natives.sdl.dialog;
    exports dev.goldberry.natives.sdl.event;
    exports dev.goldberry.natives.sdl.window;
    exports dev.goldberry.natives.sdl.desktop;

    /// GLib's logging hooks, exported to `:core` alone.
    ///
    /// Qualified like Yoga's and Blend2D's wrappers, and for the same reason: an
    /// application configures the logger names, which are
    /// `:common`'s to define, and never names a type of this module. The one
    /// caller is the SDL backend, which installs the bridge immediately before
    /// it does either of the two things that load GLib — creating a tray, and
    /// opening a page.
    exports dev.goldberry.natives.glib to
            dev.goldberry.core;

    // What the desktop says that SDL does not ask it — reduce-motion, through
    // the settings portal, `user32` and `NSWorkspace`. Its own package beside
    // `sdl.desktop` rather than inside it, because nothing here is SDL's: these
    // are read-only queries against libraries the process already has, and each
    // one answers "the desktop does not say" when it cannot ask.
    exports dev.goldberry.natives.desktop;

    // Notifications and the dock badge, and the macOS menu bar: bindings
    // against libdbus, libobjc and shell32 loaded at run time. To `:core`,
    // which puts the toolkit's own words on them.
    exports dev.goldberry.natives.desktop.notify to
            dev.goldberry.core;
    exports dev.goldberry.natives.desktop.macos to
            dev.goldberry.core;

    /// What this build of the platform layer can actually do.
    ///
    /// Qualified to `:core`, like Yoga's and Blend2D's wrappers and for the same
    /// reason: an application asks `Goldberry.capabilities()` and reads the
    /// toolkit's own `Capability`, so no type of this module appears in a
    /// signature it can name. What crosses here is an enum of five constants and
    /// an `int` behind it.
    exports dev.goldberry.natives.platform to
            dev.goldberry.core;
    /// md4c, exported to `:html` and to nobody else.
    ///
    /// Markdown is not part of the toolkit's own surface — it is `goldberry-html`'s
    /// dependency, an optional module an application opts into — so the wrapper
    /// reaches that module and stops there. `:core` cannot see it either: nothing
    /// in the widget catalogue, the cascade or the text stack knows what Markdown
    /// is.
    ///
    /// What crosses is values: a `String` in, and records out that carry no foreign
    /// memory and no struct layout. The event stream is encoded in C and read once,
    /// so the seven detail structs md4c hands its callbacks are never modelled in
    /// Java at all.
    exports dev.goldberry.natives.md4c to
            dev.goldberry.html;
    exports dev.goldberry.natives.md4c.enums to
            dev.goldberry.html;
    /// SDL's audio streams, exported to `:media` and to nobody else.
    ///
    /// The toolkit plays no audio itself, and SDL, already linked in, is a
    /// second audio library the media engine does not have to ship. What crosses
    /// is a direct `ByteBuffer` in and frame counts out.
    exports dev.goldberry.natives.sdl.audio to
            dev.goldberry.media;
    // SDL_GPU: to :core, which claims a window and presents through it, and to
    // :gpu, whose public API is built on it. The wrappers carry no
    // MemorySegment.
    exports dev.goldberry.natives.sdl.gpu to
            dev.goldberry.core,
            dev.goldberry.gpu;
    // SDL_GPU's enumerations: tables of C constants that touch no foreign
    // memory, split from the wrappers that hold a handle as `blend2d.enums` is.
    // The same two readers.
    exports dev.goldberry.natives.sdl.gpu.enums to
            dev.goldberry.core,
            dev.goldberry.gpu;
    /// libwebp's decoder, exported to `:core` alone.
    ///
    /// Blend2D's and Yoga's seal exactly, and for their reason: what an
    /// application calls is `Image.decode`, which names no type of this module.
    /// What crosses here is a `ByteBuffer` in and an `int[]` out — the decoded
    /// buffer is libwebp's for the length of one call and is freed before it
    /// returns, so there is no lifetime to hand over.
    exports dev.goldberry.natives.webp to
            dev.goldberry.core;
    /// `webview/webview`, behind the `web-view` widget.
    ///
    /// Qualified to `:core` like Blend2D's and Yoga's, and for their reason: an
    /// application names `WebPage` and `WebViews`, which are `:widgets`' types,
    /// and no type of this module appears in a signature it can read.
    ///
    /// The one wrapper here whose library is **not** `libgoldberry`. WebKitGTK
    /// cannot be linked into the toolkit's own library without making GTK and
    /// WebKit load-time dependencies of every application on Linux, so
    /// `libgoldberry-webview` is built beside it, linked into nothing, and opened
    /// on demand — which is why this is also the only wrapper whose absence is an
    /// ordinary state rather than a broken installation.
    exports dev.goldberry.natives.webview to
            dev.goldberry.core;
    exports dev.goldberry.natives.yoga to
            dev.goldberry.core;
    exports dev.goldberry.natives.yoga.style to
            dev.goldberry.core;
    exports dev.goldberry.natives.yoga.measure to
            dev.goldberry.core;
}
