package dev.goldberry.css.select;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// A selector: what a rule matches, and how strongly.
///
/// ```css
/// button.primary:hover { background: var(--gb-accent-hover) }
/// card > heading { font-weight: 600 }
/// ```
///
/// The subset is type, `.class`, `#id`, the four combinators (descendant,
/// child, `+` and `~`), the pseudo-classes that describe widget state, the
/// [Structural] ones that describe where an element sits among its siblings
/// (`:first-child`, `:nth-child(An+B)` and the rest), the [Functional] ones
/// that take selectors, `:has()` among them, and the two [PseudoElement]s a
/// part stands for. Attribute selectors and the other pseudo-elements are not
/// in it: a widget's markup properties stay out of the cascade, and a part type
/// stands where CSS has `::before`.
///
/// A selector is a chain of [Compound]s joined by [Combinator]s, stored
/// **rightmost first**. That is the order matching reads them in: find the
/// elements the last compound could apply to, then walk *up* to check the rest.
/// Matching left to right would mean walking down into every descendant of every
/// candidate.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#selectors).
///
/// @param parts rightmost compound first; never empty
public record Selector(List<Part> parts) {

    /// How a compound relates to the one on its left.
    public enum Combinator {
        /// `.a .b` — anywhere below.
        DESCENDANT,
        /// `.a > .b` — directly below.
        CHILD,
        /// `.a + .b` — the sibling just after.
        NEXT_SIBLING,
        /// `.a ~ .b` — any sibling after.
        SUBSEQUENT_SIBLING,
        /// `.a::placeholder` — the part below `.a` that stands for a
        /// [PseudoElement]. Matched as a descendant, and written with no space.
        PART,
        /// The leftmost compound, which has nothing to its left.
        NONE;

        /// Whether this joins two siblings rather than an element to an
        /// ancestor.
        public boolean isSibling() {
            return this == NEXT_SIBLING || this == SUBSEQUENT_SIBLING;
        }

        /// The combinator as written between two compounds, spaces included:
        /// `" > "`, `" + "`, or one space for a descendant.
        public String cssText() {
            return switch (this) {
                case DESCENDANT, NONE -> " ";
                case PART -> "";
                case CHILD -> " > ";
                case NEXT_SIBLING -> " + ";
                case SUBSEQUENT_SIBLING -> " ~ ";
            };
        }
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
    /// @param structural   the `:first-child`-like conditions on its position
    /// @param functional   the pseudo-classes that take a selector list:
    ///                     `:is()`, `:where()`, `:not()`, `:has()`,
    ///                     `:nth-child(… of S)`
    /// @param pseudoElement the pseudo-element the matched part stands for, or
    ///                     null; only on a selector's last compound
    public record Compound(
            @Nullable String type,
            @Nullable String id,
            List<String> classes,
            List<PseudoClass> pseudoClasses,
            List<Structural> structural,
            List<Functional> functional,
            @Nullable PseudoElement pseudoElement) {

        /// Written out so that the parameters taking null for a default can say so.
        public Compound(
                @Nullable String type,
                @Nullable String id,
                @Nullable List<String> classes,
                @Nullable List<PseudoClass> pseudoClasses,
                @Nullable List<Structural> structural,
                @Nullable List<Functional> functional,
                @Nullable PseudoElement pseudoElement) {
            classes = List.copyOf(classes == null ? List.of() : classes);
            pseudoClasses = List.copyOf(pseudoClasses == null ? List.of() : pseudoClasses);
            structural = List.copyOf(structural == null ? List.of() : structural);
            functional = List.copyOf(functional == null ? List.of() : functional);
            this.type = type;
            this.id = id;
            this.classes = classes;
            this.pseudoClasses = pseudoClasses;
            this.structural = structural;
            this.functional = functional;
            this.pseudoElement = pseudoElement;
        }

        /// A compound that stands for no pseudo-element.
        public Compound(
                @Nullable String type,
                @Nullable String id,
                @Nullable List<String> classes,
                @Nullable List<PseudoClass> pseudoClasses,
                @Nullable List<Structural> structural,
                @Nullable List<Functional> functional) {
            this(type, id, classes, pseudoClasses, structural, functional, null);
        }

        /// The part that stands for `pseudoElement`, and nothing else about it:
        /// what `::placeholder` reads as.
        public static Compound of(PseudoElement pseudoElement) {
            return new Compound(null, null, null, null, null, null, pseudoElement);
        }

        /// A compound with no functional pseudo-class.
        public Compound(
                @Nullable String type,
                @Nullable String id,
                @Nullable List<String> classes,
                @Nullable List<PseudoClass> pseudoClasses,
                @Nullable List<Structural> structural) {
            this(type, id, classes, pseudoClasses, structural, null);
        }

        /// A compound with no structural condition.
        public Compound(
                @Nullable String type,
                @Nullable String id,
                @Nullable List<String> classes,
                @Nullable List<PseudoClass> pseudoClasses) {
            this(type, id, classes, pseudoClasses, null, null);
        }

        /// Whether this compound constrains nothing — the `*` of `* > .a`.
        public boolean isUniversal() {
            return type == null
                    && id == null
                    && classes.isEmpty()
                    && pseudoClasses.isEmpty()
                    && structural.isEmpty()
                    && functional.isEmpty()
                    && pseudoElement == null;
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
            structural.forEach(p -> text.append(':').append(p.cssName()));
            functional.forEach(f -> text.append(f.cssText()));
            if (pseudoElement != null) {
                text.append("::").append(pseudoElement.cssName());
            }
            return text.isEmpty() ? "*" : text.toString();
        }
    }

    /// The pseudo-classes a stylesheet can select on.
    ///
    /// A closed set rather than a free string: these are the ones the element
    /// tree will actually track, and a typo like `:hovered` should be a
    /// stylesheet error rather than a rule that silently never matches.
    ///
    /// Most of them are widget states. [#ROOT] is the odd one and is here because
    /// the theming mechanism needs it: a theme is a layer of CSS custom
    /// properties, and the only place to hang custom properties that everything
    /// inherits is the root element. Without it the engine could not express its
    /// own themes.
    public enum PseudoClass {
        HOVER,
        ACTIVE,
        FOCUS,
        FOCUS_VISIBLE,

        /// The element has the keyboard focus, or something under it does —
        /// `field:focus-within label`.
        ///
        /// Set by the router on the focused element and every ancestor of it,
        /// and taken off only those the focus left: a move between two controls
        /// in one `field` leaves the field's answer alone.
        FOCUS_WITHIN,
        DISABLED,
        CHECKED,

        /// A tri-state control whose value is neither on nor off — CSS's own
        /// `:indeterminate`.
        ///
        /// Exists for `checkbox`, because a mixed checkbox has to be
        /// *distinguishable* from a checked one and from an unchecked one, and
        /// two pseudo-classes cannot describe three states. It is deliberately
        /// not "checked plus a modifier": a stylesheet that wrote
        /// `checkbox:checked` and meant "the tick is showing" would otherwise be
        /// wrong for the mixed case, silently.
        INDETERMINATE,

        /// A form control whose value its `field` will not accept.
        ///
        /// A pseudo-class rather than a `.invalid` class, because it is a **state**
        /// in the sense the others are: it changes while the element stands still,
        /// and it has to invalidate a subtree so that a `field`'s label and message
        /// can react to its control being wrong.
        ///
        /// It sits on the control *and* on the field, and that is not redundancy:
        /// a stylesheet wants `text-input:invalid` for the red border and
        /// `field:invalid field-message` for the message under it, and no selector
        /// in the subset can walk from a child back up to a parent.
        INVALID,

        /// An `affix` that has pinned itself.
        ///
        /// A widget's own state rather than the router's, like `:checked`: the
        /// pointer and the keyboard know nothing about it, and what decides it is
        /// a comparison between two rectangles the widget was told about after
        /// layout.
        ///
        /// It exists so a sticky header can gain a shadow **the moment it lifts**,
        /// which is the whole visual point of the widget and is not expressible any
        /// other way: no selector can say "this node is currently over another one".
        AFFIXED,

        /// A drop target with a drag over it that it accepts — `column:drag-over`
        /// for the kanban column a card would land in.
        ///
        /// The router's, like `:hover`, and set on the **target alone** rather
        /// than its ancestors: a column inside a board that is itself a target
        /// lights up, and the board does not, because only one of them would
        /// take the drop. A target whose `accepts` refused the payload never
        /// matches it, so a stylesheet that colours `:drag-over` shows the user
        /// where a drop would land and nowhere else.
        DRAG_OVER,

        /// Not [#DISABLED] — `button:enabled`.
        ///
        /// No state of its own: it holds exactly when `:disabled` does not, so the
        /// matcher answers it from that one and a change to `:disabled` is the
        /// change that moves it.
        ENABLED,

        /// The root element — `:root { --gb-accent: … }`.
        ///
        /// Structural rather than a state: it never changes for an element, so
        /// unlike the others it can never invalidate a subtree.
        ROOT;

        /// The name as it is written in CSS — `focus-visible`, not `FOCUS_VISIBLE`.
        public String cssName() {
            return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        }

        /// Public because the parser that calls it lives in `css.parse` and a
        /// selector lives here.
        public static @Nullable PseudoClass parse(String name) {
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
    ///
    /// A functional pseudo-class adds what [Functional#specificity()] says, field
    /// by field: `:is()` and `:not()` their most specific argument, `:where()`
    /// nothing, and `:nth-child(… of S)` a pseudo-class plus `S`.
    public int specificity() {
        var ids = 0;
        var classes = 0;
        var types = 0;
        for (var part : parts) {
            var compound = part.compound();
            if (compound.id() != null) {
                ids++;
            }
            classes += compound.classes().size()
                    + compound.pseudoClasses().size()
                    + compound.structural().size();
            if (compound.type() != null) {
                types++;
            }
            if (compound.pseudoElement() != null) {
                types++;
            }
            for (var functional : compound.functional()) {
                var added = functional.specificity();
                ids += added >>> 20;
                classes += (added >>> 10) & 1023;
                types += added & 1023;
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
                text.append(parts.get(i - 1).combinator().cssText());
            }
        }
        return text.toString();
    }
}
