package io.github.digitalsmile.goldberry.css.lint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.StyleElement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.StyleResolver;
import io.github.digitalsmile.goldberry.css.parse.Token;
import io.github.digitalsmile.goldberry.css.select.Selector;
import io.github.digitalsmile.goldberry.css.value.CssLength;

/// Asks a stylesheet whether the engine will do what it says.
///
/// ## Why this is asked and not logged
///
/// §8's subset is deliberately small and an unsupported declaration is
/// deliberately **not** an error: naming `backdrop-filter` before it exists must
/// not stop a window opening. (That example was `box-shadow` until ADR-0310 built
/// it.) So the engine logs and carries on — which is right, and
/// is why `border-bottom`, `currentColor`, `margin` and `max-width` were each
/// written into the toolkit's own sheets, silently discarded, and found by
/// looking at a picture.
///
/// The fix for the toolkit's own sheets was a test ([ADR-0215], [ADR-0216]), and
/// it worked; the fix that was *not* available was making the log louder, because
/// a dropped value already warned and `group-box-title` drew square corners for
/// months anyway. An application writing its own stylesheet has had neither. This
/// is the test's machinery with the test taken off it ([ADR-0257]).
///
/// ```java
/// var findings = new StyleLint(sheets).check(mine);
/// findings.forEach(f -> LOG.warn("{}", f));
/// ```
///
/// ## What it asks, and how it cannot drift
///
/// Every rule is resolved through the **real** cascade and every declaration
/// handed to the **real** [ComputedStyle]. There is no list of supported
/// properties here to fall out of step with the engine: a property added
/// tomorrow needs no edit, and one removed is found the same day. That was the
/// rule the test was written under and it is the reason this is worth promoting
/// rather than reimplementing.
///
/// ## Two sheets, not one
///
/// The sheets **in force** and the sheets **under scrutiny** are separate
/// arguments, and it is not ceremony. Half of what a declaration means is what
/// its `var()`s stood for: every colour in the toolkit is `var(--gb-something)`,
/// substitution is the resolver's, and a sheet linted without its theme reports
/// every one of them as a value the engine would not take. So an application
/// passes everything that will be loaded as `inForce` and its own as `linted`.
///
/// Cheap enough to run at start-up — one resolution per selector, over a
/// stylesheet — and nothing calls it for you, which is the point.
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

    /// Every declaration in `rule` that the engine applies nothing from, as seen
    /// through `selector`.
    ///
    /// **Per selector**, because `.a, .b { … }` is one rule with two selectors and
    /// a declaration only ever reaches the engine through an element that
    /// matches — so a rule whose second selector is the live one would go
    /// unchecked if only the first were built.
    private void checkRule(Selector selector, io.github.digitalsmile.goldberry.css.StyleRule rule, List<Finding> into) {

        var resolved = resolver.resolve(probeFor(selector));
        for (var declaration : rule.declarations()) {
            if (declaration.isCustomProperty()) {
                // The resolver's, consumed for `var()` substitution before the
                // engine sees a declaration (ADR-0049). Every theme is nothing
                // but these, so counting them would report a hundred and fifty
                // findings against a healthy tree.
                continue;
            }
            var winning = resolved.get(declaration.property());
            if (winning == null) {
                // Two ways to get here and neither is this lint's to report.
                //
                // Either **another rule won** for this property -- in which case
                // what the engine did with *this* declaration is not what it did
                // at all, and the rule that won is checked under its own
                // selector, which is where the finding belongs.
                //
                // Or **substitution failed**: a `var()` naming a token nothing
                // defines takes the whole declaration with it. That is already
                // reported, once, by the resolver -- which is the shape ADR-0243
                // settled on after the same message became a stream -- and
                // saying it again here would be a second mechanism for one
                // fault, disagreeing with the first the day either changes.
                continue;
            }
            if (!ComputedStyle.applies(declaration.property(), winning, CONTEXT)) {
                into.add(new Finding(
                        Finding.Kind.DEAD_DECLARATION,
                        selector.toString(),
                        declaration.property(),
                        text(winning),
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

    private record Probe(Selector.Compound compound, StyleElement parent) implements StyleElement {

        @Override
        public String type() {
            return compound.type();
        }

        // Unannotated, matching `StyleElement` and `Selector.Compound`, both of
        // which document a null `type` and `id` and declare neither `@Nullable`.
        // That disagreement is older than this class and wider than it: making
        // either honest would move every implementation and every caller.
        @Override
        public String id() {
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
