package dev.goldberry.css;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.container.ContainerQuery;
import dev.goldberry.css.media.MediaCondition;
import dev.goldberry.css.select.Selector;

/// A selector list and the declarations it applies.
///
/// `.a, .b { color: red }` is one rule with two selectors, not two rules. The
/// cascade treats each selector separately — the one that matches with the
/// highest specificity is the one that counts — but they share a declaration
/// list, and duplicating it would mean two rules whose `order` differs when it
/// should not.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#selectors).
///
/// @param selectors    the selector list, in source order; never empty
/// @param declarations in source order, which is also the order the cascade
///                     resolves ties in
/// @param order        the rule's position in its stylesheet, counted across
///                     nested at-rules
/// @param starting     whether it was written inside `@starting-style`, which
///                     makes it the style an element transitions *from* on its
///                     first frame rather than one it has
/// @param media        when it applies: [MediaCondition#ALWAYS] outside any
///                     `@media` block, and the block's condition inside one
/// @param layer        the `@layer` it was written in, `a.b` for one nested in
///                     another; null for a rule in none, which is most of them
public record StyleRule(
        List<Selector> selectors,
        List<Declaration> declarations,
        int order,
        boolean starting,
        MediaCondition media,
        @Nullable String layer,
        List<ContainerQuery> containers) {

    public StyleRule {
        selectors = List.copyOf(Objects.requireNonNull(selectors, "selectors"));
        declarations = List.copyOf(Objects.requireNonNull(declarations, "declarations"));
        Objects.requireNonNull(media, "media");
        containers = List.copyOf(Objects.requireNonNull(containers, "containers"));
        if (selectors.isEmpty()) {
            throw new IllegalArgumentException("a rule needs at least one selector");
        }
        if (layer != null && layer.isBlank()) {
            throw new IllegalArgumentException("a layer has a name");
        }
    }

    /// A rule in no `@container` block.
    public StyleRule(
            List<Selector> selectors,
            List<Declaration> declarations,
            int order,
            boolean starting,
            MediaCondition media,
            @Nullable String layer) {
        this(selectors, declarations, order, starting, media, layer, List.of());
    }

    /// Whether it sits inside an `@container` block, so the cascade has to ask
    /// about the element's containers.
    public boolean isContained() {
        return !containers.isEmpty();
    }

    /// A rule in no `@layer`.
    public StyleRule(
            List<Selector> selectors,
            List<Declaration> declarations,
            int order,
            boolean starting,
            MediaCondition media) {
        this(selectors, declarations, order, starting, media, null);
    }

    /// A rule outside any `@media` block.
    public StyleRule(List<Selector> selectors, List<Declaration> declarations, int order, boolean starting) {
        this(selectors, declarations, order, starting, MediaCondition.ALWAYS);
    }

    /// Whether it sits inside an `@media` block, so the cascade has to ask.
    ///
    /// By identity, which is what makes the question free for the rules
    /// outside every block.
    public boolean isConditional() {
        return media != MediaCondition.ALWAYS;
    }

    /// This rule applying only under `condition` as well: an imported sheet's
    /// rule under its `@import`'s media list. This rule itself when the
    /// condition always holds.
    public StyleRule under(MediaCondition condition) {
        Objects.requireNonNull(condition, "condition");
        if (condition == MediaCondition.ALWAYS) {
            return this;
        }
        var joined = media == MediaCondition.ALWAYS ? condition : new MediaCondition.And(List.of(condition, media));
        return new StyleRule(selectors, declarations, order, starting, joined, layer, containers);
    }

    /// This rule at position `value` of its sheet: an imported rule's place
    /// once the importing sheet's rules follow it.
    public StyleRule ordered(int value) {
        return value == order
                ? this
                : new StyleRule(selectors, declarations, value, starting, media, layer, containers);
    }

    /// This rule in `name`: an imported rule under its `@import`'s `layer(…)`,
    /// a rule already in a layer going inside it as `name.inner`.
    public StyleRule inLayer(String name) {
        Objects.requireNonNull(name, "name");
        return new StyleRule(
                selectors, declarations, order, starting, media, layer == null ? name : name + "." + layer, containers);
    }

    /// This rule with `value` in place of its declarations.
    public StyleRule declarations(List<Declaration> value) {
        return new StyleRule(selectors, value, order, starting, media, layer, containers);
    }

    /// An ordinary rule — every rule written outside `@starting-style`.
    public StyleRule(List<Selector> selectors, List<Declaration> declarations, int order) {
        this(selectors, declarations, order, false);
    }

    @Override
    public String toString() {
        return selectors.stream()
                        .map(Object::toString)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("") + " { " + declarations.size() + " declaration(s) }"
                + (isConditional() ? " under @media " + media : "")
                + (isContained() ? " under @container " + containers : "");
    }
}
