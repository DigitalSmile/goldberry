package dev.goldberry.example.ui.input;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.form.textinput.TextInput;
import dev.goldberry.widgets.text.Text;

/// Three fields in Tab order, and buttons that move focus from Java with
/// `host.focus(id, fromKeyboard)`, which says whether it moved.
///
/// Read more: [Focus](https://goldberry.dev/docs/guide/input.html#focus).
public record FocusCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "input-focus";

    @Override
    public State<?> createState() {
        return new FocusState();
    }

    static final class FocusState extends State<FocusCard> {

        private @Nullable Host host;

        private String answer = "Tab moves through the fields in order; Shift+Tab goes back.";

        @Override
        public Widget build(BuildContext context) {
            host = context.host().orElse(null);
            return new ShowcaseCard(
                            ID,
                            "Focus",
                            "One widget has the keyboard. A press or Tab moves it, and host.focus(id) moves it"
                                    + " from Java, refused for a node that is disabled or behind a modal. A ring"
                                    + " is drawn only when focus came from the keyboard.",
                            DocLink.to("guide/input", "focus"))
                    .of(
                            new TextInput("", _ -> {}).placeholder("First").id("focus-first"),
                            new TextInput("", _ -> {}).placeholder("Second").id("focus-second"),
                            new TextInput("", _ -> {})
                                    .placeholder("Disabled, so never focused")
                                    .disabled(true)
                                    .id("focus-disabled"),
                            new Row(
                                    List.of(
                                            new Button("Focus the first", () -> focus("focus-first"))
                                                    .id("focus-to-first"),
                                            new Button("Focus the second", () -> focus("focus-second"))
                                                    .id("focus-to-second"),
                                            new Button("The disabled one", () -> focus("focus-disabled"))
                                                    .id("focus-to-disabled")),
                                    Attributes.NONE.classes("toolbar")),
                            new Text(answer, Attributes.NONE.id("focus-answer").classes("readout")));
        }

        private void focus(String id) {
            var window = host;
            var moved = window != null && window.focus(id, true);
            setState(() -> answer = "host.focus(\"" + id + "\") → " + (moved ? "moved" : "refused"));
        }
    }
}
