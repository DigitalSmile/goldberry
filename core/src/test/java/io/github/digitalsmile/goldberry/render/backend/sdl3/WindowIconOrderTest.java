package io.github.digitalsmile.goldberry.render.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.digitalsmile.goldberry.render.window.IconImage;

/// Which size of a window icon SDL is handed as its base ([ADR-0351]).
///
/// SDL treats the base as the 100% size and hangs the others off it, and X11's
/// path reads the base **alone**, so the choice is visible on a desktop and is
/// worth pinning. No SDL is needed: this is the ordering and nothing else.
class WindowIconOrderTest {

    private static IconImage square(int size) {
        return IconImage.of(size, size, (x, y) -> 0xFF000000);
    }

    private static List<Integer> widths(List<IconImage> images) {
        return images.stream().map(image -> image.size().width()).toList();
    }

    /// The sizes as they came in, and the order they must leave in. The rest of
    /// the list keeps the order it arrived in, which is why the expected column
    /// is the whole list and not just its head.
    static Stream<Arguments> orderings() {
        return Stream.of(
                arguments(
                        "the smallest size that is at least 48 goes first, whatever order they came in",
                        List.of(256, 16, 64, 32, 48),
                        List.of(48, 256, 16, 64, 32)),
                arguments(
                        "with nothing that large, the largest there is goes first",
                        List.of(16, 32, 24),
                        List.of(32, 16, 24)),
                arguments("one size is its own base", List.of(128), List.of(128)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("orderings")
    @DisplayName("the base is the smallest size that is at least 48, or the largest there is")
    void baseFirst(String what, List<Integer> given, List<Integer> expected) {
        var ordered = Sdl3Window.baseFirst(
                given.stream().map(WindowIconOrderTest::square).toList());

        assertEquals(expected, widths(ordered), what);
    }
}
