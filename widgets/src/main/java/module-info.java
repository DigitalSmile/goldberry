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
module dev.goldberry.widgets {
    requires transitive dev.goldberry.core;

    /// JSpecify's nullness annotations, for the packages under NullAway.
    ///
    /// `static`, because they are compile-time only. `transitive`, because
    /// `@Nullable` appears on exported signatures and a consumer compiling
    /// against one has to read it (`docs/testing.md` §2).
    requires transitive static org.jspecify;

    /// The module-level furniture: the KDL registry, the stylesheets, and the
    /// three lookups a document resolves names against ([Controls],
    /// [dev.goldberry.bind.registry.ActionRegistry],
    /// [Icons], [Density]). Not widgets — an application reaches for exactly one
    /// of these to wire a window up, and then never again.
    /// Every widget module announces its node names this way, and this one
    /// consumes them -- including its own, whose `provides` the build patches
    /// into this descriptor from the `@Markup` annotations (ADR-0131).
    ///
    /// `uses` and not a hard-coded list: a second widget module is found by an
    /// application that never names it, which is the whole point.
    uses dev.goldberry.widgets.markup.WidgetCatalog;

    exports dev.goldberry.widgets;

    /// The markup contract, in a package of its own since ADR-0172: the
    /// `@Markup` annotation a widget carries, the `Inflatable` it satisfies, the
    /// `WidgetCatalog` the build writes from the two, and the `Wiring` a
    /// document is inflated against. An application names these only when it
    /// declares a widget of its own; the furniture above is what it uses to run
    /// one.
    exports dev.goldberry.widgets.markup;

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
    exports dev.goldberry.widgets.core;
    /// How an overlay or a panel arrives and leaves: [dev.goldberry.widgets.core.presence.Phase]
    /// and its `Departure`, which nine packages share (ADR-0496).
    exports dev.goldberry.widgets.core.presence;
    exports dev.goldberry.widgets.core.affix;
    exports dev.goldberry.widgets.core.canvas;

    /// §9's `web-view` as a **widget** — a page inside the window (ADR-0442).
    ///
    /// Here rather than in `widgets.shell.web` beside `WebPage`, because this one
    /// really is a widget: it has a box, it takes part in layout, and a `row`
    /// sizes it. What stays next door is the *window* form, for the platforms and
    /// sessions where a page cannot be a child — which on Linux means Wayland,
    /// permanently.
    exports dev.goldberry.widgets.core.web;
    exports dev.goldberry.widgets.core.image;

    /// §1's `qr-code` (`docs/gaps.md` G47, ADR-0391). A package of its own
    /// beside `image` for the same reason every widget has one: the cache that
    /// makes a rebuild free and the device-pixel arithmetic that keeps a module
    /// whole are parts, and a part is not constructible from outside
    /// (ADR-0065). The **encoder** is not here at all — it is
    /// [dev.goldberry.image.qr.QrEncoder] in `:core`, beside the
    /// image codecs, because a specification is not a widget.
    exports dev.goldberry.widgets.core.qrcode;

    /// `docs/core-widgets.md` §11's data widgets, built on `canvas` and the
    /// theme palette rather than on a chart engine (`content-widgets.md` §3).
    /// `sparkline` is the first and the smallest — no axes, no legend.
    exports dev.goldberry.widgets.data;
    /// The arithmetic between a series and a plot -- scales, ticks,
    /// downsampling, gaps, curves -- which every chart shares and none owns
    /// (ADR-0496).
    exports dev.goldberry.widgets.data.plot;
    exports dev.goldberry.widgets.data.sparkline;
    exports dev.goldberry.widgets.data.linechart;
    exports dev.goldberry.widgets.data.areachart;
    exports dev.goldberry.widgets.data.barchart;
    exports dev.goldberry.widgets.data.donutchart;
    exports dev.goldberry.widgets.core.scroll;
    exports dev.goldberry.widgets.text;
    exports dev.goldberry.widgets.panel;

    /// `docs/core-widgets.md` §5's `tabs` and its `tab`; the list, the panel, the
    /// close affordance and the add one are parts and stay in here (ADR-0107).
    exports dev.goldberry.widgets.panel.tabs;

    /// The rest of §5's containers. Each exports the widget an application names
    /// and keeps its parts to itself, which is the rule ADR-0065 set: a part is
    /// styleable and not constructible.
    exports dev.goldberry.widgets.panel.accordion;
    exports dev.goldberry.widgets.panel.card;
    exports dev.goldberry.widgets.panel.carousel;
    exports dev.goldberry.widgets.panel.collapse;
    exports dev.goldberry.widgets.panel.groupbox;
    exports dev.goldberry.widgets.panel.list;
    exports dev.goldberry.widgets.panel.masonry;
    exports dev.goldberry.widgets.panel.skeleton;
    exports dev.goldberry.widgets.panel.split;
    exports dev.goldberry.widgets.panel.statistic;
    exports dev.goldberry.widgets.panel.table;
    exports dev.goldberry.widgets.panel.calendar;
    exports dev.goldberry.widgets.panel.timeline;
    exports dev.goldberry.widgets.panel.tree;

    /// `docs/core-widgets.md` §4's `form` group. `text-input` is the first of
    /// it; what it is built on is **not here any more**. The editing model —
    /// [dev.goldberry.text.edit.TextEdit] and its undo stack —
    /// moved to `:core`'s text stack, because nothing in it ever named a widget
    /// and an application editing text on a `canvas` could not reach a control's
    /// package to borrow it (ADR-0285). `text-area`, `code-input` and every
    /// picker that owns a typed field read it from there now, and so can an
    /// application.
    exports dev.goldberry.widgets.form.textinput;

    /// §4's layout contract and its validation model.
    /// [dev.goldberry.widgets.form.Validator]
    /// is the rule an application writes; `field` is the label, the control slot
    /// and the message under it; `form` is what gates a submission on all of
    /// them. `field` exports [dev.goldberry.widgets.form.field.Validated]
    /// as well, which is the four questions a form asks of a field and is what
    /// lets the two live in different packages while keeping their parts to
    /// themselves (ADR-0065).
    exports dev.goldberry.widgets.form;
    exports dev.goldberry.widgets.form.field;
    exports dev.goldberry.widgets.form.form;
    exports dev.goldberry.widgets.form.codeinput;
    exports dev.goldberry.widgets.form.colorpicker;
    exports dev.goldberry.widgets.form.datepicker;
    exports dev.goldberry.widgets.form.timepicker;
    exports dev.goldberry.widgets.form.textarea;

    /// `…form.parts` is deliberately **not** exported. `text-input` and
    /// `text-area` draw the same `text-caret`, `text-selection` and `text-value`,
    /// and a part is styleable and not constructible (ADR-0065) — which has
    /// always meant package-private, because one widget owned its parts. Two
    /// widgets own these, so they are public in a package nothing can see: an
    /// application cannot build one, both widgets can, and there is one caret
    /// rather than two kept alike by hand.

    exports dev.goldberry.widgets.controls;
    exports dev.goldberry.widgets.controls.badge;
    exports dev.goldberry.widgets.controls.button;
    exports dev.goldberry.widgets.controls.checkbox;

    /// `chip` — §3's small rounded label you can choose and take away. A package
    /// of its own for the reason every control has one: its dot, its label and
    /// its × are parts, and a part is styleable and not constructible
    /// (ADR-0065, ADR-0305).
    exports dev.goldberry.widgets.controls.chip;
    exports dev.goldberry.widgets.controls.knob;

    /// `option`, which is `segmented`'s child node **and** `select`'s — one
    /// widget by §3's specification, and in a package of its own from the moment
    /// it had two callers rather than one (ADR-0141).
    exports dev.goldberry.widgets.controls.option;
    exports dev.goldberry.widgets.controls.progressbar;
    exports dev.goldberry.widgets.controls.radio;
    exports dev.goldberry.widgets.controls.segmented;

    /// `select` — the closed control. The rows are
    /// [dev.goldberry.widgets.controls.option.Option]s, so
    /// this package exports one type and hides the parts that draw the value and
    /// the chevron (ADR-0141).
    ///
    /// The **list** is not in here any more. `select-list` is a part with two
    /// owners — this control and `text-input`'s autocomplete — so it sits in
    /// `…controls.selectlist` beside the other one-widget packages, and that
    /// package is deliberately **not exported**: a part is styleable and not
    /// constructible (ADR-0065), which is the same arrangement `…form.parts` has
    /// and for the same reason. The move cost a `package` line and some imports,
    /// because a CSS type is the string a widget returns and never its package —
    /// the opposite of what it had been filed as costing (ADR-0417, ADR-0182).
    exports dev.goldberry.widgets.controls.select;
    exports dev.goldberry.widgets.controls.slider;
    exports dev.goldberry.widgets.controls.spinner;
    exports dev.goldberry.widgets.controls.toggle;

    /// `docs/core-widgets.md` §6's `nav` group — the package §11's table has
    /// named since v0.2 and which had nothing in it until `breadcrumbs`
    /// (ADR-0306). `steps` and `wizard` join it here; the separator, the `…` and
    /// the row itself are parts and stay inside.
    exports dev.goldberry.widgets.nav.breadcrumbs;
    exports dev.goldberry.widgets.nav.steps;
    exports dev.goldberry.widgets.nav.wizard;

    /// `docs/core-widgets.md` §7's `overlay` group. `hud` is the first of it and
    /// the only one that needs no popup: it floats in the window's own overlay
    /// layer ([dev.goldberry.Overlay]), where `toast` and a
    /// `dialog`'s scrim will join it, while `menu`, `tooltip` and `popover` wait
    /// for the backend popup windows §4 reserves.
    /// `dialog` — §7's modal, and
    /// [dev.goldberry.widgets.overlay.dialog.Dialogs],
    /// which is the half that shows one. A modal needs a window to cover and a
    /// widget has none, so the split is `menu`'s exactly (ADR-0106, ADR-0176).
    exports dev.goldberry.widgets.overlay.dialog;
    exports dev.goldberry.widgets.overlay.hud;

    /// `message` — §7's inline banner, and the one member of the overlay group
    /// that never floats: it is a child in somebody's column and persists until
    /// the condition it describes does. It is here because §7 is where the
    /// catalog put it, next to the `toast` it is deliberately not.
    exports dev.goldberry.widgets.overlay.message;
    exports dev.goldberry.widgets.overlay.popover;

    /// `toast` — §7's last widget, and the only one in the group whose *widget*
    /// an application never builds: it holds a
    /// [dev.goldberry.widgets.overlay.toast.ToastController]
    /// and raises values through it, because whatever raises a notification is by
    /// definition somewhere else (ADR-0177).
    exports dev.goldberry.widgets.overlay.toast;
    exports dev.goldberry.widgets.overlay.tour;

    /// `docs/core-widgets.md` §8's `menu` group: the panel, its items and its
    /// separators as widgets, plus [dev.goldberry.widgets.menu.Menus],
    /// which is the half that opens one — a widget cannot, because opening needs
    /// a `Host` (ADR-0106).
    exports dev.goldberry.widgets.menu;

    /// `docs/core-widgets.md` §9's `widget.shell`, opening with `tray-icon`.
    /// The one group whose first member is **not a widget**: the desktop's shell
    /// draws a tray menu, so there is no box, no cascade and no event to route,
    /// and what is exported is a value plus the call that shows it (ADR-0191).
    exports dev.goldberry.widgets.shell.tray;

    /// §9's `web-view`, and the **second** member of this group that is not a
    /// widget (ADR-0441).
    ///
    /// `tray-icon` is not a widget because the desktop's shell draws it. A page
    /// is not a widget because WebKit does — and for a harder reason than the
    /// tray's: `webview/webview` cannot render offscreen, and a Wayland session
    /// allows neither reparenting a foreign surface nor placing a window where a
    /// widget is, so there is no shape a page could take that would be a box on
    /// every platform this ships to. What is exported is a value and the call
    /// that opens it, exactly as next door.
    exports dev.goldberry.widgets.shell.web;
}
