package io.github.digitalsmile.goldberry.widgets.form.form;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.form.field.Validated;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// The fields a [Form] has collected, and what happens when it submits.
///
/// ## Registration order is document order, near enough
///
/// Fields register as they mount, and elements mount depth-first in the order
/// their parent described them — so the error summary reads top to bottom, which
/// is the only order it could usefully have. "Near enough" because a field added
/// to the middle of a form later registers last; a summary that mattered that
/// much would sort by the fields' painted position, which is a frame's question
/// and not a model's.
///
/// A [LinkedHashSet] rather than a list: a field that rebuilds must not register
/// twice, and the order still has to hold.
final class FormState extends State<Form> implements FormAccess {

    private final Set<Validated> fields = new LinkedHashSet<>();

    @Override
    protected void initState() {
        super.initState();
        attach(widget().controller());
    }

    @Override
    protected void didUpdateWidget(Form previous) {
        super.didUpdateWidget(previous);
        if (previous.controller() != widget().controller()) {
            detach(previous.controller());
            attach(widget().controller());
        }
    }

    @Override
    protected void dispose() {
        // A controller outlives its form -- an application holds it -- so one
        // still pointing here would submit a tree that is gone.
        detach(widget().controller());
        super.dispose();
    }

    private void attach(FormController controller) {
        if (controller != null) {
            controller.attached = this;
        }
    }

    private void detach(FormController controller) {
        if (controller != null && controller.attached == this) {
            controller.attached = null;
        }
    }

    @Override
    public Widget build(BuildContext context) {
        return new FormBox(widget().children(), widget().attributes());
    }

    /// Called by a field mounting inside this form.
    ///
    /// **No `setState`.** A field registering does not change anything this form
    /// draws — the summary is built from what the fields are complaining about,
    /// and a freshly mounted field is complaining about nothing. Marking the form
    /// dirty here would rebuild the whole form once per field on the frame it
    /// first appears.
    @Override
    public void register(Validated field) {
        fields.add(field);
    }

    /// Called by a field going away. Idempotent.
    @Override
    public void unregister(Validated field) {
        fields.remove(field);
    }

    /// Validates every field and, if they all pass, runs the form's `submit`.
    ///
    /// **Every** field, including ones nobody has visited: a form that only
    /// checked what had been touched would submit with an untouched required
    /// field empty. This is the one moment a field speaks without having been
    /// left first.
    ///
    /// Every field is checked even after the first failure, because the summary
    /// is the list of what is wrong and a summary with one entry when three
    /// things are wrong sends somebody round the form three times.
    ///
    /// @return whether it submitted
    public boolean submit() {
        var ok = true;
        for (var field : fields) {
            ok &= field.check();
        }
        if (!ok) {
            return false;
        }
        if (widget().onSubmit() != null) {
            widget().onSubmit().run();
        }
        return true;
    }

    /// Whether every field would pass — **without** making any of them complain.
    ///
    /// What a submit button asks to decide whether it is available. It has to be
    /// side-effect free, or a form would redden every field to work out whether
    /// to enable a button, before anyone had typed anything.
    public boolean isValid() {
        return fields.stream().allMatch(Validated::isValid);
    }

    /// What the fields are currently complaining about, in registration order —
    /// §4's error summary.
    public List<String> errors() {
        var messages = new ArrayList<String>();
        for (var field : fields) {
            var message = field.message();
            if (!message.isEmpty()) {
                messages.add(message);
            }
        }
        return List.copyOf(messages);
    }

    /// Clears every field's message and forgets that they have been visited.
    public void reset() {
        fields.forEach(Validated::clear);
    }

    /// How many fields have registered. For a test, and for a diagnostic that
    /// wants to know whether a form found the fields somebody thinks it has.
    public int fieldCount() {
        return fields.size();
    }
}
