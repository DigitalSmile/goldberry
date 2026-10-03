package dev.goldberry.example.ui.styling;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.lint.Finding;
import dev.goldberry.css.lint.StyleLint;
import dev.goldberry.css.parse.CssSyntaxException;
import dev.goldberry.css.parse.ParseMode;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.controls.option.Option;
import dev.goldberry.widgets.controls.segmented.Segmented;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.form.textarea.TextArea;
import dev.goldberry.widgets.text.Text;

/// A stylesheet the reader edits, read the way an application's is, with what
/// the parser and the lint made of it printed underneath.
///
/// `Stylesheet.parse` under the mode the reader picks, then `StyleLint` over
/// the result against the toolkit's own sheets, which is the same pair of calls
/// `-Dgoldberry.css.lint=true` makes at start-up. Read on a press and not on every
/// keystroke, because a lenient read logs one warning per rule it drops.
///
/// Read more: [Strict and lenient sheets](https://goldberry.dev/docs/guide/styling.html#strict-and-lenient-sheets).
///
/// @param id    the prefix of every id on it: `-mode`, `-sheet`, `-read`, `-result`
/// @param sheet the CSS it starts with
record SheetProbe(String id, String sheet) implements Widget.Stateful {

    /// What the probe's sheet is called in a warning.
    static final String ORIGIN = "the showcase's sample sheet";

    @Override
    public State<?> createState() {
        return new ProbeState();
    }

    /// What one read of the sheet amounted to, as the lines the card prints.
    static List<String> read(String css, ParseMode mode) {
        try {
            var parsed = Stylesheet.parse(CascadeLayer.APPLICATION, css, mode, ORIGIN);
            var lines = new ArrayList<String>();
            var rules = parsed.rules().size();
            lines.add((rules == 1 ? "Kept 1 rule" : "Kept " + rules + " rules")
                    + (parsed.dropped().isEmpty()
                            ? "."
                            : " and dropped " + parsed.dropped().size() + "."));
            var inForce = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK));
            inForce.add(parsed);
            new StyleLint(inForce)
                    .check(parsed).stream()
                            .filter(finding -> finding.kind().isDefect())
                            .map(SheetProbe::describe)
                            .forEach(lines::add);
            return List.copyOf(lines);
        } catch (CssSyntaxException refused) {
            return List.of("Refused the whole sheet at line " + refused.line() + ": " + refused.getMessage());
        }
    }

    private static String describe(Finding finding) {
        var where = "Line " + finding.line() + ": ";
        return switch (finding.kind()) {
            case DROPPED_RULE -> where + "dropped " + finding.selector() + ", because " + finding.value() + ".";
            case DEAD_DECLARATION ->
                where + finding.property() + ": " + finding.value() + " does nothing in " + finding.selector() + ".";
            case UNTYPED_RULE, UNCOLOURED_ROOT -> where + finding;
        };
    }

    private static final class ProbeState extends State<SheetProbe> {

        private String text = "";
        private ParseMode mode = ParseMode.LENIENT;
        private List<String> result = List.of("Press Read the sheet.");

        @Override
        protected void initState() {
            text = widget().sheet();
        }

        @Override
        public Widget build(BuildContext context) {
            var id = widget().id();
            var lines = new ArrayList<Widget>(result.size());
            for (var line : result) {
                lines.add(new Text(line, Attributes.NONE.classes("styling-code")));
            }
            return new Column(
                    List.of(
                            new Row(
                                    List.of(
                                            new Segmented(
                                                            mode == ParseMode.STRICT ? "strict" : "lenient",
                                                            this::pick,
                                                            new Option("lenient", "Lenient"),
                                                            new Option("strict", "Strict"))
                                                    .id(id + "-mode"),
                                            new Button("Read the sheet", this::readNow).id(id + "-read")),
                                    Attributes.NONE.classes("styling-row")),
                            new TextArea(widget().sheet(), value -> text = value)
                                    .rows(4, 8)
                                    .withAttributes(
                                            Attributes.NONE.id(id + "-sheet").classes("probe-sheet")),
                            new Column(lines, Attributes.NONE.id(id + "-result").classes("probe-result"))),
                    Attributes.NONE.classes("styling-column"));
        }

        private void pick(String value) {
            setState(() -> mode = "strict".equals(value) ? ParseMode.STRICT : ParseMode.LENIENT);
        }

        private void readNow() {
            setState(() -> result = read(text, mode));
        }
    }
}
