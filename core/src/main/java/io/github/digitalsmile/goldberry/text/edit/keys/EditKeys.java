package io.github.digitalsmile.goldberry.text.edit.keys;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.key.PrimaryModifier;

/// The editing key map — the whole of it, in one place.
///
/// ## Why this exists
///
/// Three editors ship in this toolkit and each had its own copy of this table:
/// [io.github.digitalsmile.goldberry.text.edit.Editor], `text-input`'s
/// `TextField` and `text-area`'s `TextAreaBox`. They agreed key for key because
/// each was written by reading the last, which is agreement by inheritance
/// rather than by construction — and a toolkit whose two editors disagree about
/// what `Ctrl+Shift+Z` does is a toolkit with a bug in one of them
/// ([ADR-0376]).
///
/// Converging the *editors* would mean the controls holding an
/// [io.github.digitalsmile.goldberry.text.edit.Editor] instead of their own state
/// machines, which is a rewrite of two controls with a hundred golden images
/// behind them. Converging the *keyboard* costs this file, because the keyboard
/// was the part that was identical.
///
/// ## What it does not decide
///
/// Whether the editor is read-only, whether it has a selection, whether the
/// clipboard has anything in it, or whether the key should be consumed. This
/// answers one question — what did that key ask for — and every editor answers
/// the rest for itself. [EditCommand.Simple#isEdit()] is the one piece of that
/// which is shared, because "which of these change the text" was the other list
/// being kept in three places.
public final class EditKeys {

    private EditKeys() {}

    /// What `event` asks for on `surface`, or null for a key this map has no
    /// meaning for.
    ///
    /// Null is the important half of the contract: `Tab` still moves focus,
    /// `Escape` still closes what it closes, and `Enter` in a field still reaches
    /// the form's default button — every one of those is a key an editor must
    /// *not* take.
    public static @Nullable EditCommand of(KeyEvent event, EditSurface surface) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(surface, "surface");
        if (event.kind() != KeyEvent.Kind.PRESSED) {
            return null;
        }
        var modifiers = event.modifiers();
        var word = modifiers.control();
        var extend = modifiers.shift();
        // The accelerators are on the **platform's** modifier -- `Cmd+C` on
        // macOS, `Ctrl+C` elsewhere (ADR-0378). Word movement stays on `Ctrl`,
        // which is what it is on Linux and Windows; macOS's own `Alt+Left` is a
        // second map and its own entry.
        var accelerator = modifiers.has(PrimaryModifier.current());

        // The accelerators first, so `Ctrl+A` is "select all" here rather than
        // reaching the window's shortcut map -- which is what the focused
        // chain declining an event before the shortcuts is for. `Alt` excluded:
        // `Ctrl+Alt` is AltGr on a European layout, and AltGr+V is a character.
        if (accelerator && !modifiers.alt()) {
            var pressed =
                    switch (event.key()) {
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
