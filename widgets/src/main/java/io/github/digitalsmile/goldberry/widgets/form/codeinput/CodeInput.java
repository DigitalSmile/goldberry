package io.github.digitalsmile.goldberry.widgets.form.codeinput;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.attr.Bindable;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// The one-time-code field — `docs/core-widgets.md` §4's `code-input`.
///
/// ```kdl
/// field label="Verification code" {
///     code-input length=6 type="digits" change="onCode" complete="onVerify"
/// }
/// code-input length=8 type="alnum" mask=#true
/// ```
///
/// ```java
/// new CodeInput(6, code::set).onComplete(this::verify)
/// ```
///
/// ## What it is made of
///
/// ```
/// code-input          this node. Stateful, styles nothing, holds the code
/// └── code-input      [CodeField] — the styled node: focuses, takes the keys
///     └── code-group  [CodeGroup] — half the boxes, or all of them
///         └── code-box  [CodeBox] ×length
/// ```
///
/// Stateful and unstyled for `text-input`'s reason: two `code-input` nodes in the
/// cascade, one inside the other, would apply every rule twice.
///
/// ## Its own widget, because its editing model is
///
/// §4 says so in as many words, and [CodeEdit] is what that sentence turns out to
/// mean: no selection, no caret to place, no undo stack, and no holes. Four
/// sentences of specification fall out of two operations on a string.
///
/// **It does reuse the parts with rules in them**, which is what
/// `TextEditor`'s javadoc predicted: the `field` contract around it, and
/// `TextFilter`'s alphabets, which `CodeType` names rather than restates.
///
/// ## The field holds the code, and the model is told
///
/// `text-input`'s split, unchanged ([ADR-0063]): the field owns the edit and
/// reports each value through `change`, and a `bind=` value is the initial code
/// and an override rather than the thing being edited. A value that differs from
/// what the field holds is somebody else's and takes the field; one that matches
/// is the echo of the user's own keystroke and is ignored.
///
/// `complete` is the second event and the one §4 gives this widget by name — "it
/// fires when the last box fills, which is what lets a form submit without a
/// button". It fires **on the edit that filled the last box**, not on every
/// rebuild of a full field, so a handler that submits does not submit twice.
///
/// @param value      the code when nothing is bound
/// @param source     the `bind=` value, or null
/// @param onChange   told the code after every change the user makes
/// @param onComplete told once, when the last box fills
/// @param length     how many boxes — §4's `length=6`
/// @param type       what the boxes accept
/// @param mask       whether it draws bullets, authenticator-style
/// @param disabled   whether it refuses focus and matches `:disabled`
/// @param attributes the `id`, classes and key the document wrote
@Markup("code-input")
public record CodeInput(
        String value,
        @Nullable Observable<?> source,
        @Nullable Consumer<String> onChange,
        @Nullable Consumer<String> onComplete,
        int length,
        CodeType type,
        boolean mask,
        boolean disabled,
        Attributes attributes)
        implements Widget.Stateful, Attributed<CodeInput>, Bindable<CodeInput> {

    private static final Logger LOG = Logs.of(CodeInput.class);

    public CodeInput {
        value = value == null ? "" : value;
        type = type == null ? CodeType.DIGITS : type;
        attributes = attributes == null ? Attributes.NONE : attributes;
        if (length < 1) {
            throw new IllegalArgumentException("a code has at least one box, and " + length + " is not a length");
        }
    }

    /// An empty six-box field of digits.
    public CodeInput() {
        this("", null, null, null, CodeEdit.DEFAULT_LENGTH, CodeType.DIGITS, false, false, Attributes.NONE);
    }

    /// A field of `length` boxes, reporting every change.
    public CodeInput(int length, @Nullable Consumer<String> onChange) {
        this("", null, onChange, null, length, CodeType.DIGITS, false, false, Attributes.NONE);
    }

    /// A field following a property. The Java spelling of `bind=`.
    ///
    /// @param source read-only by construction, so the field cannot write to the
    ///               model even by accident ([ADR-0063])
    public static CodeInput of(Observable<?> source, @Nullable Consumer<String> onChange) {
        return new CodeInput(
                "",
                Objects.requireNonNull(source, "source"),
                onChange,
                null,
                CodeEdit.DEFAULT_LENGTH,
                CodeType.DIGITS,
                false,
                false,
                Attributes.NONE);
    }

    /// This field with `handler` told once, when the last box fills.
    public CodeInput onComplete(@Nullable Consumer<String> handler) {
        return new CodeInput(value, source, onChange, handler, length, type, mask, disabled, attributes);
    }

    /// This field with `boxes` boxes.
    public CodeInput length(int boxes) {
        return new CodeInput(value, source, onChange, onComplete, boxes, type, mask, disabled, attributes);
    }

    /// This field accepting `accepted`.
    public CodeInput type(CodeType accepted) {
        return new CodeInput(value, source, onChange, onComplete, length, accepted, mask, disabled, attributes);
    }

    /// This field drawing bullets instead of what it holds.
    public CodeInput mask(boolean masked) {
        return new CodeInput(value, source, onChange, onComplete, length, type, masked, disabled, attributes);
    }

    /// This field refusing focus and matching `:disabled`.
    public CodeInput disabled(boolean refused) {
        return new CodeInput(value, source, onChange, onComplete, length, type, mask, refused, attributes);
    }

    @Override
    public CodeInput withAttributes(Attributes replacement) {
        return new CodeInput(value, source, onChange, onComplete, length, type, mask, disabled, replacement);
    }

    @Override
    public CodeInput bound(Observable<?> binding) {
        return new CodeInput(value, binding, onChange, onComplete, length, type, mask, disabled, attributes);
    }

    @Override
    public @Nullable Observable<?> binding() {
        return source;
    }

    /// What this field starts from — the bound value, or [#value()].
    ///
    /// `text-input`'s rule unchanged: the binding wins, and a binding that
    /// answers null is an empty field rather than a fall back to the literal.
    public String resolved() {
        if (source == null) {
            return value;
        }
        var current = source.get();
        return current == null ? "" : String.valueOf(current);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new CodeInputState();
    }

    /// Builds a `code-input` from markup.
    ///
    /// Both actions take the code, which is the valued form `text-input` and
    /// `select` already use: a handler for a field is useless without what was
    /// typed (ADR-0073).
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new CodeInput(
                Objects.requireNonNullElse(node.stringProperty("value"), ""),
                wiring.bound(node),
                wiring.valued(node, "change"),
                wiring.valued(node, "complete"),
                boxes(node),
                type(node),
                node.booleanProperty("mask"),
                Wiring.disabled(node),
                Attributes.of(node));
    }

    private static int boxes(KdlNode node) {
        var declared = (int) node.numberProperty("length", CodeEdit.DEFAULT_LENGTH);
        // A document that wrote a length of zero wrote a typo, and a field with
        // no boxes is not a reading that leaves it working. §4's six is what a
        // `code-input` with no `length=` means anyway.
        return declared < 1 ? CodeEdit.DEFAULT_LENGTH : declared;
    }

    private static CodeType type(KdlNode node) {
        var name = node.stringProperty("type");
        var type = CodeType.named(name);
        if (type == null) {
            // Logged rather than thrown, exactly as `text-input`'s unknown
            // `filter=` is: it is a typo already visible in the markup, and a
            // field that refused every keystroke is a worse way to find out.
            LOG.warn("code-input type=\"{}\" names no type this toolkit has; the field will take digits", name);
            return CodeType.DIGITS;
        }
        return type;
    }
}
