package io.github.digitalsmile.goldberry.widgets.form.field;

import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.Markup;
import io.github.digitalsmile.goldberry.widgets.Wiring;
import io.github.digitalsmile.goldberry.widgets.form.Validator;
import java.util.List;
import java.util.Objects;

/// One labelled control and what to say about it —
/// `docs/core-widgets.md` §4's `field`.
///
/// ```kdl
/// field label="Name" required=#true {
///     text-input bind="user.name" placeholder="Jane Doe"
/// }
/// ```
///
/// ## What it is made of
///
/// ```
/// field            this node. Stateful, styles nothing, holds the validation result
/// └── field        [FieldBox] — the styled node: label column or stacked
///     ├── field-label     the label, and the required marker
///     ├── …               whatever was written inside — the control slot
///     └── field-message   the reason it is invalid, when it is
/// ```
///
/// Stateful and unstyled for `scroll`'s reason, which every stateful widget in
/// this catalog now shares: a stateful widget that also carried the CSS type
/// would put two `field` nodes in the cascade, one inside the other, and every
/// rule would apply twice.
///
/// ## It finds its own control
///
/// A field validates the value of the control inside it, and it learns that value
/// from [Widget#binding()] — the same `bind=` the control already reads. Nothing
/// is written twice and no new channel exists: the field walks its children for
/// the first one with a binding and watches it.
///
/// A field with no bound control validates **nothing** and is a layout contract
/// only, which is the honest answer rather than an error: `field label="Name"`
/// around a `text` is a perfectly good way to lay out a read-only row, and a
/// toolkit that refused it would be refusing a use it has no argument against.
///
/// That includes `required=#true`, and it is worth saying plainly: **a required
/// field around an unbound control is not required**, because nothing can see
/// what it holds. The alternative is failing forever — a form gated on a control
/// somebody can type into and never satisfy — which is worse than a flag that
/// does nothing. It is the same shape as a `menu` that is only ever opened
/// registering no accelerators, because nothing is holding it (ADR-0163).
///
/// ## When it validates
///
/// §4 says "on blur and on submit". Blur is
/// [io.github.digitalsmile.goldberry.input.Handles#onFocusWithin] — the field is
/// told when the keyboard leaves its subtree, which is once, however many controls
/// are in it and however they were moved between.
///
/// **Not on every keystroke**, and this is the whole reason blur is the moment: a
/// field that validated as you typed would call an email address invalid after the
/// first letter and stay red until the last, which trains people to ignore it. Once
/// a field *has* complained, though, it re-checks on every change — so the message
/// goes away the instant the value is fixed rather than at the next blur, which is
/// the asymmetry every good form has and no specification states.
///
/// @param label      what to write beside the control, or `""` for none
/// @param children   the control slot — whatever the document wrote inside
/// @param required   whether it carries the required marker and refuses emptiness
/// @param validator  what the control's value has to satisfy, or null
/// @param attributes the `id`, classes and key the document wrote
@Markup("field")
public record Field(
        String label, List<Widget> children, boolean required, Validator<String> validator,
        Attributes attributes)
        implements Widget.Stateful, Attributed<Field> {

    /// What a `required` field says when it is empty, and the one message this
    /// toolkit writes rather than the application.
    ///
    /// It is here because `required=#true` is a *flag* — a document that writes it
    /// has supplied no words — and a required field with no message would go red
    /// and say nothing, which [Validator.Result#invalid] refuses for everything
    /// else. An application that wants its own wording writes a [Validator]
    /// instead, and gets it.
    public static final String REQUIRED_MESSAGE = "This field is required";

    public Field {
        label = label == null ? "" : label;
        children = List.copyOf(Objects.requireNonNull(children, "children"));
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A labelled field around one control.
    public Field(String label, Widget... children) {
        this(label, List.of(children), false, null, Attributes.NONE);
    }

    /// This field, marked required — the marker beside the label, and a value
    /// that may not be blank.
    public Field required(boolean value) {
        return new Field(label, children, value, validator, attributes);
    }

    /// This field, validating with `rule`.
    ///
    /// Runs **after** the required check, so a required field with a format rule
    /// reports "this is required" for an empty value rather than "that is not an
    /// email address", which is the more useful of the two things that are both
    /// true.
    public Field validate(Validator<String> rule) {
        return new Field(label, children, required, rule, attributes);
    }

    /// The rule this field actually applies: the required check, then the
    /// application's.
    ///
    /// Built here rather than in the state so that a caller can ask a field what
    /// it will do without mounting it.
    public Validator<String> rule() {
        var start = required
                ? Validator.required(REQUIRED_MESSAGE)
                : Validator.<String>none();
        return validator == null ? start : start.and(validator);
    }

    @Override
    public Field withAttributes(Attributes value) {
        return new Field(label, children, required, validator, value);
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new FieldState();
    }

    /// Builds a `field` from markup.
    ///
    /// `validator="app.port-rule"` **names** a [Validator] the application holds,
    /// exactly as `press=` names an action: markup is data and a validator is a
    /// function, so a document can say which rule and not what the rule is
    /// ([ADR-0170]). `required=#true` stays a flag because it is the one rule
    /// that *is* data.
    @SuppressWarnings("unchecked")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Field(
                node.stringProperty("label"),
                children,
                node.booleanProperty("required"),
                wiring.handle(node, "validator", Validator.class),
                Attributes.of(node));
    }
}
