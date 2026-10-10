package dev.goldberry.image.lottie;

import java.util.List;

import org.jspecify.annotations.Nullable;

/// A number, or a short vector of them, that may change over time: a
/// position, a scale, a colour, an opacity, a gradient's stops.
///
/// [Fixed] when the document gives one value and [Keyed] when it gives
/// keyframes. Evaluating a fixed one hands back the same array every time, so
/// the common case costs nothing per frame; **the arrays handed back are
/// never to be written to**.
sealed interface Property permits Property.Fixed, Property.Keyed {

    /// The value at `frame`, in the layer's own frames.
    double[] at(double frame);

    /// The first component at `frame` — what a scalar property is read as.
    default double scalar(double frame) {
        var value = at(frame);
        return value.length == 0 ? 0 : value[0];
    }

    /// Whether this never changes.
    boolean isFixed();

    /// A property that is always `value`.
    static Property of(double... value) {
        return new Fixed(value);
    }

    /// One value for all time.
    final class Fixed implements Property {

        private final double[] value;

        Fixed(double[] value) {
            this.value = value.clone();
        }

        @Override
        public double[] at(double frame) {
            return value;
        }

        @Override
        public boolean isFixed() {
            return true;
        }
    }

    /// Values at moments, and how to get from one to the next.
    final class Keyed implements Property {

        private final List<Key> keys;

        /// @param keys at least one, in ascending time
        Keyed(List<Key> keys) {
            if (keys.isEmpty()) {
                throw new IllegalArgumentException("an animated property needs a keyframe");
            }
            this.keys = List.copyOf(keys);
        }

        @Override
        public boolean isFixed() {
            return false;
        }

        @Override
        public double[] at(double frame) {
            var first = keys.getFirst();
            if (frame <= first.time()) {
                return first.start();
            }
            // The last keyframe is where the value comes to rest. A document in
            // the old shape gives it no value of its own, and the one before it
            // says where it ends.
            var last = keys.getLast();
            if (frame >= last.time()) {
                return keys.size() > 1 ? keys.get(keys.size() - 2).end() : last.start();
            }
            var index = 0;
            while (index + 1 < keys.size() && keys.get(index + 1).time() <= frame) {
                index++;
            }
            var key = keys.get(index);
            var next = keys.get(index + 1);
            if (key.hold()) {
                return key.start();
            }
            var span = next.time() - key.time();
            var x = span <= 0 ? 1 : (frame - key.time()) / span;
            return key.interpolate(x);
        }
    }

    /// One keyframe: where the value starts, where it is going, and the curve
    /// between.
    final class Key {

        private final double time;
        private final double[] start;
        private final double[] end;
        private final boolean hold;
        private final List<Easing> easing;
        private final @Nullable Spatial spatial;

        /// @param time    the frame it starts at
        /// @param start   the value at `time`
        /// @param end     the value at the next keyframe's time
        /// @param hold    whether the value jumps at the next keyframe rather
        ///                than moving towards it
        /// @param easing  one curve, or one per component
        /// @param spatial the path a position takes, or null for a straight line
        Key(double time, double[] start, double[] end, boolean hold, List<Easing> easing, @Nullable Spatial spatial) {
            this.time = time;
            this.start = start.clone();
            this.end = end.clone();
            this.hold = hold;
            this.easing = List.copyOf(easing);
            this.spatial = spatial;
        }

        double time() {
            return time;
        }

        double[] start() {
            return start;
        }

        double[] end() {
            return end;
        }

        boolean hold() {
            return hold;
        }

        double[] interpolate(double x) {
            var count = Math.min(start.length, end.length);
            var out = new double[count];
            if (spatial != null) {
                var eased = easing.isEmpty() ? x : easing.getFirst().apply(x);
                spatial.point(eased, out);
                for (var i = 2; i < count; i++) {
                    out[i] = start[i] + (end[i] - start[i]) * eased;
                }
                return out;
            }
            for (var i = 0; i < count; i++) {
                var curve = easing.isEmpty() ? Easing.LINEAR : easing.get(Math.min(i, easing.size() - 1));
                var eased = curve.apply(x);
                out[i] = start[i] + (end[i] - start[i]) * eased;
            }
            return out;
        }
    }

    /// The curve a position keyframe travels along, from its start through two
    /// tangents to its end, walked at even speed.
    ///
    /// After Effects eases a motion path by **distance** along it, not by the
    /// curve's own parameter, which bunches up where the control points are
    /// close. So the curve is measured once, when the document is read, into a
    /// table of cumulative lengths, and a progress is turned into a parameter
    /// through it.
    final class Spatial {

        private static final int SAMPLES = 32;

        private final double x0;
        private final double y0;
        private final double c1x;
        private final double c1y;
        private final double c2x;
        private final double c2y;
        private final double x1;
        private final double y1;
        private final double[] lengths = new double[SAMPLES + 1];

        /// @param outTangent the start's tangent, relative to the start
        /// @param inTangent  the end's tangent, relative to the end
        Spatial(double[] start, double[] end, double[] outTangent, double[] inTangent) {
            x0 = start[0];
            y0 = start[1];
            x1 = end[0];
            y1 = end[1];
            c1x = x0 + outTangent[0];
            c1y = y0 + outTangent[1];
            c2x = x1 + inTangent[0];
            c2y = y1 + inTangent[1];
            var px = x0;
            var py = y0;
            for (var i = 1; i <= SAMPLES; i++) {
                var t = (double) i / SAMPLES;
                var qx = coordinate(x0, c1x, c2x, x1, t);
                var qy = coordinate(y0, c1y, c2y, y1, t);
                lengths[i] = lengths[i - 1] + Math.hypot(qx - px, qy - py);
                px = qx;
                py = qy;
            }
        }

        /// Whether two tangents make a curve at all; two zero tangents are a
        /// straight line, which the plain interpolation draws already.
        static boolean curves(double[] outTangent, double[] inTangent) {
            return outTangent.length >= 2
                    && inTangent.length >= 2
                    && (outTangent[0] != 0 || outTangent[1] != 0 || inTangent[0] != 0 || inTangent[1] != 0);
        }

        void point(double progress, double[] out) {
            var total = lengths[SAMPLES];
            var t = progress;
            if (total > 0) {
                var target = progress * total;
                if (target <= 0) {
                    t = 0;
                } else if (target >= total) {
                    t = 1;
                } else {
                    var i = 1;
                    while (i < SAMPLES && lengths[i] < target) {
                        i++;
                    }
                    var before = lengths[i - 1];
                    var piece = lengths[i] - before;
                    var within = piece > 0 ? (target - before) / piece : 0;
                    t = (i - 1 + within) / SAMPLES;
                }
            }
            out[0] = coordinate(x0, c1x, c2x, x1, t);
            if (out.length > 1) {
                out[1] = coordinate(y0, c1y, c2y, y1, t);
            }
        }

        private static double coordinate(double p0, double p1, double p2, double p3, double t) {
            var u = 1 - t;
            return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3;
        }
    }
}
