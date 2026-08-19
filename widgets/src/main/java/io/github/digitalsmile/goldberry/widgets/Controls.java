package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.bind.Bindings;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.kdl.KdlInflater;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.Primitives;
import io.github.digitalsmile.goldberry.widgets.controls.Scale;
import io.github.digitalsmile.goldberry.widgets.controls.badge.Badge;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox;
import io.github.digitalsmile.goldberry.widgets.controls.knob.Knob;
import io.github.digitalsmile.goldberry.widgets.controls.progressbar.Progress;
import io.github.digitalsmile.goldberry.widgets.controls.radio.Radio;
import io.github.digitalsmile.goldberry.widgets.controls.radio.RadioGroup;
import io.github.digitalsmile.goldberry.widgets.controls.segmented.Option;
import io.github.digitalsmile.goldberry.widgets.controls.segmented.Segmented;
import io.github.digitalsmile.goldberry.widgets.controls.slider.Slider;
import io.github.digitalsmile.goldberry.widgets.controls.spinner.Spinner;
import io.github.digitalsmile.goldberry.widgets.controls.toggle.Toggle;
import io.github.digitalsmile.goldberry.widgets.overlay.hud.Hud;
import io.github.digitalsmile.goldberry.widgets.overlay.hud.Reading;
import io.github.digitalsmile.goldberry.widgets.panel.tabs.Tab;
import io.github.digitalsmile.goldberry.widgets.panel.tabs.Tabs;
import io.github.digitalsmile.goldberry.widgets.menu.Item;
import io.github.digitalsmile.goldberry.widgets.menu.Menu;
import io.github.digitalsmile.goldberry.widgets.menu.Separator;
import io.github.digitalsmile.goldberry.widgets.overlay.popover.Popover;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/// The catalog: every widget this module ships, and how it is registered and
/// styled.
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

    /// Everything the toolkit itself contributes to the cascade, in order.
    ///
    /// The base rules, then the theme's colours, then the density's metrics —
    /// the last two both being custom-property stylesheets in the
    /// [CascadeLayer#THEME] slot, and both meaning nothing until a base rule
    /// reads them. An application adds its own sheets after these.
    ///
    /// Assembled here for the reason the rest of this class exists: the order
    /// matters, getting it wrong is silent rather than loud, and an application
    /// should not have to know that a density goes above a theme in a list.
    public static List<Stylesheet> stylesheets(Theme theme, Density density) {
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(density, "density");
        return Stream.concat(
                        Stream.of(baseStylesheet(), theme.load()),
                        density.stylesheets().stream())
                .toList();
    }

    /// The toolkit's stylesheets at [Density#REGULAR], which is §1.3's default.
    public static List<Stylesheet> stylesheets(Theme theme) {
        return stylesheets(theme, Density.REGULAR);
    }

    /// An inflater that knows `:core`'s primitives *and* the controls here.
    ///
    /// The structural widgets come from [Primitives#inflater()] rather than being
    /// repeated, so a markup document can mix `column` and `button` without the
    /// caller merging two registries.
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
        var inflater = Primitives.inflater(bindings);
        inflater.register("button", (node, children) -> new Button(
                node.argument().map(v -> v.asString()).orElse(""),
                // Markup names an icon; it cannot build one. An `Icon` owns
                // native memory and has to be closed, and a document reloaded
                // every keystroke would leak one per reload — so the icons a
                // markup file may use are the ones the application registered.
                icons.resolve(node.stringProperty("icon")),
                actions.resolve(node.stringProperty("press")),
                node.booleanProperty("disabled"),
                Attributes.of(node)));
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
                Attributes.of(node)));
        inflater.register("toggle", (node, children) -> {
            // A **valued** action, the second in the toolkit: what the user
            // asked for is `true` or `false` rather than "the other one",
            // because a drag is a request for a particular state and dragging
            // right on a switch already on asks for on (ADR-0075).
            //
            // It crosses as a `String` through the one valued shape the registry
            // has, rather than as a `Consumer<Boolean>` through a second one.
            // Erasure would make `bind(name, Consumer<String>)` and
            // `bind(name, Consumer<Boolean>)` ambiguous for every implicitly
            // typed lambda, so a second shape costs an awkwardly named method or
            // a bespoke interface -- and ADR-0073 already wrote the rule for
            // this: the value crosses as the string a document would have
            // written, and an application that wants another type parses it in
            // Java, where a bad value is a bug it can see. `slider` and `knob`
            // arrive at the same door.
            var change = actions.resolveValued(node.stringProperty("change"));
            return new Toggle(
                    node.argument().map(v -> v.asString()).orElse(""),
                    node.booleanProperty("on"),
                    bindings.resolve(node.stringProperty("bind")),
                    change == null ? null : value -> change.accept(String.valueOf(value)),
                    node.booleanProperty("disabled"),
                    Attributes.of(node));
        });
        inflater.register("slider", (node, children) -> {
            // The third valued action, and the first whose value is a *number*.
            // It still crosses as the string a document would have written, for
            // the reason ADR-0073 set and ADR-0075 reused: one valued shape in
            // the registry, and an application that wants a double parses it in
            // Java where a bad value is a bug it can see.
            var change = actions.resolveValued(node.stringProperty("change"));
            var min = node.numberProperty("min", 0);
            var max = node.numberProperty("max", 1);
            return new Slider(
                    min, max,
                    node.numberProperty("value", min),
                    node.numberProperty("step", 0),
                    // §3's two optional halves. `ticks` is a count and `format`
                    // is a pattern, so both are values a document can carry --
                    // unlike an action or an icon, neither names anything the
                    // application has to have registered (ADR-0080).
                    (int) node.numberProperty("ticks", 0),
                    node.stringProperty("format"),
                    // Strict, like every other name in a document: `scale="dB"`
                    // is refused rather than resolved quietly to linear, which
                    // would be a fader that works and is wrong.
                    Scale.of(node.stringProperty("scale")),
                    bindings.resolve(node.stringProperty("bind")),
                    change == null ? null : value -> change.accept(String.valueOf(value)),
                    node.booleanProperty("disabled"),
                    Attributes.of(node));
        });
        inflater.register("knob", (node, children) -> {
            // The fourth valued action, and it crosses as a string like the other
            // three (ADR-0073): one valued shape in the registry, and an
            // application that wants a double parses it in Java where a bad value
            // is a bug it can see.
            var change = actions.resolveValued(node.stringProperty("change"));
            var min = node.numberProperty("min", 0);
            var max = node.numberProperty("max", 1);
            return new Knob(
                    min, max,
                    node.numberProperty("value", min),
                    node.numberProperty("step", 0),
                    // A count, like `slider`'s `ticks` -- a value a document can
                    // carry, naming nothing the application has to have
                    // registered (ADR-0080).
                    (int) node.numberProperty("detents", 0),
                    bindings.resolve(node.stringProperty("bind")),
                    change == null ? null : value -> change.accept(String.valueOf(value)),
                    node.booleanProperty("disabled"),
                    Attributes.of(node));
        });
        inflater.register("progress", (node, children) -> new Progress(
                node.numberProperty("value", 0),
                node.numberProperty("max", 1),
                node.booleanProperty("indeterminate"),
                bindings.resolve(node.stringProperty("bind")),
                Attributes.of(node)));
        // The one widget in the catalog with no attributes of its own: a spinner
        // has no value, no state and nothing to say. It still takes an id and
        // classes, because everything CSS-selectable does (§11).
        inflater.register("spinner", (node, children) -> new Spinner(
                Attributes.of(node)));
        // §3's only entry that is not a control: no action, no state, nothing to
        // resolve against a registry. A count is the archetypal bound value, so
        // `bind` is the one wiring it takes, and the literal argument stays as
        // the fallback the way `text`'s does.
        inflater.register("badge", (node, children) -> new Badge(
                node.argument().map(v -> v.asString()).orElse(""),
                bindings.resolve(node.stringProperty("bind")),
                Attributes.of(node)));
        inflater.register("radio-group", (node, children) -> new RadioGroup(
                node.stringProperty("value"),
                children,
                bindings.resolve(node.stringProperty("bind")),
                // The first action that has to say which one -- a group's handler
                // is useless without the value picked, and one action per option
                // would make adding an option an edit in Java too (ADR-0073).
                actions.resolveValued(node.stringProperty("change")),
                node.booleanProperty("disabled"),
                Attributes.of(node)));
        inflater.register("radio", (node, children) -> new Radio(
                // The value is what the group reports and what it matches on, so
                // an option without one cannot be picked or shown as picked.
                // Refused here rather than defaulting to the label: two options
                // sharing a defaulted value would select together, which looks
                // like a bug in the toolkit rather than in the document.
                requiredValue("radio", node.stringProperty("value")),
                node.argument().map(v -> v.asString()).orElse(""),
                // `selected` and the action are the group's to supply on every
                // build, which is why neither is an attribute: a document that
                // could mark two options selected would break the one invariant a
                // group exists to hold.
                false, null,
                node.booleanProperty("disabled"),
                Attributes.of(node)));
        inflater.register("segmented", (node, children) -> new Segmented(
                node.stringProperty("value"),
                children,
                bindings.resolve(node.stringProperty("bind")),
                // The same valued action `radio-group` takes, because §3 says
                // this control shares that model exactly -- a set's handler is
                // useless without the value picked (ADR-0073).
                actions.resolveValued(node.stringProperty("change")),
                node.booleanProperty("disabled"),
                Attributes.of(node)));
        inflater.register("option", (node, children) -> new Option(
                // Required for the reason a `radio`'s is: the value is what the
                // control reports and what it matches on, and two segments
                // sharing a defaulted value would select together.
                requiredValue("option", node.stringProperty("value")),
                node.argument().map(v -> v.asString()).orElse(""),
                // Named, not built -- the rule `button` set: an `Icon` owns
                // native memory, so markup may only name one the application
                // registered.
                icons.resolve(node.stringProperty("icon")),
                // `selected` and the action are the control's to supply on every
                // build, which is why neither is an attribute.
                false, null,
                node.booleanProperty("disabled"),
                Attributes.of(node)));
        // §7's first overlay, and the only widget in the catalog that reads
        // something about the frame loop rather than about a model. Nothing to
        // bind and nothing to resolve: what it shows arrives on the render
        // context, so a document writes `hud` and is done.
        // §5's tab strip, and the tabs that go in it. `close` and `new` are the
        // two halves of "a tab strip's list is the application's": the strip asks
        // and the application answers, exactly as `change` does for the selection
        // (ADR-0063, ADR-0107).
        inflater.register("tabs", (node, children) -> new Tabs(
                node.stringProperty("value"),
                children,
                bindings.resolve(node.stringProperty("bind")),
                actions.resolveValued(node.stringProperty("change")),
                actions.resolveValued(node.stringProperty("close")),
                actions.resolve(node.stringProperty("new")),
                Attributes.of(node)));
        inflater.register("tab", (node, children) -> new Tab(
                // Required for a `radio`'s reason: the value is what the strip
                // reports and what it matches on, and two tabs sharing a
                // defaulted value would select together.
                requiredValue("tab", node.stringProperty("value")),
                node.argument().map(v -> v.asString()).orElse(""),
                icons.resolve(node.stringProperty("icon")),
                // Written the way a stylesheet writes a colour, because it is one
                // — an author who knows `#bf616a` in CSS writes the same here.
                colour(node.stringProperty("colour"), node.stringProperty("color")),
                node.booleanProperty("closable"),
                children,
                // `selected`, the two handlers and the arrival phase are the
                // strip's to supply on every build, which is why none of them is
                // an attribute: a document that could mark two tabs selected would
                // break the one invariant a strip exists to hold.
                false, null, null, null, null, null,
                Attributes.of(node)));
        // §8's menu, its rows and its rules. A document declares a menu; opening
        // one is `Menus.open(host, …)`, because that needs a `Host` and a widget
        // must not have one (ADR-0106).
        inflater.register("menu", (node, children) -> new Menu(
                children, Attributes.of(node)));
        inflater.register("item", (node, children) -> new Item(
                node.argument().map(v -> v.asString()).orElse(""),
                icons.resolve(node.stringProperty("icon")),
                node.stringProperty("accelerator"),
                actions.resolve(node.stringProperty("press")),
                // Three states: `checked=#true` is on, `checked=#false` is a
                // checkable row that is off, and no attribute at all is a row
                // that is not checkable — which is what decides whether its menu
                // reserves a tick column (ADR-0113).
                node.property("checked").isPresent() ? node.booleanProperty("checked") : null,
                node.booleanProperty("disabled"),
                // A nested `item` is a submenu, which is the only thing a menu
                // item can contain -- so nesting *is* the syntax and there is no
                // `submenu` node to forget.
                children,
                // `reservesCheck` and the hover callback are the menu's to supply
                // on every open, which is why neither is an attribute.
                false, null,
                Attributes.of(node)));
        inflater.register("separator", (node, children) -> new Separator(Attributes.of(node)));
        // §7's floating panel. It is the panel and not the opening: where a
        // popover goes and when it goes away is `Host.popup`'s, which serves a
        // tooltip and a select equally and is not a widget (ADR-0104).
        inflater.register("popover", (node, children) -> new Popover(
                children, Attributes.of(node)));
        inflater.register("hud", (node, children) -> new Hud(
                readings(node.stringProperty("readings")),
                Attributes.of(node)));
        return inflater;
    }

    /// `colour="#bf616a"`, or `color=` for whoever spells it that way.
    ///
    /// Both, because CSS spells it `color` and this document's prose spells it
    /// `colour`, and an author guessing wrong should get a colour rather than a
    /// silent default.
    ///
    /// @return the colour as `0xAARRGGBB`, or 0 for "the stylesheet decides"
    private static int colour(String british, String american) {
        var parsed = io.github.digitalsmile.goldberry.css.CssColor.parse(
                british != null ? british : american);
        return parsed == null ? 0 : parsed;
    }

    /// `readings="fps paint"` — a space-separated list, like `class`.
    ///
    /// Null and blank both mean [Hud#DEFAULT] rather than an error: a bare `hud`
    /// is the form almost every document will write.
    private static List<Reading> readings(String value) {
        if (value == null || value.isBlank()) {
            return Hud.DEFAULT;
        }
        return List.of(value.trim().split("\\s+")).stream().map(Reading::parse).toList();
    }

    private static String requiredValue(String node, String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(
                    "a " + node + " needs a value= for its group to report and match on;"
                            + " `" + node + " value=\"dark\" \"Dark\"`");
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
    /// **Controls only.** The parts — `check-indicator`, `check-mark`,
    /// `radio-indicator`, `radio-dot`, `toggle-track`, `toggle-thumb`,
    /// `slider-track`, `slider-groove`, `slider-fill`, `slider-thumb`,
    /// `slider-rest`, `slider-ticks`, `slider-tick`, `slider-value`,
    /// `progress-fill`, `knob-track`, `knob-arc`, `segmented-track` and
    /// `segmented-indicator` — are
    /// styled by the same stylesheet and are not here, because they are parts
    /// rather than widgets: they are CSS-selectable and deliberately not
    /// KDL-constructible, and asking the parity test to inflate one would be
    /// asking for a node with no meaning outside its parent (see
    /// `CheckIndicator`, `CheckMark`, `RadioIndicator`, `RadioDot`,
    /// `ToggleTrack`, `ToggleThumb`, `SliderTrack`, `SliderGroove`,
    /// `SliderTicks`, `SliderTick`, `SliderValue`, `KnobTrack`, `KnobArc`,
    /// `KnobDial`, `SegmentedTrack` and `SegmentedIndicator` — code spans rather
    /// than links, because a part is
    /// package-private inside `…widgets.controls` and this class is not in it
    /// (ADR-0091). A link that cannot resolve is worse than a name).
    public static List<String> controlTypes() {
        return List.of("button", "checkbox", "toggle", "slider", "radio-group", "radio",
                "segmented", "option", "progress", "spinner", "badge", "knob");
    }
}
