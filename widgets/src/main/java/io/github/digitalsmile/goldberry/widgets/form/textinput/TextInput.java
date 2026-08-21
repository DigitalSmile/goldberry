package io.github.digitalsmile.goldberry.widgets.form.textinput;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.natives.log.Logs;
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

/// A single-line text field — `docs/core-widgets.md` §4's `text-input`.
///
/// ```kdl
/// field label="Name" required=#true {
///     text-input bind="user.name" placeholder="Jane Doe" max-length=64
/// }
/// text-input password=#true placeholder="Password"
/// text-input filter="digits" placeholder="Port"
/// ```
///
/// ```java
/// TextInput.of(model.name(), model::rename).placeholder("Jane Doe")
/// ```
///
/// ## What it is made of
///
/// ```
/// text-input          this node. Stateful, styles nothing, holds the text
/// └── text-input      [TextField] — the styled node: clips, focuses, takes the keys
///     ├── text-selection  the highlight, behind the text
///     ├── text-value      the text, or the placeholder
///     └── text-caret      the insertion point
/// ```
///
/// Stateful and unstyled for `scroll`'s reason: a stateful widget that also
/// carried the CSS type would put two `text-input` nodes in the cascade, one
/// inside the other, and every rule would apply twice.
///
/// ## The field holds the text, and the model is told
///
/// The editing state lives in the element, not in the application's model, which
/// is the opposite of what `checkbox` and `select` do — and it has to be. A
/// checkbox's whole state is one bit that the model can hold; a field's includes
/// a caret, a selection and an undo stack, and a model that held those would have
/// to be told about every keystroke to keep them right. So the field owns the
/// edit and **reports** each new value through `change`, exactly as §9's "data
/// flows down, events flow up" says ([ADR-0063]).
///
/// A `bind=` value therefore behaves as the *initial* text and as an override: a
/// value arriving that differs from what the field holds is somebody else's and
/// takes the field, and one that matches is the echo of the user's own keystroke
/// and is ignored. Without that test every `change` handler that wrote back to
/// its model would reset the caret to the end on every letter.
///
/// ## What §4 asks for, and what is here
///
/// Caret, selection by mouse and keyboard with word operations, clipboard,
/// undo/redo, placeholder, maximum length, `password` masking with no
/// clipboard-out, and input filters. IME preedit and right-to-left editing are
/// M5 and are deferred by `docs/ARCHITECTURE.md` §17 — committed text from an IME
/// works today, because the platform hands over finished characters and this
/// field takes them like any others; what is missing is the *underlined
/// in-progress* text, which needs a second string the field draws and does not
/// hold.
///
/// @param value       the text when nothing is bound
/// @param source      the `bind=` value, or null
/// @param onChange    told the new text after every change the user makes
/// @param placeholder what to draw when the field is empty
/// @param maxLength   the most characters it will hold, or -1 for no limit
/// @param password    whether it masks what it holds and refuses to copy it out
/// @param readOnly    whether it takes focus and a caret but no edits
/// @param filter      what it will accept — see [TextFilter]
/// @param disabled    whether it refuses focus and matches `:disabled`
/// @param attributes  the `id`, classes and key the document wrote
@Markup("text-input")
public record TextInput(
        String value, Observable<?> source, Consumer<String> onChange, String placeholder,
        int maxLength, boolean password, boolean readOnly, TextFilter filter, boolean disabled,
        Attributes attributes)
        implements Widget.Stateful, Attributed<TextInput>, Bindable<TextInput> {

    private static final org.slf4j.Logger LOG = Logs.of(TextInput.class);

    /// What [#maxLength] means when there is no limit.
    public static final int UNLIMITED = -1;

    public TextInput {
        value = value == null ? "" : value;
        placeholder = placeholder == null ? "" : placeholder;
        filter = filter == null ? TextFilter.NONE : filter;
        attributes = attributes == null ? Attributes.NONE : attributes;
        if (maxLength < UNLIMITED) {
            throw new IllegalArgumentException(
                    "a maximum length is a count of characters or " + UNLIMITED
                            + " for no limit, and " + maxLength + " is neither");
        }
    }

    /// An empty field.
    public TextInput() {
        this("", null, null, "", UNLIMITED, false, false, TextFilter.NONE, false, Attributes.NONE);
    }

    /// A field holding `value`, reporting every change.
    public TextInput(String value, Consumer<String> onChange) {
        this(value, null, onChange, "", UNLIMITED, false, false, TextFilter.NONE, false,
                Attributes.NONE);
    }

    /// A field following a property. The Java spelling of `bind=`.
    ///
    /// @param source read-only by construction, so the field cannot write to the
    ///               model even by accident ([ADR-0063])
    public static TextInput of(Observable<?> source, Consumer<String> onChange) {
        return new TextInput("", Objects.requireNonNull(source, "source"), onChange, "",
                UNLIMITED, false, false, TextFilter.NONE, false, Attributes.NONE);
    }

    /// This field with `text` shown when it is empty.
    public TextInput placeholder(String text) {
        return new TextInput(value, source, onChange, text, maxLength, password, readOnly,
                filter, disabled, attributes);
    }

    /// This field holding at most `characters`, or [#UNLIMITED].
    public TextInput maxLength(int characters) {
        return new TextInput(value, source, onChange, placeholder, characters, password, readOnly,
                filter, disabled, attributes);
    }

    /// This field masked, and refusing to copy its contents out.
    public TextInput password(boolean masked) {
        return new TextInput(value, source, onChange, placeholder, maxLength, masked, readOnly,
                filter, disabled, attributes);
    }

    /// This field taking a caret and a selection but no edits.
    ///
    /// **Not the same as disabled.** A read-only field is still focusable, still
    /// selectable and still copyable, which is what a value somebody needs to read
    /// off the screen has to be; a disabled one is out of the tab order and out of
    /// the conversation.
    public TextInput readOnly(boolean value) {
        return new TextInput(this.value, source, onChange, placeholder, maxLength, password, value,
                filter, disabled, attributes);
    }

    /// This field accepting only what `filter` allows.
    public TextInput filter(TextFilter value) {
        return new TextInput(this.value, source, onChange, placeholder, maxLength, password,
                readOnly, value, disabled, attributes);
    }

    /// This field, disabled or not.
    public TextInput disabled(boolean value) {
        return new TextInput(this.value, source, onChange, placeholder, maxLength, password,
                readOnly, filter, value, attributes);
    }

    /// What this field starts from — the bound value, or [#value()].
    ///
    /// The binding wins, and a binding that answers null is an empty field rather
    /// than a fall back to the literal: a bound field showing a stale literal
    /// because the model has not loaded yet is worse than one showing nothing.
    public String resolved() {
        if (source == null) {
            return value;
        }
        var current = source.get();
        return current == null ? "" : String.valueOf(current);
    }

    /// Tells the application what the field now holds.
    void report(String text) {
        if (onChange != null) {
            onChange.accept(text);
        }
    }

    @Override
    public TextInput bound(Observable<?> value) {
        return new TextInput(this.value, value, onChange, placeholder, maxLength, password,
                readOnly, filter, disabled, attributes);
    }

    @Override
    public TextInput withAttributes(Attributes value) {
        return new TextInput(this.value, source, onChange, placeholder, maxLength, password,
                readOnly, filter, disabled, value);
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
        return new TextInputState();
    }

    /// Builds a `text-input` from markup.
    ///
    /// The `change` action takes the new text, which is the valued form
    /// `segmented` and `select` already use: a field's handler is useless without
    /// what was typed (ADR-0073).
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new TextInput(
                node.stringProperty("value"),
                wiring.bound(node),
                wiring.valued(node, "change"),
                node.stringProperty("placeholder"),
                length(node),
                node.booleanProperty("password"),
                node.booleanProperty("read-only"),
                filter(node),
                Wiring.disabled(node),
                Attributes.of(node));
    }

    private static int length(KdlNode node) {
        var declared = (int) node.numberProperty("max-length", UNLIMITED);
        // Zero is not a length a document means: a field that can hold nothing is
        // a field nobody wrote on purpose, and reading it as "no limit" is the
        // reading that leaves the document working.
        return declared <= 0 ? UNLIMITED : declared;
    }

    private static TextFilter filter(KdlNode node) {
        var name = node.stringProperty("filter");
        if (name == null || name.isEmpty()) {
            return TextFilter.NONE;
        }
        var filter = TextFilter.named(name);
        if (filter == null) {
            // Logged rather than thrown, exactly as an accelerator that does not
            // parse is: it is a typo already visible in the markup, and a field
            // that refused every keystroke is a worse way to find out about it.
            LOG.warn("text-input filter=\"{}\" names no filter this toolkit has;"
                    + " the field will accept anything", name);
            return TextFilter.NONE;
        }
        return filter;
    }
}
