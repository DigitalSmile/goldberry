package io.github.digitalsmile.goldberry.widgets.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Every focus ring in the corpus is photographed on both themes.**
///
/// ## The rule, and why it is only about focus
///
/// The `TODO.md` entry this closes asked for "a rule about which states are
/// worth a second theme rather than one more image", and the rule is narrow on
/// purpose. §2.2's ring is the one mark in the system with **no second means of
/// being seen**: a hover has a wash, a checked control has a fill, a disabled one
/// has its opacity, and each of those is drawn in colours some other golden
/// already covers. A ring is only a ring, and `--gb-focus` resolves differently
/// per theme — so a ring photographed on one theme is a ring nothing is watching
/// on the other.
///
/// That is not a hypothetical. §2.2's ring sat below §1.2's floor on **every**
/// light surface, measured at 1.74:1, 2.00:1 and 1.64:1, and the change that
/// fixed it moved *no golden at all* ([ADR-0240]) — because every focus image in
/// the catalog was `NORD_DARK`. A colour with no picture is a colour nothing
/// would notice going wrong again ([ADR-0261]).
///
/// Doubling the *whole* corpus was the alternative and is not the rule: a hover
/// wash on a light surface is two tokens other goldens already draw, so a second
/// image of it buys a second file and no new question.
///
/// ## It discovers its subject rather than listing it
///
/// The corpus is read off the resource directory, so a focus golden added next
/// month is checked next month and nothing here has to be edited to know about
/// it. That is `ExportedSurfaceTest`'s and `SupportedPropertyTest`'s property, and
/// it is what makes this a rule instead of a list.
class FocusGoldenPairTest {

    /// Where the catalog's goldens live. A path rather than the classpath
    /// because what is being checked is the **files that are committed**, and a
    /// resource on the classpath is a copy in a build directory.
    private static final Path GOLDEN = Path.of("src/test/resources/golden");

    /// The suffix that makes an image a focus one.
    ///
    /// A convention, and the reason it is safe to lean on: every focus golden in
    /// the corpus is `<widget>-focus`, because a `PseudoState` golden is named
    /// after the state it captures. A file that captured a ring under some other
    /// name would be invisible here — which is a real limit and a smaller one
    /// than the alternative of enumerating them.
    private static final String FOCUS = "-focus";

    private static final String LIGHT = "-light";

    private static List<String> goldens() throws IOException {
        assertTrue(Files.isDirectory(GOLDEN), () -> GOLDEN.toAbsolutePath() + " is not where the goldens are");
        try (var files = Files.list(GOLDEN)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".png"))
                    .map(name -> name.substring(0, name.length() - ".png".length()))
                    .sorted()
                    .toList();
        }
    }

    @Test
    @DisplayName("every focus golden has a light twin")
    void everyRingIsPicturedOnBothThemes() throws IOException {
        var names = goldens();
        var dark = names.stream()
                .filter(name -> name.contains(FOCUS) && !name.endsWith(LIGHT))
                .toList();

        var missing = new TreeSet<String>();
        for (var name : dark) {
            if (!names.contains(name + LIGHT)) {
                missing.add(name + LIGHT);
            }
        }

        assertEquals(
                List.of(),
                List.copyOf(missing),
                () -> "§2.2's ring is the one mark with no second means of being seen, and"
                        + " `--gb-focus` differs per theme — so a ring pictured on one theme is a"
                        + " ring nothing watches on the other. Missing: " + missing);
    }

    @Test
    @DisplayName("and the check has something to check, so an empty corpus cannot pass it")
    void theSweepFindsTheRings() throws IOException {
        // Without this, renaming the convention -- or moving the directory --
        // would make the sweep above pass by finding nothing at all, which is
        // the failure mode every discovering test has.
        var dark = goldens().stream()
                .filter(name -> name.contains(FOCUS) && !name.endsWith(LIGHT))
                .toList();

        assertTrue(dark.size() >= 4, () -> "expected the catalog's focus goldens, found " + dark);
        assertTrue(dark.contains("menu-focus"), () -> dark.toString());
        assertTrue(dark.contains("segmented-focus"), () -> dark.toString());
    }

    @Test
    @DisplayName("and a light twin is never orphaned, because it would be an image of nothing")
    void noLightTwinWithoutADarkOne() throws IOException {
        var names = goldens();
        var orphans = new ArrayList<String>();
        for (var name : names) {
            if (name.contains(FOCUS) && name.endsWith(LIGHT)) {
                var dark = name.substring(0, name.length() - LIGHT.length());
                if (!names.contains(dark)) {
                    orphans.add(name);
                }
            }
        }

        assertEquals(List.of(), orphans, () -> "a light ring with no dark one to compare it against: " + orphans);
    }
}
