package io.github.digitalsmile.goldberry.layout;

import io.github.digitalsmile.goldberry.Layer;
import io.github.digitalsmile.goldberry.backend.DamageRect;
import io.github.digitalsmile.goldberry.backend.DisplayScale;
import io.github.digitalsmile.goldberry.backend.PhysicalSize;
import io.github.digitalsmile.goldberry.natives.yoga.Edge;
import io.github.digitalsmile.goldberry.natives.yoga.Gutter;
import io.github.digitalsmile.goldberry.natives.yoga.YogaConfig;
import io.github.digitalsmile.goldberry.natives.yoga.YogaNode;
import java.util.ArrayList;
import java.util.List;

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
///   ([ADR-0037](../../../../../../book/src/adr/0037-what-the-text-path-costs.md)).
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
/// ([ADR-0053](../../../../../../book/src/adr/0053-the-render-tree-is-a-box-tree-for-now.md)),
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
    private Box applied;

    /// The paragraph the attached measure callback measures, by identity.
    ///
    /// Identity rather than equality, deliberately: the callback closes over one
    /// `Paragraph`, and an equal-but-distinct one would wrap against a different
    /// memo. `ParagraphCache` is what makes this stable frame to frame, which is
    /// why [io.github.digitalsmile.goldberry.widget.Paints.Context] shapes through
    /// it rather than letting widgets call `Paragraph.of`.
    private Object measured;

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

    /// Puts `box` on the Yoga node, touching only what changed.
    ///
    /// Every branch here is a guard, and the guards are the point. Yoga marks a
    /// node dirty on any `YGNodeStyleSet*` call regardless of whether the value
    /// differs, so an unguarded version of this method would dirty every node
    /// every frame and Yoga's layout cache would never hit once — which is the
    /// same amount of work as throwing the tree away, with the memory management
    /// of keeping it.
    void apply(Box box) {
        var previous = applied;
        applied = box;

        if (previous == null || previous.direction() != box.direction()) {
            node.setFlexDirection(box.direction());
        }
        if (previous == null || previous.justifyContent() != box.justifyContent()) {
            node.setJustifyContent(box.justifyContent());
        }
        if (previous == null || previous.alignItems() != box.alignItems()) {
            node.setAlignItems(box.alignItems());
        }
        if (previous == null || !previous.width().equals(box.width())) {
            node.setWidth(box.width());
        }
        if (previous == null || !previous.height().equals(box.height())) {
            node.setHeight(box.height());
        }
        var padding = box.padding();
        if (previous == null || !previous.padding().equals(padding)) {
            // Per edge rather than Edge.ALL, because `padding: 0 12px` is what a
            // control wants and Yoga resolves the more specific edge over ALL
            // only if both are set.
            node.setPadding(Edge.TOP, padding.top());
            node.setPadding(Edge.RIGHT, padding.right());
            node.setPadding(Edge.BOTTOM, padding.bottom());
            node.setPadding(Edge.LEFT, padding.left());
        }
        if (previous == null || !previous.gap().equals(box.gap())) {
            node.setGap(Gutter.ALL, box.gap());
        }
        if (previous == null || previous.flexGrow() != box.flexGrow()) {
            node.setFlexGrow((float) box.flexGrow());
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
        if (measured == paragraph) {
            return;
        }
        // A different paragraph: different text, a different font, or the cache
        // evicted the old one.
        node.setMeasureFunction(paragraph.measureFunction());
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
    }

    /// Replaces this object's children with `next`, reusing what it can.
    ///
    /// Matched by position and then checked with [#accepts], which is enough
    /// because the **element tree has already done the keyed diff**
    /// ([ADR-0052](../../../../../../book/src/adr/0052-state-lives-on-the-element-and-rebuilds-are-deferred.md)):
    /// by the time a box tree exists, the order is stable and a node that moved
    /// moved for a reason. A mismatch costs a rebuilt subtree, never a wrong
    /// result.
    ///
    /// @return whether the child list changed, so the caller can avoid touching
    ///         Yoga's child list — which dirties the node — when it did not
    boolean reconcileChildren(List<Box> next, YogaConfig config) {
        var changed = false;

        for (var i = 0; i < next.size(); i++) {
            var box = next.get(i);
            if (i < children.size()) {
                var existing = children.get(i);
                if (existing.accepts(box)) {
                    // OR-ed in, not discarded: a promoted ancestor's raster is
                    // only reusable if *nothing* under it changed, and a child
                    // three levels down is under it.
                    changed |= existing.update(box, config);
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
            built.update(box, config);
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
    private Layer layer;

    /// The bounds the current [#layer] was allocated for.
    private RenderTree.Bounds layerBounds;

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
    private DamageRect lastRect;

    /// Whether this node's raster needs redrawing — see [#contentChanged].
    boolean hasContentChanged() {
        return contentChanged;
    }

    boolean hasSelfChanged() {
        return selfChanged;
    }

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
    /// ([ADR-0064](../../../../../../book/src/adr/0064-a-rounded-rectangle-is-four-cubics.md)
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
        return applied != null
                && applied.opacity() < 1
                && applied.opacity() > 0
                && !children.isEmpty();
    }

    /// The layer for `bounds`, reused if the one held still fits and is good.
    ///
    /// Reallocated when the size changes, which is a resize or a layout that
    /// moved something — and marked stale when anything in the subtree changed,
    /// which is what makes an animating node a blit rather than a repaint.
    Layer layerFor(RenderTree.Bounds bounds, DisplayScale scale) {
        var size = new PhysicalSize(
                Math.max(1, (int) Math.ceil(bounds.width() * scale.factor())),
                Math.max(1, (int) Math.ceil(bounds.height() * scale.factor())));

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
    boolean update(Box box, YogaConfig config) {
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
        selfChanged = previous == null || !sameAppearance(previous, box);
        contentChanged = previous == null || !sameRaster(previous, box);
        changed = selfChanged;
        apply(box);
        if (!box.children().isEmpty() || !children.isEmpty()) {
            var childrenChanged = reconcileChildren(box.children(), config);
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
        return a.opacity() == b.opacity() && a.transform().equals(b.transform())
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
    /// ([ADR-0070](../../../../../../book/src/adr/0070-the-cascade-resolves-invalidated-nodes.md)).
    private static boolean sameAppearance(Box a, Box b) {
        return a.background() == b.background()
                && a.opacity() == b.opacity()
                && a.decoration().equals(b.decoration())
                && a.transform().equals(b.transform())
                && a.direction() == b.direction()
                && a.justifyContent() == b.justifyContent()
                && a.alignItems() == b.alignItems()
                && a.width().equals(b.width())
                && a.height().equals(b.height())
                && a.padding().equals(b.padding())
                && a.gap().equals(b.gap())
                && a.flexGrow() == b.flexGrow()
                && java.util.Objects.equals(a.text(), b.text())
                && java.util.Objects.equals(a.icon(), b.icon())
                && java.util.Objects.equals(a.mark(), b.mark());
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
