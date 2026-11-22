package dev.goldberry.vector.view;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.icon.Icon;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.image.Fit;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// An SVG document, drawn in the colour of the text around it.
///
/// ```kdl
/// svg-view src="classpath:/icons/logo.svg" alt="Goldberry"
/// svg-view src="art/harbour.svg" alt="The harbour at dusk" fit="cover"
/// svg-view decorative=#true "<svg viewBox='0 0 24 24'><path d='M5 12l5 5 9-10' stroke='currentColor'
/// fill='none'/></svg>"
/// ```
///
/// ```java
/// new SvgView(SvgSource.resource(App.class, "logo.svg"), "Goldberry")
/// SvgView.decorative(SvgSource.text(tick)).fit(Fit.CONTAIN)
/// ```
///
/// ## `currentColor` is the view's `color`
///
/// A document that draws in `currentColor` draws in the view's CSS `color`,
/// which it inherits from the text around it like any other node. So a
/// monochrome icon follows the theme, and a rule on `:hover` recolours it. A
/// document that names its own colours keeps them. This is where it differs from
/// an `image` of the same file, which draws `currentColor` black, as a browser
/// draws an SVG image.
///
/// ## Sharp at every size
///
/// The document is drawn for the device pixels its box covers, at any size and
/// display scale. With no `width` and `height` the box is the document's
/// natural size; with one, the other follows its shape; with both, [Fit]
/// decides, and `fill` lets the document's own `preserveAspectRatio` place it.
///
/// ## Loading, and a document that is not one
///
/// The bytes are read and checked on a virtual thread, and the box carries
/// `.loading` until they are. A file that cannot be read, or bytes that are not
/// an SVG document, carry `.error` and show a broken-image icon with the alt
/// text, as `image` does. Its look is [VectorStyles]' sheet, which an
/// application adds beside the controls' sheets.
///
/// Alt text is required unless the picture is decorative, for `image`'s reason.
///
/// Read more: [Images](https://goldberry.dev/docs/components/drawing.html#image).
///
/// @param source     where the document comes from
/// @param alt        what it shows, for a reader who cannot see it
/// @param decorative whether it shows nothing a reader needs
/// @param fit        how it fills a box of another shape
/// @param attributes `id` and `class`, exactly as on every other widget
@Markup("svg-view")
public record SvgView(SvgSource source, String alt, boolean decorative, Fit fit, Attributes attributes)
        implements Widget.Stateful, Attributed<SvgView> {

    /// The CSS type every view answers, loaded or not.
    public static final String CSS_TYPE = "svg-view";

    /// The name of the icon a failed load shows.
    static final String ERROR_ICON = "image-off";

    /// Its size, in logical pixels.
    static final double ERROR_ICON_SIZE = 20;

    public SvgView {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(alt, "alt");
        Objects.requireNonNull(fit, "fit");
        Objects.requireNonNull(attributes, "attributes");
        if (!decorative && alt.isBlank()) {
            throw new IllegalArgumentException(
                    "an svg-view needs alt text, or decorative=#true: a picture a reader is told is a figure and"
                            + " nothing more is worse than one they are not told about");
        }
    }

    /// A document described by `alt`, contained in its box.
    public SvgView(SvgSource source, String alt) {
        this(source, alt, false, Fit.CONTAIN, Attributes.NONE);
    }

    /// A document that is decoration, with no alt text and no semantics.
    public static SvgView decorative(SvgSource source) {
        return new SvgView(source, "", true, Fit.CONTAIN, Attributes.NONE);
    }

    /// This document filling its box another way.
    public SvgView fit(Fit value) {
        return new SvgView(source, alt, decorative, value, attributes);
    }

    @Override
    public SvgView withAttributes(Attributes value) {
        return new SvgView(source, alt, decorative, fit, value);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new SvgViewState();
    }

    /// The document's classes, and `loading` or `error` while the load stands
    /// there.
    static Set<String> classes(SvgLoad load, Attributes attributes) {
        var word = switch (load) {
            case SvgLoad.Loading() -> "loading";
            case SvgLoad.Failed(var _) -> "error";
            case SvgLoad.Ready(var _) -> null;
        };
        if (word == null) {
            return attributes.classes();
        }
        var classes = new HashSet<>(attributes.classes());
        classes.add(word);
        return Set.copyOf(classes);
    }

    /// What a failed view shows inside itself: the icon, and the alt text when
    /// there is some.
    static List<Widget> failureParts(SvgLoad load, String alt, @Nullable Icon icon) {
        if (!(load instanceof SvgLoad.Failed)) {
            return List.of();
        }
        var parts = new ArrayList<Widget>(2);
        if (icon != null) {
            parts.add(new SvgErrorIcon(icon));
        }
        if (!alt.isBlank()) {
            parts.add(new SvgAlt(alt));
        }
        return List.copyOf(parts);
    }

    /// Builds an `svg-view` from markup: `src=` a path (`classpath:` for a
    /// resource), or the document itself as the node's argument; exactly one.
    ///
    /// @throws IllegalArgumentException for both or neither, for children, for a
    ///                                  `fit` it does not know, and for a view
    ///                                  with neither `alt` nor `decorative`
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        if (!children.isEmpty()) {
            throw new IllegalArgumentException("an svg-view takes its document as src= or as its argument, not as"
                    + " children; it has " + children.size() + " child node(s)");
        }
        var src = node.stringProperty("src");
        var inline = Wiring.label(node);
        if ((src == null) == inline.isEmpty()) {
            throw new IllegalArgumentException(
                    "an svg-view takes exactly one of src= and the document written as its argument");
        }
        var loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = SvgView.class.getClassLoader();
        }
        return new SvgView(
                src != null ? SvgSource.parse(src, loader) : SvgSource.text(inline),
                Objects.requireNonNullElse(node.stringProperty("alt"), ""),
                node.booleanProperty("decorative"),
                Fit.named(node.stringProperty("fit")),
                Attributes.of(node));
    }
}
