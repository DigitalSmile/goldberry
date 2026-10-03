package dev.goldberry.example.ui.overlays;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.controls.checkbox.Checkbox;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.form.textinput.TextInput;
import dev.goldberry.widgets.text.Text;

/// Tooltips: an attribute any widget takes, not a widget.
///
/// Read more: [Tooltips](https://goldberry.dev/docs/components/overlays.html#tooltips).
record TooltipsCard() implements Widget.Stateless {

    private static final ShowcaseCard CARD = new ShowcaseCard(
            "overlays-tooltips",
            "Tooltips",
            "A tooltip is an attribute any widget takes. Rest the pointer on one of these for half a second,"
                    + " or Tab to it: the tip shows above it and moves quickly between neighbours.",
            DocLink.to("components/overlays", "tooltips"));

    @Override
    public Widget build(BuildContext context) {
        return CARD.of(
                new Row(
                        List.of(
                                new Button("Save", () -> {})
                                        .tooltip("Writes the draft to disk")
                                        .id("tip-button"),
                                new Button("Share", () -> {})
                                        .styled("ghost")
                                        .tooltip("Copies a link to the clipboard")
                                        .id("tip-ghost"),
                                new Checkbox("Pack the rope", Checkbox.Value.CHECKED)
                                        .tooltip("Fifty feet of elven rope")
                                        .id("tip-check")),
                        Attributes.NONE.classes("toolbar")),
                new TextInput()
                        .placeholder("A name")
                        .tooltip("Plain text only, at most 320 wide")
                        .id("tip-input"),
                new Text(
                        "Even plain text takes one.",
                        Attributes.NONE.tooltip("Like this").classes("caption")));
    }
}
