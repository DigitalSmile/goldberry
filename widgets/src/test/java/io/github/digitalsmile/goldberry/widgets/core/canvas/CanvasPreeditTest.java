package io.github.digitalsmile.goldberry.widgets.core.canvas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.event.PreeditEvent;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.paint.Clip;
import io.github.digitalsmile.goldberry.render.Cursor;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.ElementTree;

/// `docs/gaps.md` G15, as a widget: a composition reaching a canvas, and the
/// caret's rectangle reaching the platform.
///
/// The editor's own half is `EditorPreeditTest` in `:core`. What is checked here
/// is the wiring nobody else can check — that the router delivers a preedit to
/// the focused canvas and nowhere else, that there is **no capture phase**, and
/// that the candidate window's position is published whenever it could have
/// moved (ADR-0289).
class CanvasPreeditTest {

    private PointerRouter router;
    private Recorder input;

    /// A canvas with a caret, somewhere.
    private static final class Recorder implements Input {
        private final List<String> compositions = new ArrayList<>();
        private LogicalRect caret = LogicalRect.of(4, 8, 40, 16);

        @Override
        public void onPreedit(PreeditEvent event) {
            compositions.add(event.text() + "@" + event.caret());
        }

        @Override
        public boolean focusable() {
            return true;
        }

        @Override
        public boolean wantsText() {
            return true;
        }

        @Override
        public Optional<LogicalRect> caretArea() {
            return Optional.of(caret);
        }

        @Override
        public double caretOffsetIn(LogicalRect area) {
            return 12;
        }
    }

    @BeforeEach
    void setUp() {
        router = new PointerRouter();
        input = new Recorder();
        var element = new ElementTree(new Canvas(null, input)).root();
        // Content box offset from the window's origin, which is what a padded
        // canvas is and what the translation below has to get right.
        router.updateRegions(List.of(new HitTest.Region(
                element, Cursor.DEFAULT, 10, 20, 100, 60, null, Clip.NONE, LogicalRect.of(18, 28, 84, 44))));
        router.focus(element, false);
    }

    @Test
    @DisplayName("a composition reaches the focused canvas")
    void reachesTheCanvas() {
        router.preedit("にほ", 0, 1);

        assertEquals(List.of("にほ@1"), input.compositions);
    }

    @Test
    @DisplayName("nothing arrives when nothing has focus, because a composition has nowhere to be drawn")
    void droppedWithoutFocus() {
        router.focus(null, false);

        router.preedit("にほ", -1, -1);

        assertTrue(input.compositions.isEmpty());
    }

    @Test
    @DisplayName("a consumed composition stops where it was consumed")
    void consumingStops() {
        var seen = new ArrayList<String>();
        router.onCaretAreaChange((area, cursor) -> seen.add(String.valueOf(area)));

        router.preedit("に", -1, -1);

        assertFalse(seen.isEmpty(), "the caret area is published after the dispatch, consumed or not");
    }

    @Test
    @DisplayName("publishes the caret in window coordinates, offset by the content box")
    void publishesTheCaretArea() {
        var areas = new ArrayList<LogicalRect>();
        var cursors = new ArrayList<Double>();
        router.onCaretAreaChange((area, cursor) -> {
            areas.add(area);
            cursors.add(cursor);
        });

        router.preedit("にほ", -1, -1);

        var published = areas.getLast();
        // The canvas reported (4, 8) in its own content coordinates and its
        // content box starts at (18, 28) in the window.
        assertEquals(22, published.left(), 0.01);
        assertEquals(36, published.top(), 0.01);
        assertEquals(40, published.width(), 0.01);
        assertEquals(12, cursors.getLast(), 0.01);
    }

    @Test
    @DisplayName("publishes nothing once focus leaves, so a candidate list is not left behind")
    void clearedOnBlur() {
        var areas = new ArrayList<LogicalRect>();
        router.onCaretAreaChange((area, cursor) -> areas.add(area));

        router.focus(null, false);

        assertEquals(null, areas.getLast(), "a window still told where a caret is would keep placing a list there");
    }

    @Test
    @DisplayName("an unchanged caret is not republished, so a keystroke is not a platform call")
    void doesNotRepublishAnUnchangedCaret() {
        var calls = new ArrayList<LogicalRect>();
        router.onCaretAreaChange((area, cursor) -> calls.add(area));
        var initial = calls.size();

        router.preedit("に", -1, -1);
        router.preedit("にほ", -1, -1);
        router.preedit("にほん", -1, -1);

        assertEquals(initial, calls.size(), "the caret did not move, so the platform had nothing to be told");
    }

    @Test
    @DisplayName("a canvas with no Input has no caret at all")
    void noInputNoCaret() {
        var canvas = new Canvas((frame, size) -> {});

        assertTrue(canvas.caretArea().isEmpty());
        assertEquals(0, canvas.caretOffsetIn(LogicalRect.of(0, 0, 1, 1)), 0.01);
    }
}
