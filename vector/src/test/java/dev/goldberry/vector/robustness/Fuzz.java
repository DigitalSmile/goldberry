package dev.goldberry.vector.robustness;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.SplittableRandom;

/// The robustness tests' budget: how many rounds of each kind of damage to run
/// on each document of the corpus, and from which seed.
///
/// Deterministic, as `:media`'s are. Every round draws from a
/// [SplittableRandom] seeded by the base seed, the document, the damage and
/// the round, so a failure names a seed that reproduces it, and the same build
/// damages the same bytes every run. Nothing here reads a clock.
///
/// `-Dgoldberry.vector.fuzz.rounds=<n>` raises the rounds, as the sanitizer lane
/// does; `-Dgoldberry.vector.fuzz.seed=<n>` starts somewhere else.
/// `-Dgoldberry.vector.fuzz.trace=<file>` names every input in that file before
/// it is drawn, so a run the process does not survive (a crash, or the
/// operating system's out-of-memory killer) leaves its culprit on the last line,
/// and its bytes in `<file>.last`.
final class Fuzz {

    /// The rounds per document and kind of damage.
    static final String ROUNDS_PROPERTY = "goldberry.vector.fuzz.rounds";

    /// The base seed.
    static final String SEED_PROPERTY = "goldberry.vector.fuzz.seed";

    /// A file every input is named in before it is drawn.
    static final String TRACE_PROPERTY = "goldberry.vector.fuzz.trace";

    /// Rounds when the property is not set: every kind of damage on every
    /// document a few times, few enough for `test`.
    static final int DEFAULT_ROUNDS = 6;

    private static final long DEFAULT_SEED = 0x5F6_7EC7_0B_5EEDL;

    private Fuzz() {}

    /// The rounds per document and kind of damage.
    static int rounds() {
        return Math.max(1, Integer.getInteger(ROUNDS_PROPERTY, DEFAULT_ROUNDS));
    }

    /// The seed of `round` of `what`: the base seed mixed with both.
    static long seed(String what, int round) {
        var base = Long.getLong(SEED_PROPERTY, DEFAULT_SEED);
        return base ^ ((long) what.hashCode() << 32) ^ (round * 0x9E37_79B9_7F4A_7C15L);
    }

    /// A generator for `round` of `what`.
    static SplittableRandom random(String what, int round) {
        return new SplittableRandom(seed(what, round));
    }

    /// Names `what` in the trace file, when there is one, and keeps `document`
    /// beside it as `<file>.last`, both forced to disk: the process may not
    /// live to flush them.
    static void trace(String what, byte[] document) {
        var file = System.getProperty(TRACE_PROPERTY);
        if (file == null || file.isBlank()) {
            return;
        }
        try {
            Files.writeString(
                    Path.of(file),
                    what + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.SYNC);
            Files.write(
                    Path.of(file + ".last"),
                    document,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.SYNC);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
