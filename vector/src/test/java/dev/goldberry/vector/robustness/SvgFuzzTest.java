package dev.goldberry.vector.robustness;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.vector.VectorNativesRequirement;

/// Every document of the corpus through every kind of damage, many rounds of
/// each, parsed and drawn: ThorVG's SVG loader and rasterizer on what a broken
/// download or a hostile mail hands them.
///
/// An outcome is a drawing or a refusal ([Exercise]); a crash is the failure
/// this test exists for, and only the sanitizer lane sees the quiet kind, a
/// read past the end that returns whatever is there. Seeded by document,
/// damage and round; a failure names all three and the seed.
@Tag("robustness")
@DisplayName("Damaged SVG documents")
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class SvgFuzzTest {

    @BeforeAll
    static void library() {
        VectorNativesRequirement.enforce();
    }

    @Test
    @DisplayName("every document of the corpus draws as it is")
    void corpusDraws() {
        for (var document : Corpus.documents()) {
            var outcome = Exercise.run(document.bytes(), document.name());
            assertTrue(outcome instanceof Exercise.Outcome.Drawn, () -> document.name() + ": " + outcome);
        }
    }

    @TestFactory
    @DisplayName("each kind of damage, on each document")
    Stream<DynamicTest> damaged() {
        return Corpus.documents().stream()
                .flatMap(document -> Arrays.stream(Damage.values())
                        .map(damage -> DynamicTest.dynamicTest(document.name() + ", " + damage, () -> {
                            var what = document.name() + " " + damage;
                            for (var round = 0; round < Fuzz.rounds(); round++) {
                                var random = Fuzz.random(what, round);
                                var damaged = damage.apply(document.bytes(), random);
                                Exercise.run(
                                        damaged,
                                        what + " round " + round + " seed " + Long.toHexString(Fuzz.seed(what, round)));
                            }
                        })));
    }

    @TestFactory
    @DisplayName("each document cut at every length of its first 256 bytes, and at steps after")
    Stream<DynamicTest> cut() {
        return Corpus.documents().stream()
                .map(document -> DynamicTest.dynamicTest(document.name(), () -> {
                    var bytes = document.bytes();
                    for (var length = 1; length < bytes.length; length += length < 256 ? 1 : 37) {
                        Exercise.run(Arrays.copyOf(bytes, length), document.name() + " cut at " + length);
                    }
                }));
    }
}
