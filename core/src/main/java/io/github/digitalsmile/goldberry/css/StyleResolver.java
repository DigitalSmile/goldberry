package io.github.digitalsmile.goldberry.css;

import io.github.digitalsmile.goldberry.natives.log.Logs;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;

/// Runs the cascade for one element and substitutes `var()`.
///
/// The output is still tokens, not typed values: what `4px 8px` means depends on
/// the property, and turning it into a `ComputedStyle` is the next step. What is
/// settled here is *which* declaration wins and what its `var()`s stand for.
public final class StyleResolver {

    private static final Logger LOG = Logs.of(StyleResolver.class);

    /// Custom properties inherit; ordinary ones do not, at this layer.
    ///
    /// Inheritance of ordinary properties (`color`, `font-size`) is a property
    /// table that does not exist yet and belongs with `ComputedStyle`. Custom
    /// properties have to inherit here because that is the whole theming
    /// mechanism: `:root { --gb-accent }` is useless if a button cannot see it.
    private final List<Stylesheet> stylesheets;

    public StyleResolver(List<Stylesheet> stylesheets) {
        this.stylesheets = List.copyOf(Objects.requireNonNull(stylesheets, "stylesheets"));
    }

    /// The declarations that apply to `element`, with `var()` resolved.
    ///
    /// @return property name to value tokens, in no particular order
    public Map<String, List<Token>> resolve(StyleElement element) {
        Objects.requireNonNull(element, "element");
        var customProperties = customPropertiesFor(element);
        var declared = cascade(element);

        var resolved = new LinkedHashMap<String, List<Token>>();
        for (var entry : declared.entrySet()) {
            if (entry.getKey().startsWith("--")) {
                // Already resolved into customProperties, and a custom property
                // is never itself a target for substitution.
                continue;
            }
            var value = substitute(entry.getValue(), customProperties, new HashSet<>());
            if (value == null) {
                // "Invalid at computed-value time": an unresolvable var() with no
                // fallback. CSS drops the declaration, and so does this -- but at
                // resolve time, which is inside the frame loop, so it warns
                // rather than throwing the way a parse error does.
                LOG.warn("dropping \"{}\" on <{}>: a var() in it resolves to nothing",
                        entry.getKey(), element.type());
                continue;
            }
            resolved.put(entry.getKey(), value);
        }
        return resolved;
    }

    /// Every custom property visible to `element`, its own overriding those it
    /// inherits.
    public Map<String, List<Token>> customPropertiesFor(StyleElement element) {
        // Built from the root down so a nearer definition overwrites a farther
        // one. Recursion rather than a loop because the chain is walked upward
        // and applied downward.
        var parent = element.parent();
        var inherited = parent == null
                ? new LinkedHashMap<String, List<Token>>()
                : new LinkedHashMap<>(customPropertiesFor(parent));

        for (var entry : cascade(element).entrySet()) {
            if (entry.getKey().startsWith("--")) {
                inherited.put(entry.getKey(), entry.getValue());
            }
        }
        return inherited;
    }

    /// The winning declaration for each property on `element`, before `var()`.
    private Map<String, List<Token>> cascade(StyleElement element) {
        var matches = new ArrayList<Match>();
        for (var sheet : stylesheets) {
            for (var rule : sheet.rules()) {
                // The most specific *matching* selector in the list is the one
                // that represents the rule, per the cascade.
                var best = -1;
                for (var selector : rule.selectors()) {
                    if (SelectorMatcher.matches(selector, element)) {
                        best = Math.max(best, selector.specificity());
                    }
                }
                if (best < 0) {
                    continue;
                }
                for (var declaration : rule.declarations()) {
                    matches.add(new Match(declaration, sheet.layer(), best, rule.order()));
                }
            }
        }

        // Weakest first, so a later put() overwrites a weaker one.
        matches.sort(CASCADE);

        var winners = new HashMap<String, List<Token>>();
        for (var match : matches) {
            winners.put(match.declaration().property(), match.declaration().value());
        }
        return winners;
    }

    /// The cascade order, weakest first.
    ///
    /// `!important` first because it outranks everything — with fixed layers
    /// there is no origin for it to invert, so it is simply the top key.
    ///
    /// Then specificity, then layer, then source order. Layer *after* specificity
    /// is what §8 specifies — "later layer wins at equal specificity" — which
    /// makes a layer an extension of source order rather than the override
    /// `@layer` provides. A more specific toolkit rule therefore still beats a
    /// vaguer application one, exactly as two rules in one stylesheet would.
    private static final Comparator<Match> CASCADE =
            Comparator.<Match, Boolean>comparing(m -> m.declaration().important())
                    .thenComparingInt(Match::specificity)
                    .thenComparing(Match::layer)
                    .thenComparingInt(Match::order);

    private record Match(Declaration declaration, CascadeLayer layer, int specificity, int order) {
    }

    /// Replaces every `var()` in `value`.
    ///
    /// @param inProgress the custom properties currently being expanded, so a
    ///                   cycle is caught rather than overflowing the stack
    /// @return the substituted tokens, or null if the value is invalid at
    ///         computed-value time
    static List<Token> substitute(
            List<Token> value, Map<String, List<Token>> variables, Set<String> inProgress) {

        if (value.stream().noneMatch(t -> t.is(TokenType.FUNCTION) && t.text().equalsIgnoreCase("var"))) {
            return value;
        }

        var out = new ArrayList<Token>();
        var i = 0;
        while (i < value.size()) {
            var token = value.get(i);
            if (!(token.is(TokenType.FUNCTION) && token.text().equalsIgnoreCase("var"))) {
                out.add(token);
                i++;
                continue;
            }

            var close = matchingParen(value, i);
            if (close < 0) {
                return null;
            }
            var arguments = value.subList(i + 1, close);
            var expanded = expandVar(arguments, variables, inProgress);
            if (expanded == null) {
                return null;
            }
            out.addAll(expanded);
            i = close + 1;
        }
        return out;
    }

    /// `--name` or `--name, fallback…`.
    private static List<Token> expandVar(
            List<Token> arguments, Map<String, List<Token>> variables, Set<String> inProgress) {

        var trimmed = trim(arguments);
        if (trimmed.isEmpty() || !trimmed.getFirst().is(TokenType.IDENT)
                || !trimmed.getFirst().text().startsWith("--")) {
            // var(4px) and var() are not things; treat as unresolvable rather
            // than guessing what was meant.
            return null;
        }
        var name = trimmed.getFirst().text();

        List<Token> fallback = null;
        for (var i = 1; i < trimmed.size(); i++) {
            if (trimmed.get(i).is(TokenType.COMMA)) {
                fallback = trim(trimmed.subList(i + 1, trimmed.size()));
                break;
            }
            if (!trimmed.get(i).is(TokenType.WHITESPACE)) {
                return null;
            }
        }

        var defined = variables.get(name);
        if (defined != null) {
            if (!inProgress.add(name)) {
                // --a: var(--b); --b: var(--a). Without this the stack goes.
                LOG.warn("custom property {} refers to itself; dropping the declaration", name);
                return null;
            }
            try {
                var substituted = substitute(defined, variables, inProgress);
                if (substituted != null) {
                    return substituted;
                }
            } finally {
                inProgress.remove(name);
            }
        }
        if (fallback != null) {
            return substitute(fallback, variables, inProgress);
        }
        return null;
    }

    /// The index of the `)` closing the function that starts at `open`.
    private static int matchingParen(List<Token> value, int open) {
        var depth = 0;
        for (var i = open; i < value.size(); i++) {
            var token = value.get(i);
            if (token.is(TokenType.FUNCTION) || token.is(TokenType.OPEN_PAREN)) {
                depth++;
            } else if (token.is(TokenType.CLOSE_PAREN)) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static List<Token> trim(List<Token> value) {
        var from = 0;
        var to = value.size();
        while (from < to && value.get(from).is(TokenType.WHITESPACE)) {
            from++;
        }
        while (to > from && value.get(to - 1).is(TokenType.WHITESPACE)) {
            to--;
        }
        return value.subList(from, to);
    }
}
