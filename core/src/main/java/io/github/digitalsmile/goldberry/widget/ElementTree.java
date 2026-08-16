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

    /// Builds a tree from a root widget.
    ///
    /// The first build happens here, so the tree is usable the moment it exists.
    public ElementTree(Widget root) {
        Objects.requireNonNull(root, "root");
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
        dirty.add(element);
    }

    void forget(Element element) {
        dirty.remove(element);
    }
}
