package io.github.digitalsmile.goldberry.css;

import java.util.List;
import java.util.Objects;

import io.github.digitalsmile.goldberry.css.select.Selector;

/// A selector list and the declarations it applies.
///
/// `.a, .b { color: red }` is one rule with two selectors, not two rules. The
/// cascade treats each selector separately — the one that matches with the
/// highest specificity is the one that counts — but they share a declaration
/// list, and duplicating it would mean two rules whose `order` differs when it
/// should not.
///
/// @param selectors    the selector list, in source order; never empty
/// @param declarations in source order, which is also the order the cascade
///                     resolves ties in
/// @param order        the rule's position in its stylesheet, counted across
///                     nested at-rules
/// @param starting     whether it was written inside `@starting-style`, which
///                     makes it the style an element transitions *from* on its
///                     first frame rather than one it has (ADR-0352)
public record StyleRule(List<Selector> selectors, List<Declaration> declarations, int order, boolean starting) {

    public StyleRule {
        selectors = List.copyOf(Objects.requireNonNull(selectors, "selectors"));
        declarations = List.copyOf(Objects.requireNonNull(declarations, "declarations"));
        if (selectors.isEmpty()) {
            throw new IllegalArgumentException("a rule needs at least one selector");
        }
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
                        .orElse("") + " { " + declarations.size() + " declaration(s) }";
    }
}
