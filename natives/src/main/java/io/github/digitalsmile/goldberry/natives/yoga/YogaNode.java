package io.github.digitalsmile.goldberry.natives.yoga;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// A node in a Yoga layout tree.
///
/// This is the layout engine as the rest of Goldberry sees it: a widget owns one
/// of these, sets the style properties its computed CSS resolved to, and reads
/// back a [ComputedLayout] after a pass. The `YGNodeRef` inside never leaves the
/// module (`docs/ARCHITECTURE.md` §3.1).
///
/// ## Who owns a node
///
/// Yoga's C API frees a node with `YGNodeFree`, and after that the pointer is
/// dead while every Java object holding it is not — the classic FFM hazard, and
/// on a tree the easiest one to hit, because freeing a parent says nothing about
/// its children. So ownership here is explicit and one-directional:
///
/// - A node created by [#create()] is a **root**, and the caller owns it.
/// - [#insertChild] transfers ownership to the parent. A node with a parent
///   cannot be closed directly — [#close()] on it throws rather than leaving the
///   parent holding a freed child.
/// - [#close()] on a root frees the **whole subtree**, child-first, marking every
///   Java wrapper in it dead as its pointer goes. A stale reference to a closed
///   descendant then throws [IllegalStateException] on use instead of reading
///   released memory.
/// - [#removeChild] hands ownership back: the removed node becomes a root again,
///   and the caller has to close it.
///
/// ## What Yoga would otherwise abort on
///
/// Yoga's preconditions are `assert`s that call `abort()` — which takes the JVM
/// with them, giving no stack, no exception, and nothing to catch. Every one of
/// them that Goldberry can reach is checked here first and reported as an
/// exception naming what was violated: a node cannot have both children and a
/// measure function, a node cannot be inserted twice, a node cannot become its
/// own ancestor.
///
/// ## Threading
///
/// A node belongs to the thread that created it, and every method checks. Yoga
/// has no locking whatsoever, and a tree touched from two threads corrupts
/// quietly rather than failing — which is the worst of the options. The UI
/// thread is where layout belongs anyway (ADR-0020).
public final class YogaNode implements AutoCloseable {

    /// The absence of a constraint, for [#calculateLayout].
    ///
    /// Yoga spells this `YGUndefined`, which is a NaN. Passing it for a
    /// dimension means "as large as it wants to be" — which is what a scrollable
    /// axis, or a window still deciding its own size, asks for.
    public static final float UNDEFINED = Float.NaN;

    private final Yoga yoga = Yoga.get();
    private final MemorySegment pointer;
    private final YogaConfig config;
    private final Thread owner = Thread.currentThread();
    private final List<YogaNode> children = new ArrayList<>();

    private YogaNode parent;
    private MeasureCallback measure;
    private boolean freed;

    private YogaNode(YogaConfig config) {
        this.config = config;
        this.pointer = config == null ? yoga.nodeNew() : yoga.nodeNew(config.pointer());
        if (config != null) {
            config.nodeCreated();
        }
    }

    /// A node with Yoga's default config.
    ///
    /// Yoga's own defaults, which are not CSS's: `flex-direction` is `column`,
    /// `flex-shrink` is `0`, and computed positions are snapped to whole logical
    /// pixels. [#create(YogaConfig)] with a [YogaConfig] is what a widget tree
    /// should use — this exists for the cases with nothing to configure, and for
    /// tests that want the defaults Yoga documents.
    public static YogaNode create() {
        return new YogaNode(null);
    }

    /// A node sharing `config` with the rest of its tree.
    ///
    /// The config is not copied and is not owned: it must outlive the node, and
    /// [YogaConfig#close()] refuses while any node it made is alive.
    public static YogaNode create(YogaConfig config) {
        return new YogaNode(Objects.requireNonNull(config, "config"));
    }

    // --- tree ---------------------------------------------------------------

    /// Appends a child, taking ownership of it.
    ///
    /// @throws IllegalStateException if the child already has a parent, if this
    ///         node has a measure function, or if the child is this node or one
    ///         of its ancestors
    public void addChild(YogaNode child) {
        insertChild(child, children.size());
    }

    /// Inserts a child at `index`, taking ownership of it.
    ///
    /// @throws IllegalStateException  if the child already has a parent, if this
    ///         node has a measure function, or if the child is this node or one
    ///         of its ancestors
    /// @throws IndexOutOfBoundsException if `index` is outside `0..childCount()`
    public void insertChild(YogaNode child, int index) {
        requireUsable();
        Objects.requireNonNull(child, "child");
        // Checked before the child's own usable check, which would otherwise
        // report the confinement rule from the child's point of view and bury
        // the more useful fact: it is not this node's thread that is wrong.
        if (child.owner != owner) {
            throw new IllegalStateException(
                    "the child belongs to thread " + name(child.owner)
                            + " and this node to " + name(owner)
                            + " — a Yoga tree may not span threads");
        }
        child.requireUsable();
        if (child.parent != null) {
            throw new IllegalStateException(
                    "this node already has a parent — remove it before inserting it elsewhere,"
                            + " because Yoga would leave the old parent holding a child it no"
                            + " longer owns");
        }
        if (measure != null) {
            throw new IllegalStateException(
                    "a node with a measure function may not have children: Yoga asks the measure"
                            + " function for the size and never looks at them");
        }
        for (var ancestor = this; ancestor != null; ancestor = ancestor.parent) {
            if (ancestor == child) {
                throw new IllegalStateException(
                        "that would make the node its own ancestor, and a layout pass over a"
                                + " cycle does not terminate");
            }
        }
        Objects.checkIndex(index, children.size() + 1);

        yoga.nodeInsertChild(pointer, child.pointer, index);
        children.add(index, child);
        child.parent = this;
    }

    /// Detaches a child, handing ownership of it back to the caller.
    ///
    /// The removed node becomes a root: it is still alive, still holds its
    /// style, and now has to be closed by whoever holds it.
    ///
    /// @throws IllegalArgumentException if the node is not a child of this one
    public void removeChild(YogaNode child) {
        requireUsable();
        Objects.requireNonNull(child, "child");
        if (child.parent != this) {
            throw new IllegalArgumentException("that node is not a child of this one");
        }
        child.requireUsable();

        yoga.nodeRemoveChild(pointer, child.pointer);
        children.remove(child);
        child.parent = null;
    }

    /// Detaches every child, handing ownership of all of them back to the caller.
    public void removeAllChildren() {
        requireUsable();
        if (children.isEmpty()) {
            return;
        }
        yoga.nodeRemoveAllChildren(pointer);
        for (var child : children) {
            child.parent = null;
        }
        children.clear();
    }

    /// This node's children, in order. An unmodifiable view, not a copy — it
    /// tracks later insertions and removals.
    public List<YogaNode> children() {
        requireUsable();
        return Collections.unmodifiableList(children);
    }

    /// How many children this node has.
    public int childCount() {
        requireUsable();
        return children.size();
    }

    /// The node that owns this one, or null if it is a root.
    public YogaNode parent() {
        requireUsable();
        return parent;
    }

    // --- measurement --------------------------------------------------------

    /// Makes this node a measured leaf, sized by `function` rather than by its
    /// contents.
    ///
    /// This is how text enters layout: Yoga knows nothing about glyphs, so a
    /// paragraph is a leaf whose measure function shapes the text at the width
    /// Yoga proposes and reports how tall it came out. The function runs
    /// **during** [#calculateLayout], on the calling thread, possibly several
    /// times per pass as Yoga tries widths.
    ///
    /// The previous function, if any, is replaced and its callback closed.
    /// Passing null clears it.
    ///
    /// @throws IllegalStateException if this node has children
    public void setMeasureFunction(MeasureFunction function) {
        requireUsable();
        if (function != null && !children.isEmpty()) {
            throw new IllegalStateException(
                    "a node with " + children.size() + " child(ren) may not have a measure"
                            + " function: Yoga would ask the function for the size and never lay"
                            + " the children out");
        }

        var previous = measure;
        if (function == null) {
            measure = null;
            yoga.nodeMeasureFunc(pointer, MemorySegment.NULL);
        } else {
            var callback = MeasureCallback.of(function);
            try {
                yoga.nodeMeasureFunc(pointer, callback.pointer());
            } catch (RuntimeException | Error e) {
                callback.close();
                throw e;
            }
            measure = callback;
        }
        if (previous != null) {
            previous.close();
        }
    }

    /// Whether a measure function is attached.
    public boolean hasMeasureFunction() {
        requireUsable();
        return measure != null;
    }

    // --- dirtying -----------------------------------------------------------

    /// Tells Yoga this node's measurement is stale.
    ///
    /// Only meaningful on a measured leaf: Yoga marks a node dirty by itself
    /// whenever a style changes, but it cannot know that the *text* changed. So
    /// this is the call a paragraph makes when its content or its font does.
    ///
    /// @throws IllegalStateException if no measure function is attached — Yoga
    ///         aborts on this, and there is nothing for it to recompute anyway
    public void markDirty() {
        requireUsable();
        if (measure == null) {
            throw new IllegalStateException(
                    "only a node with a measure function can be marked dirty by hand;"
                            + " a style change already dirties a node by itself");
        }
        yoga.nodeMarkDirty(pointer);
    }

    /// Whether this node needs measuring again.
    public boolean isDirty() {
        requireUsable();
        return yoga.nodeIsDirty(pointer);
    }

    /// Whether the last pass gave this node a layout different from the one
    /// before it.
    ///
    /// This is what makes an incremental repaint possible: after a pass, a node
    /// whose layout did not change does not need repainting. The flag stays set
    /// until [#clearNewLayout()] clears it, so the paint layer is what decides
    /// when it has been acted on.
    public boolean hasNewLayout() {
        requireUsable();
        return yoga.nodeHasNewLayout(pointer);
    }

    /// Clears [#hasNewLayout()], acknowledging the layout.
    public void clearNewLayout() {
        requireUsable();
        yoga.nodeHasNewLayout(pointer, false);
    }

    // --- the layout pass ----------------------------------------------------

    /// Lays out this node and everything under it, left-to-right.
    ///
    /// @see #calculateLayout(float, float, Direction)
    public void calculateLayout(float availableWidth, float availableHeight) {
        calculateLayout(availableWidth, availableHeight, Direction.LTR);
    }

    /// Lays out this node and everything under it.
    ///
    /// The available size is in points and may be [#UNDEFINED] on either axis,
    /// meaning the tree may be as large as it likes there. `ownerDirection` is
    /// the writing direction the root inherits, which is what [Edge#START] and
    /// [Edge#END] resolve against.
    ///
    /// A measure function that threw during the pass is reported here rather
    /// than in the middle of native code — see [MeasureCallback]. If several
    /// threw, the first is thrown and the rest are attached as suppressed
    /// exceptions; none is left pending for a later pass to report.
    ///
    /// @throws IllegalStateException if this node has been closed
    public void calculateLayout(float availableWidth, float availableHeight, Direction ownerDirection) {
        requireUsable();
        Objects.requireNonNull(ownerDirection, "ownerDirection");

        yoga.nodeCalculateLayout(pointer, availableWidth, availableHeight, ownerDirection);

        // Collected across the whole subtree before any of it is thrown: Yoga
        // has already returned, so every callback that failed is holding
        // something, and leaving one held would surface it from the *next* pass.
        var failures = new ArrayList<Throwable>();
        collectMeasureFailures(failures);
        if (!failures.isEmpty()) {
            var first = failures.getFirst();
            for (var other : failures.subList(1, failures.size())) {
                if (other != first) {
                    first.addSuppressed(other);
                }
            }
            throw MeasureCallback.asUnchecked(first);
        }
    }

    /// Where the last pass put this node, relative to its parent's content box.
    public ComputedLayout layout() {
        requireUsable();
        return yoga.layout(pointer);
    }

    /// The padding the last pass resolved for one side, in points.
    ///
    /// Resolved, not declared: a percentage padding is a number here, and
    /// [Edge#START] has become a physical side.
    ///
    /// @throws IllegalArgumentException if the edge is not a single physical
    ///         side — Yoga answers zero for the shorthands rather than refusing,
    ///         which reads as "no padding"
    public float layoutPadding(Edge edge) {
        requirePhysicalSide(edge, "padding");
        return yoga.layoutPadding(pointer, edge);
    }

    /// The border width the last pass resolved for one side, in points.
    ///
    /// @throws IllegalArgumentException if the edge is not a single physical side
    public float layoutBorder(Edge edge) {
        requirePhysicalSide(edge, "border");
        return yoga.layoutBorder(pointer, edge);
    }

    /// The margin the last pass resolved for one side, in points.
    ///
    /// @throws IllegalArgumentException if the edge is not a single physical side
    public float layoutMargin(Edge edge) {
        requirePhysicalSide(edge, "margin");
        return yoga.layoutMargin(pointer, edge);
    }

    /// The writing direction this node resolved to, with [Direction#INHERIT]
    /// already followed up the tree.
    public Direction layoutDirection() {
        requireUsable();
        return yoga.layoutDirection(pointer);
    }

    /// Whether the last pass could not fit this node's children into it.
    public boolean hadOverflow() {
        requireUsable();
        return yoga.layoutHadOverflow(pointer);
    }

    // --- style: enums -------------------------------------------------------

    /// CSS `direction`.
    public void setDirection(Direction value) {
        requireUsable();
        yoga.styleDirection(pointer, Objects.requireNonNull(value, "direction"));
    }

    /// CSS `flex-direction`.
    public void setFlexDirection(FlexDirection value) {
        requireUsable();
        yoga.styleFlexDirection(pointer, Objects.requireNonNull(value, "flex-direction"));
    }

    /// CSS `justify-content`.
    public void setJustifyContent(Justify value) {
        requireUsable();
        yoga.styleJustifyContent(pointer, Objects.requireNonNull(value, "justify-content"));
    }

    /// CSS `align-content`.
    public void setAlignContent(Align value) {
        requireUsable();
        yoga.styleAlignContent(pointer, Objects.requireNonNull(value, "align-content"));
    }

    /// CSS `align-items`.
    public void setAlignItems(Align value) {
        requireUsable();
        yoga.styleAlignItems(pointer, Objects.requireNonNull(value, "align-items"));
    }

    /// CSS `align-self`.
    public void setAlignSelf(Align value) {
        requireUsable();
        yoga.styleAlignSelf(pointer, Objects.requireNonNull(value, "align-self"));
    }

    /// CSS `position`.
    public void setPositionType(PositionType value) {
        requireUsable();
        yoga.stylePositionType(pointer, Objects.requireNonNull(value, "position"));
    }

    /// CSS `flex-wrap`.
    public void setFlexWrap(Wrap value) {
        requireUsable();
        yoga.styleFlexWrap(pointer, Objects.requireNonNull(value, "flex-wrap"));
    }

    /// CSS `overflow`. Yoga only sizes from this; clipping happens in paint.
    public void setOverflow(Overflow value) {
        requireUsable();
        yoga.styleOverflow(pointer, Objects.requireNonNull(value, "overflow"));
    }

    /// CSS `display`.
    public void setDisplay(Display value) {
        requireUsable();
        yoga.styleDisplay(pointer, Objects.requireNonNull(value, "display"));
    }

    // --- style: numbers -----------------------------------------------------

    /// CSS `flex-grow`. Share of the free space this node takes.
    public void setFlexGrow(float value) {
        requireUsable();
        yoga.styleFlexGrow(pointer, value);
    }

    /// CSS `flex-shrink`. Share of the overflow this node gives up. `1` with web
    /// defaults on, `0` without — see [YogaConfig].
    public void setFlexShrink(float value) {
        requireUsable();
        yoga.styleFlexShrink(pointer, value);
    }

    /// CSS `aspect-ratio`, as width over height. [#UNDEFINED] clears it.
    public void setAspectRatio(float value) {
        requireUsable();
        yoga.styleAspectRatio(pointer, value);
    }

    /// CSS `border-width` for one edge, in points.
    ///
    /// Yoga takes a plain number here: there is no percentage border in CSS
    /// either. It affects layout only — the border is *drawn* from the computed
    /// style, and this is what reserves room for it.
    public void setBorder(Edge edge, float points) {
        requireUsable();
        yoga.styleBorder(pointer, Objects.requireNonNull(edge, "edge"), points);
    }

    // --- style: lengths -----------------------------------------------------

    /// CSS `width`.
    public void setWidth(StyleLength value) {
        requireUsable();
        yoga.styleWidth(pointer, Objects.requireNonNull(value, "width"));
    }

    /// CSS `height`.
    public void setHeight(StyleLength value) {
        requireUsable();
        yoga.styleHeight(pointer, Objects.requireNonNull(value, "height"));
    }

    /// CSS `min-width`. [StyleLength#AUTO] is not a value Yoga has for this.
    public void setMinWidth(StyleLength value) {
        requireUsable();
        yoga.styleMinWidth(pointer, Objects.requireNonNull(value, "min-width"));
    }

    /// CSS `min-height`. [StyleLength#AUTO] is not a value Yoga has for this.
    public void setMinHeight(StyleLength value) {
        requireUsable();
        yoga.styleMinHeight(pointer, Objects.requireNonNull(value, "min-height"));
    }

    /// CSS `max-width`. [StyleLength#AUTO] is not a value Yoga has for this.
    public void setMaxWidth(StyleLength value) {
        requireUsable();
        yoga.styleMaxWidth(pointer, Objects.requireNonNull(value, "max-width"));
    }

    /// CSS `max-height`. [StyleLength#AUTO] is not a value Yoga has for this.
    public void setMaxHeight(StyleLength value) {
        requireUsable();
        yoga.styleMaxHeight(pointer, Objects.requireNonNull(value, "max-height"));
    }

    /// CSS `flex-basis` — the size along the main axis before growing or
    /// shrinking.
    public void setFlexBasis(StyleLength value) {
        requireUsable();
        yoga.styleFlexBasis(pointer, Objects.requireNonNull(value, "flex-basis"));
    }

    /// CSS `inset` — `left`, `top`, `right`, `bottom` — for one edge.
    ///
    /// On a [PositionType#RELATIVE] node this offsets it from where flow put it;
    /// on an [PositionType#ABSOLUTE] one it places it against the containing
    /// block. [StyleLength#AUTO] is not a value Yoga has for this.
    public void setPosition(Edge edge, StyleLength value) {
        requireUsable();
        yoga.stylePosition(
                pointer, Objects.requireNonNull(edge, "edge"), Objects.requireNonNull(value, "inset"));
    }

    /// CSS `margin` for one edge. [StyleLength#AUTO] here absorbs free space,
    /// which is what centres a node between two of them.
    public void setMargin(Edge edge, StyleLength value) {
        requireUsable();
        yoga.styleMargin(
                pointer, Objects.requireNonNull(edge, "edge"), Objects.requireNonNull(value, "margin"));
    }

    /// CSS `padding` for one edge.
    public void setPadding(Edge edge, StyleLength value) {
        requireUsable();
        yoga.stylePadding(
                pointer, Objects.requireNonNull(edge, "edge"), Objects.requireNonNull(value, "padding"));
    }

    /// CSS `gap`, `row-gap` and `column-gap`, depending on the gutter.
    public void setGap(Gutter gutter, StyleLength value) {
        requireUsable();
        yoga.styleGap(
                pointer, Objects.requireNonNull(gutter, "gutter"), Objects.requireNonNull(value, "gap"));
    }

    // --- lifecycle ----------------------------------------------------------

    /// Frees this node and every node under it.
    ///
    /// Children are freed first, and each Java wrapper is marked closed as its
    /// pointer goes, so a reference kept to a descendant throws on use instead
    /// of reaching freed memory. Closing twice does nothing.
    ///
    /// @throws IllegalStateException if this node has a parent — the parent owns
    ///         it, and freeing it here would leave Yoga holding a dangling child
    @Override
    public void close() {
        if (freed) {
            return;
        }
        requireOwner();
        if (parent != null) {
            throw new IllegalStateException(
                    "this node is owned by its parent — call removeChild on the parent first,"
                            + " or close the root and let it free the whole tree");
        }
        free();
    }

    /// Whether this node has been freed, directly or with an ancestor.
    public boolean isClosed() {
        return freed;
    }

    /// Frees the subtree, child-first.
    ///
    /// `YGNodeFree` detaches a node from its owner and orphans its children, so
    /// this order is safe at every step: a child is always freed while its
    /// parent is still alive to be detached from.
    private void free() {
        for (var child : children) {
            child.parent = null;
            child.free();
        }
        children.clear();

        if (measure != null) {
            // After the node is gone nothing can call the stub, which is the
            // only moment closing its arena is safe.
            measure.close();
            measure = null;
        }
        freed = true;
        yoga.nodeFree(pointer);
        if (config != null) {
            config.nodeFreed();
        }
    }

    /// Walks the subtree taking each pending measure failure.
    private void collectMeasureFailures(List<Throwable> into) {
        if (measure != null) {
            var failure = measure.takeFailure();
            if (failure != null) {
                into.add(failure);
            }
        }
        for (var child : children) {
            child.collectMeasureFailures(into);
        }
    }

    private void requirePhysicalSide(Edge edge, String property) {
        requireUsable();
        Objects.requireNonNull(edge, "edge");
        if (!edge.isPhysicalSide()) {
            throw new IllegalArgumentException(
                    "the computed " + property + " of " + edge + " is not a question Yoga answers"
                            + " — ask for LEFT, TOP, RIGHT or BOTTOM, which is what the shorthand"
                            + " resolved to");
        }
    }

    private void requireUsable() {
        requireOwner();
        if (freed) {
            throw new IllegalStateException(
                    "this YogaNode has been closed, either directly or with an ancestor");
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException(
                    "a YogaNode belongs to the thread that created it (" + name(owner)
                            + "), and this is " + name(Thread.currentThread())
                            + " — Yoga has no locking, so a shared tree corrupts silently");
        }
    }

    /// A thread's name, or its id when it has none — which is the ordinary case
    /// for a virtual thread, and would otherwise leave the message naming an
    /// empty pair of brackets.
    private static String name(Thread thread) {
        var name = thread.getName();
        return name.isEmpty() ? "#" + thread.threadId() : name;
    }

    /// How many children Yoga itself thinks this node has.
    ///
    /// Only used by the tests, which assert it against [#childCount()]: this
    /// class keeps its own list so that it can hand back wrappers rather than
    /// pointers, and the two views agreeing is what says the list is not a
    /// fiction.
    long nativeChildCount() {
        requireUsable();
        return yoga.nodeChildCount(pointer);
    }

    /// Whether Yoga itself thinks a measure function is attached. The tests
    /// check this against [#hasMeasureFunction()] for the same reason.
    boolean nativeHasMeasureFunction() {
        requireUsable();
        return yoga.nodeHasMeasureFunc(pointer);
    }

    @Override
    public String toString() {
        if (freed) {
            return "YogaNode (closed)";
        }
        return "YogaNode[children=" + children.size()
                + (measure != null ? ", measured" : "")
                + (parent == null ? ", root" : "")
                + "]";
    }
}
