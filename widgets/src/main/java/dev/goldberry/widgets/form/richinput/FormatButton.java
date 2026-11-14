package dev.goldberry.widgets.form.richinput;

import java.util.List;
import java.util.Objects;

import dev.goldberry.i18n.Labels;
import dev.goldberry.i18n.ToolkitLabel;
import dev.goldberry.text.edit.keys.FormatCommand;
import dev.goldberry.text.mark.MarkKind.Style;

/// One button of a `rich-input`'s formatting bar: an icon, the words a screen
/// reader hears and a tooltip shows, and the command it runs.
///
/// ```java
/// new RichInput(draft, onChange).formatting(List.of(
///         new FormatButton("bold", "Bold", new FormatCommand.Toggle(MarkKind.Style.BOLD)),
///         new FormatButton("link", "Link", FormatCommand.Simple.LINK)));
/// ```
///
/// [#defaults()] is the stock bar: bold, italic, underline, strike-through,
/// code, spoiler, link and clear formatting.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#keyboard).
///
/// @param icon    the name of a bundled icon
/// @param label   what the button is called
/// @param command what pressing it does: the same commands the formatting keys
///                give
public record FormatButton(String icon, String label, FormatCommand command) {

    public FormatButton {
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(command, "command");
        if (label.isBlank()) {
            throw new IllegalArgumentException("a formatting button has an icon and no words on it, so its label is"
                    + " the only name a screen reader has for it");
        }
    }

    /// The stock bar, in the toolkit's words as [Labels] gives them now.
    public static List<FormatButton> defaults() {
        return List.of(
                toggle("bold", ToolkitLabel.BOLD, Style.BOLD),
                toggle("italic", ToolkitLabel.ITALIC, Style.ITALIC),
                toggle("underline", ToolkitLabel.UNDERLINE, Style.UNDERLINE),
                toggle("strikethrough", ToolkitLabel.STRIKE, Style.STRIKE),
                toggle("code", ToolkitLabel.CODE, Style.CODE),
                toggle("eye-off", ToolkitLabel.SPOILER, Style.SPOILER),
                new FormatButton("link", Labels.text(ToolkitLabel.LINK), FormatCommand.Simple.LINK),
                new FormatButton(
                        "remove-formatting", Labels.text(ToolkitLabel.CLEAR_FORMATTING), FormatCommand.Simple.CLEAR));
    }

    private static FormatButton toggle(String icon, ToolkitLabel label, Style style) {
        return new FormatButton(icon, Labels.text(label), new FormatCommand.Toggle(style));
    }
}
