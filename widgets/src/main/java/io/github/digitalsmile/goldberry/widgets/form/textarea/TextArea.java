package io.github.digitalsmile.goldberry.widgets.form.textarea;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Bindable;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.Markup;
import io.github.digitalsmile.goldberry.widgets.Wiring;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// A multi-line text field — `docs/core-widgets.md` §4's `text-area`.
///
/// ```kdl
/// field label="Bio" { text-area bind="user.bio" rows=4 }
/// text-area rows=2 max-rows=8 placeholder="Say something"
/// ```
///
/// ## It is `text-input` with three differences
///
/// The editing model is the same one, unchanged: [io.github.digitalsmile.goldberry.widgets.form.textinput.TextEdit]
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
/// @param readOnly    whether it takes a caret and a selection but no edits
/// @param disabled    whether it refuses focus and matches `:disabled`
/// @param attributes  the `id`, classes and key the document wrote
@Markup("text-area")
public record TextArea(
        String value, Observable<?> source, Consumer<String> onChange, String placeholder,
        int rows, int maxRows, int maxLength, boolean readOnly, boolean disabled,
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
            throw new IllegalArgumentException(
                    "a text-area is at least one line tall, and " + rows + " is not");
        }
        if (maxRows < rows) {
            throw new IllegalArgumentException(
                    "a text-area cannot grow to fewer lines than it starts at: rows=" + rows
                            + " max-rows=" + maxRows);
        }
        if (maxLength < UNLIMITED) {
            throw new IllegalArgumentException(
                    "a maximum length is a count of characters or " + UNLIMITED
                            + " for no limit, and " + maxLength + " is neither");
        }
    }

    /// An empty area of the default height.
    public TextArea() {
        this("", null, null, "", DEFAULT_ROWS, DEFAULT_MAX_ROWS, UNLIMITED, false, false,
                Attributes.NONE);
    }

    /// An area holding `value`, reporting every change.
    public TextArea(String value, Consumer<String> onChange) {
        this(value, null, onChange, "", DEFAULT_ROWS, DEFAULT_MAX_ROWS, UNLIMITED, false, false,
                Attributes.NONE);
    }

    /// An area following a property. The Java spelling of `bind=`.
    public static TextArea of(Observable<?> source, Consumer<String> onChange) {
        return new TextArea("", Objects.requireNonNull(source, "source"), onChange, "",
                DEFAULT_ROWS, DEFAULT_MAX_ROWS, UNLIMITED, false, false, Attributes.NONE);
    }

    /// This area with `text` shown when it is empty.
    public TextArea placeholder(String text) {
        return new TextArea(value, source, onChange, text, rows, maxRows, maxLength, readOnly,
                disabled, attributes);
    }

    /// This area `lines` tall, growing to at most `most`.
    public TextArea rows(int lines, int most) {
        return new TextArea(value, source, onChange, placeholder, lines, most, maxLength, readOnly,
                disabled, attributes);
    }

    /// This area `lines` tall, keeping its current maximum — raised to `lines` if
    /// that would otherwise be smaller.
    public TextArea rows(int lines) {
        return rows(lines, Math.max(lines, maxRows));
    }

    /// This area holding at most `characters`, or [#UNLIMITED].
    public TextArea maxLength(int characters) {
        return new TextArea(value, source, onChange, placeholder, rows, maxRows, characters,
                readOnly, disabled, attributes);
    }

    /// This area taking a caret and a selection but no edits.
    public TextArea readOnly(boolean value) {
        return new TextArea(this.value, source, onChange, placeholder, rows, maxRows, maxLength,
                value, disabled, attributes);
    }

    /// This area, disabled or not.
    public TextArea disabled(boolean value) {
        return new TextArea(this.value, source, onChange, placeholder, rows, maxRows, maxLength,
                readOnly, value, attributes);
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
        return new TextArea(this.value, value, onChange, placeholder, rows, maxRows, maxLength,
                readOnly, disabled, attributes);
    }

    @Override
    public TextArea withAttributes(Attributes value) {
        return new TextArea(this.value, source, onChange, placeholder, rows, maxRows, maxLength,
                readOnly, disabled, value);
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
                node.booleanProperty("read-only"),
                Wiring.disabled(node),
                Attributes.of(node));
    }
}
