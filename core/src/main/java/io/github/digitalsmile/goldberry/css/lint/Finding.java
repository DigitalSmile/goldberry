package io.github.digitalsmile.goldberry.css.lint;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// One thing a stylesheet says that the engine will not do.
///
/// A **value**, not a log line, which is the whole of why this package exists.
/// Four properties were written into the toolkit's own sheets, silently dropped,
/// and found by looking at a picture — and the record that fixed that
/// ([ADR-0215]) argued the point that this generalises: a louder log is not the
/// answer, because a dropped *value* already warned at `WARN` and
/// `group-box-title` drew square corners for months anyway. A stream nobody is
/// watching is a stream nobody is watching at either level.
///
/// So the finding carries where it came from — the selector it was written under
/// and the line and column the parser saw it at — and an application decides when
/// to ask and what to do with the answer ([ADR-0257]).
///
/// @param kind     what is wrong
/// @param selector the selector the declaration was written under, as CSS
/// @param property the property, or null for [Kind#UNTYPED_RULE], which is about
///                 the selector rather than about anything in the block
/// @param value    the declaration's value as written, or null for the same
///                 reason
/// @param line     the 1-based line the parser saw it at, or 0 when unknown
/// @param column   the 1-based column, or 0 when unknown
public record Finding(
        Kind kind,
        String selector,
        @Nullable String property,
        @Nullable String value,
        int line,
        int column) {

    /// What is wrong with a rule.
    public enum Kind {

        /// The engine applies **nothing** from this declaration: either §8's
        /// subset has no such property, or it has one and would not take this
        /// value.
        ///
        /// The two are not told apart, and the reason is
        /// [io.github.digitalsmile.goldberry.css.ComputedStyle#applies]'s: telling
        /// them apart means the engine reporting rather than being asked, for a
        /// difference the author reads off §8's list either way. What matters is
        /// that the rule does nothing, which is what nobody could see.
        DEAD_DECLARATION,

        /// The rule's selector names no type, and could have.
        ///
        /// [ADR-0249]'s rule, which the toolkit's own sheets are held to and an
        /// application's are not: the cascade buckets rules by type, so an
        /// all-classes sheet is one every element has to consider in full. It is
        /// a **performance** finding rather than a correctness one, which is why
        /// it is reported beside the others rather than logged — a warning that
        /// is usually wrong is the log [ADR-0243] had just finished quietening.
        UNTYPED_RULE,

        /// Nothing in force gives the root a `color`, so every primitive that
        /// inherits one gets the initial black ([ADR-0415]) — the value held by
        /// [io.github.digitalsmile.goldberry.css.ComputedStyle#INITIAL]
        /// .
        ///
        /// A **fact about the sheets**, not about a pixel, and the distinction is
        /// the whole reason this is a finding rather than a warning. Black text is
        /// correct on a light theme and unreadable on a dark one, so the resolved
        /// colour is not evidence of anything; what is evidence is that no
        /// declaration anywhere set one, and the element inherited the initial
        /// value because there was nothing to inherit.
        ///
        /// A control escapes this — `controls.css` sets `color` on `checkbox`,
        /// `radio`, `toggle` and `slider` themselves — which is exactly why it
        /// took a bare `text` on a dark theme to find it, and why the check is
        /// about the root rather than about any node that happens to be dark.
        UNCOLOURED_ROOT;

        /// Whether the rule does the wrong thing, as against merely costing more
        /// than it needs to.
        ///
        /// The line an application draws when it decides whether to fail a build
        /// on a finding: a dead declaration is a drawing that is not happening,
        /// and an untyped rule draws correctly and asks every element to think
        /// about it.
        public boolean isDefect() {
            return this != UNTYPED_RULE;
        }
    }

    public Finding {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(selector, "selector");
        if (line < 0 || column < 0) {
            throw new IllegalArgumentException("a source position cannot be negative: " + line + ":" + column);
        }
    }

    /// Where this was written, as `line:column`, or the empty string when the
    /// position is not known.
    ///
    /// Not known is a real case rather than a defect: [Kind#UNTYPED_RULE] is
    /// about a selector, and a selector's position is not what the parser
    /// records — a rule's declarations carry theirs and the rule itself does not.
    public String position() {
        return line == 0 ? "" : line + ":" + column;
    }

    @Override
    public String toString() {
        var where = position();
        var prefix = where.isEmpty() ? "" : where + " ";
        return switch (kind) {
            case DEAD_DECLARATION ->
                prefix + selector + " { " + property + ": " + value
                        + " } — the engine applies nothing from this: either the property is not in the"
                        + " subset or the value is not one it takes";
            case UNTYPED_RULE -> prefix + selector + " — names no type, so every element has to consider it in full";
            case UNCOLOURED_ROOT ->
                prefix + selector + " — nothing in force gives the root a color, so every primitive that"
                        + " inherits one draws in the initial black. A control sets its own and gets away"
                        + " with it; a bare text does not. Write `color: var(--gb-text)` here.";
        };
    }
}
