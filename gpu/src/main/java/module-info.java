/// Goldberry GPU: the `canvas3d` widget and the GPU composition path.
///
/// `canvas3d` is a leaf render object -- Yoga sizes it like an image, but it
/// will own an SDL_GPU texture instead of pixels. See `docs/ARCHITECTURE.md` §12.
///
/// **Empty, and published.** This file is the whole module: M4 has not started,
/// and the artifact exists so that the coordinate an application will depend on
/// is reserved rather than invented later.
///
/// There is no `BackendWindow.gpuSurface()`, and this comment claimed there was
/// one "in the SPI from day 1" until the 2026-09-18 review read it against
/// `Backend`, whose own doc says the GPU surface "is absent from this cut and not
/// dropped: it needs a consumer before its shape can be decided, and an interface
/// designed against nothing is an interface that gets designed twice"
/// (ADR-0019). That is the position, and `canvas3d` is the consumer it is
/// waiting for.
module io.github.digitalsmile.goldberry.gpu {
    requires transitive io.github.digitalsmile.goldberry.core;

    /// JSpecify's nullness annotations, for the packages under NullAway.
    ///
    /// `static`, because they are compile-time only: a consumer's runtime module
    /// path does not need them. `transitive`, because `@Nullable` appears on
    /// exported signatures, and a consumer compiling against one has to be able
    /// to read it — `-Xlint:exports` says so in as many words
    /// (`docs/testing.md` §2).
    requires transitive static org.jspecify;
}
