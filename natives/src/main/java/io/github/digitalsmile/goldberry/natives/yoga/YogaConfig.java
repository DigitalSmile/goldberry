package io.github.digitalsmile.goldberry.natives.yoga;

import java.lang.foreign.MemorySegment;

/// Settings shared by a tree of [YogaNode]s.
///
/// Two settings matter to Goldberry, and both are wrong by default for a
/// toolkit that presents itself as CSS.
///
/// **The point scale factor** is how Yoga snaps computed positions to a pixel
/// grid. At the default of `1` every edge lands on a whole logical pixel, which
/// on a 1.5&times; display means half of them land mid-physical-pixel and the
/// compositor smears them. Set it to the window's display scale and Yoga rounds
/// to *physical* pixels instead, so a 1px border is one crisp device pixel wide
/// at any scale. This is the mechanism behind the fractional-DPI claim ADR-0019
/// makes; a window that changes displays needs a new factor and a fresh layout
/// pass.
///
/// **Web defaults** correct Yoga's two deviations from CSS: Yoga defaults
/// `flex-direction` to `column` where CSS says `row`, and `flex-shrink` to `0`
/// where CSS says `1`. Goldberry turns them on, because the CSS subset in
/// `docs/ARCHITECTURE.md` §8 promises a stylesheet behaves the way a stylesheet
/// does. [#create()] does this; a caller that wants Yoga's own defaults has to
/// ask.
///
/// Confined to the thread that created it, and must be closed — after every node
/// that uses it. Closing it early is refused rather than left to become a
/// segfault on the next layout pass.
public final class YogaConfig implements AutoCloseable {

    private final Yoga yoga = Yoga.get();
    private final MemorySegment pointer;
    private final Thread owner = Thread.currentThread();

    /// Nodes created with this config that have not been freed. A config
    /// outlives its nodes or the layout pass dereferences freed memory.
    private int liveNodes;

    private boolean freed;

    private YogaConfig() {
        this.pointer = yoga.configNew();
        yoga.configUseWebDefaults(pointer, true);
    }

    /// A config with CSS's defaults and a point scale factor of 1.
    ///
    /// The scale factor is deliberately not guessed: only the backend knows what
    /// display the window is on, and a wrong guess is worse than the default
    /// because it looks right until the window moves.
    public static YogaConfig create() {
        return new YogaConfig();
    }

    /// Sets the grid computed positions are rounded to, in physical pixels per
    /// logical pixel — the window's display scale.
    ///
    /// `0` disables rounding entirely, which leaves subpixel positions in the
    /// computed layout. That is not the same as `1`, and it is almost never what
    /// a widget wants.
    ///
    /// Takes effect on the next layout pass, not retroactively.
    ///
    /// @throws IllegalArgumentException if the factor is negative or not finite
    public void setPointScaleFactor(float factor) {
        requireUsable();
        if (!Float.isFinite(factor) || factor < 0f) {
            throw new IllegalArgumentException(
                    "a point scale factor must be a finite number of physical pixels per logical"
                            + " pixel, and " + factor + " is not");
        }
        yoga.configPointScaleFactor(pointer, factor);
    }

    /// The grid computed positions are rounded to.
    public float pointScaleFactor() {
        requireUsable();
        return yoga.configPointScaleFactor(pointer);
    }

    /// Whether CSS's defaults are in force rather than Yoga's own. On by default
    /// — see the class docs for what the two disagree about.
    public void setUseWebDefaults(boolean useWebDefaults) {
        requireUsable();
        yoga.configUseWebDefaults(pointer, useWebDefaults);
    }

    /// Whether CSS's defaults are in force.
    public boolean useWebDefaults() {
        requireUsable();
        return yoga.configUseWebDefaults(pointer);
    }

    /// Frees the config.
    ///
    /// @throws IllegalStateException if nodes created with this config are still
    ///         alive — freeing it under them leaves every one of them pointing
    ///         at released memory, which surfaces as a crash inside Yoga on the
    ///         next layout pass rather than here
    @Override
    public void close() {
        if (freed) {
            return;
        }
        requireOwner();
        if (liveNodes > 0) {
            throw new IllegalStateException(
                    "this config still has " + liveNodes + " live node(s) — close the tree first,"
                            + " because a node outliving its config crashes inside Yoga, not here");
        }
        freed = true;
        yoga.configFree(pointer);
    }

    /// Whether the config has been freed.
    public boolean isClosed() {
        return freed;
    }

    MemorySegment pointer() {
        requireUsable();
        return pointer;
    }

    void nodeCreated() {
        liveNodes++;
    }

    void nodeFreed() {
        liveNodes--;
    }

    private void requireUsable() {
        requireOwner();
        if (freed) {
            throw new IllegalStateException("this YogaConfig has been closed");
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) {
            var current = Thread.currentThread();
            throw new IllegalStateException(
                    "a YogaConfig belongs to the thread that created it ("
                            + (owner.getName().isEmpty() ? "#" + owner.threadId() : owner.getName())
                            + "), and this is "
                            + (current.getName().isEmpty()
                                    ? "#" + current.threadId()
                                    : current.getName()));
        }
    }

    @Override
    public String toString() {
        return "YogaConfig" + (freed ? " (closed)" : "[nodes=" + liveNodes + "]");
    }
}
