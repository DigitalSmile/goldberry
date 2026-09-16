package io.github.digitalsmile.goldberry.widgets.form.textarea;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.text.edit.TextEdit;
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
/// text-area class="mono" gutter=#true fill=#true bind="note.body" change="note.type"
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
/// ## An editor's two extras
///
/// A field in a form and a pane holding a document are the same control, and the
/// two things the second needs that the first does not are both here.
///
/// **[#gutter(boolean)]** puts the hard lines' numbers down the left edge. It is
/// on the widget rather than beside it because the numbers have to line up with
/// **hard** lines drawn at **soft**-wrapped positions, and only the thing that
/// laid the text out knows where those fell — a `Column` of numbers next to the
/// pane is right until the first line that wraps and wrong for every line after
/// it, which is worse than having none (`docs/gaps.md` G37, [ADR-0331]).
///
/// **[#onEdit] and [#edit(TextEdit)]** are the caret and the selection, in and
/// out. `change=` reports the new whole value, which is exactly right for a form
/// and says nothing about *where* anything is; `Ctrl+B` around a selection, `-`
/// and `Enter` continuing a list and `Tab` indenting one all need to know. The
/// second method is half the request rather than a convenience: wrapping a
/// selection in `**` is an edit **and** a caret move, and an application that
/// could compute one but only push back a `String` would leave the caret wherever
/// the widget decided (`docs/gaps.md` G38, [ADR-0332]).
///
/// `change=` is untouched and fires as it always did. The two are beside each
/// other, for the two kinds of caller.
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
/// @param onEdit      told the text, the anchor and the caret after every change
///                    **including one that changed no text** — see [#onEdit]
/// @param edit        an edit the application computed for this control to adopt,
///                    or null — see [#edit(TextEdit)]
/// @param placeholder what to draw when it is empty
/// @param rows        how many lines tall it is when empty — its minimum
/// @param maxRows     how tall it may grow before it scrolls instead
/// @param maxLength   the most characters it will hold, or -1
/// @param fill        whether it takes the height its container gives it instead of
///                    growing to fit its text — see [#fill(boolean)]
/// @param gutter      whether the hard lines are numbered down the left edge — see
///                    [#gutter(boolean)]
/// @param readOnly    whether it takes a caret and a selection but no edits
/// @param disabled    whether it refuses focus and matches `:disabled`
/// @param attributes  the `id`, classes and key the document wrote
@Markup("text-area")
public record TextArea(
        String value,
        Observable<?> source,
        Consumer<String> onChange,
        Consumer<TextEdit> onEdit,
        TextEdit edit,
        String placeholder,
        int rows,
        int maxRows,
        int maxLength,
        boolean fill,
        boolean gutter,
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

    /// The eleven components an area had before it was also an editor.
    ///
    /// Kept for [Attributes]' reason: this constructor is written out in the
    /// catalog's own tests and in every application that builds one positionally,
    /// and two more arguments on all of them would be a hundred edits to say
    /// `null` ([ADR-0332]).
    public TextArea(
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
            Attributes attributes) {
        this(
                value,
                source,
                onChange,
                null,
                null,
                placeholder,
                rows,
                maxRows,
                maxLength,
                fill,
                false,
                readOnly,
                disabled,
                attributes);
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
                value,
                source,
                onChange,
                onEdit,
                edit,
                text,
                rows,
                maxRows,
                maxLength,
                fill,
                gutter,
                readOnly,
                disabled,
                attributes);
    }

    /// This area `lines` tall, growing to at most `most`.
    public TextArea rows(int lines, int most) {
        return new TextArea(
                value,
                source,
                onChange,
                onEdit,
                edit,
                placeholder,
                lines,
                most,
                maxLength,
                fill,
                gutter,
                readOnly,
                disabled,
                attributes);
    }

    /// This area `lines` tall, keeping its current maximum — raised to `lines` if
    /// that would otherwise be smaller.
    public TextArea rows(int lines) {
        return rows(lines, Math.max(lines, maxRows));
    }

    /// This area holding at most `characters`, or [#UNLIMITED].
    public TextArea maxLength(int characters) {
        return new TextArea(
                value,
                source,
                onChange,
                onEdit,
                edit,
                placeholder,
                rows,
                maxRows,
                characters,
                fill,
                gutter,
                readOnly,
                disabled,
                attributes);
    }

    /// This area taking a caret and a selection but no edits.
    public TextArea readOnly(boolean value) {
        return new TextArea(
                this.value,
                source,
                onChange,
                onEdit,
                edit,
                placeholder,
                rows,
                maxRows,
                maxLength,
                fill,
                gutter,
                value,
                disabled,
                attributes);
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
                onEdit,
                edit,
                placeholder,
                rows,
                maxRows,
                maxLength,
                value,
                gutter,
                readOnly,
                disabled,
                attributes);
    }

    /// This area with its **hard** lines numbered down the left edge — §10 E1's
    /// line-number gutter.
    ///
    /// One number per line somebody typed, drawn at the y the line was actually
    /// laid out at. A line that soft-wraps into three takes one number and three
    /// lines' worth of height, which is what every editor does and is the whole
    /// reason this cannot be a `Column` of numbers beside the control: only the
    /// thing that laid the text out knows where a hard line ended up
    /// (`docs/gaps.md` G37, [ADR-0331]).
    ///
    /// A boolean and not a renderer. What a line number *looks* like is the
    /// stylesheet's — `text-area-line-number`, and the column behind it is
    /// `text-area-gutter` — and an application that wanted to draw something else
    /// in that column is asking for a different widget rather than a parameter.
    ///
    /// The text moves right by the gutter's width and everything that measures
    /// against the content moves with it: the wrap, the caret, the selection and
    /// where a click lands.
    public TextArea gutter(boolean on) {
        return new TextArea(
                value,
                source,
                onChange,
                onEdit,
                edit,
                placeholder,
                rows,
                maxRows,
                maxLength,
                fill,
                on,
                readOnly,
                disabled,
                attributes);
    }

    /// This area reporting the caret and the selection after **every** change.
    ///
    /// The richer half of `change=`: a [TextEdit] is `(text, anchor, caret)`, which
    /// is the value this control already holds and drives itself from. It is
    /// raised after a keystroke, a click, a drag, an arrow key, a select-all, an
    /// undo — anything that moves the caret, whether or not it changed a
    /// character. Three of the four things a Markdown shortcut needs to know about
    /// change no text at all, which is why the caret cannot simply be inferred
    /// from two versions of a string (`docs/gaps.md` G38, [ADR-0332]).
    ///
    /// It is **not** raised for an edit the application itself pushed in through
    /// [#edit(TextEdit)]: that would be an echo of something the caller already
    /// knows, and an application that mirrored it into its own state would loop.
    ///
    /// `onChange` still fires, and only when the text differs. The two are
    /// independent: a form may listen to one, an editor to the other, a screen
    /// that is both to each.
    public TextArea onEdit(Consumer<TextEdit> listener) {
        return new TextArea(
                value,
                source,
                onChange,
                listener,
                edit,
                placeholder,
                rows,
                maxRows,
                maxLength,
                fill,
                gutter,
                readOnly,
                disabled,
                attributes);
    }

    /// This area holding an edit the application computed — text, anchor and
    /// caret at once.
    ///
    /// The other half of [#onEdit], and the half that makes a shortcut possible:
    /// `**` around a selection is a new string **and** a new caret, and a control
    /// that could only be handed the string would put the caret back wherever it
    /// liked.
    ///
    /// **Offered, not imposed.** It works exactly as `value=` does: the control
    /// adopts it when it *changes*, and ignores it on every rebuild in between —
    /// otherwise a constant edit would reset the caret on every keystroke. So the
    /// application pushes one when it has computed one and leaves it in place
    /// afterwards; typing moves on from it.
    ///
    /// It is applied **after** `bind=`, so an area that is both bound and pushed
    /// to in one frame takes the caret from here rather than from the clamp a new
    /// value would leave behind — which is the case a Markdown shortcut always is.
    public TextArea edit(TextEdit next) {
        return new TextArea(
                value,
                source,
                onChange,
                onEdit,
                next,
                placeholder,
                rows,
                maxRows,
                maxLength,
                fill,
                gutter,
                readOnly,
                disabled,
                attributes);
    }

    /// This area, disabled or not.
    public TextArea disabled(boolean value) {
        return new TextArea(
                this.value,
                source,
                onChange,
                onEdit,
                edit,
                placeholder,
                rows,
                maxRows,
                maxLength,
                fill,
                gutter,
                readOnly,
                value,
                attributes);
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

    /// Tells the application where the caret and the selection now are.
    void reportEdit(TextEdit state) {
        if (onEdit != null) {
            onEdit.accept(state);
        }
    }

    @Override
    public TextArea bound(Observable<?> value) {
        return new TextArea(
                this.value,
                value,
                onChange,
                onEdit,
                edit,
                placeholder,
                rows,
                maxRows,
                maxLength,
                fill,
                gutter,
                readOnly,
                disabled,
                attributes);
    }

    @Override
    public TextArea withAttributes(Attributes value) {
        return new TextArea(
                this.value,
                source,
                onChange,
                onEdit,
                edit,
                placeholder,
                rows,
                maxRows,
                maxLength,
                fill,
                gutter,
                readOnly,
                disabled,
                value);
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
    ///
    /// `onEdit` and [#edit(TextEdit)] have no attribute: `change=` names a method
    /// that takes a value, and a [TextEdit] is not a value a KDL document can
    /// write or a registry can resolve. An editor is Java (ADR-0332).
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var rows = (int) node.numberProperty("rows", DEFAULT_ROWS);
        var maxRows = (int) node.numberProperty("max-rows", 0);
        var declared = (int) node.numberProperty("max-length", UNLIMITED);
        return new TextArea(
                node.stringProperty("value"),
                wiring.bound(node),
                wiring.valued(node, "change"),
                null,
                null,
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
                node.booleanProperty("gutter"),
                node.booleanProperty("read-only"),
                Wiring.disabled(node),
                Attributes.of(node));
    }
}
