package io.github.digitalsmile.goldberry.render.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    @Test
    @DisplayName("the smallest size that is at least 48 goes first, whatever order they came in")
    void smallestThatFits() {
        var ordered = Sdl3Window.baseFirst(List.of(square(256), square(16), square(64), square(32), square(48)));

        assertEquals(List.of(48, 256, 16, 64, 32), widths(ordered));
    }

    @Test
    @DisplayName("with nothing that large, the largest there is goes first")
    void largestWhenNoneFits() {
        var ordered = Sdl3Window.baseFirst(List.of(square(16), square(32), square(24)));

        assertEquals(List.of(32, 16, 24), widths(ordered));
    }

    @Test
    @DisplayName("one size is its own base")
    void one() {
        assertEquals(List.of(128), widths(Sdl3Window.baseFirst(List.of(square(128)))));
    }
}
