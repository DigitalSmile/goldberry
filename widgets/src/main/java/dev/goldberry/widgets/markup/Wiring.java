package dev.goldberry.widgets.markup;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.bind.Observable;
import dev.goldberry.bind.registry.ActionRegistry;
import dev.goldberry.bind.registry.BindingRegistry;
import dev.goldberry.bind.runtime.Models;
import dev.goldberry.css.value.CssColor;
import dev.goldberry.icon.Icon;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.widgets.Icons;

/// The registries a document resolves names against, and the readings of a
/// node that nearly every widget's `inflate` method needs.
///
/// ```java
/// var wiring = Wiring.of(icons, appModel, windowModel).with(named);
/// ```
///
/// The four registries answer four different questions: what a name does
/// ([ActionRegistry], for `press=` and `change=`), what it draws ([Icons], for
/// `icon=`), where a value lives ([BindingRegistry], for `bind=`) and which
/// object it is ([Named], for `controller=` and `validator=`). They travel
/// together because a factory generally needs more than one.
///
/// The static readings — [#label], [#colour], [#disabled], [#requiredValue] —
/// are the attribute readings every widget would otherwise write out again, so
/// that a widget's factory contains only what is particular to that widget.
///
/// @param actions  what a `press="save"` attribute resolves against
/// @param icons    what an `icon="plus"` attribute resolves against
/// @param bindings what a `bind="app.gain"` attribute resolves against
/// @param named    what a `controller="app.signup-form"` attribute resolves against
///
/// Read more: [Markup](https://goldberry.dev/docs/guide/markup.html#the-four-registries).
public record Wiring(ActionRegistry actions, Icons icons, BindingRegistry bindings, Named named) {

    /// Nothing bound, and no complaints — what a preview or a golden image wants.
    public static Wiring none() {
        return new Wiring(ActionRegistry.none(), Icons.none(), BindingRegistry.none(), Named.none());
    }

    /// The three registries a model publishes, with nothing named.
    public Wiring(ActionRegistry actions, Icons icons, BindingRegistry bindings) {
        this(actions, icons, bindings, Named.none());
    }

    /// This wiring, with `named` for the objects a document refers to.
    public Wiring with(Named named) {
        return new Wiring(actions, icons, bindings, named);
    }

    /// The wiring `models` publish, with icons.
    ///
    /// A model already declares its paths (`@Bind`) and its actions (`@Action`),
    /// so the application hands the models over and this reads both registries
    /// off them. Several models are allowed because a window's own actions —
    /// "open the menu", "toggle the HUD" — belong to the window rather than to
    /// the view model. A later model may not re-declare an earlier one's name:
    /// two features quietly sharing one path presents as a value changing by
    /// itself.
    ///
    /// @throws IllegalStateException if two models claim one name
    /// @throws IllegalStateException if any of them is annotated neither
    ///         `@Model` nor `@Actions`, or is annotated and cannot be bound; a
    ///         model that was not woven at build time is bound by reflection
    ///         at run time rather than refused
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
        return new Wiring(actions, icons, bindings, Named.none());
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
            throw new IllegalArgumentException("a " + node + " needs a value= for its group to report and match on;"
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
        return colour(node, "colour", "color");
    }

    /// A colour under a name of the caller's choosing, spelled either way.
    ///
    /// `dot-colour="#bf616a"` and `dot-color="#bf616a"` both answer, for
    /// [#colour(KdlNode)]'s reason: a widget that carries more than one colour
    /// needs more than one name, and each of them still has two spellings.
    ///
    /// @param british the property as this repository's prose spells it
    /// @param american the same property as CSS spells it
    /// @return the colour as `0xAARRGGBB`, or 0 for "the stylesheet decides"
    public static int colour(KdlNode node, String british, String american) {
        var written = node.stringProperty(british);
        var parsed = CssColor.parse(written != null ? written : node.stringProperty(american));
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
    /// binding: data flows down and events flow up, so markup names where a
    /// value comes from and has no way to name where it goes.
    public @Nullable Observable<?> bound(KdlNode node) {
        return bindings.resolve(node.stringProperty("bind"));
    }

    /// The **object** an attribute names — a `FormController`, a `Validator`.
    ///
    /// Resolved through [Named], the registry for objects that neither change
    /// nor close: an action is a method and a binding is a value that changes,
    /// and a controller is neither.
    ///
    /// @param type what the named object has to be
    /// @return the object, or null when the attribute is absent
    /// @throws IllegalArgumentException if the name is registered as something
    ///         that is not a `type`, or is unknown to a strict registry
    public <T> @Nullable T handle(KdlNode node, String attribute, Class<T> type) {
        return named.resolve(node.stringProperty(attribute), type);
    }

    /// The icon `icon=` names.
    ///
    /// Named and not built: an `Icon` owns native memory and has to be closed, so
    /// a document reloaded on every keystroke may only name one the application
    /// registered.
    public @Nullable Icon icon(KdlNode node) {
        return icons.resolve(node.stringProperty("icon"));
    }

    /// The action an attribute names, for a control that reports only *that*
    /// something happened.
    public @Nullable Runnable action(KdlNode node, String attribute) {
        return actions.resolve(node.stringProperty(attribute));
    }

    /// The action an attribute names, for a control that reports *what* it should
    /// become.
    public @Nullable Consumer<String> valued(KdlNode node, String attribute) {
        return actions.resolveValued(node.stringProperty(attribute));
    }

    /// The same, for a control whose value is a number.
    ///
    /// The number still crosses as the string a document would have written:
    /// the registry has one valued shape, and an application that wants a
    /// `double` parses it in Java, where a bad value is a bug it can see.
    /// `toggle`, `slider` and `knob` all report through this.
    public @Nullable DoubleConsumer numeric(KdlNode node, String attribute) {
        var change = valued(node, attribute);
        return change == null ? null : value -> change.accept(String.valueOf(value));
    }

    /// The same again, for a control whose value is a flag.
    ///
    /// `toggle` reports `true` or `false` rather than "the other one", because a
    /// drag is a request for a particular state: dragging right on a switch that
    /// is already on asks for on.
    public @Nullable Consumer<Boolean> flag(KdlNode node, String attribute) {
        var change = valued(node, attribute);
        return change == null ? null : value -> change.accept(String.valueOf(value));
    }
}
