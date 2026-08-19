package io.github.digitalsmile.goldberry.widgets;



import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;






















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

    // The inflater used to be here, as a table of nineteen names. It is now
    // generated: every `@Markup` widget in this module is collected by the build
    // into a `WidgetCatalog`, and `Widgets.inflater(icons, model)` gathers the
    // catalogs of every widget module on the path (ADR-0131). What is left here
    // is the other half of what this class always shipped -- the stylesheets,
    // because a control that is registered but unstyled renders as a transparent
    // rectangle.

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
