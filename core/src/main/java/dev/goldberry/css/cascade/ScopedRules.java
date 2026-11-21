package dev.goldberry.css.cascade;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.Declaration;
import dev.goldberry.css.StyleRule;
import dev.goldberry.css.property.GlobalKeyword;
import dev.goldberry.css.select.Selector;

/// Rules that apply to **one subtree** and nothing outside it: what a
/// [dev.goldberry.widget.style.StyleScope] carries for the widgets under it.
///
/// ```java
/// var rules = ScopedRules.of(List.of(rule));    // `.html-author-1 { color: #c00 }`
/// new StyleScope(rules, page);
/// ```
///
/// The window's stylesheets are fixed for as long as a resolver lives, and they
/// are the application's. A document that brings styles of its own, a mail's
/// `<style>` and `style=`, needs somewhere to put them that reaches its own
/// widgets and cannot reach anyone else's. This is that place.
///
/// ## A scoped rule beats every normal declaration in the window's sheets
///
/// Whatever the specificity. The rules here are a document's own statement
/// about its own nodes, the author origin a browser puts above its default
/// sheet, so a `.html-author-3 { font-size: 30px }` wins over the toolkit's
/// `.html .html-h1` rule for the same heading. An `!important` declaration in
/// the window's sheets still wins: that is how an application insists.
/// Between two scoped rules the nearer scope wins, then specificity, then
/// source order.
///
/// ## Why the selectors are so plain
///
/// One compound per selector, a type, an id and classes, and a pseudo-class only
/// from [#STATES]. No combinator, no `:has()`, no `:nth-child()`, no
/// pseudo-element, no `@media` or `@container` condition, no `@starting-style`,
/// no custom property, no `!important` and no `revert`. Each refusal is a thing
/// the resolver indexes once per stylesheet set to know what to invalidate
/// (which ancestors' states reach below, which rules read siblings, which
/// conditions a resize can flip), and a subtree's rules arrive after that
/// index was built. A plain compound needs none of it: it matches the node
/// itself or not, and a node's own state change always restyles that node. It
/// also means a scoped rule cannot reach outside its subtree through an
/// ancestor selector, by construction.
///
/// A document's richer selectors are the document's to match: `goldberry-html`
/// matches a mail's `<style>` against the mail's own tree and hands this a class
/// per element.
///
/// **Compared by value**, so a scope whose rules did not change keeps every style
/// under it cached, and one whose rules did restyles its subtree.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#the-cascade-four-layers).
public final class ScopedRules {

    /// The pseudo-classes a scoped selector may carry: the ones input sets on a
    /// node, which restyle that node whenever they change.
    public static final Set<Selector.PseudoClass> STATES = Set.copyOf(EnumSet.of(
            Selector.PseudoClass.HOVER,
            Selector.PseudoClass.ACTIVE,
            Selector.PseudoClass.FOCUS,
            Selector.PseudoClass.FOCUS_VISIBLE));

    /// No rules: what a scope with nothing to say carries.
    public static final ScopedRules NONE = new ScopedRules(List.of());

    private final List<StyleRule> rules;

    /// Each rule filed under the first class, else the id, else the type of each
    /// of its selectors.
    private final Map<String, List<StyleRule>> byClass = new HashMap<>();

    private final Map<String, List<StyleRule>> byId = new HashMap<>();

    private final Map<String, List<StyleRule>> byType = new HashMap<>();

    /// Each rule's index in [#rules], by identity, for putting what the three
    /// maps found back into source order.
    private final IdentityHashMap<StyleRule, Integer> position = new IdentityHashMap<>();

    private ScopedRules(List<StyleRule> rules) {
        this.rules = List.copyOf(rules);
        for (var i = 0; i < this.rules.size(); i++) {
            var rule = this.rules.get(i);
            check(rule);
            position.put(rule, i);
            file(rule);
        }
    }

    /// `rules`, checked against the subset this class documents.
    ///
    /// @throws IllegalArgumentException for a rule outside it, naming what the
    ///         rule asked for
    public static ScopedRules of(List<StyleRule> rules) {
        Objects.requireNonNull(rules, "rules");
        return rules.isEmpty() ? NONE : new ScopedRules(rules);
    }

    /// The rules, in source order.
    public List<StyleRule> rules() {
        return rules;
    }

    /// Whether there is nothing here.
    public boolean isEmpty() {
        return rules.isEmpty();
    }

    /// The rules whose selectors could match a node with this `type`, `id` and
    /// `classes`, in source order, each once: what the resolver asks on every
    /// node under the scope, so a page of a thousand words and a hundred rules
    /// tries a handful per word rather than all of them.
    List<StyleRule> candidates(@Nullable String type, @Nullable String id, Set<String> classes) {
        if (rules.isEmpty()) {
            return List.of();
        }
        var found = new ArrayList<StyleRule>();
        for (var name : classes) {
            addAll(found, byClass.get(name));
        }
        if (id != null) {
            addAll(found, byId.get(id));
        }
        if (type != null) {
            addAll(found, byType.get(type));
        }
        if (found.size() > 1) {
            found.sort((a, b) -> Integer.compare(order(a), order(b)));
        }
        return found;
    }

    /// Where in source order `rule` is, for the cascade's tie-break.
    int order(StyleRule rule) {
        return position.getOrDefault(rule, 0);
    }

    private static void addAll(List<StyleRule> found, @Nullable List<StyleRule> filed) {
        if (filed == null) {
            return;
        }
        for (var rule : filed) {
            // A rule filed under two keys a node both carries -- `.a, .b` on
            // a node with both classes -- is one candidate, not two.
            if (!containsSame(found, rule)) {
                found.add(rule);
            }
        }
    }

    private static boolean containsSame(List<StyleRule> found, StyleRule rule) {
        for (var candidate : found) {
            if (candidate == rule) {
                return true;
            }
        }
        return false;
    }

    private void file(StyleRule rule) {
        for (var selector : rule.selectors()) {
            var compound = selector.key();
            var list = !compound.classes().isEmpty()
                    ? byClass.computeIfAbsent(compound.classes().getFirst(), _ -> new ArrayList<>())
                    : compound.id() != null
                            ? byId.computeIfAbsent(compound.id(), _ -> new ArrayList<>())
                            : byType.computeIfAbsent(Objects.requireNonNull(compound.type()), _ -> new ArrayList<>());
            if (!containsSame(list, rule)) {
                list.add(rule);
            }
        }
    }

    // --- the subset -------------------------------------------------------------

    private static void check(StyleRule rule) {
        if (rule.starting()) {
            throw refused(rule, "an @starting-style rule");
        }
        if (rule.isConditional()) {
            throw refused(rule, "an @media condition");
        }
        if (rule.isContained()) {
            throw refused(rule, "an @container condition");
        }
        if (rule.layer() != null) {
            throw refused(rule, "an @layer");
        }
        for (var selector : rule.selectors()) {
            check(rule, selector);
        }
        for (var declaration : rule.declarations()) {
            check(rule, declaration);
        }
    }

    private static void check(StyleRule rule, Selector selector) {
        if (selector.parts().size() != 1) {
            throw refused(rule, "a combinator, in " + selector);
        }
        var compound = selector.key();
        if (compound.pseudoElement() != null) {
            throw refused(rule, "a pseudo-element, in " + selector);
        }
        if (!compound.structural().isEmpty() || !compound.functional().isEmpty()) {
            throw refused(rule, "a structural or functional pseudo-class, in " + selector);
        }
        for (var state : compound.pseudoClasses()) {
            if (!STATES.contains(state)) {
                throw refused(rule, ":" + state.cssName() + ", in " + selector);
            }
        }
        if (compound.classes().isEmpty() && compound.id() == null && compound.type() == null) {
            throw refused(rule, "a selector naming no type, id or class, " + selector);
        }
    }

    private static void check(StyleRule rule, Declaration declaration) {
        if (declaration.isCustomProperty()) {
            throw refused(rule, "a custom property, " + declaration.property());
        }
        if (declaration.important()) {
            throw refused(rule, "!important, on " + declaration.property());
        }
        var keyword = GlobalKeyword.of(declaration.value());
        if (keyword == GlobalKeyword.REVERT || keyword == GlobalKeyword.REVERT_LAYER) {
            throw refused(rule, keyword.cssName() + ", on " + declaration.property());
        }
    }

    private static IllegalArgumentException refused(StyleRule rule, String what) {
        return new IllegalArgumentException("a scoped rule cannot hold " + what + ": " + rule);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ScopedRules that && rules.equals(that.rules);
    }

    @Override
    public int hashCode() {
        return rules.hashCode();
    }

    @Override
    public String toString() {
        return "ScopedRules" + rules;
    }
}
