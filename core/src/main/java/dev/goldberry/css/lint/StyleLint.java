package dev.goldberry.css.lint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.StyleElement;
import dev.goldberry.css.StyleRule;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.select.Selector;
import dev.goldberry.css.value.CssLength;

/// Asks a stylesheet whether the engine will do what it says.
///
/// ```java
/// var findings = new StyleLint(sheets).check(mine);
/// findings.forEach(f -> LOG.warn("{}", f));
/// ```
///
/// The CSS subset is small on purpose and an unsupported declaration is not an
/// error: naming `backdrop-filter` before it exists must not stop a window
/// opening, so the engine logs the dropped declaration and carries on. That is
/// right for a stylesheet, and wrong as the only signal an author ever gets,
/// because a warning in a log nobody is watching is not seen at any level. This
/// is the check an application asks for instead, and its answer is a list of
/// [Finding] values.
///
/// Every rule is resolved through the real cascade and every declaration handed
/// to the real [ComputedStyle]. There is no list of supported properties here to
/// fall out of step with the engine: a property added tomorrow needs no edit,
/// and one removed is found the same day.
///
/// The sheets in force and the sheets under scrutiny are separate arguments, and
/// it is not ceremony. Half of what a declaration means is what its `var()`s
/// stood for: every colour in the toolkit is `var(--gb-something)`, substitution
/// is the resolver's, and a sheet linted without its theme reports every one of
/// them as a value the engine would not take. So an application passes
/// everything that will be loaded as `inForce` and its own as `linted`.
///
/// Cheap enough to run at start-up, one resolution per selector, and nothing
/// calls it for you.
///
/// Read more:
/// [Logging and diagnostics](https://goldberry.dev/docs/guide/logging.html#failure-messages-and-what-they-mean).
public final class StyleLint {

    /// What `em` and `rem` resolve against.
    ///
    /// Any context will do. The question is whether the *declaration* is one the
    /// engine applies, and a length that resolves to a different number is
    /// applied either way; a length that will not resolve at all fails under
    /// every context.
    private static final CssLength.Context CONTEXT = CssLength.Context.DEFAULT;

    private final List<Stylesheet> inForce;
    private final StyleResolver resolver;

    /// A lint over what will actually be loaded.
    ///
    /// @param inForce every sheet that will be in force — the theme included, or
    ///                every `var()` is reported as a value the engine refuses
    public StyleLint(List<Stylesheet> inForce) {
        this.inForce = List.copyOf(Objects.requireNonNull(inForce, "inForce"));
        this.resolver = new StyleResolver(this.inForce);
    }

    /// Everything wrong with `linted`, in the order the rules are written.
    ///
    /// `linted` is usually a subset of what was passed to the constructor —
    /// an application's own sheet, checked against the toolkit's and the theme's
    /// rather than alongside them.
    public List<Finding> check(List<Stylesheet> linted) {
        Objects.requireNonNull(linted, "linted");
        var findings = new ArrayList<Finding>();
        for (var sheet : linted) {
            for (var rule : sheet.rules()) {
                for (var selector : rule.selectors()) {
                    checkRule(selector, rule, findings);
                }
            }
        }
        findings.addAll(untypedRules(linted));
        return List.copyOf(findings);
    }

    /// The same, for the one sheet an application most often has.
    public List<Finding> check(Stylesheet linted) {
        return check(List.of(Objects.requireNonNull(linted, "linted")));
    }

    /// Whether the sheets in force leave `root`, and so every primitive under it
    /// that inherits a colour, with no `color` at all.
    ///
    /// ```java
    /// new StyleLint(everythingLoaded).uncolouredRoot(tree.root()).ifPresent(f -> LOG.warn("{}", f));
    /// ```
    ///
    /// Asked about the root and not about the text, because the resolved colour
    /// is not evidence. The initial `color` is black on purpose, and black text on
    /// a light theme is correct, so a check that fired on a node resolving to
    /// black would be wrong about every light-themed application there is, once
    /// per text node per frame. What is evidence is that no declaration anywhere
    /// set one. `color` inherits, so one rule on the root settles the whole tree;
    /// a root with no `color` in its cascade means there was nothing to inherit,
    /// all the way down. That is one question, asked once, about one node.
    ///
    /// It takes the root rather than looking for `:root` because an application
    /// that does this right need not write `:root`: the showcase writes
    /// `#root { color: var(--gb-text) }`, and a synthetic `:root` probe would
    /// report the one correct example as the defect. A root is whatever the
    /// tree's root element is, its selector is the application's business, and
    /// the only thing that can answer whether a rule reaches it is the element
    /// itself.
    ///
    /// Nothing calls this for you: it is a value an application asks for at
    /// start-up, beside [dev.goldberry.css.contrast.ThemeAudit#failures], and not
    /// a line in a log nobody reads. The rule it holds a root to is under
    /// [Inheritance](https://goldberry.dev/docs/guide/styling.html#inheritance).
    ///
    /// @param root the tree's root element — the one whose [StyleElement#parent]
    ///             is null
    /// @return the finding, or empty when something gives the root a colour
    /// @throws IllegalArgumentException if `root` has a parent, which would make
    ///         the answer a fact about a subtree and quietly wrong
    public Optional<Finding> uncolouredRoot(StyleElement root) {
        Objects.requireNonNull(root, "root");
        if (root.parent() != null) {
            throw new IllegalArgumentException("not a root: " + name(root) + " has a parent, and what it"
                    + " inherits is that parent's business rather than the sheets'");
        }
        // The declared cascade rather than a `ComputedStyle`, and that is the
        // whole mechanism. A resolved style always has a colour -- the initial one
        // if nothing else -- so asking it can only ever report the value, never
        // whether anybody chose it. The map the cascade produces has a `color` key
        // if and only if a declaration won one.
        //
        // A `color: var(--nothing)` is absent here too, because substitution
        // failing takes the declaration with it. That is the right answer rather
        // than a gap: a root whose colour resolves to nothing has no colour.
        if (resolver.resolve(root).containsKey("color")) {
            return Optional.empty();
        }
        return Optional.of(new Finding(Finding.Kind.UNCOLOURED_ROOT, name(root), "color", null, 0, 0));
    }

    /// How a selector would name `root`, so the finding tells the author what to
    /// write rather than only what is missing.
    ///
    /// `:root` is the fallback rather than the first choice: it is what a root
    /// with no type and no id has to be selected by, and an application that has
    /// given its root either would rather be told about the one it chose.
    private static String name(StyleElement root) {
        var id = root.id();
        if (id != null) {
            return "#" + id;
        }
        var type = root.type();
        return type == null ? ":root" : type;
    }

    /// Every declaration in `rule` that the engine applies nothing from, as seen
    /// through `selector`.
    ///
    /// **Per selector**, because `.a, .b { … }` is one rule with two selectors and
    /// a declaration only ever reaches the engine through an element that
    /// matches — so a rule whose second selector is the live one would go
    /// unchecked if only the first were built.
    ///
    /// **Each declaration against its own value**, and not against the cascade's
    /// winner for its property. Looking the property up in [StyleResolver#resolve]
    /// answers a different question: a declaration that lost would be checked
    /// against the winning rule's value and reported at the loser's line, so a
    /// rule overridden anywhere in the sheets in force could be as wrong as it
    /// liked and never be reported. What a lint wants is what the author wrote,
    /// with its `var()`s resolved as they would be here, which is
    /// [StyleResolver#substitutedFor].
    private void checkRule(Selector selector, StyleRule rule, List<Finding> into) {
        var probe = probeFor(selector);
        for (var declaration : rule.declarations()) {
            if (declaration.isCustomProperty()) {
                // The resolver's, consumed for `var()` substitution before the
                // engine sees a declaration. Every theme is nothing but these, so
                // counting them would report a hundred and fifty findings against
                // a healthy tree.
                continue;
            }
            var value = resolver.substitutedFor(probe, declaration.value());
            if (value == null) {
                // Substitution failed: a `var()` naming a token nothing defines
                // takes the whole declaration with it. The resolver already reports
                // that, once per property and element type, and saying it again
                // here would be a second mechanism for one fault, disagreeing with
                // the first the day either changes.
                continue;
            }
            if (!ComputedStyle.applies(declaration.property(), value, CONTEXT)) {
                into.add(new Finding(
                        Finding.Kind.DEAD_DECLARATION,
                        selector.toString(),
                        declaration.property(),
                        text(value),
                        declaration.line(),
                        declaration.column()));
            }
        }
    }

    /// [Finding.Kind#UNTYPED_RULE] for every selector in `linted` that names no
    /// type.
    ///
    /// Asked of a resolver built over **`linted` alone**, because the answer is a
    /// fact about those rules and a resolver over everything in force would
    /// report the toolkit's eight as the application's.
    private static List<Finding> untypedRules(List<Stylesheet> linted) {
        return new StyleResolver(linted)
                .untypedSelectors().stream()
                        .map(selector -> new Finding(Finding.Kind.UNTYPED_RULE, selector, null, null, 0, 0))
                        .toList();
    }

    /// An element that exists only to make one rule apply.
    ///
    /// The **resolver** is what is needed and not just [ComputedStyle]: a raw
    /// declaration handed straight to the engine still has its `var()` in it, and
    /// a `var()` is not a value the engine has ever been asked to understand.
    ///
    /// One probe per compound of the selector, chained by [Probe#parent], so
    /// `select text-input` is a `text-input` inside a `select`. Both combinators
    /// are satisfied by a direct parent, and the leftmost probe has none — which
    /// is what makes it the root, and is how the theme's custom properties reach
    /// the rest of the chain.
    private static StyleElement probeFor(Selector selector) {
        StyleElement element = null;
        // `parts` is rightmost first, so walking it backwards builds the chain
        // from the root down and ends holding the element the rule is *about*.
        var parts = selector.parts();
        for (var i = parts.size() - 1; i >= 0; i--) {
            element = new Probe(parts.get(i).compound(), element);
        }
        // A selector always has at least one part, so this is never null -- and
        // saying so here is cheaper than making every caller wonder.
        return Objects.requireNonNull(element, "a selector with no parts");
    }

    /// @param parent the probe to the left, or **null** for the leftmost — which
    ///               is what makes it the root, so `:root`'s custom properties
    ///               reach the rest of the chain
    private record Probe(
            Selector.Compound compound, @Nullable StyleElement parent) implements StyleElement {

        @Override
        public @Nullable String type() {
            return compound.type();
        }

        @Override
        public @Nullable String id() {
            return compound.id();
        }

        @Override
        public Set<String> classes() {
            return Set.copyOf(compound.classes());
        }

        @Override
        public boolean hasState(Selector.PseudoClass state) {
            return compound.pseudoClasses().contains(state);
        }
    }

    /// A declaration's value as an author would recognise it.
    ///
    /// [Token#cssText()] rather than [Token#text()], which is the difference
    /// between `1px solid red` and `1 solid red`: `text` is the token's own
    /// spelling and a dimension keeps its unit somewhere else. A finding whose
    /// value does not match what is in the file is a finding the author has to
    /// go and check.
    private static String text(List<Token> value) {
        var out = new StringBuilder();
        for (var token : value) {
            out.append(token.cssText());
        }
        return out.toString().trim();
    }
}
