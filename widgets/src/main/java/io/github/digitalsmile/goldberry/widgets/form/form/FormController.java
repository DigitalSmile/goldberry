package io.github.digitalsmile.goldberry.widgets.form.form;

import java.util.List;

/// A handle on a [Form] — what a Save button holds.
///
/// The arrangement [io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollController]
/// already uses, and here for the same reason: what submits a form is by
/// definition somewhere else. A button inside the form could reach it through
/// `findAncestorState` the way a `field` does, and a button *outside* it — in a
/// dialog's action bar, in a toolbar — could not, and that is where Save usually
/// is.
///
/// So the application makes one, hands it to the form, and calls it:
///
/// ```java
/// var form = new FormController();
/// // …
/// new Form(fields).controller(form)
/// new Button("Save", form::submit)
/// ```
///
/// ## Lifetime
///
/// A controller with no form attached does nothing and reports nothing wrong —
/// [#isAttached()] is false and [#submit()] returns false. That is the state
/// every controller is in for at least one frame, before the tree it names has
/// been built, and it is the state it returns to when the form is unmounted. A
/// stale controller cannot submit a form that is gone.
///
/// Confined to the UI thread, like the tree it reaches into.
public final class FormController {

    /// A controller with nothing attached yet.
    public FormController() {
    }

    /// The attached form's state, or null. Package-private and set only by
    /// [FormState], which attaches on mount and detaches on unmount.
    FormState attached;

    /// Whether a form is currently listening.
    public boolean isAttached() {
        return attached != null;
    }

    /// Validates every field and, if they all pass, runs the form's `submit`.
    ///
    /// **Every** field, including ones nobody has visited — otherwise a form
    /// submits with an untouched required field empty. This is the one moment a
    /// field complains without having been left first.
    ///
    /// @return whether it submitted; false for a controller with no form
    public boolean submit() {
        return attached != null && attached.submit();
    }

    /// Whether every field would pass, **without** making any of them complain.
    ///
    /// What a submit button asks to decide whether it is available. Side-effect
    /// free on purpose: a form that reddened its fields to answer would redden
    /// them before anyone had typed anything.
    ///
    /// True for a controller with no form, because a form that does not exist has
    /// nothing wrong with it — and a button that disabled itself waiting for one
    /// would be disabled on the first frame of every window.
    public boolean isValid() {
        return attached == null || attached.isValid();
    }

    /// What the fields are complaining about, in the order they registered —
    /// §4's error summary. Empty for a controller with no form.
    public List<String> errors() {
        return attached == null ? List.of() : attached.errors();
    }

    /// Clears every message and forgets that the fields have been visited.
    public void reset() {
        if (attached != null) {
            attached.reset();
        }
    }

    /// How many fields the form has collected. For a test, and for a diagnostic
    /// that wants to know whether a form found the fields somebody thinks it has.
    public int fieldCount() {
        return attached == null ? 0 : attached.fieldCount();
    }

    @Override
    public String toString() {
        return "FormController[" + (attached == null ? "detached" : fieldCount() + " fields") + "]";
    }
}
