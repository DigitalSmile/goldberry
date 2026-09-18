package io.github.digitalsmile.goldberry.paint.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.value.Affine;
import io.github.digitalsmile.goldberry.layout.Insets;
import io.github.digitalsmile.goldberry.natives.yoga.YogaConfig;
import io.github.digitalsmile.goldberry.natives.yoga.YogaNode;
import io.github.digitalsmile.goldberry.natives.yoga.style.Edge;
import io.github.digitalsmile.goldberry.natives.yoga.style.Gutter;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.paint.Layer;
import io.github.digitalsmile.goldberry.paint.cull.BoxInk;
import io.github.digitalsmile.goldberry.paint.cull.Ink;
import io.github.digitalsmile.goldberry.render.DamageRect;
import io.github.digitalsmile.goldberry.render.model.DisplayScale;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;
import io.github.digitalsmile.goldberry.text.flow.TextFlow;

/// One visual node, kept between frames — ADR-0004's third tree.
///
/// > **Render objects** — one per visual node. Owns a `YGNode`, a
/// > `ComputedStyle`, and the paint logic.
///
/// It owns the `YGNode`, and the [Box] it holds is the style. What it does *not*
/// own is the paint logic, which stays in [BoxPainter] as a function of the box —
/// there is one drawing routine either way, and giving each render object a
/// virtual `paint` would be inheritance where a value and a function will do.
///
/// ## What retention buys, and where
///
/// Nothing here makes a first frame faster. The whole benefit is in the second
/// frame and every one after it:
///
/// - **The Yoga node survives.** Building one is cheap; attaching a measure
///   function is not — a [io.github.digitalsmile.goldberry.natives.yoga.MeasureCallback]
///   is a confined `Arena` and a `MethodHandle` bound into native code, measured
///   at 11 µs against 0.3 µs for actually calling through it
///   (ADR-0037).
///   Paid per text node per frame, it was the largest single cost of text in a
///   layout pass.
/// - **Yoga skips what did not change.** Yoga dirties a node when a style is
///   *set on it*, not when the value differs — so re-setting an unchanged width
///   every frame dirties the whole tree and the layout cache never hits. Every
///   setter here is guarded by a comparison against the box already applied, and
///   that guard is what turns retention into a layout that is skipped rather than
///   merely a tree that is not rebuilt.
///
/// ## Reconciled against a value, not mutated by widgets
///
/// A widget still describes itself as an immutable [Box]
/// (ADR-0053),
/// and this tree is diffed against that description. Widgets never touch a render
/// object. That keeps the declarative contract intact and makes the diff input an
/// immutable tree, which is the ideal thing to diff — and it means this whole
/// layer could be deleted and the toolkit would still draw, just slower.
///
/// Confined to the UI thread, like the Yoga nodes underneath it, and must be
/// closed — it holds native memory in the node and in the measure callback.
public final class RenderObject implements AutoCloseable {

    private final YogaNode node;
    private final List<RenderObject> children = new ArrayList<>();

    /// The box whose values are currently *on* the Yoga node.
    ///
    /// The comparison target for every guard below. Null until the first
    /// [#apply], which is what makes the first frame set everything.
    private @Nullable Box applied;

    /// What this subtree draws, in this node's own coordinates — see [#ink()].
    ///
    /// [Ink#NONE] until the first [#settle], and [Ink#NONE] overlaps nothing —
    /// which is the safe way round only because a tree is always settled before
    /// it is painted, and [RenderTree#paint] refuses a tree that never was.
    private Ink ink = Ink.NONE;

    /// The inset currently *on* the node, which is not the one the box declared.
    ///
    /// An absolutely positioned child is shifted by its containing block's
    /// padding before it reaches Yoga ([ContainingBlock]), so the box's own
    /// `inset` is the wrong thing to guard against: a parent that grew padding
    /// moves this child without changing a single field of its box. Kept
    /// separately rather than derived, because deriving it needs the parent's
    /// padding and this object has never had a reference to its parent.
    private @Nullable Insets appliedInset;

    /// The paragraph the attached measure callback measures, by identity.
    ///
    /// Identity rather than equality, deliberately: the callback closes over one
    /// `Paragraph`, and an equal-but-distinct one would wrap against a different
    /// memo. `ParagraphCache` is what makes this stable frame to frame, which is
    /// why [io.github.digitalsmile.goldberry.widget.style.Paints.Context] shapes through
    /// it rather than letting widgets call `Paragraph.of`.
    private @Nullable Object measured;

    /// The flow the attached callback was built for.
    ///
    /// By **equality**, unlike [#measured]: a `TextFlow` is a value, two equal
    /// ones measure identically, and the cascade hands out a fresh instance on
    /// every resolution. Comparing it by identity would rebind the callback every
    /// restyle and mark the node dirty each time — which is the cost this whole
    /// guard exists to avoid.
    private @Nullable TextFlow measuredFlow;

    /// Whether this is a measured leaf.
    ///
    /// Fixed at construction because a node's kind cannot change: Yoga refuses
    /// children on a node with a measure function, so a text box that becomes a
    /// container is a different node rather than a reconfigured one.
    private final boolean leaf;

    RenderObject(YogaConfig config, boolean measuredLeaf) {
        this.node = YogaNode.create(config);
        this.leaf = measuredLeaf;
    }

    /// The Yoga node this object owns. Package-private: its lifetime is this
    /// object's, and handing it out would be handing out something closeable.
    YogaNode node() {
        return node;
    }

    List<RenderObject> children() {
        return children;
    }

    /// The box last applied — what the painter draws.
    @Nullable
    Box box() {
        return applied;
    }

    /// Whether this object can take `box` without being rebuilt.
    ///
    /// Two things make it impossible. A **measured leaf and a container are
    /// different kinds of node**: Yoga asks a measure function for the size and
    /// never lays children out, so the two are not interchangeable and the
    /// wrapper refuses to mix them. And a box with a **different owner** is a
    /// different element's — reusing the node would still draw correctly, because
    /// every style is re-applied, but it would inherit a layout cache and a
    /// measure callback belonging to something else and quietly lose the benefit
    /// of both.
    boolean accepts(Box box) {
        return leaf == (box.text() != null) && applied != null && applied.owner() == box.owner();
    }

    /// Where this node was laid out, in the toolkit's own geometry.
    ///
    /// The conversion lives here rather than at each caller because this class is
    /// already the one place a `YogaNode` is touched — see [Yoga].
    ///
    /// **Read from Yoga once per frame**, by [#settle], and handed back from
    /// there afterwards. Four native downcalls and two allocations is nothing for
    /// one node and is not nothing four thousand times over — and a frame asks
    /// four separate walks where every node is: the paint pass, the damage pass,
    /// the hit-test snapshot and the ink pass below. Reading Yoga in each of them
    /// was four times the cost for four identical answers, since nothing between
    /// [RenderTree#update] and the next one can move a node ([ADR-0313]).
    LogicalRect layout() {
        return placed;
    }

    /// Where Yoga put this node, as of the last [#settle].
    ///
    /// Zero until the first one, which is what an unlaid-out node has always
    /// reported — [io.github.digitalsmile.goldberry.natives.yoga.ComputedLayout]
    /// says so in as many words.
    private LogicalRect placed = LogicalRect.of(0, 0, 0, 0);

    /// What this subtree draws, in **this node's own coordinates** — its border
    /// box's top-left corner is the origin, and its own transform is not applied.
    ///
    /// Read by the painter to skip a subtree that cannot put a pixel inside the
    /// clip in force ([ADR-0313]). Recomputed once per layout pass by [#settle],
    /// because it is a function of where Yoga put everything and of nothing
    /// else.
    Ink ink() {
        return ink;
    }

    /// Reads where Yoga put this subtree and recomputes its [#ink], reporting
    /// the answer in the **parent's** coordinates.
    ///
    /// Bottom-up in one pass, because a parent's answer is its children's — and
    /// once per frame rather than once per paint, so a tree painted twice (a
    /// damage pass and a full one) measures nothing twice.
    ///
    /// The return value is what it is so that `layout()` is read **once per
    /// node**. That is four native downcalls and two allocations a time, and a
    /// parent asking its children where they are would double every one of them
    /// across a tree that can be five thousand nodes deep in this application —
    /// which is the whole measurable cost of this pass ([ADR-0313]).
    ///
    /// **The child's transform is applied here and its parent's is not.** A
    /// transform is written in the box's own coordinates, so `compose` anchors it
    /// at the child's position *within its parent* rather than at its absolute
    /// one — which differs from the painter's anchor by exactly the translation
    /// the painter applies afterwards, and therefore composes to the same matrix.
    Ink settle() {
        var before = placed;
        placed = Yoga.rect(node.layout());
        var layout = placed;
        // **Nothing in this subtree can have moved**, so the ink measured for it
        // last frame is still the ink, and the `placed` rectangles under it are
        // still where Yoga put them -- they are relative to *this* node, and this
        // node has not changed shape.
        //
        // The two halves are both needed and neither implies the other.
        // `changed` is the box diff over the whole subtree, so it catches a
        // decoration that grew a ring, a text node rebound to a longer paragraph,
        // a child added. `placed` catches everything Yoga decided: a flex sibling
        // that grew and squeezed this node is a layout this node's own boxes know
        // nothing about. With the same styles throughout and the same rectangle
        // to lay them out in, Yoga is a function and its answer is the one
        // already read ([ADR-0313]).
        //
        // This is what makes **scrolling** cost nothing here: a viewport moves by
        // a `transform` on one box, so exactly one node is `changed` and the
        // thousand under it are skipped at the first one whose rectangle held.
        if (!changed && layout.equals(before)) {
            return inParentSpace(ink, layout);
        }
        var union = applied == null ? Ink.NONE : BoxInk.of(applied, layout.width(), layout.height());
        for (var child : children) {
            union = union.union(child.settle());
        }
        ink = union;
        return inParentSpace(union, layout);
    }

    /// `union`, measured in this node's coordinates, expressed in its parent's.
    private Ink inParentSpace(Ink union, LogicalRect layout) {
        var shifted = union.shiftedBy(layout.left(), layout.top());
        if (applied == null) {
            return shifted;
        }
        return shifted.mappedBy(RenderTree.compose(
                Affine.IDENTITY, applied.transform(), layout.left(), layout.top(), layout.width(), layout.height()));
    }

    /// Puts `box` on the Yoga node, touching only what changed.
    ///
    /// Every branch here is a guard, and the guards are the point. Yoga marks a
    /// node dirty on any `YGNodeStyleSet*` call regardless of whether the value
    /// differs, so an unguarded version of this method would dirty every node
    /// every frame and Yoga's layout cache would never hit once — which is the
    /// same amount of work as throwing the tree away, with the memory management
    /// of keeping it.
    void apply(Box box, Insets inset) {
        var previous = applied;
        applied = box;

        if (previous == null || previous.direction() != box.direction()) {
            node.setFlexDirection(Yoga.direction(box.direction()));
        }
        if (previous == null || previous.justifyContent() != box.justifyContent()) {
            node.setJustifyContent(Yoga.justify(box.justifyContent()));
        }
        if (previous == null || previous.alignItems() != box.alignItems()) {
            node.setAlignItems(Yoga.align(box.alignItems()));
        }
        if (previous == null || previous.alignSelf() != box.alignSelf()) {
            node.setAlignSelf(Yoga.align(box.alignSelf()));
        }
        // Read by Yoga only when the container wraps, and set unconditionally
        // anyway: "does this box wrap" is a question about the box next frame as
        // well as this one, and a value the engine ignores costs one foreign
        // call on the frame it changes (ADR-0374).
        if (previous == null || previous.alignContent() != box.alignContent()) {
            node.setAlignContent(Yoga.align(box.alignContent()));
        }
        if (previous == null || previous.wrap() != box.wrap()) {
            node.setFlexWrap(Yoga.wrap(box.wrap()));
        }
        if (previous == null || !previous.width().equals(box.width())) {
            node.setWidth(Yoga.length(box.width()));
        }
        if (previous == null || !previous.height().equals(box.height())) {
            node.setHeight(Yoga.length(box.height()));
        }
        // The four limits, together, because they arrive together. Skipped
        // wholesale when neither frame had any -- which is nearly every node --
        // so a box that never mentions a minimum costs one comparison rather
        // than four foreign calls (ADR-0181).
        var limits = box.limits();
        if (previous == null ? !limits.isNone() : !previous.limits().equals(limits)) {
            node.setMinWidth(Yoga.length(limits.minWidth()));
            node.setMaxWidth(Yoga.length(limits.maxWidth()));
            node.setMinHeight(Yoga.length(limits.minHeight()));
            node.setMaxHeight(Yoga.length(limits.maxHeight()));
        }
        // Before padding, because that is the order the box model reads in and
        // the order a reader looking for one of the two expects to find it.
        // Per edge for padding's reason, and `Length.AUTO` on an edge reaches
        // Yoga's own `YGNodeStyleSetMarginAuto` — which is what `margin: 0 auto`
        // resolves to and what absorbs the free space beside the node
        // (ADR-0313).
        var margin = box.margin();
        // `Insets.ZERO` on a first apply is skipped wholesale, which `limits`
        // does for the same reason (ADR-0181): Yoga's own default margin is zero,
        // so a box that never mentions one costs a single comparison rather than
        // four foreign calls on the frame it first appears. Margin is rarer than
        // padding in this catalog -- nothing in it wore one until today -- so
        // that is nearly every node.
        if (previous == null ? !margin.equals(Insets.ZERO) : !previous.margin().equals(margin)) {
            node.setMargin(Edge.TOP, Yoga.length(margin.top()));
            node.setMargin(Edge.RIGHT, Yoga.length(margin.right()));
            node.setMargin(Edge.BOTTOM, Yoga.length(margin.bottom()));
            node.setMargin(Edge.LEFT, Yoga.length(margin.left()));
        }
        var padding = box.padding();
        if (previous == null || !previous.padding().equals(padding)) {
            // Per edge rather than Edge.ALL, because `padding: 0 12px` is what a
            // control wants and Yoga resolves the more specific edge over ALL
            // only if both are set.
            node.setPadding(Edge.TOP, Yoga.length(padding.top()));
            node.setPadding(Edge.RIGHT, Yoga.length(padding.right()));
            node.setPadding(Edge.BOTTOM, Yoga.length(padding.bottom()));
            node.setPadding(Edge.LEFT, Yoga.length(padding.left()));
        }
        if (previous == null || !previous.gap().equals(box.gap())) {
            node.setGap(Gutter.ALL, Yoga.length(box.gap()));
        }
        if (previous == null || previous.flexGrow() != box.flexGrow()) {
            node.setFlexGrow((float) box.flexGrow());
        }
        if (previous == null || previous.flexShrink() != box.flexShrink()) {
            node.setFlexShrink((float) box.flexShrink());
        }
        if (previous == null || !previous.flexBasis().equals(box.flexBasis())) {
            node.setFlexBasis(Yoga.length(box.flexBasis()));
        }
        if (previous == null || previous.position() != box.position()) {
            node.setPositionType(Yoga.position(box.position()));
        }
        // Yoga's half of `overflow`: a node that is not VISIBLE does not grow to
        // contain a child that overruns it. Without this a viewport would simply
        // stretch to its content and there would be nothing to scroll — the
        // clip in the painter hides the overflow, and this is what *creates* it
        // (ADR-0114).
        if (previous == null || previous.overflow() != box.overflow()) {
            node.setOverflow(Yoga.overflow(box.overflow()));
        }
        // Against `appliedInset` rather than against `previous.inset()`, because
        // the value on the node is the box's inset shifted by the containing
        // block's padding and the box does not carry that ([ContainingBlock]).
        // Guarding on the declared inset would leave a child where it was when
        // only its parent's padding had changed.
        if (!inset.equals(appliedInset)) {
            appliedInset = inset;
            // Per edge, like padding, and for the same reason: Yoga resolves the
            // more specific edge over `Edge.ALL` only when both are set, so a
            // node that named one edge and left the rest undefined would keep
            // whichever ALL had been given.
            node.setPosition(Edge.TOP, Yoga.length(inset.top()));
            node.setPosition(Edge.RIGHT, Yoga.length(inset.right()));
            node.setPosition(Edge.BOTTOM, Yoga.length(inset.bottom()));
            node.setPosition(Edge.LEFT, Yoga.length(inset.left()));
        }

        applyMeasure(box);
    }

    /// Attaches, keeps or replaces the measure function.
    ///
    /// The `keep` case is the one this class exists for. A paragraph that is the
    /// same instance as last frame's measures the same way, so the callback
    /// already bound is correct and rebinding it would cost an `Arena` and a
    /// native stub to arrive at the same behaviour.
    private void applyMeasure(Box box) {
        var text = box.text();
        if (text == null) {
            if (measured != null) {
                node.setMeasureFunction(null);
                measured = null;
            }
            return;
        }
        var paragraph = text.paragraph();
        var flow = text.flow();
        if (measured == paragraph && flow.equals(measuredFlow)) {
            return;
        }
        // A different paragraph: different text, a different font, or the cache
        // evicted the old one. **Or the same paragraph measured by a different
        // rule** -- `white-space` is what decides whether the callback takes the
        // width Yoga offers or reports its own, so a restyle that changes it has
        // to rebind even though the text did not change (ADR-0255).
        node.setMeasureFunction(Yoga.measure(paragraph.measureFunction(flow)));
        // And then say so, because **Yoga does not dirty a node when its measure
        // function is replaced**. It dirties on a style change, and the text is
        // not a style — from Yoga's point of view nothing about this node
        // changed, so it would reuse the height it cached for the *previous*
        // paragraph. That is a wrong layout with no error: a one-line height
        // reported for text that wraps to six.
        //
        // This never came up while the tree was thrown away every frame, because
        // a node built this frame has no cached measurement to reuse. It is the
        // first bug that only exists because the tree is kept, and
        // [YogaNode#markDirty()] documents itself as the call for exactly this.
        node.markDirty();
        measured = paragraph;
        measuredFlow = flow;
    }

    /// Replaces this object's children with `next`, reusing what it can.
    ///
    /// Matched by position and then checked with [#accepts], which is enough
    /// because the **element tree has already done the keyed diff**
    /// (ADR-0052):
    /// by the time a box tree exists, the order is stable and a node that moved
    /// moved for a reason. A mismatch costs a rebuilt subtree, never a wrong
    /// result.
    ///
    /// @return whether the child list changed, so the caller can avoid touching
    ///         Yoga's child list — which dirties the node — when it did not
    boolean reconcileChildren(List<Box> next, Insets blockPadding, YogaConfig config) {
        var changed = false;

        for (var i = 0; i < next.size(); i++) {
            var box = next.get(i);
            if (i < children.size()) {
                var existing = children.get(i);
                if (existing.accepts(box)) {
                    // OR-ed in, not discarded: a promoted ancestor's raster is
                    // only reusable if *nothing* under it changed, and a child
                    // three levels down is under it.
                    changed |= existing.update(box, blockPadding, config);
                    continue;
                }
                // Not interchangeable. Detach and close it; the replacement is
                // built below.
                node.removeChild(existing.node);
                existing.close();
                children.remove(i);
                changed = true;
            }
            var built = new RenderObject(config, box.text() != null);
            built.update(box, blockPadding, config);
            children.add(i, built);
            node.insertChild(built.node, i);
            changed = true;
        }

        // Anything the new list did not reach is gone from the tree.
        while (children.size() > next.size()) {
            var extra = children.removeLast();
            node.removeChild(extra.node);
            extra.close();
            changed = true;
        }
        return changed;
    }

    // --- layers ------------------------------------------------------------

    /// The raster of this node's subtree, kept between frames while it is valid.
    ///
    /// Null unless this node is promoted, which almost none are.
    private @Nullable Layer layer;

    /// The bounds the current [#layer] was allocated for.
    private RenderTree.@Nullable Bounds layerBounds;

    /// Whether anything in this subtree changed on the most recent update.
    ///
    /// What decides whether a cached raster can be blitted again. It is the
    /// guards in [#apply] paying a second dividend: they already know whether a
    /// value differed, and this is that answer propagated upward.
    private boolean changed = true;

    /// Whether **this node's own box** changed, ignoring its children.
    ///
    /// [#changed] is the subtree's answer and is what a promoted layer reads;
    /// this is the node's own, and it is what damage tracking reads. The two have
    /// to be separate: a parent whose child moved is "changed" for the layer's
    /// purposes and has not itself moved a pixel, so damaging its whole rectangle
    /// would report the entire window dirty every time anything in it did
    /// anything.
    private boolean selfChanged = true;

    /// Whether this node's **raster** needs redrawing, ignoring the `opacity` and
    /// `transform` that are applied to its blit rather than inside its layer.
    ///
    /// Includes its descendants: their own opacity and transform *are* baked in.
    private boolean contentChanged = true;

    /// Where this node was drawn last frame, in physical pixels, or null the
    /// first time.
    ///
    /// Kept so that a node which *moved* damages both the place it left and the
    /// place it arrived. Damaging only the new one leaves the old drawing on
    /// screen, which is the classic partial-repaint artefact.
    private @Nullable DamageRect lastRect;

    /// Whether this node's raster needs redrawing — see [#contentChanged].
    boolean hasContentChanged() {
        return contentChanged;
    }

    boolean hasSelfChanged() {
        return selfChanged;
    }

    @Nullable
    DamageRect lastRect() {
        return lastRect;
    }

    void rememberRect(DamageRect rect) {
        this.lastRect = rect;
    }

    /// Whether this node composites through a layer of its own.
    ///
    /// **The policy, stated in one place.** A node is promoted when it is
    /// translucent *and has children*, because that is exactly where CSS's group
    /// opacity and Goldberry's per-box alpha multiply give different answers —
    /// faded separately, a lower child shows through an upper one
    /// (ADR-0064
    /// stated that difference and left it open).
    ///
    /// A translucent **leaf** is deliberately not promoted. Its own background,
    /// border and text can overlap each other, so a layer would differ there too
    /// — by a fraction of a level along an antialiased edge — and paying an
    /// allocation and a blit for every faded label to fix that would be a poor
    /// trade. `:disabled` at 45% (§2.1) is the case that matters and it is a
    /// control with children.
    ///
    /// Fully transparent is not promoted either: there is nothing to composite,
    /// and the ordinary path already draws nothing.
    boolean isPromoted() {
        return applied != null && applied.opacity() < 1 && applied.opacity() > 0 && !children.isEmpty();
    }

    /// The layer for `bounds`, reused if the one held still fits and is good.
    ///
    /// Reallocated when the size changes, which is a resize or a layout that
    /// moved something — and marked stale when anything in the subtree changed,
    /// which is what makes an animating node a blit rather than a repaint.
    Layer layerFor(RenderTree.Bounds bounds, DisplayScale scale) {
        var size = new PhysicalSize(Math.max(1, (int) Math.ceil(bounds.width() * scale.factor())), Math.max(1, (int)
                Math.ceil(bounds.height() * scale.factor())));

        if (layer == null || layer.isClosed() || !layer.size().equals(size)) {
            if (layer != null) {
                layer.close();
            }
            layer = Layer.of(size);
        }
        // The raster is only reusable if the subtree drew the same thing *and*
        // drew it in the same place within the layer. A node that moved keeps its
        // raster and is blitted somewhere else; one whose bounds changed shape
        // has to be drawn again.
        // `contentChanged`, not `changed`: this node's own opacity and transform
        // are applied to the composite, so a group that is only fading or moving
        // keeps the raster it already has. That is the whole of §1.7's layer
        // promotion, and reading `changed` here meant an opacity transition
        // invalidated the raster on every frame of itself.
        if (contentChanged || !bounds.equals(layerBounds)) {
            layer.valid(false);
        }
        layerBounds = bounds;
        return layer;
    }

    /// Whether this subtree changed on the last update — diagnostics, and what a
    /// test asserts when it wants to know a layer was reused.
    boolean hasChanged() {
        return changed;
    }

    /// This object brought up to date with `box`, children and all.
    ///
    /// @return whether anything in this subtree changed, which is what a promoted
    ///         ancestor needs to know to decide its raster is still good
    boolean update(Box box, Insets blockPadding, YogaConfig config) {
        // Compared before `apply` overwrites it. Everything that affects what is
        // drawn, not only what Yoga reads: a background that changed needs a
        // repaint even though the layout is untouched.
        //
        // Three questions, and one flag used to answer all of them, which is why
        // a fading group re-rasterized itself every frame:
        //
        //   1. Does the *screen* look different? -> `selfChanged`. Damage.
        //   2. Does an *ancestor's* raster need redrawing? -> `changed`. An
        //      ancestor bakes in this node's finished blit, alpha and matrix
        //      included, so both count.
        //   3. Does *this node's own* raster need redrawing? -> `contentChanged`.
        //      Its `opacity` and `transform` are applied to the blit, not inside
        //      the layer, so neither does.
        //
        // (3) is the one §1.7 promotes a node for. Answering it with (1) meant an
        // opacity transition invalidated the very raster it existed to reuse.
        var previous = applied;
        // The inset that will reach Yoga, which is the box's own only when
        // nothing shifts it ([ContainingBlock]). Resolved here rather than inside
        // `apply` because it needs the parent's padding, which `apply` has no
        // reason to take.
        //
        // Nothing below counts it as a change, and that is checked rather than
        // assumed. The only thing that shifts a child without touching its own
        // box is its **parent's** padding — which `sameAppearance` compares, so
        // the parent is `selfChanged` and its rectangle is damaged; and a child
        // that moved out from under it is caught by `collectDamage` comparing
        // where it was against where it is, which is a comparison of results
        // rather than of styles and does not care why it moved.
        var inset = ContainingBlock.insetFor(box.position(), box.inset(), blockPadding);
        selfChanged = previous == null || !sameAppearance(previous, box);
        contentChanged = previous == null || !sameRaster(previous, box);
        changed = selfChanged;
        apply(box, inset);
        if (!box.children().isEmpty() || !children.isEmpty()) {
            // This box's own padding is the containing block for every absolutely
            // positioned child of it.
            var childrenChanged = reconcileChildren(box.children(), box.padding(), config);
            changed |= childrenChanged;
            // A descendant's own opacity and transform *are* baked into this
            // node's raster, so a child changing anything invalidates it.
            contentChanged |= childrenChanged;
        }
        return changed;
    }

    /// Whether two boxes would draw the same thing **into a layer** — that is,
    /// ignoring the two properties applied to the composite rather than to the
    /// raster.
    ///
    /// Only meaningful for a promoted node, and only used there.
    private static boolean sameRaster(Box a, Box b) {
        return (a.opacity() == b.opacity() && a.transform().equals(b.transform()))
                ? sameAppearance(a, b)
                // Compare everything else by putting this box's blit properties
                // onto the other one: cheaper to reason about than a second
                // fifteen-line comparison that has to be kept in step with the
                // first.
                : sameAppearance(a, b.opacity(a.opacity()).transform(a.transform()));
    }

    /// Whether two boxes would draw the same thing, **not counting children**.
    ///
    /// `Box.equals` would answer this and would walk the whole subtree to do it,
    /// once per node — which is quadratic in the depth of the tree and would cost
    /// more than the repaint it is trying to avoid. The children are compared by
    /// the recursion instead, each one exactly once.
    ///
    /// Most of these comparisons are reference checks in practice: a cached
    /// `ComputedStyle` hands `Box.style` the same `Decoration`, `Insets` and
    /// `Transform` instances every frame
    /// (ADR-0070).
    private static boolean sameAppearance(Box a, Box b) {
        return a.background() == b.background()
                && a.opacity() == b.opacity()
                && a.decoration().equals(b.decoration())
                && a.transform().equals(b.transform())
                && a.direction() == b.direction()
                && a.justifyContent() == b.justifyContent()
                && a.alignItems() == b.alignItems()
                // The four Yoga reads and the one paint order that were missing,
                // and each of them moves ink. `flex-wrap` is the one that found
                // it: a row that starts wrapping puts its third child on a second
                // line without changing a single field of that child's box, so a
                // subtree "unchanged" by this comparison had every rectangle in
                // it move ([ADR-0313]). `overflow` and `elevated` change what is
                // drawn rather than where -- a box that starts clipping, and one
                // that starts painting over its siblings.
                && a.alignSelf() == b.alignSelf()
                && a.alignContent() == b.alignContent()
                && a.wrap() == b.wrap()
                && a.limits().equals(b.limits())
                && a.overflow() == b.overflow()
                && a.elevated() == b.elevated()
                && a.width().equals(b.width())
                && a.height().equals(b.height())
                && a.margin().equals(b.margin())
                && a.padding().equals(b.padding())
                && a.gap().equals(b.gap())
                && a.flexGrow() == b.flexGrow()
                && a.flexShrink() == b.flexShrink()
                && a.flexBasis().equals(b.flexBasis())
                && a.position() == b.position()
                && a.inset().equals(b.inset())
                && Objects.equals(a.text(), b.text())
                && Objects.equals(a.icon(), b.icon())
                && Objects.equals(a.mark(), b.mark())
                // The painter, which was missing and is the one piece of a box
                // whose *contents* this class cannot see. A `canvas`, a
                // `sparkline`, a chart surface and a colour plane all draw
                // through one, and a box whose painter changed was called
                // unchanged: the node kept last frame's pixels and, in a promoted
                // layer, was not even re-rastered (the 2026-09-18 review, §7).
                //
                // Compared by identity, because a `Painter` is a lambda and there
                // is nothing else to compare. The consequence is worth stating:
                // a widget that mints a fresh painter on every build is damaged
                // on every build. That is the safe answer -- a new lambda may
                // close over new data, and this class has no way to know it does
                // not -- and a widget that wants otherwise holds its painter
                // rather than writing it inline.
                && Objects.equals(a.painting(), b.painting());
    }

    /// Frees this node and everything under it.
    ///
    /// [YogaNode#close()] already frees a subtree child-first and closes each
    /// measure callback on the way, so detaching every descendant here would be
    /// undoing the tree in order to let Yoga undo it again. What is left to do is
    /// drop this object's own references, so a caller holding one cannot reach a
    /// node that has been freed.
    @Override
    public void close() {
        if (layer != null) {
            layer.close();
            layer = null;
        }
        if (node.isClosed()) {
            forget();
            return;
        }
        if (node.parent() != null) {
            // A subtree being replaced rather than a whole tree being torn down:
            // `YogaNode.close` refuses while a parent owns it, and rightly — it
            // would leave Yoga holding a dangling child.
            node.parent().removeChild(node);
        }
        node.close();
        forget();
    }

    /// Drops the Java-side child references whose nodes Yoga has already freed.
    private void forget() {
        for (var child : children) {
            child.forget();
        }
        children.clear();
    }

    @Override
    public String toString() {
        return "RenderObject[" + (leaf ? "text" : children.size() + " children") + "]";
    }
}
