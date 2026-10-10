package dev.goldberry.image.lottie;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/// What a trim does to the paths before it: keeps the stretch from its start to
/// its end, moved along by its offset.
///
/// The range is read as After Effects and every player read it: start and end
/// are fractions of the length, the offset is a fraction of a turn added to
/// both, the two are swapped when the start is past the end, and a range that
/// runs past the end of the path wraps round to its beginning. On a closed path
/// the wrapped pieces are joined into one run, so the seam where the path
/// started is not drawn as two caps.
final class Trimming {

    private Trimming() {}

    /// `start`, `end` and `offset` as the range that is kept: the offset added
    /// and the two in order, the end up to 2 when the range wraps. Null when the
    /// range keeps everything; an empty range is `{0, 0}`.
    ///
    /// @param start  0 to 1
    /// @param end    0 to 1
    /// @param offset turns
    static double @Nullable [] range(double start, double end, double offset) {
        var shift = offset % 1;
        if (shift < 0) {
            shift += 1;
        }
        var s = Math.clamp(start, 0, 1) + shift;
        var e = Math.clamp(end, 0, 1) + shift;
        if (s > e) {
            var swap = s;
            s = e;
            e = swap;
        }
        if (e - s >= 1) {
            return null;
        }
        if (e - s <= 0) {
            return new double[] {0, 0};
        }
        return new double[] {s, e};
    }

    /// Each of `contours` cut down to the range on its own.
    static List<Contour> each(List<Contour> contours, double start, double end, double offset) {
        var range = range(start, end, offset);
        if (range == null) {
            return contours;
        }
        var out = new ArrayList<Contour>();
        if (range[1] <= range[0]) {
            return out;
        }
        for (var contour : contours) {
            var lengths = contour.segmentLengths();
            var total = sum(lengths);
            cut(contour, lengths, range[0] * total, range[1] * total, total, out);
        }
        return out;
    }

    /// The contours laid end to end and cut as one path, each contour's share
    /// of what is kept returned in its place.
    ///
    /// @param start what [#range] made of the start
    /// @param end   what [#range] made of the end
    static List<List<Contour>> together(List<Contour> contours, double start, double end) {
        var all = new ArrayList<double[]>(contours.size());
        var total = 0.0;
        for (var contour : contours) {
            var lengths = contour.segmentLengths();
            all.add(lengths);
            total += sum(lengths);
        }
        var from = start * total;
        var to = end * total;
        var walked = 0.0;
        var out = new ArrayList<List<Contour>>(contours.size());
        for (var i = 0; i < contours.size(); i++) {
            var lengths = all.get(i);
            var length = sum(lengths);
            var contour = contours.get(i);
            var kept = new ArrayList<Contour>(1);
            // The range, and its wrapped part, in this contour's own distances.
            keep(contour, lengths, from - walked, to - walked, length, kept);
            if (to > total) {
                keep(contour, lengths, from - total - walked, to - total - walked, length, kept);
            }
            out.add(kept);
            walked += length;
        }
        return out;
    }

    /// Cuts one contour by a range that may run past its end and wrap.
    private static void cut(
            Contour contour, double[] lengths, double from, double to, double total, List<Contour> out) {
        if (total <= 0) {
            return;
        }
        if (to <= total) {
            out.addAll(contour.between(from, to, lengths));
            return;
        }
        if (from >= total) {
            out.addAll(contour.between(from - total, to - total, lengths));
            return;
        }
        var tail = contour.between(from, total, lengths);
        var head = contour.between(0, to - total, lengths);
        if (contour.closed() && tail.size() == 1 && head.size() == 1) {
            out.add(tail.getFirst().joined(head.getFirst()));
            return;
        }
        out.addAll(tail);
        out.addAll(head);
    }

    private static void keep(
            Contour contour, double[] lengths, double from, double to, double length, List<Contour> out) {
        var a = Math.max(0, from);
        var b = Math.min(length, to);
        if (b > a) {
            out.addAll(contour.between(a, b, lengths));
        }
    }

    private static double sum(double[] values) {
        var total = 0.0;
        for (var value : values) {
            total += value;
        }
        return total;
    }
}
