package dev.goldberry.example.ui.richinput;

import java.util.stream.Collectors;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.example.ui.richinput.suggest.Directories;
import dev.goldberry.example.ui.richinput.suggest.SuggestionPicker;
import dev.goldberry.text.mark.Mark;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkedText;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.form.richinput.RichInput;
import dev.goldberry.widgets.text.Text;

/// Four triggers on one field, each answered from its own list and each
/// putting its choice in under a different kind: `@` a person as an atom, `#`
/// a topic as a reference of the application's own, `:` an emoji as plain
/// text, and `/` a command as an atom of the application's own. The line under
/// the field lists what went in.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#keyboard).
public record TriggersCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "rich-triggers-card";

    /// The field's id.
    public static final String FIELD = "rich-triggers";

    /// The line that lists the marks.
    public static final String MARKS = "rich-triggers-marks";

    private static final ShowcaseCard CARD = new ShowcaseCard(
            ID,
            "Mentions, topics, emoji, commands",
            "A trigger at a word start asks the application for suggestions: @ people, # topics, : emoji and"
                    + " / commands. Up, Down and Enter choose; a person or a command goes in whole.",
            DocLink.to("components/forms", "keyboard"));

    @Override
    public State<?> createState() {
        return new TriggersState();
    }

    /// The field's text and the picker answering its triggers.
    static final class TriggersState extends State<TriggersCard> {

        private MarkedText value = MarkedText.EMPTY;

        private final SuggestionPicker picker = new SuggestionPicker(Directories.ALL, this::context);

        @Override
        public Widget build(BuildContext context) {
            return CARD.of(
                    new RichInput(value, next -> setState(() -> value = next))
                            .placeholder("Try @an, #de, :ro or /po")
                            .mentions(picker.mentions())
                            .maxRows(3)
                            .id(FIELD),
                    new Text(describe(value), Attributes.NONE.id(MARKS).classes("caption")));
        }

        @Override
        protected void dispose() {
            picker.close();
        }

        /// The picker, for a test.
        SuggestionPicker picker() {
            return picker;
        }

        /// The field's text, for a test.
        MarkedText value() {
            return value;
        }
    }

    /// What `text` holds beyond its words, one mark at a time.
    static String describe(MarkedText text) {
        if (text.marks().isEmpty()) {
            return "Nothing chosen yet.";
        }
        return text.marks().stream().map(TriggersCard::describe).collect(Collectors.joining(" · "));
    }

    private static String describe(Mark mark) {
        return switch (mark.kind()) {
            case MarkKind.Mention(var id) -> "mention " + id;
            case MarkKind.Custom(var name, var value, var behaviour) ->
                name + " " + value + (behaviour.atomic() ? " (atom)" : "");
            case MarkKind.Link(var uri) -> "link " + uri;
            case MarkKind.Style style -> style.className();
        };
    }
}
