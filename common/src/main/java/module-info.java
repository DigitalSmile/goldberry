/// Goldberry common: what every module needs and no layer owns.
///
/// The lowest module in the graph. `:natives` requires it, `:core` requires it,
/// and nothing here requires either of them — which is the whole reason it
/// exists: logging is used by the FFM bindings and by the widget catalog alike,
/// and `:natives` is the lower of those two, so before this module the shared
/// code had to live *inside* the native layer and be exported from it
/// (ADR-0174).
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
    /// module path does not need them. **`transitive`**, because `@Nullable`
    /// appears on exported signatures, and a consumer compiling against one has
    /// to be able to read it — which `-Xlint:exports` requires in as many words.
    requires transitive static org.jspecify;

    /// Where every Goldberry logger comes from, and the start-up timeline they
    /// report. Both are cross-cutting diagnostics: `NativeLibrary` marks the
    /// moment `libgoldberry` was mapped, and a widget marks nothing but logs
    /// through the same factory, and the ordering between them is what
    /// `Logs` exists to guarantee.
    exports io.github.digitalsmile.goldberry.log;

    /// Where a message raised by a native library becomes an SLF4J event.
    ///
    /// Unqualified, unlike most of what `:natives` exports, and deliberately:
    /// `NativeLogBridge.ROOT` is the logger name an application puts in its
    /// `logback.xml`, and a constant nobody can read is a string that gets
    /// copied. The bridges that call it are in `:natives`; what is here is the
    /// destination and the naming convention alone.
    exports io.github.digitalsmile.goldberry.log.bridge;
}
