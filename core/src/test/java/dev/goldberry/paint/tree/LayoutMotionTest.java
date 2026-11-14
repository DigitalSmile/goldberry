package dev.goldberry.paint.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.value.Transform;
import dev.goldberry.css.value.ViewTransition;
import dev.goldberry.layout.FlexDirection;
import dev.goldberry.layout.Length;
import dev.goldberry.motion.Easing;
import dev.goldberry.motion.layout.LayoutTracks;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.render.DamageRect;

/// A box with a `view-transition-name` that layout moves is drawn where it was
/// and travels to where it is: through the render tree alone, with boxes built
/// by hand and a clock given to each update.
///
/// The scene is a 200 × 200 black column. A green gap 100 high can open above
/// a red 50 × 50 tile, which pushes the tile from the top to `y = 100`. The
/// tile's move takes 100 ms on a linear curve, so at 50 ms it is halfway.
@DisplayName("layout motion")
class LayoutMotionTest {

    private static final int BLACK = 0xFF000000;
    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;

    private static final ViewTransition TILE = new ViewTransition("tile", 100, Easing.LINEAR);

    private final List<TestFrames.Target> targets = new ArrayList<>();
    private RenderTree render;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        render = RenderTree.create();
    }

    @AfterEach
    void tearDown() {
        targets.forEach(TestFrames.Target::end);
        if (render != null) {
            render.close();
        }
    }

    private static Box named(Box box, ViewTransition timing) {
        return box.transform(Transform.NONE.viewTransition(timing));
    }

    private static Box column(Box... children) {
        return Box.filled(BLACK)
                .size(Length.points(200), Length.points(200))
                .direction(FlexDirection.COLUMN)
                .children(children);
    }

    private static Box gap() {
        return Box.filled(GREEN).size(Length.points(50), Length.points(100));
    }

    private static Box tile(int colour, float width) {
        return Box.filled(colour).size(Length.points(width), Length.points(50));
    }

    /// The scene, with or without the gap, its tile named with `timing`.
    private static Box scene(boolean gap, ViewTransition timing) {
        var tile = named(tile(RED, 50), timing);
        return gap ? column(gap(), tile) : column(tile);
    }

    /// Lays out `box` at `now` and paints it, answering the finished frame.
    private TestFrames.Target frame(Box box, double now) {
        var target = TestFrames.of(200, 200, 1.0f, 0);
        targets.add(target);
        render.update(target.frame(), box, now);
        render.paint(target.frame());
        target.end();
        return target;
    }

    @Test
    @DisplayName("draws a named box where it was, carries it to where layout put it, and stops")
    void moves() {
        frame(scene(false, TILE), 0);

        var start = frame(scene(true, TILE), 1000);
        assertEquals(RED, start.pixel(25, 25), "drawn where it was, over the gap that pushed it");
        assertTrue(render.isMoving());

        var halfway = frame(scene(true, TILE), 1050);
        assertEquals(GREEN, halfway.pixel(25, 25));
        assertEquals(RED, halfway.pixel(25, 75), "halfway: from y = 50 to 100");
        assertEquals(BLACK, halfway.pixel(25, 125));

        var arrived = frame(scene(true, TILE), 1100);
        assertEquals(GREEN, arrived.pixel(25, 75));
        assertEquals(RED, arrived.pixel(25, 125));
        assertFalse(render.isMoving(), "arrived, so the loop may rest");
    }

    @Test
    @DisplayName("moves a box with no name at once, as before")
    void unnamed() {
        frame(column(tile(RED, 50)), 0);

        var next = frame(column(gap(), tile(RED, 50)), 1000);

        assertEquals(GREEN, next.pixel(25, 25));
        assertEquals(RED, next.pixel(25, 125));
        assertFalse(render.isMoving());
    }

    @Test
    @DisplayName("damages where the box was drawn and where it is drawn now")
    void damage() {
        frame(scene(false, TILE), 0);
        frame(scene(true, TILE), 1000);

        var target = TestFrames.of(200, 200, 1.0f, 0);
        targets.add(target);
        render.update(target.frame(), scene(true, TILE), 1050);
        var damage = render.damage(target.frame());

        assertTrue(covers(damage, 25, 25), "where it was drawn: " + damage);
        assertTrue(covers(damage, 25, 75), "where it is drawn: " + damage);
    }

    @Test
    @DisplayName("sets off from where the box is drawn when layout moves it again mid-move")
    void interrupted() {
        frame(scene(false, TILE), 0);
        frame(scene(true, TILE), 1000);
        frame(scene(true, TILE), 1050);

        var turned = frame(scene(false, TILE), 1050);
        assertEquals(RED, turned.pixel(25, 75), "no jump back to the top");
        assertEquals(BLACK, turned.pixel(25, 25));

        var back = frame(scene(false, TILE), 1100);
        assertEquals(RED, back.pixel(25, 30), "halfway back, from y = 50 to 0");
        assertEquals(BLACK, back.pixel(25, 90));
    }

    @Test
    @DisplayName("stretches a named box that layout made wider")
    void stretches() {
        frame(column(named(tile(RED, 50), TILE)), 0);
        frame(column(named(tile(RED, 150), TILE)), 1000);

        var halfway = frame(column(named(tile(RED, 150), TILE)), 1050);

        assertEquals(RED, halfway.pixel(90, 25), "100 wide halfway");
        assertEquals(BLACK, halfway.pixel(110, 25));
    }

    @Test
    @DisplayName("moves at once with an instant timing, and without a clock")
    void instant() {
        var snaps = new ViewTransition("tile", 0, Easing.LINEAR);
        frame(scene(false, snaps), 0);
        assertEquals(RED, frame(scene(true, snaps), 1000).pixel(25, 125));

        frame(scene(false, TILE), LayoutTracks.NO_CLOCK);
        assertEquals(RED, frame(scene(true, TILE), LayoutTracks.NO_CLOCK).pixel(25, 125));
        assertFalse(render.isMoving());
    }

    @Test
    @DisplayName("finds a named box under another parent, and carries it across")
    void anotherParent() {
        var left = Box.of().size(Length.points(100), Length.points(200)).direction(FlexDirection.COLUMN);
        var right = Box.of().size(Length.points(100), Length.points(200)).direction(FlexDirection.COLUMN);
        var row = Box.filled(BLACK).size(Length.points(200), Length.points(200)).direction(FlexDirection.ROW);
        var tile = named(tile(RED, 50), TILE);
        frame(row.children(left.children(tile), right), 0);

        var moved = frame(row.children(left, right.children(tile)), 1000);
        assertEquals(RED, moved.pixel(25, 25), "drawn in the left column, where it was");
        assertEquals(BLACK, moved.pixel(125, 25));

        var halfway = frame(row.children(left, right.children(tile)), 1050);
        assertEquals(RED, halfway.pixel(75, 25), "halfway across, from x = 50 to 100");
    }

    @Test
    @DisplayName("moves a child with no name with its named parent, and the parent's paint with it")
    void childrenFollow() {
        var parent = Box.filled(BLUE).size(Length.points(100), Length.points(50));
        frame(column(named(parent.children(tile(RED, 20)), TILE)), 0);

        var start = frame(column(gap(), named(parent.children(tile(RED, 20)), TILE)), 1000);
        assertEquals(RED, start.pixel(10, 25), "drawn where it was, child and all");
        assertEquals(BLUE, start.pixel(60, 25));

        var halfway = frame(column(gap(), named(parent.children(tile(RED, 20)), TILE)), 1050);
        assertEquals(RED, halfway.pixel(10, 75), "halfway, child and all");
        assertEquals(BLUE, halfway.pixel(60, 75));
    }

    @Test
    @DisplayName("lets a named box inside a moving named box travel its own way")
    void nested() {
        var slow = new ViewTransition("outer", 200, Easing.LINEAR);
        var outer = Box.filled(BLUE).size(Length.points(100), Length.points(50));
        var inner = named(tile(RED, 20), TILE);
        frame(column(named(outer.children(inner), slow)), 0);
        frame(column(gap(), named(outer.children(inner), slow)), 1000);

        var later = frame(column(gap(), named(outer.children(inner), slow)), 1100);

        // The outer box is halfway, y = 50; the inner one, on a 100 ms move, has
        // arrived at y = 100 on its own, not at 150 on top of its parent's move.
        assertEquals(BLUE, later.pixel(60, 75));
        assertEquals(RED, later.pixel(10, 125), "the inner box is where layout put it");
        assertEquals(BLUE, later.pixel(10, 75), "and is not drawn twice-moved inside its parent");
    }

    private static boolean covers(List<DamageRect> damage, int x, int y) {
        return damage.stream()
                .anyMatch(r -> x >= r.x() && x < r.x() + r.width() && y >= r.y() && y < r.y() + r.height());
    }
}
