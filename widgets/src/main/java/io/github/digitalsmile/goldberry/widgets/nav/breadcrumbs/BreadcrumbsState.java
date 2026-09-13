package io.github.digitalsmile.goldberry.widgets.nav.breadcrumbs;

import java.util.ArrayList;
import java.util.List;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.menu.Item;
import io.github.digitalsmile.goldberry.widgets.menu.Menu;
import io.github.digitalsmile.goldberry.widgets.menu.Menus;

/// A [Breadcrumbs]'s two facts: where its `…` was drawn, and which window it is
/// in.
///
/// Neither is describable. A widget is a value rebuilt every frame and thrown
/// away, so it cannot hold the rectangle a previous frame reported; and opening a
/// popup needs a [Host], which only a build has
/// ([BuildContext#host()]). That is the whole of why this widget is stateful —
/// everything else about a trail is a pure function of its crumbs
/// ([ADR-0306]).
final class BreadcrumbsState extends State<Breadcrumbs> {

    /// The window this is being built into, captured for the handlers.
    ///
    /// Read in `build` and **used** only from a click or a keypress, which is
    /// what [BuildContext#host()] allows: a build that read anything off a host
    /// would depend on the last frame, and nothing invalidates that.
    private Host host;

    /// Where the `…` was painted, in the window's coordinates.
    ///
    /// Assigned and **never** dirtied, which is [Located]'s rule: a widget told
    /// where it is must not move itself, and calling `setState` from here is how
    /// a located widget rebuilds forever
    /// ([ADR-0119]).
    /// It is read at the moment of a click, by which time the last frame has
    /// reported it.
    private LogicalRect overflowAt = LogicalRect.of(0, 0, 0, 0);

    @Override
    public Widget build(BuildContext context) {
        host = context.host().orElse(null);
        var trail = widget();
        var crumbs = trail.children();

        // The last crumb is where you are, and the trail is what says so -- see
        // `Breadcrumbs`. Computed against the written list rather than the shown
        // one, so collapsing the middle cannot change which crumb is current.
        var last = lastCrumbIndex(crumbs);

        var row = new ArrayList<Widget>(crumbs.size() * 2);
        var hidden = hiddenRange(crumbs.size(), trail.collapseAfter());
        for (var index = 0; index < crumbs.size(); index++) {
            if (index >= hidden[0] && index < hidden[1]) {
                if (index == hidden[0]) {
                    separate(row);
                    row.add(new CrumbOverflow(this::openOverflow, this::locateOverflow));
                }
                continue;
            }
            separate(row);
            var child = crumbs.get(index);
            row.add(child instanceof Crumb crumb ? crumb.asCurrent(index == last) : child);
        }
        return new CrumbTrail(row, trail.attributes());
    }

    /// A separator before everything but the first thing on the row.
    ///
    /// Driven by what is **already in the row** rather than by the loop's index,
    /// which is what makes it right on both sides of the collapsed middle: the
    /// `…` gets a chevron before it and the crumb after it gets another, with no
    /// case analysis about where the gap is.
    private static void separate(List<Widget> row) {
        if (!row.isEmpty()) {
            row.add(new CrumbSeparator());
        }
    }

    /// The half-open range of crumbs the row is not showing, or an empty range.
    ///
    /// `[first + 1, size - tail)`, where the row keeps the first crumb, the `…`
    /// and the last `collapseAfter - 2` — so what is on the row is exactly
    /// `collapseAfter` things, counting the `…` as one.
    ///
    /// Package-private and static so that
    /// [io.github.digitalsmile.goldberry.widgets.nav.breadcrumbs] tests can ask it
    /// directly: the arithmetic is the whole of the overflow rule, and asserting
    /// it through a built element tree would be asserting it twice removed.
    static int[] hiddenRange(int size, int collapseAfter) {
        if (collapseAfter <= 0 || size <= collapseAfter) {
            return new int[] {0, 0};
        }
        var shown = Math.max(collapseAfter, Breadcrumbs.MINIMUM_COLLAPSE_AFTER);
        if (size <= shown) {
            return new int[] {0, 0};
        }
        // One slot for the first crumb and one for the `…`; the rest is tail.
        var tail = shown - 2;
        return new int[] {1, size - tail};
    }

    /// The index of the last [Crumb] in the written list, or -1.
    ///
    /// The **last crumb**, not the last child: a trail may hold a `spacer` or a
    /// `badge` after its path, and "where you are" is the last step of the path
    /// rather than whatever was written last.
    private static int lastCrumbIndex(List<Widget> children) {
        for (var index = children.size() - 1; index >= 0; index--) {
            if (children.get(index) instanceof Crumb) {
                return index;
            }
        }
        return -1;
    }

    private void locateOverflow(LogicalRect self, LogicalRect clip) {
        overflowAt = self;
    }

    /// Opens a menu of the crumbs that are not on the row.
    ///
    /// Built at the moment of the click rather than held, because it is a
    /// function of the crumbs and the crumbs are the application's — a menu
    /// banked at build time would be the path as it was one frame ago.
    ///
    /// A crumb with no `press` still gets a row, disabled: it is part of the
    /// path, and hiding it would make the menu a different list from the trail.
    private void openOverflow() {
        if (host == null) {
            return;
        }
        var trail = widget();
        var crumbs = trail.children();
        var hidden = hiddenRange(crumbs.size(), trail.collapseAfter());
        var rows = new ArrayList<Widget>(hidden[1] - hidden[0]);
        for (var index = hidden[0]; index < hidden[1]; index++) {
            if (crumbs.get(index) instanceof Crumb crumb) {
                rows.add(new Item(crumb.label(), crumb.onPress()).disabled(crumb.onPress() == null));
            }
        }
        if (rows.isEmpty()) {
            return;
        }
        Menus.open(host, overflowAt, new Menu(rows.toArray(Widget[]::new)));
    }
}
