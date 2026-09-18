package io.github.digitalsmile.goldberry.content.select;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.Clipboard;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// A rendered document a reader can select text in and copy from.
///
/// What both content views build instead of the fold's bare column, and the only
/// stateful thing in either of them. The state is the selection and the geometry: two
/// things that must survive a rebuild, because a preview re-parses on every keystroke
/// and a reader's selection should not vanish because the frame after it was a
/// different object (ADR-0301).
///
/// ```
/// selection-host          this: hears the pointer and the keyboard
/// └── .markdown / .html   the fold's own column
///     ├── selection-layer the wash, absolutely positioned and painted first
///     └── …               the document
/// ```
///
/// ## What it does, and what it deliberately does not
///
/// - **Drag** to select, **double-click** for a word, **triple-click** for a block —
///   a paragraph, a heading, a cell, a line of a fence.
/// - **`Ctrl+C`** copies what is selected, with the separators the document implies:
///   a space between words, a newline between blocks. **`Ctrl+A`** takes the lot and
///   **`Escape`** lets it go.
/// - A link and an image are **part of the selection** — their words are in it and
///   their rectangles are washed — because a selection that skipped them would copy
///   "Read first." out of "Read the help first."
/// - It is **not an editor**. There is no caret, nothing blinks, and nothing can be
///   typed: what a reader can do to a document here is read it, take a copy of part
///   of it, and follow what it points at.
/// - A drag that **starts** on a link or a task box belongs to that control, because
///   the router captures on press — so a link is pressed rather than selected from.
///   Dragging *through* one is fine, and that is the common case.
///
/// ## What decides a selection has outlived its document
///
/// Not the document. This used to carry the view's resolved `Document` beside the
/// fold, on the reading that two builds could be compared — and nothing ever read
/// it, because the comparison that matters is finer than a document: [WordGeometry]
/// reports whether the **words** this build registered say something different from
/// the ones the selection was measured against, which is the question asked in
/// `build` below and the only one an offset into a flat list can be answered by.
///
/// @param fold what turns the minter, the memo and the overlay into the document's
///        widgets — the view's own, because only it knows whether this is Markdown or
///        HTML
public record SelectableDocument(Fold fold) implements Widget.Stateful {

    /// What a view does with the three things this state owns.
    ///
    /// A named interface rather than a `BiFunction` since [ADR-0389] put a third thing
    /// in it: the memo, which is what a view hands its unchanged blocks back from.
    @FunctionalInterface
    public interface Fold {

        /// The document, as widgets.
        ///
        /// @param minter where the words come from, fresh for this build
        /// @param memo what the last build made, kept across them
        /// @param overlay the selection's wash, which goes first
        Widget apply(WordMinter minter, BlockMemo memo, Widget overlay);
    }

    public SelectableDocument {
        Objects.requireNonNull(fold, "fold");
    }

    @Override
    public State<?> createState() {
        return new DocumentState();
    }

    static final class DocumentState extends State<SelectableDocument> {

        private final WordGeometry geometry = new WordGeometry();

        /// What the last build made, so this one can hand back the blocks nobody
        /// touched ([ADR-0389]). Beside the geometry because the two are one thing:
        /// a memoized block's words report into entries this geometry owns.
        private final BlockMemo memo = new BlockMemo();

        private final Selection selection = new Selection();

        /// Whether a press is still down, so a `MOVED` is a drag rather than a hover.
        private boolean dragging;

        @Override
        public Widget build(BuildContext context) {
            // The fold registers every word into the geometry as it walks, in document
            // order, which is what makes an index mean the same thing to the fold, the
            // geometry and the selection.
            geometry.beginBuild();
            // The minter is this build's and the memo is not: one walk mints one
            // document's words, and what survives is the widgets and the entries they
            // report into.
            var content =
                    widget().fold().apply(new WordMinter(geometry), memo, new SelectionLayer(selection, geometry));
            if (geometry.endBuild()) {
                // The document says something different from the one the selection was
                // measured against. Keeping it would highlight whatever is now at those
                // indices, which is worse than losing it: a preview re-parses on every
                // keystroke, and a selection that survived a keystroke would be a
                // selection of somebody else's words.
                selection.clear();
            }
            return new SelectionHost(content, this);
        }

        /// The pointer, in the window's own coordinates — which is the space the
        /// geometry is in, because [io.github.digitalsmile.goldberry.input.handler.Located]
        /// reports where a word was **painted**.
        void onPointer(PointerEvent event) {
            switch (event.kind()) {
                case PRESSED -> {
                    // A second or third click of the same gesture selects more rather
                    // than starting again -- `clickCount` is the router's count of a
                    // run, which is the same one `text-input` reads.
                    var caret = geometry.at(event.x(), event.y());
                    switch (event.clickCount()) {
                        case 2 -> {
                            var word = geometry.wordAt(caret);
                            selection.select(word[0], word[1]);
                        }
                        case 3 -> {
                            var block = geometry.blockAt(caret);
                            selection.select(block[0], block[1]);
                        }
                        default -> selection.begin(caret);
                    }
                    dragging = event.clickCount() <= 1;
                    repaint(event);
                    event.consume();
                }
                case MOVED -> {
                    if (dragging) {
                        selection.extendTo(geometry.at(event.x(), event.y()));
                        repaint(event);
                        event.consume();
                    }
                }
                case RELEASED -> {
                    dragging = false;
                    // Not consumed: a release that follows a press the document handled
                    // is also what produces the CLICKED a link would want, and this node
                    // is the one behind them rather than the one in front.
                }
                default -> {}
            }
        }

        void onKey(KeyEvent event) {
            if (event.kind() != KeyEvent.Kind.PRESSED) {
                return;
            }
            if (event.key() == Key.ESCAPE && !selection.isEmpty()) {
                selection.clear();
                repaint(event.target());
                event.consume();
                return;
            }
            if (!event.modifiers().onlyControl()) {
                return;
            }
            switch (event.key()) {
                case A -> {
                    selection.select(new Caret(0, 0), geometry.end());
                    repaint(event.target());
                    event.consume();
                }
                case C -> {
                    if (copy(event)) {
                        event.consume();
                    }
                }
                default -> {}
            }
        }

        /// Puts the selected text on the clipboard, and says whether there was any.
        ///
        /// The clipboard is reached through the element that received the key, which is
        /// the one route a widget has to the window it is in — a view does not hold a
        /// `Host`, and one passed in at construction would be a lifetime for an
        /// application to get wrong.
        private boolean copy(KeyEvent event) {
            var host = event.target().host();
            return host.isPresent() && copyTo(host.get().clipboard());
        }

        /// The same, against a clipboard somebody else found.
        ///
        /// Split out so a test can drive a copy without standing up a window: a `Host`
        /// is forty methods and a [Clipboard] is three, and what is worth asserting is
        /// the **text** — that a selection across two paragraphs arrives with the
        /// newline the document implies.
        boolean copyTo(Clipboard clipboard) {
            var text = geometry.text(selection.anchor(), selection.focus());
            return !text.isEmpty() && clipboard.text(text);
        }

        /// What a selection change costs: **one repaint**, and no rebuild.
        ///
        /// `setState` would be the reflex and it is the wrong call here — it rebuilds
        /// the document and re-resolves every style, per pointer move. The overlay's
        /// painter reads the selection at paint time, so a frame is all this needs.
        private void repaint(PointerEvent event) {
            repaint(event.target());
        }

        private void repaint(@Nullable Element target) {
            if (target != null) {
                target.host().ifPresent(host -> host.repaint());
            }
        }

        boolean hasSelection() {
            return !selection.isEmpty();
        }

        /// The whole document as a copy would take it — what `Ctrl+A` and then
        /// `Ctrl+C` produce, for a test with no window to press them in.
        String text() {
            return geometry.text(new Caret(0, 0), geometry.end());
        }

        /// What the last build kept and what it built — for a test, which is where a
        /// claim about reuse belongs: it is a **count**, and a count says the same
        /// thing on a loaded machine as on an idle one.
        BlockMemo memo() {
            return memo;
        }

        /// What is selected, for a test and for a caller that wants it without the
        /// clipboard.
        String selectedText() {
            return geometry.text(selection.anchor(), selection.focus());
        }
    }

    /// The node that hears the pointer and the keyboard — `selection-host`, a part.
    ///
    /// A node of its own rather than input on the document's own column, because that
    /// column is `column` from the catalog and a shared widget does not carry a
    /// handler per instance. It adds no padding, no background and no size: what a
    /// stylesheet reaches through `selection-host` is the **cursor**, which is the one
    /// visual thing a selectable region owes a reader.
    record SelectionHost(Widget content, DocumentState state)
            implements Widget.Leaf, Styled, Paints, Handles, Semantics {

        @Override
        public String cssType() {
            return "selection-host";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public List<Widget> children() {
            return List.of(content);
        }

        /// Focusable, because `Ctrl+C` goes to whatever has the focus and a document
        /// nobody can focus is a document nobody can copy from. A click focuses it,
        /// which is also how a reader gets there.
        @Override
        public boolean isFocusable() {
            return true;
        }

        @Override
        public void onPointer(PointerEvent event) {
            state.onPointer(event);
        }

        @Override
        public void onKey(KeyEvent event) {
            state.onKey(event);
        }

        /// A document, which is what a screen reader should call it — and the name is
        /// the content's, not this node's.
        @Override
        public Role role() {
            return Role.GROUP;
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).children(children.toArray(Box[]::new));
        }
    }
}
