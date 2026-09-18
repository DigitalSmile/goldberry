package io.github.digitalsmile.goldberry.qr;

/// The module grid while it is being built — ISO/IEC 18004 §7.7 to §7.9.
///
/// [QrMatrix] is the value that comes out; this is the mutable thing it comes
/// out of. Two parallel grids, because almost every rule in the placement
/// chapter is about the difference between them: [#dark] is what a scanner sees,
/// and [#function] is which of those modules are finders, timing lines,
/// alignment patterns or format information — the ones data skips over and the
/// mask must not touch.
final class Grid {

    private final int version;
    private final int size;
    private final boolean[] dark;
    private final boolean[] function;

    Grid(int version) {
        this.version = version;
        this.size = Version.size(version);
        this.dark = new boolean[size * size];
        this.function = new boolean[size * size];
    }

    int size() {
        return size;
    }

    boolean dark(int x, int y) {
        return dark[y * size + x];
    }

    private void set(int x, int y, boolean value) {
        dark[y * size + x] = value;
    }

    /// Sets a module **and** declares it a function pattern, which is the only
    /// way anything outside the data region is written.
    private void setFunction(int x, int y, boolean value) {
        if (x < 0 || x >= size || y < 0 || y >= size) {
            return;
        }
        set(x, y, value);
        function[y * size + x] = true;
    }

    /// Draws everything that is the same for every payload: the three finders
    /// and their separators, the two timing lines, the alignment patterns, and
    /// the version blocks.
    ///
    /// The format information is drawn too, with a placeholder, because its
    /// *position* is a function pattern even though its content depends on a
    /// mask that has not been chosen yet. Reserving it any other way would mean
    /// a second notion of "reserved" that the data placement also had to know
    /// about.
    void drawFunctionPatterns(Level level) {
        for (var i = 0; i < size; i++) {
            setFunction(6, i, i % 2 == 0);
            setFunction(i, 6, i % 2 == 0);
        }
        drawFinder(3, 3);
        drawFinder(size - 4, 3);
        drawFinder(3, size - 4);
        drawAlignment();
        drawFormat(level, 0);
        drawVersion();
    }

    /// A finder, its separator and the light ring between them, from the centre
    /// out: dark at distances 0 and 1, light at 2, dark at 3, light at 4 — which
    /// is the separator, and falls off the edge on three sides of each corner.
    private void drawFinder(int centreX, int centreY) {
        for (var dy = -4; dy <= 4; dy++) {
            for (var dx = -4; dx <= 4; dx++) {
                var distance = Math.max(Math.abs(dx), Math.abs(dy));
                setFunction(centreX + dx, centreY + dy, distance != 2 && distance != 4);
            }
        }
    }

    /// The 5×5 alignment patterns, wherever two centre coordinates meet.
    ///
    /// Except the three corners where they would land on a finder. The standard
    /// states that as an exception; it is the same thing as saying the finders
    /// were there first.
    private void drawAlignment() {
        var centres = Version.alignmentCentres(version);
        var last = centres.size() - 1;
        for (var i = 0; i <= last; i++) {
            for (var j = 0; j <= last; j++) {
                var onFinder = (i == 0 && j == 0) || (i == 0 && j == last) || (i == last && j == 0);
                if (onFinder) {
                    continue;
                }
                var centreX = centres.get(i);
                var centreY = centres.get(j);
                for (var dy = -2; dy <= 2; dy++) {
                    for (var dx = -2; dx <= 2; dx++) {
                        setFunction(centreX + dx, centreY + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
                    }
                }
            }
        }
    }

    /// §7.9's format information: five bits of level and mask, a BCH(15, 5)
    /// check over them, and the whole thing XORed with `101010000010010`.
    ///
    /// Written **twice**, in two places that share no modules, so a code with a
    /// corner torn off still says which level it is. The mask is not protected by
    /// the data's own error correction — a decoder has to unmask before it can
    /// correct anything — which is why fifteen bits are spent twice on five.
    void drawFormat(Level level, int mask) {
        var data = level.bits() << 3 | mask;
        var remainder = data;
        for (var i = 0; i < 10; i++) {
            remainder = (remainder << 1) ^ ((remainder >>> 9) * 0x537);
        }
        var bits = ((data << 10) | (remainder & 0x3FF)) ^ 0x5412;

        for (var i = 0; i <= 5; i++) {
            setFunction(8, i, bit(bits, i));
        }
        setFunction(8, 7, bit(bits, 6));
        setFunction(8, 8, bit(bits, 7));
        setFunction(7, 8, bit(bits, 8));
        for (var i = 9; i < 15; i++) {
            setFunction(14 - i, 8, bit(bits, i));
        }
        for (var i = 0; i < 8; i++) {
            setFunction(size - 1 - i, 8, bit(bits, i));
        }
        for (var i = 8; i < 15; i++) {
            setFunction(8, size - 15 + i, bit(bits, i));
        }
        // The one module that is dark in every code ever made. It carries no
        // information; it is there so the format information around it is the
        // same length in both copies.
        setFunction(8, size - 8, true);
    }

    /// §7.10's version information — eighteen bits, BCH(18, 6), twice, and only
    /// from version 7.
    ///
    /// Below that a decoder counts the modules between the finders, which is
    /// unambiguous up to 21 versions of difference; above it the count is not
    /// reliable enough and the version is written down.
    private void drawVersion() {
        if (version < 7) {
            return;
        }
        var remainder = version;
        for (var i = 0; i < 12; i++) {
            remainder = (remainder << 1) ^ ((remainder >>> 11) * 0x1F25);
        }
        var bits = (version << 12) | (remainder & 0xFFF);
        for (var i = 0; i < 18; i++) {
            var value = bit(bits, i);
            var far = size - 11 + i % 3;
            var near = i / 3;
            setFunction(far, near, value);
            setFunction(near, far, value);
        }
    }

    /// Lays `codewords` into every module that is not a function pattern —
    /// §7.7.3's two-module-wide zigzag, upwards from the bottom right.
    ///
    /// The column at x = 6 is the vertical timing line and is skipped entirely,
    /// so the pairs to its left are shifted by one. Any modules left over after
    /// the last codeword stay light: they are the remainder bits, and the
    /// standard says nothing goes in them.
    void drawCodewords(byte[] codewords) {
        var index = 0;
        for (var right = size - 1; right >= 1; right -= 2) {
            if (right == 6) {
                // The timing line is not a column of its own for this purpose:
                // it is stepped *over*, so every pair to its left shifts by one
                // and the walk continues at 5, 3, 1 rather than 4, 2, 0. Moving
                // the loop variable is the whole of the rule -- a pair that only
                // renamed its columns would visit column 4 twice and column 0
                // never.
                right = 5;
            }
            for (var row = 0; row < size; row++) {
                for (var offset = 0; offset < 2; offset++) {
                    var x = right - offset;
                    // Every other pair of columns runs the other way, which is
                    // what makes it a boustrophedon rather than a raster.
                    var upward = ((right + 1) & 2) == 0;
                    var y = upward ? size - 1 - row : row;
                    if (function[y * size + x] || index >= codewords.length * 8) {
                        continue;
                    }
                    set(x, y, bit(codewords[index >>> 3] & 0xFF, 7 - (index & 7)));
                    index++;
                }
            }
        }
    }

    /// Inverts every data module the mask selects — §7.8.2.
    ///
    /// Its own inverse, which is what lets the encoder try all eight in place:
    /// apply, score, apply again to undo.
    void applyMask(int mask) {
        for (var y = 0; y < size; y++) {
            for (var x = 0; x < size; x++) {
                if (function[y * size + x] || !masked(mask, x, y)) {
                    continue;
                }
                set(x, y, !dark(x, y));
            }
        }
    }

    /// §7.8.2's eight conditions. `x` is the column and `y` the row, which is
    /// the opposite of the standard's `(i, j)` — it names the row first — and is
    /// the one place this file deliberately does not use its notation, because
    /// everything else here is `(x, y)` and mixing the two is how a mask ends up
    /// transposed.
    private static boolean masked(int mask, int x, int y) {
        return switch (mask) {
            case 0 -> (x + y) % 2 == 0;
            case 1 -> y % 2 == 0;
            case 2 -> x % 3 == 0;
            case 3 -> (x + y) % 3 == 0;
            case 4 -> (y / 2 + x / 3) % 2 == 0;
            case 5 -> x * y % 2 + x * y % 3 == 0;
            case 6 -> (x * y % 2 + x * y % 3) % 2 == 0;
            case 7 -> ((x + y) % 2 + x * y % 3) % 2 == 0;
            default -> throw new IllegalArgumentException("a mask is 0..7, not " + mask);
        };
    }

    /// What [Penalty] thinks of how this grid looks. Lower is better.
    int penalty() {
        return Penalty.score(dark, size);
    }

    /// Bit `index` of `value`, counting from the least significant.
    private static boolean bit(int value, int index) {
        return ((value >>> index) & 1) != 0;
    }

    /// This grid, frozen.
    QrMatrix freeze(Level level, int mask) {
        return new QrMatrix(version, level, mask, size, dark);
    }
}
