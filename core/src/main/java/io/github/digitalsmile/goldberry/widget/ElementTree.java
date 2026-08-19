package io.github.digitalsmile.goldberry.widget;

import io.github.digitalsmile.goldberry.natives.log.Logs;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;

/// The element tree, and the rebuild schedule that drives it.
///
/// Owns the root element and the set of elements waiting to be rebuilt.
/// [#flush()] is the whole of the rebuild half of [ADR-0052]: `setState` marks an
/// element dirty and returns, and exactly one flush per frame turns however many
/// marks arrived into at most one build each.
///
/// Confined to the UI thread, like everything above the backend SPI.
public final class ElementTree {

    private static final Logger LOG = Logs.of(ElementTree.class);

    /// A build that schedules a build that schedules a build. Ten passes is far
    /// more than any legitimate settle and small enough to stop quickly.
    private static final int MAX_PASSES = 10;

    private final Element root;
    private final Set<Element> dirty = new LinkedHashSet<>();

    /// The window every element in this tree is being built into, or null.
    ///
    /// Held on the tree rather than on each element because it is the same
    /// answer for all of them, and because a popup's tree has a different one
    /// from the window that opened it (ADR-0140).
    private final io.github.digitalsmile.goldberry.Host host;

    /// Builds a tree from a root widget, with no window behind it.
    ///
    /// What a widget test and a golden image build. A control that wants a popup
    /// finds [BuildContext#host()] empty and stays closed.
    public ElementTree(Widget root) {
        this(root, null);
    }

    /// Builds a tree from a root widget, into `host`.
    ///
    /// The first build happens here, so the tree is usable the moment it exists.
    ///
    /// @param host the window this tree is drawn in, or null for a tree with no
    ///             window — a measurement, a test, a still picture
    public ElementTree(Widget root, io.github.digitalsmile.goldberry.Host host) {
        Objects.requireNonNull(root, "root");
        this.host = host;
        this.root = new Element(this, null, root);
        this.root.rebuild();
        // Deliberately NOT clearing `dirty` here. A setState during the very
        // first build is legal, and discarding it -- which an earlier version of
        // this constructor did -- lost the change silently and produced a tree
        // one build behind its own state.
    }

    public Element root() {
        return root;
    }

    /// The resolver this tree is currently being drawn with, or null before the
    /// first render.
    ///
    /// Held here because the question it answers is the tree's rather than any
    /// node's — "which stylesheets are in force" — and because the node that
    /// needs to ask is often one that has never resolved a style of its own: a
    /// composition node in the hover chain has no cache and no resolver, and
    /// treating that as "unknown, be conservative" re-resolved the whole tree on
    /// every click (ADR-0149).
    private io.github.digitalsmile.goldberry.css.StyleResolver styleResolver;

    /// Told by the renderer at the start of every frame.
    public void styleResolver(io.github.digitalsmile.goldberry.css.StyleResolver resolver) {
        this.styleResolver = resolver;
    }

    io.github.digitalsmile.goldberry.css.StyleResolver styleResolver() {
        return styleResolver;
    }

    /// The window this tree is built into, or empty — [BuildContext#host()]'s
    /// answer, held once for the whole tree.
    java.util.Optional<io.github.digitalsmile.goldberry.Host> host() {
        return java.util.Optional.ofNullable(host);
    }

    /// Whether anything is waiting to be rebuilt.
    ///
    /// What a frame loop asks before deciding it has nothing to do.
    public boolean needsBuild() {
        return !dirty.isEmpty();
    }

    /// Rebuilds every dirty element.
    ///
    /// Shallowest first. A parent's rebuild can replace a child's whole subtree,
    /// and rebuilding the child first would be work thrown away — or worse, a
    /// build on an element that is about to be unmounted.
    ///
    /// Repeats while builds mark more elements dirty, because a `setState` during
    /// a build is legal and has to settle before the frame is painted. It stops
    /// after [#MAX_PASSES] and says so rather than spinning: a build that always
    /// dirties itself is an application bug, and a frozen window with no message
    /// is a bad way to report it.
    ///
    /// @return how many elements were rebuilt
    public int flush() {
        var built = 0;
        for (var pass = 0; pass < MAX_PASSES; pass++) {
            if (dirty.isEmpty()) {
                return built;
            }
            var batch = new ArrayList<>(dirty);
            dirty.clear();
            batch.sort(Comparator.comparingInt(Element::depth));

            for (var element : batch) {
                // A shallower rebuild earlier in this pass may have replaced or
                // unmounted this one.
                if (element.isMounted() && element.needsBuild()) {
                    element.rebuild();
                    built++;
                }
            }
        }
        if (!dirty.isEmpty()) {
            LOG.warn("giving up after {} rebuild passes with {} element(s) still dirty:"
                            + " a build is calling setState on every pass",
                    MAX_PASSES, dirty.size());
            dirty.clear();
        }
        return built;
    }

    /// Tears the tree down, disposing every state.
    public void unmount() {
        root.unmount();
        dirty.clear();
    }

    void markDirty(Element element) {
        // The listener fires on the transition into dirty, not on every mark: a
        // handler calling `setState` ten times asks for one frame, which is the
        // same coalescing `flush` does one level down (ADR-0052).
        var wasClean = dirty.isEmpty();
        dirty.add(element);
        if (wasClean && onDirty != null) {
            onDirty.run();
        }
    }

    /// Told when this tree goes from clean to dirty — see [#onDirty].
    private Runnable onDirty;

    /// Asks `listener` for a frame whenever a `setState` lands on a clean tree.
    ///
    /// **Without this a `setState` reaches nothing.** The frame loop is idle when
    /// nothing is animating (§1.7), and input handlers do not paint — so a widget
    /// that changed its own state sat there until some *unrelated* event caused a
    /// frame, and then showed the change one interaction late. A scroll view was
    /// where it was noticed: the first turn of the wheel appeared to do nothing
    /// and the second appeared to do one turn's worth
    /// ([ADR-0122](../../../../../../book/src/adr/0122-a-setstate-asks-for-a-frame.md)).
    ///
    /// The window sets this. It is a single listener rather than a list because
    /// there is exactly one thing that can paint a tree, and a second one would
    /// mean two windows drawing one element tree.
    public void onDirty(Runnable listener) {
        this.onDirty = listener;
    }

    void forget(Element element) {
        dirty.remove(element);
    }
}
