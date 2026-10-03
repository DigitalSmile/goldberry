package dev.goldberry.example.ui.controls;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.radio.Radio;
import dev.goldberry.widgets.controls.radio.RadioGroup;
import dev.goldberry.widgets.text.Text;

/// A `radio-group` with a caption among its options, holding a value of its own.
///
/// The caption is a child that is not a `radio`, and the group leaves it where it
/// was written. The arrows rove and select, the controlled way: an arrow raises
/// `change`, the card sets the value, and the dot follows it.
///
/// Read more: [`radio-group`](https://goldberry.dev/docs/components/choices.html#radio-group).
public record RadioGroupCard() implements Widget.Stateful {

    @Override
    public State<?> createState() {
        return new RadioGroupState();
    }

    /// Where the Company winters.
    static final class RadioGroupState extends State<RadioGroupCard> {

        private String winter = "rivendell";

        @Override
        public Widget build(BuildContext context) {
            return new ShowcaseCard(
                            "choices-radio-group",
                            "A group is one Tab stop",
                            "A radio group holds the fact that exactly one option is on. Tab enters it at the"
                                    + " selected option and the arrows move and select. A child that is not a radio,"
                                    + " such as a caption, stays where it was written.",
                            DocLink.to("components/choices", "radio-group"))
                    .of(new RadioGroup(
                            winter,
                            List.of(
                                    new Radio("rivendell", "Rivendell"),
                                    new Radio("bree", "Bree"),
                                    new Radio("shire", "The Shire"),
                                    new Text(
                                            "Wintering in " + label(winter) + ".",
                                            Attributes.NONE.classes("caption").id("winter-caption"))),
                            null,
                            value -> setState(() -> winter = value),
                            false,
                            Attributes.NONE.id("winter")));
        }

        private static String label(String value) {
            return switch (value) {
                case "rivendell" -> "Rivendell";
                case "bree" -> "Bree";
                case "shire" -> "the Shire";
                default -> value;
            };
        }
    }
}
