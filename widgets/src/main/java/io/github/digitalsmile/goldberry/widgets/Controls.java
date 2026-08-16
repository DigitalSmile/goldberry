package io.github.digitalsmile.goldberry.widgets;

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
        var inflater = Widgets.inflater();
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
        return inflater;
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
    public static List<String> controlTypes() {
        return List.of("button");
    }
}
