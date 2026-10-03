package dev.goldberry.example.ui.styling;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.example.ShowcaseModel;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.icon.Icon;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Shortcut;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.badge.Badge;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.controls.checkbox.Checkbox;
import dev.goldberry.widgets.controls.chip.Chip;
import dev.goldberry.widgets.controls.option.Option;
import dev.goldberry.widgets.controls.progressbar.Progress;
import dev.goldberry.widgets.controls.segmented.Segmented;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.icon.IconView;
import dev.goldberry.widgets.core.scroll.Scroll;
import dev.goldberry.widgets.core.scroll.ScrollAxis;
import dev.goldberry.widgets.form.textinput.TextInput;
import dev.goldberry.widgets.panel.Panel;
import dev.goldberry.widgets.text.Text;

/// The Design system screen's cards about behaviour and rules: principles,
/// icons, motion, states, focus, the keyboard, density, scroll bars, metrics,
/// accessibility and governance.
///
/// Read more: [The design system](https://goldberry.dev/docs/guide/design-system.html).
final class DesignCards {

    private static final String CHAPTER = "guide/design-system";

    /// The three motion tokens, by the class a swatch wears.
    static final List<String> DURATIONS = List.of("fast", "base", "overlay");

    private DesignCards() {}

    static Widget principles() {
        return new ShowcaseCard(
                        "design-principles",
                        "Principles",
                        "Five rules every widget is drawn to. When a screen needs a value no token names, the"
                                + " system grows a token rather than the screen improvising one.",
                        DocLink.to(CHAPTER, "principles"))
                .of(Demo.column(
                        "principles-list",
                        new Text("1. Desktop only: pointer and keyboard first, 32 by 32 targets."),
                        new Text("2. Opaque first: frost is an enhancement, and it is not built."),
                        new Text("3. Token or extend: no improvised values."),
                        new Text("4. Keyboard complete, with a focus ring that is always legible."),
                        new Text("5. Deterministic: the same markup renders the same bytes everywhere.")));
    }

    static Widget icons(Icon plus) {
        return new ShowcaseCard(
                        "design-icons",
                        "Icons",
                        "Lucide on a 24 by 24 grid with a 2 px round stroke, drawn at 16, 20 or 24 and tinted by"
                                + " color like text. An icon-only control carries an accessible name: hover the"
                                + " round button for it.",
                        DocLink.to(CHAPTER, "icons"))
                .of(
                        Demo.row(
                                "icon-sizes",
                                new IconView("palette").styled("icon-16"),
                                new IconView("palette").styled("icon-20"),
                                new IconView("palette").styled("icon-24"),
                                new IconView("circle-check").styled("icon-24", "icon-success"),
                                new IconView("triangle-alert").styled("icon-24", "icon-warning"),
                                new IconView("octagon-x").styled("icon-24", "icon-danger")),
                        Demo.row(
                                "icon-buttons",
                                new Button("New", plus, () -> {}, false, Attributes.NONE.id("icon-labelled")),
                                new Button(
                                        "",
                                        plus,
                                        () -> {},
                                        false,
                                        Attributes.NONE
                                                .id("icon-only")
                                                .name("Add a waypoint")
                                                .tooltip("Add a waypoint"))));
    }

    static Widget states() {
        return new ShowcaseCard(
                        "design-states",
                        "States",
                        "Every control draws rest, hover, active, focus and disabled, and checked, mixed or invalid"
                                + " where they apply. Hover moves the surface, never the text; disabled is 45%"
                                + " opacity, so a disabled danger button still reads as danger.",
                        DocLink.to(CHAPTER, "states"))
                .of(
                        Demo.row(
                                "state-buttons",
                                new Button("Hover, press", () -> {}).id("state-rest"),
                                new Button("Disabled", () -> {}).disabled(true).id("state-disabled"),
                                new Button("Danger", () -> {}).styled("danger").id("state-danger"),
                                new Button("Danger, disabled", () -> {})
                                        .styled("danger")
                                        .disabled(true)
                                        .id("state-danger-disabled")),
                        Demo.row(
                                "state-checks",
                                new Checkbox("Checked", Checkbox.Value.CHECKED).id("state-checked"),
                                new Checkbox("Mixed", Checkbox.Value.MIXED).id("state-mixed"),
                                new Checkbox("Off", Checkbox.Value.UNCHECKED).id("state-off"),
                                new Checkbox(
                                        "Disabled",
                                        Checkbox.Value.CHECKED,
                                        null,
                                        null,
                                        true,
                                        Attributes.NONE.id("state-check-disabled"))));
    }

    static Widget focus() {
        return new ShowcaseCard(
                        "design-focus",
                        "The focus ring",
                        "One focus owner per window. The ring is 2 px of --gb-focus, 2 px outside the control and"
                                + " following its radius, and only keyboard focus draws it. Press Tab to walk it"
                                + " through these; a click does not draw it.",
                        DocLink.to(CHAPTER, "focus"))
                .of(
                        Demo.row(
                                "focus-controls",
                                new Button("Button", () -> {}).id("focus-button"),
                                new Live.Check("Checkbox", false, "focus-check"),
                                new Live.Switch("Toggle", false, "focus-toggle")),
                        new TextInput("A field", value -> {}).id("focus-field"),
                        new Segmented("one", value -> {}, new Option("one", "One"), new Option("two", "Two"))
                                .id("focus-segmented"),
                        Demo.caption("The segmented bar is one Tab stop; the arrow keys move inside it."));
    }

    static Widget keyboard() {
        return new ShowcaseCard(
                        "design-keyboard",
                        "Keyboard conventions",
                        "Accelerators are written against the desktop's primary modifier, Cmd on macOS and Ctrl"
                                + " elsewhere. A button presses on Space and Enter, a checkbox ticks on Space only,"
                                + " and Tab order is document order.",
                        DocLink.to(CHAPTER, "keyboard-conventions"))
                .of(
                        Demo.code("Shortcut.primary(Key.S) is " + Shortcut.primary(Key.S) + " here"),
                        Demo.code("\"Primary+Shift+Z\" is " + Shortcut.of("Primary+Shift+Z") + " here"),
                        new Live.Counter("keyboard-button", "Space or Enter", List.of()),
                        new Live.Check("Space ticks me, Enter does not", false, "keyboard-check"));
    }

    static Widget density(ShowcaseModel model, ShowcaseModel.Actions actions) {
        return new ShowcaseCard(
                        "design-density",
                        "Density",
                        "Compact density is a handful of height tokens on :root: controls 28 instead of 32, list"
                                + " rows 26, table headers 30. The 16 px glyph inside a checkbox does not shrink;"
                                + " the margin round it does.",
                        DocLink.to(CHAPTER, "density"))
                .of(
                        Demo.row(
                                "density-sample",
                                new Button("Switch the density", actions::toggleDensity).id("design-density-switch"),
                                new Live.Choice(List.of("Shire", "Bree", "Rivendell"), "density-select"),
                                new Live.Check("A checkbox", true, "density-check")),
                        new TextInput("A field, as tall as a button", value -> {}).id("density-field"),
                        new Text(
                                ThemeCards.describe(model),
                                Attributes.NONE.id("density-now").classes("caption")));
    }

    static Widget scrollbars(ShowcaseModel.Actions actions) {
        var stops = new ArrayList<Widget>();
        for (var stop : List.of(
                "Hobbiton",
                "Bywater",
                "Woodhall",
                "Bucklebury",
                "Bree",
                "Weathertop",
                "Rivendell",
                "Moria",
                "Lothlórien",
                "Amon Hen",
                "Edoras",
                "Minas Tirith")) {
            stops.add(new Chip(stop));
        }
        return new ShowcaseCard(
                        "design-scrollbars",
                        "Scroll bars",
                        "Overlay by default: a 6 px thumb over the content that widens to 10 on hover, with no"
                                + " gutter. Always-shown bars reserve a 12 px gutter with an 8 px thumb. Switch"
                                + " them and scroll the row sideways.",
                        DocLink.to(CHAPTER, "scrollbars"))
                .of(
                        Demo.row(
                                "scrollbars-actions",
                                new Button("Switch the scroll bars", actions::toggleScrollbars)
                                        .id("design-scrollbars-switch")),
                        new Scroll(
                                List.of(new Row(stops, Attributes.NONE.classes("scroll-stops"))),
                                ScrollAxis.HORIZONTAL,
                                Attributes.NONE.id("design-scroll")));
    }

    static Widget metrics() {
        return new ShowcaseCard(
                        "design-metrics",
                        "Component metrics",
                        "Every control's height, padding and radius is fixed in the base sheet, compact in brackets:"
                                + " a button and a field are 32 (28), a badge 20, a chip 24, a progress track 4.",
                        DocLink.to(CHAPTER, "component-metrics"))
                .of(
                        metric("button: height 32 (28), padding-x 12, radius 8", new Button("Button", () -> {})),
                        metric("text-input: height 32 (28), padding-x 8, radius 4", new TextInput("Field", v -> {})),
                        metric("checkbox: glyph 16, hit 32, gap 8", new Live.Check("Checkbox", true, "metric-check")),
                        metric("toggle: track 36 by 20, thumb 16", new Live.Switch("Toggle", true, "metric-toggle")),
                        metric("chip: height 24, radius 12", new Chip("Chip")),
                        metric("badge: height 20, full radius", new Badge("20")),
                        metric("progress: track 4, full radius", new Progress(0.6)));
    }

    private static Widget metric(String text, Widget control) {
        return new Column(List.of(control, Demo.code(text)), Attributes.NONE.classes("metric"));
    }

    static Widget accessibility() {
        return new ShowcaseCard(
                        "design-accessibility",
                        "Accessibility baseline",
                        "Built: contrast measured in both themes, every control keyboard-operable, text scale to"
                                + " 150%, reduced motion, 32 px targets, and a role and a name on every focusable"
                                + " widget. Not built: a screen-reader bridge.",
                        DocLink.to(CHAPTER, "accessibility-baseline"))
                .reference();
    }

    static Widget governance() {
        return new ShowcaseCard(
                        "design-governance",
                        "Governance",
                        "A new widget enters with a specification, a metrics row and gallery coverage in both themes"
                                + " before code. The alias tokens, the component contracts and the metrics tables"
                                + " are the stable tier.",
                        DocLink.to(CHAPTER, "governance"))
                .reference();
    }

    /// Three swatches on three tracks, one per motion token, sent across together.
    record Durations() implements Widget.Stateful {

        @Override
        public State<?> createState() {
            return new DurationsState();
        }
    }

    static final class DurationsState extends State<Durations> {

        private boolean across;

        @Override
        public Widget build(BuildContext context) {
            var tracks = new ArrayList<Widget>(DURATIONS.size() + 1);
            tracks.add(Demo.row(
                    "durations-actions",
                    new Button(across ? "Send them back" : "Send them across", () -> setState(() -> across = !across))
                            .id("durations-go")));
            for (var duration : DURATIONS) {
                var swatch = across
                        ? Attributes.NONE.id("duration-" + duration).classes("duration-swatch", duration, "across")
                        : Attributes.NONE.id("duration-" + duration).classes("duration-swatch", duration);
                tracks.add(new Row(
                        List.of(
                                Demo.code("--gb-motion-" + duration),
                                new Panel(
                                        List.of(new Panel(List.of(), swatch)),
                                        Attributes.NONE.classes("duration-track"))),
                        Attributes.NONE.classes("duration-row")));
            }
            return new ShowcaseCard(
                            "design-motion",
                            "Three durations",
                            "100 ms for state feedback, 160 for a component, 240 for an overlay, on three curves:"
                                    + " ease-enter, ease-exit and linear. Exits are faster than enters, and under"
                                    + " reduced motion every one is zero.",
                            DocLink.to(CHAPTER, "motion"))
                    .of(tracks);
        }
    }
}
