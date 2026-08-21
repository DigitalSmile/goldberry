package io.github.digitalsmile.goldberry.widgets.form.form;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widgets.form.field.Validated;

/// How a `field` reaches the `form` above it, without either package opening
/// itself to the other.
///
/// [FormState] is package-private, as a state should be — it is not something an
/// application constructs or holds. A `field` is in another package and still has
/// to register with it, so this is the two methods a field needs, and nothing
/// else: the state implements it and the interface says what a field may do to a
/// form, which is join it and leave.
///
/// The alternative was making `FormState` public, which would put `submit()`,
/// `reset()` and the field list on the toolkit's API for the sake of one internal
/// call.
public interface FormAccess {

    /// Takes this field into the form.
    void register(Validated field);

    /// Takes it out again. Idempotent.
    void unregister(Validated field);

    /// The nearest enclosing form, or null when the field is not in one.
    ///
    /// A field outside a form is not an error and is not unusual: `field` is a
    /// layout contract before it is anything, and one used on its own validates
    /// on blur exactly as it would inside a form — it simply has nothing to
    /// submit with.
    static FormAccess of(BuildContext context) {
        return context.findAncestorState(FormState.class).orElse(null);
    }
}
