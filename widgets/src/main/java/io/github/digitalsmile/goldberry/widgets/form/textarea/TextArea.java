package io.github.digitalsmile.goldberry.widgets.form.textarea;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.attr.Bindable;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// A multi-line text field — `docs/core-widgets.md` §4's `text-area`.
///
/// ```kdl
/// field label="Bio" { text-area bind="user.bio" rows=4 }
/// text-area rows=2 max-rows=8 placeholder="Say something"
/// ```
///
/// ## It is `text-input` with three differences
///
/// The editing model is the same one, unchanged:
/// [io.github.digitalsmile.goldberry.text.edit.TextEdit]
/// and its history are where §4's rules live, and they were written without a
/// single line about how many lines there are. What differs is only what a second
/// dimension makes different:
///
/// - **`Enter` inserts a newline** rather than reaching the form around it.
/// - **`Up` and `Down` move between lines**, keeping the column — which needs the
///   *layout* and not the model, because a wrapped line is a fact about a width.
/// - **It grows.** Between [#rows] and [#maxRows], and scrolls after that.
///
/// Everything else — selection, the clipboard, undo, word operations, the caret's
/// blink, `:invalid` through its `field` — is inherited rather than reimplemented.
///
/// ## Soft wrap, and what a line is
///
/// The text wraps at the control's width, which `Paragraph` has done since M1.
/// **Hard** lines are the ones somebody typed and are the model's; **soft** ones
/// are the wrap and belong to whatever laid the text out. `Home` and `End` go to
/// the ends of a *soft* line here, which is what every editor does and what a
/// reader means by "this line" — and `TextEdit.lineStart` is the hard version,
/// for the model's own use.
///
/// ## No `password`, and no filter
///
/// A masked multi-line field is not a thing, and a filter over a value with
/// newlines in it would be judging a document rather than a value. Both are
/// `text-input`'s and stay there.
///
/// @param value       the text when nothing is bound
/// @param source      the `bind=` value, or null
/// @param onChange    told the new text after every change the user makes
/// @param placeholder what to draw when it is empty
/// @param rows        how many lines tall it is when empty — its minimum
/// @param maxRows     how tall it may grow before it scrolls instead
/// @param maxLength   the most characters it will hold, or -1
/// @param fill        whether it takes the height its container gives it instead of
///                    growing to fit its text — see [#fill(boolean)]
/// @param readOnly    whether it takes a caret and a selection but no edits
/// @param disabled    whether it refuses focus and matches `:disabled`
/// @param attributes  the `id`, classes and key the document wrote
@Markup("text-area")
public record TextArea(
        String value,
        Observable<?> source,
        Consumer<String> onChange,
        String placeholder,
        int rows,
        int maxRows,
        int maxLength,
        boolean fill,
        boolean readOnly,
        boolean disabled,
        Attributes attributes)
        implements Widget.Stateful, Attributed<TextArea>, Bindable<TextArea> {

    /// What [#maxLength] means when there is no limit.
    public static final int UNLIMITED = -1;

    /// How tall a `text-area` is when a document does not say.
    ///
    /// Three, which is the smallest height that reads as "more than one line is
    /// expected here" — two looks like a single-line field that went wrong.
    public static final int DEFAULT_ROWS = 3;

    /// How far it grows before it scrolls, when a document does not say.
    ///
    /// Ten. A control that grew without limit would push the button below it off
    /// the bottom of a form, which is the one thing auto-grow must not do.
    public static final int DEFAULT_MAX_ROWS = 10;

    public TextArea {
        value = value == null ? "" : value;
        placeholder = placeholder == null ? "" : placeholder;
        attributes = attributes == null ? Attributes.NONE : attributes;
        if (rows < 1) {
            throw new IllegalArgumentException("a text-area is at least one line tall, and " + rows + " is not");
        }
        if (maxRows < rows) {
            throw new IllegalArgumentException(
                    "a text-area cannot grow to fewer lines than it starts at: rows=" + rows + " max-rows=" + maxRows);
        }
        if (maxLength < UNLIMITED) {
            throw new IllegalArgumentException("a maximum length is a count of characters or " + UNLIMITED
                    + " for no limit, and " + maxLength + " is neither");
        }
    }

    /// An empty area of the default height.
    public TextArea() {
        this("", null, null, "", DEFAULT_ROWS, DEFAULT_MAX_ROWS, UNLIMITED, false, false, false, Attributes.NONE);
    }

    /// An area holding `value`, reporting every change.
    public TextArea(String value, Consumer<String> onChange) {
        this(
                value,
                null,
                onChange,
                "",
                DEFAULT_ROWS,
                DEFAULT_MAX_ROWS,
                UNLIMITED,
                false,
                false,
                false,
                Attributes.NONE);
    }

    /// An area following a property. The Java spelling of `bind=`.
    public static TextArea of(Observable<?> source, Consumer<String> onChange) {
        return new TextArea(
                "",
                Objects.requireNonNull(source, "source"),
                onChange,
                "",
                DEFAULT_ROWS,
                DEFAULT_MAX_ROWS,
                UNLIMITED,
                false,
                false,
                false,
                Attributes.NONE);
    }

    /// This area with `text` shown when it is empty.
    public TextArea placeholder(String text) {
        return new TextArea(
                value, source, onChange, text, rows, maxRows, maxLength, fill, readOnly, disabled, attributes);
    }

    /// This area `lines` tall, growing to at most `most`.
    public TextArea rows(int lines, int most) {
        return new TextArea(
                value, source, onChange, placeholder, lines, most, maxLength, fill, readOnly, disabled, attributes);
    }

    /// This area `lines` tall, keeping its current maximum — raised to `lines` if
    /// that would otherwise be smaller.
    public TextArea rows(int lines) {
        return rows(lines, Math.max(lines, maxRows));
    }

    /// This area holding at most `characters`, or [#UNLIMITED].
    public TextArea maxLength(int characters) {
        return new TextArea(
                value, source, onChange, placeholder, rows, maxRows, characters, fill, readOnly, disabled, attributes);
    }

    /// This area taking a caret and a selection but no edits.
    public TextArea readOnly(boolean value) {
        return new TextArea(
                this.value, source, onChange, placeholder, rows, maxRows, maxLength, fill, value, disabled, attributes);
    }

    /// This area **sized by its container** rather than by its text.
    ///
    /// §4's `text-area` grows to fit what is typed into it, between [#rows] and
    /// [#maxRows], which is right for a field in a form: a control that took the
    /// height of a pane it happened to be in would leave a form full of holes.
    ///
    /// It is wrong for the other thing a multi-line field is — an **editor**. A pane
    /// holding a document wants the document to have the pane: the height comes from
    /// the layout, the text scrolls inside it, and how many lines fit is an answer
    /// rather than a setting. Without this, an editor in a `split-pane` was as tall
    /// as `max-rows` and left the rest of its side empty (ADR-0296).
    ///
    /// What changes: the box grows into whatever its parent gives it, and the
    /// visible-line count that decides scrolling comes from the **measured** height
    /// instead of from [#maxRows]. `rows` and `max-rows` are then ignored, and a
    /// container that gives no height at all is a control one line tall — which is
    /// flexbox being asked for something impossible rather than this being subtle.
    public TextArea fill(boolean value) {
        return new TextArea(
                this.value,
                source,
                onChange,
                placeholder,
                rows,
                maxRows,
                maxLength,
                value,
                readOnly,
                disabled,
                attributes);
    }

    /// This area, disabled or not.
    public TextArea disabled(boolean value) {
        return new TextArea(
                this.value, source, onChange, placeholder, rows, maxRows, maxLength, fill, readOnly, value, attributes);
    }

    /// What this area starts from — the bound value, or [#value()].
    public String resolved() {
        if (source == null) {
            return value;
        }
        var current = source.get();
        return current == null ? "" : String.valueOf(current);
    }

    /// Tells the application what it now holds.
    void report(String text) {
        if (onChange != null) {
            onChange.accept(text);
        }
    }

    @Override
    public TextArea bound(Observable<?> value) {
        return new TextArea(
                this.value,
                value,
                onChange,
                placeholder,
                rows,
                maxRows,
                maxLength,
                fill,
                readOnly,
                disabled,
                attributes);
    }

    @Override
    public TextArea withAttributes(Attributes value) {
        return new TextArea(
                this.value, source, onChange, placeholder, rows, maxRows, maxLength, fill, readOnly, disabled, value);
    }

    @Override
    public Observable<?> binding() {
        return source;
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new TextAreaState();
    }

    /// Builds a `text-area` from markup.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var rows = (int) node.numberProperty("rows", DEFAULT_ROWS);
        var maxRows = (int) node.numberProperty("max-rows", 0);
        var declared = (int) node.numberProperty("max-length", UNLIMITED);
        return new TextArea(
                node.stringProperty("value"),
                wiring.bound(node),
                wiring.valued(node, "change"),
                node.stringProperty("placeholder"),
                Math.max(1, rows),
                // A document that gives `rows` and not `max-rows` means "this
                // tall, and grow if there is more" -- so the default maximum
                // follows the minimum up rather than clamping it back down to
                // ten, which would be a smaller area than the one written.
                Math.max(maxRows <= 0 ? DEFAULT_MAX_ROWS : maxRows, Math.max(1, rows)),
                declared <= 0 ? UNLIMITED : declared,
                // `fill=#true` is an editor rather than a field: it takes the height
                // its container gives it and scrolls inside that.
                node.booleanProperty("fill"),
                node.booleanProperty("read-only"),
                Wiring.disabled(node),
                Attributes.of(node));
    }
}
