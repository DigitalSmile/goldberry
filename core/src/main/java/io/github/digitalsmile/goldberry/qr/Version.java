package io.github.digitalsmile.goldberry.qr;

import java.util.ArrayList;
import java.util.List;

/// What each of the forty versions holds — ISO/IEC 18004 §7.3 and §7.5.
///
/// A "version" is a size: version 1 is 21×21 modules and every version after it
/// is four modules wider, up to version 40 at 177×177. What changes with it is
/// how many codewords fit, how they are split into Reed–Solomon blocks, and
/// where the alignment patterns go.
///
/// ## Two tables and three formulas
///
/// The two tables — [#ECC_CODEWORDS_PER_BLOCK] and [#BLOCKS] — are §7.5.1's, and
/// there is no arithmetic that produces them; they are a committee's choice, and
/// every encoder in the world carries the same 320 numbers. Everything else is a
/// formula, because it is geometry: how many modules a version has, where its
/// alignment patterns sit, and how many of its modules carry data.
final class Version {

    /// The smallest and largest versions the standard defines.
    static final int MIN = 1;

    static final int MAX = 40;

    /// §7.5.1's error correction codewords per block, indexed
    /// `[level.ordinal()][version]`. Index 0 of each row is unused.
    private static final int[][] ECC_CODEWORDS_PER_BLOCK = {
        // L
        {
            0, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28,
            30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30
        },
        // M
        {
            0, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28,
            28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28
        },
        // Q
        {
            0, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28,
            30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30
        },
        // H
        {
            0, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30,
            30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30
        }
    };

    /// §7.5.1's block counts, indexed `[level.ordinal()][version]`.
    private static final int[][] BLOCKS = {
        // L
        {
            0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17,
            18, 19, 19, 20, 21, 22, 24, 25
        },
        // M
        {
            0, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29,
            31, 33, 35, 37, 38, 40, 43, 45, 47, 49
        },
        // Q
        {
            0, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38,
            40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68
        },
        // H
        {
            0, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45,
            48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81
        }
    };

    private Version() {}

    /// How wide and tall `version` is, in modules.
    static int size(int version) {
        return version * 4 + 17;
    }

    /// Every module of `version` that is not a function pattern and not reserved
    /// for format or version information.
    ///
    /// The standard gives this as a table; it is a formula because the geometry
    /// is regular. The quadratic is the whole grid minus the three finders and
    /// their separators, the two timing lines and the format areas; the
    /// alignment term subtracts the 5×5 patterns, adding back the modules where
    /// they overlap the timing lines; and the 36 is the two version blocks that
    /// appear from version 7.
    static int dataModules(int version) {
        var result = (16 * version + 128) * version + 64;
        if (version >= 2) {
            var centres = version / 7 + 2;
            result -= (25 * centres - 10) * centres - 55;
            if (version >= 7) {
                result -= 36;
            }
        }
        return result;
    }

    /// How many codewords — data plus error correction — `version` holds.
    static int totalCodewords(int version) {
        return dataModules(version) / 8;
    }

    /// The remainder bits that are laid down after the last codeword.
    ///
    /// Zero for most versions and up to seven for the rest: the data region is
    /// not always a multiple of eight modules, and the leftovers are placed as
    /// light modules rather than left unwritten.
    static int remainderBits(int version) {
        return dataModules(version) % 8;
    }

    /// Which row of the two tables a level is, written out rather than taken
    /// from the enum's declaration order: the tables are the standard's and
    /// their row order is L, M, Q, H, which is a fact about §7.5.1 and not about
    /// this file. Reordering [Level] must not silently reorder the capacities.
    private static int row(Level level) {
        return switch (level) {
            case L -> 0;
            case M -> 1;
            case Q -> 2;
            case H -> 3;
        };
    }

    /// How many error correction codewords each block of `version` at `level`
    /// carries.
    static int eccPerBlock(int version, Level level) {
        return ECC_CODEWORDS_PER_BLOCK[row(level)][version];
    }

    /// Into how many Reed–Solomon blocks `version` at `level` is split.
    static int blocks(int version, Level level) {
        return BLOCKS[row(level)][version];
    }

    /// How many **data** codewords `version` at `level` holds.
    static int dataCodewords(int version, Level level) {
        return totalCodewords(version) - eccPerBlock(version, level) * blocks(version, level);
    }

    /// The row and column coordinates of the alignment pattern centres —
    /// §7.3.5's table, as the formula that produces it.
    ///
    /// Version 1 has none. Every other version has `version / 7 + 2` centres per
    /// axis, the first at 6 and the last at `size - 7`, spaced as evenly as an
    /// even number allows — and version 32 is the one the formula gets wrong, so
    /// it is written down. A pattern is drawn wherever two coordinates meet,
    /// except over the three finders.
    static List<Integer> alignmentCentres(int version) {
        if (version == 1) {
            return List.of();
        }
        var centres = version / 7 + 2;
        var size = size(version);
        var step = version == 32 ? 26 : (version * 4 + centres * 2 + 1) / (centres * 2 - 2) * 2;
        var result = new ArrayList<Integer>(centres);
        for (var position = size - 7; result.size() < centres - 1; position -= step) {
            result.addFirst(position);
        }
        result.addFirst(6);
        return List.copyOf(result);
    }

    /// The smallest version that holds `bits` of segment payload at `level`,
    /// or -1 when nothing does.
    ///
    /// `bits` is a function of the version — a character count field is wider in
    /// a bigger code — so this is given the segment and asks it, rather than
    /// being given a number. That is also why it is a loop: there is no closed
    /// form for "the smallest version whose own count width still lets the
    /// payload fit".
    static int smallestFor(Segment segment, Level level) {
        for (var version = MIN; version <= MAX; version++) {
            if (segment.bitLength(version) <= dataCodewords(version, level) * 8) {
                return version;
            }
        }
        return -1;
    }
}
