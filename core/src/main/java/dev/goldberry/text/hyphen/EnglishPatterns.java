package dev.goldberry.text.hyphen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

/// Liang's pattern algorithm for the bundled hyph-utf8 American English table.
final class EnglishPatterns {

    static final int LEFT_MIN = 2;
    static final int RIGHT_MIN = 3;
    static final EnglishPatterns INSTANCE = new EnglishPatterns();

    private final Node root = new Node();
    private final Map<String, int[]> exceptions = new HashMap<>();

    private EnglishPatterns() {
        var resource = Objects.requireNonNull(EnglishPatterns.class.getResourceAsStream("hyphen.en"), "hyphen.en");
        try (var reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.US_ASCII))) {
            boolean inExceptions = false;
            for (String line; (line = reader.readLine()) != null; ) {
                var comment = line.indexOf('%');
                var token = (comment < 0 ? line : line.substring(0, comment)).strip();
                switch (token) {
                    case "", "}", "\\patterns{" -> {}
                    case "\\hyphenation{" -> inExceptions = true;
                    default -> {
                        if (inExceptions) {
                            addException(token);
                        } else {
                            addPattern(token);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Reading hyphen.en", e);
        }
    }

    private void addException(String marked) {
        var offsets = IntStream.builder();
        var offset = 0;
        for (var i = 0; i < marked.length(); i++) {
            if (marked.charAt(i) == '-') {
                offsets.add(offset);
            } else {
                offset++;
            }
        }
        exceptions.put(marked.replace("-", ""), offsets.build().toArray());
    }

    private void addPattern(String pattern) {
        var node = root;
        var weights = new byte[pattern.length() + 1];
        var letters = 0;
        for (var i = 0; i < pattern.length(); i++) {
            var c = pattern.charAt(i);
            if (c >= '0' && c <= '9') {
                weights[letters] = (byte) (c - '0');
            } else {
                var slot = slot(c);
                var child = node.children[slot];
                if (child == null) {
                    node.children[slot] = child = new Node();
                }
                node = child;
                letters++;
            }
        }
        node.weights = Arrays.copyOf(weights, letters + 1);
    }

    void addBreaks(String word, int start, IntStream.Builder breaks) {
        var exception = exceptions.get(word);
        if (exception != null) {
            for (var offset : exception) {
                if (offset >= LEFT_MIN && offset <= word.length() - RIGHT_MIN) {
                    breaks.add(start + offset);
                }
            }
            return;
        }

        // Patterns use a dot to anchor at a word boundary. Of the patterns
        // meeting at a position, the strongest weight wins. Odd allows a cut.
        var padded = "." + word + ".";
        var weights = new byte[padded.length() + 1];
        for (var i = 0; i < padded.length(); i++) {
            var node = root;
            for (var j = i; j < padded.length(); j++) {
                var child = node.children[slot(padded.charAt(j))];
                if (child == null) {
                    break;
                }
                node = child;
                for (var k = 0; k < node.weights.length; k++) {
                    weights[i + k] = (byte) Math.max(weights[i + k], node.weights[k]);
                }
            }
        }
        for (var offset = LEFT_MIN; offset <= word.length() - RIGHT_MIN; offset++) {
            if ((weights[offset + 1] & 1) != 0) {
                breaks.add(start + offset);
            }
        }
    }

    private static int slot(char c) {
        return c == '.' ? 26 : c - 'a';
    }

    private static final class Node {
        private static final byte[] NO_WEIGHTS = {};

        private final @Nullable Node[] children = new Node[27];
        private byte[] weights = NO_WEIGHTS;
    }
}
