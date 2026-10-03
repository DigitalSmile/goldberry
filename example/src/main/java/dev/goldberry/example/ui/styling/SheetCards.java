package dev.goldberry.example.ui.styling;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.controls.progressbar.Progress;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.form.textinput.TextInput;
import dev.goldberry.widgets.panel.Panel;
import dev.goldberry.widgets.text.Text;

/// The Styling screen's cards about the sheet itself: how it is read, how its
/// layers stack, what it can select, and how values travel down the tree.
///
/// Every demonstration here is a rule in `chapter-styling.css`, worn by an
/// ordinary widget.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html).
final class SheetCards {

    private static final String CHAPTER = "guide/styling";

    /// A sheet with two rules the subset has not got among one it has.
    static final String AMBITIOUS_SHEET = """
            .lane { gap: 8px }
            .lane::before { color: #bf616a }
            button:hovered { opacity: 0.8 }""";

    /// A sheet whose rules are all legal and two of whose declarations do nothing.
    static final String UNKNOWN_PROPERTIES = """
            .note {
              font-style: italic;
              letter-spacing: 2px;
              text-align: justify;
            }""";

    private SheetCards() {}

    static Widget sheets() {
        return new ShowcaseCard(
                        "styling-sheets",
                        "Strict and lenient sheets",
                        "An application's sheet is lenient: a rule asking for something outside the subset is"
                                + " dropped with a warning and the rest is kept. The toolkit's own sheets are strict"
                                + " and refuse. Pick a mode and read the sheet.",
                        DocLink.to(CHAPTER, "strict-and-lenient-sheets"))
                .of(new SheetProbe("probe-modes", AMBITIOUS_SHEET));
    }

    static Widget properties() {
        return new ShowcaseCard(
                        "styling-properties",
                        "A closed set of properties",
                        "The engine takes a fixed list of properties. One it does not know, or a value it cannot"
                                + " read, does nothing and is warned about once. The lint names each such"
                                + " declaration: edit the sheet and read it again.",
                        DocLink.to(CHAPTER, "properties"))
                .of(new SheetProbe("probe-properties", UNKNOWN_PROPERTIES));
    }

    static Widget cascade() {
        return new ShowcaseCard(
                        "styling-cascade",
                        "Four layers",
                        "Base, theme, application, inline. At equal specificity the later layer wins, so this"
                                + " screen's rule repaints the middle button over the theme's; an id beats a class"
                                + " whatever the layer.",
                        DocLink.to(CHAPTER, "the-cascade-four-layers"))
                .of(
                        Demo.row(
                                "cascade-buttons",
                                new Button("Theme", () -> {}).id("cascade-theme"),
                                new Button("Application", () -> {})
                                        .withAttributes(Attributes.NONE
                                                .id("cascade-app")
                                                .classes("cascade-plum")),
                                new Button("Id beats class", () -> {})
                                        .withAttributes(Attributes.NONE
                                                .id("cascade-winner")
                                                .classes("cascade-plum"))),
                        Demo.code("button.cascade-plum { background: var(--nord15) }"),
                        Demo.code("button#cascade-winner { background: var(--nord14) }"));
    }

    static Widget selectors() {
        var stripes = new ArrayList<Widget>();
        for (var name : List.of("Hobbiton", "Bree", "Weathertop", "Rivendell", "Moria", "Lothlórien")) {
            stripes.add(new Row(List.of(new Text(name)), Attributes.NONE.classes("stripe")));
        }
        return new ShowcaseCard(
                        "styling-selectors",
                        "Selectors and pseudo-classes",
                        "Types, classes, ids, descendants and children, a closed set of states, and where a node"
                                + " sits among its siblings. Hover and press the tiles, click into the field, and"
                                + " read the stripes, which are :nth-child(even).",
                        DocLink.to(CHAPTER, "selectors"))
                .of(
                        Demo.row(
                                "selector-tiles",
                                Demo.box("Hover me", "selector-tile"),
                                Demo.box("Press me", "selector-tile"),
                                Demo.box("Me too", "selector-tile")),
                        new TextInput("A field that goes green on :focus", value -> {}).id("selector-field"),
                        new Column(stripes, Attributes.NONE.id("selector-stripes")));
    }

    static Widget parts() {
        return new ShowcaseCard(
                        "styling-parts",
                        "Parts",
                        "A control with two surfaces is two nodes, and the inner one is a part with a type name:"
                                + " check-indicator, toggle-track, toggle-thumb. The second row restyles only its"
                                + " parts. Tick and switch both rows.",
                        DocLink.to(CHAPTER, "parts"))
                .of(
                        Demo.row(
                                "parts-plain",
                                new Live.Check("As shipped", true, "parts-check-plain"),
                                new Live.Switch("As shipped", true, "parts-toggle-plain")),
                        Demo.row(
                                "parts-restyled",
                                new Live.Check("Round", true, "parts-check-round"),
                                new Live.Switch("Plum", true, "parts-toggle-plum")),
                        Demo.code("#parts-restyled check-indicator { border-radius: 8px }"),
                        Demo.code("#parts-restyled toggle-track:checked { background: var(--nord15) }"));
    }

    static Widget customProperties() {
        return new ShowcaseCard(
                        "styling-custom-properties",
                        "Custom properties and var()",
                        "A custom property inherits, and every colour a control draws comes through a --gb-*"
                                + " token. The second group redefines the accent tokens on itself alone, and every"
                                + " control inside it follows.",
                        DocLink.to(CHAPTER, "custom-properties-and-var"))
                .of(
                        tokenGroup("vars-default", "As the theme says"),
                        tokenGroup("vars-plum", "#vars-plum { --gb-accent: #b48ead; --gb-accent-fill: #b48ead }"),
                        new Text(
                                "This line is var(--vars-unset, #a3be8c): the fallback, because nothing sets it.",
                                Attributes.NONE.id("vars-fallback")));
    }

    private static Widget tokenGroup(String id, String label) {
        return new Column(
                List.of(
                        Demo.code(label),
                        Demo.row(
                                id + "-controls",
                                new Button("Primary", () -> {}).styled("primary"),
                                new Live.Check("Ticked", true, id + "-check"),
                                new Live.Fader(0.6, id + "-slider")),
                        new Progress(0.6)),
                Attributes.NONE.id(id).classes("token-group"));
    }

    static Widget inheritance() {
        return new ShowcaseCard(
                        "styling-inheritance",
                        "Inheritance",
                        "Colour, the font properties and the text-flow properties pass down the tree; layout,"
                                + " background, border, opacity and transform do not. The box sets yellow italic"
                                + " and a dashed border, and only the first two reach inside.",
                        DocLink.to(CHAPTER, "inheritance"))
                .of(new Column(
                        List.of(
                                new Text("I am yellow and slanted because my parent is."),
                                new Panel(
                                        List.of(new Text("So am I, two levels down, inside a plain border.")),
                                        Attributes.NONE.classes("styling-box")),
                                new Button("A button sets its own colour", () -> {})),
                        Attributes.NONE.id("inherit-parent")));
    }
}
