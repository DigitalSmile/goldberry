package dev.goldberry.widgets.core.icon;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.assets.BundledAssets;
import dev.goldberry.icon.Icon;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Icons;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// One icon on its own, as big as the stylesheet makes its box and in the
/// box's `color`.
///
/// ```kdl
/// icon "cloud-upload"
/// icon "circle-alert" class="warn" name="Not signed in"
/// ```
///
/// ```java
/// new IconView("cloud-upload")
/// new IconView(Icon.bundled("circle-alert", 24)).withAttributes(Attributes.NONE.name("Not signed in"))
/// ```
///
/// ## The size is the stylesheet's
///
/// An [Icon] is a path built at one size, and inside a `button` that size is
/// the box's. Here it is the other way round: the box is sized by `width` and
/// `height`, 16 by default, and the outline is drawn under a scale that fits
/// it to the smaller of the two, centred. Lucide is drawn on a 24-unit grid
/// with a 2-unit stroke, and the stroke scales with the outline, so an 11px
/// icon is the same drawing as a 24px one, thinner. `width: 1em; height: 1em`
/// sizes one to the text beside it.
///
/// Nothing native is held. The outline is a value, turned into the
/// rasterizer's own path for the length of one paint, so a document reloaded
/// on every keystroke builds nothing that has to be closed.
///
/// ## A name that is not there draws nothing
///
/// Markup's argument is looked up in the application's [Icons] registry
/// first, so `icon "home"` draws whatever the application registered as
/// `home`, and then in the bundled set. A name neither has is an empty box of
/// the right size with the class `missing`, which a stylesheet may draw a
/// fallback on. A name typed into a document mid-edit should leave a gap
/// rather than take the window down, and a fallback is the application's
/// choice.
///
/// ## Decorative unless named
///
/// An icon beside a label says nothing the label does not. An icon that is
/// the only sign of something, a warning with no words, is given a `name=`,
/// and is then announced as a figure with that name.
///
/// Read more: [Canvas, images and QR codes](https://goldberry.dev/docs/components/drawing.html#icon).
///
/// @param icon       what to draw, or null for a name that found nothing
/// @param attributes `id`, `class` and the `name=` a reader is given
@Markup("icon")
public record IconView(@Nullable Icon icon, Attributes attributes) implements Widget.Stateless, Attributed<IconView> {

    /// The CSS type of the node this describes.
    static final String CSS_TYPE = "icon";

    /// The class a view carries when its name found nothing.
    public static final String MISSING = "missing";

    /// The size a bundled icon is built at, Lucide's own grid. Any size would
    /// do, since the box rescales it; this one makes the numbers the set's.
    static final double GRID = BundledAssets.ICON_SIZE;

    /// Written out so that the parameters taking null for a default can say so.
    public IconView(@Nullable Icon icon, @Nullable Attributes attributes) {
        this.icon = icon;
        this.attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// Any icon, drawn at the size of the box rather than its own.
    public IconView(Icon icon) {
        this(Objects.requireNonNull(icon, "icon"), Attributes.NONE);
    }

    /// The bundled icon of this name, or an empty `missing` box when the set
    /// has none.
    public IconView(String name) {
        this(bundled(name), Attributes.NONE);
    }

    /// Whether this view has nothing to draw.
    public boolean isMissing() {
        return icon == null;
    }

    @Override
    public IconView withAttributes(Attributes value) {
        return new IconView(icon, value);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public Widget build(BuildContext context) {
        return attributes.name() == null ? new IconBox(icon, attributes) : new IconFigure(icon, attributes);
    }

    /// The document's classes, plus `missing` when there is nothing to draw.
    static Set<String> classes(@Nullable Icon icon, Attributes attributes) {
        if (icon != null) {
            return attributes.classes();
        }
        var classes = new HashSet<>(attributes.classes());
        classes.add(MISSING);
        return Set.copyOf(classes);
    }

    /// The bundled icon `name` names, or null.
    private static @Nullable Icon bundled(String name) {
        return Icon.find(Objects.requireNonNull(name, "name"), GRID).orElse(null);
    }

    /// Builds an `icon` from markup.
    ///
    /// The argument is the icon's name: the application's registered icon of
    /// that name if there is one, else the bundled one. `name=` is what a
    /// reader is told, as it is on every node.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var name = Wiring.label(node);
        var icon = name.isEmpty()
                ? Optional.<Icon>empty()
                : wiring.icons().find(name).or(() -> Icon.find(name, GRID));
        return new IconView(icon.orElse(null), Attributes.of(node));
    }
}
