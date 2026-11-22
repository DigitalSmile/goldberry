package dev.goldberry.vector;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.image.Image;
import dev.goldberry.render.model.PhysicalSize;

/// Documents parsed, drawn and released from many virtual threads at once: they
/// take turns at the engine's lock, and every one draws what it draws alone.
@DisplayName("Svg, from many threads")
class SvgThreadsTest {

    private static final int THREADS = 32;
    private static final int ROUNDS = 8;

    @BeforeAll
    static void library() {
        VectorNativesRequirement.enforce();
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    @DisplayName("draws the same pixels as one thread, with documents shared and documents of their own")
    void manyThreads() throws Exception {
        var size = new PhysicalSize(48, 24);
        int[] expected;
        try (var alone = Svg.parse(SvgGoldenTest.FEATURES)) {
            expected = pixels(alone.render(size));
        }
        try (var shared = Svg.parse(SvgGoldenTest.FEATURES);
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = new ArrayList<Future<int[]>>();
            for (var thread = 0; thread < THREADS; thread++) {
                var own = thread % 2 == 0;
                Callable<int[]> work = () -> {
                    int[] last = null;
                    for (var round = 0; round < ROUNDS; round++) {
                        if (own) {
                            try (var svg = Svg.parse(SvgGoldenTest.FEATURES)) {
                                last = pixels(svg.render(size));
                            }
                        } else {
                            last = pixels(shared.render(size));
                        }
                    }
                    return last;
                };
                results.add(executor.submit(work));
            }
            for (var result : results) {
                assertArrayEquals(expected, result.get());
            }
        }
    }

    private static int[] pixels(Image image) {
        var pixels = new int[image.width() * image.height()];
        for (var y = 0; y < image.height(); y++) {
            for (var x = 0; x < image.width(); x++) {
                pixels[y * image.width() + x] = image.argb(x, y);
            }
        }
        return pixels;
    }
}
