package dev.goldberry.example.ui.styling;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.form.textinput.TextInput;
import dev.goldberry.widgets.panel.Panel;
import dev.goldberry.widgets.text.Text;

/// The Styling screen's cards for the CSS past the basics: selectors that look
/// at siblings and descendants, colour functions, lengths that resolve late,
/// stacking and sizing, clipping and filters, lettering, right-to-left text and
/// container queries.
///
/// Like [PropertyCards], each demonstration is a rule in `chapter-styling.css`
/// worn by an ordinary widget, and the box says which declaration it wears.
///
/// Read more: [Properties](https://goldberry.dev/docs/guide/styling.html#properties).
final class AdvancedCards {

    private static final String CHAPTER = "guide/styling";

    /// The weights the variable-weight row draws, each at its own value.
    static final List<Integer> WEIGHTS = List.of(200, 300, 400, 500, 600, 700, 800, 900);

    private AdvancedCards() {}

    static Widget selectors() {
        return new ShowcaseCard(
                        "styling-selectors-more",
                        "Selectors that look around",
                        "+ and ~ reach the siblings after a box, :has() asks about what is inside it, :is(), :where()"
                                + " and :not() take lists, rules nest with &, and :focus-within lights a box while"
                                + " anything in it has the keyboard. Click into the field.",
                        DocLink.to(CHAPTER, "selectors"))
                .of(
                        Demo.row(
                                "sib-tiles",
                                Demo.box("1", "sib-tile"),
                                Demo.box("picked", "sib-tile", "picked"),
                                Demo.box("3", "sib-tile"),
                                Demo.box("4", "sib-tile"),
                                Demo.box("5", "sib-tile")),
                        Demo.code(".picked + .sib-tile { accent }"),
                        Demo.code(".picked ~ .sib-tile { dimmed }"),
                        Demo.row(
                                "has-hosts",
                                new Panel(
                                        List.of(
                                                new Text("I hold a flag"),
                                                new Text("flag", Attributes.NONE.classes("has-flag"))),
                                        Attributes.NONE.id("has-with").classes("styling-box", "has-host")),
                                new Panel(
                                        List.of(new Text("I hold nothing")),
                                        Attributes.NONE.id("has-without").classes("styling-box", "has-host"))),
                        Demo.code(".has-host:has(.has-flag) { outlined }"),
                        new Panel(
                                List.of(
                                        new Text("A box with a field in it"),
                                        new TextInput("Its border follows the keyboard", value -> {})
                                                .id("within-field")),
                                Attributes.NONE.id("within-host").classes("styling-box")));
    }

    static Widget colourFunctions() {
        return new ShowcaseCard(
                        "styling-colour-functions",
                        "Colour functions",
                        "hsl(), hwb(), lab(), oklch() and the named colours, color-mix() to blend two in a space you"
                                + " name, and relative colour, which derives a shade from a token: a hover colour"
                                + " with no token of its own.",
                        DocLink.to(CHAPTER, "colour"))
                .of(
                        Demo.row(
                                "colour-function-swatches",
                                Demo.swatch("hsl()", "fn-hsl"),
                                Demo.swatch("hwb()", "fn-hwb"),
                                Demo.swatch("lab()", "fn-lab"),
                                Demo.swatch("oklch()", "fn-oklch"),
                                Demo.swatch("rebeccapurple", "fn-named")),
                        Demo.row(
                                "colour-derived",
                                Demo.swatch("the accent", "fn-accent"),
                                Demo.swatch("color-mix()", "fn-mix"),
                                Demo.swatch("darker", "fn-relative"),
                                Demo.swatch("lighter", "fn-lighter")),
                        Demo.box("linear-gradient(in oklch, …)", "fn-ramp"),
                        Demo.code("oklch(from var(--gb-accent) calc(l - 0.15) c h)"));
    }

    static Widget lengths() {
        return new ShowcaseCard(
                        "styling-lengths",
                        "Lengths that resolve late",
                        "calc(), min(), max() and clamp() mix units when the style is computed, and a length may be in"
                                + " vw, ch or em as well as pixels. Each bar is one declaration; resize the window and the vw and"
                                + " clamp() bars follow it.",
                        DocLink.to(CHAPTER, "box-and-layout"))
                .of(Demo.column(
                        "length-bars",
                        Demo.box("width: calc(240px - 2em)", "length-bar", "length-calc"),
                        Demo.box("width: clamp(120px, 20vw, 300px)", "length-bar", "length-clamp"),
                        Demo.box("width: 28ch", "length-bar", "length-ch"),
                        Demo.box("min(300px, 12em)", "length-bar", "length-min"),
                        Demo.box("width: 12vw", "length-bar", "length-vw")));
    }

    static Widget layering() {
        return new ShowcaseCard(
                        "styling-layering",
                        "Stacking, ratio and sizing",
                        "z-index lifts a box over its siblings and order moves it among them; aspect-ratio keeps a"
                                + " shape; a border takes room, and box-sizing says whether width counts it. Both"
                                + " bottom boxes say width: 120px.",
                        DocLink.to(CHAPTER, "box-and-layout"))
                .of(
                        Demo.row(
                                "z-stack",
                                Demo.box("z-index: 0", "z-plate", "z-low"),
                                Demo.box("z-index: 1", "z-plate", "z-high"),
                                Demo.box("z-index: 0", "z-plate", "z-last")),
                        Demo.row(
                                "order-row",
                                Demo.box("first in markup, order: 1", "order-late"),
                                Demo.box("second", "order-plain"),
                                Demo.box("third", "order-plain")),
                        Demo.row("ratio-row", Demo.box("16 / 9", "ratio-wide"), Demo.box("1", "ratio-square")),
                        Demo.row(
                                "sizing-row",
                                Demo.box("border-box", "sizing-box", "sizing-border"),
                                Demo.box("content-box", "sizing-box", "sizing-content")));
    }

    static Widget compositing() {
        return new ShowcaseCard(
                        "styling-compositing",
                        "Clip, blend and filter",
                        "clip-path cuts a box to a circle, an ellipse, a polygon or a rounded inset, filter runs"
                                + " over everything it draws, and mix-blend-mode blends it with what is under it,"
                                + " kept inside its group by isolation.",
                        DocLink.to(CHAPTER, "border-outline-and-shadow"))
                .of(
                        Demo.row(
                                "clip-plates",
                                Demo.swatch("circle()", "clip-plate", "clip-circle"),
                                Demo.swatch("ellipse()", "clip-plate", "clip-ellipse"),
                                Demo.swatch("polygon()", "clip-plate", "clip-diamond"),
                                Demo.swatch("inset() round", "clip-plate", "clip-inset")),
                        Demo.row(
                                "filter-plates",
                                Demo.swatch("blur(2px)", "filter-plate", "filter-blur"),
                                Demo.swatch("grayscale(1)", "filter-plate", "filter-grey"),
                                Demo.swatch("hue-rotate(120deg)", "filter-plate", "filter-hue"),
                                Demo.swatch("drop-shadow()", "filter-plate", "filter-drop")),
                        Demo.row(
                                "blend-group",
                                Demo.box("multiply", "blend-plate", "blend-multiply"),
                                Demo.box("screen", "blend-plate", "blend-screen"),
                                Demo.box("difference", "blend-plate", "blend-difference")));
    }

    static Widget lettering() {
        var weights = new ArrayList<Widget>(WEIGHTS.size());
        for (var weight : WEIGHTS) {
            weights.add(new Text(String.valueOf(weight), Attributes.NONE.classes("weight-sample", "weight-" + weight)));
        }
        return new ShowcaseCard(
                        "styling-lettering",
                        "Lettering",
                        "letter-spacing, word-spacing, text-transform and text-shadow change how a line is set;"
                                + " line-clamp keeps the first lines and marks the cut; justify fills each line but"
                                + " the last; and the upright face is variable, so every weight is drawn as written.",
                        DocLink.to(CHAPTER, "typography"))
                .of(
                        new Text("letter-spacing and uppercase", Attributes.NONE.id("letter-spaced")),
                        new Text("word spacing opens the gaps", Attributes.NONE.id("word-spaced")),
                        new Text("a shadow under every glyph", Attributes.NONE.id("text-shadowed")),
                        new Text(
                                "line-clamp: 2 keeps two lines of a paragraph that would take four or five at this"
                                        + " width, and marks the second with an ellipsis where the rest was cut away"
                                        + " from the reader.",
                                Attributes.NONE.id("text-clamped")),
                        new Text(
                                "Justified text shares the room left on each line among its word gaps, so both"
                                        + " edges are straight, and the last line of the paragraph starts at the"
                                        + " start.",
                                Attributes.NONE.id("text-justified")),
                        Demo.row("weight-row", weights));
    }

    static Widget direction() {
        return new ShowcaseCard(
                        "styling-direction",
                        "Right to left",
                        "direction: rtl starts a row and a line at the right and mirrors the logical properties,"
                                + " punctuation follows the base direction, bidi-override forces every letter one"
                                + " way, and a field's caret and arrows follow its text.",
                        DocLink.to(CHAPTER, "box-and-layout"))
                .of(
                        Demo.row(
                                "rtl-row",
                                Demo.box("first", "rtl-item", "rtl-first"),
                                Demo.box("second", "rtl-item"),
                                Demo.box("third", "rtl-item")),
                        Demo.code("margin-inline-start: 24px on the first"),
                        new Text("Order 42 shipped!", Attributes.NONE.id("rtl-line")),
                        new Text("Goldberry", Attributes.NONE.id("rtl-override")),
                        new TextInput("Typed from the right", value -> {}).id("rtl-field"),
                        Demo.caption("Arabic and Hebrew read from the right too, in a face that has their letters."));
    }

    static Widget containers() {
        return new ShowcaseCard(
                        "styling-containers",
                        "Container queries",
                        "A box with container-type answers @container rules about its own width, so the same"
                                + " children lay out by the room they are given rather than the window's; cqw sizes"
                                + " the title from it.",
                        DocLink.to(CHAPTER, "media-queries"))
                .of(container("cq-narrow"), container("cq-wide"));
    }

    /// A query container holding the same plate and two lines as its twin.
    ///
    /// The body is a `panel` rather than a `column`, because a column's
    /// direction is the widget's and the query's rule turns this one into a row.
    private static Widget container(String id) {
        return new Panel(
                List.of(new Panel(
                        List.of(
                                new Panel(List.of(), Attributes.NONE.classes("styling-plate", "cq-plate")),
                                new Column(
                                        List.of(
                                                new Text("A card", Attributes.NONE.classes("cq-title")),
                                                new Text(
                                                        "A row from 280 px of room",
                                                        Attributes.NONE.classes("caption"))),
                                        Attributes.NONE.classes("cq-text"))),
                        Attributes.NONE.classes("cq-body"))),
                Attributes.NONE.id(id).classes("styling-box", "cq-host"));
    }
}
