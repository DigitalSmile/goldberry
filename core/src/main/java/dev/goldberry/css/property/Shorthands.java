package dev.goldberry.css.property;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.css.Border;

/// Every shorthand the engine reads, with the longhands it writes, and the
/// aliases.
///
/// A shorthand's list is what a declaration of it *writes*, which is not always
/// CSS's list: `outline` here leaves `outline-offset` alone, as the engine
/// does, and `border` does not reset `border-image`. The tests apply each
/// shorthand and check that it changed nothing outside its list.
final class Shorthands {

    private Shorthands() {}

    static List<Shorthand> all() {
        var all = new ArrayList<Shorthand>();
        all.add(new Shorthand("padding", sides("padding-", "")));
        all.add(new Shorthand("margin", sides("margin-", "")));
        all.add(new Shorthand("inset", sides("", "")));
        all.add(new Shorthand("gap", List.of("row-gap", "column-gap")));
        all.add(new Shorthand("flex", List.of("flex-grow", "flex-shrink", "flex-basis")));
        all.add(new Shorthand("overflow", List.of("overflow-x", "overflow-y")));
        all.add(new Shorthand("place-content", List.of("align-content", "justify-content")));
        all.add(new Shorthand("place-items", List.of("align-items", "justify-items")));
        all.add(new Shorthand("place-self", List.of("align-self", "justify-self")));
        all.add(new Shorthand("text-decoration", List.of("text-decoration-line")));
        all.add(new Shorthand(
                "background",
                List.of(
                        "background-color",
                        "background-image",
                        "background-position-x",
                        "background-position-y",
                        "background-size",
                        "background-repeat",
                        "background-origin",
                        "background-clip")));
        all.add(new Shorthand("background-position", List.of("background-position-x", "background-position-y")));
        all.add(new Shorthand("border-width", sides("border-", "-width")));
        all.add(new Shorthand("border-style", sides("border-", "-style")));
        all.add(new Shorthand(
                "border-radius",
                List.of(
                        "border-top-left-radius",
                        "border-top-right-radius",
                        "border-bottom-right-radius",
                        "border-bottom-left-radius")));
        all.add(new Shorthand("border-color", sides("border-", "-color")));
        for (var side : Border.Side.values()) {
            var name = "border-" + Longhands.sideName(side);
            all.add(new Shorthand(name, List.of(name + "-width", name + "-color", name + "-style")));
        }
        all.add(new Shorthand("border", sides("border-", "")));
        all.add(new Shorthand("outline", List.of("outline-width", "outline-color", "outline-style")));
        all.add(new Shorthand(
                "border-image",
                List.of(
                        "border-image-source",
                        "border-image-slice",
                        "border-image-width",
                        "border-image-outset",
                        "border-image-repeat")));
        all.add(new Shorthand(
                "animation",
                List.of(
                        "animation-name",
                        "animation-duration",
                        "animation-timing-function",
                        "animation-delay",
                        "animation-iteration-count",
                        "animation-direction",
                        "animation-fill-mode",
                        "animation-play-state")));
        all.add(new Shorthand(
                "transition",
                List.of(
                        "transition-property",
                        "transition-duration",
                        "transition-timing-function",
                        "transition-delay")));
        all.add(new Shorthand("container", List.of("container-name", "container-type")));
        // `font` resets the stretch and the variation settings, as CSS's does:
        // it names a face from scratch.
        all.add(new Shorthand(
                "font",
                List.of(
                        "font-style",
                        "font-weight",
                        "font-size",
                        "line-height",
                        "font-family",
                        "font-stretch",
                        "font-variation-settings")));
        // Every longhand, which is what CSS's `all` resets. It takes only the
        // global keywords.
        all.add(new Shorthand(
                "all", Longhands.all().stream().map(Longhand::name).toList()));
        return List.copyOf(all);
    }

    /// The properties read and changing nothing: CSS ignores them on a flex
    /// item, which every box is.
    static List<Inert> inert() {
        return List.of(new Inert("justify-items"), new Inert("justify-self"));
    }

    static List<Alias> aliases() {
        return List.of(
                new Alias("word-wrap", "overflow-wrap"),
                new Alias("-webkit-line-clamp", "line-clamp"),
                new Alias("-webkit-user-select", "user-select"),
                new Alias("font-width", "font-stretch"),
                new Alias("-webkit-filter", "filter"));
    }

    /// The four sides' names between `prefix` and `suffix`, top first.
    private static List<String> sides(String prefix, String suffix) {
        var names = new ArrayList<String>(4);
        for (var side : Border.Side.values()) {
            names.add(prefix + Longhands.sideName(side) + suffix);
        }
        return names;
    }
}
