package dev.goldberry.example.ui.shell;

import java.util.Optional;

import dev.goldberry.Host;
import dev.goldberry.input.hit.HitTest.Region;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.widget.Element;
import dev.goldberry.widgets.core.scroll.ScrollScope;

/// Scrolls a node into view by its id, from anywhere that holds the window.
///
/// The same two steps a `tour` takes before it lights a stop: find where the
/// node was painted, then ask the nearest viewport around it to move the least
/// it can to show it. Nothing is wired between the caller and the viewport —
/// the node is found by id and the viewport by walking up from it.
///
/// Read more: [Scroll](https://goldberry.dev/docs/layout/scroll.html).
public final class Reveal {

    private Reveal() {}

    /// How far below the viewport's top edge [#atTop] leaves a node.
    static final float MARGIN = 16;

    /// Brings the node `id` into its viewport, moving the least it can.
    ///
    /// @return whether the node was found; a node that is not painted yet, or
    ///         not built at all, is not
    public static boolean inView(Host host, String id) {
        var region = host.anchor(id);
        if (region.isEmpty()) {
            return false;
        }
        var found = region.orElseThrow();
        scope(found).ifPresent(scope -> scope.reveal(found.painted(), clip(found)));
        return true;
    }

    /// Scrolls the node `id` to the top of its viewport, or as near as the
    /// content allows — where a reader who asked for it expects it, rather
    /// than wherever the least move leaves it.
    ///
    /// The least move again, of a rectangle that starts just above the node
    /// and is as tall as the viewport: the only way to fit that is to put the
    /// node's top at the viewport's.
    ///
    /// @return whether the node was found
    public static boolean atTop(Host host, String id) {
        var region = host.anchor(id);
        if (region.isEmpty()) {
            return false;
        }
        var found = region.orElseThrow();
        var clip = clip(found);
        var painted = found.painted();
        var target = LogicalRect.of(
                painted.left(),
                painted.top() - MARGIN,
                painted.size().width(),
                Math.max(painted.size().height(), clip.size().height()));
        scope(found).ifPresent(scope -> scope.reveal(target, clip));
        return true;
    }

    /// Where the node `id` is on screen, if it has been painted.
    public static Optional<LogicalRect> painted(Host host, String id) {
        return host.anchor(id).map(Region::painted);
    }

    private static Optional<ScrollScope> scope(Region region) {
        return region.owner() instanceof Element element ? ScrollScope.enclosing(element) : Optional.empty();
    }

    /// What clips the node: its viewport's rectangle, or its own bounds when
    /// nothing clips it.
    private static LogicalRect clip(Region region) {
        var clip = region.clip();
        return clip.isNone()
                ? region.bounds()
                : LogicalRect.of((float) clip.left(), (float) clip.top(), (float) clip.width(), (float) clip.height());
    }
}
