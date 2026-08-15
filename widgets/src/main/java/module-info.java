/// The Goldberry widget catalog: controls, containers, menus, and charts.
///
/// Everything in `docs/ARCHITECTURE.md` §11 above the `core` primitives lives
/// here — `button`, `checkbox`, `select`, `tabs`, `dialog`, `menubar`, and the
/// canvas-based chart widgets that were previously a separate `charts` module
/// (ADR-0014).
///
/// It also holds the widget showcase, which is not merely a demo: per §14 it is
/// the visual regression corpus, so a widget with no screen here is a widget
/// with no pixel coverage. Populated from M2.
module io.github.digitalsmile.goldberry.widgets {
    requires transitive io.github.digitalsmile.goldberry.core;
}
