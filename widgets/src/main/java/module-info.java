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

    /// JSpecify's nullness annotations, for the packages under NullAway.
    ///
    /// `static`, because they are compile-time only. `transitive`, because
    /// `@Nullable` appears on exported signatures and a consumer compiling
    /// against one has to read it (`docs/testing.md` §2).
    requires transitive static org.jspecify;

    /// The module-level furniture: the KDL registry, the stylesheets, and the
    /// three lookups a document resolves names against ([Controls],
    /// [io.github.digitalsmile.goldberry.bind.registry.ActionRegistry],
    /// [Icons], [Density]). Not widgets — an application reaches for exactly one
    /// of these to wire a window up, and then never again.
    /// Every widget module announces its node names this way, and this one
    /// consumes them -- including its own, whose `provides` the build patches
    /// into this descriptor from the `@Markup` annotations (ADR-0131).
    ///
    /// `uses` and not a hard-coded list: a second widget module is found by an
    /// application that never names it, which is the whole point.
    uses io.github.digitalsmile.goldberry.widgets.markup.WidgetCatalog;

    exports io.github.digitalsmile.goldberry.widgets;

    /// The markup contract, in a package of its own since ADR-0172: the
    /// `@Markup` annotation a widget carries, the `Inflatable` it satisfies, the
    /// `WidgetCatalog` the build writes from the two, and the `Wiring` a
    /// document is inflated against. An application names these only when it
    /// declares a widget of its own; the furniture above is what it uses to run
    /// one.
    exports io.github.digitalsmile.goldberry.widgets.markup;

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
    exports io.github.digitalsmile.goldberry.widgets.core.affix;
    exports io.github.digitalsmile.goldberry.widgets.core.canvas;

    /// `docs/core-widgets.md` §11's data widgets, built on `canvas` and the
    /// theme palette rather than on a chart engine (`content-widgets.md` §3).
    /// `sparkline` is the first and the smallest — no axes, no legend.
    exports io.github.digitalsmile.goldberry.widgets.data;
    exports io.github.digitalsmile.goldberry.widgets.data.sparkline;
    exports io.github.digitalsmile.goldberry.widgets.data.linechart;
    exports io.github.digitalsmile.goldberry.widgets.data.areachart;
    exports io.github.digitalsmile.goldberry.widgets.data.barchart;
    exports io.github.digitalsmile.goldberry.widgets.data.donutchart;
    exports io.github.digitalsmile.goldberry.widgets.core.scroll;
    exports io.github.digitalsmile.goldberry.widgets.text;
    exports io.github.digitalsmile.goldberry.widgets.panel;

    /// `docs/core-widgets.md` §5's `tabs` and its `tab`; the list, the panel, the
    /// close affordance and the add one are parts and stay in here (ADR-0107).
    exports io.github.digitalsmile.goldberry.widgets.panel.tabs;

    /// The rest of §5's containers. Each exports the widget an application names
    /// and keeps its parts to itself, which is the rule ADR-0065 set: a part is
    /// styleable and not constructible.
    exports io.github.digitalsmile.goldberry.widgets.panel.accordion;
    exports io.github.digitalsmile.goldberry.widgets.panel.card;
    exports io.github.digitalsmile.goldberry.widgets.panel.carousel;
    exports io.github.digitalsmile.goldberry.widgets.panel.collapse;
    exports io.github.digitalsmile.goldberry.widgets.panel.groupbox;
    exports io.github.digitalsmile.goldberry.widgets.panel.list;
    exports io.github.digitalsmile.goldberry.widgets.panel.masonry;
    exports io.github.digitalsmile.goldberry.widgets.panel.skeleton;
    exports io.github.digitalsmile.goldberry.widgets.panel.split;
    exports io.github.digitalsmile.goldberry.widgets.panel.statistic;
    exports io.github.digitalsmile.goldberry.widgets.panel.table;
    exports io.github.digitalsmile.goldberry.widgets.panel.calendar;
    exports io.github.digitalsmile.goldberry.widgets.panel.tree;

    /// `docs/core-widgets.md` §4's `form` group. `text-input` is the first of
    /// it; what it is built on is **not here any more**. The editing model —
    /// [io.github.digitalsmile.goldberry.text.edit.TextEdit] and its undo stack —
    /// moved to `:core`'s text stack, because nothing in it ever named a widget
    /// and an application editing text on a `canvas` could not reach a control's
    /// package to borrow it (ADR-0285). `text-area`, `code-input` and every
    /// picker that owns a typed field read it from there now, and so can an
    /// application.
    exports io.github.digitalsmile.goldberry.widgets.form.textinput;

    /// §4's layout contract and its validation model.
    /// [io.github.digitalsmile.goldberry.widgets.form.Validator]
    /// is the rule an application writes; `field` is the label, the control slot
    /// and the message under it; `form` is what gates a submission on all of
    /// them. `field` exports [io.github.digitalsmile.goldberry.widgets.form.field.Validated]
    /// as well, which is the four questions a form asks of a field and is what
    /// lets the two live in different packages while keeping their parts to
    /// themselves (ADR-0065).
    exports io.github.digitalsmile.goldberry.widgets.form;
    exports io.github.digitalsmile.goldberry.widgets.form.field;
    exports io.github.digitalsmile.goldberry.widgets.form.form;
    exports io.github.digitalsmile.goldberry.widgets.form.codeinput;
    exports io.github.digitalsmile.goldberry.widgets.form.colorpicker;
    exports io.github.digitalsmile.goldberry.widgets.form.datepicker;
    exports io.github.digitalsmile.goldberry.widgets.form.timepicker;
    exports io.github.digitalsmile.goldberry.widgets.form.textarea;

    /// `…form.parts` is deliberately **not** exported. `text-input` and
    /// `text-area` draw the same `text-caret`, `text-selection` and `text-value`,
    /// and a part is styleable and not constructible (ADR-0065) — which has
    /// always meant package-private, because one widget owned its parts. Two
    /// widgets own these, so they are public in a package nothing can see: an
    /// application cannot build one, both widgets can, and there is one caret
    /// rather than two kept alike by hand.

    exports io.github.digitalsmile.goldberry.widgets.controls;
    exports io.github.digitalsmile.goldberry.widgets.controls.badge;
    exports io.github.digitalsmile.goldberry.widgets.controls.button;
    exports io.github.digitalsmile.goldberry.widgets.controls.checkbox;
    exports io.github.digitalsmile.goldberry.widgets.controls.knob;

    /// `option`, which is `segmented`'s child node **and** `select`'s — one
    /// widget by §3's specification, and in a package of its own from the moment
    /// it had two callers rather than one (ADR-0141).
    exports io.github.digitalsmile.goldberry.widgets.controls.option;
    exports io.github.digitalsmile.goldberry.widgets.controls.progressbar;
    exports io.github.digitalsmile.goldberry.widgets.controls.radio;
    exports io.github.digitalsmile.goldberry.widgets.controls.segmented;

    /// `select` — the closed control and the list under it. The rows are
    /// [io.github.digitalsmile.goldberry.widgets.controls.option.Option]s, so
    /// this package exports one type and hides the parts that draw the value and
    /// the chevron (ADR-0141).
    exports io.github.digitalsmile.goldberry.widgets.controls.select;
    exports io.github.digitalsmile.goldberry.widgets.controls.slider;
    exports io.github.digitalsmile.goldberry.widgets.controls.spinner;
    exports io.github.digitalsmile.goldberry.widgets.controls.toggle;

    /// `docs/core-widgets.md` §7's `overlay` group. `hud` is the first of it and
    /// the only one that needs no popup: it floats in the window's own overlay
    /// layer ([io.github.digitalsmile.goldberry.Overlay]), where `toast` and a
    /// `dialog`'s scrim will join it, while `menu`, `tooltip` and `popover` wait
    /// for the backend popup windows §4 reserves.
    /// `dialog` — §7's modal, and
    /// [io.github.digitalsmile.goldberry.widgets.overlay.dialog.Dialogs],
    /// which is the half that shows one. A modal needs a window to cover and a
    /// widget has none, so the split is `menu`'s exactly (ADR-0106, ADR-0176).
    exports io.github.digitalsmile.goldberry.widgets.overlay.dialog;
    exports io.github.digitalsmile.goldberry.widgets.overlay.hud;

    /// `message` — §7's inline banner, and the one member of the overlay group
    /// that never floats: it is a child in somebody's column and persists until
    /// the condition it describes does. It is here because §7 is where the
    /// catalog put it, next to the `toast` it is deliberately not.
    exports io.github.digitalsmile.goldberry.widgets.overlay.message;
    exports io.github.digitalsmile.goldberry.widgets.overlay.popover;

    /// `toast` — §7's last widget, and the only one in the group whose *widget*
    /// an application never builds: it holds a
    /// [io.github.digitalsmile.goldberry.widgets.overlay.toast.ToastController]
    /// and raises values through it, because whatever raises a notification is by
    /// definition somewhere else (ADR-0177).
    exports io.github.digitalsmile.goldberry.widgets.overlay.toast;
    exports io.github.digitalsmile.goldberry.widgets.overlay.tour;

    /// `docs/core-widgets.md` §8's `menu` group: the panel, its items and its
    /// separators as widgets, plus [io.github.digitalsmile.goldberry.widgets.menu.Menus],
    /// which is the half that opens one — a widget cannot, because opening needs
    /// a `Host` (ADR-0106).
    exports io.github.digitalsmile.goldberry.widgets.menu;

    /// `docs/core-widgets.md` §9's `widget.shell`, opening with `tray-icon`.
    /// The one group whose first member is **not a widget**: the desktop's shell
    /// draws a tray menu, so there is no box, no cascade and no event to route,
    /// and what is exported is a value plus the call that shows it (ADR-0191).
    exports io.github.digitalsmile.goldberry.widgets.shell.tray;
}
