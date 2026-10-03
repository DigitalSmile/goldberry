package dev.goldberry.example.ui.input;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Shortcut;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.form.textinput.TextInput;
import dev.goldberry.widgets.text.Text;

/// The window's accelerators, and one more that this card binds while it is on
/// screen and gives back when it leaves.
///
/// Read more: [Accelerators](https://goldberry.dev/docs/guide/input.html#accelerators).
public record AcceleratorsCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "input-accelerators";

    /// What the card binds: `Cmd+J` on macOS, `Ctrl+J` everywhere else.
    static final Shortcut OWN = Shortcut.primary(Key.J);

    /// What the showcase's window binds in its `start`, as a menu prints it.
    static final List<String[]> WINDOW = List.of(
            new String[] {"Ctrl+T", "switch light and dark"},
            new String[] {"Ctrl+D", "switch the density"},
            new String[] {"Ctrl+F", "the frame-time overlay"},
            new String[] {"Ctrl+O", "the unsaved-changes dialog"},
            new String[] {"Ctrl+K", "march a league"},
            new String[] {"Ctrl+Z", "turn back"},
            new String[] {"Ctrl+1 … Ctrl+0", "the first ten tabs"},
            new String[] {"Ctrl+Q", "quit"});

    @Override
    public State<?> createState() {
        return new AcceleratorsState();
    }

    static final class AcceleratorsState extends State<AcceleratorsCard> {

        /// The window the shortcut was bound on, or null until the first build
        /// that had one.
        private @Nullable Host bound;

        private int presses;

        private String typed = "";

        @Override
        public Widget build(BuildContext context) {
            if (bound == null) {
                context.host().ifPresent(host -> {
                    host.shortcut(OWN, () -> setState(() -> presses++), this);
                    bound = host;
                });
            }
            var rows = new ArrayList<Widget>();
            for (var accelerator : WINDOW) {
                rows.add(new Row(
                        List.of(
                                new Text(accelerator[0], Attributes.NONE.classes("accelerator-keys")),
                                new Text(accelerator[1], Attributes.NONE.classes("caption"))),
                        Attributes.NONE.classes("accelerator-row")));
            }
            var own = bound == null
                    ? "No window here, so " + OWN + " is not bound."
                    : OWN + " is bound by this card. Pressed " + presses + (presses == 1 ? " time." : " times.");
            return new ShowcaseCard(
                            ID,
                            "Accelerators",
                            "An accelerator belongs to the window and fires only after the focused widget declined"
                                    + " the key, so a field keeps its own Ctrl+A. Press " + OWN + " anywhere, even"
                                    + " in the field.",
                            DocLink.to("guide/input", "accelerators"))
                    .of(
                            new Column(
                                    rows, Attributes.NONE.id("accelerator-list").classes("accelerator-list")),
                            new Text(own, Attributes.NONE.id("accelerator-own").classes("readout")),
                            new TextInput(typed, value -> setState(() -> typed = value))
                                    .placeholder("Ctrl+A here selects the text")
                                    .id("accelerator-field"));
        }

        @Override
        protected void dispose() {
            var host = bound;
            if (host != null) {
                host.removeShortcut(OWN, this);
                bound = null;
            }
        }
    }
}
