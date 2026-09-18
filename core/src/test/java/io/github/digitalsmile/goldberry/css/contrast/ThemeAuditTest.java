package io.github.digitalsmile.goldberry.css.contrast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;

/// §1.2's floors, measured against a theme the toolkit has never seen
/// ([ADR-0241]).
///
/// `ContrastTest` in `:widgets` is the other half and a different question: it
/// resolves **widgets** through the real cascade and asks what a reader receives.
/// This asks what a *theme* declares, which is the only version of the question
/// an application swapping tokens can act on — and it is in `:core` because a
/// theme is, and because an application should not have to depend on the widget
/// catalog to find out its colours are unreadable.
class ThemeAuditTest {

    /// A theme with one deliberately unreadable pair, layered over a real one so
    /// everything it does not mention still resolves.
    ///
    /// `--gb-badge-warning-text` on `--gb-badge-warning-bg` is the example the
    /// `TODO.md` entry used by name, so it is the one used here.
    private static List<Stylesheet> withUnreadableBadge() {
        return List.of(Theme.NORD_DARK.load(), Stylesheet.parse(CascadeLayer.APPLICATION, """
                        :root {
                          --gb-badge-warning-bg: #ebcb8b;
                          --gb-badge-warning-text: #ffffff;
                        }
                        """));
    }

    @Nested
    @DisplayName("the themes the toolkit ships")
    class Shipped {

        @Test
        @DisplayName("every pair either theme declares clears §1.2's floor")
        void bothThemesPass() {
            for (var theme : List.of(Theme.NORD_DARK, Theme.NORD_LIGHT)) {
                assertEquals(
                        List.of(),
                        ThemeAudit.failures(List.of(theme.load())).stream()
                                .map(ContrastFinding::describe)
                                .toList(),
                        () -> theme + " declares a pair below §1.2's floor");
            }
        }

        /// A count rather than a list, because the list is the theme's business
        /// and the *number* is this test's: an audit that quietly stopped finding
        /// pairs — a renamed token, a convention that stopped matching, a
        /// resolver that returned nothing — would otherwise pass by measuring
        /// nothing at all, which is the failure mode a sweep has.
        @Test
        @DisplayName("and there are seventeen of them, so the sweep is not measuring nothing")
        void findsThePairs() {
            for (var theme : List.of(Theme.NORD_DARK, Theme.NORD_LIGHT)) {
                assertEquals(17, ThemeAudit.audit(List.of(theme.load())).size(), () -> theme + " pair count");
            }
        }

        /// The pairs are found by convention, and this is the convention working
        /// on a token nobody wrote it for: `--gb-badge-warning-bg` and
        /// `--gb-badge-warning-text` are paired because of their names.
        @Test
        @DisplayName("a variant pair is found by its name, not by a list")
        void conventionFindsVariants() {
            var found = ThemeAudit.audit(List.of(Theme.NORD_DARK.load())).stream()
                    .map(ContrastFinding::background)
                    .toList();

            assertTrue(found.contains("--gb-badge-warning-bg"), () -> "found " + found);
            assertTrue(found.contains("--gb-button-danger-bg"), () -> "found " + found);
        }

        /// `--gb-bg` ends in `-bg`, so the convention derives `--gb-text` from it
        /// and finds the pair the surface list already states. Both are right and
        /// one report is enough.
        @Test
        @DisplayName("the pair both halves find is reported once")
        void noDuplicates() {
            var pairs = ThemeAudit.audit(List.of(Theme.NORD_DARK.load())).stream()
                    .map(finding -> finding.background() + " " + finding.foreground())
                    .toList();

            assertEquals(pairs.size(), pairs.stream().distinct().count(), () -> "duplicates in " + pairs);
        }
    }

    @Nested
    @DisplayName("a theme the toolkit has never seen")
    class Custom {

        /// The entry's own example: "a third-party theme that pairs
        /// `--gb-badge-warning-bg` with an unreadable `--gb-badge-warning-text`
        /// is a legibility bug the toolkit will not notice". It notices.
        @Test
        @DisplayName("an unreadable override is caught")
        void catchesAnUnreadableOverride() {
            var failures = ThemeAudit.failures(withUnreadableBadge());

            assertEquals(1, failures.size(), () -> "failures were " + describe(failures));
            var bad = failures.getFirst();
            assertEquals("--gb-badge-warning-bg", bad.background());
            assertEquals("--gb-badge-warning-text", bad.foreground());
            assertFalse(bad.passes());
            // White on `--nord13`, the pale yellow ADR-0087 named as the hardest
            // case the system has. The number is asserted rather than a bound,
            // because a check that only says "below 4.5" would pass on a pair
            // that had quietly become 4.4.
            assertEquals(1.56, bad.ratio(), 0.01, () -> "ratio was " + bad.ratio());
        }

        /// A token an application invented, following the same convention. This
        /// is what a hard-coded list of the toolkit's own pairs would have
        /// missed, and it is the reason the pairs are discovered.
        @Test
        @DisplayName("a pair the toolkit never defined is measured too")
        void measuresTokensNobodyHereWrote() {
            var sheets = List.of(Theme.NORD_DARK.load(), Stylesheet.parse(CascadeLayer.APPLICATION, """
                            :root {
                              --gb-mycard-bg: #2e3440;
                              --gb-mycard-text: #333a47;
                            }
                            """));

            var failures = ThemeAudit.failures(sheets);

            assertEquals(
                    List.of("--gb-mycard-bg"),
                    failures.stream().map(ContrastFinding::background).toList(),
                    () -> "failures were " + describe(failures));
        }

        /// **Substituted, not raw.** A theme written the ordinary way says
        /// `--gb-badge-warning-bg: var(--gb-warning)`, and an audit that read the
        /// tokens without resolving would decide that is not a colour and skip
        /// the pair — auditing a real theme as having nothing to check.
        @Test
        @DisplayName("a pair written through another token is still measured")
        void resolvesThroughVar() {
            var sheets = List.of(Theme.NORD_DARK.load(), Stylesheet.parse(CascadeLayer.APPLICATION, """
                            :root {
                              --gb-relay-ink: #3b4252;
                              --gb-relay-bg: var(--gb-bg);
                              --gb-relay-text: var(--gb-relay-ink);
                            }
                            """));

            var relay = ThemeAudit.audit(sheets).stream()
                    .filter(finding -> "--gb-relay-bg".equals(finding.background()))
                    .findFirst()
                    .orElse(null);

            assertTrue(relay != null, "a pair written through var() was skipped entirely");
            assertFalse(relay.passes(), () -> "expected the dark-on-dark pair to fail: " + relay.describe());
        }
    }

    @Nested
    @DisplayName("what it will not measure")
    class Skipped {

        /// A translucent colour has no single ratio — what it composites over
        /// decides the answer. Skipping is the honest answer; measuring would
        /// read the alpha off and score a comfortable pass on a colour nobody
        /// receives.
        @Test
        @DisplayName("a translucent fill is skipped rather than scored")
        void translucentIsSkipped() {
            var sheets = List.of(Theme.NORD_DARK.load(), Stylesheet.parse(CascadeLayer.APPLICATION, """
                            :root {
                              --gb-veil-bg: rgba(255, 255, 255, 0.08);
                              --gb-veil-text: #ffffff;
                            }
                            """));

            var found = ThemeAudit.audit(sheets).stream()
                    .map(ContrastFinding::background)
                    .toList();

            assertFalse(found.contains("--gb-veil-bg"), () -> "a translucent pair was measured: " + found);
        }

        /// The toolkit's own instance of the rule, and the reason it is not
        /// hypothetical: a `hud`'s plate is `#1c212ae6`, deliberately translucent
        /// so the frame behind shows through.
        @Test
        @DisplayName("the hud's own plate is the shipped example of it")
        void hudIsSkipped() {
            var found = ThemeAudit.audit(List.of(Theme.NORD_DARK.load())).stream()
                    .map(ContrastFinding::background)
                    .toList();

            assertFalse(found.contains("--gb-hud-bg"), () -> "the hud plate is translucent and was measured: " + found);
        }

        @Test
        @DisplayName("a `-bg` with no matching `-text` is not half a pair")
        void unmatchedBackgroundIsNotAPair() {
            var found = ThemeAudit.audit(List.of(Theme.NORD_DARK.load())).stream()
                    .map(ContrastFinding::background)
                    .toList();

            // `--gb-checkbox-bg` carries a *mark*, not text, and the token that
            // goes on it is `--gb-checkbox-mark`. §1.2 holds it to the non-text
            // floor, which is ContrastTest's sweep and not this one.
            assertFalse(found.contains("--gb-checkbox-bg"), () -> "found " + found);
        }
    }

    @Test
    @DisplayName("a finding describes itself in one readable line")
    void findingReads() {
        var pass = new ContrastFinding("--gb-bg", "--gb-text", 0xFF2E3440, 0xFFECEFF4, 10.84, 4.5);
        var fail = new ContrastFinding("--gb-bg", "--gb-text", 0xFF2E3440, 0xFF303540, 1.05, 4.5);

        // What the line is for: which pair, what it measured, and what it
        // needed — enough to go and change a token. The sentence around those
        // four is free to be reworded; `FAILS` is not, because it is what makes
        // a failing pair findable in a page of passing ones.
        assertTrue(pass.describe().contains("--gb-text"), pass::describe);
        assertTrue(pass.describe().contains("--gb-bg"), pass::describe);
        assertTrue(pass.describe().contains("10.84"), pass::describe);
        assertTrue(pass.describe().contains("4.5"), pass::describe);
        assertFalse(pass.describe().contains("FAILS"), pass::describe);

        assertTrue(fail.describe().endsWith("FAILS"), fail::describe);
    }

    private static List<String> describe(List<ContrastFinding> findings) {
        return findings.stream().map(ContrastFinding::describe).toList();
    }
}
