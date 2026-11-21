package dev.goldberry.html.style;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.Declaration;
import dev.goldberry.css.cascade.ScopedRules;
import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.select.Selector;
import dev.goldberry.css.select.SelectorMatcher;
import dev.goldberry.html.model.Element;
import dev.goldberry.html.model.HtmlDocument;
import dev.goldberry.html.model.HtmlNode;
import dev.goldberry.html.model.HtmlText;

/// A mail's `<style>` blocks, read: one [Rule] per selector that survived, filed
/// by what its subject names so an element tries only the rules that could match
/// it.
///
/// ## What a selector may be
///
/// What the toolkit's own parser reads — types, classes, ids, the combinators,
/// `:is()`, `:not()`, `:first-child`, `:nth-child()` — matched against the
/// document's own tree by [StyleNode]. Beyond that:
///
/// - `:link` is dropped from a selector (every anchor here has an `href`), and a
///   selector with `:visited` is dropped whole: nothing here has visited anything.
/// - `:hover`, `:active` and `:focus` are kept **on the subject**, as a state the
///   rule applies in. They are matched as the pointer moves, by the toolkit's
///   cascade, on the widget the element became.
/// - A selector the parser cannot read, a pseudo-element, and an attribute
///   selector are dropped, each on its own: `a, b::before` keeps `a`.
final class AuthorSheet {

    /// One selector of one rule, with what it declares.
    ///
    /// @param selector     what to match, with its state taken off the subject
    /// @param specificity  the selector's as written, state included
    /// @param order        where the rule stands among the document's rules
    /// @param state        the subject's state, or null for a rule that always applies
    /// @param declarations what survived [AuthorDeclarations]
    record Rule(
            Selector selector,
            int specificity,
            int order,
            Selector.@Nullable PseudoClass state,
            List<Declaration> declarations) {}

    /// No rules.
    static final AuthorSheet EMPTY = new AuthorSheet(List.of());

    private final List<Rule> rules;
    private final Map<String, List<Rule>> byClass = new HashMap<>();
    private final Map<String, List<Rule>> byId = new HashMap<>();
    private final Map<String, List<Rule>> byType = new HashMap<>();
    private final List<Rule> unfiled = new ArrayList<>();

    private AuthorSheet(List<Rule> rules) {
        this.rules = List.copyOf(rules);
        for (var rule : this.rules) {
            var key = rule.selector().key();
            if (!key.classes().isEmpty()) {
                byClass.computeIfAbsent(key.classes().getFirst(), _ -> new ArrayList<>())
                        .add(rule);
            } else if (key.id() != null) {
                byId.computeIfAbsent(key.id(), _ -> new ArrayList<>()).add(rule);
            } else if (key.type() != null) {
                byType.computeIfAbsent(key.type(), _ -> new ArrayList<>()).add(rule);
            } else {
                unfiled.add(rule);
            }
        }
    }

    /// Every `<style>` in `document`, in document order, read.
    static AuthorSheet of(HtmlDocument document) {
        var rules = new ArrayList<Rule>();
        collect(document.children(), rules);
        return rules.isEmpty() ? EMPTY : new AuthorSheet(rules);
    }

    /// The rules of one stylesheet's text, read.
    static AuthorSheet parse(String css) {
        var rules = new ArrayList<Rule>();
        read(css, rules);
        return rules.isEmpty() ? EMPTY : new AuthorSheet(rules);
    }

    boolean isEmpty() {
        return rules.isEmpty();
    }

    /// The rules whose selector matches `node`, in no particular order.
    List<Rule> matching(StyleNode node) {
        if (rules.isEmpty()) {
            return List.of();
        }
        var found = new ArrayList<Rule>();
        for (var name : node.classes()) {
            match(byClass.get(name), node, found);
        }
        var id = node.id();
        if (id != null) {
            match(byId.get(id), node, found);
        }
        match(byType.get(node.type()), node, found);
        match(unfiled, node, found);
        return found;
    }

    private static void match(@Nullable List<Rule> candidates, StyleNode node, List<Rule> found) {
        if (candidates == null) {
            return;
        }
        for (var rule : candidates) {
            if (!found.contains(rule)
                    && SelectorMatcher.matches(rule.selector(), node, SelectorMatcher.Reach.OWN_TREE)) {
                found.add(rule);
            }
        }
    }

    // --- reading ---------------------------------------------------------------

    private static void collect(List<HtmlNode> nodes, List<Rule> rules) {
        for (var node : nodes) {
            switch (node) {
                case Element element
                when "style".equals(element.tag()) -> {
                    if (forScreen(element.attribute("media"))) {
                        read(source(element), rules);
                    }
                }
                case Element element -> collect(element.children(), rules);
                case HtmlDocument nested -> collect(nested.children(), rules);
                default -> {}
            }
        }
    }

    /// A `<style>`'s text: its text children as written. Not [Element#rawText()],
    /// which leaves a stylesheet out on purpose because it is not content.
    private static String source(Element style) {
        var text = new StringBuilder();
        for (var child : style.children()) {
            if (child instanceof HtmlText(var written)) {
                text.append(written);
            }
        }
        return text.toString();
    }

    /// Whether a `<style media=…>` is for a screen: no attribute, `all` or
    /// `screen`. A `print` sheet is not, and a query is not read.
    private static boolean forScreen(@Nullable String media) {
        if (media == null || media.isBlank()) {
            return true;
        }
        var value = media.strip().toLowerCase(Locale.ROOT);
        return value.equals("all") || value.equals("screen");
    }

    private static void read(String css, List<Rule> rules) {
        for (var rule : StyleText.rules(css)) {
            var declarations = AuthorDeclarations.parse(rule.body());
            if (declarations.isEmpty()) {
                continue;
            }
            var order = rules.isEmpty() ? 0 : rules.getLast().order() + 1;
            for (var written : StyleText.selectors(rule.prelude())) {
                var selector = selector(written);
                if (selector != null) {
                    rules.add(rule(selector, order, declarations));
                }
            }
        }
    }

    /// `written` as the parser reads it, `:link` taken out, or null when it is
    /// refused.
    private static @Nullable Selector selector(String written) {
        var lower = written.toLowerCase(Locale.ROOT);
        if (lower.contains(":visited") || lower.contains("[") || lower.contains("::")) {
            return null;
        }
        var text = written.replaceAll("(?i):link\\b", "");
        if (text.isBlank()) {
            return null;
        }
        try {
            var parsed = CssParser.parse(text + "{}");
            if (parsed.size() != 1 || parsed.getFirst().selectors().size() != 1) {
                return null;
            }
            var selector = parsed.getFirst().selectors().getFirst();
            return selector.key().pseudoElement() == null ? selector : null;
        } catch (RuntimeException e) {
            // Written for some other client's CSS, or for none: dropped alone.
            return null;
        }
    }

    /// The rule for `selector`, with the state its subject names taken off it.
    private static Rule rule(Selector selector, int order, List<Declaration> declarations) {
        var key = selector.key();
        Selector.PseudoClass state = null;
        var kept = new ArrayList<Selector.PseudoClass>();
        for (var pseudo : key.pseudoClasses()) {
            if (state == null && ScopedRules.STATES.contains(pseudo)) {
                state = pseudo;
            } else {
                kept.add(pseudo);
            }
        }
        if (state == null) {
            return new Rule(selector, selector.specificity(), order, null, declarations);
        }
        var parts = new ArrayList<>(selector.parts());
        var subject = new Selector.Compound(
                key.type(), key.id(), key.classes(), kept, key.structural(), key.functional(), key.pseudoElement());
        parts.set(0, new Selector.Part(subject, parts.getFirst().combinator()));
        return new Rule(new Selector(parts), selector.specificity(), order, state, declarations);
    }
}
