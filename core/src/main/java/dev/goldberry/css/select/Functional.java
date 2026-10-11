package dev.goldberry.css.select;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/// A pseudo-class that takes a selector list: `:is()`, `:where()`, `:not()`,
/// `:has()`, and `:nth-child(An+B of S)` with its `nth-last-child` twin.
///
/// ```css
/// :is(button, toggle):hover { … }
/// :where(.card) text        { … }   /* specificity of `text` alone */
/// button:not(:disabled)     { … }
/// list-row:nth-child(odd of .visible) { … }
/// field:has(> text-input:focus) { … }
/// ```
///
/// Each argument is a complex selector, combinators and all, matched against
/// the element the compound is on: `:is(card > text)` holds for a `text` whose
/// parent is a `card`.
///
/// Sealed, so the matcher, the specificity sum and the resolver's indexes each
/// read it with a `switch` that has no default arm.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#selectors).
public sealed interface Functional {

    /// The selectors this pseudo-class holds, which the resolver's indexes walk
    /// into.
    List<Selector> arguments();

    /// The specificity this pseudo-class adds to its compound, packed as
    /// [Selector#specificity()] packs it.
    int specificity();

    /// The CSS spelling, `:` included.
    String cssText();

    /// `:is(S)`: any argument matches. As specific as its most specific
    /// argument.
    record Is(List<Selector> arguments) implements Functional {
        public Is {
            arguments = requireArguments(arguments);
        }

        @Override
        public int specificity() {
            return mostSpecific(arguments);
        }

        @Override
        public String cssText() {
            return ":is(" + list(arguments) + ")";
        }
    }

    /// `:where(S)`: [Is], with no specificity at all, which is what makes it
    /// the way to write a default anyone can override.
    record Where(List<Selector> arguments) implements Functional {
        public Where {
            arguments = requireArguments(arguments);
        }

        @Override
        public int specificity() {
            return 0;
        }

        @Override
        public String cssText() {
            return ":where(" + list(arguments) + ")";
        }
    }

    /// `:not(S)`: no argument matches. As specific as its most specific
    /// argument.
    record Not(List<Selector> arguments) implements Functional {
        public Not {
            arguments = requireArguments(arguments);
        }

        @Override
        public int specificity() {
            return mostSpecific(arguments);
        }

        @Override
        public String cssText() {
            return ":not(" + list(arguments) + ")";
        }
    }

    /// `:nth-child(An+B of S)`: the element matches `S`, and its position among
    /// the siblings that match `S` is one the formula names.
    ///
    /// As specific as a pseudo-class plus the most specific of `S`.
    ///
    /// @param position the formula, counted from the end for
    ///                 `:nth-last-child`
    /// @param arguments `S`, the selectors a counted sibling matches
    record NthOf(Structural.Nth position, List<Selector> arguments) implements Functional {
        public NthOf {
            Objects.requireNonNull(position, "position");
            arguments = requireArguments(arguments);
        }

        @Override
        public int specificity() {
            return (1 << 10) + mostSpecific(arguments);
        }

        @Override
        public String cssText() {
            var name = position.cssName();
            var formula = name.substring(name.indexOf('(') + 1, name.length() - 1);
            var function = position.fromEnd() ? "nth-last-child" : "nth-child";
            if (!name.contains("(")) {
                // `first-child` and `last-child`, spelled as the formula they are.
                formula = "1";
            }
            return ":" + function + "(" + formula + " of " + list(arguments) + ")";
        }
    }

    /// `:has(S)`: some element placed relative to this one, as each argument
    /// says, matches it. As specific as its most specific argument.
    ///
    /// Each argument is a **relative** selector, and its leftmost part's
    /// combinator, which is [Selector.Combinator#NONE] in an ordinary
    /// selector, says where it starts from: `:has(text)` is a descendant
    /// ([Selector.Combinator#DESCENDANT]), `:has(> text)` a child, `:has(+ x)`
    /// the next sibling and `:has(~ x)` any later one.
    ///
    /// @param arguments the relative selectors, leftmost combinator included
    record Has(List<Selector> arguments) implements Functional {
        public Has {
            arguments = requireArguments(arguments);
            for (var argument : arguments) {
                if (anchor(argument) == Selector.Combinator.NONE) {
                    throw new IllegalArgumentException(
                            "a :has() argument says how it is placed against its element: " + argument);
                }
            }
        }

        /// How `argument`'s leftmost compound is placed against the element
        /// `:has()` is on.
        public static Selector.Combinator anchor(Selector argument) {
            return argument.parts().getLast().combinator();
        }

        /// Whether some argument starts at a sibling rather than below, which
        /// is what makes a later sibling's change reach this element.
        public boolean looksAtSiblings() {
            return arguments.stream().anyMatch(argument -> anchor(argument).isSibling());
        }

        @Override
        public int specificity() {
            return mostSpecific(arguments);
        }

        @Override
        public String cssText() {
            return ":has("
                    + arguments.stream()
                            .map(argument -> switch (anchor(argument)) {
                                case DESCENDANT, PART, NONE -> argument.toString();
                                default -> anchor(argument).cssText().stripLeading() + argument;
                            })
                            .collect(Collectors.joining(", "))
                    + ")";
        }
    }

    private static List<Selector> requireArguments(List<Selector> arguments) {
        var copy = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("a functional pseudo-class needs at least one selector");
        }
        return copy;
    }

    /// The highest specificity among `selectors`. The packed form compares as
    /// the triple does, so the highest number is the most specific selector.
    private static int mostSpecific(List<Selector> selectors) {
        var most = 0;
        for (var selector : selectors) {
            most = Math.max(most, selector.specificity());
        }
        return most;
    }

    private static String list(List<Selector> selectors) {
        return selectors.stream().map(Selector::toString).collect(Collectors.joining(", "));
    }
}
