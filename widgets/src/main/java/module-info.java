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

    /// The module-level furniture: the KDL registry, the stylesheets, and the
    /// three lookups a document resolves names against ([Controls], [Actions],
    /// [Icons], [Density]). Not widgets — an application reaches for exactly one
    /// of these to wire a window up, and then never again.
    exports io.github.digitalsmile.goldberry.widgets;

    /// `docs/core-widgets.md` §3's `controls` group, **one package per control**.
    ///
    /// One module, packages by group — a change of mind about half of ADR-0014,
    /// recorded as ADR-0091: the single *module* argument held, the single
    /// *package* one did not survive the catalog reaching thirty types with
    /// `form`, `panel`, `nav`, `overlay` and `collection` still to come.
    ///
    /// The per-control split is what makes ADR-0065's rule a boundary rather
    /// than a convention. A part is CSS-selectable and deliberately not
    /// constructible, which it expresses by being package-private — and with one
    /// package that meant "visible to the whole catalog", so nothing stopped a
    /// checkbox reaching into a slider's thumb. A `slider-thumb` is now invisible
    /// outside `…controls.slider`, enforced by the compiler.
    ///
    /// Each line below therefore exports exactly one public widget (two for
    /// `radio` and for `segmented`, each of which is a set and its members) and
    /// hides its parts. `…controls` itself carries only [Scale],
    /// which `slider` and a future `fader` share.
    /// `core-widgets.md` §1, §2 and §5's structural widgets — `row`, `column`,
    /// `spacer`, `text`, `panel` — and the registry that builds them. They were
    /// nested records inside a `Widgets` class in `:core` until ADR-0092: the
    /// engines needed something to prove the widget tree against before there was
    /// a catalog, and once there was one, `:core` was shipping five widgets it
    /// had no other use for.
    exports io.github.digitalsmile.goldberry.widgets.core;
    exports io.github.digitalsmile.goldberry.widgets.text;
    exports io.github.digitalsmile.goldberry.widgets.panel;

    /// `docs/core-widgets.md` §5's `tabs` and its `tab`; the list, the panel, the
    /// close affordance and the add one are parts and stay in here (ADR-0107).
    exports io.github.digitalsmile.goldberry.widgets.panel.tabs;

    exports io.github.digitalsmile.goldberry.widgets.controls;
    exports io.github.digitalsmile.goldberry.widgets.controls.badge;
    exports io.github.digitalsmile.goldberry.widgets.controls.button;
    exports io.github.digitalsmile.goldberry.widgets.controls.checkbox;
    exports io.github.digitalsmile.goldberry.widgets.controls.knob;
    exports io.github.digitalsmile.goldberry.widgets.controls.progressbar;
    exports io.github.digitalsmile.goldberry.widgets.controls.radio;
    exports io.github.digitalsmile.goldberry.widgets.controls.segmented;
    exports io.github.digitalsmile.goldberry.widgets.controls.slider;
    exports io.github.digitalsmile.goldberry.widgets.controls.spinner;
    exports io.github.digitalsmile.goldberry.widgets.controls.toggle;

    /// `docs/core-widgets.md` §7's `overlay` group. `hud` is the first of it and
    /// the only one that needs no popup: it floats in the window's own overlay
    /// layer ([io.github.digitalsmile.goldberry.Overlay]), where `toast` and a
    /// `dialog`'s scrim will join it, while `menu`, `tooltip` and `popover` wait
    /// for the backend popup windows §4 reserves.
    exports io.github.digitalsmile.goldberry.widgets.overlay.hud;
    exports io.github.digitalsmile.goldberry.widgets.overlay.popover;

    /// `docs/core-widgets.md` §8's `menu` group: the panel, its items and its
    /// separators as widgets, plus [io.github.digitalsmile.goldberry.widgets.menu.Menus],
    /// which is the half that opens one — a widget cannot, because opening needs
    /// a `Host` (ADR-0106).
    exports io.github.digitalsmile.goldberry.widgets.menu;
}
