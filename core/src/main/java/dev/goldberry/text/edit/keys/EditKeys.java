package dev.goldberry.text.edit.keys;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.key.PrimaryModifier;

/// The editing key map: what a key asks an editor to do.
///
/// ```java
/// @Override
/// public void onKey(KeyEvent event) {
///     var command = EditKeys.of(event, EditSurface.FIELD);
///     if (command != null && perform(command)) {
///         event.consume();
///     }
/// }
/// ```
///
/// One table serves `text-input`, `text-area` and the canvas `Editor`, so the
/// three cannot disagree about what `Ctrl+Shift+Z` does. The accelerators —
/// select all, copy, cut, paste, undo and redo — are on the desktop's own
/// modifier, `Cmd` on macOS and `Ctrl` elsewhere; word movement stays on `Ctrl`
/// everywhere. Arrows move by character or, with `Ctrl`, by word; `Home` and
/// `End` are the ends of the visual line, or of the whole text with `Ctrl`;
/// `Shift` with any movement extends the selection. What `Up`, `Down`, the page
/// keys and `Enter` mean depends on the [EditSurface].
///
/// The map decides only what a key asked for. Whether the editor is read-only,
/// whether it has a selection, whether the clipboard holds anything, and whether
/// to consume the key are each editor's own answers; [EditCommand.Simple#isEdit()]
/// is the one piece of that which is shared.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#keyboard).
public final class EditKeys {

    private EditKeys() {}

    /// What `event` asks for on `surface`, or null for a key this map has no
    /// meaning for.
    ///
    /// Null is the important half of the contract: `Tab` still moves focus,
    /// `Escape` still closes what it closes, and `Enter` in a field still reaches
    /// the form's default button — every one of those is a key an editor must
    /// not take. Only a press produces a command; a release never does.
    public static @Nullable EditCommand of(KeyEvent event, EditSurface surface) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(surface, "surface");
        if (event.kind() != KeyEvent.Kind.PRESSED) {
            return null;
        }
        var modifiers = event.modifiers();
        var word = modifiers.control();
        var extend = modifiers.shift();
        // The accelerators are on the desktop's own modifier -- `Cmd+C` on
        // macOS, `Ctrl+C` elsewhere. Word movement stays on `Ctrl`, which is
        // what it is on Linux and Windows; macOS's own `Alt+Left` would be a
        // second map.
        var accelerator = modifiers.has(PrimaryModifier.current());

        // The accelerators first, so `Ctrl+A` is "select all" here rather than
        // reaching the window's shortcut map -- which is what the focused
        // chain declining an event before the shortcuts is for. `Alt` excluded:
        // `Ctrl+Alt` is AltGr on a European layout, and AltGr+V is a character.
        if (accelerator && !modifiers.alt()) {
            var pressed = switch (event.key()) {
                case A -> EditCommand.Simple.SELECT_ALL;
                case C -> EditCommand.Simple.COPY;
                case X -> EditCommand.Simple.CUT;
                case V -> EditCommand.Simple.PASTE;
                case Z -> extend ? EditCommand.Simple.REDO : EditCommand.Simple.UNDO;
                case Y -> EditCommand.Simple.REDO;
                default -> null;
            };
            if (pressed != null) {
                return pressed;
            }
        }

        return switch (event.key()) {
            case LEFT -> new EditCommand.Move(Motion.LEFT, word, extend);
            case RIGHT -> new EditCommand.Move(Motion.RIGHT, word, extend);
            // `Ctrl` jumps to the ends of the whole text; plain `Home` and `End`
            // are the ends of the *visual* line, which is a question only a
            // layout can answer.
            case HOME -> new EditCommand.Move(word ? Motion.DOCUMENT_START : Motion.LINE_START, word, extend);
            case END -> new EditCommand.Move(word ? Motion.DOCUMENT_END : Motion.LINE_END, word, extend);
            // On a field there is one line, so `Up` is the start of it -- and it
            // must still be taken, or `Up` would walk out of a vertical focus
            // scope from a field somebody is editing.
            case UP ->
                surface.isVertical()
                        ? new EditCommand.MoveLine(-1, false, extend)
                        : new EditCommand.Move(Motion.DOCUMENT_START, word, extend);
            case DOWN ->
                surface.isVertical()
                        ? new EditCommand.MoveLine(1, false, extend)
                        : new EditCommand.Move(Motion.DOCUMENT_END, word, extend);
            case PAGE_UP -> surface.isVertical() ? new EditCommand.MoveLine(-1, true, extend) : null;
            case PAGE_DOWN -> surface.isVertical() ? new EditCommand.MoveLine(1, true, extend) : null;
            case BACKSPACE -> new EditCommand.Delete(true, word);
            case DELETE -> new EditCommand.Delete(false, word);
            // The one key whose meaning is the surface's: a multi-line control is
            // where a newline comes from, and a form's default button cannot have
            // one.
            case ENTER -> surface.takesNewlines() ? new EditCommand.Type("\n") : null;
            default -> null;
        };
    }
}
