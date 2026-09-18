package io.github.digitalsmile.goldberry.build.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The decision log, held to the three rules {@code book/src/adr/index.md} states
 * about itself: one file per decision, numbered and named; every record says what
 * its status is; and every record is reachable from the book.
 *
 * <p>A drift guard rather than a style check. The 2026-09-18 review read all 397
 * records by hand to find out whether any of them had lost its status line -- the
 * answer was no, and two of them say it in italics rather than in a bullet, which
 * is what the review reported as missing. Reading 400 files to answer a question
 * a test can answer is the cost this class removes; the next record added without
 * a {@code SUMMARY.md} line is the failure it is really for.
 */
class DecisionLogTest {

    /** The number at the front of a record's file name. */
    private static final Pattern NUMBERED = Pattern.compile("^(\\d{4})-[a-z0-9-]+\\.md$");

    /**
     * The three spellings in use. A bullet (`- **Status:** Accepted`) in the early
     * records, a `## Status` section in the later ones, and an italic line in
     * ADR-0080 and ADR-0081. All three say the same thing, so all three pass: this
     * is a test about whether a reader can find the status, not about which of the
     * house styles a record was written in.
     */
    private static final Pattern STATUS =
            Pattern.compile("(\\*\\*Status:\\*\\*|^## Status|^\\*(Accepted|Proposed|Superseded))", Pattern.MULTILINE);

    private static final Path RECORDS = Path.of("book", "src", "adr");

    private static List<Path> records() {
        try (Stream<Path> files = Files.list(Repository.root().resolve(RECORDS))) {
            return files.filter(path -> NUMBERED.matcher(path.getFileName().toString()).matches())
                    .filter(path -> !path.getFileName().toString().startsWith("0000-"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + RECORDS, e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path, e);
        }
    }

    @Nested
    @DisplayName("every record")
    class EveryRecord {

        @Test
        @DisplayName("says what its status is, in one of the three spellings the log uses")
        void statesItsStatus() {
            var silent = new ArrayList<String>();
            for (var record : records()) {
                var head = read(record).lines().limit(14).reduce("", (a, b) -> a + "\n" + b);
                if (!STATUS.matcher(head).find()) {
                    silent.add(record.getFileName().toString());
                }
            }
            assertTrue(
                    silent.isEmpty(),
                    () -> "a record with no status is a decision nobody can tell the standing of: " + silent);
        }

        @Test
        @DisplayName("is listed in the book, so the log is reachable from the table of contents")
        void isInTheSummary() {
            var summary = read(Repository.root().resolve(Path.of("book", "src", "SUMMARY.md")));
            var unlisted = records().stream()
                    .map(record -> record.getFileName().toString())
                    .filter(name -> !summary.contains("adr/" + name))
                    .toList();
            assertTrue(unlisted.isEmpty(), () -> "records written and never linked from SUMMARY.md: " + unlisted);
        }

        @Test
        @DisplayName("is numbered in its heading the way it is numbered in its name")
        void agreesWithItsOwnNumber() {
            var disagreeing = new ArrayList<String>();
            for (var record : records()) {
                var name = record.getFileName().toString();
                var matcher = NUMBERED.matcher(name);
                if (!matcher.matches()) {
                    continue;
                }
                var number = matcher.group(1);
                var heading = read(record).lines().findFirst().orElse("");
                // Both house styles: `# ADR-0019: ...` and `# 396. ...`.
                if (!heading.contains("ADR-" + number) && !heading.startsWith("# " + Integer.parseInt(number) + ".")) {
                    disagreeing.add(name + " -> " + heading);
                }
            }
            assertTrue(disagreeing.isEmpty(), () -> "a record whose heading and file name disagree: " + disagreeing);
        }
    }

    @Test
    @DisplayName("the numbers run without a gap, so a citation resolves to exactly one record")
    void theNumbersAreContiguous() {
        var numbers = records().stream()
                .map(record -> NUMBERED.matcher(record.getFileName().toString()))
                .filter(java.util.regex.Matcher::matches)
                .map(matcher -> Integer.parseInt(matcher.group(1)))
                .sorted()
                .toList();

        assertEquals(1, numbers.getFirst(), "the log starts at ADR-0001");
        var missing = new ArrayList<Integer>();
        for (var expected = numbers.getFirst(); expected <= numbers.getLast(); expected++) {
            if (!numbers.contains(expected)) {
                missing.add(expected);
            }
        }
        assertTrue(missing.isEmpty(), () -> "numbers nothing was recorded under: " + missing);
    }
}
