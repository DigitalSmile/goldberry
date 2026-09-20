package io.github.digitalsmile.goldberry.widgets.controls.spinner;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.value.Transform;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// Something is happening — `docs/core-widgets.md` §3's `spinner`, "a small
/// indeterminate activity indicator". The eighth control, and the smallest thing
/// in the catalog: no value, no state, no input, no children.
///
/// ```kdl
/// spinner
/// ```
///
/// ## It is a ring with a gap, drawn by the painter
///
/// The obvious implementation is an icon, and it is the wrong one twice over: an
/// [io.github.digitalsmile.goldberry.icon.Icon] owns native memory and a widget
/// is a value rebuilt every frame, so a spinner holding one would leak a ring per
/// reload — the argument [io.github.digitalsmile.goldberry.widgets.controls.button.Button]'s
/// borrowed icon makes — and
/// it would put the
/// toolkit's own spinner behind an asset an application has to register.
///
/// So it is a `Box.Mark`, like a checkbox's tick and a radio's dot, and the arc
/// behind it is **three cubics through the already-exported `bl_path_cubic_to`**.
/// No symbol was added to the export list, which is the rule
/// ADR-0064
/// set when a rounded corner turned out to be four of them.
///
/// Three quarters rather than a whole circle because **a spinning circle is a
/// circle**: the gap is the entire reason the rotation is visible.
///
/// ## The rotation is the frame clock, and nothing else
///
/// §3.1: "rotation 900ms `linear` loop". There is no controller, no start, no
/// stop and no state — the angle is `(now mod 900) / 900` of a turn, so a row of
/// spinners is in step by construction and one that unmounts leaves nothing
/// behind (ADR-0081).
///
/// Reduced motion replaces the rotation with §3.1's opacity pulse, which is the
/// stylesheet's: this widget simply stops turning.
///
/// ## It has a size, because a ring has a stroke
///
/// ```kdl
/// spinner size="large"
/// ```
///
/// [SpinnerSize] is a **value** rather than only a class, which is the line
/// `Message`'s kind draws and `badge`'s variant does not: a size decides the
/// weight of the arc, and §8's CSS subset has no property for the stroke of a
/// mark the painter draws. A 32px ring drawn with a 16px ring's 2px stroke is a
/// thin hoop, and no stylesheet could have said otherwise.
///
/// The **diameter** is still the stylesheet's. The size puts a class on the node,
/// `controls.css` gives that class a width and a height, and [#render] reads the
/// width the cascade actually resolved — so an application that writes
/// `#busy { width: 48px }` gets a stroke weighted for 48px, and the two numbers
/// cannot drift.
///
/// @param size       how big, and therefore how heavy the ring is
/// @param attributes the `id` and classes, plus the size's own class
@Markup("spinner")
public record Spinner(SpinnerSize size, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Spinner> {

    /// §3.1's "rotation **900ms** linear loop", in milliseconds.
    private static final double PERIOD = 900;

    public Spinner {
        size = size == null ? SpinnerSize.MEDIUM : size;
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A spinner of the default size with no attributes of its own.
    public Spinner() {
        this(SpinnerSize.MEDIUM, Attributes.NONE);
    }

    /// A spinner of the default size. The shape every caller had before there
    /// were sizes, kept so that adding one changed nothing that already worked.
    public Spinner(Attributes attributes) {
        this(SpinnerSize.MEDIUM, attributes);
    }

    /// A spinner of this size with no attributes of its own.
    public Spinner(SpinnerSize size) {
        this(size, Attributes.NONE);
    }

    /// The same spinner at another size.
    public Spinner sized(SpinnerSize value) {
        return new Spinner(value, attributes);
    }

    @Override
    public Spinner withAttributes(Attributes attributes) {
        return new Spinner(size, attributes);
    }

    @Override
    public String cssType() {
        return "spinner";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    /// The application's classes, and the size's own.
    ///
    /// Added here rather than asked of the caller, so that `spinner.large`
    /// selects without anybody writing it — `message.danger`'s arrangement.
    @Override
    public Set<String> classes() {
        var declared = attributes.classes();
        if (declared.isEmpty()) {
            return Set.of(size.cssClass());
        }
        var all = new java.util.LinkedHashSet<>(declared);
        all.add(size.cssClass());
        return Set.copyOf(all);
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    /// Always. A spinner that stopped asking for frames would be a picture of a
    /// spinner, and §1.7's idle loop would leave it there — which is exactly what
    /// an application should be able to see when it forgets to unmount one.
    @Override
    public boolean isAnimating() {
        return true;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of()
                .style(style)
                .mark(new Box.Mark(Box.Mark.Kind.ARC, style.color(), thicknessAt(style)))
                .transform(angleAt(context));
    }

    /// The ring's stroke for the width the cascade resolved.
    ///
    /// Read off the **style** rather than off [SpinnerSize], so a stylesheet
    /// that overrides the diameter gets a stroke to match it and the two numbers
    /// cannot disagree. The size's own diameter is the fallback for a width that
    /// is not a length a ring can be drawn from — `auto`, or a percentage of a
    /// parent this widget knows nothing about.
    private double thicknessAt(ComputedStyle style) {
        var diameter =
                style.width() instanceof Length.Points points && points.value() > 0 ? points.value() : size.diameter();
        return SpinnerSize.thicknessFor(diameter);
    }

    /// A rotation about the box's centre, which is `transform-origin`'s default
    /// and the only origin a ring has any use for.
    private Transform angleAt(Context context) {
        if (context.reducedMotion()) {
            return Transform.NONE;
        }
        return Transform.of(new Transform.Function.Rotate(turnAt(context.nowMillis()) * 2 * Math.PI));
    }

    /// How far round the loop `now` is, `0..1` — see `ProgressFill#phaseAt`.
    static double turnAt(double now) {
        var phase = (now % PERIOD) / PERIOD;
        return phase < 0 ? phase + 1 : phase;
    }

    /// Builds a `spinner` from markup.
    ///
    /// `size` is the only attribute of its own a spinner has: it has no value,
    /// no state and nothing to say. It still takes an id and classes, because
    /// everything CSS-selectable does (§11).
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Spinner(SpinnerSize.of(node.stringProperty("size")), Attributes.of(node));
    }
}
