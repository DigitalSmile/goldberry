package io.github.digitalsmile.goldberry.css;

import java.util.Objects;

/// Decides whether a [Selector] applies to a [StyleElement].
///
/// Matches **right to left**, which is why [Selector] stores its parts that way:
/// the rightmost compound is the only one that has to match the element itself,
/// and everything else is a walk up the ancestor chain. Matching left to right
/// would mean descending into every descendant of every candidate — the same
/// answer, enormously more work.
public final class SelectorMatcher {

    private SelectorMatcher() {
    }

    /// Whether `selector` matches `element`.
    public static boolean matches(Selector selector, StyleElement element) {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(element, "element");
        return matchFrom(selector, 0, element);
    }

    /// Matches `parts[index]` against `element`, then everything to its left
    /// against that element's ancestors.
    ///
    /// Recursive rather than a loop **because of backtracking**. A descendant
    /// combinator can match more than one ancestor, and the first one that fits
    /// is not always the one that lets the rest of the selector fit. For
    /// `.a > .b .c` against `.a > .b > .b > .c`, a greedy walk picks the inner
    /// `.b`, fails to find `.a` as its direct parent, and wrongly reports no
    /// match. Trying each candidate ancestor in turn is what makes it correct.
    private static boolean matchFrom(Selector selector, int index, StyleElement element) {
        var parts = selector.parts();
        if (!matchesCompound(parts.get(index).compound(), element)) {
            return false;
        }
        if (index == parts.size() - 1) {
            return true;
        }

        // The combinator on this part joins it to the compound on its left,
        // which -- the list being rightmost first -- is the next one along.
        var combinator = parts.get(index).combinator();
        var parent = element.parent();
        if (combinator == Selector.Combinator.CHILD) {
            return parent != null && matchFrom(selector, index + 1, parent);
        }
        for (var ancestor = parent; ancestor != null; ancestor = ancestor.parent()) {
            if (matchFrom(selector, index + 1, ancestor)) {
                return true;
            }
        }
        return false;
    }

    /// Whether every simple selector in one compound holds for one element.
    static boolean matchesCompound(Selector.Compound compound, StyleElement element) {
        if (compound.type() != null && !compound.type().equals(element.type())) {
            return false;
        }
        if (compound.id() != null && !compound.id().equals(element.id())) {
            return false;
        }
        for (var required : compound.classes()) {
            if (!element.classes().contains(required)) {
                return false;
            }
        }
        for (var state : compound.pseudoClasses()) {
            // ROOT is answered by the tree rather than by the element, so an
            // implementation cannot report something the tree contradicts.
            var holds = state == Selector.PseudoClass.ROOT
                    ? element.parent() == null
                    : element.hasState(state);
            if (!holds) {
                return false;
            }
        }
        return true;
    }
}
