package dev.goldberry.css.select;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.StyleElement;

/// Decides whether a [Selector] applies to a [StyleElement].
///
/// Matches **right to left**, which is why [Selector] stores its parts that way:
/// the rightmost compound is the only one that has to match the element itself,
/// and everything else is a walk up the ancestor chain and back along the
/// siblings. Matching left to right would mean descending into every
/// descendant of every candidate — the same answer, enormously more work.
/// `:has()` is the exception, and the only walk down.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#selectors).
public final class SelectorMatcher {

    private SelectorMatcher() {}

    /// Whether `selector` matches `element`.
    public static boolean matches(Selector selector, StyleElement element) {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(element, "element");
        return matchFrom(selector, 0, element, null);
    }

    /// Matches `parts[index]` against `element`, then everything to its left
    /// against that element's ancestors and earlier siblings.
    ///
    /// Recursive rather than a loop **because of backtracking**. A descendant
    /// combinator can match more than one ancestor, and the first one that fits
    /// is not always the one that lets the rest of the selector fit. For
    /// `.a > .b .c` against `.a > .b > .b > .c`, a greedy walk picks the inner
    /// `.b`, fails to find `.a` as its direct parent, and wrongly reports no
    /// match. Trying each candidate ancestor in turn is what makes it correct,
    /// and `~` tries each earlier sibling the same way.
    ///
    /// @param anchor the element a `:has()` argument is relative to, which the
    ///               leftmost compound must stand against as its combinator
    ///               says; null for an ordinary selector
    private static boolean matchFrom(
            Selector selector, int index, StyleElement element, @Nullable StyleElement anchor) {
        var parts = selector.parts();
        if (!matchesCompound(parts.get(index).compound(), element)) {
            return false;
        }
        // The combinator on this part joins it to the compound on its left,
        // which -- the list being rightmost first -- is the next one along.
        var combinator = parts.get(index).combinator();
        if (index == parts.size() - 1) {
            return anchor == null || placed(combinator, element, anchor);
        }
        var next = index + 1;
        return switch (combinator) {
            case CHILD -> {
                var parent = element.parent();
                yield parent != null && matchFrom(selector, next, parent, anchor);
            }
            case DESCENDANT, PART, NONE -> {
                for (var ancestor = element.parent(); ancestor != null; ancestor = ancestor.parent()) {
                    if (matchFrom(selector, next, ancestor, anchor)) {
                        yield true;
                    }
                }
                yield false;
            }
            case NEXT_SIBLING -> {
                var siblings = element.siblings();
                var at = indexAmong(siblings, element);
                yield at > 0 && matchFrom(selector, next, siblings.get(at - 1), anchor);
            }
            case SUBSEQUENT_SIBLING -> {
                var siblings = element.siblings();
                for (var at = indexAmong(siblings, element) - 1; at >= 0; at--) {
                    if (matchFrom(selector, next, siblings.get(at), anchor)) {
                        yield true;
                    }
                }
                yield false;
            }
        };
    }

    /// Whether `element` stands against `anchor` as `combinator` says: below
    /// it, its child, the sibling just after it, or a sibling after it.
    private static boolean placed(Selector.Combinator combinator, StyleElement element, StyleElement anchor) {
        return switch (combinator) {
            case CHILD -> element.parent() == anchor;
            case DESCENDANT, PART, NONE -> {
                for (var ancestor = element.parent(); ancestor != null; ancestor = ancestor.parent()) {
                    if (ancestor == anchor) {
                        yield true;
                    }
                }
                yield false;
            }
            case NEXT_SIBLING -> {
                var siblings = element.siblings();
                var at = indexAmong(siblings, element);
                yield at > 0 && siblings.get(at - 1) == anchor;
            }
            case SUBSEQUENT_SIBLING -> {
                var siblings = element.siblings();
                var at = indexAmong(siblings, element);
                var found = false;
                for (var i = 0; i < at && !found; i++) {
                    found = siblings.get(i) == anchor;
                }
                yield found;
            }
        };
    }

    /// Where `element` is in `siblings`, by identity, or -1. The element's own
    /// [StyleElement#indexInParent()] is tried first, since it is right
    /// whenever the tree has positioned its children.
    private static int indexAmong(List<? extends StyleElement> siblings, StyleElement element) {
        var hint = element.indexInParent();
        if (hint >= 0 && hint < siblings.size() && siblings.get(hint) == element) {
            return hint;
        }
        for (var i = 0; i < siblings.size(); i++) {
            if (siblings.get(i) == element) {
                return i;
            }
        }
        return -1;
    }

    /// Whether every simple selector in one compound holds for one element.
    static boolean matchesCompound(Selector.Compound compound, StyleElement element) {
        if (compound.type() != null && !compound.type().equals(element.type())) {
            return false;
        }
        if (compound.id() != null && !compound.id().equals(element.id())) {
            return false;
        }
        if (compound.pseudoElement() != null && compound.pseudoElement() != element.pseudoElement()) {
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
            // ENABLED is DISABLED's negation, answered from it.
            var holds = switch (state) {
                case ROOT -> element.parent() == null;
                case ENABLED -> !element.hasState(Selector.PseudoClass.DISABLED);
                default -> element.hasState(state);
            };
            if (!holds) {
                return false;
            }
        }
        if (!compound.structural().isEmpty()) {
            var position = SiblingPosition.of(element);
            for (var structural : compound.structural()) {
                if (!structural.matches(position)) {
                    return false;
                }
            }
        }
        for (var functional : compound.functional()) {
            var holds = switch (functional) {
                case Functional.Is(var arguments) -> any(arguments, element);
                case Functional.Where(var arguments) -> any(arguments, element);
                case Functional.Not(var arguments) -> !any(arguments, element);
                case Functional.NthOf(var position, var arguments) -> nthOf(position, arguments, element);
                case Functional.Has(var arguments) -> has(arguments, element);
            };
            if (!holds) {
                return false;
            }
        }
        return true;
    }

    /// Whether any of `selectors` matches `element`.
    private static boolean any(List<Selector> selectors, StyleElement element) {
        for (var selector : selectors) {
            if (matchFrom(selector, 0, element, null)) {
                return true;
            }
        }
        return false;
    }

    /// `:has(S)`: some element where one of the relative selectors `S` starts
    /// from `element` matches it.
    ///
    /// Searched downward from `element`, or along and below its later siblings
    /// for an argument that starts at one: the one walk in the matcher that
    /// goes down, and the reason the resolver restyles an element's ancestors
    /// when it changes under sheets that write `:has()`.
    private static boolean has(List<Selector> relative, StyleElement element) {
        for (var argument : relative) {
            if (Functional.Has.anchor(argument).isSibling()) {
                var siblings = element.siblings();
                for (var at = indexAmong(siblings, element) + 1; at > 0 && at < siblings.size(); at++) {
                    if (within(argument, siblings.get(at), element)) {
                        return true;
                    }
                }
            } else {
                for (var child : element.children()) {
                    if (within(argument, child, element)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /// Whether `argument`, relative to `anchor`, matches `candidate` or
    /// anything under it.
    private static boolean within(Selector argument, StyleElement candidate, StyleElement anchor) {
        if (matchFrom(argument, 0, candidate, anchor)) {
            return true;
        }
        for (var child : candidate.children()) {
            if (within(argument, child, anchor)) {
                return true;
            }
        }
        return false;
    }

    /// `:nth-child(An+B of S)`: `element` matches `S`, and its place among the
    /// siblings that match `S` is one `position` names.
    private static boolean nthOf(Structural.Nth position, List<Selector> filter, StyleElement element) {
        if (!any(filter, element)) {
            return false;
        }
        var index = -1;
        var count = 0;
        for (var sibling : element.siblings()) {
            if (sibling == element) {
                index = count;
                count++;
            } else if (any(filter, sibling)) {
                count++;
            }
        }
        return index >= 0 && position.matches(index, count);
    }
}
