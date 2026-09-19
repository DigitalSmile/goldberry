package io.github.digitalsmile.goldberry.css.lint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.TestElement;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.cascade.StyleResolver;
import io.github.digitalsmile.goldberry.css.value.CssLength;

/// What a stylesheet says that the engine will not do.
///
/// The assertions are about the **two ways a rule does nothing** and about the
/// two ways this could report a healthy sheet as broken — a `var()` it was not
/// given the theme for, and a custom property, which between them produced over
/// a hundred false findings each while the machinery this promotes was being
/// written as a test ([ADR-0215], [ADR-0216], [ADR-0257]).
class StyleLintTest {

    /// What `em` and `rem` resolve against. Any context will do here for the same
    /// reason [StyleLint]'s own does: nothing below is a length.
    private static final CssLength.Context CONTEXT = CssLength.Context.DEFAULT;

    private static Stylesheet sheet(String css) {
        return Stylesheet.parse(CascadeLayer.APPLICATION, css);
    }

    private static List<Finding> check(String css) {
        var parsed = sheet(css);
        return new StyleLint(List.of(parsed)).check(parsed);
    }

    private static List<Finding> dead(String css) {
        return check(css).stream()
                .filter(finding -> finding.kind() == Finding.Kind.DEAD_DECLARATION)
                .toList();
    }

    @Nested
    @DisplayName("a declaration the engine applies nothing from")
    class DeadDeclarations {

        @Test
        @DisplayName("a property the subset does not have is reported")
        void unsupportedProperty() {
            // `border-bottom` is the one that shipped in `table-head`, drew
            // nothing, and left one debug line among thousands (ADR-0215).
            var findings = dead("table-head { border-bottom: 1px solid red }");

            assertEquals(1, findings.size(), () -> findings.toString());
            assertEquals("border-bottom", findings.getFirst().property());
            assertEquals("table-head", findings.getFirst().selector());
        }

        @Test
        @DisplayName("a value the engine would not take is reported, with the property spelled right")
        void badValue() {
            // The other way a rule does nothing, and the one ADR-0216 found:
            // the property is one the engine has and the value is not one it
            // takes, so the declaration is dropped whole. `align-items: left` is
            // the recorded case -- refused for a reason rather than an omission,
            // because `left` is not `start` under RTL (ADR-0247).
            var findings = dead("row { align-items: left }");

            assertEquals(1, findings.size(), () -> findings.toString());
            assertEquals("align-items", findings.getFirst().property());
            assertEquals("left", findings.getFirst().value());
        }

        /// The declaration a lint that asks "what won?" cannot see.
        ///
        /// This resolved the probe and looked the property up in the result, so a
        /// declaration that **lost** the cascade was checked against the
        /// *winner's* value: `align-items: left` under a later `align-items:
        /// center` came back as `center`, which the engine takes, and the bad line
        /// was never reported. The comment saying an overridden rule reached the
        /// `winning == null` branch was simply false — the property is in the
        /// result either way, with somebody else's value in it.
        @Test
        @DisplayName("a declaration that lost the cascade is still reported, against its own value")
        void anOverriddenDeclarationIsCheckedOnItsOwnValue() {
            var findings = dead("""
                    row { align-items: left }
                    row { align-items: center }
                    """);

            assertEquals(1, findings.size(), () -> findings.toString());
            assertEquals("align-items", findings.getFirst().property());
            assertEquals("left", findings.getFirst().value());
            assertEquals(1, findings.getFirst().line(), "the line the bad declaration is written on");
        }

        /// The same fault the other way up, which is what the author actually saw:
        /// the winner's bad value reported twice, once at a line that reads
        /// `align-items: center` and is perfectly fine.
        @Test
        @DisplayName("and a good declaration under a bad one is not reported at the good one's line")
        void aGoodDeclarationUnderABadOneIsQuiet() {
            var findings = dead("""
                    row { align-items: center }
                    row { align-items: left }
                    """);

            assertEquals(1, findings.size(), () -> findings.toString());
            assertEquals(2, findings.getFirst().line());
            assertEquals("left", findings.getFirst().value());
        }

        @Test
        @DisplayName("a declaration the engine applies is not reported")
        void goodDeclarationsAreQuiet() {
            assertTrue(dead("button { color: #ff0000; padding: 4px 8px; white-space: nowrap }")
                    .isEmpty());
        }

        /// The check that keeps this from being a copy of the switch. A property
        /// added to the engine needs no edit here, and one whose value parser
        /// gains a form is right the same day.
        @Test
        @DisplayName("a value that only became legal recently is legal here too")
        void itAsksTheEngineRatherThanAList() {
            // Both of these were reported as dead until the engine grew them:
            // the corner shorthand (ADR-0216) and `start` (ADR-0247).
            assertTrue(dead("group-box-title { border-radius: 7px 7px 0 0 }").isEmpty());
            assertTrue(dead("row { align-items: start }").isEmpty());
        }

        @Test
        @DisplayName("a custom property is the resolver's and is never a finding")
        void customPropertiesAreNotFindings() {
            // They reach the same branch as an unsupported property and are not a
            // fault: every theme is nothing but these, so counting them reported
            // a hundred and fifty-eight failures against a healthy tree.
            assertTrue(
                    dead(":root { --gb-accent: #88c0d0; --anything-at-all: 3 }").isEmpty());
        }

        @Test
        @DisplayName("a rule with two selectors is checked under both")
        void everySelectorIsProbed() {
            // `.a, .b { … }` is one rule, and a declaration only reaches the
            // engine through an element that matches — so a rule whose *second*
            // selector is the live one would go unchecked if only the first were
            // built.
            var findings = dead("button, table-head { border-bottom: 1px solid red }");

            assertEquals(2, findings.size(), () -> findings.toString());
            assertEquals(
                    List.of("button", "table-head"),
                    findings.stream().map(Finding::selector).sorted().toList());
        }

        @Test
        @DisplayName("a finding says where it was written")
        void findingsCarryTheirPosition() {
            var findings = dead("button {\n  color: red;\n  border-bottom: 1px solid red;\n}");

            assertEquals(1, findings.size());
            assertEquals(3, findings.getFirst().line(), "the line the parser saw it at");
            assertFalse(findings.getFirst().position().isEmpty());
        }
    }

    @Nested
    @DisplayName("the two sheets, which are not ceremony")
    class InForceAgainstLinted {

        private static final String THEME = ":root { --gb-accent: #88c0d0 }";

        @Test
        @DisplayName("a var() resolved against the theme is a value the engine takes")
        void varsResolveAgainstWhatIsInForce() {
            var mine = sheet("button { color: var(--gb-accent) }");
            var theme = sheet(THEME);

            var findings = new StyleLint(List.of(theme, mine)).check(mine);

            assertTrue(
                    findings.stream().noneMatch(f -> f.kind() == Finding.Kind.DEAD_DECLARATION),
                    () -> findings.toString());
        }

        /// **An unresolvable `var()` is not a finding**, and that is a decision
        /// rather than a gap. Substitution failing takes the whole declaration
        /// with it before the engine ever sees one, and the resolver already
        /// says so once — which is the shape ADR-0243 settled on after the same
        /// message became a stream. Two mechanisms for one fault disagree the
        /// day either changes.
        ///
        /// Asserted so that a later reader finds the reason rather than the
        /// silence.
        @Test
        @DisplayName("a var() nothing defines is the resolver's report and not a finding here")
        void varsWithoutAThemeAreTheResolversReport() {
            var mine = sheet("button { color: var(--gb-accent) }");

            var findings = new StyleLint(List.of(mine)).check(mine);

            assertTrue(
                    findings.stream().noneMatch(f -> f.kind() == Finding.Kind.DEAD_DECLARATION),
                    () -> findings.toString());
        }

        @Test
        @DisplayName("only what was asked about is reported")
        void inForceIsNotLinted() {
            var broken = sheet("table-head { border-bottom: 1px solid red }");
            var mine = sheet("button { color: #ff0000 }");

            var findings = new StyleLint(List.of(broken, mine)).check(mine);

            assertTrue(findings.isEmpty(), () -> "someone else's sheet is not this one's problem: " + findings);
        }
    }

    @Nested
    @DisplayName("a rule that could name a type and does not")
    class UntypedRules {

        private static List<Finding> untyped(String css) {
            return check(css).stream()
                    .filter(finding -> finding.kind() == Finding.Kind.UNTYPED_RULE)
                    .toList();
        }

        @Test
        @DisplayName("an all-classes rule is reported")
        void classOnlyRules() {
            var findings = untyped(".sidebar { padding: 8px }");

            assertEquals(1, findings.size(), () -> findings.toString());
            assertEquals(".sidebar", findings.getFirst().selector());
        }

        @Test
        @DisplayName("a rule that names a type is not")
        void typedRules() {
            assertTrue(untyped("button.primary { padding: 8px }").isEmpty());
        }

        /// It is about the cascade's cost rather than about the drawing, which is
        /// the difference an application needs when it decides whether to fail a
        /// build on a finding.
        @Test
        @DisplayName("it is not a defect, and a dead declaration is")
        void onlyOneOfTheTwoIsADefect() {
            assertFalse(Finding.Kind.UNTYPED_RULE.isDefect());
            assertTrue(Finding.Kind.DEAD_DECLARATION.isDefect());
        }

        @Test
        @DisplayName("the toolkit's own untyped rules are not reported against an application's sheet")
        void anotherSheetsUntypedRulesAreItsOwn() {
            var theirs = sheet(".theirs { padding: 8px }");
            var mine = sheet("button { padding: 8px }");

            assertTrue(new StyleLint(List.of(theirs, mine)).check(mine).isEmpty());
        }
    }

    @Nested
    @DisplayName("the shape of it")
    class Shape {

        @Test
        @DisplayName("an empty sheet has nothing wrong with it")
        void emptySheet() {
            assertTrue(check("").isEmpty());
        }

        @Test
        @DisplayName("a finding reads as something an author could act on")
        void findingsAreLegible() {
            var finding = dead("table-head { border-bottom: 1px solid red }").getFirst();

            var text = finding.toString();
            assertTrue(text.contains("table-head"), text);
            assertTrue(text.contains("border-bottom"), text);
            assertTrue(text.contains("1px solid red"), text);
        }

        @Test
        @DisplayName("a position that is not known prints as nothing rather than as 0:0")
        void unknownPositions() {
            var finding = untypedFinding();

            assertEquals("", finding.position());
            assertFalse(finding.toString().contains("0:0"));
        }

        private static Finding untypedFinding() {
            return check(".sidebar { padding: 8px }").stream()
                    .filter(f -> f.kind() == Finding.Kind.UNTYPED_RULE)
                    .findFirst()
                    .orElseThrow();
        }
    }

    /// A root nothing gives a colour, and every primitive under it
    /// ([ADR-0415]).
    ///
    /// The premise is asserted first, because the whole check rests on it: a bare
    /// `text` with no ancestor setting `color` really does draw in the initial
    /// black, which is ADR-0066's deliberate decision and a trap all the same.
    @Nested
    @DisplayName("a root with no colour")
    class UncolouredRoot {

        private static final String THEME = ":root { --gb-text: #eceff4 }";

        private static List<Finding> uncoloured(TestElement root, String... css) {
            var sheets = java.util.Arrays.stream(css).map(StyleLintTest::sheet).toList();
            return new StyleLint(sheets).uncolouredRoot(root).stream().toList();
        }

        /// The trap, stated as an assertion rather than as prose: a `text`
        /// inherits a colour, nothing hands it one, and the initial value is
        /// black. On the dark theme that is a label nobody can read, and on a
        /// light one it is perfectly fine — which is why the *resolved* colour
        /// cannot be what a check looks at.
        @Test
        @DisplayName("a bare text under an uncoloured root really does resolve to the initial black")
        void thePremise() {
            var resolver = new StyleResolver(List.of(sheet(THEME)));
            var root = TestElement.element(".app");
            var label = TestElement.element("text");
            root.with(label);

            var rootStyle = ComputedStyle.of(resolver.resolve(root), CONTEXT, null);
            var labelStyle = ComputedStyle.of(resolver.resolve(label), CONTEXT, rootStyle);

            assertEquals(ComputedStyle.INITIAL.color(), labelStyle.color(), "nothing set a colour anywhere");
        }

        @Test
        @DisplayName("is reported when nothing in force sets one")
        void reportedWhenNothingSetsOne() {
            var findings = uncoloured(TestElement.element(".app"), THEME, "text { font-size: 13px }");

            assertEquals(1, findings.size(), () -> findings.toString());
            assertEquals(Finding.Kind.UNCOLOURED_ROOT, findings.getFirst().kind());
            assertEquals("color", findings.getFirst().property());
            assertTrue(findings.getFirst().kind().isDefect(), "unreadable text is a defect, not a cost");
        }

        /// **The showcase's own shape, and the reason this takes an element.** The
        /// one application in the repository that does this right writes
        /// `#root { color: var(--gb-text) }` — an *id* selector. A check that
        /// resolved a synthetic `:root` probe would have reported the reference
        /// application as the defect on the day it was written.
        @Test
        @DisplayName("is not reported when an id rule sets one, which is how the showcase does it")
        void anIdRuleCounts() {
            var root = TestElement.element("panel#root");

            assertTrue(
                    uncoloured(root, THEME, "#root { color: var(--gb-text) }").isEmpty());
        }

        @Test
        @DisplayName("nor when a type rule or a :root rule does")
        void theOtherTwoSpellingsCount() {
            assertTrue(uncoloured(TestElement.element("window"), THEME, "window { color: var(--gb-text) }")
                    .isEmpty());
            assertTrue(uncoloured(TestElement.element(".app"), THEME, ":root { color: var(--gb-text) }")
                    .isEmpty());
        }

        /// A `var()` naming nothing takes the declaration with it before the
        /// engine sees one, so the root has no colour and saying so is the right
        /// answer rather than a gap — the author wrote a rule and got nothing.
        @Test
        @DisplayName("a colour whose var() resolves to nothing is no colour")
        void anUnresolvableColourIsNoColour() {
            var findings = uncoloured(TestElement.element(".app"), "#root { color: var(--nobody-defines-this) }");

            assertEquals(1, findings.size(), () -> findings.toString());
        }

        @Test
        @DisplayName("the finding names the root the way a selector would, so it says what to write")
        void theFindingNamesTheRoot() {
            var byId = uncoloured(TestElement.element("panel#root"), THEME);
            var byType = uncoloured(TestElement.element("window"), THEME);
            var neither = uncoloured(TestElement.element(".app"), THEME);

            assertEquals("#root", byId.getFirst().selector());
            assertEquals("window", byType.getFirst().selector());
            assertEquals(":root", neither.getFirst().selector(), "a root with no type and no id has one name");
            assertTrue(
                    byId.getFirst().toString().contains("--gb-text"),
                    byId.getFirst().toString());
        }

        /// Asking about a node that is not a root would answer a question nobody
        /// asked: what a child inherits is its parent's business, and a check that
        /// quietly reported "no colour" for every uncoloured node in a tree is the
        /// per-node diagnostic this deliberately is not.
        @Test
        @DisplayName("a node with a parent is refused rather than answered")
        void onlyARootIsAnswered() {
            var root = TestElement.element(".app");
            var child = TestElement.element("text");
            root.with(child);
            var lint = new StyleLint(List.of(sheet(THEME)));

            var thrown = assertThrows(IllegalArgumentException.class, () -> lint.uncolouredRoot(child));
            assertTrue(thrown.getMessage().contains("not a root"), thrown.getMessage());
        }
    }

    /// The probe's null parent, which this package was left unmarked for
    /// ([ADR-0413]).
    ///
    /// `Probe` is a record whose `parent` component was declared non-null and is
    /// **null for every single-compound selector** — that is, for most rules in
    /// any sheet. It had to be, because a null parent is how [StyleLint] makes the
    /// leftmost probe the root, and the root is what `:root`'s custom properties
    /// hang off. So the one thing the package could not say was the one thing it
    /// depended on.
    @Nested
    @DisplayName("the probe, and the root it needs to be")
    class TheProbe {

        @Test
        @DisplayName("a one-compound selector's probe is the root, so :root reaches it")
        void aSingleCompoundProbeIsTheRoot() {
            // If the probe were not the root, `--gb-accent` would resolve to
            // nothing, substitution would take the declaration with it, and this
            // would be quiet for the wrong reason — so the assertion below is
            // paired with a sheet whose var() is genuinely undefined.
            var theme = sheet(":root { --gb-accent: #88c0d0 }");
            var mine = sheet("button { color: var(--gb-accent) }");

            assertTrue(
                    new StyleLint(List.of(theme, mine)).check(mine).isEmpty(),
                    "the probe matched :root and the colour resolved");
        }

        @Test
        @DisplayName("and so is the leftmost of a chain, three compounds deep")
        void theLeftmostOfAChainIsTheRoot() {
            // `probeFor` walks the parts backwards and hands each new probe the
            // one before it, so only the first gets null. A chain is where the
            // annotation matters least and the behaviour matters most: the theme
            // has to reach the *rightmost* probe through two links.
            var theme = sheet(":root { --gb-space: 4px }");
            var mine = sheet("card row button { padding: var(--gb-space) }");

            assertTrue(
                    new StyleLint(List.of(theme, mine)).check(mine).isEmpty(),
                    "the custom property reached the end of the chain");
        }
    }
}
