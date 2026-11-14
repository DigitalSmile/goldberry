package dev.goldberry.text.edit;

import static dev.goldberry.text.mark.MarkKind.Style.BOLD;
import static dev.goldberry.text.mark.MarkKind.Style.CODE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PreeditEvent;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Mod;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.input.key.PrimaryModifier;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.render.clipboard.Clipboard;
import dev.goldberry.text.TextLine;
import dev.goldberry.text.font.Font;
import dev.goldberry.text.mark.Mark;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkStyle;
import dev.goldberry.text.mark.MarkStyles;
import dev.goldberry.text.mark.MarkedClipboard;
import dev.goldberry.text.mark.MarkedText;

/// The canvas editor with marks: shaped in their own fonts on the editor's own
/// rows, toggled by keys, kept by undo and the clipboard.
class EditorMarksTest {

    private static final int RED = 0xFFBF616A;

    private Font regular;
    private Font strong;
    private Font mono;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        regular = Font.bundled(BundledFont.UI, 13);
        strong = Font.bundled(BundledFont.UI_STRONG, 13);
        mono = Font.bundled(BundledFont.CODE, 16);
    }

    @AfterEach
    void tearDown() {
        for (var font : List.of(regular, strong, mono)) {
            font.close();
        }
    }

    /// Bold in the strong face, code in a larger monospace, links in red.
    private MarkStyles styles() {
        return kinds -> {
            if (kinds.contains(CODE)) {
                return MarkStyle.plain(mono);
            }
            var font = kinds.contains(BOLD) ? strong : regular;
            var link = kinds.stream().anyMatch(MarkKind.Link.class::isInstance);
            return new MarkStyle(font, link ? RED : MarkStyle.TEXT_COLOUR, Set.of(), 0);
        };
    }

    private Editor editor(MarkedText value) {
        return new Editor(regular).multiline(true).markStyles(styles()).value(value);
    }

    private static KeyEvent primary(Key key, Mod... more) {
        var mods = new ArrayList<Mod>(List.of(more));
        mods.add(PrimaryModifier.current());
        return new KeyEvent(KeyEvent.Kind.PRESSED, key, Modifiers.of(mods.toArray(Mod[]::new)), false, null);
    }

    @Nested
    @DisplayName("shaping")
    class Shaping {

        @Test
        @DisplayName("a bold word is shaped in the bold face and is wider")
        void boldIsWider() {
            var plain = editor(MarkedText.of("Goldberry"));
            var bold = editor(MarkedText.of("Goldberry").mark(0, 9, BOLD));
            assertTrue(bold.document().paragraphOf(0).widthBetween(0, 9)
                    > plain.document().paragraphOf(0).widthBetween(0, 9));
        }

        @Test
        @DisplayName("rows stay the editor's height whatever is on them")
        void rowsOnTheGrid() {
            var plain = editor(MarkedText.of("one\ntwo\nthree"));
            var code = editor(MarkedText.of("one\ntwo\nthree").mark(0, 13, CODE));
            assertEquals(plain.contentHeight(), code.contentHeight());
            assertEquals(regular.lineHeight(), code.caret().height());
            var line = code.lines().get(1);
            assertEquals(
                    regular.lineHeight(),
                    code.document()
                            .paragraphOf(1)
                            .heightOf(new TextLine(0, 3, line.glyphStart(), line.glyphEnd(), line.width())));
        }

        @Test
        @DisplayName("the caret walks across a seam between two faces without going back")
        void caretAcrossSeam() {
            var editor = editor(MarkedText.of("ab code cd").mark(3, 7, CODE));
            editor.caretTo(0, false);
            var last = -1.0;
            for (var i = 0; i < 10; i++) {
                editor.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.RIGHT, Modifiers.NONE, false, null));
                var x = editor.caret().x();
                assertTrue(x > last, "step " + i + " moved the caret to " + x + " from " + last);
                last = x;
            }
        }

        @Test
        @DisplayName("a click on either side of a seam lands on that side")
        void clickAtSeam() {
            var editor = editor(MarkedText.of("ab code cd").mark(3, 7, CODE));
            var seam = editor.document().caretX(0, 10, 3);
            editor.pointerAt(seam - 0.5, 2, false, 1);
            assertEquals(3, editor.edit().caret(), "just left of the seam is the seam");
            var inside = editor.document().caretX(0, 10, 5);
            editor.pointerAt(inside + 1, 2, false, 1);
            assertEquals(5, editor.edit().caret());
        }

        @Test
        @DisplayName("Down keeps the column across a line in another face")
        void verticalKeepsColumn() {
            var editor =
                    editor(MarkedText.of("aaaaaaaaaa\ncode code\naaaaaaaaaa").mark(11, 20, CODE));
            editor.caretTo(6, false);
            var x = editor.caret().x();
            editor.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.DOWN, Modifiers.NONE, false, null));
            editor.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.DOWN, Modifiers.NONE, false, null));
            assertEquals(27, editor.edit().caret());
            assertEquals(x, editor.caret().x(), 0.01);
        }

        @Test
        @DisplayName("a word made bold re-shapes its line and only its line")
        void markChangeReshapesOneLine() {
            var editor = editor(MarkedText.of("one\ntwo\nthree"));
            var before = editor.document();
            editor.caretTo(4, false);
            editor.caretTo(7, true);
            assertTrue(editor.toggle(BOLD));
            var after = editor.document();
            assertTrue(before.paragraphOf(0) == after.paragraphOf(0), "line one kept its shaping");
            assertFalse(before.paragraphOf(1) == after.paragraphOf(1), "line two was shaped again");
            assertTrue(before.paragraphOf(2) == after.paragraphOf(2), "line three kept its shaping");
        }

        @Test
        @DisplayName("a link is drawn in its colour")
        void paintsColour() {
            var link = new MarkKind.Link(URI.create("https://goldberry.dev"));
            var editor = editor(MarkedText.of("WWWW").mark(0, 4, link));
            var target = TestFrames.of(80, 30, 1.0f);
            target.frame().fill(0xFF000000);
            editor.paint(target.frame(), 2, 2, Editor.Ink.of(0xFFFFFFFF, 0xFF444444), false);
            target.end();
            var red = false;
            for (var y = 0; y < 30 && !red; y++) {
                for (var x = 0; x < 80 && !red; x++) {
                    var pixel = target.pixel(x, y);
                    red = ((pixel >>> 16) & 0xFF) > 0x80 && (pixel & 0xFF) < 0x80;
                }
            }
            assertTrue(red, "the link was not drawn in red");
        }
    }

    @Nested
    @DisplayName("keys and commands")
    class Commands {

        @Test
        @DisplayName("Ctrl+B with a selection makes it bold, and one undo takes it back")
        void boldKey() {
            var editor = editor(MarkedText.of("Goldberry"));
            editor.caretTo(0, false);
            editor.caretTo(4, true);
            assertTrue(editor.onKey(primary(Key.B)));
            assertEquals(List.of(new Mark(0, 4, BOLD)), editor.value().marks());
            assertTrue(editor.undo());
            assertTrue(editor.value().isPlain());
            assertTrue(editor.redo());
            assertEquals(List.of(new Mark(0, 4, BOLD)), editor.value().marks());
        }

        @Test
        @DisplayName("Ctrl+B with nothing selected bolds what is typed next, and is no undo step")
        void boldForTyping() {
            var editor = editor(MarkedText.of("Say "));
            assertTrue(editor.onKey(primary(Key.B)));
            assertFalse(editor.canUndo());
            editor.onText("hi");
            assertEquals(List.of(new Mark(4, 6, BOLD)), editor.value().marks());
            assertEquals(Set.of(BOLD), editor.activeKinds());
        }

        @Test
        @DisplayName("without styles the formatting keys are left to the application")
        void plainEditorLeavesKeys() {
            var editor = new Editor(regular).text("Goldberry");
            editor.caretTo(0, true);
            assertFalse(editor.onKey(primary(Key.B)));
            assertTrue(editor.value().isPlain());
        }

        @Test
        @DisplayName("Ctrl+K is left to the caller, who asks for the address")
        void linkKey() {
            var editor = editor(MarkedText.of("site"));
            editor.caretTo(0, true);
            assertFalse(editor.onKey(primary(Key.K)));
            var link = new MarkKind.Link(URI.create("https://goldberry.dev"));
            assertTrue(editor.mark(link));
            assertEquals(List.of(new Mark(0, 4, link)), editor.value().marks());
        }

        @Test
        @DisplayName("a read-only editor refuses formatting")
        void readOnly() {
            var editor = editor(MarkedText.of("Goldberry")).readOnly(true);
            editor.caretTo(0, true);
            assertFalse(editor.toggle(BOLD));
            assertFalse(editor.onKey(primary(Key.B)));
        }

        @Test
        @DisplayName("a mention replaces its query and is stepped over")
        void mention() {
            var editor = editor(MarkedText.of("Hi @an"));
            var ann = new MarkKind.Mention("user:1");
            assertTrue(editor.replace(3, 6, MarkedText.of("@Ann").mark(0, 4, ann)));
            assertEquals("Hi @Ann", editor.text());
            editor.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.LEFT, Modifiers.NONE, false, null));
            assertEquals(3, editor.edit().caret());
        }

        @Test
        @DisplayName("a composition in bold looks bold, and commits bold")
        void preeditInBold() {
            var editor = editor(MarkedText.of("Say "));
            editor.onKey(primary(Key.B));
            editor.onPreedit(new PreeditEvent("にほ", -1, -1, null));
            assertEquals(List.of(new Mark(4, 6, BOLD)), editor.document().marks());
            assertTrue(editor.value().isPlain(), "a composition is not an edit");
            editor.onText("日本");
            assertEquals(List.of(new Mark(4, 6, BOLD)), editor.value().marks());
        }
    }

    @Nested
    @DisplayName("the clipboard")
    class Clipboards {

        /// A clipboard that holds text and typed bytes, as a desktop's does.
        private static final class Board implements Clipboard {

            private String text = "";
            private final Map<String, byte[]> offered = new HashMap<>();

            @Override
            public boolean hasText() {
                return !text.isEmpty();
            }

            @Override
            public String text() {
                return text;
            }

            @Override
            public boolean text(String value) {
                text = value;
                offered.clear();
                return true;
            }

            @Override
            public boolean has(String mime) {
                return offered.containsKey(mime);
            }

            @Override
            public byte[] read(String mime) {
                return offered.getOrDefault(mime, new byte[0]);
            }

            @Override
            public boolean write(Map<String, byte[]> byMime) {
                offered.clear();
                offered.putAll(new LinkedHashMap<>(byMime));
                text = new String(byMime.getOrDefault(MarkedClipboard.TEXT_MIME, new byte[0]), StandardCharsets.UTF_8);
                return true;
            }
        }

        @Test
        @DisplayName("bold copied from one editor is bold pasted into another")
        void roundTrip() {
            var board = new Board();
            var from = editor(MarkedText.of("Ship it now").mark(0, 4, BOLD)).clipboard(board);
            from.caretTo(0, false);
            from.caretTo(7, true);
            assertTrue(from.copy());
            assertEquals("Ship it", board.text(), "a plain paste gets the words");

            var into = editor(MarkedText.of("> ")).clipboard(board);
            assertTrue(into.paste());
            assertEquals("> Ship it", into.text());
            assertEquals(List.of(new Mark(2, 6, BOLD)), into.value().marks());
        }

        @Test
        @DisplayName("plain text pastes as plain text")
        void plainPaste() {
            var board = new Board();
            board.text("words");
            var into = editor(MarkedText.of("x").mark(0, 1, BOLD)).clipboard(board);
            assertTrue(into.paste());
            assertEquals("xwords", into.text());
            assertEquals(List.of(new Mark(0, 1, BOLD)), into.value().marks(), "a paste takes nothing from beside it");
        }

        @Test
        @DisplayName("a single-line editor flattens a marked paste one char for one")
        void singleLine() {
            var board = new Board();
            MarkedClipboard.copy(board, MarkedText.of("a\nb").mark(2, 3, BOLD));
            var into = new Editor(regular).markStyles(styles()).clipboard(board);
            assertTrue(into.paste());
            assertEquals("a b", into.text());
            assertEquals(List.of(new Mark(2, 3, BOLD)), into.value().marks());
            assertNotEquals("", board.text());
        }
    }
}
