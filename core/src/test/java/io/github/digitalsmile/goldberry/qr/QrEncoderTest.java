package io.github.digitalsmile.goldberry.qr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// ISO/IEC 18004, checked against the standard rather than against itself.
///
/// The two worked examples below are the ones every reference reproduces: the
/// numeric `01234567` at version 1 level M, which is the standard's own, and the
/// alphanumeric `HELLO WORLD` at version 1 level Q. Both are pinned at the
/// codeword level — data and parity, byte for byte — because that is where a
/// wrong table shows itself, and a matrix comparison would only say that
/// something somewhere differed.
///
/// [QrVectorTest] does the other half: whole grids from a different
/// implementation entirely.
class QrEncoderTest {

    /// `01234567` at 1-M, from the standard's worked example. Mode indicator
    /// `0001`, count 8, three groups of digits, terminator, then §7.4.10's pad
    /// codewords.
    private static final int[] NUMERIC_DATA = {16, 32, 12, 86, 97, 128, 236, 17, 236, 17, 236, 17, 236, 17, 236, 17};

    private static final int[] NUMERIC_ECC = {165, 36, 212, 193, 237, 54, 199, 135, 44, 85};

    /// `HELLO WORLD` at 1-Q, alphanumeric — the other example every reference
    /// walks through.
    private static final int[] ALPHANUMERIC_DATA = {32, 91, 11, 120, 209, 114, 220, 77, 67, 64, 236, 17, 236};

    private static final int[] ALPHANUMERIC_ECC = {168, 72, 22, 82, 217, 54, 156, 0, 46, 15, 180, 122, 16};

    private static byte[] bytes(int[] values) {
        var result = new byte[values.length];
        for (var i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    @Test
    @DisplayName("the standard's numeric example produces the codewords it prints")
    void numericWorkedExample() {
        var segment = Segment.of("01234567");
        assertEquals(Mode.NUMERIC, segment.mode());
        assertEquals(1, Version.smallestFor(segment, Level.M));

        var data = QrEncoder.codewords(segment, 1, Level.M);
        assertArrayEquals(bytes(NUMERIC_DATA), data);
        assertArrayEquals(bytes(NUMERIC_ECC), ReedSolomon.remainder(data, Version.eccPerBlock(1, Level.M)));
    }

    @Test
    @DisplayName("the alphanumeric example produces the codewords it prints")
    void alphanumericWorkedExample() {
        var segment = Segment.of("HELLO WORLD");
        assertEquals(Mode.ALPHANUMERIC, segment.mode());
        assertEquals(1, Version.smallestFor(segment, Level.Q));

        var data = QrEncoder.codewords(segment, 1, Level.Q);
        assertArrayEquals(bytes(ALPHANUMERIC_DATA), data);
        assertArrayEquals(bytes(ALPHANUMERIC_ECC), ReedSolomon.remainder(data, Version.eccPerBlock(1, Level.Q)));
    }

    @Test
    @DisplayName("a payload takes the narrowest mode that covers all of it")
    void modeIsTheNarrowestThatFits() {
        assertEquals(Mode.NUMERIC, Segment.of("0123456789").mode());
        assertEquals(Mode.ALPHANUMERIC, Segment.of("HELLO WORLD").mode());
        // One lower-case letter and the whole payload is bytes: the mode is
        // chosen for the segment, not per character.
        assertEquals(Mode.BYTE, Segment.of("HELLO WORLd").mode());
        assertEquals(Mode.BYTE, Segment.of("tg://login?token=abc").mode());
    }

    @Test
    @DisplayName("a narrower mode fits the same payload in a smaller code")
    void narrowerModesFitMore() {
        var digits = "1".repeat(120);
        var letters = "a".repeat(120);

        assertTrue(
                QrEncoder.encode(digits, Level.M).version()
                        < QrEncoder.encode(letters, Level.M).version(),
                "numeric mode should need a smaller version than byte mode for the same length");
    }

    @Test
    @DisplayName("the version chosen is the smallest one the payload fits in")
    void versionIsTheSmallestThatFits() {
        for (var version = 1; version <= Version.MAX; version++) {
            var capacity = Version.dataCodewords(version, Level.M);
            // Four bits of mode and eight or sixteen of count, so the payload is
            // the capacity less the header, rounded down to whole bytes.
            var header = (4 + Mode.BYTE.countBits(version) + 7) / 8;
            var payload = "a".repeat(capacity - header);

            assertEquals(version, QrEncoder.encode(payload, Level.M).version(), "at version " + version);
        }
    }

    @Test
    @DisplayName("a payload larger than version 40 holds is refused when it is built")
    void oversizedPayloadIsRefused() {
        var tooLong = "a".repeat(Version.dataCodewords(Version.MAX, Level.H));

        var refusal = assertThrows(IllegalArgumentException.class, () -> QrEncoder.encode(tooLong, Level.H));

        assertTrue(refusal.getMessage().contains("does not fit"), refusal.getMessage());
    }

    @Test
    @DisplayName("a higher error correction level needs at least as big a code")
    void higherLevelsCostSize() {
        var payload = "https://goldberry.example/invite/9f2c1b";

        var low = QrEncoder.encode(payload, Level.L).version();
        var high = QrEncoder.encode(payload, Level.H).version();

        assertTrue(low <= high, "level H should not be smaller than level L: " + low + " and " + high);
    }

    @Test
    @DisplayName("every version is four modules wider than the one before it")
    void everyVersionIsTheSizeTheStandardGivesIt() {
        for (var version = 1; version <= Version.MAX; version++) {
            assertEquals(version * 4 + 17, Version.size(version));
        }
        assertEquals(21, Version.size(1));
        assertEquals(177, Version.size(Version.MAX));
    }

    @Test
    @DisplayName("the three finder patterns are in the three corners")
    void findersAreInTheCorners() {
        var code = QrEncoder.encode("finders", Level.M);
        var last = code.size() - 1;

        for (var corner : new int[][] {{0, 0}, {last - 6, 0}, {0, last - 6}}) {
            for (var dy = 0; dy < 7; dy++) {
                for (var dx = 0; dx < 7; dx++) {
                    var ring = Math.max(Math.abs(dx - 3), Math.abs(dy - 3));
                    assertEquals(
                            ring != 2,
                            code.isDark(corner[0] + dx, corner[1] + dy),
                            "finder at " + corner[0] + "," + corner[1] + " module " + dx + "," + dy);
                }
            }
        }
    }

    @Test
    @DisplayName("the timing patterns alternate between the finders")
    void timingPatternsAlternate() {
        var code = QrEncoder.encode("timing", Level.M);

        for (var i = 8; i < code.size() - 8; i++) {
            assertEquals(i % 2 == 0, code.isDark(i, 6), "horizontal timing at " + i);
            assertEquals(i % 2 == 0, code.isDark(6, i), "vertical timing at " + i);
        }
    }

    @Test
    @DisplayName("the module beside the bottom-left finder is always dark")
    void theDarkModuleIsDark() {
        for (var level : Level.values()) {
            var code = QrEncoder.encode("dark module", level);
            assertTrue(code.isDark(8, code.size() - 8), "at level " + level);
        }
    }

    @Test
    @DisplayName("outside the grid is light, which is what a quiet zone is")
    void outsideTheGridIsLight() {
        var code = QrEncoder.encode("quiet", Level.M);

        assertEquals(21, code.size(), "the quiet zone is not part of the matrix");
        assertTrue(!code.isDark(-1, 0) && !code.isDark(0, -1));
        assertTrue(!code.isDark(code.size(), 0) && !code.isDark(0, code.size()));
    }

    @Test
    @DisplayName("an empty payload still makes a readable code")
    void emptyPayloadEncodes() {
        var code = QrEncoder.encode("", Level.M);

        assertEquals(1, code.version());
        assertTrue(code.isDark(0, 0), "the top-left finder is still there");
    }

    @Test
    @DisplayName("two payloads that differ produce codes that differ")
    void differentPayloadsDiffer() {
        assertNotEquals(QrEncoder.encode("one", Level.M), QrEncoder.encode("two", Level.M));
    }

    @Test
    @DisplayName("encoding the same payload twice produces equal matrices")
    void encodingIsDeterministic() {
        assertEquals(QrEncoder.encode("determinism", Level.Q), QrEncoder.encode("determinism", Level.Q));
    }

    @Test
    @DisplayName("the mask chosen is the one with the lowest penalty")
    void theChosenMaskIsTheBestScoring() {
        var payload = "tg://login?token=AQAAAB8AAAAmaW1wb3J0YW50";
        var chosen = QrEncoder.encode(payload, Level.M);

        var segment = Segment.of(payload);
        var version = chosen.version();
        var best = Integer.MAX_VALUE;
        var bestMask = -1;
        for (var mask = 0; mask < 8; mask++) {
            var grid = new Grid(version);
            grid.drawFunctionPatterns(Level.M);
            grid.drawCodewords(QrEncoder.interleave(QrEncoder.codewords(segment, version, Level.M), version, Level.M));
            grid.drawFormat(Level.M, mask);
            grid.applyMask(mask);
            var score = grid.penalty();
            if (score < best) {
                best = score;
                bestMask = mask;
            }
        }

        assertEquals(bestMask, chosen.mask());
    }

    @Test
    @DisplayName("a payload of bytes encodes the same as the string they spell")
    void bytesAndStringsAgree() {
        var payload = "bytes and strings";

        assertEquals(
                QrEncoder.encode(payload, Level.M),
                QrEncoder.encode(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8), Level.M));
    }

    @Test
    @DisplayName("a payload outside ASCII is its UTF-8 and is counted in bytes")
    void nonAsciiIsCountedInBytes() {
        // Three characters, five bytes -- and a count of three would truncate it.
        assertEquals(5, Segment.of("Ünë").count());
        assertEquals(Mode.BYTE, Segment.of("Ünë").mode());
    }
}
