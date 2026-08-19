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

    /// For each pseudo-class, the element **types** that carry it in an ancestor
    /// position of some selector — `checkbox` for `:hover`, from
    /// `checkbox:hover check-indicator`.
    ///
    /// What it is for: a node whose state changed has to invalidate its
    /// descendants only if some rule can reach them *through* that state. See
    /// [#reachesDescendants].
    private final java.util.Map<Selector.PseudoClass, java.util.Set<String>> ancestorStates =
            new java.util.EnumMap<>(Selector.PseudoClass.class);

    /// The pseudo-classes used in an ancestor position by a compound that names
    /// **no type** — `.section:affixed > affix-content`.
    ///
    /// Those cannot be narrowed by type, so any node changing one of them stays
    /// conservative and invalidates its whole subtree.
    private final java.util.Set<Selector.PseudoClass> untypedAncestorStates =
            java.util.EnumSet.noneOf(Selector.PseudoClass.class);

    /// One rule, with the layer it came from — what a bucket holds.
    ///
    /// The layer travels with the rule because the buckets flatten the sheets
    /// away, and the cascade needs it back.
    private record Candidate(StyleRule rule, CascadeLayer layer) {
    }

    /// Rules whose **rightmost** compound names a type, bucketed by that type.
    ///
    /// A selector's rightmost compound is the one that has to match the element
    /// being styled, so a rule for `button` cannot possibly apply to a `text`.
    /// Testing it anyway is what made a single style resolve cost 1.7ms in the
    /// showcase: four stylesheets, some two thousand rules, matched in full
    /// against every element — and then again against every one of its ancestors,
    /// because custom properties are collected by walking to the root
    /// ([ADR-0152](../../../../../../book/src/adr/0152-the-cascade-looks-at-rules-that-could-match.md)).
    private final java.util.Map<String, List<Candidate>> byType = new java.util.HashMap<>();

    /// Rules whose rightmost compound names no type — `.primary`, `#gain`, `*`.
    ///
    /// These have to be tested against everything, and there are few of them: the
    /// toolkit's own sheets are written type-first, which is what makes the
    /// bucketing worth having.
    private final List<Candidate> untyped = new java.util.ArrayList<>();

    public StyleResolver(List<Stylesheet> stylesheets) {
        this.stylesheets = List.copyOf(Objects.requireNonNull(stylesheets, "stylesheets"));
        indexAncestorStates();
        indexByType();
    }

    /// Buckets every rule by the type its rightmost compound names.
    ///
    /// A rule with several selectors goes in every bucket any of them names, and
    /// in [#untyped] if any of them names none — a rule is a unit and the
    /// cascade has to see it whole, so over-collecting is the only safe error.
    private void indexByType() {
        for (var sheet : stylesheets) {
            for (var rule : sheet.rules()) {
                var candidate = new Candidate(rule, sheet.layer());
                var everywhere = false;
                for (var selector : rule.selectors()) {
                    var subject = selector.parts().getFirst().compound().type();
                    if (subject == null) {
                        everywhere = true;
                    } else {
                        byType.computeIfAbsent(subject, key -> new java.util.ArrayList<>())
                                .add(candidate);
                    }
                }
                if (everywhere) {
                    untyped.add(candidate);
                }
            }
        }
    }

    /// The rules that could match an element of this type.
    private List<Candidate> candidatesFor(String type) {
        if (type == null) {
            return untyped;
        }
        var typed = byType.get(type);
        if (typed == null) {
            return untyped;
        }
        if (untyped.isEmpty()) {
            return typed;
        }
        var all = new java.util.ArrayList<Candidate>(typed.size() + untyped.size());
        all.addAll(typed);
        all.addAll(untyped);
        return all;
    }

    /// Walks every selector once, recording which pseudo-classes appear to the
    /// **left** of a combinator and on what.
    ///
    /// Once per resolver, which is once per theme change — against a cascade pass
    /// per element per frame, which is what this saves.
    private void indexAncestorStates() {
        for (var sheet : stylesheets) {
            for (var rule : sheet.rules()) {
                for (var selector : rule.selectors()) {
                    var parts = selector.parts();
                    // Rightmost first, so everything past index 0 is an ancestor.
                    for (var i = 1; i < parts.size(); i++) {
                        var compound = parts.get(i).compound();
                        for (var state : compound.pseudoClasses()) {
                            if (compound.type() == null) {
                                untypedAncestorStates.add(state);
                            } else {
                                ancestorStates
                                        .computeIfAbsent(state, key -> new java.util.HashSet<>())
                                        .add(compound.type());
                            }
                        }
                    }
                }
            }
        }
    }

    /// Whether `state` changing on an element of `type` can change what matches
    /// **below** it.
    ///
    /// The question [io.github.digitalsmile.goldberry.widget.Element#setPseudoClass]
    /// asks before throwing a subtree's styles away. `checkbox:hover
    /// check-indicator` means yes for `:hover` on a `checkbox`; nothing in any
    /// sheet says `column:hover …`, so hovering a `column` — which is what a
    /// click on empty space does — changes that node and nothing under it
    /// ([ADR-0149](../../../../../../book/src/adr/0149-a-state-invalidates-what-it-can-reach.md)).
    ///
    /// Conservative in both directions it can be: an untyped ancestor compound
    /// makes its pseudo-class reach everything, and a caller with no type of its
    /// own gets `true`.
    public boolean reachesDescendants(Selector.PseudoClass state, String type) {
        Objects.requireNonNull(state, "state");
        if (untypedAncestorStates.contains(state)) {
            return true;
        }
        if (type == null) {
            // A node with no CSS type is a composition node or a bare painter:
            // the only compound that can name it is one that names no type
            // either, and every such compound is in the set just checked. So
            // there is no rule left that could reach through this node's state
            // -- which matters because the hover and active chains run to the
            // root through several of them, and treating those as unknown was
            // the whole tree re-resolving on every click (ADR-0149).
            return false;
        }
        var types = ancestorStates.get(state);
        return types != null && types.contains(type);
    }

    /// The declarations that apply to `element`, with `var()` resolved.
    ///
    /// @return property name to value tokens, in no particular order
    public Map<String, List<Token>> resolve(StyleElement element) {
        Objects.requireNonNull(element, "element");
        // One cascade, two readers. Custom properties are collected from the same
        // declarations the cascade produces, so asking for them separately ran it
        // twice for every element resolved (ADR-0152).
        var declared = cascade(element);
        var customProperties = customPropertiesFor(element, declared);

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
        return customPropertiesFor(element, null);
    }

    /// The same, when the caller has already cascaded `element` and would
    /// otherwise pay for it twice.
    ///
    /// @param ownCascade this element's winning declarations, or null to compute
    ///                   them here
    private Map<String, List<Token>> customPropertiesFor(
            StyleElement element, Map<String, List<Token>> ownCascade) {
        // Built from the root down so a nearer definition overwrites a farther
        // one. Recursion rather than a loop because the chain is walked upward
        // and applied downward.
        var parent = element.parent();
        var inherited = parent == null ? EMPTY : customPropertiesFor(parent, null);

        // **Cached against what the parent handed down, by identity.** Without
        // this the recursion above runs a full cascade at every level of the
        // tree, so one node at depth ten costs eleven of them -- which was the
        // largest term left in a frame after the rule index (ADR-0152).
        var cached = element.cachedCustomProperties(this, inherited);
        if (cached != null) {
            return cached;
        }

        var own = new LinkedHashMap<>(inherited);
        for (var entry : (ownCascade == null ? cascade(element) : ownCascade).entrySet()) {
            if (entry.getKey().startsWith("--")) {
                own.put(entry.getKey(), entry.getValue());
            }
        }
        // The parent's own map when this node declares none, so a chain of nodes
        // that define nothing shares one instance -- and a child's cache stays
        // valid through all of them, because the identity it is keyed on does not
        // change on the way down.
        var resolved = own.equals(inherited) ? inherited : Map.copyOf(own);
        element.cacheCustomProperties(this, inherited, resolved);
        return resolved;
    }

    /// The empty map every root starts from, as one instance — so a tree whose
    /// root declares no custom property still hands its children something with a
    /// stable identity.
    private static final Map<String, List<Token>> EMPTY = Map.of();

    /// The winning declaration for each property on `element`, before `var()`.
    private Map<String, List<Token>> cascade(StyleElement element) {
        var matches = new ArrayList<Match>();
        // Only the rules whose rightmost compound could name this element. The
        // order they come out in does not matter: every match carries its layer,
        // its specificity and the rule's own order, and the sort below is what
        // decides the winner (ADR-0152).
        for (var candidate : candidatesFor(element.type())) {
            var rule = candidate.rule();
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
                matches.add(new Match(declaration, candidate.layer(), best, rule.order()));
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
