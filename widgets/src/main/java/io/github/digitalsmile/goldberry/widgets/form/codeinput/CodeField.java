package io.github.digitalsmile.goldberry.widgets.form.codeinput;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.Cursor;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The node a stylesheet calls `code-input`, and everything that needs a frame.
///
/// [CodeInput] is stateful and styles nothing, so this carries the CSS type, the
/// `id` and the classes — `text-input`'s arrangement and for its reason: a
/// stateful widget that was also styled would put two `code-input` nodes in the
/// cascade, one inside the other, and every rule would apply twice.
///
/// ## What it is made of
///
/// ```
/// code-input          this node. Takes the focus, the keys and the committed text
/// └── code-group      [CodeGroup] — half the boxes, or all of them
///     └── code-box    [CodeBox] ×length — a character, or nothing
/// ```
///
/// **One Tab stop, and one textbox.** §4: "six boxes are a drawing, not six
/// fields, and announcing them separately would be a lie". So the boxes are
/// parts with no focus and no semantics, this node is `Role.TEXT_FIELD`, and its
/// value is the whole code.
///
/// ## Nothing here is absolutely positioned
///
/// Which is the difference between this and every other field in §4. A
/// `text-input` places its caret and its selection by measuring shaped text,
/// so Yoga is told where they go; a code box is a box in a row, so flexbox
/// places all of them and this node describes a tree rather than a geometry.
///
/// @param edit       what is held and which box is next
/// @param masked     whether the boxes draw bullets instead of characters
/// @param focused    whether this field has the keyboard
/// @param disabled   whether it refuses everything and matches `:disabled`
/// @param attributes the `id` and classes the document wrote
/// @param editor     what to tell about a key or a focus change
record CodeField(
        CodeEdit edit, boolean masked, boolean focused, boolean disabled, Attributes attributes, CodeEditor editor)
        implements Widget.Leaf, Styled, Paints, Handles, Semantics {

    /// `U+2022 BULLET`, the character `text-input`'s mask draws, for its reason:
    /// an asterisk is a footnote marker and sits on the cap line, so a row of
    /// them reads as superscript.
    private static final String BULLET = "•";

    @Override
    public String cssType() {
        return "code-input";
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
    public void onFocusChanged(boolean gained, boolean fromKeyboard) {
        editor.focusChanged(gained, fromKeyboard);
    }

    // --- the keyboard ---------------------------------------------------------

    /// The four keys §4 gives this widget, and nothing else.
    ///
    /// Unlike `text-input`, **the arrows are not consumed**. A text field owns
    /// them because it has a caret they move; this has one insertion point that
    /// is a function of what is filled, so `Left` here would either do nothing
    /// visible or move a ring the next keystroke would move back. Leaving them
    /// alone is what lets a code field sit in an arrow-navigated scope — a
    /// `wizard` step, a `radio-group`'s neighbour — and behave like the single
    /// control it announces itself as.
    @Override
    public void onKey(KeyEvent event) {
        if (disabled || event.kind() != KeyEvent.Kind.PRESSED) {
            return;
        }
        var modifiers = event.modifiers();
        if (modifiers.control() && !modifiers.alt()) {
            // Paste, and **no copy or cut**. §4 asks for the paste by name; it
            // asks for no way out, and a `mask`ed code must not have one for
            // `password`'s reason. A code is typed once and read from somewhere
            // else — there is nothing in a form's code field worth taking to the
            // clipboard, and offering it on an unmasked field only would be a
            // control whose keys depend on how it is drawn.
            if (event.key() == Key.V && editor.paste()) {
                event.consume();
            }
            return;
        }
        if (!modifiers.none() && !modifiers.shift()) {
            return;
        }
        var handled =
                switch (event.key()) {
                    case BACKSPACE, DELETE -> editor.backspace();
                    case ESCAPE -> editor.clear();
                    default -> false;
                };
        if (handled) {
            event.consume();
        }
    }

    /// Committed text — one character typed, or a whole code the platform
    /// delivered in one event.
    @Override
    public void onText(TextEvent event) {
        if (disabled || event.text().isEmpty()) {
            return;
        }
        if (editor.type(event.text())) {
            event.consume();
        }
    }

    // --- drawing --------------------------------------------------------------

    /// The boxes, in one group or two.
    ///
    /// Two when the length is **even**, which is §2's "group gap 16 at the
    /// midpoint when `length` is even" — see [CodeGroup] for why the split is
    /// here rather than in a selector.
    @Override
    public List<Widget> children() {
        var boxes = new ArrayList<Widget>(edit.length());
        for (var i = 0; i < edit.length(); i++) {
            var held = edit.boxAt(i);
            boxes.add(new CodeBox(masked && !held.isEmpty() ? BULLET : held, focused && i == edit.caret()));
        }
        if (edit.length() % 2 != 0) {
            return List.of(new CodeGroup(boxes));
        }
        var half = edit.length() / 2;
        return List.of(new CodeGroup(boxes.subList(0, half)), new CodeGroup(boxes.subList(half, boxes.size())));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of()
                .style(style)
                .children(children.toArray(Box[]::new))
                // The I-beam over the whole field, `text-input`'s reason
                // unchanged: clicking anywhere in it puts the keyboard somewhere,
                // and a pointer that only changed shape over a box would be
                // saying the gaps are not the field.
                .cursor(disabled ? Cursor.DEFAULT : Cursor.TEXT);
    }

    // --- semantics ------------------------------------------------------------

    @Override
    public Role role() {
        return Role.TEXT_FIELD;
    }

    /// No name of its own: `field` supplies the label, which is the whole point
    /// of §4 having one.
    @Override
    public @Nullable String accessibleName() {
        return null;
    }
}
