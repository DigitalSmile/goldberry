/// Goldberry core: the platform-agnostic toolkit.
///
/// Everything above the backend SPI lives here -- the three trees (widgets,
/// elements, render objects), the CSS engine, the layout and text stacks, the
/// paint pipeline, and the semantics tree. See `docs/ARCHITECTURE.md` §2.
///
/// The `natives` module is required for the `sdl3` backend. It exports only its
/// wrapper packages, so nothing here can reach a raw `MemorySegment` even by
/// accident -- the boundary in §3.1 is the module graph, not a convention.
module io.github.digitalsmile.goldberry.core {
    requires transitive io.github.digitalsmile.goldberry.natives;
    requires org.slf4j;

    exports io.github.digitalsmile.goldberry;

    // The fonts and icons that ship in this jar (§6.1, ADR-0033). Exported
    // because an application choosing a font source, or registering an icon
    // pack, needs to name what it is replacing.
    exports io.github.digitalsmile.goldberry.assets;

    // Where Yoga's layout meets Blend2D's painting (ADR-0033). Not the widget
    // model -- that is still open (ADR-0004) -- but the join between the two
    // engines, so the seam is exercised before a widget tree lands on it.
    exports io.github.digitalsmile.goldberry.layout;

    // The backend SPI, and the one backend that needs no platform under it.
    // `sdl3` will live here too and will be what makes this module `requires`
    // the natives module; `headless` deliberately does not, so tests of
    // everything above the SPI need no native library at all (ADR-0019).
    exports io.github.digitalsmile.goldberry.backend;
    exports io.github.digitalsmile.goldberry.backend.headless;
    exports io.github.digitalsmile.goldberry.backend.sdl3;
}
