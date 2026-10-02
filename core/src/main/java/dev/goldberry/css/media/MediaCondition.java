package dev.goldberry.css.media;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import dev.goldberry.render.desktop.SystemTheme;

/// When the rules inside an `@media` block apply.
///
/// ```css
/// @media (max-width: 900px) { .sidebar { width: 200px } }
/// @media (prefers-color-scheme: dark) { :root { --brand: #88c0d0 } }
/// @media (prefers-reduced-motion: reduce) { * { animation: none } }
/// ```
///
/// A rule carries one, [#ALWAYS] for a rule outside any block, and the cascade
/// skips a rule whose condition does not hold under the renderer's
/// [MediaContext]. The features are the window's logical `width` and `height`
/// (as `min-`/`max-` or a range such as `(width >= 600px)`), `orientation`,
/// `prefers-color-scheme` and `prefers-reduced-motion`. They combine with
/// `and`, `or`, `not` and a comma list, and the media types `all` and
/// `screen` hold where `print` does not.
///
/// A feature outside that list is [Unsupported]: the query it is in never
/// holds, and the parser says so once with the sheet and the line. That is
/// CSS's own rule for a query it cannot read, and it is the safe direction:
/// a block that is never applied is visibly missing, where one applied
/// regardless styles every window for a condition nobody checked.
///
/// Read more: [Media queries](https://goldberry.dev/docs/guide/styling.html#media-queries).
public sealed interface MediaCondition {

    /// Whether the condition holds under `context`.
    boolean matches(MediaContext context);

    /// What a rule outside any `@media` block carries: it always applies.
    MediaCondition ALWAYS = Constant.ALL;

    /// A condition that never holds: `not all`, which `print` and the media
    /// types nobody draws on read as.
    MediaCondition NEVER = Constant.NOT_ALL;

    /// The reason `condition` will never hold because it asks something this
    /// toolkit cannot answer, or empty when every part of it is understood.
    ///
    /// Static rather than a `default` method, and not by preference: an
    /// interface that holds constants of its own subtypes **and** declares a
    /// default method is initialized whenever one of those subtypes is, which
    /// Error Prone flags as a possible class-initialization deadlock.
    static Optional<String> unsupported(MediaCondition condition) {
        return switch (condition) {
            case Unsupported(var _, var reason) -> Optional.of(reason);
            case Not(var inner) -> unsupported(inner);
            case And(var parts) -> firstUnsupported(parts);
            case Or(var parts) -> firstUnsupported(parts);
            default -> Optional.empty();
        };
    }

    private static Optional<String> firstUnsupported(List<MediaCondition> parts) {
        for (var part : parts) {
            var reason = unsupported(part);
            if (reason.isPresent()) {
                return reason;
            }
        }
        return Optional.empty();
    }

    /// The two answers that do not depend on the window: `all`, which `screen`
    /// and an `@media` with no query read as, and `not all`, which `print` does.
    ///
    /// An enum, so that the two constants above are this type's own and not a
    /// subclass the interface would have to initialize.
    enum Constant implements MediaCondition {
        ALL,
        NOT_ALL;

        @Override
        public boolean matches(MediaContext context) {
            return this == ALL;
        }

        @Override
        public String toString() {
            return this == ALL ? "all" : "not all";
        }
    }

    /// A query this toolkit cannot answer, which therefore never holds.
    ///
    /// @param text   the query as it was written
    /// @param reason what in it is not understood
    record Unsupported(String text, String reason) implements MediaCondition {

        public Unsupported {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(reason, "reason");
        }

        @Override
        public boolean matches(MediaContext context) {
            return false;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    /// `not …`.
    record Not(MediaCondition condition) implements MediaCondition {

        public Not {
            Objects.requireNonNull(condition, "condition");
        }

        @Override
        public boolean matches(MediaContext context) {
            // A query that cannot be read stays false under `not` too, which is
            // CSS's rule: an unknown is not a false that negation can rescue.
            return unsupported(condition).isEmpty() && !condition.matches(context);
        }

        @Override
        public String toString() {
            return "not " + condition;
        }
    }

    /// `… and …`: every part holds.
    record And(List<MediaCondition> conditions) implements MediaCondition {

        public And {
            conditions = List.copyOf(conditions);
        }

        @Override
        public boolean matches(MediaContext context) {
            for (var condition : conditions) {
                if (!condition.matches(context)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return conditions.stream().map(Object::toString).collect(Collectors.joining(" and "));
        }
    }

    /// `… or …`, and a comma-separated list of queries: any part holds.
    record Or(List<MediaCondition> conditions) implements MediaCondition {

        public Or {
            conditions = List.copyOf(conditions);
        }

        @Override
        public boolean matches(MediaContext context) {
            for (var condition : conditions) {
                if (condition.matches(context)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return conditions.stream().map(Object::toString).collect(Collectors.joining(", "));
        }
    }

    /// How a size is compared with the window's.
    enum Comparison {
        LESS("<"),
        AT_MOST("<="),
        EQUAL("="),
        AT_LEAST(">="),
        GREATER(">");

        private final String symbol;

        Comparison(String symbol) {
            this.symbol = symbol;
        }

        /// Whether `actual` stands in this relation to `limit`. False for NaN,
        /// which is a window that has not said its size.
        boolean holds(double actual, double limit) {
            return switch (this) {
                case LESS -> actual < limit;
                case AT_MOST -> actual <= limit;
                case EQUAL -> actual == limit;
                case AT_LEAST -> actual >= limit;
                case GREATER -> actual > limit;
            };
        }

        String symbol() {
            return symbol;
        }
    }

    /// `(min-width: 600px)`, `(max-width: 900px)`, `(width < 600px)`.
    ///
    /// @param comparison how the window's logical width relates to `px`
    /// @param px         the limit, in logical pixels
    record Width(Comparison comparison, double px) implements MediaCondition {

        public Width {
            Objects.requireNonNull(comparison, "comparison");
        }

        @Override
        public boolean matches(MediaContext context) {
            return comparison.holds(context.width(), px);
        }

        @Override
        public String toString() {
            return "(width " + comparison.symbol() + " " + px + "px)";
        }
    }

    /// `(min-height: 600px)` and the rest, as [Width] for the height.
    record Height(Comparison comparison, double px) implements MediaCondition {

        public Height {
            Objects.requireNonNull(comparison, "comparison");
        }

        @Override
        public boolean matches(MediaContext context) {
            return comparison.holds(context.height(), px);
        }

        @Override
        public String toString() {
            return "(height " + comparison.symbol() + " " + px + "px)";
        }
    }

    /// `(orientation: portrait)`: a window at least as tall as it is wide, which
    /// is CSS's definition; `landscape` is the rest.
    record Orientation(boolean portrait) implements MediaCondition {

        @Override
        public boolean matches(MediaContext context) {
            var tall = context.height() >= context.width();
            return portrait ? tall : context.width() > context.height();
        }

        @Override
        public String toString() {
            return "(orientation: " + (portrait ? "portrait" : "landscape") + ")";
        }
    }

    /// `(prefers-color-scheme: dark)`: the desktop's theme, read as light where
    /// the desktop does not say.
    record ColorScheme(SystemTheme theme) implements MediaCondition {

        public ColorScheme {
            Objects.requireNonNull(theme, "theme");
        }

        @Override
        public boolean matches(MediaContext context) {
            return context.colorScheme() == theme;
        }

        @Override
        public String toString() {
            return "(prefers-color-scheme: " + (theme == SystemTheme.DARK ? "dark" : "light") + ")";
        }
    }

    /// `(prefers-reduced-motion: reduce)`, or `no-preference`.
    record ReducedMotion(boolean reduce) implements MediaCondition {

        @Override
        public boolean matches(MediaContext context) {
            return context.reducedMotion() == reduce;
        }

        @Override
        public String toString() {
            return "(prefers-reduced-motion: " + (reduce ? "reduce" : "no-preference") + ")";
        }
    }
}
