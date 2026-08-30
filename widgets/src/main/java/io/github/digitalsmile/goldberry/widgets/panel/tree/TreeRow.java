package io.github.digitalsmile.goldberry.widgets.panel.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.natives.yoga.style.StyleLength;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// One visible row of a [Tree] — a **part**, so it is styleable and not
/// constructible ([ADR-0065]).
///
/// ## The indent is a width, not a padding
///
/// §2: "indent 20 per level; chevron 16 in the indent gutter". The row draws a
/// gutter of `depth × 20` before its chevron, as a sized box rather than as
/// padding on the row, for two reasons: a stylesheet cannot compute a depth, and
/// the row's own background has to reach the left edge — an indented *padding*
/// would leave the selection highlight starting 40px in, which reads as a
/// misaligned row rather than a nested one.
///
/// ## The keyboard is the part §3 says has to be right
///
/// `Right` expands, or moves to the first child when it is already open; `Left`
/// collapses, or moves to the parent when it is already closed. That pairing is
/// what makes a tree navigable without ever looking at the chevron, and it is why
/// both keys are the row's rather than the tree's: only the row knows whether it
/// is open.
///
/// @param node       the model row this draws
/// @param depth      how many levels down it sits
/// @param expanded   whether its children are showing
/// @param selectable whether it may be chosen — false for a parent in a
///                   leaf-only tree (§3's `checkable`)
/// @param selected   whether it is the chosen row
/// ## The three keys that are the tree's rather than the row's
///
/// `Home`, `End` and `*` all need to know about rows this one cannot see — the
/// first and last of the whole flattened list, and every sibling of this one — so
/// each is a callback the tree hands down, in the shape [#onOut] already had
/// ([ADR-0209](../../../../../../../../book/src/adr/0209-a-tree-finishes-its-keyboard.md)).
/// Type-to-select is the same, and arrives as [TextEvent] rather than a key for
/// `select`'s reason: what a typeahead wants is what was *typed*, and one
/// character can take several keys.
///
/// @param node       the model row this draws
/// @param depth      how many levels down it sits
/// @param expanded   whether its children are showing
/// @param selectable whether it may be chosen — false for a parent in a
///                   leaf-only tree (§3's `checkable`)
/// @param selected   whether it is a chosen row
/// @param check      the state of its checkbox, or null when it has none
/// @param onToggle   asked to open or close
/// @param onSelect   asked to be chosen, **with the modifiers that were held** —
///                   `Ctrl` and `Shift` mean different things in a multi-select
///                   tree, and only the tree knows what they resolve to
/// @param onOut      asked to move to the parent, when there is nothing to close
/// @param onEnd      asked to move to the first or last visible row
/// @param onSiblings asked to open every sibling of this row — §3's `*`
/// @param onType     what was typed, for §3's type-to-select
/// @param onCheck    asked to tick or untick, when there is a box to tick
record TreeRow(
        TreeNode node,
        int depth,
        boolean expanded,
        boolean selectable,
        boolean selected,
        io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox.Value check,
        Runnable onToggle,
        java.util.function.Consumer<io.github.digitalsmile.goldberry.input.key.Modifiers> onSelect,
        Runnable onOut,
        java.util.function.IntConsumer onEnd,
        Runnable onSiblings,
        java.util.function.Consumer<String> onType,
        Runnable onCheck)
        implements Widget.Leaf, Styled, Paints, Handles {

    /// §2's "indent 20 per level".
    static final double INDENT = 20;

    @Override
    public String cssType() {
        return "tree-row";
    }

    @Override
    public String id() {
        return "tree-" + node.id();
    }

    /// Its node's id, so a row keeps its element — and its focus — when the model
    /// is rebuilt or reordered. §3's rule about expansion, applied to the
    /// reconciler.
    @Override
    public Object key() {
        return node.id();
    }

    @Override
    public Set<String> classes() {
        var out = new java.util.LinkedHashSet<String>(3);
        if (expanded) {
            out.add("expanded");
        }
        if (selected) {
            out.add("selected");
        }
        if (!selectable) {
            // A parent in a leaf-only tree: still a row, still navigable, and not
            // an answer. A class rather than `:disabled`, which would say it is
            // inert -- it is not, it opens and closes.
            out.add("heading");
        }
        return out;
    }

    /// Every row is a Tab stop's worth of the tree's own scope, so the arrows
    /// rove between them and the tree is one stop from outside (§7.2).
    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public List<Widget> children() {
        var parts = new ArrayList<Widget>(4);
        parts.add(new TreeIndent(depth));
        parts.add(new TreeChevron(node.mayHaveChildren(), expanded, onToggle));
        if (check != null) {
            // **Between the chevron and the label**, which is where every file
            // manager and installer puts it: the chevron belongs to the gutter
            // and says what the row *is*, and the box belongs to the content and
            // says what the reader has done to it.
            parts.add(new TreeCheck(check, onCheck));
        }
        parts.add(new TreeLabel(node.label()));
        return List.copyOf(parts);
    }

    /// A click on the chevron opens; a click on the rest of the row chooses, or
    /// opens when there is nothing to choose.
    ///
    /// The last clause is the one that was missing and it is not a nicety: in a
    /// **leaf-only** tree a parent is not an answer, so a click on "Europe" had
    /// nothing to do and did nothing — the chevron was the only way in, and the
    /// chevron had no handler either. A tree whose branches cannot be opened with
    /// a mouse is not a tree ([ADR-0185]).
    ///
    /// So: choose if it is an answer, and otherwise open it. A row that is both —
    /// a parent in an `any` tree — chooses, because that is what the click on its
    /// label means; its chevron is how it opens, which is every file manager's
    /// arrangement.
    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() != PointerEvent.Kind.CLICKED) {
            return;
        }
        if (selectable) {
            // **The modifiers travel with it.** A click is `Ctrl`-clicked or
            // `Shift`-clicked or neither, and which of the three it was decides
            // what the new selection is — a question only the tree can answer,
            // because a range runs over rows this one cannot see (ADR-0210).
            onSelect.accept(event.modifiers());
        } else if (node.mayHaveChildren()) {
            onToggle.run();
        }
        event.consume();
    }

    /// §3: "`Right` expands or moves to the first child, `Left` collapses or
    /// moves to the parent".
    ///
    /// `Enter` chooses, which is `option`'s rule in a list and the same one: a
    /// set where the keyboard commits must not choose before it.
    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED) {
            return;
        }
        // **`Enter` is handled before the unmodified guard**, because it is the
        // one key here that means something different when a modifier is held:
        // `Ctrl+Enter` adds a row to a selection and `Shift+Enter` sweeps to it,
        // which are the keyboard's halves of the same gestures the pointer has
        // (ADR-0210). `Alt` and the platform key are nobody's here and fall
        // through, so an application's `Alt+Enter` accelerator still reaches it.
        if (event.key() == Key.ENTER) {
            if (selectable && !event.modifiers().alt() && !event.modifiers().meta()) {
                onSelect.accept(event.modifiers());
                event.consume();
            }
            return;
        }
        // `Space` ticks the box, which is `checkbox`'s own key and the desktop
        // convention — and is why `Enter` is not: `Enter` belongs to a dialog's
        // default action, and a row that swallowed it would leave a form with no
        // way to submit once the focus was in a tree.
        if (event.key() == Key.SPACE && check != null && event.modifiers().none()) {
            if (onCheck != null) {
                onCheck.run();
            }
            event.consume();
            return;
        }
        if (!event.modifiers().none()) {
            return;
        }
        switch (event.key()) {
            case RIGHT -> {
                if (node.mayHaveChildren() && !expanded) {
                    onToggle.run();
                    event.consume();
                }
                // Already open: the arrow falls through to the scope, which moves
                // to the next row -- and the next row *is* the first child.
            }
            case LEFT -> {
                if (expanded) {
                    onToggle.run();
                } else {
                    onOut.run();
                }
                event.consume();
            }
            // §3: "`Home`/`End` go to the first and last **visible** rows".
            //
            // Visible in the tree's sense -- the flattened list -- and not in the
            // viewport's: `End` in a scrolled tree lands on the last row of the
            // model and scrolls to it, which is what every tree does and what
            // `Ctrl+End` means in every document. A tree's own scrolling is a
            // `scroll` ancestor's business, and the focus ring is what asks it to
            // follow (ADR-0120).
            case HOME -> {
                onEnd.accept(-1);
                event.consume();
            }
            case END -> {
                onEnd.accept(1);
                event.consume();
            }
            default -> {}
        }
    }

    /// §3's `*`: "expands every sibling".
    ///
    /// A [TextEvent] rather than a key, because `*` is a *character* and the key
    /// it takes differs by layout — `Shift+8` on a US keyboard, the numpad's own
    /// key on any, and neither on AZERTY. Asking for the key would be asking for
    /// the position, which §7.1 says this toolkit does not answer.
    ///
    /// Everything else typed is the typeahead. Both live here rather than in two
    /// handlers because they arrive through one event, and the split between them
    /// is one character.
    @Override
    public void onText(io.github.digitalsmile.goldberry.input.event.TextEvent event) {
        if (event.text().isEmpty()) {
            return;
        }
        if ("*".equals(event.text())) {
            if (onSiblings != null) {
                onSiblings.run();
            }
            event.consume();
            return;
        }
        if (onType != null) {
            onType.accept(event.text());
        }
        event.consume();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }

    /// The gutter before the chevron — `depth × 20`, as a sized box.
    record TreeIndent(int depth) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "tree-indent";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).size(StyleLength.points((float) (depth * INDENT)), StyleLength.UNDEFINED);
        }
    }

    /// The mark saying a row opens, drawn only when it may.
    ///
    /// ## §2's `rotate` is two marks instead
    ///
    /// §2 asks for "expand/collapse: chevron `rotate` base" and it is not
    /// available: §8's subset has no `transform` **on a mark**, which is the wall
    /// `select`'s chevron hit and the reason [Box.Mark.Kind#CHEVRON_DOWN] exists
    /// beside [Box.Mark.Kind#CHEVRON_END] at all ([ADR-0141]). So a closed row
    /// draws `>` and an open one draws `v`, switching rather than turning.
    ///
    /// The cost is the animation, and it is the whole cost: the two marks are the
    /// two ends the rotation would have interpolated between.
    record TreeChevron(boolean present, boolean expanded, Runnable onToggle)
            implements Widget.Leaf, Styled, Paints, Handles {

        @Override
        public String cssType() {
            return "tree-chevron";
        }

        @Override
        public Set<String> classes() {
            return expanded ? Set.of("expanded") : Set.of();
        }

        @Override
        public boolean isFocusable() {
            return false;
        }

        /// **Consumed**, so opening a folder does not also select it — `TabClose`'s
        /// rule and the same mistake it exists to avoid.
        @Override
        public void onPointer(PointerEvent event) {
            if (event.kind() == PointerEvent.Kind.CLICKED && present) {
                if (onToggle != null) {
                    onToggle.run();
                }
                event.consume();
            }
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            if (!present) {
                // A leaf keeps the gutter -- the labels of a folder and a file at
                // one level have to line up -- and draws nothing in it.
                return Box.of().style(style);
            }
            return Box.of()
                    .style(style)
                    .mark(new Box.Mark(
                            expanded ? Box.Mark.Kind.CHEVRON_DOWN : Box.Mark.Kind.CHEVRON_END, style.color(), 1.5));
        }
    }

    /// The box §3's `checkable=` puts on a row, drawn only where there is one.
    ///
    /// ## It borrows `checkbox`'s indicator rather than drawing its own
    ///
    /// A `check-indicator` is already a 16px square that draws a tick, draws a
    /// bar for the mixed state, and takes its colours from `:checked` and
    /// `:indeterminate` rules a theme has already written. A second one here
    /// would be a second thing to keep in step with the first, and the first is
    /// where the reasoning about the tri-state lives — that the mixed mark is a
    /// *bar* and not a greyed tick, because "some of these are on" and "all of
    /// these are on" have to be distinguishable at a glance
    /// ([ADR-0210](../../../../../../../../book/src/adr/0210-a-tree-checks-and-selects-two-different-things.md)).
    ///
    /// What this adds is the hit target and the click. The indicator is a
    /// [Paints] leaf with no handler — it is a square inside a control, and the
    /// control is what a pointer talks to — so a row that simply nested one would
    /// have a box that could be looked at and not ticked.
    ///
    /// ## And it consumes the click
    ///
    /// [TreeChevron]'s rule, and the same mistake it exists to avoid: ticking a
    /// row must not also select it. They are two values (§3 asks for both), and
    /// a click that did both would make the checkbox unusable in a
    /// single-selection tree — every tick would move the highlight.
    record TreeCheck(io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox.Value state, Runnable onCheck)
            implements Widget.Leaf, Styled, Paints, Handles {

        /// The mark's stroke, in logical pixels — §1.6's icon stroke, which is
        /// what `checkbox` draws its own tick at.
        private static final double MARK_THICKNESS = 2;

        @Override
        public String cssType() {
            return "tree-check";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public boolean isFocusable() {
            // The row is the Tab stop and `Space` is how the keyboard reaches
            // this, so a focusable box would put a second stop inside a control
            // that §7.2 says is one.
            return false;
        }

        @Override
        public List<Widget> children() {
            return List.of(new io.github.digitalsmile.goldberry.widgets.controls.checkbox.CheckIndicator(
                    state, false, MARK_THICKNESS));
        }

        @Override
        public void onPointer(PointerEvent event) {
            if (event.kind() == PointerEvent.Kind.CLICKED) {
                if (onCheck != null) {
                    onCheck.run();
                }
                event.consume();
            }
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).children(children.toArray(Box[]::new));
        }
    }

    /// The row's words. A child box rather than text on the row's own node,
    /// because a box with text is a measured leaf and Yoga never lays a measured
    /// node's children out.
    record TreeLabel(String text) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "tree-label";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).children(Box.text(context.paragraph(style, text), style.color()));
        }
    }
}
