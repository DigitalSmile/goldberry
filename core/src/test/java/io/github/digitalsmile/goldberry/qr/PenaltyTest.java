package io.github.digitalsmile.goldberry.qr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// §7.8.3's four penalty rules, each one on a grid built to trip exactly it.
///
/// A mask is chosen by comparing eight of these numbers, so a rule that is
/// nearly right produces a code that is legal, scannable and quietly worse than
/// the one the standard asks for — which is the kind of thing a test that only
/// checks the code decodes will never catch. So each rule is driven on its own,
/// on grids small enough that the expected number can be written out.
class PenaltyTest {

    /// A square grid from rows of `#` and `.`.
    private static boolean[] of(String... rows) {
        var size = rows.length;
        var modules = new boolean[size * size];
        for (var y = 0; y < size; y++) {
            for (var x = 0; x < size; x++) {
                modules[y * size + x] = rows[y].charAt(x) == '#';
            }
        }
        return modules;
    }

    /// A `size`×`size` grid with nothing on it.
    private static boolean[] light(int size) {
        return new boolean[size * size];
    }

    /// A `size`×`size` grid of alternating modules — the one arrangement that
    /// trips none of the first three rules, and is within a module of half dark.
    private static boolean[] alternating(int size) {
        var modules = new boolean[size * size];
        for (var y = 0; y < size; y++) {
            for (var x = 0; x < size; x++) {
                modules[y * size + x] = (x + y) % 2 == 0;
            }
        }
        return modules;
    }

    @Test
    @DisplayName("an alternating grid trips none of the four rules")
    void alternatingGridsAreFree() {
        var modules = alternating(21);

        assertEquals(0, Penalty.runs(modules, 21), "nothing is five long");
        assertEquals(0, Penalty.blocks(modules, 21), "nothing is two by two");
        assertEquals(0, Penalty.finderLike(modules, 21), "nothing is three long");
        assertEquals(0, Penalty.balance(modules, 21), "221 of 441 modules is half");
        assertEquals(0, Penalty.score(modules, 21));
    }

    @Test
    @DisplayName("a run of five costs three and every module past it costs one")
    void runsAreScoredByLength() {
        // A blank grid is nothing but runs: every row and every column is one,
        // and the price of a run of n is 3 + (n - 5).
        assertEquals(2 * 5 * Penalty.N1, Penalty.runs(light(5), 5), "ten runs of five");
        assertEquals(2 * 6 * (Penalty.N1 + 1), Penalty.runs(light(6), 6), "twelve runs of six");
        assertEquals(2 * 9 * (Penalty.N1 + 4), Penalty.runs(light(9), 9), "eighteen runs of nine");
    }

    @Test
    @DisplayName("a run of four is free")
    void shortRunsAreFree() {
        assertEquals(0, Penalty.runs(light(4), 4));
    }

    @Test
    @DisplayName("a block of one colour costs three for each two-by-two inside it")
    void blocksAreScoredByArea() {
        // The standard's price for an m×n block is N2 × (m−1)(n−1), which is
        // exactly how many 2×2 blocks fit inside it.
        assertEquals(Penalty.N2, Penalty.blocks(light(2), 2));
        assertEquals(4 * Penalty.N2, Penalty.blocks(light(3), 3));
        assertEquals(9 * Penalty.N2, Penalty.blocks(light(4), 4));
    }

    @Test
    @DisplayName("a finder's proportions with four light modules beside it costs forty")
    void finderLikePatternsAreScored() {
        var blank = ".".repeat(11);

        assertEquals(
                Penalty.N3,
                Penalty.finderLike(
                        of("#.###.#....", blank, blank, blank, blank, blank, blank, blank, blank, blank, blank), 11),
                "the light run after it");
        assertEquals(
                Penalty.N3,
                Penalty.finderLike(
                        of("....#.###.#", blank, blank, blank, blank, blank, blank, blank, blank, blank, blank), 11),
                "the light run before it");
        assertEquals(
                Penalty.N3,
                Penalty.finderLike(
                        of(
                                "#..........",
                                ".".repeat(11),
                                "#..........",
                                "#..........",
                                "#..........",
                                ".".repeat(11),
                                "#..........",
                                blank,
                                blank,
                                blank,
                                blank),
                        11),
                "the same pattern down a column");
    }

    @Test
    @DisplayName("a finder's proportions without four light modules is free")
    void aFinderWithoutItsLightRunIsFree() {
        var blank = ".".repeat(11);

        assertEquals(
                0,
                Penalty.finderLike(
                        of("#.###.#..#.", blank, blank, blank, blank, blank, blank, blank, blank, blank, blank), 11),
                "the light run has to be four long");
    }

    @Test
    @DisplayName("a grid that is all one colour pays the most its balance can")
    void oneColourGridsPayTheMost() {
        var dark = new boolean[21 * 21];
        java.util.Arrays.fill(dark, true);

        assertEquals(10 * Penalty.N4, Penalty.balance(dark, 21));
        assertEquals(10 * Penalty.N4, Penalty.balance(light(21), 21));
    }

    @Test
    @DisplayName("every whole five per cent away from half costs ten more")
    void balanceIsScoredInFivePerCentSteps() {
        var total = 21 * 21;
        for (var percent = 50; percent <= 100; percent += 5) {
            var modules = new boolean[total];
            var darkCount = total * percent / 100;
            for (var i = 0; i < darkCount; i++) {
                modules[i] = true;
            }

            var expected = Math.abs(darkCount * 100 / total - 50) / 5 * Penalty.N4;
            assertEquals(expected, Penalty.balance(modules, 21), percent + "% dark");
        }
    }

    @Test
    @DisplayName("a real code is scored by all four rules added together")
    void aRealCodeIsScoredByAllFour() {
        var code = QrEncoder.encode("tg://login?token=AQAAAB8AAAAmaW1wb3J0YW50", Level.M);
        var size = code.size();
        var modules = new boolean[size * size];
        for (var y = 0; y < size; y++) {
            for (var x = 0; x < size; x++) {
                modules[y * size + x] = code.isDark(x, y);
            }
        }

        assertTrue(Penalty.runs(modules, size) > 0, "the finders alone are runs of seven");
        assertTrue(Penalty.blocks(modules, size) > 0, "a finder's middle is a three-by-three");
        assertTrue(Penalty.finderLike(modules, size) > 0, "a finder is the pattern the rule is named after");
        assertEquals(
                Penalty.score(modules, size),
                Penalty.runs(modules, size)
                        + Penalty.blocks(modules, size)
                        + Penalty.finderLike(modules, size)
                        + Penalty.balance(modules, size));
    }
}
