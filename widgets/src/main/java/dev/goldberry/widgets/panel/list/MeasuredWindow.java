package dev.goldberry.widgets.panel.list;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

/// What a list virtualized over [RowHeights] remembers: the height each row came
/// out as, where every row begins, which rows are built, and which row the
/// reader is on.
///
/// ## Where a row begins
///
/// [#tops] holds one more entry than there are rows: entry `i` is where row `i`
/// begins below the list's top, and the last is the list's whole height. A row
/// that has been measured counts at its measurement and every other row at the
/// estimate, so the spacers the list builds out of these are exactly as tall as
/// the rows they stand for were counted — which is what keeps the column the
/// same height whichever rows are built, and the window from oscillating.
///
/// Recomputed in one pass when anything under it changed and not before it is
/// read, so a frame that measures twenty rows pays for one sum rather than
/// twenty.
///
/// ## The reader's line
///
/// The row the reader is looking at is the first one that begins at or below
/// the viewport's top edge — the line a reader would put a finger on, and the
/// one `scroll`'s own preservation picks. A height that changes **above** it moves
/// it on screen by exactly the change, and that change is what [#measured]
/// returns, for the list to hand to the enclosing `scroll`. A height that changes
/// below it moves nothing the reader can see.
///
/// The reader is re-read from the geometry only on a frame that corrected
/// nothing, for the reason `scroll`'s preservation re-picks only on a quiet
/// frame: until the correction has landed, the row at the top edge is the one
/// the jump put there, and re-reading it would measure the correction a second
/// time.
final class MeasuredWindow {

    /// How many rows beyond each edge of the viewport are built anyway — the
    /// fixed-height path's figure, for its reason.
    static final int OVERSCAN = 4;

    /// What the first build holds, before any geometry has been seen. A guess,
    /// harmless for the fixed-height path's reason: the spacers are right from
    /// the first frame however few rows are in the window.
    private static final int FIRST_GUESS = 40;

    /// Below half a logical pixel a height has not changed: it is float
    /// arithmetic out of layout, not a row that grew.
    private static final double STILL = 0.5;

    /// The height each item's row came out as, by the item's identity.
    private final Map<String, Double> heights = new HashMap<>();

    /// The model the arrays below describe — compared by identity, since a
    /// `ListView` copies its items once and a new list is a new model.
    private List<?> items = List.of();

    private double estimate = Double.NaN;
    private String[] ids = new String[0];
    private Map<String, Integer> indexOf = Map.of();
    private double[] tops = {0};
    private boolean stale = true;

    /// The rows built, `[first, last)`.
    private int first;

    private int last = FIRST_GUESS;

    /// The reader's row, by identity, so that a prepend does not move it.
    private @Nullable String reader;

    /// Whether a correction has been handed out since the reader was last read.
    private boolean correcting;

    /// How far the reader's line has moved in the list since the window was last
    /// fitted, and is about to be followed by the viewport: the corrections
    /// [#measured] handed out, and the ones [#widthIs] did.
    private double corrected;

    /// How far rows inserted or removed above the reader's line moved it since
    /// the window was last fitted — which a viewport that preserves its reader's
    /// line is about to follow, and any other is not.
    private double inserted;

    /// The width the heights were measured at, or NaN before the first frame.
    private double width = Double.NaN;

    /// Takes the current model and estimate, keeping the window on the same
    /// **items** when rows were inserted or removed around it.
    <T> void sync(List<T> model, Function<T, String> identity, double guess) {
        if (model == items && guess == estimate) {
            return;
        }
        var firstId = first < ids.length ? ids[first] : null;
        var span = last - first;
        var line = reader == null ? -1 : index(reader);
        var lineWas = line < 0 ? Double.NaN : tops()[line];
        if (model != items) {
            var next = new String[model.size()];
            var index = new HashMap<String, Integer>(Math.max(16, model.size() * 2));
            for (var i = 0; i < next.length; i++) {
                next[i] = identity.apply(model.get(i));
                index.putIfAbsent(next[i], i);
            }
            ids = next;
            indexOf = index;
            items = model;
            var moved = firstId == null ? null : indexOf.get(firstId);
            if (moved != null) {
                first = moved;
                last = moved + span;
            }
        }
        estimate = guess;
        stale = true;
        line = reader == null ? -1 : index(reader);
        if (line >= 0 && !Double.isNaN(lineWas)) {
            var moved = tops()[line] - lineWas;
            if (moved != 0) {
                inserted += moved;
                correcting = true;
            }
        }
    }

    /// Where every row begins, with the list's height last.
    double[] tops() {
        if (stale) {
            if (tops.length != ids.length + 1) {
                tops = new double[ids.length + 1];
            }
            var at = 0.0;
            for (var i = 0; i < ids.length; i++) {
                tops[i] = at;
                var measured = heights.get(ids[i]);
                at += measured == null ? estimate : measured;
            }
            tops[ids.length] = at;
            stale = false;
        }
        return tops;
    }

    int size() {
        return ids.length;
    }

    String id(int index) {
        return ids[index];
    }

    int index(String id) {
        var found = indexOf.get(id);
        return found == null ? -1 : found;
    }

    /// The first row built, clamped to the model.
    int first() {
        return Math.min(first, ids.length);
    }

    /// One past the last row built, clamped to the model.
    int last() {
        return Math.max(first(), Math.min(last, ids.length));
    }

    /// Takes a row's height, and answers how far the reader's line moved because
    /// of it — zero unless the row is above that line and changed.
    double measured(String id, double height) {
        var known = heights.put(id, height);
        var was = known == null ? estimate : known;
        if (Math.abs(height - was) < STILL) {
            return 0;
        }
        stale = true;
        var at = indexOf.get(id);
        var line = reader == null ? null : indexOf.get(reader);
        if (at == null || line == null || at >= line) {
            return 0;
        }
        correcting = true;
        corrected += height - was;
        return height - was;
    }

    /// Forgets the heights measured at another width, apart from the rows built
    /// now, which the frame that changed the width has just measured again — and
    /// answers how far that moved the reader's line.
    double widthIs(double value) {
        if (Double.isNaN(width) || Math.abs(value - width) < STILL) {
            width = value;
            return 0;
        }
        width = value;
        var line = reader == null ? -1 : index(reader);
        var moved = 0.0;
        for (var i = 0; i < ids.length; i++) {
            if (i >= first() && i < last()) {
                continue;
            }
            var forgotten = heights.remove(ids[i]);
            if (forgotten != null && i < line) {
                moved += estimate - forgotten;
            }
        }
        stale = true;
        if (moved != 0) {
            correcting = true;
            corrected += moved;
        }
        return moved;
    }

    /// The window a viewport `height` tall needs when its top edge is `above`
    /// pixels below the list's top — with `reaching` kept inside it when it is
    /// not negative. True when it changed.
    ///
    /// `above` is where the viewport **was painted**, and a viewport about to
    /// follow a correction is about to be somewhere else: the corrections handed
    /// out since the last fit, and an insertion above the line when the viewport
    /// preserves its line, are added before anything is read. Without that, a
    /// page of history prepended above would fit the window to the rows that
    /// slid under the old offset for one frame — and the row the viewport keeps
    /// its line by would not be built on the frame it needs to be.
    boolean fit(double above, double height, int reaching, boolean preserving) {
        var at = tops();
        var count = ids.length;
        var ahead = corrected + (preserving ? inserted : 0);
        corrected = 0;
        inserted = 0;
        if (count == 0) {
            return false;
        }
        above += ahead;
        if (!correcting) {
            var line = rowAt(at, Math.max(0, above));
            if (at[line] < above && line + 1 < count) {
                line++;
            }
            reader = ids[line];
        }
        correcting = false;
        var from = Math.max(0, rowAt(at, Math.max(0, above)) - OVERSCAN);
        var to = Math.min(count, rowAt(at, Math.max(0, above + height)) + 1 + OVERSCAN);
        if (reaching >= 0) {
            from = Math.min(from, reaching);
            to = Math.max(to, reaching + 1);
        }
        if (from == first && to == last) {
            return false;
        }
        first = from;
        last = to;
        return true;
    }

    /// Moves the window to start at `index`, with enough rows below it for a
    /// viewport `height` tall, and makes it the reader's row: the list is about
    /// to scroll it to the top edge, and it is the line that has to stay put
    /// while the rows around it are measured.
    void jumpTo(int index, double height) {
        first = index;
        last = Math.min(ids.length, index + (int) Math.ceil(height / estimate) + 1 + OVERSCAN);
        reader = ids[index];
        correcting = true;
    }

    /// Whether the row for `id` has been measured.
    boolean isMeasured(String id) {
        return heights.containsKey(id);
    }

    /// The last row that begins at or above `y`.
    private static int rowAt(double[] at, double y) {
        var low = 0;
        var high = at.length - 2;
        while (low < high) {
            var mid = (low + high + 1) >>> 1;
            if (at[mid] <= y) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return Math.max(0, low);
    }
}
