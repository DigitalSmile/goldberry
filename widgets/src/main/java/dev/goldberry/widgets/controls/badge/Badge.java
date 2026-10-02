package dev.goldberry.widgets.controls.badge;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.bind.Observable;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.attr.Bindable;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;
import dev.goldberry.widgets.text.Text;

/// A count or a status in a small pill: `3`, `offline`, `passing`.
///
/// ```kdl
/// badge "3"
/// badge class="danger" "offline"
/// badge class="success" bind="build.state" "passing"
/// ```
///
/// In Java, `new Badge("3")` or `Badge.of("passing", source)`. A badge is not a
/// control: it is not focusable, holds no value of its own, has no keyboard and
/// never animates. It shows its argument, or the bound value when `bind=` names
/// one, with the argument as the fallback until the binding answers.
///
/// The variants — `accent`, `danger`, `warning`, `success`, `info` — are
/// classes rather than an enum, because KDL spells a variant `class="danger"`
/// and an enum would be a second vocabulary only Java could use. A filled badge
/// in a semantic hue cannot take the theme's text colour and stay legible, so
/// each variant pins its own foreground token, and the two hues with no legible
/// text at either end of the palette ship as a derived, darker fill.
///
/// Read more: [Buttons, badges and chips](https://goldberry.dev/docs/components/buttons.html#badge).
///
/// @param text       what the badge says; a count is already a string, because
///                   formatting one is the application's business and not a
///                   pattern this widget would have to validate
/// @param source     the bound value, or null for a literal. An [Observable]
///                   rather than a property, because a badge reports nothing
///                   back
/// @param attributes `id` and `class`, exactly as on every other widget
@Markup("badge")
public record Badge(String text, @Nullable Observable<?> source, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Badge>, Bindable<Badge> {

    /// Written out so that the parameters taking null for a default can say so.
    public Badge(String text, @Nullable Observable<?> source, @Nullable Attributes attributes) {
        Objects.requireNonNull(text, "text");
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.text = text;
        this.source = source;
        this.attributes = attributes;
    }

    /// A chip with a literal, which is what a status wants.
    public Badge(String text) {
        this(text, null, Attributes.NONE);
    }

    /// A chip that follows a property. The Java spelling of `bind=`.
    ///
    /// The literal stays as the fallback rather than being refused, which is the
    /// rule [Text] set: a path nothing answers yet should show a designer
    /// something rather than nothing.
    public static Badge of(String fallback, Observable<?> source) {
        return new Badge(fallback, Objects.requireNonNull(source, "source"), Attributes.NONE);
    }

    /// What the chip says right now: the bound value, or the literal.
    ///
    /// `String.valueOf` rather than a `Number` check, because unlike a slider's
    /// binding this one has no numeric meaning to fall back to — a badge shows a
    /// count **or a status**, and a status is whatever the model calls it.
    public String resolved() {
        return source == null ? text : String.valueOf(source.get());
    }

    @Override
    public Badge bound(Observable<?> source) {
        return new Badge(text, source, attributes);
    }

    @Override
    public Badge withAttributes(Attributes attributes) {
        return new Badge(text, source, attributes);
    }

    @Override
    public @Nullable Observable<?> binding() {
        return source;
    }

    @Override
    public String cssType() {
        return "badge";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        // The text is a **child box** and not text on the chip's own box, which
        // is [dev.goldberry.widgets.controls.button.Button]'s split and here it buys the one thing a
        // pill needs: a box
        // that measures its own text is a measured leaf, and Yoga sizes a
        // measured node to its content, so the chip could not be given the
        // 20px height that makes `border-radius: 10px` a full radius. With the
        // text as a child, `align-items: center` centres it in a height the
        // stylesheet pins -- and it is still **one styled element**, because the
        // paragraph is built from the chip's own `style` rather than from a
        // second widget's (`SliderValue` takes the other branch, and says why).
        return Box.of().style(style).children(Box.text(context.paragraph(style, resolved()), style.color()));
    }

    /// Builds a `badge` from markup.
    ///
    /// Not a control: no action, no state, nothing to resolve against a
    /// registry. A count is the archetypal bound value, so
    /// `bind` is the one wiring it takes, and the literal argument stays as the
    /// fallback the way `text`'s does.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Badge(Wiring.label(node), wiring.bound(node), Attributes.of(node));
    }
}
