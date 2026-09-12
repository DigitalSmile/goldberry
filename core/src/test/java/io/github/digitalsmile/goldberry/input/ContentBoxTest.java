package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.layout.Insets;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// Where a pointer is inside the rectangle a painter was actually given.
///
/// ## Why this exists
///
/// `BoxPainter` hands a `canvas` painter a frame whose origin is the **content**
/// corner — inside the padding — and clips it there, so that
/// `canvas { padding: 8px }` is a framed drawing surface rather than a surprise
/// (ADR-0193). The hit-test snapshot recorded only the border box, so a canvas
/// reading `local()` had every event offset from its own ink by exactly the
/// padding: a press eight pixels from where it was drawn, silently, on the one
/// widget whose whole job is to be drawn on (ADR-0281).
///
/// `PointerEvent.content()` is the other half. These assertions are the
/// arithmetic; `CanvasInputTest` in `:widgets` is the widget that reads it.
class ContentBoxTest {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 120;

    private PointerRouter router;
    private Element element;
    private Recorder widget;

    /// A leaf that keeps the last pointer event it was handed.
    private static final class Recorder implements Widget.Leaf, Styled, Handles {
        private PointerEvent last;

        @Override
        public String cssType() {
            return "recorder";
        }

        @Override
        public void onPointer(PointerEvent event) {
            last = event;
        }
    }

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        router = new PointerRouter();
        widget = new Recorder();
        element = new ElementTree(widget).root();
    }

    /// Paints `box` — tagged as the recorder's — and hands the router the regions
    /// that paint produced, which is the snapshot a real frame leaves behind.
    private void show(Box box) {
        var target = TestFrames.of(WIDTH, HEIGHT, 1.0f);
        try {
            router.updateRegions(HitTest.capture(target.frame(), box.owner(element)));
        } finally {
            target.end();
        }
    }

    @Nested
    @DisplayName("the snapshot")
    class Snapshot {

        @Test
        @DisplayName("a box with padding records a content rectangle inside it")
        void paddingShrinksTheContentBox() {
            var target = TestFrames.of(WIDTH, HEIGHT, 1.0f);
            List<HitTest.Region> regions;
            try {
                regions = HitTest.capture(
                        target.frame(),
                        Box.of()
                                .owner(element)
                                .size(Length.points(100), Length.points(60))
                                .padding(Insets.all(Length.points(8))));
            } finally {
                target.end();
            }

            var region = regions.stream()
                    .filter(candidate -> candidate.owner() == element)
                    .findFirst()
                    .orElseThrow();

            assertEquals(0, region.left(), 0.01, "the border box is where it always was");
            assertEquals(100, region.width(), 0.01);
            assertEquals(8, region.content().left(), 0.01, "and the content box is inside the padding");
            assertEquals(8, region.content().top(), 0.01);
            assertEquals(84, region.content().width(), 0.01);
            assertEquals(44, region.content().height(), 0.01);
        }

        @Test
        @DisplayName("a box with no padding records the same rectangle twice")
        void noPaddingIsNoDifference() {
            // Which is most boxes, and the reason this costs nothing to have.
            var target = TestFrames.of(WIDTH, HEIGHT, 1.0f);
            List<HitTest.Region> regions;
            try {
                regions = HitTest.capture(
                        target.frame(), Box.of().owner(element).size(Length.points(100), Length.points(60)));
            } finally {
                target.end();
            }

            var region = regions.stream()
                    .filter(candidate -> candidate.owner() == element)
                    .findFirst()
                    .orElseThrow();

            assertEquals(region.left(), region.content().left(), 0.01);
            assertEquals(region.width(), region.content().width(), 0.01);
        }

        @Test
        @DisplayName("padding larger than the box leaves a content box of nothing, not a negative one")
        void paddingCannotInvertTheContentBox() {
            // A collapsed split pane produces this. A negative extent would make
            // every fraction a caller computes from it a nonsense.
            var target = TestFrames.of(WIDTH, HEIGHT, 1.0f);
            List<HitTest.Region> regions;
            try {
                regions = HitTest.capture(
                        target.frame(),
                        Box.of()
                                .owner(element)
                                .size(Length.points(10), Length.points(10))
                                .padding(Insets.all(Length.points(40))));
            } finally {
                target.end();
            }

            var region = regions.stream()
                    .filter(candidate -> candidate.owner() == element)
                    .findFirst()
                    .orElseThrow();

            assertEquals(0, region.content().width(), 0.01);
            assertEquals(0, region.content().height(), 0.01);
        }
    }

    @Nested
    @DisplayName("what a handler is told")
    class Events {

        @Test
        @DisplayName("content() is measured from the padding, local() from the border box")
        void bothAreReported() {
            show(Box.of().size(Length.points(100), Length.points(60)).padding(Insets.all(Length.points(8))));

            router.pointerMoved(30, 30);

            // The same pointer, two honest answers: 30 from the box's corner and
            // 22 from the corner the painter draws at.
            assertEquals(30, widget.last.local().x(), 0.01);
            assertEquals(22, widget.last.content().x(), 0.01);
            assertEquals(22, widget.last.content().y(), 0.01);
        }

        @Test
        @DisplayName("content() reports the drawing's extent, not the box's")
        void extentIsTheDrawings() {
            show(Box.of().size(Length.points(100), Length.points(60)).padding(Insets.all(Length.points(8))));

            router.pointerMoved(30, 30);

            assertEquals(100, widget.last.local().width(), 0.01);
            assertEquals(84, widget.last.content().width(), 0.01);
            assertEquals(44, widget.last.content().height(), 0.01);
        }

        @Test
        @DisplayName("a percentage padding resolves per axis, exactly as the painter's does")
        void percentagePadding() {
            show(Box.of().size(Length.points(100), Length.points(60)).padding(Insets.all(Length.percent(10))));

            router.pointerMoved(30, 30);

            // **Each axis against its own dimension**: 10% of the 100 wide on the
            // left, 10% of the 60 tall on the top. CSS resolves all four against
            // the containing block's *width*, so this diverges — but it is
            // `BoxPainter.paintCanvas`'s own arithmetic, reached through the same
            // `Length.resolve`, and what matters here is that input and paint
            // agree. They cannot drift, because there is one implementation.
            assertEquals(20, widget.last.content().x(), 0.01);
            assertEquals(24, widget.last.content().y(), 0.01);
        }

        @Test
        @DisplayName("a press outside the content box still reports, with a negative coordinate")
        void pressOnThePadding() {
            // The padding is part of the box, so the event belongs to it -- and a
            // canvas that wants to ignore ink outside its drawing can see that it
            // is outside rather than having it clamped to an edge it never
            // touched.
            show(Box.of().size(Length.points(100), Length.points(60)).padding(Insets.all(Length.points(8))));

            router.pointerMoved(3, 3);

            assertEquals(-5, widget.last.content().x(), 0.01);
            assertEquals(-5, widget.last.content().y(), 0.01);
        }
    }
}
