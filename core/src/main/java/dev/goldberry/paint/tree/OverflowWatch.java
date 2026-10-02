package dev.goldberry.paint.tree;

import java.util.function.Consumer;

import dev.goldberry.css.StyleElement;
import dev.goldberry.layout.Overflow;
import dev.goldberry.layout.Position;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.overflow.OverflowLog;
import dev.goldberry.paint.overflow.Overrun;

/// Finds the boxes that did not fit.
///
/// ## Where it looks, and what that costs
///
/// **Every subtree the layout pass laid out again**, and nothing else. A
/// render tree already walks exactly those once per frame to read where Yoga
/// put them ([RenderObject#settle()]), and skips a subtree whose boxes and
/// rectangle both held. That walk asks [#inspect] about each node it
/// enters, so a node is looked at when it is first laid out and whenever it
/// moves or changes, and a static frame looks at nothing.
///
/// The question itself is arithmetic on rectangles that walk has already
/// read: no foreign call, and no allocation unless something overran.
///
/// Asking the root's `hadOverflow` instead was the first design, and it missed
/// the case that matters most: Yoga sets that flag on the container whose
/// line ran over, and an overrun inside a fixed-width or absolutely placed
/// box — a dialog — never reaches the root's line.
///
/// [OverflowLog] makes sure each shape is *said* once however often it is
/// found. [#walk] is the other entry: the whole tree, unconditionally, into
/// whatever the caller collects with, for a test that cannot depend on what
/// the log has already said.
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
/// - **Three more, and they are geometric rather than structural**, so they live
///   in [dev.goldberry.paint.overflow.Overrun#between]
///   where both rectangles are: a container with no size, a child that starts
///   outside its container, and a pixel or two.
final class OverflowWatch {

    /// The two names an overrun is first measured with, before it is known
    /// whether there is one to name.
    private static final String UNNAMED = "";

    private OverflowWatch() {}

    /// Reports every child of `node` laid out past it, to the log.
    ///
    /// One level: the caller is the walk that settles the tree, and it reaches
    /// the children itself.
    static void inspect(RenderObject node) {
        inspect(node, OverflowLog::report);
    }

    /// Hands every overrun in the subtree under `node` to `sink`, whether or
    /// not it was laid out this pass and whether or not it was said before.
    static void walk(RenderObject node, Consumer<Overrun> sink) {
        inspect(node, sink);
        for (var child : node.children()) {
            walk(child, sink);
        }
    }

    private static void inspect(RenderObject node, Consumer<Overrun> sink) {
        var box = node.box();
        if (box == null || box.overflow() != Overflow.VISIBLE) {
            return;
        }
        var container = node.layout();
        for (var child : node.children()) {
            var childBox = child.box();
            if (childBox == null || childBox.position() == Position.ABSOLUTE) {
                continue;
            }
            // Measured unnamed first: a name is a string built per child, and
            // nearly every child fits.
            var overrun = Overrun.between(UNNAMED, UNNAMED, container, child.layout());
            if (overrun != null) {
                sink.accept(new Overrun(name(box), name(childBox), overrun.overrunX(), overrun.overrunY()));
            }
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
