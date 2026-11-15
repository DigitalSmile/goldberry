package dev.goldberry.widgets.form.richinput;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.bind.Observable;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.text.mark.MarkedText;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// A field that takes formatting: bold, italic, code, links and mentions in a
/// message being written.
///
/// ```kdl
/// rich-input placeholder="Message" rows=1 max-rows=8
/// ```
///
/// ```java
/// new RichInput(draft, next -> draft = next).placeholder("Message").maxRows(8)
/// ```
///
/// The value is a [MarkedText]: the words, and the marks over them as ranges in
/// `char` offsets. A field grows from `rows` lines to `max-rows` as text is
/// typed, then scrolls. It takes the editing keys every field takes, and the
/// formatting keys: `Ctrl+B` bold, `Ctrl+I` italic, `Ctrl+U` underline,
/// `Ctrl+Shift+X` strike-through, `Ctrl+Shift+M` code, `Ctrl+Shift+P` spoiler and
/// `Ctrl+Shift+N` to clear. Undo brings formatting back with the words, and a
/// copy keeps its marks when it is pasted into another rich field.
///
/// The marks are styled by the stylesheet: each set of kinds in the text is a
/// `mark` node under the field with a class per kind, so
/// `rich-input mark.code { font-family: monospace }` and
/// `rich-input mark.bold.italic { … }` style them as CSS styles any element. A
/// mark's font, `color`, `text-decoration` and `background` are read; the
/// field's lines stay its own font's height.
///
/// A selection that settles (the pointer let go, or `Shift` released) opens a
/// formatting bar over it: [FormatButton#defaults()] unless the application
/// gives its own buttons, or none. `Ctrl+K` and the bar's Link open a small
/// editor for the address. The text menu has a Format submenu with the same
/// commands.
///
/// With [#mentions] set, a trigger such as `@` typed at a word start opens a
/// **query**: the field reports the word after it as it is typed
/// ([MentionPrompt]), the application shows its own suggestions, and the chosen
/// one goes in as an atom the caret steps over and `Backspace` removes whole.
/// While a query is open, `Up`, `Down`, `Enter`, `Tab` and `Escape` go to the
/// application first.
///
/// With [#onSubmit] set, `Enter` sends and `Shift+Enter` breaks the line, as a
/// message composer does; without it `Enter` breaks the line.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#keyboard).
///
/// @param value       the text and its marks, or null for none
/// @param source      a bound value to follow, holding a [MarkedText] or a
///                    `String`, or null
/// @param onChange    what hears every change, or null
/// @param onSubmit    what `Enter` sends the text to, or null for `Enter` to
///                    break the line
/// @param placeholder what an empty field shows, or null for nothing
/// @param rows        how many lines tall it starts, at least one
/// @param maxRows     how many it grows to before it scrolls
/// @param formatting  the formatting bar's buttons: null for
///                    [FormatButton#defaults()], empty for no bar
/// @param mentions    what opens a mention query and who hears it, or null
///                    for none
/// @param readOnly    whether edits are refused; the text can still be selected
///                    and copied
/// @param disabled    whether it takes no input at all
/// @param attributes  its id, classes and key, or null for none
@Markup("rich-input")
public record RichInput(
        MarkedText value,
        @Nullable Observable<?> source,
        @Nullable Consumer<MarkedText> onChange,
        @Nullable Consumer<MarkedText> onSubmit,
        String placeholder,
        int rows,
        int maxRows,
        List<FormatButton> formatting,
        @Nullable Mentions mentions,
        boolean readOnly,
        boolean disabled,
        Attributes attributes)
        implements Widget.Stateful, Attributed<RichInput> {

    /// How many lines tall a field starts: one, as a message composer does.
    public static final int DEFAULT_ROWS = 1;

    /// How many lines it grows to before it scrolls.
    public static final int DEFAULT_MAX_ROWS = 8;

    /// Written out so that the parameters taking null for a default can say so.
    public RichInput(
            @Nullable MarkedText value,
            @Nullable Observable<?> source,
            @Nullable Consumer<MarkedText> onChange,
            @Nullable Consumer<MarkedText> onSubmit,
            @Nullable String placeholder,
            int rows,
            int maxRows,
            @Nullable List<FormatButton> formatting,
            @Nullable Mentions mentions,
            boolean readOnly,
            boolean disabled,
            @Nullable Attributes attributes) {
        if (rows < 1) {
            throw new IllegalArgumentException("a rich-input is at least one line tall, and " + rows + " is not");
        }
        if (maxRows < rows) {
            throw new IllegalArgumentException(
                    "a rich-input cannot grow to fewer lines than it starts at: rows=" + rows + " max-rows=" + maxRows);
        }
        this.value = value == null ? MarkedText.EMPTY : value;
        this.source = source;
        this.onChange = onChange;
        this.onSubmit = onSubmit;
        this.placeholder = placeholder == null ? "" : placeholder;
        this.rows = rows;
        this.maxRows = maxRows;
        this.formatting = formatting == null ? FormatButton.defaults() : List.copyOf(formatting);
        this.mentions = mentions;
        this.readOnly = readOnly;
        this.disabled = disabled;
        this.attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// An empty field.
    public RichInput() {
        this(null, null, null, null, "", DEFAULT_ROWS, DEFAULT_MAX_ROWS, null, null, false, false, null);
    }

    /// A field holding `value`, reporting every change to `onChange`.
    public RichInput(MarkedText value, Consumer<MarkedText> onChange) {
        this(value, null, onChange, null, "", DEFAULT_ROWS, DEFAULT_MAX_ROWS, null, null, false, false, null);
    }

    /// A field following `source`, which holds a [MarkedText] or a `String`.
    /// The Java spelling of `bind=`.
    public static RichInput of(Observable<?> source, Consumer<MarkedText> onChange) {
        Objects.requireNonNull(source, "source");
        return new RichInput(
                null, source, onChange, null, "", DEFAULT_ROWS, DEFAULT_MAX_ROWS, null, null, false, false, null);
    }

    /// This field, showing `value` while it is empty.
    public RichInput placeholder(String value) {
        return new RichInput(
                this.value,
                source,
                onChange,
                onSubmit,
                value,
                rows,
                maxRows,
                formatting,
                mentions,
                readOnly,
                disabled,
                attributes);
    }

    /// This field, starting `value` lines tall.
    public RichInput rows(int value) {
        return new RichInput(
                this.value,
                source,
                onChange,
                onSubmit,
                placeholder,
                value,
                Math.max(value, maxRows),
                formatting,
                mentions,
                readOnly,
                disabled,
                attributes);
    }

    /// This field, growing to `value` lines before it scrolls.
    public RichInput maxRows(int value) {
        return new RichInput(
                this.value,
                source,
                onChange,
                onSubmit,
                placeholder,
                rows,
                value,
                formatting,
                mentions,
                readOnly,
                disabled,
                attributes);
    }

    /// This field, sending its text to `value` on `Enter`; `Shift+Enter` then
    /// breaks the line.
    public RichInput onSubmit(Consumer<MarkedText> value) {
        Objects.requireNonNull(value, "value");
        return new RichInput(
                this.value,
                source,
                onChange,
                value,
                placeholder,
                rows,
                maxRows,
                formatting,
                mentions,
                readOnly,
                disabled,
                attributes);
    }

    /// This field, with `value` as its formatting bar; empty for no bar.
    public RichInput formatting(List<FormatButton> value) {
        Objects.requireNonNull(value, "value");
        return new RichInput(
                this.value,
                source,
                onChange,
                onSubmit,
                placeholder,
                rows,
                maxRows,
                value,
                mentions,
                readOnly,
                disabled,
                attributes);
    }

    /// This field, reporting a word typed after one of `value`'s triggers,
    /// such as `@an`, so the application can offer whom to mention; null for
    /// none. See [Mentions].
    public RichInput mentions(@Nullable Mentions value) {
        return new RichInput(
                this.value,
                source,
                onChange,
                onSubmit,
                placeholder,
                rows,
                maxRows,
                formatting,
                value,
                readOnly,
                disabled,
                attributes);
    }

    /// This field, refusing edits or not.
    public RichInput readOnly(boolean value) {
        return new RichInput(
                this.value,
                source,
                onChange,
                onSubmit,
                placeholder,
                rows,
                maxRows,
                formatting,
                mentions,
                value,
                disabled,
                attributes);
    }

    /// This field, taking no input or not.
    public RichInput disabled(boolean value) {
        return new RichInput(
                this.value,
                source,
                onChange,
                onSubmit,
                placeholder,
                rows,
                maxRows,
                formatting,
                mentions,
                readOnly,
                value,
                attributes);
    }

    @Override
    public RichInput withAttributes(Attributes value) {
        return new RichInput(
                this.value,
                source,
                onChange,
                onSubmit,
                placeholder,
                rows,
                maxRows,
                formatting,
                mentions,
                readOnly,
                disabled,
                value);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public @Nullable Observable<?> binding() {
        return source;
    }

    @Override
    public State<?> createState() {
        return new RichInputState();
    }

    /// Builds a `rich-input` from markup: `placeholder`, `rows`, `max-rows`,
    /// `formatting=#false` for no bar, `read-only`, `disabled` and `bind=`. A
    /// change or submit handler is code, so a document cannot give one.
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var rows = Math.max(1, (int) node.numberProperty("rows", DEFAULT_ROWS));
        var maxRows = (int) node.numberProperty("max-rows", 0);
        var barless = node.property("formatting").isPresent() && !node.booleanProperty("formatting");
        return new RichInput(
                null,
                wiring.bound(node),
                null,
                null,
                node.stringProperty("placeholder"),
                rows,
                Math.max(maxRows <= 0 ? DEFAULT_MAX_ROWS : maxRows, rows),
                barless ? List.of() : null,
                null,
                node.booleanProperty("read-only"),
                Wiring.disabled(node),
                Attributes.of(node));
    }
}
