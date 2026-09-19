package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.input.drop.FileDrop;
import io.github.digitalsmile.goldberry.input.drop.TextDrop;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.model.LogicalPoint;

/// Text dropped on a window — [ADR-0408].
///
/// `FileDropTest`'s method and its argument, because the gesture is the same one:
/// the run of events is assembled in [Window] and is therefore testable without a
/// desktop, and the `sdl3` half is one `switch` arm whose event number the layout
/// probe checks against the compiled SDL.
///
/// What is **not** provable here is the number: `SDL_EVENT_DROP_TEXT` is 0x1001 in
/// the header and the probe compares that against the compiled library, which has
/// to be rebuilt before it can answer. See the ADR.
class TextDropTest {

    private final List<TextDrop> drops = new ArrayList<>();

    private Window window;

    @BeforeEach
    void openAWindow() {
        GoldberryRuntime.install(new HeadlessBackend());
        window = Window.open("drop", 400, 300);
    }

    @AfterEach
    void shutDown() {
        GoldberryRuntime.shutdown();
    }

    /// One whole gesture, the way the backend delivers it: one event per line and
    /// then the completion both kinds share.
    private void drop(float x, float y, String... lines) {
        for (var line : lines) {
            window.handleTextDropped(line, x, y);
        }
        window.handleDropCompleted(x, y);
    }

    @Test
    @DisplayName("dropped text arrives once, with where it landed")
    void oneLine() {
        window.onTextDrop(drops::add);

        drop(128.5f, 64.25f, "https://example.com/x.png");

        assertEquals(1, drops.size());
        assertEquals("https://example.com/x.png", drops.getFirst().text());
        assertEquals(List.of("https://example.com/x.png"), drops.getFirst().lines());
        assertEquals("https://example.com/x.png", drops.getFirst().first());
        assertEquals(new LogicalPoint(128.5f, 64.25f), drops.getFirst().at());
    }

    @Test
    @DisplayName("several lines are one drop, in the order they arrived")
    void severalLinesAreOneGesture() {
        window.onTextDrop(drops::add);

        // This is what SDL actually delivers for a two-line selection: it
        // tokenises the payload on \r\n and sends one event per token.
        drop(10, 20, "first", "second", "third");

        assertEquals(1, drops.size(), "one gesture is one event, however many lines were in it");
        assertEquals(3, drops.getFirst().count());
        assertEquals(List.of("first", "second", "third"), drops.getFirst().lines());
    }

    @Test
    @DisplayName("text() joins the lines with a newline this toolkit chose")
    void textJoinsWithNewline() {
        window.onTextDrop(drops::add);

        drop(10, 20, "first", "second");

        // SDL threw the separators away before Java saw anything, so `\n` is
        // ours. The assertion is here so that changing it is a decision.
        assertEquals("first\nsecond", drops.getFirst().text());
    }

    @Test
    @DisplayName("a drag that crossed the window and dropped nothing raises nothing")
    void anEmptyGestureIsSilent() {
        window.onTextDrop(drops::add);

        window.handleDropCompleted(10, 20);

        assertTrue(drops.isEmpty());
    }

    @Test
    @DisplayName("an empty line is not text, and does not make an empty gesture raise")
    void anEmptyLineIsSkipped() {
        window.onTextDrop(drops::add);

        drop(10, 20, "");

        assertTrue(drops.isEmpty(), "a gesture whose only token was empty has nothing to report");
    }

    @Test
    @DisplayName("one gesture does not leak into the next")
    void gesturesAreIndependent() {
        window.onTextDrop(drops::add);

        drop(10, 20, "one");
        drop(30, 40, "two");

        assertEquals(2, drops.size());
        assertEquals(List.of("one"), drops.get(0).lines());
        assertEquals(List.of("two"), drops.get(1).lines());
        assertEquals(new LogicalPoint(30, 40), drops.get(1).at());
    }

    @Test
    @DisplayName("every listener is told, and closing one stops only that one")
    void listenersAreAList() {
        var second = new ArrayList<TextDrop>();
        window.onTextDrop(drops::add);
        var subscription = window.onTextDrop(second::add);

        drop(10, 20, "one");
        subscription.close();
        drop(10, 20, "two");

        assertEquals(2, drops.size(), "the listener that stayed heard both");
        assertEquals(1, second.size(), "and the one that unsubscribed heard only the first");
    }

    @Test
    @DisplayName("the position survives events that carry none")
    void aPositionlessDropKeepsTheLastOne() {
        window.onTextDrop(drops::add);

        window.handleTextDropped("dropped", 96, 48);
        window.handleDropCompleted(0, 0);

        assertEquals(new LogicalPoint(96, 48), drops.getFirst().at());
    }

    @Test
    @DisplayName("a file drop raises nothing here, and a text drop raises nothing there")
    void theTwoKindsDoNotCrossOver() {
        var files = new ArrayList<FileDrop>();
        window.onFileDrop(files::add);
        window.onTextDrop(drops::add);

        window.handleFileDropped("/tmp/a.png", 10, 20);
        window.handleDropCompleted(10, 20);

        assertEquals(1, files.size());
        assertTrue(drops.isEmpty(), "the shared completion must not turn a file drop into a text drop");

        window.handleTextDropped("some text", 30, 40);
        window.handleDropCompleted(30, 40);

        assertEquals(1, files.size(), "and the file listener hears nothing about text");
        assertEquals(1, drops.size());
    }

    @Test
    @DisplayName("a gesture carrying both raises both, at the same point")
    void aGestureCarryingBothRaisesBoth() {
        var files = new ArrayList<FileDrop>();
        window.onFileDrop(files::add);
        window.onTextDrop(drops::add);

        // No platform SDL supports is known to do this -- each one picks a
        // representation for the drag. The two buffers are separate so that a
        // platform that did would not lose half the gesture.
        window.handleFileDropped("/tmp/a.png", 50, 60);
        window.handleTextDropped("and text", 50, 60);
        window.handleDropCompleted(50, 60);

        assertEquals(List.of(Path.of("/tmp/a.png")), files.getFirst().paths());
        assertEquals(List.of("and text"), drops.getFirst().lines());
        assertEquals(files.getFirst().at(), drops.getFirst().at());
    }

    @Test
    @DisplayName("a TextDrop is a value, and an empty one is not a drop")
    void theValueRefusesToBeEmpty() {
        assertThrows(IllegalArgumentException.class, () -> new TextDrop(List.of(), new LogicalPoint(0, 0)));
        assertThrows(NullPointerException.class, () -> new TextDrop(null, new LogicalPoint(0, 0)));
    }

    @Test
    @DisplayName("a TextDrop copies the lines it was given")
    void theValueCopies() {
        var lines = new ArrayList<String>();
        lines.add("one");
        var drop = new TextDrop(lines, new LogicalPoint(0, 0));

        lines.add("two");

        assertEquals(1, drop.count());
        assertThrows(UnsupportedOperationException.class, () -> drop.lines().add("three"));
    }
}
