package io.github.digitalsmile.goldberry.natives.yoga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Real layout passes through the bound node API.
///
/// The point is not to test Yoga — Meta does that — but to test the binding: a
/// wrong enum value, a swapped argument or a descriptor of the wrong width all
/// produce a layout that is plausible and wrong, which is precisely what a
/// compile-time check cannot catch. Every assertion here is a number a human can
/// verify by hand, so a binding that drifts fails with an arithmetic difference
/// rather than a crash.
class YogaLayoutTest {

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    @Test
    @DisplayName("two grown children split a row down the middle")
    void flexGrowSplitsTheMainAxis() {
        try (var config = YogaConfig.create();
                var root = YogaNode.create(config)) {

            root.setFlexDirection(FlexDirection.ROW);
            var left = YogaNode.create(config);
            var right = YogaNode.create(config);
            left.setFlexGrow(1f);
            right.setFlexGrow(1f);
            root.addChild(left);
            root.addChild(right);

            root.calculateLayout(200f, 100f);

            // The cross axis fills because `align-items: stretch` is CSS's
            // default and YogaConfig turns web defaults on.
            assertEquals(new ComputedLayout(0f, 0f, 100f, 100f), left.layout(), "left");
            assertEquals(new ComputedLayout(100f, 0f, 100f, 100f), right.layout(), "right");
            assertEquals(new ComputedLayout(0f, 0f, 200f, 100f), root.layout(), "root");
        }
    }

    @Test
    @DisplayName("padding insets the content box on all four sides")
    void paddingInsetsChildren() {
        try (var config = YogaConfig.create();
                var root = YogaNode.create(config)) {

            root.setPadding(Edge.ALL, StyleLength.points(10f));
            var child = YogaNode.create(config);
            child.setFlexGrow(1f);
            root.addChild(child);

            root.calculateLayout(200f, 100f);

            assertEquals(new ComputedLayout(10f, 10f, 180f, 80f), child.layout());
            // The shorthand resolved to four physical sides, which is what the
            // computed getters answer for.
            assertEquals(10f, root.layoutPadding(Edge.LEFT), "left");
            assertEquals(10f, root.layoutPadding(Edge.BOTTOM), "bottom");
        }
    }

    @Test
    @DisplayName("a gap is taken out of the space before it is shared")
    void gapIsTakenBeforeGrowing() {
        try (var config = YogaConfig.create();
                var root = YogaNode.create(config)) {

            root.setFlexDirection(FlexDirection.ROW);
            // COLUMN is the gap *between columns*, so it is horizontal space.
            root.setGap(Gutter.COLUMN, StyleLength.points(20f));
            var left = YogaNode.create(config);
            var right = YogaNode.create(config);
            left.setFlexGrow(1f);
            right.setFlexGrow(1f);
            root.addChild(left);
            root.addChild(right);

            root.calculateLayout(200f, 100f);

            assertEquals(90f, left.layout().width(), "left width");
            assertEquals(110f, right.layout().left(), "right offset");
            assertEquals(90f, right.layout().width(), "right width");
        }
    }

    @Test
    @DisplayName("a percentage resolves against the parent")
    void percentResolvesAgainstTheParent() {
        try (var config = YogaConfig.create();
                var root = YogaNode.create(config)) {

            var child = YogaNode.create(config);
            child.setWidth(StyleLength.percent(25f));
            child.setHeight(StyleLength.percent(50f));
            root.addChild(child);

            root.calculateLayout(200f, 100f);

            assertEquals(50f, child.layout().width(), "width");
            assertEquals(50f, child.layout().height(), "height");
        }
    }

    @Test
    @DisplayName("a percentage resolves against the content box, not the border box")
    void percentResolvesAgainstTheContentBox() {
        try (var config = YogaConfig.create();
                var root = YogaNode.create(config)) {

            root.setFlexDirection(FlexDirection.ROW);
            root.setPadding(Edge.ALL, StyleLength.points(8f));
            var sidebar = YogaNode.create(config);
            sidebar.setWidth(StyleLength.percent(25f));
            root.addChild(sidebar);

            root.calculateLayout(960f, 640f);

            // 25% of 944 -- the 960 less 8 of padding on each side -- and not
            // 25% of 960. The distinction is the whole difference between a
            // sidebar that fits and one that pushes the content off the edge.
            assertEquals(new ComputedLayout(8f, 8f, 236f, 624f), sidebar.layout());
        }
    }

    @Test
    @DisplayName("auto margins absorb the free space on both sides")
    void autoMarginsCentre() {
        try (var config = YogaConfig.create();
                var root = YogaNode.create(config)) {

            root.setFlexDirection(FlexDirection.ROW);
            var child = YogaNode.create(config);
            child.setWidth(StyleLength.points(50f));
            child.setMargin(Edge.LEFT, StyleLength.AUTO);
            child.setMargin(Edge.RIGHT, StyleLength.AUTO);
            root.addChild(child);

            root.calculateLayout(200f, 100f);

            assertEquals(75f, child.layout().left(), "centred");
            assertEquals(50f, child.layout().width(), "width");
        }
    }

    @Test
    @DisplayName("undefined puts a property back to its default")
    void undefinedClearsAProperty() {
        try (var config = YogaConfig.create();
                var root = YogaNode.create(config)) {

            // A column, so that width is the cross axis and `align-items:
            // stretch` has something to say once the width is gone.
            root.setFlexDirection(FlexDirection.COLUMN);
            var child = YogaNode.create(config);
            child.setWidth(StyleLength.points(50f));
            root.addChild(child);
            root.calculateLayout(200f, 100f);
            assertEquals(50f, child.layout().width(), "the width that was set");

            // Yoga spells this YGUndefined, which is a NaN passed to the points
            // setter. Going through the keyword is what keeps NaN out of the API.
            child.setWidth(StyleLength.UNDEFINED);
            root.calculateLayout(200f, 100f);

            assertEquals(200f, child.layout().width(), "stretched, because there is no width now");
        }
    }

    @Test
    @DisplayName("start and end mirror under RTL, left and right do not")
    void startAndEndFollowTheWritingDirection() {
        try (var config = YogaConfig.create();
                var root = YogaNode.create(config)) {

            root.setFlexDirection(FlexDirection.ROW);
            var child = YogaNode.create(config);
            child.setWidth(StyleLength.points(50f));
            child.setMargin(Edge.START, StyleLength.points(20f));
            root.addChild(child);

            root.calculateLayout(200f, 100f, Direction.LTR);
            assertEquals(20f, child.layout().left(), "start is the left edge under LTR");
            assertEquals(Direction.LTR, child.layoutDirection());

            root.calculateLayout(200f, 100f, Direction.RTL);
            // 200 - 50 - 20: the start margin has become the right one, and the
            // row itself now runs from the right.
            assertEquals(130f, child.layout().left(), "start is the right edge under RTL");
            assertEquals(Direction.RTL, child.layoutDirection());
        }
    }

    @Test
    @DisplayName("max-width stops a child from growing into the whole row")
    void maxWidthClampsGrowth() {
        try (var config = YogaConfig.create();
                var root = YogaNode.create(config)) {

            root.setFlexDirection(FlexDirection.ROW);
            var child = YogaNode.create(config);
            child.setFlexGrow(1f);
            child.setMaxWidth(StyleLength.points(60f));
            root.addChild(child);

            root.calculateLayout(200f, 100f);

            assertEquals(60f, child.layout().width(), "clamped, not grown to 200");
        }
    }

    @Test
    @DisplayName("min-width overrides a smaller declared width")
    void minWidthRaisesADeclaredWidth() {
        try (var config = YogaConfig.create();
                var root = YogaNode.create(config)) {

            root.setFlexDirection(FlexDirection.ROW);
            var child = YogaNode.create(config);
            child.setWidth(StyleLength.points(20f));
            child.setMinWidth(StyleLength.points(120f));
            root.addChild(child);

            root.calculateLayout(200f, 100f);

            assertEquals(120f, child.layout().width(), "raised to the minimum");
        }
    }

    @Test
    @DisplayName("display:none takes a child out of layout entirely")
    void displayNoneRemovesFromLayout() {
        try (var config = YogaConfig.create();
                var root = YogaNode.create(config)) {

            root.setFlexDirection(FlexDirection.ROW);
            var visible = YogaNode.create(config);
            var hidden = YogaNode.create(config);
            visible.setFlexGrow(1f);
            hidden.setFlexGrow(1f);
            hidden.setDisplay(Display.NONE);
            root.addChild(visible);
            root.addChild(hidden);

            root.calculateLayout(200f, 100f);

            assertEquals(200f, visible.layout().width(), "the whole row, not half of it");
            assertEquals(0f, hidden.layout().width(), "no size at all");
        }
    }

    @Test
    @DisplayName("an absolute child is placed against its containing block")
    void absolutePositioning() {
        try (var config = YogaConfig.create();
                var root = YogaNode.create(config)) {

            var child = YogaNode.create(config);
            child.setPositionType(PositionType.ABSOLUTE);
            child.setPosition(Edge.LEFT, StyleLength.points(30f));
            child.setPosition(Edge.TOP, StyleLength.points(15f));
            child.setWidth(StyleLength.points(40f));
            child.setHeight(StyleLength.points(20f));
            root.addChild(child);

            root.calculateLayout(200f, 100f);

            assertEquals(new ComputedLayout(30f, 15f, 40f, 20f), child.layout());
        }
    }

    @Test
    @DisplayName("overflow is reported when the children do not fit")
    void overflowIsReported() {
        try (var config = YogaConfig.create();
                var root = YogaNode.create(config)) {

            root.setFlexDirection(FlexDirection.ROW);
            var child = YogaNode.create(config);
            child.setWidth(StyleLength.points(500f));
            child.setFlexShrink(0f);
            root.addChild(child);

            root.calculateLayout(200f, 100f);

            assertTrue(root.hadOverflow(), "500 does not fit in 200");
        }
    }

    @Test
    @DisplayName("the point scale factor is what snaps a layout to physical pixels")
    void pointScaleFactorSnapsToTheDeviceGrid() {
        // 101 split two ways is 50.5 -- a position that exists at 2x and does not
        // at 1x. This is the whole fractional-DPI argument in one number: the
        // same tree, the same widths, and a different grid to land on.
        try (var wholePixels = YogaConfig.create();
                var halfPixels = YogaConfig.create()) {

            wholePixels.setPointScaleFactor(1f);
            halfPixels.setPointScaleFactor(2f);

            var atOne = splitInTwo(wholePixels, 101f);
            var atTwo = splitInTwo(halfPixels, 101f);

            assertEquals(Math.rint(atOne[0]), atOne[0], "at 1x every edge is a whole pixel");
            assertEquals(Math.rint(atOne[1]), atOne[1], "at 1x every edge is a whole pixel");
            assertEquals(101f, atOne[0] + atOne[1], "and the halves still tile the whole");
            assertNotEquals(atOne[0], atOne[1], "which means one of them had to give");

            assertEquals(50.5f, atTwo[0], "at 2x a half-pixel edge is on the grid");
            assertEquals(50.5f, atTwo[1], "so both halves are equal");
        }
    }

    /// Lays out two grown children in a row of `width` and returns their widths.
    private static float[] splitInTwo(YogaConfig config, float width) {
        try (var root = YogaNode.create(config)) {
            root.setFlexDirection(FlexDirection.ROW);
            var left = YogaNode.create(config);
            var right = YogaNode.create(config);
            left.setFlexGrow(1f);
            right.setFlexGrow(1f);
            root.addChild(left);
            root.addChild(right);

            root.calculateLayout(width, 100f);

            return new float[] {left.layout().width(), right.layout().width()};
        }
    }

    @Test
    @DisplayName("a property Yoga has no `auto` for is refused, not dropped")
    void autoIsRefusedWhereYogaHasNone() {
        try (var root = YogaNode.create()) {
            var thrown = assertThrows(
                    IllegalArgumentException.class, () -> root.setMaxWidth(StyleLength.AUTO));

            assertTrue(thrown.getMessage().contains("max-width"), thrown.getMessage());
        }
    }

    @Test
    @DisplayName("the computed padding of a shorthand edge is refused")
    void computedGettersWantAPhysicalSide() {
        try (var root = YogaNode.create()) {
            root.setPadding(Edge.ALL, StyleLength.points(4f));
            root.calculateLayout(100f, 100f);

            // Yoga answers zero for this rather than refusing, which reads as
            // "no padding" and is the worst possible answer.
            assertThrows(IllegalArgumentException.class, () -> root.layoutPadding(Edge.ALL));
            assertEquals(4f, root.layoutPadding(Edge.LEFT));
        }
    }

    @Test
    @DisplayName("web defaults are on, so a stylesheet behaves like a stylesheet")
    void webDefaultsAreOn() {
        try (var config = YogaConfig.create();
                var root = YogaNode.create(config)) {

            assertTrue(config.useWebDefaults(), "CSS's defaults, not Yoga's");

            // Yoga's own default is column; CSS's is row, and that is what a
            // node built with this config gets without being told.
            var left = YogaNode.create(config);
            var right = YogaNode.create(config);
            left.setWidth(StyleLength.points(50f));
            right.setWidth(StyleLength.points(50f));
            root.addChild(left);
            root.addChild(right);

            root.calculateLayout(200f, 100f);

            assertEquals(50f, right.layout().left(), "laid out along a row");
            assertEquals(0f, right.layout().top(), "and not down a column");
        }
    }

    @Test
    @DisplayName("Yoga's own defaults are what a node without a config gets")
    void withoutAConfigYogaDefaultsApply() {
        try (var root = YogaNode.create()) {
            var left = YogaNode.create();
            var right = YogaNode.create();
            left.setWidth(StyleLength.points(50f));
            left.setHeight(StyleLength.points(20f));
            right.setWidth(StyleLength.points(50f));
            right.setHeight(StyleLength.points(20f));
            root.addChild(left);
            root.addChild(right);

            root.calculateLayout(200f, 100f);

            // Column, because this is Yoga's default and nothing turned web
            // defaults on. The difference is the reason YogaConfig exists.
            assertEquals(20f, right.layout().top(), "stacked down a column");
            assertEquals(0f, right.layout().left());
        }
    }
}
