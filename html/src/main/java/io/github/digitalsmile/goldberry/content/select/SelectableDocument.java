package io.github.digitalsmile.goldberry.content.select;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.handler.Located;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.clipboard.Clipboard;
import io.github.digitalsmile.goldberry.render.clipboard.PrimarySelection;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.core.scroll.EdgeScroll;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollAxis;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollScope;

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
/// - A drag **held at the edge** of the `scroll` around the document carries it on,
///   faster the further past the edge the pointer is, and the selection follows the
///   words that arrive ([EdgeScroll], [ADR-0500]). A wheel turned mid-drag scrolls
///   as a wheel does, and the selection follows that too.
/// - **`Ctrl+C`** copies what is selected, with the separators the document implies:
///   a space between words, a newline between blocks. **`Ctrl+A`** takes the lot and
///   **`Escape`** lets it go.
/// - A selection is **published to the primary selection** when it is finished — the
///   button comes up, or `Ctrl+A` lands — where the platform has one, which is X11
///   and Wayland: a middle click in another application pastes it ([ADR-0504]).
/// - A link and an image are **part of the selection** — their boxes are washed and a
///   link's label is in what is copied — because a selection that skipped them would
///   copy "Read first." out of "Read the help first." An image contributes no text,
///   which is what a browser puts on the clipboard for one. Neither is shaped by a
///   word, so a caret *inside* one is a proportion of its box rather than a character
///   boundary ([WordGeometry]); the ends are exact, which is what a double-click and a
///   drag across one need.
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

        /// Whether the gesture in progress began with a press here — a drag, a
        /// double or a triple click — so its release is a selection finished and
        /// worth publishing ([ADR-0504]).
        private boolean selecting;

        /// The viewport this document is in, carried on while a drag is held at its
        /// edge — and the clamp that keeps a pointer past the edge asking about the
        /// words at it rather than about nothing ([ADR-0500]).
        private final EdgeScroll edge = new EdgeScroll();

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
                    selecting = true;
                    if (dragging) {
                        // The viewport is found from the element that heard the
                        // press, which is the one route from a widget to the tree
                        // around it -- no controller wired, nothing the view was
                        // handed ([ADR-0439]).
                        var scope = ScrollScope.enclosing(event.target()).orElse(null);
                        edge.hold(scope == null ? null : scope::nudge, scope == null ? ScrollAxis.BOTH : scope.axis());
                        edge.pointer(event.x(), event.y());
                    }
                    repaint(event);
                    event.consume();
                }
                case MOVED -> {
                    if (dragging) {
                        // The pointer pulled back inside the viewport: past its edge
                        // every word is clipped away and `at` answers nothing, which
                        // is where a drag used to stop selecting.
                        edge.pointer(event.x(), event.y());
                        selection.extendTo(geometry.at(edge.x(), edge.y()));
                        repaint(event);
                        event.consume();
                    }
                }
                case RELEASED -> {
                    dragging = false;
                    edge.release();
                    // The selection is finished, so it goes on the primary selection
                    // where the platform has one: once per gesture, because every
                    // write is an ownership change the whole desktop hears about.
                    if (selecting) {
                        selecting = false;
                        publish(event.target());
                    }
                    // Not consumed: a release that follows a press the document handled
                    // is also what produces the CLICKED a link would want, and this node
                    // is the one behind them rather than the one in front.
                }
                default -> {}
            }
        }

        /// One frame of a held drag, from the host's `render` — the one place the
        /// frame clock is handed to a widget.
        ///
        /// Carries the viewport on when the pointer is at its edge, and then asks
        /// again what is under the pointer **whether or not it moved it**: a wheel
        /// turned mid-drag moves the words under a pointer that is holding still,
        /// and a selection that waited for the pointer to move would lag the page.
        /// The geometry is last frame's, which is what is on the screen; what this
        /// step moves is painted next frame and selected the frame after.
        ///
        /// The selection is written and not repainted: this runs inside a frame
        /// whose paint has not happened yet, and the wash reads it at paint time.
        void frame(double nowMillis) {
            if (!dragging) {
                return;
            }
            edge.tick(nowMillis);
            selection.extendTo(geometry.at(edge.x(), edge.y()));
        }

        /// Whether a held drag still has somewhere to carry the viewport.
        boolean autoScrolling() {
            return edge.isScrolling();
        }

        /// What clips the host, in window coordinates — which inside a `scroll` is
        /// its viewport, and the rectangle every word here is clipped to.
        void located(LogicalRect clip) {
            edge.viewport(clip);
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
                    publish(event.target());
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
            var host = Objects.requireNonNull(event.target(), "a key reaches a view only through a focused node")
                    .host();
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

        /// Puts a finished selection on the primary selection — X11's middle-click
        /// buffer — where the window this document is in has one ([ADR-0504]).
        ///
        /// Found through the element, for [#copy]'s reason. A document is never a
        /// paste target, so this is the only half of the primary selection it has.
        private void publish(@Nullable Element target) {
            if (target == null || selection.isEmpty()) {
                return;
            }
            target.host().flatMap(Host::primarySelection).ifPresent(this::offerTo);
        }

        /// The same, against a primary selection somebody else found — [#copyTo]'s
        /// seam, for a test with no window.
        ///
        /// @return whether there was a selection and it was accepted
        boolean offerTo(PrimarySelection primary) {
            var text = geometry.text(selection.anchor(), selection.focus());
            return !text.isEmpty() && primary.text(text);
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

        /// The rectangles the wash covers, in window coordinates — the same list
        /// [SelectionLayer] paints, for a test that cannot read a painter's fills.
        ///
        /// Worth asserting separately from the text because the two answer different
        /// questions: what a copy takes comes from the entries, and what a reader sees
        /// highlighted comes from the geometry. A link had the first and not the
        /// second.
        List<LogicalRect> washed() {
            return geometry.rectangles(selection.anchor(), selection.focus());
        }
    }

    /// The node that hears the pointer and the keyboard — `selection-host`, a part.
    ///
    /// A node of its own rather than input on the document's own column, because that
    /// column is `column` from the catalog and a shared widget does not carry a
    /// handler per instance. It adds no padding, no background and no size: what a
    /// stylesheet reaches through `selection-host` is the **cursor**, which is the one
    /// visual thing a selectable region owes a reader.
    ///
    /// It is also the node that is told where the viewport around the document is,
    /// and whose `render` steps a drag held at the viewport's edge — it is the one
    /// node every document has whatever it says, so one clock serves the whole of it
    /// ([ADR-0500]).
    record SelectionHost(Widget content, DocumentState state)
            implements Widget.Leaf, Styled, Paints, Handles, Located, Semantics {

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

        /// The clip is what matters: inside a `scroll` it is the viewport, which is
        /// what a held drag measures its edge against.
        @Override
        public void located(LogicalRect self, LogicalRect clip) {
            state.located(clip);
        }

        /// While a drag is held past the viewport's edge and it has somewhere to go.
        @Override
        public boolean isAnimating() {
            return state.autoScrolling();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            state.frame(context.nowMillis());
            return Box.of().style(style).children(children.toArray(Box[]::new));
        }
    }
}
