package io.github.digitalsmile.goldberry.qr;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The two bit strings the standard tabulates rather than describes —
/// ISO/IEC 18004 §7.9 and §7.10.
///
/// Both are BCH codes over a handful of bits, and both are printed in full in
/// the standard because a decoder has to read them before it can correct
/// anything else. So they are the one part of a QR code that can be checked
/// against a **table** rather than against an argument, and the tables below are
/// the standard's: thirty-two format strings, one per level and mask, and
/// thirty-four version patterns, one per version from 7.
///
/// They are read back out of a [Grid] rather than compared against the
/// arithmetic that produced them, which means these also pin *where* the bits
/// go — a BCH computation that was right and written into the wrong modules
/// would pass an arithmetic test and fail every scanner.
class FormatInformationTest {

    /// §7.9's Table 25, by level and then by mask, as fifteen bits each.
    private static final String[][] FORMAT = {
        {
            "111011111000100", "111001011110011", "111110110101010", "111100010011101",
            "110011000101111", "110001100011000", "110110001000001", "110100101110110"
        },
        {
            "101010000010010", "101000100100101", "101111001111100", "101101101001011",
            "100010111111001", "100000011001110", "100111110010111", "100101010100000"
        },
        {
            "011010101011111", "011000001101000", "011111100110001", "011101000000110",
            "010010010110100", "010000110000011", "010111011011010", "010101111101101"
        },
        {
            "001011010001001", "001001110111110", "001110011100111", "001100111010000",
            "000011101100010", "000001001010101", "000110100001100", "000100000111011"
        }
    };

    /// §7.10's Table 26, versions 7 to 40, as eighteen bits each.
    private static final String[] VERSION = {
        "000111110010010100", "001000010110111100", "001001101010011001", "001010010011010011",
        "001011101111110110", "001100011101100010", "001101100001000111", "001110011000001101",
        "001111100100101000", "010000101101111000", "010001010001011101", "010010101000010111",
        "010011010100110010", "010100100110100110", "010101011010000011", "010110100011001001",
        "010111011111101100", "011000111011000100", "011001000111100001", "011010111110101011",
        "011011000010001110", "011100110000011010", "011101001100111111", "011110110101110101",
        "011111001001010000", "100000100111010101", "100001011011110000", "100010100010111010",
        "100011011110011111", "100100101100001011", "100101010000101110", "100110101001100100",
        "100111010101000001", "101000110001101001"
    };

    /// The fifteen format modules beside the top-left finder, most significant
    /// bit first — the copy §7.9.1 describes first.
    private static String formatBitsNear(Grid grid) {
        var bits = new StringBuilder(15);
        for (var i = 14; i >= 0; i--) {
            bits.append(darkAt(grid, i) ? '1' : '0');
        }
        return bits.toString();
    }

    private static boolean darkAt(Grid grid, int index) {
        if (index <= 5) {
            return grid.dark(8, index);
        }
        return switch (index) {
            case 6 -> grid.dark(8, 7);
            case 7 -> grid.dark(8, 8);
            case 8 -> grid.dark(7, 8);
            default -> grid.dark(14 - index, 8);
        };
    }

    /// The fifteen format modules in the second copy, which straddles two
    /// corners: the low eight run right-to-left along the top, the high seven
    /// run bottom-to-top down the left.
    private static String formatBitsFar(Grid grid) {
        var bits = new StringBuilder(15);
        for (var i = 14; i >= 0; i--) {
            var dark = i < 8 ? grid.dark(grid.size() - 1 - i, 8) : grid.dark(8, grid.size() - 15 + i);
            bits.append(dark ? '1' : '0');
        }
        return bits.toString();
    }

    @Test
    @DisplayName("every one of the thirty-two format strings is the standard's")
    void formatStringsMatchTheTable() {
        var levels = new Level[] {Level.L, Level.M, Level.Q, Level.H};
        for (var l = 0; l < levels.length; l++) {
            for (var mask = 0; mask < 8; mask++) {
                var grid = new Grid(1);
                grid.drawFunctionPatterns(levels[l]);
                grid.drawFormat(levels[l], mask);

                assertEquals(FORMAT[l][mask], formatBitsNear(grid), "level " + levels[l] + ", mask " + mask);
            }
        }
    }

    @Test
    @DisplayName("the second copy of the format information says the same thing")
    void bothCopiesAgree() {
        for (var level : Level.values()) {
            for (var mask = 0; mask < 8; mask++) {
                var grid = new Grid(7);
                grid.drawFunctionPatterns(level);
                grid.drawFormat(level, mask);

                assertEquals(formatBitsNear(grid), formatBitsFar(grid), "level " + level + ", mask " + mask);
            }
        }
    }

    @Test
    @DisplayName("every one of the thirty-four version patterns is the standard's")
    void versionPatternsMatchTheTable() {
        for (var version = 7; version <= Version.MAX; version++) {
            var grid = new Grid(version);
            grid.drawFunctionPatterns(Level.M);

            var bits = new StringBuilder(18);
            for (var i = 17; i >= 0; i--) {
                bits.append(grid.dark(grid.size() - 11 + i % 3, i / 3) ? '1' : '0');
            }

            assertEquals(VERSION[version - 7], bits.toString(), "version " + version);
        }
    }

    @Test
    @DisplayName("the version information is written twice, transposed")
    void versionInformationIsWrittenTwice() {
        for (var version = 7; version <= Version.MAX; version++) {
            var grid = new Grid(version);
            grid.drawFunctionPatterns(Level.M);

            for (var i = 0; i < 18; i++) {
                var far = grid.size() - 11 + i % 3;
                var near = i / 3;
                assertEquals(grid.dark(far, near), grid.dark(near, far), "version " + version + ", bit " + i);
            }
        }
    }

    /// §7.3.5's Table E.1 — where the alignment pattern centres go, version by
    /// version. The one geometric table the standard prints rather than derives,
    /// and version 32 is the row that no formula reproduces.
    private static final int[][] ALIGNMENT = {
        {6, 18},
        {6, 22},
        {6, 26},
        {6, 30},
        {6, 34},
        {6, 22, 38},
        {6, 24, 42},
        {6, 26, 46},
        {6, 28, 50},
        {6, 30, 54},
        {6, 32, 58},
        {6, 34, 62},
        {6, 26, 46, 66},
        {6, 26, 48, 70},
        {6, 26, 50, 74},
        {6, 30, 54, 78},
        {6, 30, 56, 82},
        {6, 30, 58, 86},
        {6, 34, 62, 90},
        {6, 28, 50, 72, 94},
        {6, 26, 50, 74, 98},
        {6, 30, 54, 78, 102},
        {6, 28, 54, 80, 106},
        {6, 32, 58, 84, 110},
        {6, 30, 58, 86, 114},
        {6, 34, 62, 90, 118},
        {6, 26, 50, 74, 98, 122},
        {6, 30, 54, 78, 102, 126},
        {6, 26, 52, 78, 104, 130},
        {6, 30, 56, 82, 108, 134},
        {6, 34, 60, 86, 112, 138},
        {6, 30, 58, 86, 114, 142},
        {6, 34, 62, 90, 118, 146},
        {6, 30, 54, 78, 102, 126, 150},
        {6, 24, 50, 76, 102, 128, 154},
        {6, 28, 54, 80, 106, 132, 158},
        {6, 32, 58, 84, 110, 136, 162},
        {6, 26, 54, 82, 110, 138, 166},
        {6, 30, 58, 86, 114, 142, 170}
    };

    @Test
    @DisplayName("the alignment pattern centres are where the standard tabulates them")
    void alignmentCentresMatchTheTable() {
        assertEquals(0, Version.alignmentCentres(1).size(), "version 1 has no alignment patterns");

        for (var version = 2; version <= Version.MAX; version++) {
            var expected = ALIGNMENT[version - 2];
            var centres = Version.alignmentCentres(version);

            assertEquals(expected.length, centres.size(), "version " + version + " has the wrong number of centres");
            for (var i = 0; i < expected.length; i++) {
                assertEquals(expected[i], centres.get(i), "version " + version + ", centre " + i);
            }
        }
    }
}
