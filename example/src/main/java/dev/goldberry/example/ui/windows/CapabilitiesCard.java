package dev.goldberry.example.ui.windows;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Goldberry;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.platform.Capability;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.text.Text;

/// What this build of the native library can ask the desktop, one line per
/// [Capability].
///
/// Asked on a press rather than when the card is built, so a picture of the
/// card is the same on every machine.
///
/// Read more: [Capabilities](https://goldberry.dev/docs/guide/windows.html#capabilities).
///
/// @param ask where the answer comes from: `Goldberry::capabilities`, or a fixed
///            set in a test
public record CapabilitiesCard(Supplier<Set<Capability>> ask) implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "windows-capabilities";

    /// The card over the running build's own answer.
    public CapabilitiesCard() {
        this(Goldberry::capabilities);
    }

    @Override
    public State<?> createState() {
        return new CapabilitiesState();
    }

    static final class CapabilitiesState extends State<CapabilitiesCard> {

        /// The answer, or null until asked.
        private @Nullable Set<Capability> answer;

        @Override
        public Widget build(BuildContext context) {
            var known = answer;
            var lines = known == null
                    ? List.<Widget>of(new Text("Not asked yet.", Attributes.NONE.classes("readout")))
                    : Arrays.stream(Capability.values())
                            .<Widget>map(capability -> new Text(
                                    (known.contains(capability) ? "yes  " : "no   ") + capability.name(),
                                    Attributes.NONE.classes("readout")))
                            .toList();
            return new ShowcaseCard(
                            ID,
                            "Capabilities",
                            "A desktop integration is compiled in only where the build machine had its headers;"
                                    + " elsewhere the call answers that the desktop does not say."
                                    + " Goldberry.capabilities() tells the two apart.",
                            DocLink.to("guide/windows", "capabilities"))
                    .of(
                            new Button(
                                            "Ask this build",
                                            () -> setState(() ->
                                                    answer = widget().ask().get()))
                                    .id("capabilities-ask"),
                            new Column(
                                    lines,
                                    Attributes.NONE.id("capabilities-list").classes("readout-lines")));
        }
    }
}
