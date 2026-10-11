package dev.goldberry.css.property;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import dev.goldberry.css.Border;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Corners;
import dev.goldberry.css.Decoration;
import dev.goldberry.css.Typography;
import dev.goldberry.css.background.Background;
import dev.goldberry.css.background.BackgroundParser;
import dev.goldberry.css.cascade.KeyframeAnimations;
import dev.goldberry.css.cascade.Transitions;
import dev.goldberry.css.image.BorderImage;
import dev.goldberry.layout.Insets;
import dev.goldberry.layout.Length;
import dev.goldberry.layout.Limits;

/// Every longhand the engine stores, grouped as `ComputedStyle`'s property
/// switch groups them.
///
/// Each copier is written against the component the switch's arm writes, so a
/// longhand copies exactly what a declaration of it would have set.
final class Longhands {

    private Longhands() {}

    /// The table's longhands, in the switch's order.
    static List<Longhand> all() {
        var all = new ArrayList<Longhand>();
        layout(all);
        edges(all, "padding-", ComputedStyle::padding, ComputedStyle::padding);
        edges(all, "margin-", ComputedStyle::margin, ComputedStyle::margin);
        edges(all, "", ComputedStyle::inset, ComputedStyle::inset);
        text(all);
        background(all);
        border(all);
        borderImage(all);
        typography(all);
        motion(all);
        return List.copyOf(all);
    }

    private static void layout(List<Longhand> all) {
        all.add(own("flex-direction", (from, into) -> into.direction(from.direction())));
        all.add(own("justify-content", (from, into) -> into.justifyContent(from.justifyContent())));
        all.add(own("align-items", (from, into) -> into.alignItems(from.alignItems())));
        all.add(own("align-self", (from, into) -> into.alignSelf(from.alignSelf())));
        all.add(own("align-content", (from, into) -> into.alignContent(from.alignContent())));
        all.add(own("flex-wrap", (from, into) -> into.wrap(from.wrap())));
        all.add(own("width", (from, into) -> into.width(from.width())));
        all.add(own("height", (from, into) -> into.height(from.height())));
        all.add(limit("min-width", Limits::minWidth, Limits::minWidth));
        all.add(limit("max-width", Limits::maxWidth, Limits::maxWidth));
        all.add(limit("min-height", Limits::minHeight, Limits::minHeight));
        all.add(limit("max-height", Limits::maxHeight, Limits::maxHeight));
        all.add(own("row-gap", (from, into) -> into.rowGap(from.rowGap())));
        all.add(own("column-gap", (from, into) -> into.columnGap(from.columnGap())));
        all.add(own("flex-grow", (from, into) -> into.flexGrow(from.flexGrow())));
        all.add(own("flex-shrink", (from, into) -> into.flexShrink(from.flexShrink())));
        all.add(own("flex-basis", (from, into) -> into.flexBasis(from.flexBasis())));
        all.add(own("position", (from, into) -> into.position(from.position())));
        all.add(own("overflow-x", (from, into) -> into.overflowX(from.overflowX())));
        all.add(own("overflow-y", (from, into) -> into.overflowY(from.overflowY())));
        all.add(own(
                "display",
                (from, into) ->
                        into.presence(into.presence().display(from.presence().display()))));
        all.add(own(
                "aspect-ratio",
                (from, into) -> into.presence(
                        into.presence().aspectRatio(from.presence().aspectRatio()))));
        all.add(own(
                "z-index",
                (from, into) ->
                        into.presence(into.presence().zIndex(from.presence().zIndex()))));
        all.add(own(
                "filter",
                (from, into) -> into.presence(into.presence()
                        .compositing(into.presence()
                                .compositing()
                                .filter(from.presence().compositing().filter())))));
        all.add(own(
                "clip-path",
                (from, into) -> into.presence(into.presence()
                        .compositing(into.presence()
                                .compositing()
                                .clip(from.presence().compositing().clip())))));
        all.add(own(
                "mix-blend-mode",
                (from, into) -> into.presence(into.presence()
                        .compositing(into.presence()
                                .compositing()
                                .blend(from.presence().compositing().blend())))));
        all.add(own(
                "isolation",
                (from, into) -> into.presence(into.presence()
                        .compositing(into.presence()
                                .compositing()
                                .isolate(from.presence().compositing().isolate())))));
        all.add(own(
                "container-type",
                (from, into) -> into.presence(into.presence()
                        .containment(into.presence()
                                .containment()
                                .type(from.presence().containment().type())))));
        all.add(own(
                "container-name",
                (from, into) -> into.presence(into.presence()
                        .containment(into.presence()
                                .containment()
                                .names(from.presence().containment().names())))));
        all.add(own(
                "box-sizing",
                (from, into) ->
                        into.presence(into.presence().boxSizing(from.presence().boxSizing()))));
        all.add(own(
                "order",
                (from, into) ->
                        into.presence(into.presence().order(from.presence().order()))));
        all.add(inherited(
                "direction",
                (from, into) ->
                        into.presence(into.presence().direction(from.presence().direction()))));
        all.add(own(
                "unicode-bidi",
                (from, into) -> into.presence(
                        into.presence().unicodeBidi(from.presence().unicodeBidi()))));
        all.add(inherited(
                "visibility",
                (from, into) ->
                        into.presence(into.presence().visibility(from.presence().visibility()))));
        all.add(inherited(
                "pointer-events",
                (from, into) -> into.presence(
                        into.presence().pointerEvents(from.presence().pointerEvents()))));
        all.add(inherited(
                "user-select",
                (from, into) ->
                        into.presence(into.presence().userSelect(from.presence().userSelect()))));
    }

    /// The four edges of one of the box's [Insets]: `padding-top` and the rest,
    /// or `top`, `right`, `bottom` and `left` for the inset, whose prefix is
    /// empty.
    private static void edges(
            List<Longhand> all,
            String prefix,
            Function<ComputedStyle, Insets> get,
            BiFunction<ComputedStyle, Insets, ComputedStyle> set) {
        for (var side : Border.Side.values()) {
            all.add(own(prefix + sideName(side), (from, into) -> {
                var source = get.apply(from);
                var target = get.apply(into);
                return set.apply(into, withEdge(target, side, edge(source, side)));
            }));
        }
    }

    private static void text(List<Longhand> all) {
        all.add(inherited("white-space", (from, into) -> into.whiteSpace(from.whiteSpace())));
        all.add(inherited("overflow-wrap", (from, into) -> into.overflowWrap(from.overflowWrap())));
        all.add(inherited("word-break", (from, into) -> into.wordBreak(from.wordBreak())));
        all.add(own("text-overflow", (from, into) -> into.textOverflow(from.textOverflow())));
        all.add(own("line-clamp", (from, into) -> into.lineClamp(from.lineClamp())));
        all.add(inherited("text-align", (from, into) -> into.textAlign(from.textAlign())));
        all.add(inherited("text-decoration-line", (from, into) -> into.textDecoration(from.textDecoration())));
        all.add(inherited("color", (from, into) -> into.color(from.color())));
        all.add(own("opacity", (from, into) -> into.opacity(from.opacity())));
    }

    private static void background(List<Longhand> all) {
        all.add(fill("background-color", (from, into) -> into.colour(from.colour())));
        all.add(fill("background-image", (from, into) -> into.layers(from.layers())));
        all.add(fill(
                "background-position-x",
                (from, into) -> into.positions(BackgroundParser.withAxis(into.positions(), from.positions(), true))));
        all.add(fill(
                "background-position-y",
                (from, into) -> into.positions(BackgroundParser.withAxis(into.positions(), from.positions(), false))));
        all.add(fill("background-size", (from, into) -> into.sizes(from.sizes())));
        all.add(fill("background-repeat", (from, into) -> into.repeats(from.repeats())));
        all.add(fill("background-origin", (from, into) -> into.origins(from.origins())));
        all.add(fill("background-clip", (from, into) -> into.clips(from.clips())));
    }

    private static void border(List<Longhand> all) {
        all.add(decoration(
                "border-top-left-radius",
                (from, into) -> into.corners(new Corners(
                        from.corners().topLeftRadius(),
                        into.corners().topRightRadius(),
                        into.corners().bottomRightRadius(),
                        into.corners().bottomLeftRadius()))));
        all.add(decoration(
                "border-top-right-radius",
                (from, into) -> into.corners(new Corners(
                        into.corners().topLeftRadius(),
                        from.corners().topRightRadius(),
                        into.corners().bottomRightRadius(),
                        into.corners().bottomLeftRadius()))));
        all.add(decoration(
                "border-bottom-right-radius",
                (from, into) -> into.corners(new Corners(
                        into.corners().topLeftRadius(),
                        into.corners().topRightRadius(),
                        from.corners().bottomRightRadius(),
                        into.corners().bottomLeftRadius()))));
        all.add(decoration(
                "border-bottom-left-radius",
                (from, into) -> into.corners(new Corners(
                        into.corners().topLeftRadius(),
                        into.corners().topRightRadius(),
                        into.corners().bottomRightRadius(),
                        from.corners().bottomLeftRadius()))));
        for (var side : Border.Side.values()) {
            var name = "border-" + sideName(side);
            all.add(line(name + "-width", side, true, (from, into) -> into.width(from.width())));
            all.add(line(name + "-color", side, true, (from, into) -> into.argb(from.argb())));
            all.add(line(name + "-style", side, true, (from, into) -> into.style(from.style())));
        }
        all.add(decoration("outline-width", (from, into) -> into.outlineWidth(from.outlineWidth())));
        all.add(decoration("outline-color", (from, into) -> into.outlineColor(from.outlineColor())));
        all.add(decoration("outline-offset", (from, into) -> into.outlineOffset(from.outlineOffset())));
        all.add(decoration("outline-style", (from, into) -> into.outlineStyle(from.outlineStyle())));
        all.add(decoration("box-shadow", (from, into) -> into.shadows(from.shadows())));
    }

    private static void borderImage(List<Longhand> all) {
        all.add(borderImage("border-image-source", (from, into) -> into.source(from.source())));
        all.add(borderImage("border-image-slice", (from, into) -> into.slice(from.slice())));
        all.add(borderImage("border-image-width", (from, into) -> into.widths(from.widths())));
        all.add(borderImage("border-image-outset", (from, into) -> into.outsets(from.outsets())));
        all.add(borderImage("border-image-repeat", (from, into) -> into.repeat(from.across(), from.down())));
    }

    private static void typography(List<Longhand> all) {
        all.add(typography("font-family", (from, into) -> into.family(from.family())));
        all.add(typography("font-size", (from, into) -> into.size(from.size())));
        all.add(typography("font-weight", (from, into) -> into.weight(from.weight())));
        all.add(typography("font-style", (from, into) -> into.style(from.style())));
        all.add(typography("line-height", (from, into) -> into.lineHeight(from.lineHeight())));
        all.add(typography(
                "letter-spacing",
                (from, into) -> into.lettering(
                        into.lettering().letterSpacing(from.lettering().letterSpacing()))));
        all.add(typography(
                "word-spacing",
                (from, into) -> into.lettering(
                        into.lettering().wordSpacing(from.lettering().wordSpacing()))));
        all.add(typography(
                "font-stretch",
                (from, into) ->
                        into.variation(into.variation().stretch(from.variation().stretch()))));
        all.add(typography(
                "font-variation-settings",
                (from, into) -> into.variation(
                        into.variation().settings(from.variation().settings()))));
        all.add(typography(
                "text-justify",
                (from, into) ->
                        into.lettering(into.lettering().justify(from.lettering().justify()))));
        all.add(typography(
                "text-transform",
                (from, into) -> into.lettering(
                        into.lettering().transform(from.lettering().transform()))));
        all.add(typography(
                "text-shadow",
                (from, into) ->
                        into.lettering(into.lettering().shadows(from.lettering().shadows()))));
    }

    private static void motion(List<Longhand> all) {
        all.add(animation("animation-name", (from, into) -> into.names(from.names())));
        all.add(animation("animation-duration", (from, into) -> into.durations(from.durations())));
        all.add(animation("animation-timing-function", (from, into) -> into.easings(from.easings())));
        all.add(animation("animation-delay", (from, into) -> into.delays(from.delays())));
        all.add(animation("animation-iteration-count", (from, into) -> into.iterations(from.iterations())));
        all.add(animation("animation-direction", (from, into) -> into.directions(from.directions())));
        all.add(animation("animation-fill-mode", (from, into) -> into.fillModes(from.fillModes())));
        all.add(animation("animation-play-state", (from, into) -> into.playStates(from.playStates())));
        all.add(transition("transition-property", (from, into) -> into.properties(from.properties())));
        all.add(transition("transition-duration", (from, into) -> into.durations(from.durations())));
        all.add(transition("transition-timing-function", (from, into) -> into.easings(from.easings())));
        all.add(transition("transition-delay", (from, into) -> into.delays(from.delays())));
        all.add(own(
                "transform",
                (from, into) -> into.transform(
                        into.transform().functions(from.transform().functions()))));
        all.add(own(
                "translate",
                (from, into) -> into.transform(into.transform()
                        .individual(into.transform()
                                .individual()
                                .translate(from.transform().individual().translate())))));
        all.add(own(
                "rotate",
                (from, into) -> into.transform(into.transform()
                        .individual(into.transform()
                                .individual()
                                .rotate(from.transform().individual().rotate())))));
        all.add(own(
                "scale",
                (from, into) -> into.transform(into.transform()
                        .individual(into.transform()
                                .individual()
                                .scale(from.transform().individual().scale())))));
        all.add(own(
                "transform-box",
                (from, into) ->
                        into.transform(into.transform().box(from.transform().box()))));
        all.add(own(
                "transform-origin",
                (from, into) ->
                        into.transform(into.transform().origin(from.transform().origin()))));
        all.add(own("cursor", (from, into) -> into.cursor(from.cursor())));
    }

    // --- the shapes a copier comes in ------------------------------------------

    private static Longhand own(String name, Longhand.Copier copier) {
        return new Longhand(name, false, true, copier);
    }

    private static Longhand inherited(String name, Longhand.Copier copier) {
        return new Longhand(name, true, true, copier);
    }

    private static Longhand limit(String name, Function<Limits, Length> get, BiFunction<Limits, Length, Limits> set) {
        return own(name, (from, into) -> into.limits(set.apply(into.limits(), get.apply(from.limits()))));
    }

    private static Longhand fill(String name, BiFunction<Background, Background, Background> copy) {
        return own(name, (from, into) -> into.fill(copy.apply(from.fill(), into.fill())));
    }

    private static Longhand decoration(String name, BiFunction<Decoration, Decoration, Decoration> copy) {
        return own(name, (from, into) -> into.decoration(copy.apply(from.decoration(), into.decoration())));
    }

    private static Longhand line(
            String name, Border.Side side, boolean readable, BiFunction<Border.Line, Border.Line, Border.Line> copy) {
        return new Longhand(name, false, readable, (from, into) -> {
            var border = into.decoration().border();
            var line = copy.apply(from.decoration().border().side(side), border.side(side));
            return into.decoration(into.decoration().border(border.side(side, line)));
        });
    }

    private static Longhand borderImage(String name, BiFunction<BorderImage, BorderImage, BorderImage> copy) {
        return decoration(name, (from, into) -> into.borderImage(copy.apply(from.borderImage(), into.borderImage())));
    }

    /// One of the four lists `transition` writes, copied, and the timings
    /// worked out again from the lists that result.
    private static Longhand transition(
            String name, BiFunction<Transitions.Lists, Transitions.Lists, Transitions.Lists> copy) {
        return own(
                name,
                (from, into) -> into.transitions(Transitions.of(copy.apply(
                        from.transitions().lists(), into.transitions().lists()))));
    }

    private static Longhand typography(String name, BiFunction<Typography, Typography, Typography> copy) {
        return inherited(name, (from, into) -> into.typography(copy.apply(from.typography(), into.typography())));
    }

    private static Longhand animation(
            String name, BiFunction<KeyframeAnimations, KeyframeAnimations, KeyframeAnimations> copy) {
        return own(name, (from, into) -> into.animations(copy.apply(from.animations(), into.animations())));
    }

    // --- edges --------------------------------------------------------------

    static String sideName(Border.Side side) {
        return switch (side) {
            case TOP -> "top";
            case RIGHT -> "right";
            case BOTTOM -> "bottom";
            case LEFT -> "left";
        };
    }

    private static Length edge(Insets insets, Border.Side side) {
        return switch (side) {
            case TOP -> insets.top();
            case RIGHT -> insets.right();
            case BOTTOM -> insets.bottom();
            case LEFT -> insets.left();
        };
    }

    private static Insets withEdge(Insets insets, Border.Side side, Length value) {
        return switch (side) {
            case TOP -> new Insets(value, insets.right(), insets.bottom(), insets.left());
            case RIGHT -> new Insets(insets.top(), value, insets.bottom(), insets.left());
            case BOTTOM -> new Insets(insets.top(), insets.right(), value, insets.left());
            case LEFT -> new Insets(insets.top(), insets.right(), insets.bottom(), value);
        };
    }
}
