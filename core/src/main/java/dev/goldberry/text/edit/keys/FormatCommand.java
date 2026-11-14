package dev.goldberry.text.edit.keys;

import java.util.Objects;

import dev.goldberry.text.mark.MarkKind;

/// What a formatting key asks a rich editor to do: [EditKeys#format]'s answer.
///
/// ```java
/// switch (EditKeys.format(event)) {
///     case FormatCommand.Toggle(var style) -> edit = edit.toggle(style);
///     case FormatCommand.Simple.CLEAR -> edit = edit.clearFormatting();
///     case FormatCommand.Simple.LINK -> openLinkEditor();
///     case null -> { }                           // not a formatting key
/// }
/// ```
///
/// Apart from [EditCommand], so a plain field never sees one and `Ctrl+B` stays
/// free for an application around a `text-input`.
///
/// Read more: [Selection and editing](https://goldberry.dev/docs/guide/text.html#selection-and-editing).
public sealed interface FormatCommand {

    /// One of the styles turned on or off.
    ///
    /// @param style which
    record Toggle(MarkKind.Style style) implements FormatCommand {

        public Toggle {
            Objects.requireNonNull(style, "style");
        }
    }

    /// The formatting commands that take no style.
    enum Simple implements FormatCommand {

        /// Every style off. `Ctrl+Shift+N`.
        CLEAR,

        /// A link asked for, which needs an address. `Ctrl+K`.
        LINK
    }
}
