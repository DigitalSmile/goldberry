package io.github.digitalsmile.goldberry.css.lint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;

/// What a stylesheet says that the engine will not do.
///
/// The assertions are about the **two ways a rule does nothing** and about the
/// two ways this could report a healthy sheet as broken — a `var()` it was not
/// given the theme for, and a custom property, which between them produced over
/// a hundred false findings each while the machinery this promotes was being
/// written as a test ([ADR-0215], [ADR-0216], [ADR-0257]).
class StyleLintTest {

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
        @DisplayName("neither argument may be null")
        void nullsAreRefused() {
            assertThrows(NullPointerException.class, () -> new StyleLint(null));
            assertThrows(NullPointerException.class, () -> new StyleLint(List.of()).check((List<Stylesheet>) null));
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
}
