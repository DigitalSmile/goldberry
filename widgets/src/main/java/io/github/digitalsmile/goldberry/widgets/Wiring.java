package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.bind.ActionRegistry;
import io.github.digitalsmile.goldberry.bind.BindingRegistry;
import io.github.digitalsmile.goldberry.bind.Models;
import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.css.CssColor;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/// The three registries a document resolves names against, and the readings of a
/// node that nearly every widget needs.
///
/// §9 asks for three lookups because they answer three different questions: what
/// a name *does* ([ActionRegistry]), what a name *draws* ([Icons]), and where a value
/// *lives* ([BindingRegistry]). They travel together because a factory generally needs
/// more than one of them, and threading three parameters through nineteen
/// registrations was three chances to pass the wrong one.
///
/// ## Why the readings are here
///
/// `node.argument().map(v -> v.asString()).orElse("")` appeared eight times in
/// the catalog, and `change == null ? null : value -> change.accept(String
/// .valueOf(value))` three times. Neither is a decision — they are the same
/// sentence written out again — and a widget's factory should be the part that
/// differs ([ADR-0130](../../../../../../book/src/adr/0130-a-widget-inflates-itself.md)).
///
/// @param actions  what a `press="save"` attribute resolves against
/// @param icons    what an `icon="plus"` attribute resolves against
/// @param bindings what a `bind="app.gain"` attribute resolves against
public record Wiring(ActionRegistry actions, Icons icons, BindingRegistry bindings) {

    /// Nothing bound, and no complaints — what a preview or a golden image wants.
    public static Wiring none() {
        return new Wiring(ActionRegistry.none(), Icons.none(), BindingRegistry.none());
    }

    /// The wiring `models` publish, with icons.
    ///
    /// What removes `Models.bindings(model)` and `Models.actions(model)` from an
    /// application: a model already declares its paths and its actions, and
    /// asking the caller to fetch both and hand them back was ceremony around a
    /// fact the object already carried
    /// ([ADR-0132](../../../../../../book/src/adr/0132-a-model-wires-itself.md)).
    ///
    /// **More than one model**, because a window's own actions — "open the menu",
    /// "toggle the HUD" — belong to the window rather than to the view model, and
    /// making the application merge two registries by hand is the thing this
    /// exists to stop. Later models may not re-declare an earlier one's name: two
    /// features quietly sharing one path is a bug that presents as a value
    /// changing by itself, and it is no less a bug for the two being in different
    /// classes.
    ///
    /// @throws IllegalStateException if two models claim one name
    /// @throws IllegalStateException if any model was not woven
    public static Wiring of(Icons icons, Object... models) {
        Objects.requireNonNull(icons, "icons");
        var bindings = BindingRegistry.strict();
        var actions = ActionRegistry.strict();
        for (var model : models) {
            Models.bindings(model).bound().forEach(bindings::bind);
            Models.actions(model).bound().forEach((name, handler) -> {
                if (handler instanceof Consumer<?> valued) {
                    @SuppressWarnings("unchecked")
                    var typed = (Consumer<String>) valued;
                    actions.bind(name, typed);
                } else {
                    actions.bind(name, (Runnable) handler);
                }
            });
        }
        return new Wiring(actions, icons, bindings);
    }

    /// The wiring `models` publish, with no icons.
    public static Wiring of(Object... models) {
        return of(Icons.none(), models);
    }

    // --- what a node says ----------------------------------------------------

    /// The node's primary content — `button "Apply"` — or `""`.
    ///
    /// Empty rather than null, because every widget that takes one treats a
    /// missing label as an empty one and none of them wants to write the check.
    public static String label(KdlNode node) {
        return node.argument().map(value -> value.asString()).orElse("");
    }

    /// A `value=` that the widget cannot do without.
    ///
    /// Refused rather than defaulted to the label: the value is what a group
    /// reports and what it matches on, and two options sharing a defaulted value
    /// would select together — which looks like a bug in the toolkit rather than
    /// in the document.
    public static String requiredValue(String node, KdlNode from) {
        var value = from.stringProperty("value");
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(
                    "a " + node + " needs a value= for its group to report and match on;"
                            + " `" + node + " value=\"dark\" \"Dark\"`");
        }
        return value;
    }

    /// `colour="#bf616a"`, or `color=` for whoever spells it that way.
    ///
    /// Both, because CSS spells it `color` and this repository's prose spells it
    /// `colour`, and an author guessing wrong should get a colour rather than a
    /// silent default.
    ///
    /// @return the colour as `0xAARRGGBB`, or 0 for "the stylesheet decides"
    public static int colour(KdlNode node) {
        var british = node.stringProperty("colour");
        var parsed = CssColor.parse(british != null ? british : node.stringProperty("color"));
        return parsed == null ? 0 : parsed;
    }

    /// Whether `disabled` is set.
    public static boolean disabled(KdlNode node) {
        return node.booleanProperty("disabled");
    }

    // --- what a node names ---------------------------------------------------

    /// The value `bind=` names, read-only.
    ///
    /// An [Observable] and never a `Property`, which is the whole of one-way
    /// binding: markup names where a value comes from and has no way to name
    /// where it goes (ADR-0063).
    public Observable<?> bound(KdlNode node) {
        return bindings.resolve(node.stringProperty("bind"));
    }

    /// The icon `icon=` names.
    ///
    /// Named and not built: an `Icon` owns native memory and has to be closed, so
    /// a document reloaded on every keystroke may only name one the application
    /// registered.
    public Icon icon(KdlNode node) {
        return icons.resolve(node.stringProperty("icon"));
    }

    /// The action an attribute names, for a control that reports only *that*
    /// something happened.
    public Runnable action(KdlNode node, String attribute) {
        return actions.resolve(node.stringProperty(attribute));
    }

    /// The action an attribute names, for a control that reports *what* it should
    /// become.
    public Consumer<String> valued(KdlNode node, String attribute) {
        return actions.resolveValued(node.stringProperty(attribute));
    }

    /// The same, for a control whose value is a number.
    ///
    /// It still crosses as the string a document would have written, for the
    /// reason ADR-0073 set and ADR-0075 reused: one valued shape in the registry,
    /// and an application that wants a `double` parses it in Java, where a bad
    /// value is a bug it can see. `toggle`, `slider` and `knob` all arrive at this
    /// door, and used to write the adapter out one at a time.
    public DoubleConsumer numeric(KdlNode node, String attribute) {
        var change = valued(node, attribute);
        return change == null ? null : value -> change.accept(String.valueOf(value));
    }

    /// The same again, for a control whose value is a flag.
    ///
    /// `toggle` reports `true` or `false` rather than "the other one", because a
    /// drag is a request for a particular state and dragging right on a switch
    /// already on asks for on (ADR-0075).
    public Consumer<Boolean> flag(KdlNode node, String attribute) {
        var change = valued(node, attribute);
        return change == null ? null : value -> change.accept(String.valueOf(value));
    }
}
