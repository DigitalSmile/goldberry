package dev.goldberry.widgets.form.richinput;

import org.jspecify.annotations.Nullable;

import dev.goldberry.paint.Frame;
import dev.goldberry.paint.Painter;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.text.Paragraph;
import dev.goldberry.text.edit.Editor;

/// What a `rich-input` draws in its content box: the editor's selection, text
/// and caret, and the placeholder under them while the text is empty.
///
/// A record so that two frames with nothing changed hold equal painters and
/// the box is not damaged: `revision` is the state's count of changes, and it
/// moves on every edit, caret move, focus change and resize.
///
/// @param editor      the state's editor
/// @param revision    the state's change count
/// @param ink         the editor's colours
/// @param caret       whether the caret is drawn
/// @param placeholder what to draw while the text is empty, or null for nothing
/// @param hint        the placeholder's colour
record RichInputPainter(
        Editor editor,
        long revision,
        Editor.Ink ink,
        boolean caret,
        @Nullable Paragraph placeholder,
        int hint) implements Painter {

    @Override
    public void paint(Frame frame, LogicalSize size) {
        var words = placeholder;
        if (words != null && editor.text().isEmpty() && !editor.isComposing()) {
            words.paint(frame, 0, 0, size.width(), hint);
        }
        editor.paint(frame, 0, 0, ink, caret);
    }
}
