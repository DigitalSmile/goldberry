package dev.goldberry.example.ui.styling;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.badge.Badge;
import dev.goldberry.widgets.panel.Panel;
import dev.goldberry.widgets.text.Text;

/// The Styling screen's cards about properties, one per group the guide lists:
/// box and layout, text flow, colour, backgrounds, borders, transform, media
/// queries and the cursor.
///
/// Each box wears one class, and the class is one rule in `chapter-styling.css`
/// that the box names in its own text, so the declaration and its result are
/// read together.
///
/// Read more: [Properties](https://goldberry.dev/docs/guide/styling.html#properties).
final class PropertyCards {

    private static final String CHAPTER = "guide/styling";

    /// The cursors the subset has, each on a box of its own.
    static final List<String> CURSORS = List.of(
            "default",
            "pointer",
            "text",
            "move",
            "wait",
            "progress",
            "crosshair",
            "not-allowed",
            "ew-resize",
            "ns-resize",
            "nesw-resize",
            "nwse-resize",
            "grab",
            "grabbing");

    private PropertyCards() {}

    static Widget box() {
        return new ShowcaseCard(
                        "styling-box",
                        "Box and layout",
                        "Flex direction, wrapping, gap, padding, margin, sizes and absolute position compile to"
                                + " Yoga. Narrow the window: the row wraps, each item grows from a 96 px basis, and"
                                + " the badge stays pinned to its corner.",
                        DocLink.to(CHAPTER, "box-and-layout"))
                .of(
                        Demo.row(
                                "box-toolbar",
                                Demo.box("flex: 1 0 96px", "box-item"),
                                Demo.box("flex: 1 0 96px", "box-item"),
                                Demo.box("align-self: flex-end", "box-item", "box-low"),
                                Demo.box("flex: 2 0 96px", "box-item", "box-wide")),
                        new Panel(
                                List.of(
                                        new Text("position: absolute; top: 6px; right: 6px"),
                                        new Badge("3").withAttributes(Attributes.NONE.id("box-badge"))),
                                Attributes.NONE.id("box-host").classes("styling-box")),
                        Demo.row("box-centring", Demo.idBox("box-centred", "margin: 0 auto")));
    }

    static Widget textFlow() {
        return new ShowcaseCard(
                        "styling-text-flow",
                        "Text flow",
                        "white-space, overflow-wrap, word-break, text-overflow, text-align and text-decoration."
                                + " One line that ends in an ellipsis, one set to the end, one long address cut"
                                + " anywhere, and one struck through.",
                        DocLink.to(CHAPTER, "text-flow"))
                .of(Demo.column(
                        "flow-samples",
                        new Text(
                                "white-space: nowrap; text-overflow: ellipsis on a line far too long for its" + " box",
                                Attributes.NONE.id("flow-ellipsis")),
                        new Text("text-align: end", Attributes.NONE.id("flow-end")),
                        new Text(
                                "https://example.org/a/path/long/enough/that/a/line/has/to/cut/it/anywhere",
                                Attributes.NONE.id("flow-anywhere")),
                        new Text("text-decoration: line-through", Attributes.NONE.id("flow-struck"))));
    }

    static Widget colour() {
        return new ShowcaseCard(
                        "styling-colour",
                        "Colour and opacity",
                        "A colour is hex, rgb(), rgba(), transparent or a var(). Opacity on a box with children"
                                + " draws them as one layer, so the left pair does not show through each other;"
                                + " the right pair is two translucent leaves.",
                        DocLink.to(CHAPTER, "colour"))
                .of(
                        Demo.row(
                                "colour-washes",
                                Demo.box("var(--gb-surface-sunken)", "colour-surface"),
                                Demo.box("rgba(136, 192, 208, 0.2)", "colour-wash"),
                                Demo.box("opacity: 0.5", "colour-faded")),
                        Demo.row(
                                "colour-layers",
                                overlap("colour-layer", "colour-group"),
                                overlap("colour-leaves", "colour-plain")));
    }

    /// Two overlapping plates in a box, translucent as a group (`colour-group`,
    /// opacity on the box) or one by one (`colour-plain`, opacity on each plate).
    private static Widget overlap(String id, String className) {
        return new Panel(
                List.of(
                        new Panel(List.of(), Attributes.NONE.classes("overlap-a")),
                        new Panel(List.of(), Attributes.NONE.classes("overlap-b"))),
                Attributes.NONE.id(id).classes("overlap", className));
    }

    static Widget gradients() {
        return new ShowcaseCard(
                        "styling-gradients",
                        "Gradient layers",
                        "background is a comma list of layers over a colour: linear, radial and their repeating"
                                + " forms, each filled in the box's own rounded shape. The stripe marches because"
                                + " background-position animates.",
                        DocLink.to(CHAPTER, "backgrounds-and-gradients"))
                .of(Demo.row(
                        "gradient-plates",
                        Demo.box("radial-gradient over a colour", "gradient-glow"),
                        Demo.box("linear-gradient(to right, …)", "gradient-tint"),
                        Demo.box("linear-gradient(135deg, …)", "gradient-dusk"),
                        Demo.box("repeating-linear-gradient", "gradient-march")));
    }

    static Widget borders() {
        return new ShowcaseCard(
                        "styling-borders",
                        "Borders, outlines and shadows",
                        "A border is drawn inside the box and takes no room: solid, dashed, dotted or double, side"
                                + " by side. An outline sits outside. box-shadow is a list, first on top, and a"
                                + " shadow may be cast inside.",
                        DocLink.to(CHAPTER, "border-outline-and-shadow"))
                .of(Demo.row(
                        "border-plates",
                        Demo.box("2px dashed", "border-dashed"),
                        Demo.box("3px dotted", "border-dotted"),
                        Demo.box("6px double", "border-double"),
                        Demo.box("border-left: 4px", "border-side"),
                        Demo.box("outline, offset 4px", "border-outline"),
                        Demo.box("three shadows", "shadow-list"),
                        Demo.box("inset shadow", "shadow-inset")));
    }

    static Widget transform() {
        return new ShowcaseCard(
                        "styling-transform",
                        "Transform",
                        "translate, scale and rotate move a box after layout, so a transform costs no layout pass."
                                + " Hit testing maps the pointer back through the same matrix: press the turned"
                                + " button where it is drawn.",
                        DocLink.to(CHAPTER, "transform"))
                .of(
                        Demo.row(
                                "transform-plates",
                                Demo.box("rotate(-8deg)", "transform-turned"),
                                Demo.box("scale(0.8), origin left top", "transform-shrunk"),
                                Demo.box("translate(0, 8px)", "transform-lowered")),
                        new Live.Counter("transform-button", "Turned and grown", List.of("transform-button")));
    }

    static Widget media() {
        return new ShowcaseCard(
                        "styling-media",
                        "Media queries",
                        "@media asks about the window's size, its orientation, the desktop's light or dark, and"
                                + " reduced motion, and re-runs the cascade when an answer changes. Resize the"
                                + " window and watch the lamps.",
                        DocLink.to(CHAPTER, "media-queries"))
                .of(
                        Demo.row(
                                "media-lamps",
                                Demo.idBox("media-narrow", "under 900 px", "media-lamp"),
                                Demo.idBox("media-medium", "900 to 1199 px", "media-lamp"),
                                Demo.idBox("media-wide", "1200 px and wider", "media-lamp")),
                        Demo.row(
                                "media-desktop",
                                Demo.idBox("media-portrait", "portrait", "media-lamp"),
                                Demo.idBox("media-dark", "a dark desktop", "media-lamp"),
                                Demo.idBox("media-still", "reduced motion", "media-lamp")));
    }

    static Widget cursor() {
        var boxes = new ArrayList<Widget>(CURSORS.size());
        for (var cursor : CURSORS) {
            boxes.add(Demo.idBox("cursor-" + cursor, cursor, "cursor-box", "cursor-" + cursor));
        }
        return new ShowcaseCard(
                        "styling-cursor",
                        "Cursor",
                        "cursor rides on the painted box and inherits down whatever is under the pointer. Hover"
                                + " each box to see the desktop's own cursor; grab and grabbing fall back to move.",
                        DocLink.to(CHAPTER, "cursor"))
                .of(Demo.row("cursor-boxes", boxes));
    }
}
