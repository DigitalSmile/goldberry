package dev.goldberry.example.ui.styling;

import java.util.List;

import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.controls.checkbox.Checkbox;
import dev.goldberry.widgets.controls.option.Option;
import dev.goldberry.widgets.controls.select.Select;
import dev.goldberry.widgets.controls.slider.Slider;
import dev.goldberry.widgets.controls.toggle.Toggle;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.text.Text;

/// Controls that keep their own value, for the cards that show how a control
/// *looks* and have no application state to bind it to.
///
/// A control in this toolkit is controlled: the value is the application's and
/// a click only asks for a new one. A card about a focus ring or a density still
/// wants a checkbox that ticks, so each of these is the smallest application
/// there is, one field in a [State].
///
/// Read more: [States](https://goldberry.dev/docs/guide/design-system.html#states).
final class Live {

    private Live() {}

    /// A checkbox that ticks itself.
    ///
    /// @param label   what it says
    /// @param checked whether it starts ticked
    /// @param id      its id
    record Check(String label, boolean checked, String id) implements Widget.Stateful {

        @Override
        public State<?> createState() {
            return new CheckState();
        }

        private static final class CheckState extends State<Check> {

            private boolean on;

            @Override
            protected void initState() {
                on = widget().checked();
            }

            @Override
            public Widget build(BuildContext context) {
                return new Checkbox(widget().label(), Checkbox.Value.of(on), () -> setState(() -> on = !on))
                        .id(widget().id());
            }
        }
    }

    /// A toggle that switches itself.
    ///
    /// @param label what it says
    /// @param on    whether it starts on
    /// @param id    its id
    record Switch(String label, boolean on, String id) implements Widget.Stateful {

        @Override
        public State<?> createState() {
            return new SwitchState();
        }

        private static final class SwitchState extends State<Switch> {

            private boolean on;

            @Override
            protected void initState() {
                on = widget().on();
            }

            @Override
            public Widget build(BuildContext context) {
                return new Toggle(widget().label(), on, value -> setState(() -> on = value)).id(widget().id());
            }
        }
    }

    /// A slider that moves itself, from 0 to 1.
    ///
    /// @param value where it starts
    /// @param id    its id
    record Fader(double value, String id) implements Widget.Stateful {

        @Override
        public State<?> createState() {
            return new FaderState();
        }

        private static final class FaderState extends State<Fader> {

            private double value;

            @Override
            protected void initState() {
                value = widget().value();
            }

            @Override
            public Widget build(BuildContext context) {
                return new Slider(0, 1, value, 0.05, next -> setState(() -> value = next)).id(widget().id());
            }
        }
    }

    /// A select that keeps what was picked.
    ///
    /// @param choices the options, the first picked to begin with
    /// @param id      its id
    record Choice(List<String> choices, String id) implements Widget.Stateful {

        @Override
        public State<?> createState() {
            return new ChoiceState();
        }

        private static final class ChoiceState extends State<Choice> {

            private String picked = "";

            @Override
            protected void initState() {
                picked = widget().choices().getFirst();
            }

            @Override
            public Widget build(BuildContext context) {
                var options = widget().choices().stream()
                        .map(choice -> new Option(choice, choice))
                        .toArray(Option[]::new);
                return new Select(picked, next -> setState(() -> picked = next), options).id(widget().id());
            }
        }
    }

    /// A button and a count of its presses: the smallest proof that a press
    /// landed where the button is drawn.
    ///
    /// @param id      the button's id; the count is `<id>-count`
    /// @param label   what the button says
    /// @param classes the button's classes, which is where a card's demonstration
    ///                is
    record Counter(String id, String label, List<String> classes) implements Widget.Stateful {

        @Override
        public State<?> createState() {
            return new CounterState();
        }

        private static final class CounterState extends State<Counter> {

            private int presses;

            @Override
            public Widget build(BuildContext context) {
                var counter = widget();
                return new Row(
                        List.of(
                                new Button(counter.label(), () -> setState(() -> presses++))
                                        .withAttributes(Attributes.NONE
                                                .id(counter.id())
                                                .classes(counter.classes().toArray(String[]::new))),
                                new Text(
                                        presses == 1 ? "Pressed once" : "Pressed " + presses + " times",
                                        Attributes.NONE
                                                .id(counter.id() + "-count")
                                                .classes("caption"))),
                        Attributes.NONE.classes("styling-row"));
            }
        }
    }
}
