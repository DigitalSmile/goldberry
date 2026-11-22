package dev.goldberry.paint.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.goldberry.render.DamageRect;

/// Brings a frame's damage down to a few rectangles by merging the nearest
/// ones, rather than giving up and damaging the whole frame.
///
/// A window with twenty small things changing in it, a column of status dots
/// or a toolbar of icons, used to cross the rectangle limit and repaint
/// everything. Merging keeps the repaint near what changed: two rectangles a
/// few pixels apart cost little more as one, and two at opposite corners are
/// the last to be joined.
///
/// **Nearest is cheapest.** Each step joins the pair whose bounding rectangle
/// adds the least area that neither covered. Overlapping and touching pairs
/// add the least, so a row of icons collapses into a strip before the strip is
/// joined to anything across the window.
///
/// Pure arithmetic on physical rectangles, so it is tested without a frame.
final class DamageMerge {

    /// Past this many rectangles the pairwise search is not worth its cost,
    /// and the whole frame is the answer. A frame that changes in this many
    /// places is a frame that changed nearly everywhere.
    static final int SEARCH_LIMIT = 256;

    private DamageMerge() {}

    /// `rects` merged down to at most `max`, or empty when there are too many
    /// to search ([#SEARCH_LIMIT]) and the caller should damage the whole frame.
    ///
    /// Every pixel of `rects` is inside one of the rectangles returned: merging
    /// only ever grows what is repainted.
    static Optional<List<DamageRect>> reduce(List<DamageRect> rects, int max) {
        if (max < 1) {
            throw new IllegalArgumentException("at least one rectangle must be allowed, not " + max);
        }
        if (rects.size() <= max) {
            return Optional.of(List.copyOf(rects));
        }
        if (rects.size() > SEARCH_LIMIT) {
            return Optional.empty();
        }
        var merged = new ArrayList<>(rects);
        while (merged.size() > max) {
            var bestA = 0;
            var bestB = 1;
            var bestCost = Long.MAX_VALUE;
            for (var a = 0; a < merged.size(); a++) {
                for (var b = a + 1; b < merged.size(); b++) {
                    var cost = cost(merged.get(a), merged.get(b));
                    if (cost < bestCost) {
                        bestCost = cost;
                        bestA = a;
                        bestB = b;
                    }
                }
            }
            // `bestB` is after `bestA`, so removing it first keeps `bestA` where it is.
            var joined = union(merged.get(bestA), merged.remove(bestB));
            merged.set(bestA, joined);
            absorb(merged, bestA);
        }
        return Optional.of(List.copyOf(merged));
    }

    /// Folds into `rects[at]` every other rectangle it now overlaps, until none
    /// does: a join that grew over a third rectangle would otherwise upload the
    /// overlap twice.
    private static void absorb(List<DamageRect> rects, int at) {
        var grew = true;
        while (grew) {
            grew = false;
            for (var i = rects.size() - 1; i >= 0; i--) {
                if (i != at && intersects(rects.get(at), rects.get(i))) {
                    var other = rects.remove(i);
                    if (i < at) {
                        at--;
                    }
                    rects.set(at, union(rects.get(at), other));
                    grew = true;
                }
            }
        }
    }

    /// The area the union of `a` and `b` covers that neither of them does:
    /// what joining them adds to the repaint. Never negative.
    static long cost(DamageRect a, DamageRect b) {
        return area(union(a, b)) - area(a) - area(b) + overlap(a, b);
    }

    /// The area `a` and `b` share, or zero.
    private static long overlap(DamageRect a, DamageRect b) {
        var width = Math.min(a.x() + a.width(), b.x() + b.width()) - Math.max(a.x(), b.x());
        var height = Math.min(a.y() + a.height(), b.y() + b.height()) - Math.max(a.y(), b.y());
        return width > 0 && height > 0 ? (long) width * height : 0;
    }

    private static long area(DamageRect rect) {
        return (long) rect.width() * rect.height();
    }

    static boolean intersects(DamageRect a, DamageRect b) {
        return a.x() < b.x() + b.width()
                && b.x() < a.x() + a.width()
                && a.y() < b.y() + b.height()
                && b.y() < a.y() + a.height();
    }

    static DamageRect union(DamageRect a, DamageRect b) {
        var x = Math.min(a.x(), b.x());
        var y = Math.min(a.y(), b.y());
        return new DamageRect(
                x,
                y,
                Math.max(a.x() + a.width(), b.x() + b.width()) - x,
                Math.max(a.y() + a.height(), b.y() + b.height()) - y);
    }
}
