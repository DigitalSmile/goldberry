package dev.goldberry.example.ui.text;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.form.textarea.TextArea;
import dev.goldberry.widgets.form.textinput.TextInput;
import dev.goldberry.widgets.text.Text;

/// The editor under every field: a line to copy from, a field to paste into, and
/// a text area with its hard lines numbered.
///
/// Static text cannot be selected, so the line to copy is a read-only field. The
/// two editable values are this card's own, because nothing else in the window
/// reads them.
///
/// Read more: [Selection and editing](https://goldberry.dev/docs/guide/text.html#selection-and-editing).
public record SelectionCard() implements Widget.Stateful {

    /// What the read-only field holds.
    static final String QUOTE = "Not all those who wander are lost.";

    /// What the text area starts with: short lines and one long enough to wrap,
    /// so the gutter has a soft break to number around.
    static final String VERSE = """
            All that is gold does not glitter,
            Not all those who wander are lost;
            The old that is strong does not wither, deep roots are not reached by the frost.
            From the ashes a fire shall be woken""";

    @Override
    public State<?> createState() {
        return new SelectionState();
    }

    /// The pasted line and the verse.
    static final class SelectionState extends State<SelectionCard> {

        private String pasted = "";
        private String verse = VERSE;

        @Override
        public Widget build(BuildContext context) {
            return new ShowcaseCard(
                            "text-selection",
                            "Selection and editing",
                            "Under every field is an editor: arrows and Ctrl+arrows, Shift to extend, undo and"
                                    + " redo, cut, copy and paste. Static text cannot be selected, so copy from the"
                                    + " read-only field and paste into the one below it.",
                            DocLink.to(TextChapter.GUIDE.page(), "selection-and-editing"))
                    .of(
                            caption("Read-only: select with the pointer or Shift+arrows, then Ctrl+C."),
                            new TextInput(QUOTE, _ -> {})
                                    .readOnly(true)
                                    .withAttributes(Attributes.NONE.id("text-copy-from")),
                            caption("Editable: paste with Ctrl+V, and Ctrl+Z takes it back."),
                            new TextInput(pasted, value -> setState(() -> pasted = value))
                                    .placeholder("Paste here")
                                    .withAttributes(Attributes.NONE.id("text-paste-into")),
                            caption("A text area is an editor too. Its gutter numbers the hard lines where the wrap put"
                                    + " them."),
                            new TextArea(verse, value -> setState(() -> verse = value))
                                    .rows(5)
                                    .gutter(true)
                                    .withAttributes(Attributes.NONE.id("text-editor-area")));
        }

        private static Text caption(String text) {
            return new Text(text, Attributes.NONE.classes("caption"));
        }
    }
}
