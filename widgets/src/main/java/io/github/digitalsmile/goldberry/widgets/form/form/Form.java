package io.github.digitalsmile.goldberry.widgets.form.form;

import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.Markup;
import io.github.digitalsmile.goldberry.widgets.Wiring;
import java.util.List;
import java.util.Objects;

/// A set of `field`s that submit together — `docs/core-widgets.md` §4's `form`.
///
/// ```kdl
/// form submit="app.save" {
///     field label="Name" required=#true { text-input bind="user.name" }
///     field label="Port" { text-input bind="user.port" filter="digits" }
///     row class="actions" { spacer; button class="primary" "Save" }
/// }
/// ```
///
/// ## The fields find the form, not the other way round
///
/// A `form`'s fields are anywhere in its subtree — inside rows, inside cards,
/// inside a `collapse` — so a form that went looking for them would have to walk
/// its whole subtree and know what to skip. Instead each [io.github.digitalsmile.goldberry.widgets.form.field.Field]
/// registers with the nearest enclosing form when it mounts, through
/// `BuildContext.findAncestorState`.
///
/// That call has been on `BuildContext` since the element tree was built and this
/// is its **first consumer**. `TabsState` looked at it and said it "looks the
/// wrong way" — which was right for tabs, where a strip needs to enumerate its
/// panels, and is exactly right here: a field knows one form and a form knows
/// however many fields the document wrote.
///
/// ## What `submit` carries, and what it does not
///
/// §4 says `form.submit()` "raises a typed event with bound values". The event
/// here carries **nothing**, and the reason is that the values are already the
/// application's: a `bind=` reads *from* the application's model, so a submit
/// event carrying the bound values would be handing an application its own data
/// back. The toolkit could not name them anyway —
/// [Widget#binding()] is an `Observable` and not a path, which is what makes
/// `bind=` a read-only channel in the first place ([ADR-0063]).
///
/// So `submit="app.save"` is called when every field passes, and `app.save`
/// reads the model it already owns.
///
/// ## Gating
///
/// Submitting validates **every** field, including ones nobody has visited —
/// otherwise a form with an untouched required field would submit empty. That is
/// the one moment a field speaks without having been left first.
///
/// @param children   whatever the document wrote inside
/// @param onSubmit   run when every field passes, or null
/// @param attributes the `id`, classes and key the document wrote
@Markup("form")
public record Form(List<Widget> children, Runnable onSubmit, FormController controller,
        Attributes attributes)
        implements Widget.Stateful, Attributed<Form> {

    public Form {
        children = List.copyOf(Objects.requireNonNull(children, "children"));
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A form around some widgets, with nothing wired.
    public Form(Widget... children) {
        this(List.of(children), null, null, Attributes.NONE);
    }

    /// This form, calling `action` when it submits and every field passes.
    public Form onSubmit(Runnable action) {
        return new Form(children, action, controller, attributes);
    }

    /// This form, reachable through `handle`.
    ///
    /// What a Save button holds — see [FormController]. A form with no controller
    /// still validates on blur; it simply has nothing that can submit it.
    public Form controller(FormController handle) {
        return new Form(children, onSubmit, handle, attributes);
    }

    @Override
    public Form withAttributes(Attributes value) {
        return new Form(children, onSubmit, controller, value);
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new FormState();
    }

    /// Builds a `form` from markup.
    ///
    /// `controller="app.signup"` names a [FormController] the application holds,
    /// through the same registry and the same path syntax `bind=` uses — because
    /// a controller is a thing a document can *name* and cannot describe, which
    /// is the rule markup has always followed for actions and icons
    /// ([ADR-0170]). Without it a document could declare a form and nothing
    /// could ever submit it.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Form(children, wiring.action(node, "submit"),
                wiring.handle(node, "controller", FormController.class), Attributes.of(node));
    }
}
