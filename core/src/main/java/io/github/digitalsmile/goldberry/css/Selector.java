package io.github.digitalsmile.goldberry.css;

import java.util.List;
import java.util.Objects;

/// A selector: what a rule matches, and how strongly.
///
/// The subset in `ARCHITECTURE.md` §8 — type, `.class`, `#id`, the descendant and
/// child combinators, and the six pseudo-classes that describe widget state.
/// Nothing else in v1, and the omissions are load-bearing: no sibling
/// combinators, no attribute selectors, no `:nth-child`. Each of those needs the
/// element tree to answer questions about *order*, and the cheapest way to keep
/// matching a walk up the ancestor chain is to never ask one.
///
/// A selector is a chain of [Compound]s joined by [Combinator]s, stored
/// **rightmost first**. That is the order matching reads them in: find the
/// elements the last compound could apply to, then walk *up* to check the rest.
/// Matching left to right would mean walking down into every descendant of every
/// candidate.
///
/// @param parts rightmost compound first; never empty
public record Selector(List<Part> parts) {

    /// How a compound relates to the one on its left.
    public enum Combinator {
        /// `.a .b` — anywhere below.
        DESCENDANT,
        /// `.a > .b` — directly below.
        CHILD,
        /// The leftmost compound, which has nothing to its left.
        NONE
    }

    /// One compound and the combinator tying it to what precedes it.
    ///
    /// @param compound   the simple selectors that must all match one element
    /// @param combinator how [#compound()] relates to the part after it in
    ///                   [Selector#parts()] — which, since the list is rightmost
    ///                   first, is the part to its *left* in the source text
    public record Part(Compound compound, Combinator combinator) {
        public Part {
            Objects.requireNonNull(compound, "compound");
            Objects.requireNonNull(combinator, "combinator");
        }
    }

    /// Everything that must be true of a single element: `button.primary:hover`.
    ///
    /// @param type         the element type, or null for `*` and for a compound
    ///                     that names none
    /// @param id           the `#id`, or null
    /// @param classes      the `.class` names, in source order
    /// @param pseudoClasses the `:state` names
    public record Compound(
            String type, String id, List<String> classes, List<PseudoClass> pseudoClasses) {

        public Compound {
            classes = List.copyOf(classes == null ? List.of() : classes);
            pseudoClasses = List.copyOf(pseudoClasses == null ? List.of() : pseudoClasses);
        }

        /// Whether this compound constrains nothing — the `*` of `* > .a`.
        public boolean isUniversal() {
            return type == null && id == null && classes.isEmpty() && pseudoClasses.isEmpty();
        }

        @Override
        public String toString() {
            var text = new StringBuilder();
            if (type != null) {
                text.append(type);
            }
            if (id != null) {
                text.append('#').append(id);
            }
            classes.forEach(c -> text.append('.').append(c));
            pseudoClasses.forEach(p -> text.append(':').append(p.cssName()));
            return text.isEmpty() ? "*" : text.toString();
        }
    }

    /// The pseudo-classes a stylesheet can select on.
    ///
    /// A closed set rather than a free string: these are the ones the element
    /// tree will actually track, and a typo like `:hovered` should be a
    /// stylesheet error rather than a rule that silently never matches.
    ///
    /// Six of them are the widget states §8 lists. [#ROOT] is the odd one and is
    /// here because the theming mechanism needs it: §10 makes a theme "a CSS
    /// custom-property layer", and the only place to hang custom properties that
    /// everything inherits is the root element. Without it the engine could not
    /// express its own themes.
    public enum PseudoClass {
        HOVER,
        ACTIVE,
        FOCUS,
        FOCUS_VISIBLE,
        DISABLED,
        CHECKED,

        /// A tri-state control whose value is neither on nor off — CSS's own
        /// `:indeterminate`, and the eighth of a set `docs/core-widgets.md` lists
        /// as seven.
        ///
        /// Added with `checkbox`, because a mixed checkbox has to be
        /// *distinguishable* from a checked one and from an unchecked one, and
        /// two pseudo-classes cannot describe three states. It is deliberately
        /// not "checked plus a modifier": a stylesheet that wrote
        /// `checkbox:checked` and meant "the tick is showing" would otherwise be
        /// wrong for the mixed case, silently.
        INDETERMINATE,

        /// The root element — `:root { --gb-accent: … }`.
        ///
        /// Structural rather than a state: it never changes for an element, so
        /// unlike the others it can never invalidate a subtree.
        ROOT;

        /// The name as it is written in CSS — `focus-visible`, not `FOCUS_VISIBLE`.
        public String cssName() {
            return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        }

        static PseudoClass parse(String name) {
            for (var candidate : values()) {
                if (candidate.cssName().equalsIgnoreCase(name)) {
                    return candidate;
                }
            }
            return null;
        }
    }

    public Selector {
        parts = List.copyOf(Objects.requireNonNull(parts, "parts"));
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("a selector needs at least one compound");
        }
    }

    /// The compound that must match the element itself.
    public Compound key() {
        return parts.getFirst().compound();
    }

    /// Specificity, packed so that plain integer comparison orders two selectors.
    ///
    /// CSS specificity is the triple (ids, classes+pseudo-classes, types). Packed
    /// into one int at 10 bits each rather than compared field by field, because
    /// the cascade sorts by it on every rule and a comparator that allocates is a
    /// comparator that shows up in a profile.
    ///
    /// 10 bits caps each count at 1023. A selector with 1024 classes is not a
    /// thing that happens, and the alternative — saturating arithmetic on a hot
    /// path — costs more than it protects.
    public int specificity() {
        var ids = 0;
        var classes = 0;
        var types = 0;
        for (var part : parts) {
            var compound = part.compound();
            if (compound.id() != null) {
                ids++;
            }
            classes += compound.classes().size() + compound.pseudoClasses().size();
            if (compound.type() != null) {
                types++;
            }
        }
        return (Math.min(ids, 1023) << 20) | (Math.min(classes, 1023) << 10) | Math.min(types, 1023);
    }

    @Override
    public String toString() {
        var text = new StringBuilder();
        // Reversed, because the parts are stored rightmost first and a human
        // reads a selector left to right.
        for (var i = parts.size() - 1; i >= 0; i--) {
            var part = parts.get(i);
            text.append(part.compound());
            if (i > 0) {
                text.append(parts.get(i - 1).combinator() == Combinator.CHILD ? " > " : " ");
            }
        }
        return text.toString();
    }
}
