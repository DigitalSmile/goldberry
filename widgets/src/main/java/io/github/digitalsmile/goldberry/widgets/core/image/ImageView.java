package io.github.digitalsmile.goldberry.widgets.core.image;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// A picture — `docs/core-widgets.md` §1's `image`.
///
/// ```kdl
/// image src="photos/harbour.jpg" alt="The harbour at dusk" fit="cover"
/// image srcset="classpath:logo.png 1x, classpath:logo@2x.png 2x" alt="Goldberry"
/// image src="classpath:divider.png" decorative=#true
/// ```
///
/// ```java
/// new ImageView(ImageSource.file(path), "The harbour at dusk").fit(Fit.COVER)
/// new ImageView(ImageSource.supplied("avatar:" + id, () -> fetchAvatar(id)), name)
/// ```
///
/// ## Loading is the widget's, and it does not block a frame
///
/// Reading a file and decoding a JPEG is tens of milliseconds, and a build runs
/// every frame. So a view asks an [ImageLoader] and draws what it has: nothing,
/// with `image.loading`, until the pixels arrive on a virtual thread; the
/// picture after; and `image.error`, with Lucide's `image-off` and the alt text,
/// when they cannot. The shared loader keeps one decode per source for the
/// process, so ten thumbnails of one file decode once (ADR-0358).
///
/// ## The size is the image's until a stylesheet says otherwise
///
/// With no `width` and `height` the box is the picture's natural size — its
/// pixels over its variant's scale — and a `max-width` shrinks it in proportion.
/// With one of them, the other follows the picture's shape. With both, [Fit]
/// decides how the picture fills the box. See [ImagePaint] for the arithmetic.
///
/// ## Alt text is required, unless it is decoration
///
/// §1: "alt text (required attribute for non-decorative use)". An image with
/// neither is refused when it is built, because a picture a screen reader calls
/// "figure" and nothing else is worse than one it skips. `decorative` is the
/// other honest answer, and it removes the node from the semantics entirely.
///
/// ## Not built
///
/// §1 routes `image/svg+xml` to `goldberry-vector`, which does not exist; an SVG
/// fails to decode and shows the error state.
///
/// @param variants   the rasters, one per display scale; at least one
/// @param alt        what the picture shows, for a reader who cannot see it
/// @param decorative whether it shows nothing a reader needs
/// @param fit        how the picture fills a box of another shape
/// @param loader     where the pixels come from
/// @param attributes `id` and `class`, exactly as on every other widget
@Markup("image")
public record ImageView(
        List<Variant> variants, String alt, boolean decorative, Fit fit, ImageLoader loader, Attributes attributes)
        implements Widget.Stateful, Attributed<ImageView> {

    /// The CSS type every image answers, loaded or not.
    public static final String CSS_TYPE = "image";

    /// The name of the icon a failed load shows.
    static final String ERROR_ICON = "image-off";

    /// Its size, in logical pixels.
    static final double ERROR_ICON_SIZE = 20;

    public ImageView {
        variants = List.copyOf(Objects.requireNonNull(variants, "variants"));
        if (variants.isEmpty()) {
            throw new IllegalArgumentException("an image needs a source");
        }
        if (!decorative && alt.isBlank()) {
            throw new IllegalArgumentException(
                    "an image needs alt text, or decorative=#true: a picture a reader is told is a figure and"
                            + " nothing more is worse than one they are not told about (§1)");
        }
        Objects.requireNonNull(alt, "alt");
        Objects.requireNonNull(fit, "fit");
        Objects.requireNonNull(loader, "loader");
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A picture from one source, described by `alt`.
    public ImageView(ImageSource source, String alt) {
        this(List.of(new Variant(1, source)), alt, false, Fit.CONTAIN, ImageLoader.shared(), Attributes.NONE);
    }

    /// A picture that is decoration, with no alt text and no semantics.
    public static ImageView decorative(ImageSource source) {
        return new ImageView(
                List.of(new Variant(1, source)), "", true, Fit.CONTAIN, ImageLoader.shared(), Attributes.NONE);
    }

    /// This picture filling its box another way.
    public ImageView fit(Fit value) {
        return new ImageView(variants, alt, decorative, value, loader, attributes);
    }

    /// This picture with one more raster, for displays at `scale`.
    public ImageView variant(double scale, ImageSource source) {
        var next = new ArrayList<>(variants);
        next.removeIf(variant -> variant.scale() == scale);
        next.add(new Variant(scale, source));
        return new ImageView(next, alt, decorative, fit, loader, attributes);
    }

    /// This picture loaded through `value` rather than the shared loader.
    public ImageView loader(ImageLoader value) {
        return new ImageView(variants, alt, decorative, fit, value, attributes);
    }

    @Override
    public ImageView withAttributes(Attributes value) {
        return new ImageView(variants, alt, decorative, fit, loader, value);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new ImageState();
    }

    /// The document's classes, and `loading` or `error` while the load stands
    /// there.
    static Set<String> classes(ImageLoad load, Attributes attributes) {
        var word =
                switch (load) {
                    case ImageLoad.Loading() -> "loading";
                    case ImageLoad.Failed(var _) -> "error";
                    case ImageLoad.Ready(var _, var _) -> null;
                };
        if (word == null) {
            return attributes.classes();
        }
        var classes = new HashSet<>(attributes.classes());
        classes.add(word);
        return Set.copyOf(classes);
    }

    /// What a failed image shows inside itself: the icon, and the alt text when
    /// there is some.
    static List<Widget> failureParts(ImageLoad load, String alt, @Nullable Icon icon) {
        if (!(load instanceof ImageLoad.Failed)) {
            return List.of();
        }
        var parts = new ArrayList<Widget>(2);
        if (icon != null) {
            parts.add(new ImageErrorIcon(icon));
        }
        if (!alt.isBlank()) {
            parts.add(new ImageAlt(alt));
        }
        return List.copyOf(parts);
    }

    /// Builds an `image` from markup.
    ///
    /// `src` is one path; `srcset` is several, with `Nx` scales. A path that
    /// starts `classpath:` is a resource on the application's class loader.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = ImageView.class.getClassLoader();
        }
        var src = node.stringProperty("src");
        var srcset = node.stringProperty("srcset");
        if ((src == null) == (srcset == null)) {
            throw new IllegalArgumentException("an image takes exactly one of src= and srcset=");
        }
        var variants =
                src != null ? List.of(new Variant(1, ImageSource.parse(src, loader))) : Variant.parse(srcset, loader);
        return new ImageView(
                variants,
                Objects.requireNonNullElse(node.stringProperty("alt"), ""),
                node.booleanProperty("decorative"),
                Fit.named(node.stringProperty("fit")),
                ImageLoader.shared(),
                Attributes.of(node));
    }
}
