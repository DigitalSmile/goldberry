package io.github.digitalsmile.goldberry.widgets.form.textinput;

import io.github.digitalsmile.goldberry.backend.Cursor;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.Extent;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.Measured;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.input.TextEvent;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.Insets;
import io.github.digitalsmile.goldberry.natives.yoga.PositionType;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

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
/// text-input          this node. Clips, takes the focus, the keys and the pointer
/// ├── text-selection  the highlight, behind the text
/// ├── text-value      the text, or the placeholder
/// └── text-caret      the insertion point
/// ```
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
/// @param display     the text to draw — already masked, if the field masks
/// @param placeholder whether `display` is the placeholder
/// @param edit        where the caret and the selection are, in **display**
///                    offsets
/// @param focused     whether this field has the keyboard
/// @param caretShown  whether this is the lit half of the blink
/// @param disabled    whether it refuses everything and matches `:disabled`
/// @param readOnly    whether it takes the caret but no edits
/// @param attributes  the `id` and classes the document wrote
/// @param editor      what to tell about a key, a click or a measurement
record TextField(
        String display, boolean placeholder, TextEdit edit, boolean focused, boolean caretShown,
        boolean disabled, boolean readOnly, Attributes attributes, TextEditor editor)
        implements Widget.Leaf, Styled, Paints, Handles, Measured {

    /// How wide the caret is, in logical pixels.
    ///
    /// In Java rather than in a stylesheet for [io.github.digitalsmile.goldberry.Overlay#WINDOW_MARGIN]'s
    /// reason: §8's subset gives a node its width through `width`, and this node's
    /// width is set here in the same call that sets its position — a stylesheet
    /// that disagreed would move the caret rather than resize it. One pixel is
    /// what every desktop draws; a theme that wants a fat caret is a
    /// `--gb-caret-width` token and a design-system decision (Principle 3).
    private static final double CARET_WIDTH = 1;

    @Override
    public String cssType() {
        return "text-input";
    }

    @Override
    public String id() {
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
            default -> {
            }
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
    @Override
    public void onKey(KeyEvent event) {
        if (disabled || event.kind() != KeyEvent.Kind.PRESSED) {
            return;
        }
        var modifiers = event.modifiers();
        var word = modifiers.control();
        var extend = modifiers.shift();

        // The accelerators first: Ctrl+A is "select all" here and must not reach
        // the window's shortcut map, which is exactly what PointerRouter's
        // "the focused chain declines it first" ordering is for.
        if (modifiers.control() && !modifiers.alt()) {
            var handled = switch (event.key()) {
                case A -> editor.selectAll();
                case C -> editor.copy();
                case X -> !readOnly && editor.cut();
                case V -> !readOnly && editor.paste();
                case Z -> !readOnly && (modifiers.shift() ? editor.redo() : editor.undo());
                case Y -> !readOnly && editor.redo();
                default -> false;
            };
            if (handled) {
                event.consume();
                return;
            }
        }

        var handled = switch (event.key()) {
            case LEFT -> editor.move(TextEditor.Motion.LEFT, word, extend);
            case RIGHT -> editor.move(TextEditor.Motion.RIGHT, word, extend);
            // A single-line field has one line, so Up and Home are the same
            // movement -- and Up must still be taken, or it would walk out of a
            // vertical focus scope from a field somebody is editing.
            case HOME, UP -> editor.move(TextEditor.Motion.START, word, extend);
            case END, DOWN -> editor.move(TextEditor.Motion.END, word, extend);
            case BACKSPACE -> !readOnly && editor.deleteBefore(word);
            case DELETE -> !readOnly && editor.deleteAfter(word);
            default -> false;
        };
        if (handled) {
            event.consume();
        }
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

    // --- drawing --------------------------------------------------------------

    @Override
    public List<Widget> children() {
        return List.of(
                new TextSelection(focused && edit.hasSelection()),
                new TextValue(display, placeholder),
                new TextCaret(focused && caretShown && !edit.hasSelection()));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var paragraph = context.paragraph(style, display);
        var length = display.length();

        // Stashed for the pointer, which arrives outside a render pass and cannot
        // shape anything for itself. The same move `scroll` makes with the clock:
        // render is the only place a widget is handed what it needs to measure.
        var padding = leftPadding(style);
        var offset = editor.laidOut(paragraph, padding);

        // Every child's `left` carries the padding, because an absolutely
        // positioned box here is placed against the **border** box while the clip
        // is the padding box. Without it the first character of every field is
        // drawn under the left padding and clipped away -- which is exactly what
        // the Forms screen's first golden showed.

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
        var line = StyleLength.points((float) paragraph.font().lineHeight());

        var selection = children.get(0)
                .position(PositionType.ABSOLUTE)
                .inset(new Insets(
                        StyleLength.UNDEFINED,
                        StyleLength.UNDEFINED,
                        StyleLength.UNDEFINED,
                        StyleLength.points((float) (padding
                                + paragraph.widthBetween(0, clamp(edit.start(), length)) - offset))))
                .size(StyleLength.points((float) paragraph.widthBetween(
                                clamp(edit.start(), length), clamp(edit.end(), length))),
                        line);

        // Only the left edge is pinned. An absolute box with no top or bottom is
        // placed by its parent's alignment, so `align-items: center` on the field
        // is what puts the text on the vertical middle -- and the field keeps one
        // way of saying that rather than two. All three children are placed the
        // same way, and the two that are not text take their height from the line
        // rather than from the control.
        var value = children.get(1)
                .position(PositionType.ABSOLUTE)
                .inset(new Insets(
                        StyleLength.UNDEFINED,
                        StyleLength.UNDEFINED,
                        StyleLength.UNDEFINED,
                        StyleLength.points((float) (padding - offset))));

        var caret = children.get(2)
                .position(PositionType.ABSOLUTE)
                .inset(new Insets(
                        StyleLength.UNDEFINED,
                        StyleLength.UNDEFINED,
                        StyleLength.UNDEFINED,
                        StyleLength.points((float) (padding
                                + paragraph.widthBetween(0, clamp(edit.caret(), length)) - offset))))
                .size(StyleLength.points((float) CARET_WIDTH), line);

        return Box.of().style(style)
                .children(selection, value, caret)
                // The I-beam over the whole field and not only over the text:
                // the padding is part of the field, clicking it puts the caret
                // somewhere, and a pointer that changed shape over the gap would
                // be saying that the gap is not the field.
                .cursor(disabled ? Cursor.DEFAULT : Cursor.TEXT)
                // Read by Yoga for sizing and by the painter as a clip
                // (ADR-0114). Without it a field would draw its text over the
                // control beside it the moment the text outgrew the box.
                .overflow(io.github.digitalsmile.goldberry.natives.yoga.Overflow.HIDDEN);
    }

    /// The field's left padding in logical pixels, or 0 when the style gives none
    /// in points.
    ///
    /// A percentage padding on a text field is not something §3 asks for and not
    /// something this can resolve without the width Yoga has not computed yet, so
    /// it reads as zero rather than as a guess.
    private static double leftPadding(ComputedStyle style) {
        return style.padding().left() instanceof StyleLength.Points points ? points.value() : 0;
    }

    private static int clamp(int offset, int length) {
        return Math.clamp(offset, 0, length);
    }
}
