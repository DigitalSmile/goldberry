package io.github.digitalsmile.goldberry.example.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.overlay.dialog.Dialog;
import io.github.digitalsmile.goldberry.widgets.overlay.dialog.DialogAction;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The dialog a sheet opens when a tile is pressed: one glyph at **five sizes**,
/// side by side, and — for an emoji — the same glyph in a line of text at five
/// sizes more.
///
/// ## Why five sizes is the demonstration
///
/// A sheet shows every glyph at one size, and that size is chosen to fit a
/// thousand of them on a screen. What a reader choosing one wants to know is
/// what it looks like where *they* will put it: in a 16-point toolbar, on a
/// 64-point empty state. For an icon that is the difference between a path that
/// holds up and one whose detail closes into a blot; for an emoji it is the
/// claim ADR-0456 makes — a paint graph is drawn at whatever size is asked, and
/// is as sharp at 64 as at 16.
///
/// ## And how to write one
///
/// Under the emoji's sizes the dialog shows the two ways an application puts
/// this emoji in its own text — a KDL document and Java — each with the
/// character itself and with its code point, for the editor that cannot type
/// it. Neither names a font: the text is routed to the emoji face by the
/// itemizer ([ADR-0393]), which is the whole of what an application has to
/// know. The snippets are built for the emoji pressed, and `SpecimensTest`
/// parses the KDL one with the toolkit's own parser, so the example cannot
/// drift from the syntax it shows.
///
/// ## Icons are built at size, emoji are styled at size
///
/// An [Icon] is a path built *at* a size ([ADR-0034]), so the icon row builds
/// five of them. An emoji is text, and text is sized by the cascade, so the
/// emoji row is five glyphs each carrying a `size-<n>` class that
/// `showcase.css` turns into a `font-size`. The same split as the two sheets'
/// tiles, for the same reason.
final class Specimens {

    /// The five sizes, in logical pixels: the small end is a toolbar and a
    /// menu, 24 is Lucide's design size, and 64 is a hero.
    static final List<Integer> GLYPH_SIZES = List.of(16, 24, 32, 48, 64);

    /// The five text sizes an emoji's sample line is set at — caption to
    /// heading, so the emoji is seen sitting in the text ranks an application
    /// actually uses.
    static final List<Integer> TEXT_SIZES = List.of(12, 14, 16, 20, 24);

    /// The panel ids, which a test or a tour finds the dialog by.
    static final String ICON_DIALOG = "icon-specimen";

    static final String EMOJI_DIALOG = "emoji-specimen";

    private Specimens() {}

    /// The dialog for the icon called `name`.
    ///
    /// @param categories the icon's categories, named under the title so the
    ///                   reader sees where else to find it
    /// @param close      what the Close action does — the screen holds the
    ///                   overlay, so the screen takes it away
    static Dialog icon(String name, List<String> categories, Runnable close) {
        var cells = new ArrayList<Widget>(GLYPH_SIZES.size());
        for (var size : GLYPH_SIZES) {
            cells.add(new SpecimenCell(new SpecimenIcon(Icon.bundled(name, size)), size + " px", size));
        }
        return new Dialog(
                name,
                List.of(
                        new Text(
                                "icon=\"" + name + "\" in a document, Icon.bundled(\"" + name + "\", size) in Java."
                                        + " Filed under "
                                        + String.join(
                                                ", ",
                                                categories.stream()
                                                        .map(CategorySheet::label)
                                                        .toList()) + ".",
                                Attributes.NONE.id("icon-specimen-note").classes("screen-note")),
                        new Row(cells, Attributes.NONE.id("icon-specimen-sizes").classes("specimen-sizes")),
                        new DialogAction("Close", DialogAction.Role.DISMISSIVE, close)),
                Attributes.NONE.id(ICON_DIALOG).classes("specimen-dialog"));
    }

    /// The dialog for one emoji.
    ///
    /// @param character the emoji, as the string it is drawn from
    /// @param name      Unicode's name for it, which titles the dialog
    /// @param codePoint what the caption spells as `U+…`
    /// @param group     the Unicode group it is filed under
    /// @param close     what the Close action does
    static Dialog emoji(String character, String name, int codePoint, String group, Runnable close) {
        var cells = new ArrayList<Widget>(GLYPH_SIZES.size());
        for (var size : GLYPH_SIZES) {
            cells.add(new SpecimenCell(new SpecimenEmoji(character, size), size + " px", size));
        }
        var lines = new ArrayList<Widget>(TEXT_SIZES.size());
        for (var size : TEXT_SIZES) {
            // The UI face, not the emoji one: this line names no font, so the
            // emoji in it is routed out of the prose and back in by the
            // itemizer (ADR-0393) — which is how an application actually draws
            // one, and the half of this dialog a glyph on its own cannot show.
            lines.add(new Text(
                    size + " px — shipping on Friday " + character + " as planned " + character,
                    Attributes.NONE.classes("specimen-sample", "sample-" + size).key("sample-" + size)));
        }
        return new Dialog(
                title(name),
                List.of(
                        new Text(
                                "U+" + Integer.toHexString(codePoint).toUpperCase(Locale.ROOT) + " · " + group,
                                Attributes.NONE.id("emoji-specimen-note").classes("screen-note")),
                        new Row(
                                cells,
                                Attributes.NONE.id("emoji-specimen-sizes").classes("specimen-sizes")),
                        new Column(
                                lines, Attributes.NONE.id("emoji-specimen-text").classes("specimen-text")),
                        new Row(
                                List.of(
                                        code("In a document", kdlSample(character, codePoint), "emoji-specimen-kdl"),
                                        code("In Java", javaSample(character, codePoint), "emoji-specimen-java")),
                                Attributes.NONE.id("emoji-specimen-code").classes("specimen-code-row")),
                        new DialogAction("Close", DialogAction.Role.DISMISSIVE, close)),
                Attributes.NONE.id(EMOJI_DIALOG).classes("specimen-dialog"));
    }

    /// How a KDL document puts `character` in its text: typed, in a `text` and a
    /// `button` label, and by code point with KDL's Unicode escape — a backslash,
    /// `u` and the code point in braces, which cannot be spelled in this comment
    /// because `javac` reads it as an escape of its own.
    ///
    /// Every line is a line of real markup — `SpecimensTest` parses them — and
    /// nothing in it names a font.
    static List<String> kdlSample(String character, int codePoint) {
        return List.of(
                "// Typed, anywhere a document writes text",
                "text \"Shipping on Friday " + character + "\"",
                "button press=\"app.celebrate\" \"Celebrate " + character + "\"",
                "// Or by code point, where it cannot be typed",
                "text \"Shipping on Friday \\u{" + hex(codePoint) + "}\"");
    }

    /// How Java puts `character` in a widget's text: typed into a string
    /// literal, and built from its code point with `Character.toString`.
    static List<String> javaSample(String character, int codePoint) {
        return List.of(
                "// Typed into any string",
                "new Text(\"Shipping on Friday " + character + "\");",
                "new Button(\"Celebrate " + character + "\", this::celebrate);",
                "// Or from its code point",
                "var emoji = Character.toString(0x" + hex(codePoint) + ");",
                "new Text(\"Shipping on Friday \" + emoji);");
    }

    private static String hex(int codePoint) {
        return Integer.toHexString(codePoint).toUpperCase(Locale.ROOT);
    }

    /// A captioned block of code, a `text` per line in the code face.
    ///
    /// A line per `text` rather than one string with breaks in it, so each line
    /// is laid out as written and never re-wrapped into the next — code that
    /// wraps is code a reader copies wrongly.
    private static Widget code(String caption, List<String> lines, String id) {
        var block = new ArrayList<Widget>(lines.size());
        for (var i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            block.add(new Text(
                    line,
                    Attributes.NONE
                            .classes(line.startsWith("//") ? "code-comment" : "code-line")
                            .key(i)));
        }
        return new Column(
                List.of(
                        new Text(caption, Attributes.NONE.classes("specimen-caption")),
                        new Column(block, Attributes.NONE.id(id).classes("specimen-code"))),
                Attributes.NONE.classes("specimen-code-column"));
    }

    /// Unicode's name as a title: "grinning face" is "Grinning face".
    static String title(String name) {
        return name.isEmpty() ? name : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    /// One size: the glyph over its caption, the cell as wide as the largest
    /// glyph so the five line up on a common baseline of captions.
    ///
    /// @param size what the glyph is drawn at, which also classes the cell
    ///             `size-<n>` for a stylesheet that wants to reach one
    record SpecimenCell(Widget glyph, String caption, int size) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "specimen-cell";
        }

        @Override
        public Set<String> classes() {
            return Set.of("size-" + size);
        }

        @Override
        public Object key() {
            return size;
        }

        @Override
        public List<Widget> children() {
            return List.of(glyph, new Text(caption, Attributes.NONE.classes("specimen-caption")));
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).children(children.toArray(Box[]::new));
        }
    }

    /// An icon, drawn at the size it was built at in the text colour.
    record SpecimenIcon(Icon icon) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "specimen-icon";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).children(Box.icon(icon, style.color()));
        }
    }

    /// An emoji, drawn through the emoji face at the size its `size-<n>` class
    /// asks for.
    record SpecimenEmoji(String character, int size) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "specimen-emoji";
        }

        @Override
        public Set<String> classes() {
            return Set.of("size-" + size);
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).children(Box.text(context.paragraph(style, character), style.color()));
        }
    }
}
