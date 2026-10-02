/// The Goldberry widget catalogue: controls, containers, navigation, overlays,
/// menus and charts.
///
/// Everything above `:core`'s primitives lives here — `button`, `checkbox`,
/// `select`, `tabs`, `dialog`, `menubar`, and the chart widgets drawn on a
/// canvas. An application wires a window up once, with `Controls` for the
/// stylesheets and `Widgets.inflater(icons, model)` for the markup, and then
/// names widgets in KDL or builds them in Java.
///
/// Read more: [The catalogue](https://goldberry.dev/docs/components/index.html).
module dev.goldberry.widgets {
    requires transitive dev.goldberry.core;

    /// JSpecify's nullness annotations, for the packages under NullAway.
    ///
    /// `static`, because they are compile-time only. `transitive`, because
    /// `@Nullable` appears on exported signatures and a consumer compiling
    /// against one has to read it.
    requires transitive static org.jspecify;

    /// The module-level furniture: the KDL registry, the stylesheets, and the
    /// three lookups a document resolves names against ([Controls],
    /// [dev.goldberry.bind.registry.ActionRegistry],
    /// [Icons], [Density]). Not widgets — an application reaches for exactly one
    /// of these to wire a window up, and then never again.
    /// Every widget module announces its node names this way, and this one
    /// consumes them -- including its own, whose `provides` the build writes
    /// into this descriptor from the `@Markup` annotations.
    ///
    /// `uses` and not a hard-coded list: a second widget module is found by an
    /// application that never names it, which is the whole point.
    uses dev.goldberry.widgets.markup.WidgetCatalog;

    exports dev.goldberry.widgets;

    /// The markup contract, in a package of its own: the `@Markup` annotation a
    /// widget carries, the `Inflatable` it satisfies, the `WidgetCatalog` the
    /// build writes from the two, and the `Wiring` a document is inflated
    /// against. An application names these only when it declares a widget of
    /// its own; the furniture above is what it uses to run one.
    exports dev.goldberry.widgets.markup;

    /// The structural widgets — `row`, `column`, `spacer`, `text`, `panel` —
    /// and the registry that builds them. They are widgets like any other,
    /// kept apart from `:core` so that the engines have nothing to ship that
    /// only a catalogue needs.
    exports dev.goldberry.widgets.core;
    /// How an overlay or a panel arrives and leaves: [dev.goldberry.widgets.core.presence.Phase]
    /// and its `Departure`, which nine packages share.
    exports dev.goldberry.widgets.core.presence;
    exports dev.goldberry.widgets.core.affix;
    exports dev.goldberry.widgets.core.canvas;

    /// `web-view` as a **widget** — a page inside the window.
    ///
    /// Here rather than in `widgets.shell.web` beside `WebPage`, because this one
    /// really is a widget: it has a box, it takes part in layout, and a `row`
    /// sizes it. What stays next door is the *window* form, for the platforms and
    /// sessions where a page cannot be a child — which on Linux means Wayland,
    /// permanently.
    exports dev.goldberry.widgets.core.web;
    exports dev.goldberry.widgets.core.image;

    /// `qr-code`. A package of its own beside `image` for the same reason every
    /// widget has one: the cache that makes a rebuild free and the device-pixel
    /// arithmetic that keeps a module whole are parts, and a part is not
    /// constructible from outside. The **encoder** is not here at all — it is
    /// [dev.goldberry.image.qr.QrEncoder] in `:core`, beside the
    /// image codecs, because a specification is not a widget.
    exports dev.goldberry.widgets.core.qrcode;

    /// `icon`: one icon on its own, sized by the stylesheet. Its two parts,
    /// decorative and named, are styleable and not constructible.
    exports dev.goldberry.widgets.core.icon;

    /// The chart widgets, built on `canvas` and the theme palette rather than
    /// on a chart engine. `sparkline` is the smallest — no axes, no legend.
    exports dev.goldberry.widgets.data;
    /// The arithmetic between a series and a plot -- scales, ticks,
    /// downsampling, gaps, curves -- which every chart shares and none owns.
    exports dev.goldberry.widgets.data.plot;
    exports dev.goldberry.widgets.data.sparkline;
    exports dev.goldberry.widgets.data.linechart;
    exports dev.goldberry.widgets.data.areachart;
    exports dev.goldberry.widgets.data.barchart;
    exports dev.goldberry.widgets.data.donutchart;
    exports dev.goldberry.widgets.core.scroll;
    exports dev.goldberry.widgets.text;
    exports dev.goldberry.widgets.panel;

    /// `tabs` and its `tab`; the list, the panel, the close affordance and the
    /// add one are parts and stay in here.
    exports dev.goldberry.widgets.panel.tabs;

    /// The rest of the containers. Each exports the widget an application names
    /// and keeps its parts to itself: a part is styleable from CSS and not
    /// constructible from outside its package.
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

    /// The `form` group's `text-input`. What it is built on is not here: the
    /// editing model — [dev.goldberry.text.edit.TextEdit] and its undo
    /// stack — lives in `:core`'s text stack, because nothing in it names a
    /// widget and an application editing text on a `canvas` needs it too.
    /// `text-area`, `code-input` and every picker that owns a typed field read
    /// it from there, and so can an application.
    exports dev.goldberry.widgets.form.textinput;

    /// The form group's layout contract and its validation model.
    /// [dev.goldberry.widgets.form.Validator]
    /// is the rule an application writes; `field` is the label, the control slot
    /// and the message under it; `form` is what gates a submission on all of
    /// them. `field` exports [dev.goldberry.widgets.form.field.Validated]
    /// as well, which is the four questions a form asks of a field and is what
    /// lets the two live in different packages while keeping their parts to
    /// themselves.
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
    /// and a part is styleable and not constructible — which has always meant
    /// package-private, because one widget owned its parts. Two widgets own
    /// these, so they are public in a package nothing can see: an application
    /// cannot build one, both widgets can, and there is one caret rather than
    /// two kept alike by hand.

    /// The `controls` group, **one package per control**.
    ///
    /// A part — a slider's thumb, a checkbox's tick — is styleable from CSS and
    /// deliberately not constructible, which it expresses by being
    /// package-private. One package per control makes that a boundary the
    /// compiler holds rather than a convention: a `slider-thumb` is invisible
    /// outside `…controls.slider`, so nothing stops short of the compiler when a
    /// checkbox reaches for a slider's thumb.
    ///
    /// Each line below therefore exports exactly one public widget (two for
    /// `radio` and for `segmented`, each of which is a set and its members) and
    /// hides its parts. `…controls` itself carries only [Scale], the curve
    /// between a value and a position, which `slider` and `knob` share.
    exports dev.goldberry.widgets.controls;
    exports dev.goldberry.widgets.controls.badge;
    exports dev.goldberry.widgets.controls.button;
    exports dev.goldberry.widgets.controls.checkbox;

    /// `chip` — a small rounded label you can choose and take away. A package
    /// of its own for the reason every control has one: its dot, its label and
    /// its × are parts, and a part is styleable and not constructible.
    exports dev.goldberry.widgets.controls.chip;
    exports dev.goldberry.widgets.controls.knob;

    /// `option`, which is `segmented`'s child node **and** `select`'s — one
    /// widget, in a package of its own because it has two callers.
    exports dev.goldberry.widgets.controls.option;
    exports dev.goldberry.widgets.controls.pressable;
    exports dev.goldberry.widgets.controls.progressbar;
    exports dev.goldberry.widgets.controls.radio;
    exports dev.goldberry.widgets.controls.segmented;

    /// `select` — the closed control. The rows are
    /// [dev.goldberry.widgets.controls.option.Option]s, so
    /// this package exports one type and hides the parts that draw the value and
    /// the chevron.
    ///
    /// The **list** is not in here. `select-list` is a part with two owners —
    /// this control and `text-input`'s suggestions — so it sits in
    /// `…controls.selectlist` beside the other one-widget packages, and that
    /// package is deliberately **not exported**: a part is styleable and not
    /// constructible, which is the same arrangement `…form.parts` has and for
    /// the same reason. A CSS type is the string a widget returns and never its
    /// package, so the list's package does not show in a stylesheet.
    exports dev.goldberry.widgets.controls.select;
    exports dev.goldberry.widgets.controls.slider;
    exports dev.goldberry.widgets.controls.spinner;
    exports dev.goldberry.widgets.controls.toggle;

    /// The `nav` group: `breadcrumbs`, `steps` and `wizard`. The separator, the
    /// `…` and the row itself are parts and stay inside.
    exports dev.goldberry.widgets.nav.breadcrumbs;
    exports dev.goldberry.widgets.nav.steps;
    exports dev.goldberry.widgets.nav.wizard;

    /// The `overlay` group. `hud` needs no popup: it floats in the window's own
    /// overlay layer ([dev.goldberry.Overlay]), where `toast` and a
    /// `dialog`'s scrim join it, while `menu`, `tooltip` and `popover` open
    /// backend popup windows.
    /// `dialog` is the modal, and
    /// [dev.goldberry.widgets.overlay.dialog.Dialogs]
    /// is the half that shows one: a modal needs a window to cover and a
    /// widget has none, so the split is `menu`'s exactly.
    exports dev.goldberry.widgets.overlay.dialog;
    exports dev.goldberry.widgets.overlay.hud;

    /// `message` — the inline banner, and the one member of the overlay group
    /// that never floats: it is a child in somebody's column and persists until
    /// the condition it describes does. It is here because it belongs with the
    /// overlays, next to the `toast` it is deliberately not.
    exports dev.goldberry.widgets.overlay.message;
    exports dev.goldberry.widgets.overlay.popover;

    /// `toast` — the only widget in the group an application never builds: it
    /// holds a
    /// [dev.goldberry.widgets.overlay.toast.ToastController]
    /// and raises values through it, because whatever raises a notification is by
    /// definition somewhere else.
    exports dev.goldberry.widgets.overlay.toast;
    exports dev.goldberry.widgets.overlay.tour;

    /// The `menu` group: the panel, its items and its separators as widgets,
    /// plus [dev.goldberry.widgets.menu.Menus], which is the half that opens one
    /// — a widget cannot, because opening needs a `Host`.
    exports dev.goldberry.widgets.menu;

    /// The shell group, opening with `tray-icon`. The one group whose first
    /// member is **not a widget**: the desktop's shell draws a tray menu, so
    /// there is no box, no cascade and no event to route, and what is exported
    /// is a value plus the call that shows it.
    exports dev.goldberry.widgets.shell.tray;

    /// `web-view` as a window, and the **second** member of this group that is
    /// not a widget.
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
