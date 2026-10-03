package dev.goldberry.example.ui.text;

import java.util.Objects;

import dev.goldberry.bind.Property;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.form.textinput.TextInput;
import dev.goldberry.widgets.text.Text;
import dev.goldberry.widgets.text.TextRank;

/// A text bound to a value, and a field writing that value.
///
/// `Text.of(fallback, observable)` is the Java spelling of `text bind="…"`: the
/// text reads the value when it is drawn. The value is a property this card owns,
/// because nothing else in the window reads it.
///
/// Read more: [`text`](https://goldberry.dev/docs/components/text.html#text).
public record BoundTextCard() implements Widget.Stateful {

    /// What the field and the text start with.
    static final String NAME = "Tom Bombadil";

    @Override
    public State<?> createState() {
        return new BoundTextState();
    }

    /// The name, as the property both widgets follow.
    static final class BoundTextState extends State<BoundTextCard> {

        private final Property<String> name = Property.of(NAME);

        @Override
        public Widget build(BuildContext context) {
            return new ShowcaseCard(
                            "text-bound",
                            "A text that follows a value",
                            "A bound text reads its value when it is drawn, and its argument is the fallback until the"
                                    + " value answers. Type in the field: the line under it reads the same value.",
                            DocLink.to("components/text", "text"))
                    .of(
                            new TextInput(
                                            Objects.requireNonNullElse(name.get(), ""),
                                            value -> setState(() -> name.set(value)))
                                    .placeholder("Who goes there?")
                                    .withAttributes(Attributes.NONE.id("text-bound-field")),
                            Text.of("Nobody yet", name).style(TextRank.HEADING).id("text-bound-echo"));
        }
    }
}
