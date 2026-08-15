/// Goldberry core: the platform-agnostic toolkit.
///
/// Everything above the backend SPI lives here -- the three trees (widgets,
/// elements, render objects), the CSS engine, the layout and text stacks, the
/// paint pipeline, and the semantics tree. See `docs/ARCHITECTURE.md` §2.
///
/// This module will `requires` the `natives` module once the FFM bindings land
/// in M0; it deliberately does not yet, so that nothing accidentally depends on
/// native types before the wrapper boundary described in §3.1 exists.
module io.github.digitalsmile.goldberry.core {
    exports io.github.digitalsmile.goldberry;

    // The backend SPI, and the one backend that needs no platform under it.
    // `sdl3` will live here too and will be what makes this module `requires`
    // the natives module; `headless` deliberately does not, so tests of
    // everything above the SPI need no native library at all (ADR-0019).
    exports io.github.digitalsmile.goldberry.backend;
    exports io.github.digitalsmile.goldberry.backend.headless;
}
