package dev.goldberry.example.ui.styling;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.example.ShowcaseModel;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.panel.Panel;
import dev.goldberry.widgets.text.Text;

/// The Design system screen's cards about tokens: the palette, the aliases,
/// the spacing ramp, the type scale, and the radii, elevations and materials.
///
/// Every swatch reads its token through `var()` in `chapter-styling.css`, so
/// switching the light repaints them from the other theme's values, which is the
/// demonstration.
///
/// Read more: [The design system](https://goldberry.dev/docs/guide/design-system.html).
final class TokenCards {

    private static final String CHAPTER = "guide/design-system";

    /// The alias tokens shown, without their `--gb-` prefix.
    static final List<String> ALIASES = List.of(
            "bg",
            "surface",
            "surface-2",
            "surface-raised",
            "surface-sunken",
            "text",
            "text-muted",
            "border",
            "border-strong",
            "accent",
            "focus",
            "selection",
            "scrim",
            "danger",
            "warning",
            "success",
            "info");

    /// The spacing ramp, every legal value.
    static final List<Integer> RAMP = List.of(2, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64);

    private TokenCards() {}

    static Widget palette() {
        return new ShowcaseCard(
                        "design-palette",
                        "The Nord palette",
                        "Sixteen colours as --nord0 to --nord15: Polar Night, Snow Storm, Frost and Aurora. Widgets"
                                + " never read them directly, and the Aurora hues carry meaning rather than"
                                + " decoration.",
                        DocLink.to(CHAPTER, "colour"))
                .of(
                        nords("palette-night", "Polar Night", 0, 4),
                        nords("palette-snow", "Snow Storm", 4, 7),
                        nords("palette-frost", "Frost", 7, 11),
                        nords("palette-aurora", "Aurora", 11, 16));
    }

    private static Widget nords(String id, String name, int from, int to) {
        var swatches = new ArrayList<Widget>(to - from);
        for (var i = from; i < to; i++) {
            swatches.add(Demo.swatch("nord" + i, "nord-" + i));
        }
        return new Column(
                List.of(Demo.caption(name), Demo.row(id, swatches)), Attributes.NONE.classes("styling-column"));
    }

    static Widget aliases(ShowcaseModel.Actions actions) {
        var swatches = new ArrayList<Widget>(ALIASES.size());
        for (var alias : ALIASES) {
            swatches.add(Demo.swatch(alias, "alias-" + alias));
        }
        return new ShowcaseCard(
                        "design-aliases",
                        "Alias tokens",
                        "What widgets read: surfaces, text, borders, accent, focus, selection, scrim and the semantic"
                                + " hues. Only these change between the two themes, so switch the light and every"
                                + " swatch here follows.",
                        DocLink.to(CHAPTER, "alias-tokens"))
                .of(
                        Demo.row(
                                "alias-actions",
                                new Button("Switch the light", actions::toggleTheme).id("alias-theme-switch")),
                        Demo.row("alias-swatches", swatches));
    }

    static Widget spacing() {
        var bars = new ArrayList<Widget>(RAMP.size());
        for (var step : RAMP) {
            bars.add(new Row(
                    List.of(
                            Demo.code(step + " px"),
                            new Panel(List.of(), Attributes.NONE.classes("spacing-bar", "space-" + step))),
                    Attributes.NONE.classes("spacing-step")));
        }
        return new ShowcaseCard(
                        "design-spacing",
                        "The 4 px ramp",
                        "Every gap, padding and margin is one of eleven values on a 4 px base, and nothing off it."
                                + " Padding is 8 or 12, related controls 8 apart, groups 16, the window's margin 16.",
                        DocLink.to(CHAPTER, "spacing"))
                .of(new Column(bars, Attributes.NONE.id("spacing-ramp")));
    }

    /// The type scale, as the legacy Basic screen showed it, with the strong
    /// body rank beside the plain one.
    static Widget type() {
        return new ShowcaseCard(
                        "type-card",
                        "The type scale",
                        "Seven ranks, each a size and a line height from the theme, at two weights that are two"
                                + " faces: display, title, heading, body, strong body, caption and code.",
                        DocLink.to(CHAPTER, "type"))
                .of(
                        new Text("A map of the West", Attributes.NONE.classes("rank-display")),
                        new Text("From Bag End to the Sea", Attributes.NONE.classes("rank-title")),
                        new Text("Nine set out from Rivendell in the winter", Attributes.NONE.classes("rank-heading")),
                        new Text(
                                "The Company kept to the high paths while the passes held, and turned under the"
                                        + " mountain only when the snow refused them.",
                                Attributes.NONE.classes("rank-body")),
                        new Text("They went on in silence", Attributes.NONE.classes("rank-body-strong")),
                        new Text(
                                "Marked in the Red Book, in a hand that was not steady",
                                Attributes.NONE.classes("rank-caption")),
                        new Text("leagues = 1795  //  pace 3.1/hr", Attributes.NONE.classes("rank-code")));
    }

    static Widget shape() {
        return new ShowcaseCard(
                        "design-shape",
                        "Radii, elevation and materials",
                        "Radii of 4, 8, 12 and full. Three elevation tokens whose geometry is fixed and whose alpha"
                                + " follows the theme. Surfaces are opaque, and the veil behind a dialog is the"
                                + " one material besides.",
                        DocLink.to(CHAPTER, "shape-elevation-and-materials"))
                .of(
                        Demo.row(
                                "shape-radii",
                                Demo.box("4", "radius-4"),
                                Demo.box("8", "radius-8"),
                                Demo.box("12", "radius-12"),
                                Demo.box("full", "radius-full")),
                        Demo.row(
                                "shape-elevations",
                                Demo.box("--gb-elevation-1", "elevation-1"),
                                Demo.box("--gb-elevation-2", "elevation-2"),
                                Demo.box("--gb-elevation-3", "elevation-3")),
                        new Panel(
                                List.of(
                                        new Text("Under the veil"),
                                        new Panel(
                                                List.of(new Text("--gb-scrim")),
                                                Attributes.NONE.classes("material-veil"))),
                                Attributes.NONE.id("material-under").classes("styling-box")));
    }
}
