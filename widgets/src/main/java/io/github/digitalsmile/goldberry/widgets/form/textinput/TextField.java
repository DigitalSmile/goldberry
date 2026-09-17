package io.github.digitalsmile.goldberry.widgets.form.textinput;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.event.PreeditEvent;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.handler.Measured;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.layout.Insets;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.layout.Position;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.Cursor;
import io.github.digitalsmile.goldberry.text.edit.TextEdit;
import io.github.digitalsmile.goldberry.text.edit.keys.EditCommand;
import io.github.digitalsmile.goldberry.text.edit.keys.EditKeys;
import io.github.digitalsmile.goldberry.text.edit.keys.EditSurface;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.form.parts.Caret;
import io.github.digitalsmile.goldberry.widgets.form.parts.Composing;
import io.github.digitalsmile.goldberry.widgets.form.parts.Highlight;
import io.github.digitalsmile.goldberry.widgets.form.parts.Underline;
import io.github.digitalsmile.goldberry.widgets.form.parts.Value;

/// The node a stylesheet calls `text-input`, and everything that needs a frame.
///
/// [TextInput] is stateful and styles nothing, so this carries the CSS type, the
/// `id` and the classes — the shape `scroll`, `tabs` and `select` already use,
/// and for their reason: a stateful widget that was also styled would put two
/// `text-input` nodes in the cascade, one inside the other, and every rule would
/// apply twice.
///
/// ## What it is made of
///
/// ```
/// text-input             this node. Clips, takes the focus, the keys and the pointer
/// ├── text-selection     the highlight, behind the text
/// ├── text-value         the text, or the placeholder
/// ├── text-caret         the insertion point
/// └── text-composition   the rule under what an input method is still assembling
/// ```
///
/// The fourth is drawn only while a composition is open, which is never on a
/// Latin keyboard — and while one is, the **highlight draws the converting
/// clause** rather than a selection, because a composition replaces the selection
/// when it commits and every platform's input method collapses it (ADR-0292).
///
/// All three children are **absolutely positioned by this node**, because where
/// they go is a measurement rather than a layout: a caret's x is the width of the
/// text before it, and only [Paints.Context#paragraph] can say what that is. Yoga
/// is told where they are; it is not asked.
///
/// ## The scroll offset is computed here and remembered above
///
/// A field narrower than its text scrolls, and the rule is "move as little as
/// possible to keep the caret in view". That needs the content width, which is
/// this box's width less its padding — and a box does not know its width during
/// `render`, because Yoga has not run yet. So it uses **the width the last frame
/// measured** ([Measured]), which is what ADR-0116 already decided a scroll view
/// does, and is wrong only on the first frame and on the frame a resize lands.
/// Neither is visible: both are followed immediately by another.
///
/// @param display     the text to draw — already masked, if the field masks, and
///                    with any composition spliced in
/// @param placeholder whether `display` is the placeholder
/// @param edit        where the caret and the selection are, in **display**
///                    offsets
/// @param composing   which part of `display` an input method has not finished
///                    with, in display offsets — [Composing#NONE] almost always
/// @param focused     whether this field has the keyboard
/// @param caretShown  whether this is the lit half of the blink
/// @param disabled    whether it refuses everything and matches `:disabled`
/// @param readOnly    whether it takes the caret but no edits
/// @param attributes  the `id` and classes the document wrote
/// @param editor      what to tell about a key, a click or a measurement
record TextField(
        String display,
        boolean placeholder,
        TextEdit edit,
        Composing composing,
        boolean focused,
        boolean caretShown,
        boolean disabled,
        boolean readOnly,
        Attributes attributes,
        TextEditor editor)
        implements Widget.Leaf,
                Styled,
                Paints,
                Handles,
                Measured,
                io.github.digitalsmile.goldberry.input.handler.Located,
                Semantics {

    /// Where the frame put this, handed straight to the state — which uses it
    /// only to anchor a popover, so [io.github.digitalsmile.goldberry.input.handler.Located]'s
    /// rule that a widget told where it is must not move itself holds trivially.
    @Override
    public void located(
            io.github.digitalsmile.goldberry.render.model.LogicalRect self,
            io.github.digitalsmile.goldberry.render.model.LogicalRect clip) {
        editor.located(self, clip);
    }

    @Override
    public String cssType() {
        return "text-input";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public boolean isFocusable() {
        return !disabled;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public void measured(Extent bounds, Extent part) {
        editor.measured(bounds);
    }

    @Override
    public void onFocusChanged(boolean gained, boolean fromKeyboard) {
        editor.focusChanged(gained, fromKeyboard);
    }

    // --- the pointer ----------------------------------------------------------

    /// Press to place the caret, drag to extend, and the click count to widen.
    ///
    /// The press places rather than the click, because a selection drag has to
    /// start from somewhere and the click has not happened yet when the drag
    /// does. Every editor works this way and it is why click-and-drag selects
    /// rather than selecting after the button comes up.
    @Override
    public void onPointer(PointerEvent event) {
        if (disabled) {
            return;
        }
        switch (event.kind()) {
            case PRESSED -> {
                if (event.button() != PointerEvent.Button.PRIMARY) {
                    return;
                }
                editor.pointerAt(event.local().x(), event.modifiers().shift(), event.clickCount());
                event.consume();
            }
            case MOVED -> {
                // A drag and not a hover. `dragX()` is NaN when no button is
                // down, which is the router reporting "no gesture" through the
                // arithmetic rather than through a flag (ADR-0075).
                //
                // The **button is not asked about here**, and testing it above
                // this switch was the bug that kept click-and-drag from ever
                // selecting anything: `PointerRouter.pointerMoved` builds its
                // event with a null button, because a motion is not a button
                // event — so a guard that demanded PRIMARY threw away every drag
                // before it arrived. Which button started the gesture is the
                // press's question and is asked there.
                if (!Double.isNaN(event.dragX())) {
                    // Always extending: a drag *is* a selection, and the shift
                    // key adds nothing to one.
                    editor.pointerAt(event.local().x(), true, 1);
                    event.consume();
                }
            }
            default -> {}
        }
    }

    // --- the keyboard ---------------------------------------------------------

    /// §4's editing keys, and the accelerators every platform binds on a field.
    ///
    /// **Everything here is consumed**, including the keys that do nothing — a
    /// field with focus owns its arrows, or `Left` inside one would walk the
    /// focus scope it sits in and `Home` would scroll the page behind it. The
    /// exceptions are `Tab`, which is focus traversal and must reach the router,
    /// and `Enter` and `Escape`, which belong to the form or the dialog around
    /// this and not to the field.
    /// The platform's text input is on while a field that can be typed into has
    /// the keyboard.
    ///
    /// Declared as well as set from [TextEditor#focusChanged], which is not
    /// belt and braces: the router asks this *before* the handlers run and the
    /// state corrects it afterwards, so the two orders agree and a field that is
    /// disabled between frames is still right (ADR-0285).
    @Override
    public boolean wantsTextInput() {
        return !disabled && !readOnly;
    }

    /// §4's editing keys, through the map all three editors read ([ADR-0376]).
    ///
    /// A field is [EditSurface#FIELD]: one line, so `Up` is the start of the text
    /// rather than a line above it — and it must still be taken, or `Up` would
    /// walk out of a vertical focus scope from a field somebody is editing.
    /// `Enter` is not taken at all, because a form's default button needs it.
    @Override
    public void onKey(KeyEvent event) {
        if (disabled) {
            return;
        }
        var command = EditKeys.of(event, EditSurface.FIELD);
        if (command == null) {
            return;
        }
        var handled =
                switch (command) {
                    case EditCommand.Simple simple -> simple(simple);
                    case EditCommand.Move(var motion, var word, var extend) ->
                        switch (motion) {
                            case LEFT -> editor.move(TextEditor.Motion.LEFT, word, extend);
                            case RIGHT -> editor.move(TextEditor.Motion.RIGHT, word, extend);
                            // One line: its start and the text's are the same
                            // place, and so are its end and the text's.
                            case LINE_START, DOCUMENT_START -> editor.move(TextEditor.Motion.START, word, extend);
                            case LINE_END, DOCUMENT_END -> editor.move(TextEditor.Motion.END, word, extend);
                        };
                    // Neither can reach a field: `FIELD` is not vertical and takes
                    // no newline, so the map produces neither.
                    case EditCommand.MoveLine ignored -> false;
                    case EditCommand.Type ignored -> false;
                    case EditCommand.Delete(var before, var word) ->
                        !readOnly && (before ? editor.deleteBefore(word) : editor.deleteAfter(word));
                };
        if (handled) {
            event.consume();
        }
    }

    /// The accelerators. `Ctrl+A` must not reach the window's shortcut map, which
    /// is exactly what `PointerRouter`'s "the focused chain declines it first"
    /// ordering is for.
    private boolean simple(EditCommand.Simple command) {
        if (readOnly && command.isEdit()) {
            return false;
        }
        return switch (command) {
            case SELECT_ALL -> editor.selectAll();
            case COPY -> editor.copy();
            case CUT -> editor.cut();
            case PASTE -> editor.paste();
            case UNDO -> editor.undo();
            case REDO -> editor.redo();
        };
    }

    /// Committed text — what the user actually typed, after the platform's
    /// layout, compose and IME handling (§7.1).
    @Override
    public void onText(TextEvent event) {
        if (disabled || readOnly || event.text().isEmpty()) {
            return;
        }
        if (editor.type(event.text())) {
            event.consume();
        }
    }

    /// The composition an input method is assembling — `docs/gaps.md` G16.
    ///
    /// Not an edit: see [TextEditor#compose]. A `password` refuses, so the event
    /// is left unconsumed and the field draws nothing inline.
    @Override
    public void onPreedit(PreeditEvent event) {
        if (disabled || readOnly) {
            return;
        }
        if (editor.compose(event.text(), event.caret(), event.start(), event.length())) {
            event.consume();
        }
    }

    /// Where this field's caret is, so the platform can place a candidate window
    /// beside it rather than over it (ADR-0289).
    @Override
    public java.util.Optional<io.github.digitalsmile.goldberry.render.model.LogicalRect> caretArea() {
        return editor.caretArea();
    }

    @Override
    public double caretOffsetIn(io.github.digitalsmile.goldberry.render.model.LogicalRect area) {
        return editor.caretOffset();
    }

    // --- drawing --------------------------------------------------------------

    @Override
    public List<Widget> children() {
        return List.of(
                // While a composition is open the highlight draws its converting
                // clause: there is no selection to draw, because a composition
                // replaces one when it commits.
                new Highlight(focused && (composing.hasClause() || (!composing.isActive() && edit.hasSelection()))),
                new Value(display, placeholder),
                new Caret(focused && caretShown && !edit.hasSelection()),
                new Underline(focused && composing.isActive()));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var paragraph = context.paragraph(style, display);
        var length = display.length();

        // Stashed for the pointer, which arrives outside a render pass and cannot
        // shape anything for itself. The same move `scroll` makes with the clock:
        // render is the only place a widget is handed what it needs to measure.
        var padding = padding(style.padding().left());
        // Read once and used three times: the caret's box, the room the scroll
        // offset must leave for it, and nothing else. A caret three pixels wide
        // whose field reserved one would be clipped at the end of the text
        // (ADR-0253).
        var caretWidth = context.length(
                io.github.digitalsmile.goldberry.widgets.form.Carets.WIDTH_TOKEN,
                io.github.digitalsmile.goldberry.widgets.form.Carets.WIDTH);
        // The alignment goes down with the paragraph, because the editor places the
        // caret and the highlight from it and the `Value` beside them draws from the
        // same resolved style ([ADR-0324]).
        var offset =
                editor.laidOut(paragraph, padding, padding(style.padding().right()), caretWidth, style.textAlign());

        // No child's `left` carries the padding any more. It used to: an
        // absolutely positioned box was placed against the **border** box while
        // the clip was the padding box, so without the compensation the first
        // character of every field was drawn under the left padding and clipped
        // away. `ContainingBlock` shifts every absolute child by its containing
        // block's padding now (ADR-0272), so adding it here as well would count
        // it twice and start the text a padding's width too far in.

        // A line tall, and centred by the field's `align-items` like the text is —
        // **not** pinned top and bottom. A caret that filled a 32-point control
        // would be nearly twice the height of the 18-point line it is sitting in,
        // which reads as a cursor from a terminal rather than as an insertion
        // point; and a highlight that filled it would extend above and below the
        // glyphs it is meant to be behind.
        //
        // The font's line height rather than a number in the stylesheet, because
        // it has to follow the text: a field at a larger `font-size` has a taller
        // line, and a CSS height that disagreed would be wrong at every size but
        // one.
        var line = Length.points((float) paragraph.font().lineHeight());

        // The highlight covers the selection, or the clause an input method is
        // converting when there is one -- never both, because there is never
        // both.
        var washStart = composing.hasClause() ? composing.clauseStart() : edit.start();
        var washEnd = composing.hasClause() ? composing.clauseEnd() : edit.end();
        var selection = children.get(0)
                .position(Position.ABSOLUTE)
                .inset(new Insets(Length.UNDEFINED, Length.UNDEFINED, Length.UNDEFINED, Length.points((float)
                        (paragraph.widthBetween(0, clamp(washStart, length)) - offset))))
                .size(
                        Length.points((float) paragraph.widthBetween(clamp(washStart, length), clamp(washEnd, length))),
                        line);

        // Only the left edge is pinned. An absolute box with no top or bottom is
        // placed by its parent's alignment, so `align-items: center` on the field
        // is what puts the text on the vertical middle -- and the field keeps one
        // way of saying that rather than two. All three children are placed the
        // same way, and the two that are not text take their height from the line
        // rather than from the control.
        var value = children.get(1)
                .position(Position.ABSOLUTE)
                .inset(new Insets(
                        Length.UNDEFINED, Length.UNDEFINED, Length.UNDEFINED, Length.points((float) -offset)));

        var caret = children.get(2)
                .position(Position.ABSOLUTE)
                .inset(new Insets(Length.UNDEFINED, Length.UNDEFINED, Length.UNDEFINED, Length.points((float)
                        (paragraph.widthBetween(0, clamp(edit.caret(), length)) - offset))))
                .size(Length.points((float) caretWidth), line);

        // The rule under the composition, sitting on the bottom of the line
        // rather than filling it: `bottom` is pinned instead of nothing, so the
        // field's `align-items` still decides where the line is and this sits at
        // the foot of it.
        var underline = children.get(3)
                .position(Position.ABSOLUTE)
                .inset(new Insets(Length.UNDEFINED, Length.UNDEFINED, Length.UNDEFINED, Length.points((float)
                        (paragraph.widthBetween(0, clamp(composing.start(), length)) - offset))))
                .size(
                        Length.points((float) paragraph.widthBetween(
                                clamp(composing.start(), length), clamp(composing.end(), length))),
                        Length.points((float) Underline.THICKNESS));

        return Box.of()
                .style(style)
                .children(selection, value, caret, underline)
                // The I-beam over the whole field and not only over the text:
                // the padding is part of the field, clicking it puts the caret
                // somewhere, and a pointer that changed shape over the gap would
                // be saying that the gap is not the field.
                .cursor(disabled ? Cursor.DEFAULT : Cursor.TEXT)
                // Read by Yoga for sizing and by the painter as a clip
                // (ADR-0114). Without it a field would draw its text over the
                // control beside it the moment the text outgrew the box.
                .overflow(io.github.digitalsmile.goldberry.layout.Overflow.HIDDEN);
    }

    /// One edge of the field's padding in logical pixels, or 0 when the style
    /// gives none in points.
    ///
    /// A percentage padding on a text field is not something §3 asks for and not
    /// something this can resolve without the width Yoga has not computed yet, so
    /// it reads as zero rather than as a guess.
    private static double padding(Length edge) {
        return edge instanceof Length.Points points ? points.value() : 0;
    }

    private static int clamp(int offset, int length) {
        return Math.clamp(offset, 0, length);
    }

    @Override
    public Role role() {
        return Role.TEXT_FIELD;
    }

    /// No name of its own: `field` supplies the label, which is the whole point of §4 having one.
    @Override
    public @Nullable String accessibleName() {
        return null;
    }
}
