package dev.goldberry.example.ui.controls;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.pressable.Pressable;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.text.Text;

/// Two release rows made pressable, with no button drawn round them, and a line
/// saying which was opened last.
///
/// What was opened is this card's own, because nothing else in the window reads
/// it.
///
/// Read more: [`pressable`](https://goldberry.dev/docs/components/buttons.html#pressable).
public record PressableCard() implements Widget.Stateful {

    @Override
    public State<?> createState() {
        return new PressableState();
    }

    /// The release opened last.
    static final class PressableState extends State<PressableCard> {

        private String opened = "";

        private Widget release(String id, String version, String when, boolean disabled) {
            return new Pressable(
                            "Open release " + version,
                            () -> setState(() -> opened = version),
                            new Row(
                                            new Text(version, Attributes.NONE.classes("body-strong")),
                                            new Text(when, Attributes.NONE.classes("caption")))
                                    .styled("release-line"))
                    .disabled(disabled)
                    .withAttributes(Attributes.NONE.id(id).classes("release-row"));
        }

        @Override
        public Widget build(BuildContext context) {
            return new ShowcaseCard(
                            "buttons-pressable",
                            "Anything, pressable",
                            "A pressable makes a row or a picture behave as a button without drawing one: a Tab"
                                    + " stop, a click, Space and Enter. It has no words a reader can use, so it must"
                                    + " be named. Press the first row.",
                            DocLink.to("components/buttons", "pressable"))
                    .of(
                            new Column(
                                    release("release-current", "2.4.0", "Released on Tuesday", false),
                                    release("release-sealed", "2.3.0", "Withdrawn, and disabled", true)),
                            new Text(
                                            opened.isEmpty()
                                                    ? "No release opened yet."
                                                    : "Opened release " + opened + ".",
                                            Attributes.NONE.classes("caption"))
                                    .id("release-opened"));
        }
    }
}
