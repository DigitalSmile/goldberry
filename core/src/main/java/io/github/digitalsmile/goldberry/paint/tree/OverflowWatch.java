package io.github.digitalsmile.goldberry.paint.tree;

import io.github.digitalsmile.goldberry.css.StyleElement;
import io.github.digitalsmile.goldberry.layout.Overflow;
import io.github.digitalsmile.goldberry.layout.Position;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.overflow.OverflowLog;
import io.github.digitalsmile.goldberry.paint.overflow.Overrun;

/// Finds the boxes that did not fit, and is asked only when one of them did not.
///
/// ## What it costs on a frame where everything fits
///
/// One foreign call. Yoga records `hadOverflow` on the **container** whose flex
/// line ran past its own edge, and the container that matters is the root: a
/// control pushed off the edge of a window is the window's line overflowing.
/// So the frame loop asks the root that one question, and this walk only runs
/// when the answer is yes ([ADR-0375]).
///
/// On the frames after that the walk runs every time, and it is a tree walk with
/// one foreign call per node. That is the deliberate trade: a window that is
/// already too small for its content is not the frame budget's difficult case,
/// and [OverflowLog] makes sure the *reporting* happens once however often the
/// walk does.
///
/// ## What is not an overrun
///
/// - **A box that clips on purpose.** A `scroll` viewport's content is longer
///   than the viewport; that is the widget working. Any container whose
///   `overflow` is not [Overflow#VISIBLE] is skipped, and so is everything
///   directly inside it.
/// - **A box that was placed rather than flowed.** An absolute child is put
///   where its insets say — a tab's underline is pinned across the bottom of its
///   header and a popover's arrow hangs off its panel.
final class OverflowWatch {

    private OverflowWatch() {}

    /// Walks `node` and reports every child laid out past its container.
    static void check(RenderObject node) {
        var box = node.box();
        if (box == null) {
            return;
        }
        if (box.overflow() == Overflow.VISIBLE) {
            var container = node.layout();
            for (var child : node.children()) {
                var childBox = child.box();
                if (childBox == null || childBox.position() == Position.ABSOLUTE) {
                    continue;
                }
                var overrun = Overrun.between(name(box), name(childBox), container, child.layout());
                if (overrun != null) {
                    OverflowLog.report(overrun);
                }
            }
        }
        for (var child : node.children()) {
            check(child);
        }
    }

    /// What to call a box in a report.
    ///
    /// Its owner's type when the widget layer gave it one, its id when it has
    /// one and no type, and a bare noun otherwise — a box built by composition
    /// has no name, which is the same reason it matches no type selector.
    private static String name(Box box) {
        if (box.owner() instanceof StyleElement element) {
            var type = element.type();
            if (type != null) {
                return element.id() == null ? "`" + type + "`" : "`" + type + "#" + element.id() + "`";
            }
        }
        return "a box";
    }
}
