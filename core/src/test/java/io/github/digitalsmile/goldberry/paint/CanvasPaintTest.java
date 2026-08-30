package io.github.digitalsmile.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.value.Transform;
import io.github.digitalsmile.goldberry.natives.yoga.Insets;
import io.github.digitalsmile.goldberry.natives.yoga.style.StyleLength;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;

/// What a `canvas`'s painter is handed, and what it cannot do with it.
///
/// The three guarantees are the whole of the primitive: the painter draws from
/// its own origin, it cannot draw outside its box, and whatever it leaves on the
/// context is undone
/// ([ADR-0193](../../../../../../book/src/adr/0193-a-canvas-is-a-second-clip-depth.md)).
/// Each is asserted in pixels rather than by inspecting calls, because each is a
/// claim about what ends up on the screen.
class CanvasPaintTest {

    @BeforeAll
    static void requireRenderer() {
        RendererRequirement.enforce();
    }

    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;
    private static final int BLUE = 0xFF88C0D0;

    /// A 40×40 box at (20, 20) in a 100×100 frame, holding `painter`.
    private static Box scene(Painter painter) {
        return Box.of()
                .size(StyleLength.points(100), StyleLength.points(100))
                .children(Box.of()
                        .size(StyleLength.points(40), StyleLength.points(40))
                        .inset(new Insets(
                                StyleLength.points(20),
                                StyleLength.UNDEFINED,
                                StyleLength.UNDEFINED,
                                StyleLength.points(20)))
                        .position(io.github.digitalsmile.goldberry.natives.yoga.style.PositionType.ABSOLUTE)
                        .painting(painter));
    }

    @Test
    @DisplayName("the painter's origin is the canvas's corner, not the window's")
    void originIsTheCanvasCorner() {
        var target = TestFrames.of(100, 100, 1.0f);
        try {
            target.frame().fill(WHITE);
            // Ten pixels from the painter's own origin. If the frame were not
            // translated this would land at (10,10) in the window instead.
            BoxPainter.paint(target.frame(), scene((frame, size) -> frame.fillRect(0, 0, 10, 10, BLUE)));
        } finally {
            target.end();
        }

        assertEquals(BLUE, target.pixel(25, 25), "inside the painted square");
        assertEquals(
                WHITE, target.pixel(10, 10), "where the square would be if the painter drew in window coordinates");
    }

    @Test
    @DisplayName("the painter is told the size the layout gave it")
    void sizeIsTheLaidOutSize() {
        var seen = new ArrayList<LogicalSize>();
        var target = TestFrames.of(100, 100, 1.0f);
        try {
            BoxPainter.paint(target.frame(), scene((frame, size) -> seen.add(size)));
        } finally {
            target.end();
        }

        assertEquals(List.of(new LogicalSize(40, 40)), seen);
    }

    @Test
    @DisplayName("a painter cannot draw outside its own box")
    void theCanvasIsClipped() {
        var target = TestFrames.of(100, 100, 1.0f);
        try {
            target.frame().fill(WHITE);
            // Deliberately wrong arithmetic: a painter that thinks it owns the
            // window. It gets its own 40x40 and nothing else.
            BoxPainter.paint(target.frame(), scene((frame, size) -> frame.fillRect(-50, -50, 200, 200, BLUE)));
        } finally {
            target.end();
        }

        assertEquals(BLUE, target.pixel(20, 20), "its own top-left corner");
        assertEquals(BLUE, target.pixel(59, 59), "its own bottom-right corner");
        assertEquals(WHITE, target.pixel(19, 19), "one pixel outside it");
        assertEquals(WHITE, target.pixel(60, 60), "and one pixel past the far edge");
    }

    @Test
    @DisplayName("it draws inside the padding, like every other content")
    void paddingIsSurfaceNotCanvas() {
        var target = TestFrames.of(100, 100, 1.0f);
        try {
            target.frame().fill(WHITE);
            var box = Box.of()
                    .size(StyleLength.points(100), StyleLength.points(100))
                    .padding(new Insets(
                            StyleLength.points(10),
                            StyleLength.points(10),
                            StyleLength.points(10),
                            StyleLength.points(10)))
                    .painting((frame, size) -> frame.fillRect(0, 0, size.width(), size.height(), BLUE));
            BoxPainter.paint(target.frame(), box);
        } finally {
            target.end();
        }

        assertEquals(WHITE, target.pixel(5, 5), "the padding is the box's surface");
        assertEquals(BLUE, target.pixel(10, 10), "and the canvas starts inside it");
        assertEquals(BLUE, target.pixel(89, 89), "up to the far padding edge");
        assertEquals(WHITE, target.pixel(95, 95), "which is surface again");
    }

    @Test
    @DisplayName("whatever the painter leaves on the context is undone")
    void thePainterCannotDamageTheFrame() {
        var target = TestFrames.of(100, 100, 1.0f);
        try {
            target.frame().fill(WHITE);
            // A painter behaving as badly as it is allowed to: it clips to a
            // sliver, moves the origin and fades everything, and never puts any
            // of it back.
            var box = Box.of()
                    .size(StyleLength.points(100), StyleLength.points(100))
                    .children(
                            Box.of()
                                    .size(StyleLength.points(50), StyleLength.points(20))
                                    .painting((frame, size) -> {
                                        frame.clipTo(0, 0, 2, 2);
                                        frame.transform(1, 0, 0, 1, 30, 30);
                                        frame.fillRect(0, 0, 100, 100, BLUE);
                                    }),
                            Box.filled(BLACK).size(StyleLength.points(50), StyleLength.points(20)));
            BoxPainter.paint(target.frame(), box);
        } finally {
            target.end();
        }

        // The box painted *after* the canvas is whole. Without the restore it
        // would be clipped to the painter's 2x2 sliver and offset by its
        // translation, which is to say invisible. A row, so it sits beside the
        // canvas rather than under it: x 50..100, y 0..20.
        assertEquals(BLACK, target.pixel(55, 10), "the box after the canvas is drawn in full");
        assertEquals(BLACK, target.pixel(95, 19), "including its far corner");
    }

    @Test
    @DisplayName("a canvas moves with a transform above it, which is what scrolling is")
    void theCanvasMovesWithItsAncestors() {
        var target = TestFrames.of(100, 100, 1.0f);
        try {
            target.frame().fill(WHITE);
            // What a `scroll` does to its content: the viewport clips, and the
            // content is *translated* rather than laid out somewhere else
            // (ScrollContent). Scrolled down by 15, so the canvas at y=20 draws
            // at y=5.
            var box = Box.of()
                    .size(StyleLength.points(100), StyleLength.points(100))
                    .children(Box.of()
                            .size(StyleLength.points(100), StyleLength.points(100))
                            .transform(Transform.of(
                                    new Transform.Function.Translate(Transform.Length.px(0), Transform.Length.px(-15))))
                            .children(Box.of()
                                    .size(StyleLength.points(40), StyleLength.points(40))
                                    .inset(new Insets(
                                            StyleLength.points(20),
                                            StyleLength.UNDEFINED,
                                            StyleLength.UNDEFINED,
                                            StyleLength.points(20)))
                                    .position(io.github.digitalsmile.goldberry.natives.yoga.style.PositionType.ABSOLUTE)
                                    .painting(
                                            (frame, size) -> frame.fillRect(0, 0, size.width(), size.height(), BLUE))));
            BoxPainter.paint(target.frame(), box);
        } finally {
            target.end();
        }

        // `Frame.transform` assigns rather than composes, so a canvas that
        // spelled only its own translation would draw at the position it was
        // laid out at and sit still while the panel scrolled under it.
        assertEquals(BLUE, target.pixel(25, 10), "the canvas is drawn 15 up from its layout");
        assertEquals(BLUE, target.pixel(25, 44), "down to its scrolled bottom edge");
        assertEquals(WHITE, target.pixel(25, 50), "and not where it was laid out");
    }

    @Test
    @DisplayName("a canvas with no room does not call its painter")
    void nothingToDrawIntoIsNotAnError() {
        var called = new ArrayList<String>();
        var target = TestFrames.of(100, 100, 1.0f);
        try {
            var box = Box.of()
                    .size(StyleLength.points(100), StyleLength.points(100))
                    .children(Box.of()
                            .size(StyleLength.points(0), StyleLength.points(0))
                            .painting((frame, size) -> called.add("painted")));
            BoxPainter.paint(target.frame(), box);
        } finally {
            target.end();
        }

        // A collapsed split pane and a zero-height row both produce this, and
        // `clipTo` refuses a non-positive size -- so it is skipped rather than
        // being an exception an application never asked for.
        assertTrue(called.isEmpty(), "a canvas laid out to nothing has nothing to paint into");
    }

    @Test
    @DisplayName("a painter that throws restores the context on its way out")
    void aThrowingPainterStillRestores() {
        var target = TestFrames.of(100, 100, 1.0f);
        try {
            target.frame().fill(WHITE);
            assertThrows(
                    IllegalStateException.class,
                    () -> BoxPainter.paint(target.frame(), scene((frame, size) -> {
                        frame.clipTo(0, 0, 1, 1);
                        throw new IllegalStateException("an application bug");
                    })),
                    "the exception is the application's and is not swallowed");

            // The frame is still usable, and unclipped: a painter's bug must not
            // turn into a window that draws wrong from then on.
            target.frame().fillRect(0, 0, 100, 100, BLACK);
        } finally {
            target.end();
        }

        assertEquals(BLACK, target.pixel(90, 90), "the clip the painter left behind did not survive it");
    }
}
