package dev.goldberry.example.ui.input;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.checkbox.Checkbox;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.text.Text;

/// Three nested boxes that trace a press: capture from the outside in, the
/// target, then bubble from the inside out.
///
/// Read more: [How an event travels](https://goldberry.dev/docs/guide/input.html#how-an-event-travels).
public record EventTravelCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "input-travel";

    @Override
    public State<?> createState() {
        return new TravelState();
    }

    static final class TravelState extends State<EventTravelCard> {

        /// The last press's trace, one line per phase.
        private final List<String> trace = new ArrayList<>();

        /// Whether the middle box stops the press on its way up.
        private boolean middleConsumes;

        @Override
        public Widget build(BuildContext context) {
            var inner = new PhaseBox(
                    "inner",
                    false,
                    this::record,
                    List.of(),
                    Attributes.NONE.id("travel-inner").classes("phase-inner"));
            var middle = new PhaseBox(
                    "middle",
                    middleConsumes,
                    this::record,
                    List.of(new Text("middle", Attributes.NONE.classes("phase-label")), inner),
                    Attributes.NONE.id("travel-middle"));
            var outer = new PhaseBox(
                    "outer",
                    false,
                    this::record,
                    List.of(new Text("outer", Attributes.NONE.classes("phase-label")), middle),
                    Attributes.NONE.id("travel-outer"));
            var lines = trace.isEmpty() ? List.of("Press the innermost box.") : trace;
            return new ShowcaseCard(
                            ID,
                            "How an event travels",
                            "A press goes down through every ancestor (capture), reaches the deepest box (target),"
                                    + " then climbs back up (bubble). consume() stops it at any step.",
                            DocLink.to("guide/input", "how-an-event-travels"))
                    .of(
                            outer,
                            new Checkbox(
                                            "The middle box consumes it",
                                            Checkbox.Value.of(middleConsumes),
                                            () -> setState(() -> middleConsumes = !middleConsumes))
                                    .id("travel-consume"),
                            new Column(
                                    lines.stream()
                                            .<Widget>map(line -> new Text(line, Attributes.NONE.classes("readout")))
                                            .toList(),
                                    Attributes.NONE.id("travel-trace").classes("readout-lines")));
        }

        /// One phase of a press. The first capture starts a new trace.
        private void record(String line) {
            setState(() -> {
                if (line.startsWith("capture  outer")) {
                    trace.clear();
                }
                trace.add(line);
            });
        }
    }
}
