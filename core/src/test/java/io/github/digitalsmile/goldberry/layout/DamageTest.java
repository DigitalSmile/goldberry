package io.github.digitalsmile.goldberry.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.backend.DamageRect;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Which parts of a frame differ from the last one — ADR-0071.
///
/// Damage is advisory: getting it wrong shows a stale region rather than a
/// corrupt one, which makes it the kind of thing that is quietly wrong for
/// months. So the tests are about the two ways it goes wrong — reporting *too
/// little*, which leaves the old drawing on screen, and reporting the whole
/// window every frame, which is the same as not having it.
class DamageTest {

    private TestFrames.Target target;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        target = TestFrames.of(200, 200, 1.0f, 0);
    }

    @AfterEach
    void tearDown() {
        if (target != null) {
            target.end();
        }
    }

    private static Box tree(int firstColour, float firstHeight) {
        return Box.filled(0xFF000000)
                .size(StyleLength.points(200), StyleLength.points(200))
                .direction(FlexDirection.COLUMN)
                .children(
                        Box.filled(firstColour)
                                .size(StyleLength.points(50), StyleLength.points(firstHeight)),
                        Box.filled(0xFF00FF00)
                                .size(StyleLength.points(50), StyleLength.points(20)));
    }

    private static boolean covers(List<DamageRect> damage, int x, int y) {
        return damage.stream().anyMatch(r ->
                x >= r.x() && x < r.x() + r.width() && y >= r.y() && y < r.y() + r.height());
    }

    @Test
    @DisplayName("the first frame damages everything, because there is no last one")
    void firstFrame() {
        try (var render = RenderTree.create()) {
            render.update(target.frame(), tree(0xFFFF0000, 20));
            assertEquals(List.of(DamageRect.all(target.frame().pixelSize())),
                    render.damage(target.frame()));
        }
    }

    @Test
    @DisplayName("a frame that changed nothing damages nothing")
    void nothingChanged() {
        try (var render = RenderTree.create()) {
            var box = tree(0xFFFF0000, 20);
            render.update(target.frame(), box);
            render.damage(target.frame());

            render.update(target.frame(), box);
            assertTrue(render.damage(target.frame()).isEmpty(),
                    "a static window should upload nothing at all");
        }
    }

    @Test
    @DisplayName("a colour change damages that box and not the window")
    void oneBoxChanged() {
        try (var render = RenderTree.create()) {
            render.update(target.frame(), tree(0xFFFF0000, 20));
            render.damage(target.frame());

            render.update(target.frame(), tree(0xFF0000FF, 20));
            var damage = render.damage(target.frame());

            assertTrue(covers(damage, 25, 10), "the box that changed colour");
            // The second child is at y=20..40 and did not change. Reporting the
            // whole window here would be correct and useless, which is the
            // failure this asserts against.
            var area = damage.stream().mapToInt(r -> r.width() * r.height()).sum();
            assertTrue(area < 200 * 200 / 2,
                    () -> "damaged " + area + " of 40000 pixels for one 50x20 box");
        }
    }

    @Test
    @DisplayName("a box that moved damages where it was as well as where it is")
    void movedBox() {
        // The classic partial-repaint artefact: damage only the new position and
        // the old drawing stays on screen forever. Growing the first child pushes
        // the second one down, so the second one's *old* rectangle has to be in
        // the damage even though nothing about the second one changed.
        try (var render = RenderTree.create()) {
            render.update(target.frame(), tree(0xFFFF0000, 20));
            render.damage(target.frame());

            render.update(target.frame(), tree(0xFFFF0000, 60));
            var damage = render.damage(target.frame());

            assertTrue(covers(damage, 25, 25),
                    "the hole the second child left behind is not being repainted");
            assertTrue(covers(damage, 25, 70), "nor is where it went");
        }
    }

    @Test
    @DisplayName("a resize never damages outside the frame it is presenting")
    void resizeStaysInsideTheFrame() {
        // The bug this is here for, seen while dragging a window's edge:
        //
        //     damage 2066x1103+0+0 falls outside the 2065x1102 px frame
        //
        // A node's remembered rectangle was measured against the *previous*
        // frame. Dragging a window smaller gives one a pixel narrower, and the
        // union of where a node was and where it is then fits neither — so the
        // backend refuses the frame and the event loop ends, mid-drag.
        //
        // One pixel, because that is what a drag actually produces and because a
        // fix that only handled large jumps would still fail on the real case.
        var smaller = TestFrames.of(199, 199, 1.0f, 0);
        try (var render = RenderTree.create()) {
            render.update(target.frame(), tree(0xFFFF0000, 20));
            render.damage(target.frame());

            render.update(smaller.frame(), tree(0xFFFF0000, 20));
            var damage = render.damage(smaller.frame());

            var size = smaller.frame().pixelSize();
            for (var rect : damage) {
                assertTrue(rect.x() >= 0 && rect.y() >= 0
                                && rect.x() + rect.width() <= size.width()
                                && rect.y() + rect.height() <= size.height(),
                        () -> "damage " + rect + " falls outside the " + size + " frame");
            }
        } finally {
            smaller.end();
        }
    }

    @Test
    @DisplayName("and neither does a window that grew")
    void growStaysInsideTheFrame() {
        // The other direction, which is safe by construction today and is
        // asserted so it stays that way: every rectangle is clipped to the frame
        // being presented, whichever way the window moved.
        var larger = TestFrames.of(260, 260, 1.0f, 0);
        try (var render = RenderTree.create()) {
            render.update(target.frame(), tree(0xFFFF0000, 20));
            render.damage(target.frame());

            render.update(larger.frame(), tree(0xFFFF0000, 20));
            var damage = render.damage(larger.frame());

            var size = larger.frame().pixelSize();
            for (var rect : damage) {
                assertTrue(rect.x() + rect.width() <= size.width()
                                && rect.y() + rect.height() <= size.height(),
                        () -> "damage " + rect + " falls outside the " + size + " frame");
            }
        } finally {
            larger.end();
        }
    }

    @Test
    @DisplayName("scattered changes fall back to the whole frame rather than a list")
    void tooManyRegions() {
        // Past a handful of rectangles the bookkeeping costs more than the upload
        // it saves, so the answer becomes "all of it" — stated behaviour rather
        // than an unbounded list.
        try (var render = RenderTree.create()) {
            render.update(target.frame(), scattered(0xFFFF0000));
            render.damage(target.frame());

            render.update(target.frame(), scattered(0xFF0000FF));
            assertEquals(List.of(DamageRect.all(target.frame().pixelSize())),
                    render.damage(target.frame()));
        }
    }

    @Test
    @DisplayName("a clipped repaint is pixel-identical to a full one")
    void partialRepaintMatchesFull() {
        // The invariant the whole second half of damage tracking rests on. If a
        // clipped frame differs from a full one anywhere, damage is not an
        // optimisation, it is a rendering bug with a performance excuse.
        //
        // Both trees start from the same first frame -- which is what a real
        // window does, and what makes "the buffer already holds last frame's
        // pixels" true for the clipped one.
        var full = TestFrames.of(200, 200, 1.0f, 0);
        var clipped = TestFrames.of(200, 200, 1.0f, 0);
        try (var a = RenderTree.create(); var b = RenderTree.create()) {
            a.update(full.frame(), tree(0xFFFF0000, 20));
            a.paint(full.frame());
            a.damage(full.frame());

            b.update(clipped.frame(), tree(0xFFFF0000, 20));
            b.paint(clipped.frame());
            b.damage(clipped.frame());

            // Second frame: one box changes colour. One repaints everything, the
            // other only the damage.
            a.update(full.frame(), tree(0xFF0000FF, 20));
            a.damage(full.frame());
            a.paint(full.frame());

            b.update(clipped.frame(), tree(0xFF0000FF, 20));
            b.paint(clipped.frame(), b.damage(clipped.frame()));
        } finally {
            full.end();
            clipped.end();
        }

        for (var y = 0; y < 200; y++) {
            for (var x = 0; x < 200; x++) {
                var expected = full.pixel(x, y);
                var actual = clipped.pixel(x, y);
                if (expected != actual) {
                    throw new AssertionError(
                            "a clipped repaint differs at (" + x + ", " + y + "): full #"
                                    + Integer.toHexString(expected) + ", clipped #"
                                    + Integer.toHexString(actual));
                }
            }
        }
    }

    @Test
    @DisplayName("empty damage draws nothing at all")
    void emptyDamageDrawsNothing() {
        // The best case rather than a degenerate one: a window sitting still
        // costs no rasterization. Asserted by painting a *different* tree under
        // empty damage and finding the old pixels untouched.
        try (var render = RenderTree.create()) {
            render.update(target.frame(), tree(0xFFFF0000, 20));
            render.paint(target.frame());
            render.damage(target.frame());

            render.update(target.frame(), tree(0xFF0000FF, 20));
            render.paint(target.frame(), List.of());
        } finally {
            target.end();
        }

        assertEquals(0xFFFF0000, target.pixel(25, 10),
                "empty damage rasterized something anyway");
    }

    /// Twelve small boxes spread across the frame, none touching another.
    private static Box scattered(int colour) {
        var children = new Box[12];
        for (var i = 0; i < children.length; i++) {
            children[i] = Box.filled(colour)
                    .size(StyleLength.points(4), StyleLength.points(4));
        }
        return Box.filled(0xFF000000)
                .size(StyleLength.points(200), StyleLength.points(200))
                .direction(FlexDirection.COLUMN)
                .gap(StyleLength.points(12))
                .children(children);
    }
}
