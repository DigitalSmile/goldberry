package dev.goldberry.example.ui.input;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.controls.radio.Radio;
import dev.goldberry.widgets.controls.radio.RadioGroup;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.text.Text;

/// A radio group, which is one Tab stop with the arrows inside it, beside three
/// buttons, which are three.
///
/// Read more: [A composite is one Tab stop](https://goldberry.dev/docs/guide/input.html#a-composite-is-one-tab-stop).
public record CompositeCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "input-composite";

    @Override
    public State<?> createState() {
        return new CompositeState();
    }

    static final class CompositeState extends State<CompositeCard> {

        private String road = "east";

        private String pressed = "none yet";

        @Override
        public Widget build(BuildContext context) {
            return new ShowcaseCard(
                            ID,
                            "A composite is one Tab stop",
                            "A radio group, a tab list or a toolbar is one Tab stop, and the arrows move inside it;"
                                    + " Tab enters at the checked one. Tab into the group, use the arrows, then Tab"
                                    + " on: the three buttons are three stops.",
                            DocLink.to("guide/input", "a-composite-is-one-tab-stop"))
                    .of(
                            new Text("One stop", Attributes.NONE.classes("caption")),
                            new RadioGroup(
                                            road,
                                            value -> setState(() -> road = value),
                                            new Radio("east", "East Road"),
                                            new Radio("north", "North Way"),
                                            new Radio("south", "Greenway"))
                                    .id("composite-group"),
                            new Text("Three stops", Attributes.NONE.classes("caption")),
                            new Row(
                                    List.of(
                                            new Button("Bree", () -> setState(() -> pressed = "Bree"))
                                                    .id("composite-bree"),
                                            new Button("Weathertop", () -> setState(() -> pressed = "Weathertop"))
                                                    .id("composite-weathertop"),
                                            new Button("Rivendell", () -> setState(() -> pressed = "Rivendell"))
                                                    .id("composite-rivendell")),
                                    Attributes.NONE.classes("toolbar")),
                            new Text(
                                    "Road: " + road + "   Last button: " + pressed,
                                    Attributes.NONE.id("composite-readout").classes("readout")));
        }
    }
}
