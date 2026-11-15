package dev.goldberry.widgets.form.richinput;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.hit.Extent;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.input.key.PrimaryModifier;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkedFormat;
import dev.goldberry.text.mark.MarkedText;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.TestHost;
import dev.goldberry.widgets.controls.TestFont;

/// A `rich-input` with a clipboard format installed: a copy offers it, and a
/// paste of it keeps what it says, as `text/html` from a browser would.
@DisplayName("rich-input clipboard formats")
class RichInputClipboardFormatTest {

    private static final LogicalRect FIELD = LogicalRect.of(20, 200, 300, 40);

    /// `text/x-shout`: written upper-cased, read back all bold.
    private static final MarkedFormat SHOUT = new MarkedFormat() {
        @Override
        public String mime() {
            return "text/x-shout";
        }

        @Override
        public byte[] write(MarkedText text) {
            return text.text().toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Optional<MarkedText> read(byte[] bytes) {
            var text = new String(bytes, StandardCharsets.UTF_8);
            return Optional.of(MarkedText.of(text).mark(0, text.length(), MarkKind.Style.BOLD));
        }
    };

    private final TestHost host = new TestHost();

    private final List<MarkedText> changes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private ElementTree mounted(RichInput input) {
        var tree = new ElementTree(input, host);
        render(tree);
        box(tree).measured(new Extent(300, 40), new Extent(300, 40));
        render(tree);
        box(tree).located(FIELD, FIELD);
        box(tree).onFocusChanged(true, false);
        render(tree);
        return tree;
    }

    private static void render(ElementTree tree) {
        tree.flush();
        new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get()).render(tree);
    }

    private static RichInputBox box(ElementTree tree) {
        return (RichInputBox) find(tree.root()).widget();
    }

    private static Element find(Element from) {
        if (from.widget() instanceof RichInputBox) {
            return from;
        }
        for (var child : from.children()) {
            try {
                return find(child);
            } catch (AssertionError notHere) {
                // the next child
            }
        }
        throw new AssertionError("no rich-input box");
    }

    private static void primary(ElementTree tree, Key key) {
        box(tree).onKey(new KeyEvent(KeyEvent.Kind.PRESSED, key, Modifiers.of(PrimaryModifier.current()), false, null));
        render(tree);
    }

    @Test
    @DisplayName("a copy with marks offers the format beside the toolkit's own type and the text")
    void copyOffers() {
        var tree = mounted(new RichInput(MarkedText.of("hi").mark(0, 2, MarkKind.Style.ITALIC), changes::add)
                .clipboardFormats(List.of(SHOUT)));
        primary(tree, Key.A);
        primary(tree, Key.C);
        var board = host.clipboard();
        assertArrayEquals("HI".getBytes(StandardCharsets.UTF_8), board.read("text/x-shout"));
        assertEquals("hi", board.text());
    }

    @Test
    @DisplayName("a paste of the format keeps its marks; without the format installed it is the text")
    void pasteReads() {
        host.clipboard()
                .write(Map.of(
                        "text/x-shout", "loud".getBytes(StandardCharsets.UTF_8),
                        "text/plain", "loud".getBytes(StandardCharsets.UTF_8)));
        var formatted = mounted(new RichInput(MarkedText.EMPTY, changes::add).clipboardFormats(List.of(SHOUT)));
        primary(formatted, Key.V);
        assertEquals(MarkedText.of("loud").mark(0, 4, MarkKind.Style.BOLD), changes.getLast());

        changes.clear();
        var plain = mounted(new RichInput(MarkedText.EMPTY, changes::add));
        primary(plain, Key.V);
        assertEquals(MarkedText.of("loud"), changes.getLast());
    }

    @Test
    @DisplayName("the text menu offers Paste when only the format is on the clipboard")
    void canPaste() {
        host.clipboard().write(Map.of("text/x-shout", "x".getBytes(StandardCharsets.UTF_8)));
        var tree = mounted(new RichInput(MarkedText.EMPTY, changes::add).clipboardFormats(List.of(SHOUT)));
        assertTrue(((RichInputState) tree.root().state().orElseThrow()).canPaste());
    }
}
