package io.github.digitalsmile.goldberry.qr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Whole grids from an encoder that is not this one.
///
/// `libqrencode-vectors.txt` is twenty-six codes produced by libqrencode 4.1.1
/// through its own ABI, spanning every level, versions 1 to 40, both block
/// structures, the versions that carry version information, version 32's
/// alignment exception and all three modes. A worked example pins the
/// codewords; these pin every module of the finished thing, including the
/// function patterns, the interleave and the mask.
class QrVectorTest {

    /// One vector: what to encode, and what somebody else got when they did.
    private record Vector(Level level, String payload, List<String> rows) {}

    private static List<Vector> vectors() {
        try (var in = QrVectorTest.class.getResourceAsStream("libqrencode-vectors.txt")) {
            if (in == null) {
                throw new IllegalStateException("libqrencode-vectors.txt is not beside " + QrVectorTest.class);
            }
            var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            var vectors = new ArrayList<Vector>();
            Level level = null;
            String payload = null;
            var rows = new ArrayList<String>();
            String line;
            while ((line = reader.readLine()) != null) {
                // A comment is a hash and then a space or nothing. Not simply a
                // leading hash: a dark module is drawn as one, so half the grid
                // rows in the file start with a character that would otherwise
                // be read as a comment and dropped.
                if (line.equals("#") || line.startsWith("# ")) {
                    continue;
                }
                if (line.isEmpty()) {
                    if (level != null) {
                        vectors.add(new Vector(level, payload, List.copyOf(rows)));
                    }
                    level = null;
                    rows.clear();
                    continue;
                }
                if (level == null) {
                    var tab = line.indexOf('\t');
                    level = Level.valueOf(line.substring(0, tab));
                    payload = line.substring(tab + 1);
                } else {
                    rows.add(line);
                }
            }
            if (level != null) {
                vectors.add(new Vector(level, payload, List.copyOf(rows)));
            }
            return List.copyOf(vectors);
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("every module of every vector matches what libqrencode produced")
    void vectorsMatchModuleForModule() {
        var vectors = vectors();
        assertEquals(26, vectors.size(), "the vector file lost or gained entries");

        for (var vector : vectors) {
            var code = QrEncoder.encode(vector.payload(), vector.level());
            var name = "level " + vector.level() + ", " + vector.payload().length() + " characters";

            assertEquals(vector.rows().size(), code.size(), name + ": the grid is a different size");
            for (var y = 0; y < code.size(); y++) {
                var row = vector.rows().get(y);
                var drawn = new StringBuilder(code.size());
                for (var x = 0; x < code.size(); x++) {
                    drawn.append(code.isDark(x, y) ? '#' : '.');
                }
                assertEquals(row, drawn.toString(), name + ": row " + y);
            }
        }
    }

    @Test
    @DisplayName("the vectors between them cover every version band and every level")
    void vectorsCoverTheRange() {
        var versions = new java.util.TreeSet<Integer>();
        var levels = new java.util.TreeSet<Level>();
        for (var vector : vectors()) {
            var code = QrEncoder.encode(vector.payload(), vector.level());
            versions.add(code.version());
            levels.add(code.level());
        }

        assertEquals(4, levels.size(), "a level is missing from the corpus");
        assertTrue(versions.contains(1), "version 1 is missing");
        assertTrue(versions.contains(Version.MAX), "version 40 is missing");
        // From version 7 the version number is written into the grid, and from
        // 10 and 27 the character count field gets wider. A corpus that stopped
        // below any of those would not exercise the code that changes there.
        assertTrue(versions.stream().anyMatch(v -> v >= 7 && v <= 9), "no vector carries version information");
        assertTrue(versions.stream().anyMatch(v -> v >= 10 && v <= 26), "no vector in the middle band");
        assertTrue(versions.stream().anyMatch(v -> v >= 27), "no vector in the top band");
        assertTrue(versions.contains(32), "version 32's alignment exception is untested");
    }
}
