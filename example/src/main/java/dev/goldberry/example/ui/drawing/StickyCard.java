package dev.goldberry.example.ui.drawing;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.css.value.CssColor;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.event.TextEvent;
import dev.goldberry.paint.Frame;
import dev.goldberry.paint.Path;
import dev.goldberry.paint.stroke.Stroke;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.text.edit.Editor;
import dev.goldberry.text.flow.TextDecoration;
import dev.goldberry.text.font.Font;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkStyle;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.canvas.Canvas;
import dev.goldberry.widgets.core.canvas.Input;

/// A sticky note with a caret in it: an `Editor` drawn on a canvas.
///
/// Everything inside the paper is the editor's: the selection, the shaped text
/// and the caret come out of one shaping, which keeps a click landing where the
/// glyph was drawn. What the card owns is the paper and the wiring, and the one
/// line of the wiring that matters most is [Input#wantsText()]: the platform
/// produces no characters until something says it is being typed into.
///
/// The note takes formatting too: `Ctrl+B`, `Ctrl+I`, `Ctrl+U` and the rest, in
/// the faces [StickyState#styleOf] picks for each set of marks.
///
/// Read more: [Input](https://goldberry.dev/docs/components/drawing.html#input).
record StickyCard() implements Widget.Stateful {

    static final float WIDTH = 260;

    static final float HEIGHT = 150;

    static final float PADDING = 10;

    /// Where the paper sits in the canvas.
    private static final float MARGIN = 8;

    @Override
    public State<?> createState() {
        return new StickyState();
    }

    static final class StickyState extends State<StickyCard> {

        /// The editor's font, which this state opened and so closes.
        private @Nullable Font font;

        /// The faces marked words are shaped in, opened with [#font]: bold,
        /// italic, both, and code.
        private @Nullable Font strong;

        private @Nullable Font italic;

        private @Nullable Font strongItalic;

        private @Nullable Font code;

        /// The text, its caret and its undo history. On the state because a widget
        /// is rebuilt every frame and a caret is not. Built on first use, because
        /// a font needs the rasterizer and the shape-only tests have none.
        private @Nullable Editor editor;

        /// Whether the press began with the middle button: a paste, so the drag
        /// after it selects nothing.
        private boolean pasting;

        /// Whether the sticky has the keyboard, which is the only reason to draw a
        /// caret. False at rest, so the golden is the same on every machine.
        private boolean editing;

        /// The window, for its clipboard and primary selection. Null in a tree
        /// built with no host.
        private @Nullable Host host;

        private Editor editor() {
            var current = editor;
            if (current == null) {
                var opened = Font.bundled(BundledFont.UI, 13);
                font = opened;
                strong = Font.bundled(BundledFont.UI_STRONG, 13);
                italic = Font.bundled(BundledFont.UI_ITALIC, 13);
                strongItalic = Font.bundled(BundledFont.UI_STRONG_ITALIC, 13);
                code = Font.bundled(BundledFont.CODE, 13);
                current = new Editor(opened)
                        .multiline(true)
                        .markStyles(this::styleOf)
                        .wrapWidth(WIDTH - 2 * PADDING)
                        .text("Type here. The caret, the selection, Ctrl+Z and the word jumps are the"
                                + " toolkit's: this card holds an Editor and draws what it says.");
                if (host != null) {
                    current.clipboard(host.clipboard());
                    // Only where there is one: X11 and Wayland.
                    host.primarySelection().ifPresent(current::primarySelection);
                }
                editor = current;
            }
            return current;
        }

        /// How words under `kinds` look on the note: a face per weight and
        /// slant, the rules for underline and strike-through, the accent for a
        /// link and a wash behind a spoiler.
        private MarkStyle styleOf(Set<MarkKind> kinds) {
            var bold = kinds.contains(MarkKind.Style.BOLD);
            var slanted = kinds.contains(MarkKind.Style.ITALIC);
            var face = kinds.contains(MarkKind.Style.CODE)
                    ? code
                    : bold && slanted ? strongItalic : bold ? strong : slanted ? italic : font;
            var rules = EnumSet.noneOf(TextDecoration.class);
            if (kinds.contains(MarkKind.Style.UNDERLINE)) {
                rules.add(TextDecoration.UNDERLINE);
            }
            if (kinds.contains(MarkKind.Style.STRIKE)) {
                rules.add(TextDecoration.LINE_THROUGH);
            }
            var link = kinds.stream().anyMatch(MarkKind.Link.class::isInstance);
            var spoiler = kinds.contains(MarkKind.Style.SPOILER);
            return new MarkStyle(
                    Objects.requireNonNull(face, "face"),
                    link ? Ink.ACCENT : MarkStyle.TEXT_COLOUR,
                    rules,
                    spoiler ? CssColor.fade(Ink.LINE, 0.3) : 0);
        }

        @Override
        protected void dispose() {
            if (font != null) {
                for (var opened : List.of(font, strong, italic, strongItalic, code)) {
                    if (opened != null) {
                        opened.close();
                    }
                }
                font = null;
                strong = null;
                italic = null;
                strongItalic = null;
                code = null;
                editor = null;
            }
        }

        @Override
        public Widget build(BuildContext context) {
            host = context.host().orElse(null);
            return new ShowcaseCard(
                            "sticky-card",
                            "A caret on a canvas",
                            "The editor under text-input works on its own, here on a canvas. Click into the"
                                    + " note and type: arrows, Ctrl+arrows, Shift to select, Ctrl+Z and the"
                                    + " clipboard all work.",
                            DocLink.to("components/drawing", "canvas"))
                    .of(new Canvas(this::paint, new StickyInput(), Attributes.NONE.id("sticky")));
        }

        private void paint(Frame frame, LogicalSize size) {
            frame.fillPath(Path.roundRect(MARGIN, MARGIN, WIDTH, HEIGHT, 6), Ink.PAPER);
            frame.strokePath(
                    Path.roundRect(MARGIN, MARGIN, WIDTH, HEIGHT, 6), Stroke.of(1), editing ? Ink.ACCENT : Ink.MUTED);
            frame.save();
            try {
                // Clipped to the paper, so a note typed past its bottom does not
                // write on the card.
                frame.clipTo(MARGIN, MARGIN, WIDTH, HEIGHT);
                editor().paint(
                                frame,
                                MARGIN + PADDING,
                                MARGIN + PADDING,
                                new Editor.Ink(Ink.WRITING, CssColor.fade(Ink.LINE, 0.45), Ink.WRITING),
                                editing);
            } finally {
                frame.restore();
            }
        }

        /// What the canvas hears: the pointer places the caret, keys and text go to
        /// the editor, and an unhandled key stays unhandled so Tab still moves on.
        private final class StickyInput implements Input {

            @Override
            public void onPointer(PointerEvent event) {
                var at = event.content();
                var x = at.x() - MARGIN - PADDING;
                var y = at.y() - MARGIN - PADDING;
                switch (event.kind()) {
                    case PRESSED -> {
                        // The middle button is X11's paste, and does nothing where
                        // there is no primary selection.
                        pasting = event.button() == PointerEvent.Button.MIDDLE;
                        if (pasting) {
                            setState(() -> editor().pastePrimaryAt(x, y));
                        } else {
                            setState(() ->
                                    editor().pointerAt(x, y, event.modifiers().shift(), event.clickCount()));
                        }
                    }
                    case MOVED -> {
                        if (!pasting && !Float.isNaN(event.pressX())) {
                            setState(() -> editor().pointerAt(x, y, true, 1));
                        }
                    }
                    case RELEASED -> {
                        if (!pasting) {
                            editor().pointerReleased();
                        }
                    }
                    default -> {}
                }
                event.consume();
            }

            @Override
            public void onKey(KeyEvent event) {
                if (editor().onKey(event)) {
                    setState(() -> {});
                    event.consume();
                }
            }

            @Override
            public void onText(TextEvent event) {
                if (editor().onText(event.text())) {
                    setState(() -> {});
                    event.consume();
                }
            }

            @Override
            public void onFocusChanged(boolean focused, boolean fromKeyboard) {
                setState(() -> editing = focused);
            }

            /// The switch that makes typing work: without it the sticky takes every
            /// arrow key and never a character.
            @Override
            public boolean wantsText() {
                return true;
            }

            @Override
            public String accessibleName() {
                return "A sticky note";
            }
        }
    }
}
