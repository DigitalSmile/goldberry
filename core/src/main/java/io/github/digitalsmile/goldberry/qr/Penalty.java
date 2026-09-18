package io.github.digitalsmile.goldberry.qr;

/// How bad a masked grid looks — ISO/IEC 18004 §7.8.3's four rules.
///
/// The rules are not about correctness. Every one of the eight masks decodes,
/// and a decoder is told which was used; what they are about is what a camera
/// can *find* in a photograph of a wall. Long runs of one colour confuse the
/// sampling grid, 2×2 blocks do the same in two dimensions, anything with a
/// finder's proportions is a false corner, and a code that is nine tenths dark
/// has no contrast left to threshold against.
///
/// A pure function of a grid, in a class of its own, because that is what it is:
/// the encoder calls it eight times on the same modules and keeps the lowest,
/// and nothing about it needs to know how the grid was built.
final class Penalty {

    /// A run of five or more modules of one colour.
    static final int N1 = 3;

    /// A 2×2 block of one colour.
    static final int N2 = 3;

    /// A run with a finder's 1:1:3:1:1 proportions.
    static final int N3 = 40;

    /// Every 5% the dark proportion is away from half.
    static final int N4 = 10;

    /// The eleven modules a scanner mistakes for a finder: the 1:1:3:1:1 ratio
    /// with four light modules on one side of it. Looked for in both directions
    /// and read either way round.
    ///
    /// Written as a picture rather than as eleven booleans, because that is what
    /// it is and because eleven booleans in a row are eleven chances to typo.
    private static final boolean[] FINDER_LIKE = drawn("#.###.#....");

    private Penalty() {}

    private static boolean[] drawn(String picture) {
        var modules = new boolean[picture.length()];
        for (var i = 0; i < picture.length(); i++) {
            modules[i] = picture.charAt(i) == '#';
        }
        return modules;
    }

    /// The total penalty for a `size`×`size` grid, row-major in `dark`.
    static int score(boolean[] dark, int size) {
        return runs(dark, size) + blocks(dark, size) + finderLike(dark, size) + balance(dark, size);
    }

    /// Rule 1: five in a row of one colour costs 3, and every module past the
    /// fifth costs 1 more.
    static int runs(boolean[] dark, int size) {
        var score = 0;
        for (var line = 0; line < size; line++) {
            score += runsAlong(dark, size, line, true);
            score += runsAlong(dark, size, line, false);
        }
        return score;
    }

    private static int runsAlong(boolean[] dark, int size, int line, boolean horizontal) {
        var score = 0;
        var run = 1;
        var colour = at(dark, size, line, 0, horizontal);
        for (var i = 1; i < size; i++) {
            var here = at(dark, size, line, i, horizontal);
            if (here != colour) {
                colour = here;
                run = 1;
                continue;
            }
            run++;
            if (run == 5) {
                score += N1;
            } else if (run > 5) {
                score++;
            }
        }
        return score;
    }

    /// Rule 2: every 2×2 block of one colour costs 3.
    ///
    /// Overlapping blocks each count, which is not a simplification: the
    /// standard's penalty for an `m`×`n` block is `N2 × (m − 1)(n − 1)`, and
    /// that is exactly how many 2×2 blocks fit inside it.
    static int blocks(boolean[] dark, int size) {
        var score = 0;
        for (var y = 0; y < size - 1; y++) {
            for (var x = 0; x < size - 1; x++) {
                var colour = dark[y * size + x];
                if (colour == dark[y * size + x + 1]
                        && colour == dark[(y + 1) * size + x]
                        && colour == dark[(y + 1) * size + x + 1]) {
                    score += N2;
                }
            }
        }
        return score;
    }

    /// Rule 3: each 1:1:3:1:1 run with four light modules beside it costs 40,
    /// in both directions and read either way round.
    ///
    /// Only where all eleven modules are **inside** the symbol. The quiet zone
    /// outside it is light and would make the pattern match at three of the four
    /// edges of every code ever made, which would be a penalty that carried no
    /// information because every mask pays it.
    static int finderLike(boolean[] dark, int size) {
        var score = 0;
        for (var line = 0; line < size; line++) {
            for (var start = 0; start + FINDER_LIKE.length <= size; start++) {
                for (var horizontal = 0; horizontal < 2; horizontal++) {
                    if (matches(dark, size, line, start, horizontal == 0, false)
                            || matches(dark, size, line, start, horizontal == 0, true)) {
                        score += N3;
                    }
                }
            }
        }
        return score;
    }

    private static boolean matches(
            boolean[] dark, int size, int line, int start, boolean horizontal, boolean backwards) {
        for (var i = 0; i < FINDER_LIKE.length; i++) {
            var offset = start + (backwards ? FINDER_LIKE.length - 1 - i : i);
            if (at(dark, size, line, offset, horizontal) != FINDER_LIKE[i]) {
                return false;
            }
        }
        return true;
    }

    /// Rule 4: 10 for every whole 5% the dark proportion is away from half.
    static int balance(boolean[] dark, int size) {
        var darkCount = 0;
        for (var module : dark) {
            if (module) {
                darkCount++;
            }
        }
        var total = size * size;
        return Math.abs(darkCount * 2 - total) * 10 / total * N4;
    }

    private static boolean at(boolean[] dark, int size, int line, int index, boolean horizontal) {
        return horizontal ? dark[line * size + index] : dark[index * size + line];
    }
}
