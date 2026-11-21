package dev.goldberry.example.ui.richinput;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.text.mark.MarkedText;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.toggle.Toggle;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.form.richinput.RichInput;

/// One field and two switches: read-only refuses edits and still selects and
/// copies, and disabled takes no input at all and leaves the `Tab` order. Each
/// is changed while the field is shown, and the text stays as it was.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#attributes).
public record StatesCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "rich-states-card";

    /// The field's id.
    public static final String FIELD = "rich-states";

    private static final ShowcaseCard CARD = new ShowcaseCard(
            ID,
            "Read-only and disabled",
            "A read-only field refuses edits and still selects and copies. A disabled one takes no input at all."
                    + " Switch either while the field is shown: its text stays.",
            DocLink.to("components/forms", "attributes"));

    @Override
    public State<?> createState() {
        return new StatesState();
    }

    /// The two switches and the field's text.
    static final class StatesState extends State<StatesCard> {

        private boolean readOnly;

        private boolean disabled;

        private MarkedText value = Samples.RESTYLED;

        @Override
        public Widget build(BuildContext context) {
            return CARD.of(
                    new Row(
                                    new Toggle(
                                            "Read-only",
                                            readOnly,
                                            null,
                                            on -> setState(() -> readOnly = on),
                                            false,
                                            Attributes.NONE.id("rich-states-read-only")),
                                    new Toggle(
                                            "Disabled",
                                            disabled,
                                            null,
                                            on -> setState(() -> disabled = on),
                                            false,
                                            Attributes.NONE.id("rich-states-disabled")))
                            .withAttributes(Attributes.NONE.classes("toolbar")),
                    new RichInput(value, next -> value = next)
                            .placeholder("Type, then switch")
                            .readOnly(readOnly)
                            .disabled(disabled)
                            .maxRows(3)
                            .id(FIELD));
        }

        /// The field's text, for a test.
        MarkedText value() {
            return value;
        }

        /// What the switches say, for a test.
        List<Boolean> switches() {
            return List.of(readOnly, disabled);
        }
    }
}
