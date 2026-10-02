package dev.goldberry.css;

import java.util.List;
import java.util.Objects;

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
public record StyleRule(
        List<Selector> selectors, List<Declaration> declarations, int order, boolean starting, MediaCondition media) {

    public StyleRule {
        selectors = List.copyOf(Objects.requireNonNull(selectors, "selectors"));
        declarations = List.copyOf(Objects.requireNonNull(declarations, "declarations"));
        Objects.requireNonNull(media, "media");
        if (selectors.isEmpty()) {
            throw new IllegalArgumentException("a rule needs at least one selector");
        }
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
                + (isConditional() ? " under @media " + media : "");
    }
}
