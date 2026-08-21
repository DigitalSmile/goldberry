package io.github.digitalsmile.goldberry.widgets.form.field;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.bind.Subscription;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;

/// What a [Field] holds: whether it has complained, and what about.
///
/// ## Two moments, and the second one is the interesting one
///
/// §4 says "run on blur and on submit". Blur is the first: the field is told by
/// [io.github.digitalsmile.goldberry.input.Handles#onFocusWithin] when the
/// keyboard leaves its subtree, which happens once however many controls are in
/// it.
///
/// The second is **not in the specification and every good form has it**: once a
/// field has complained, it re-checks on every change to its value, so the
/// message goes away the moment the value is fixed rather than at the next blur.
/// The asymmetry is the point — a field that validated as you typed would call an
/// email address invalid after the first letter and stay red until the last,
/// which teaches people to ignore it; a field that waited for a second blur to
/// forgive you is one you have to leave and come back to.
///
/// So: silent until blur, then live.
///
/// ## It watches the control's binding
///
/// A field learns its value from [Widget#binding()] — the `bind=` the control
/// inside it already reads. Nothing is written twice and there is no channel from
/// a control to its field; the field walks its children for the first bound one
/// and subscribes.
final class FieldState extends State<Field> implements Validated {

    /// What is wrong, or `""`. The whole of this state: a field is invalid
    /// exactly when it has something to say.
    private String message = "";

    /// Whether the user has finished with this field at least once.
    ///
    /// Until they have, a field says nothing however wrong its value is — which
    /// is the difference between a form that helps and a form that shouts at an
    /// empty screen.
    private boolean complained;

    /// The control's value, and the subscription that keeps this in step with it.
    private Observable<?> source;
    private Subscription watching;

    /// The form this field is in, or null — found once, on the first build.
    ///
    /// `BuildContext.findAncestorState` has been on the interface since the
    /// element tree was built and this is its first consumer: a field knows one
    /// form, a form knows however many fields the document wrote, and looking
    /// *up* is the direction that needs no subtree walk and no knowledge of what
    /// to skip.
    private io.github.digitalsmile.goldberry.widgets.form.form.FormAccess form;
    private boolean looked;

    @Override
    public Widget build(BuildContext context) {
        joinForm(context);
        follow();
        var field = widget();
        return new FieldBox(
                field.label(), field.children(), field.required(), message,
                field.attributes(), this::blurred);
    }

    @Override
    protected void dispose() {
        unwatch();
        if (form != null) {
            // A form outlives its fields -- a `collapse` closing takes a field
            // out of the tree and leaves the form standing -- so a field that did
            // not unregister would keep a form gated on a control nobody can see.
            form.unregister(this);
            form = null;
        }
        super.dispose();
    }

    /// Registers with the nearest enclosing form, once.
    ///
    /// Once and not per build: the ancestor cannot change without this element
    /// being rebuilt from a different parent, which unmounts it — and the lookup
    /// walks to the root, which is not something to do per frame.
    private void joinForm(BuildContext context) {
        if (looked) {
            return;
        }
        looked = true;
        form = io.github.digitalsmile.goldberry.widgets.form.form.FormAccess.of(context);
        if (form != null) {
            form.register(this);
        }
    }

    /// Subscribes to whatever the control inside this field is bound to.
    ///
    /// Re-checked on every build rather than only on mount, because the children
    /// are the widget's and a rebuild can carry different ones — a `field` whose
    /// control is chosen by the application is a `field` whose binding changes.
    private void follow() {
        var found = boundChild();
        if (found == source) {
            return;
        }
        unwatch();
        source = found;
        if (source != null) {
            // A property outlives the tree, so a listener left behind keeps this
            // subtree alive and rebuilds something nobody can see -- the same
            // trap `Screen` documents.
            watching = source.subscribe(value -> revalidate());
        }
    }

    private void unwatch() {
        if (watching != null) {
            watching.close();
            watching = null;
        }
    }

    /// The first child with a `bind=`, or null.
    ///
    /// One level deep, and deliberately: a field's control is what the document
    /// wrote inside it, and walking the whole subtree would find a binding on
    /// something incidental — the `text` in a hint under the control, say — and
    /// validate that instead.
    private Observable<?> boundChild() {
        for (var child : widget().children()) {
            if (child.binding() != null) {
                return child.binding();
            }
        }
        return null;
    }

    /// The value the control holds, as text.
    ///
    /// A `String` because that is what §4's validators are over: what a user
    /// typed is text until something parses it, and parsing is what a validator
    /// decides is possible. A control bound to a number reports its `toString`,
    /// which is what `select` and `text` already do with a binding.
    private String value() {
        if (source == null) {
            return null;
        }
        var current = source.get();
        return current == null ? null : String.valueOf(current);
    }

    /// The user has finished with this field.
    private void blurred() {
        complained = true;
        revalidate();
    }

    /// Re-runs the rule, but only once the field has earned the right to speak.
    private void revalidate() {
        if (!complained) {
            return;
        }
        check();
    }

    /// Runs the rule now and reports whether the field is happy.
    ///
    /// Called by [blurred], by a change once the field has complained, and by a
    /// `form` submitting — which is the one caller that makes a silent field
    /// speak whether or not it has been visited, because a form that submitted
    /// with an untouched required field empty would be a form that lost data.
    @Override
    public boolean check() {
        if (source == null) {
            // Nothing to validate against. A field learns its value from its
            // control's `bind=`, so one wrapping something unbound cannot see a
            // value at all -- and the alternative to passing is failing forever,
            // which would gate a form on a control the user can type into and
            // never satisfy. See [Field] for why this is not an error.
            return true;
        }
        complained = true;
        var result = widget().rule().check(value());
        var next = result.isValid() ? "" : result.message();
        if (!next.equals(message)) {
            setState(() -> message = next);
        }
        return result.isValid();
    }

    /// Whether this field would pass, **without** making it complain.
    ///
    /// What a form asks to decide whether its submit button is available. It has
    /// to be side-effect free: a form that reddened every field to work out
    /// whether to enable a button would redden them before anyone had typed
    /// anything.
    @Override
    public boolean isValid() {
        return source == null || widget().rule().check(value()).isValid();
    }

    /// Forgets that the user has been here, and clears the message.
    ///
    /// What a form does when it has been reset.
    @Override
    public void clear() {
        complained = false;
        if (!message.isEmpty()) {
            setState(() -> message = "");
        }
    }

    /// What this field is currently complaining about, or `""` — a form's error
    /// summary is the list of these.
    @Override
    public String message() {
        return message;
    }

    /// The bound value, for a form assembling what it would submit.
    String currentValue() {
        return value();
    }

}
