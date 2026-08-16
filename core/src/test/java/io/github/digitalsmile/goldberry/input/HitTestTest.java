package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Hit testing against a real layout pass.
///
/// The other half of [PointerRouterTest], which supplies its own rectangles:
/// this one checks that the rectangles a paint produces are the ones a pointer
/// is tested against.
class HitTestTest {

    @BeforeEach
    void requireRenderer() {
        RendererRequirement.enforce();
    }

    private static List<HitTest.Region> capture(Box root, int width, int height, float scale) {
        var target = TestFrames.of(width, height, scale);
        try {
            return HitTest.capture(target.frame(), root);
        } finally {
            target.end();
        }
    }

    @Test
    @DisplayName("the topmost box under a point wins")
    void topmostWins() {
        var child = Box.filled(0xFF00FF00).size(StyleLength.points(40), StyleLength.points(40))
                .owner("child");
        var root = Box.filled(0xFFFF0000).padding(StyleLength.points(20))
                .children(child).owner("root");

        var regions = capture(root, 100, 100, 1.0f);

        // The child is painted after its parent, so inside its bounds it is what
        // the user can see and therefore what they clicked.
        assertEquals("child", HitTest.at(regions, 30, 30).orElseThrow());
        assertEquals("root", HitTest.at(regions, 5, 5).orElseThrow());
    }

    @Test
    @DisplayName("a point outside everything hits nothing")
    void missesEverything() {
        var root = Box.filled(0xFFFF0000)
                .size(StyleLength.points(20), StyleLength.points(20)).owner("root");

        assertTrue(HitTest.at(capture(root, 100, 100, 1.0f), 50, 50).isEmpty());
    }

    @Test
    @DisplayName("an untagged box is scenery and is skipped")
    void untaggedBoxesAreSkipped() {
        // A box nobody claimed has nowhere to deliver an event, so hit testing
        // passes through it rather than returning something undeliverable.
        var child = Box.filled(0xFF00FF00).size(StyleLength.points(40), StyleLength.points(40));
        var root = Box.filled(0xFFFF0000).padding(StyleLength.points(20))
                .children(child).owner("root");

        assertEquals("root", HitTest.at(capture(root, 100, 100, 1.0f), 30, 30).orElseThrow());
    }

    @Test
    @DisplayName("regions are in logical coordinates, not physical")
    void logicalCoordinates() {
        // At 200% a 100x100 physical frame is 50x50 logical. A pointer position
        // an application sees is logical, so the regions have to be too --
        // otherwise every hit test is out by the display scale.
        var root = Box.filled(0xFFFF0000).owner("root");
        var regions = capture(root, 100, 100, 2.0f);

        var region = regions.getFirst();
        assertEquals(50, region.width(), 0.01);
        assertEquals(50, region.height(), 0.01);
        assertTrue(HitTest.at(regions, 25, 25).isPresent());
    }

    @Test
    @DisplayName("regions are recorded parents first, as a paint draws them")
    void paintOrder() {
        var child = Box.filled(0xFF00FF00).size(StyleLength.points(10), StyleLength.points(10))
                .owner("child");
        var root = Box.filled(0xFFFF0000).children(child).owner("root");

        var owners = capture(root, 50, 50, 1.0f).stream().map(HitTest.Region::owner).toList();

        // The order is what lets `at` take the last match as the topmost.
        assertEquals(List.of("root", "child"), owners);
    }
}
