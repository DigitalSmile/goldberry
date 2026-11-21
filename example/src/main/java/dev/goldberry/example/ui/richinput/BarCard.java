package dev.goldberry.example.ui.richinput;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.text.edit.keys.FormatCommand;
import dev.goldberry.text.mark.MarkKind.Style;
import dev.goldberry.text.mark.MarkedText;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.option.Option;
import dev.goldberry.widgets.controls.segmented.Segmented;
import dev.goldberry.widgets.form.richinput.FormatButton;
import dev.goldberry.widgets.form.richinput.RichInput;
import dev.goldberry.widgets.text.Text;

/// The formatting bar three ways over one field: the stock bar, a short bar of
/// the application's own buttons, or no bar, leaving the keys and the text
/// menu's Format submenu.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#keyboard).
public record BarCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "rich-bar-card";

    /// The field's id.
    public static final String FIELD = "rich-bar";

    /// The choice's id.
    public static final String CHOICE = "rich-bar-choice";

    /// The stock bar: [FormatButton#defaults()].
    public static final String STOCK = "stock";

    /// The short bar: [#SHORT_BAR].
    public static final String SHORT = "short";

    /// No bar.
    public static final String NONE = "none";

    /// The application's own bar: bold, italic, code and a link, in its own
    /// words.
    public static final List<FormatButton> SHORT_BAR = List.of(
            new FormatButton("bold", "Strong", new FormatCommand.Toggle(Style.BOLD)),
            new FormatButton("italic", "Emphasis", new FormatCommand.Toggle(Style.ITALIC)),
            new FormatButton("code", "Monospace", new FormatCommand.Toggle(Style.CODE)),
            new FormatButton("link", "Add a link", FormatCommand.Simple.LINK));

    private static final ShowcaseCard CARD = new ShowcaseCard(
            ID,
            "The formatting bar",
            "Select words and let go: the bar opens over them. Choose the stock bar, a short bar of your own"
                    + " buttons, or none. Ctrl+K edits a link, and the text menu has Format either way.",
            DocLink.to("components/forms", "keyboard"));

    private static final MarkedText SAMPLE =
            MarkedText.of("Select a few of these words, then press a button on the bar that opens over them.");

    /// The buttons a choice gives the field.
    public static List<FormatButton> buttons(String choice) {
        return switch (choice) {
            case SHORT -> SHORT_BAR;
            case NONE -> List.of();
            default -> FormatButton.defaults();
        };
    }

    @Override
    public State<?> createState() {
        return new BarState();
    }

    /// The choice and the field's text.
    static final class BarState extends State<BarCard> {

        private String choice = STOCK;

        private MarkedText value = SAMPLE;

        @Override
        public Widget build(BuildContext context) {
            var buttons = buttons(choice);
            return CARD.of(
                    new Segmented(
                            choice,
                            List.of(
                                    new Option(STOCK, "Stock"),
                                    new Option(SHORT, "Your own"),
                                    new Option(NONE, "None")),
                            null,
                            next -> setState(() -> choice = next),
                            false,
                            Attributes.NONE.id(CHOICE)),
                    new RichInput(value, next -> value = next)
                            .formatting(buttons)
                            .rows(2)
                            .maxRows(4)
                            .id(FIELD),
                    new Text(
                            describe(buttons),
                            Attributes.NONE.id("rich-bar-says").classes("caption")));
        }

        private static String describe(List<FormatButton> buttons) {
            if (buttons.isEmpty()) {
                return "No bar: the keys and the text menu still format.";
            }
            return buttons.size() + " buttons: "
                    + String.join(
                            ", ", buttons.stream().map(FormatButton::label).toList());
        }
    }
}
