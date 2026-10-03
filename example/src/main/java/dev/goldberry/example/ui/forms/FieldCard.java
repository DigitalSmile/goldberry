package dev.goldberry.example.ui.forms;

import dev.goldberry.bind.Property;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.form.field.Field;
import dev.goldberry.widgets.form.form.Form;
import dev.goldberry.widgets.form.textinput.TextInput;

/// The `field` card: a required field that complains once you leave it empty, and
/// a label that hands focus to its control.
///
/// A field validates the value its control is bound to, so the required one is
/// bound to a [Property] this card keeps for itself rather than to a model path
/// another card shares.
///
/// Read more: [`field`](https://goldberry.dev/docs/components/forms.html#field).
public record FieldCard() implements Widget.Stateful {

    /// The card's id.
    static final String ID = "label-card";

    private static final ShowcaseCard CARD = new ShowcaseCard(
            ID,
            "A label, a control, a message",
            "A field is a label, a control and a message under it, and stays silent until you leave it once. "
                    + "Tab out of the required field while it is empty, or click a label to focus its field.",
            DocLink.to("components/forms", "field"));

    @Override
    public State<?> createState() {
        return new FieldCardState();
    }

    /// The required field's value, which lives as long as the card does.
    static final class FieldCardState extends State<FieldCard> {

        private final Property<String> bearer = Property.of("");

        @Override
        public Widget build(BuildContext context) {
            return CARD.of(new Form(
                            new Field(
                                            "Ring-bearer",
                                            TextInput.of(bearer, bearer::set)
                                                    .placeholder("Required")
                                                    .id("bearer"))
                                    .required(true),
                            new Field(
                                    "Click this label",
                                    new TextInput()
                                            .placeholder("…the caret lands here")
                                            .id("label-target")))
                    .id("labelled"));
        }
    }
}
