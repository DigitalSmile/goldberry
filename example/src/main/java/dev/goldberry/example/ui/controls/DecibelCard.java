package dev.goldberry.example.ui.controls;

import java.util.Locale;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.Scale;
import dev.goldberry.widgets.controls.slider.Slider;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.text.Text;

/// A fader with a decibel taper, and the value it settled at when the drag
/// ended.
///
/// `change` arrives on every step of a drag and `commit` once, when the gesture
/// ends: work that should not run per step, such as a seek, belongs on the
/// second. Both values are this card's own.
///
/// Read more: [`slider`](https://goldberry.dev/docs/components/values.html#slider).
public record DecibelCard() implements Widget.Stateful {

    @Override
    public State<?> createState() {
        return new DecibelState();
    }

    /// The gain as it moves, and as it was last settled.
    static final class DecibelState extends State<DecibelCard> {

        private double gain = 0.5;
        private double settled = 0.5;

        @Override
        public Widget build(BuildContext context) {
            return new ShowcaseCard(
                            "values-decibels",
                            "A fader in decibels, and commit",
                            "scale=\"db\" places a gain linearly in decibels, so half gain sits 90% of the way up."
                                    + " change arrives on every step of a drag, and commit once when it ends: drag,"
                                    + " and the second line moves only when you let go.",
                            DocLink.to("components/values", "slider"))
                    .of(
                            new Row(new Slider(0, 1, gain, 0, value -> setState(() -> gain = value))
                                            .scale(Scale.decibels())
                                            .format("%.2f")
                                            .onCommit(value -> setState(() -> settled = value))
                                            .withAttributes(Attributes.NONE
                                                    .id("decibel-fader")
                                                    .classes("vertical")))
                                    .id("decibel-faders"),
                            new Text(
                                            String.format(Locale.ROOT, "Moving: %.2f", gain),
                                            Attributes.NONE.classes("caption"))
                                    .id("decibel-moving"),
                            new Text(
                                            String.format(Locale.ROOT, "Settled: %.2f", settled),
                                            Attributes.NONE.classes("caption"))
                                    .id("decibel-settled"));
        }
    }
}
