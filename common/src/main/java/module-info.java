/// Goldberry common: what every module needs and no layer owns.
///
/// The lowest module in the graph. `:natives` requires it, `:core` requires it,
/// and nothing here requires either of them — which is the whole reason it
/// exists: logging is used by the FFM bindings and by the widget catalog alike,
/// and `:natives` is the lower of those two, so before this module the shared
/// code had to live *inside* the native layer and be exported from it
/// ([ADR-0174](../../../../book/src/adr/0174-what-both-halves-need-is-its-own-module.md)).
///
/// The bar for adding something here is that **both** halves need it and neither
/// owns it. A type that needs `:natives` belongs in `:natives`; one that only the
/// toolkit above the SPI needs belongs in `:core`. `NativePlatform` looks like a
/// candidate and is not one: `classifier()` and `libraryFileName()` exist to pick
/// a native artifact.
module io.github.digitalsmile.goldberry.common {

    /// `transitive`, because a consumer that reads Goldberry's logs configures
    /// SLF4J itself -- the toolkit binds no implementation, by design (ADR-0023).
    requires transitive org.slf4j;

    /// JSpecify's nullness annotations, for the packages that have opted into
    /// NullAway (`docs/testing.md` §2).
    ///
    /// **`static`**, because they are compile-time only: a consumer's runtime
    /// module path does not need them, and requiring them non-statically would
    /// put an annotation jar on every application's module path to describe
    /// something the compiler has already checked.
    requires static org.jspecify;

    /// Where every Goldberry logger comes from, and the start-up timeline they
    /// report. Both are cross-cutting diagnostics: `NativeLibrary` marks the
    /// moment `libgoldberry` was mapped, and a widget marks nothing but logs
    /// through the same factory, and the ordering between them is what
    /// `Logs` exists to guarantee.
    exports io.github.digitalsmile.goldberry.log;
}
