package io.github.digitalsmile.goldberry.widgets.panel.tree;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.natives.yoga.style.StyleLength;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
/// @param onToggle   asked to open or close
/// @param onSelect   asked to be chosen
/// @param onOut      asked to move to the parent, when there is nothing to close
record TreeRow(TreeNode node, int depth, boolean expanded, boolean selectable, boolean selected,
        Runnable onToggle, Runnable onSelect, Runnable onOut)
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
        var parts = new ArrayList<Widget>(3);
        parts.add(new TreeIndent(depth));
        parts.add(new TreeChevron(node.mayHaveChildren(), expanded));
        parts.add(new TreeLabel(node.label()));
        return List.copyOf(parts);
    }

    /// A click chooses, and a click on the chevron opens — the chevron consumes
    /// its own, so opening a folder does not also select it.
    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.CLICKED && selectable) {
            onSelect.run();
            event.consume();
        }
    }

    /// §3: "`Right` expands or moves to the first child, `Left` collapses or
    /// moves to the parent".
    ///
    /// `Enter` chooses, which is `option`'s rule in a list and the same one: a
    /// set where the keyboard commits must not choose before it.
    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED || !event.modifiers().none()) {
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
            case ENTER -> {
                if (selectable) {
                    onSelect.run();
                    event.consume();
                }
            }
            default -> {
            }
        }
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
            return Box.of().style(style)
                    .size(StyleLength.points((float) (depth * INDENT)), StyleLength.UNDEFINED);
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
    record TreeChevron(boolean present, boolean expanded)
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

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            if (!present) {
                // A leaf keeps the gutter -- the labels of a folder and a file at
                // one level have to line up -- and draws nothing in it.
                return Box.of().style(style);
            }
            return Box.of().style(style)
                    .mark(new Box.Mark(
                            expanded ? Box.Mark.Kind.CHEVRON_DOWN : Box.Mark.Kind.CHEVRON_END,
                            style.color(), 1.5));
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
            return Box.of().style(style)
                    .children(Box.text(context.paragraph(style, text), style.color()));
        }
    }
}
