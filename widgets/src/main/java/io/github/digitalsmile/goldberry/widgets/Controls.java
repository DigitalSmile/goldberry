package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.bind.Bindings;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.kdl.KdlInflater;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.Widgets;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/// The catalog: what `:widgets` adds to `:core`'s primitives, and how it is
/// registered and styled.
///
/// Two things an application needs and should not have to assemble itself — the
/// KDL registry and the default appearance — because a control that is registered
/// but unstyled renders as a transparent rectangle, and one that is styled but
/// unregistered fails to inflate. They ship together.
public final class Controls {

    private Controls() {
    }

    /// The default appearance of every control here, for the
    /// [CascadeLayer#TOOLKIT_BASE] layer.
    ///
    /// Bottom of the cascade, so a theme's custom properties and then the
    /// application's own rules both win over it without either having to fight
    /// specificity (§8). It reads `var(--gb-*)` throughout and names no colour of
    /// its own, which is what lets switching a theme restyle a button that never
    /// mentions one (§10).
    public static Stylesheet baseStylesheet() {
        return Stylesheet.parse(CascadeLayer.TOOLKIT_BASE, baseSource());
    }

    /// The base stylesheet's text, as it ships.
    ///
    /// Public for the same reason [io.github.digitalsmile.goldberry.css.Theme#source()]
    /// is: someone overriding a control's look should be able to read what they
    /// are overriding rather than guess at it.
    public static String baseSource() {
        var resource = "controls.css";
        try (InputStream in = Controls.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(
                        "the control stylesheet is missing from the jar: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + resource, e);
        }
    }

    /// An inflater that knows `:core`'s primitives *and* the controls here.
    ///
    /// The primitives come from [Widgets#inflater()] rather than being repeated,
    /// so a markup document can mix `column` and `button` without the caller
    /// merging two registries.
    ///
    /// @param actions what a `press="save"` attribute resolves against — see
    ///                [Actions]
    /// @param icons   what an `icon="plus"` attribute resolves against — see
    ///                [Icons]
    public static KdlInflater<Widget> inflater(Actions actions, Icons icons) {
        return inflater(actions, icons, Bindings.none());
    }

    /// An inflater with actions, icons **and** bindings bound.
    ///
    /// The three registries §9 asks for, and they are three rather than one
    /// because they answer three different questions: what a name *does*, what a
    /// name *draws*, and where a value *lives*. An application that uses one and
    /// not the others says so by passing [Actions#none()] and friends.
    ///
    /// @param bindings what a `bind="prefs.frost"` attribute resolves against —
    ///                 see [io.github.digitalsmile.goldberry.bind.Bindings]
    public static KdlInflater<Widget> inflater(Actions actions, Icons icons, Bindings bindings) {
        var inflater = Widgets.inflater(bindings);
        inflater.register("button", (node, children) -> new Button(
                node.argument().map(v -> v.asString()).orElse(""),
                // Markup names an icon; it cannot build one. An `Icon` owns
                // native memory and has to be closed, and a document reloaded
                // every keystroke would leak one per reload — so the icons a
                // markup file may use are the ones the application registered.
                icons.resolve(node.stringProperty("icon")),
                actions.resolve(node.stringProperty("press")),
                node.booleanProperty("disabled"),
                Widgets.Attributes.of(node)));
        inflater.register("checkbox", (node, children) -> new Checkbox(
                node.argument().map(v -> v.asString()).orElse(""),
                // `indeterminate` wins over `checked`, because a document that
                // says both has said something contradictory and the mixed state
                // is the one that cannot be reached any other way -- resolving it
                // to "checked" would silently discard the more specific claim.
                node.booleanProperty("indeterminate")
                        ? Checkbox.Value.MIXED
                        : Checkbox.Value.of(node.booleanProperty("checked")),
                // Read-only by construction: the registry hands out the
                // `Observable` half, so markup can name where a value comes from
                // and has no way to name where it goes (ADR-0063).
                bindings.resolve(node.stringProperty("bind")),
                actions.resolve(node.stringProperty("change")),
                node.booleanProperty("disabled"),
                Widgets.Attributes.of(node)));
        inflater.register("radio-group", (node, children) -> new RadioGroup(
                node.stringProperty("value"),
                children,
                bindings.resolve(node.stringProperty("bind")),
                // The first action that has to say which one -- a group's handler
                // is useless without the value picked, and one action per option
                // would make adding an option an edit in Java too (ADR-0073).
                actions.resolveValued(node.stringProperty("change")),
                node.booleanProperty("disabled"),
                Widgets.Attributes.of(node)));
        inflater.register("radio", (node, children) -> new Radio(
                // The value is what the group reports and what it matches on, so
                // an option without one cannot be picked or shown as picked.
                // Refused here rather than defaulting to the label: two options
                // sharing a defaulted value would select together, which looks
                // like a bug in the toolkit rather than in the document.
                requiredValue(node.stringProperty("value")),
                node.argument().map(v -> v.asString()).orElse(""),
                // `selected` and the action are the group's to supply on every
                // build, which is why neither is an attribute: a document that
                // could mark two options selected would break the one invariant a
                // group exists to hold.
                false, null,
                node.booleanProperty("disabled"),
                Widgets.Attributes.of(node)));
        return inflater;
    }

    private static String requiredValue(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(
                    "a radio needs a value= for its group to report and match on;"
                            + " `radio value=\"dark\" \"Dark\"`");
        }
        return value;
    }

    /// An inflater with actions bound and no icons.
    public static KdlInflater<Widget> inflater(Actions actions) {
        return inflater(actions, Icons.none());
    }

    /// An inflater with nothing bound: every `press=` and `icon=` resolves to
    /// nothing.
    ///
    /// What a golden image or a layout preview wants — the markup is about what
    /// the window looks like, and a preview that had to supply a handler for
    /// every button in it would be unusable.
    public static KdlInflater<Widget> inflater() {
        return inflater(Actions.none(), Icons.none());
    }

    /// The CSS type names this module adds, which is what the parity test checks
    /// the other two forms against.
    ///
    /// **Controls only.** The four parts — `check-indicator`, `check-mark`,
    /// `radio-indicator` and `radio-dot` — are styled by the same stylesheet and
    /// are not here, because they are parts rather than widgets: they are
    /// CSS-selectable and deliberately not KDL-constructible, and asking the
    /// parity test to inflate one would be asking for a node with no meaning
    /// outside its parent (see [CheckIndicator], [CheckMark], [RadioIndicator],
    /// [RadioDot]).
    public static List<String> controlTypes() {
        return List.of("button", "checkbox", "radio-group", "radio");
    }
}
