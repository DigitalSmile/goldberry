package dev.goldberry.css.select;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// A structural pseudo-class: where an element sits among its siblings.
///
/// ```css
/// list-row:first-child       { border-top: none }
/// .steps > :last-child       { margin-bottom: 0 }
/// table-row:nth-child(even)  { background: var(--gb-surface-2) }
/// ```
///
/// `:first-child`, `:last-child`, `:only-child`, `:nth-child(An+B)` and
/// `:nth-last-child(An+B)`, with `odd` and `even`. Unlike the widget states in
/// [Selector.PseudoClass], these are answered from the element tree's shape,
/// so an element's answer changes when its parent's children are added,
/// removed or reordered, and the element tree invalidates exactly the
/// children whose answers changed.
///
/// The siblings are the children of the element [dev.goldberry.css.StyleElement#parent()]
/// names, which is also what the child combinator `>` reads. A composition
/// widget is an element with one child, like an unstyled `<div>` around one
/// node, so a card built by a stateless wrapper is the only child of that
/// wrapper.
///
/// Read more: [Selectors](https://goldberry.dev/docs/guide/styling.html#selectors).
public sealed interface Structural {

    /// Whether this holds for the element at `index` of `count` siblings.
    ///
    /// @param index the element's position among its parent's children, from 0
    /// @param count how many children its parent has, the element included
    boolean matches(int index, int count);

    /// The pseudo-class as CSS writes it, without the colon.
    String cssName();

    /// `:nth-child(An+B)`, or `:nth-last-child(An+B)` counting from the end.
    ///
    /// `:first-child` is `Nth(0, 1, false)` and `:last-child` is
    /// `Nth(0, 1, true)`; they print as their own names.
    ///
    /// @param step    the `A`: every how many siblings it matches, or 0 for one
    /// @param offset  the `B`: the first position it matches, counted from 1
    /// @param fromEnd whether positions count from the last child
    record Nth(int step, int offset, boolean fromEnd) implements Structural {

        @Override
        public boolean matches(int index, int count) {
            var position = fromEnd ? count - index : index + 1;
            if (step == 0) {
                return position == offset;
            }
            var distance = position - offset;
            return distance % step == 0 && distance / step >= 0;
        }

        @Override
        public String cssName() {
            if (step == 0 && offset == 1) {
                return fromEnd ? "last-child" : "first-child";
            }
            return (fromEnd ? "nth-last-child(" : "nth-child(") + formula() + ")";
        }

        private String formula() {
            if (step == 0) {
                return Integer.toString(offset);
            }
            var a = switch (step) {
                case 1 -> "";
                case -1 -> "-";
                default -> Integer.toString(step);
            };
            if (offset == 0) {
                return a + "n";
            }
            return a + "n" + (offset > 0 ? "+" : "") + offset;
        }
    }

    /// `:only-child`: the one child of its parent.
    record OnlyChild() implements Structural {

        @Override
        public boolean matches(int index, int count) {
            return count == 1;
        }

        @Override
        public String cssName() {
            return "only-child";
        }
    }

    /// `:first-child`.
    static Structural firstChild() {
        return new Nth(0, 1, false);
    }

    /// `:last-child`.
    static Structural lastChild() {
        return new Nth(0, 1, true);
    }

    /// `:only-child`.
    static Structural onlyChild() {
        return new OnlyChild();
    }

    /// The pseudo-class `name` names when written without an argument, or null.
    static @Nullable Structural parse(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "first-child" -> firstChild();
            case "last-child" -> lastChild();
            case "only-child" -> onlyChild();
            default -> null;
        };
    }
}
