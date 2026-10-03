package dev.goldberry.example.ui.styling;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.text.flow.TextAlign;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.checkbox.Checkbox;
import dev.goldberry.widgets.controls.option.Option;
import dev.goldberry.widgets.controls.segmented.Segmented;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.form.textarea.TextArea;
import dev.goldberry.widgets.text.Text;

/// The **Styling** screen's typography card: every text property the stylesheet
/// has, over one `text-area` the reader is typing in.
///
/// ## What it is demonstrating, and why a still picture cannot
///
/// Six declarations and one control. The point is not that they *render* — a
/// golden image shows that — it is that the **caret, the selection and the hit
/// test move with the glyphs**. Under `text-align: center` a caret measured from
/// the paragraph's origin sits half the line's slack away from the text it belongs
/// to, and on a wrapped line it is a *different* half on every line; the only way
/// to see that it does not is to put a caret in centred text and press where it is
/// drawn.
///
/// ## The options are classes
///
/// A class set computed from seven options is a value of the application's, and
/// every class is one rule in the stylesheet. What the application chooses is
/// which of them apply, and the toolkit's half is that the field honours whatever
/// the cascade resolved for it.
///
/// ## The two rules are one declaration
///
/// `text-decoration` is a **set** — CSS allows `underline line-through` together —
/// so it cannot be two classes each writing the property, where the later would
/// simply win. The class is computed from the pair, which is what
/// `#styled-area.rule-both` is, and the line under the field prints the
/// declaration so the reader can see the shorthand rather than infer it.
///
/// ## Monospace is where the face-matching rule shows
///
/// Inter ships four faces — 400 and 600, upright and italic — and
/// JetBrains Mono ships one. So switching the family on with *italic* still set
/// draws upright code: matching is family, then style, then weight.
///
/// Read more: [Typography](https://goldberry.dev/docs/guide/styling.html#typography).
public record TextStylingCard() implements Widget.Stateful {

    /// The card's head and summary.
    static final ShowcaseCard CARD = new ShowcaseCard(
            "styling-card",
            "Every text property, one field",
            "Size, weight, style, family, alignment and decoration, each a class over one text area. The caret"
                    + " and the selection follow the glyphs wherever the rules put them: change one, then click"
                    + " into a word and type.",
            DocLink.to("guide/styling", "typography"));

    /// Four lines of deliberately different lengths, so an alignment has something
    /// to show: every line of a centred paragraph is indented by its *own* share of
    /// the slack, which is the case one number cannot describe.
    private static final String SAMPLE = """
            Four lines, and no two of them the same length.
            Centre them: each moves by its own share of the room.
            Underline them and the rules follow the glyphs.
            Now click in the middle of a word and type.""";

    @Override
    public State<?> createState() {
        return new StylingState();
    }

    /// The type scale a reader can put the sample at — the design system's three
    /// body ranks, as the `--gb-font-*`/`--gb-line-*` pairs a stylesheet writes.
    ///
    /// A named triple rather than a free `font-size`, because the design system
    /// ships a scale and a screen that needs a fourth rank extends the system
    /// rather than improvising one.
    private enum Scale {
        CAPTION("caption", 11, 14),
        BODY("body", 13, 18),
        HEADING("heading", 15, 20);

        private final String token;
        private final int size;
        private final int line;

        Scale(String token, int size, int line) {
            this.token = token;
            this.size = size;
            this.line = line;
        }

        Option option() {
            return new Option(token, token);
        }
    }

    static final class StylingState extends State<TextStylingCard> {

        private TextAlign align = TextAlign.START;
        private Scale scale = Scale.BODY;
        private boolean bold;
        private boolean italic;
        private boolean underline;
        private boolean struck;
        private boolean mono;

        @Override
        public Widget build(BuildContext context) {
            return CARD.of(
                    alignment(),
                    size(),
                    marks(),
                    area(),
                    new Text(declarations(), Attributes.NONE.id("styled-css").classes("styling-code")),
                    Demo.caption("Monospace with italic still set draws upright: JetBrains Mono has one face,"
                            + " and a face is matched by family, then style, then weight."));
        }

        /// `text-align`, as the three keywords the subset takes. `left` and
        /// `right` are absent from the toolkit, so they are absent here: they
        /// coincide with `start`/`end` under LTR and part company under RTL.
        private Widget alignment() {
            return new Row(
                            new Text("text-align", Attributes.NONE.classes("caption", "option-label")),
                            new Segmented(
                                            align.name().toLowerCase(Locale.ROOT),
                                            this::align,
                                            new Option("start", "start"),
                                            new Option("center", "center"),
                                            new Option("end", "end"))
                                    .withAttributes(Attributes.NONE.id("styled-align")))
                    .withAttributes(Attributes.NONE.classes("option-row"));
        }

        private Widget size() {
            var options = new ArrayList<Option>(Scale.values().length);
            for (var value : Scale.values()) {
                options.add(value.option());
            }
            return new Row(
                            new Text("font-size", Attributes.NONE.classes("caption", "option-label")),
                            new Segmented(scale.token, this::scale, options.toArray(Option[]::new))
                                    .withAttributes(Attributes.NONE.id("styled-scale")))
                    .withAttributes(Attributes.NONE.classes("option-row"));
        }

        /// The five that are on or off, in a wrapped row: a card is as wide as the
        /// column it landed in, so the row that cannot shrink is the row that
        /// wraps.
        private Widget marks() {
            return new Row(
                    List.of(
                            check("Bold", bold, () -> setState(() -> bold = !bold), "styled-bold"),
                            check("Italic", italic, () -> setState(() -> italic = !italic), "styled-italic"),
                            check("Underline", underline, () -> setState(() -> underline = !underline), "styled-under"),
                            check("Strike", struck, () -> setState(() -> struck = !struck), "styled-struck"),
                            check("Monospace", mono, () -> setState(() -> mono = !mono), "styled-mono")),
                    Attributes.NONE.id("styled-marks").classes("option-row"));
        }

        private static Widget check(String label, boolean on, Runnable onChange, String id) {
            return new Checkbox(label, Checkbox.Value.of(on), onChange).withAttributes(Attributes.NONE.id(id));
        }

        /// The field itself, wearing whatever the options add up to.
        ///
        /// The value is handed in unchanged on every rebuild, which is what lets a
        /// reader keep what they typed: a `text-area` adopts an offered value only
        /// when the offer *changes*, so toggling an option restyles the control
        /// without touching its text, its caret or its undo history.
        private Widget area() {
            // No `change` handler: the control holds its own text, and nothing on
            // this card reads it.
            return new TextArea(SAMPLE, null)
                    .rows(4, 8)
                    .withAttributes(Attributes.NONE.id("styled-area").classes(classes()));
        }

        /// What the seven options amount to, as class names.
        ///
        /// Classes rather than declarations, because a widget cannot write CSS:
        /// every one of these *is* a rule, and what the application chooses is
        /// which of them apply.
        private String[] classes() {
            var names = new ArrayList<String>(6);
            switch (align) {
                case START -> {
                    // The initial value, so there is no class: a rule that restated
                    // the default would be a rule nothing can be told from.
                }
                case CENTER -> names.add("align-center");
                case END -> names.add("align-end");
            }
            if (scale != Scale.BODY) {
                names.add("size-" + scale.token);
            }
            if (bold) {
                names.add("bold");
            }
            if (italic) {
                names.add("italic");
            }
            if (mono) {
                names.add("mono");
            }
            // One class for the pair, because `text-decoration` is one property.
            if (underline && struck) {
                names.add("rule-both");
            } else if (underline) {
                names.add("rule-underline");
            } else if (struck) {
                names.add("rule-struck");
            }
            return names.toArray(String[]::new);
        }

        /// The declarations those classes carry, printed so the reader can see the
        /// stylesheet rather than guess at it.
        private String declarations() {
            var out = new ArrayList<String>(6);
            out.add("text-align: " + align.name().toLowerCase(Locale.ROOT));
            out.add("font-size: " + scale.size + "px");
            out.add("line-height: " + scale.line + "px");
            out.add("font-weight: " + (bold ? "600" : "400"));
            out.add("font-style: " + (italic ? "italic" : "normal"));
            var rules = new ArrayList<String>(2);
            if (underline) {
                rules.add("underline");
            }
            if (struck) {
                rules.add("line-through");
            }
            out.add("text-decoration: " + (rules.isEmpty() ? "none" : String.join(" ", rules)));
            out.add("font-family: " + (mono ? "\"JetBrains Mono\"" : "Inter"));
            return String.join("; ", out);
        }

        private void align(String value) {
            setState(() -> align = switch (value) {
                case "center" -> TextAlign.CENTER;
                case "end" -> TextAlign.END;
                default -> TextAlign.START;
            });
        }

        private void scale(String value) {
            setState(() -> scale = switch (value) {
                case "caption" -> Scale.CAPTION;
                case "heading" -> Scale.HEADING;
                default -> Scale.BODY;
            });
        }
    }
}
