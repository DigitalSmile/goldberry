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

    // The CSS engine (ADR-0049): stylesheets, the cascade, and the ComputedStyle
    // a render object is styled by (§8, ADR-0004). Exported because loading a
    // stylesheet and choosing a theme are things an application does.
    exports io.github.digitalsmile.goldberry.css;

    // KDL 2.0 markup and the inflater registry (§9, ADR-0051). Exported
    // because an application registers its own widgets in the same registry the
    // built-ins use.
    exports io.github.digitalsmile.goldberry.kdl;

    // Hot reload of stylesheets and markup (§1, §8, ADR-0051).
    exports io.github.digitalsmile.goldberry.reload;

    // The widget and element trees (ADR-0004, ADR-0052). The declarative layer
    // an application writes in, and the persistent tree the cascade, focus and
    // state hang off.
    exports io.github.digitalsmile.goldberry.widget;

    // Observable values and the paths markup binds to (§9, ADR-0062). Exported
    // because the properties are the application's: it declares them, writes to
    // them, and registers the paths a markup file may name.
    exports io.github.digitalsmile.goldberry.bind;

    // Pointer input: hit testing against the painted frame, and the dispatch
    // that turns it into events, pseudo-classes and focus (§7, ADR-0054).
    exports io.github.digitalsmile.goldberry.input;

    // The frame clock, the three easing curves, and the per-node animation
    // overlay CSS transitions run through (design-system.md §1.7, ADR-0067).
    // Exported because an application supplies the clock -- a test drives a
    // virtual one so a golden image can snapshot a mid-animation frame -- and
    // because `Easing` is named by a `transition` a stylesheet writes.
    exports io.github.digitalsmile.goldberry.motion;

    // Where Yoga's layout meets Blend2D's painting (ADR-0033). Not the widget
    // model -- that is still open (ADR-0004) -- but the join between the two
    // engines, so the seam is exercised before a widget tree lands on it.
    exports io.github.digitalsmile.goldberry.layout;

    // Icons: the bundled Lucide set and the SVG path reader that gets it onto a
    // Blend2D path (ADR-0043). Separate from `text` because an icon shares
    // nothing with the font chain except the context it is drawn into.
    exports io.github.digitalsmile.goldberry.icon;

    // Where HarfBuzz's shaping meets Blend2D's rasterizer (ADR-0034). The two
    // libraries know nothing of each other, and this package is what holds them
    // to the one thing they must agree on: the units a glyph position is in.
    exports io.github.digitalsmile.goldberry.text;

    // The backend SPI, and the one backend that needs no platform under it.
    // `sdl3` will live here too and will be what makes this module `requires`
    // the natives module; `headless` deliberately does not, so tests of
    // everything above the SPI need no native library at all (ADR-0019).
    exports io.github.digitalsmile.goldberry.backend;
    exports io.github.digitalsmile.goldberry.backend.headless;
    exports io.github.digitalsmile.goldberry.backend.sdl3;
}
