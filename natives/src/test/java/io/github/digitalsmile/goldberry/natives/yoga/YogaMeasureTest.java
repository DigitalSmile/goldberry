package io.github.digitalsmile.goldberry.natives.yoga;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The measure callback driven by a real layout pass.
///
/// [MeasureUpcallTest] proved the crossing itself — that a `YGSize` returned by
/// value from Java arrives in C intact — by calling the stub from a C function
/// written for the purpose. What it could not show is that *Yoga* calls it: that
/// the pointer handed to `YGNodeSetMeasureFunc` is the one Yoga invokes, with
/// the constraints the algorithm arrived at, and that what comes back is what
/// ends up in the computed layout.
///
/// That is this file, and it is the last open question in the M0 milestone. It
/// is also the shape the whole text stack will take: a paragraph is a leaf that
/// shapes its text at whatever width Yoga proposes and reports how tall it came
/// out.
class YogaMeasureTest {

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    @Test
    @DisplayName("Yoga sizes a measured leaf from what the callback returns")
    void measuredSizeBecomesTheLayout() {
        try (var root = YogaNode.create()) {
            root.setMeasureFunction((width, widthMode, height, heightMode) ->
                    new MeasuredSize(123f, 45f));

            root.calculateLayout(YogaNode.UNDEFINED, YogaNode.UNDEFINED);

            assertEquals(new ComputedLayout(0f, 0f, 123f, 45f), root.layout());
        }
    }

    @Test
    @DisplayName("the constraints Yoga passes are the ones the algorithm arrived at")
    void constraintsComeFromTheLayoutAlgorithm() {
        var calls = new ArrayList<String>();
        try (var config = YogaConfig.create();
                var root = YogaNode.create(config)) {

            root.setWidth(StyleLength.points(200f));
            root.setPadding(Edge.ALL, StyleLength.points(10f));

            var leaf = YogaNode.create(config);
            leaf.setMeasureFunction((width, widthMode, height, heightMode) -> {
                calls.add(width + "/" + widthMode);
                return new MeasuredSize(Math.min(width, 80f), 24f);
            });
            root.addChild(leaf);

            root.calculateLayout(200f, YogaNode.UNDEFINED);

            // 200 less 10 of padding on each side. Nothing on the Java side
            // computed that number -- Yoga did, and passed it back through the
            // upcall.
            assertTrue(calls.contains("180.0/AT_MOST"), "constraints seen: " + calls);
            assertEquals(24f, leaf.layout().height(), "the height the callback reported");
            assertEquals(10f, leaf.layout().top(), "inside the padding");
        }
    }

    @Test
    @DisplayName("a measure function that throws surfaces from the layout call")
    void aThrowingMeasureFunctionSurfaces() {
        var boom = new IllegalStateException("no font loaded");
        try (var root = YogaNode.create()) {
            root.setMeasureFunction((width, widthMode, height, heightMode) -> {
                throw boom;
            });

            // Reaching the assertion at all is half the result: had the
            // exception escaped into Yoga, the process would be gone.
            var thrown = assertThrows(
                    IllegalStateException.class,
                    () -> root.calculateLayout(YogaNode.UNDEFINED, YogaNode.UNDEFINED));

            assertSame(boom, thrown, "the caller's own exception, not a wrapper");
        }
    }

    @Test
    @DisplayName("when several callbacks fail, none of them is left pending")
    void everyFailureIsReportedOnce() {
        var first = new IllegalStateException("first leaf");
        var second = new IllegalStateException("second leaf");
        try (var config = YogaConfig.create();
                var root = YogaNode.create(config)) {

            root.setFlexDirection(FlexDirection.ROW);
            root.addChild(throwing(config, first));
            root.addChild(throwing(config, second));

            var thrown = assertThrows(
                    IllegalStateException.class, () -> root.calculateLayout(200f, 100f));

            assertSame(first, thrown, "the first in tree order");
            assertEquals(
                    List.of(second),
                    List.of(thrown.getSuppressed()),
                    "and the rest attached rather than left to surface from the next pass");

            // The proof that nothing is still held: a pass whose callbacks all
            // succeed must not fail with an exception from the one before it.
            for (var child : root.children()) {
                child.setMeasureFunction((w, wm, h, hm) -> new MeasuredSize(10f, 10f));
            }
            assertDoesNotThrow(() -> root.calculateLayout(200f, 100f));
        }
    }

    private static YogaNode throwing(YogaConfig config, RuntimeException failure) {
        var node = YogaNode.create(config);
        node.setMeasureFunction((width, widthMode, height, heightMode) -> {
            throw failure;
        });
        return node;
    }

    @Test
    @DisplayName("marking a leaf dirty is what makes Yoga ask again")
    void markDirtyForcesAFreshMeasurement() {
        var height = new float[] {20f};
        try (var root = YogaNode.create()) {
            root.setMeasureFunction((width, widthMode, heightIn, heightMode) ->
                    new MeasuredSize(50f, height[0]));

            root.calculateLayout(YogaNode.UNDEFINED, YogaNode.UNDEFINED);
            assertEquals(20f, root.layout().height(), "the first measurement");

            // A style change dirties a node by itself. A *content* change is
            // invisible to Yoga, which is the whole reason this call exists.
            height[0] = 60f;
            root.markDirty();
            assertTrue(root.isDirty(), "dirty until the next pass");

            root.calculateLayout(YogaNode.UNDEFINED, YogaNode.UNDEFINED);

            assertEquals(60f, root.layout().height(), "measured again");
            assertFalse(root.isDirty(), "and clean once the pass has run");
        }
    }

    @Test
    @DisplayName("marking a node with no measure function dirty is refused")
    void markDirtyNeedsAMeasureFunction() {
        try (var root = YogaNode.create()) {
            // Yoga aborts on this, which takes the JVM with it.
            var thrown = assertThrows(IllegalStateException.class, root::markDirty);

            assertTrue(thrown.getMessage().contains("measure function"), thrown.getMessage());
        }
    }

    @Test
    @DisplayName("a measured node may not have children, in either order")
    void measuredNodesAreLeaves() {
        try (var config = YogaConfig.create();
                var root = YogaNode.create(config)) {

            root.setMeasureFunction((w, wm, h, hm) -> new MeasuredSize(1f, 1f));
            var child = YogaNode.create(config);

            assertThrows(IllegalStateException.class, () -> root.addChild(child));

            root.setMeasureFunction(null);
            root.addChild(child);

            assertThrows(
                    IllegalStateException.class,
                    () -> root.setMeasureFunction((w, wm, h, hm) -> new MeasuredSize(1f, 1f)));
        }
    }

    @Test
    @DisplayName("Yoga agrees about whether a measure function is attached")
    void yogaAgreesAboutTheMeasureFunction() {
        try (var root = YogaNode.create()) {
            assertFalse(root.hasMeasureFunction());
            assertFalse(root.nativeHasMeasureFunction(), "and so does Yoga");

            root.setMeasureFunction((w, wm, h, hm) -> new MeasuredSize(1f, 1f));
            assertTrue(root.hasMeasureFunction());
            assertTrue(root.nativeHasMeasureFunction(), "the stub reached Yoga");

            root.setMeasureFunction(null);
            assertFalse(root.hasMeasureFunction());
            assertFalse(root.nativeHasMeasureFunction(), "and clearing reached it too");
        }
    }

    @Test
    @DisplayName("replacing a measure function does not leave the old one wired up")
    void replacingAMeasureFunctionSwapsTheStub() {
        try (var root = YogaNode.create()) {
            root.setMeasureFunction((w, wm, h, hm) -> new MeasuredSize(10f, 10f));
            root.calculateLayout(YogaNode.UNDEFINED, YogaNode.UNDEFINED);
            assertEquals(10f, root.layout().width());

            // The old callback's arena is closed here, so if Yoga were still
            // holding its stub this pass would call freed memory.
            root.setMeasureFunction((w, wm, h, hm) -> new MeasuredSize(30f, 30f));
            root.markDirty();
            root.calculateLayout(YogaNode.UNDEFINED, YogaNode.UNDEFINED);

            assertEquals(30f, root.layout().width(), "the replacement measured it");
        }
    }
}
