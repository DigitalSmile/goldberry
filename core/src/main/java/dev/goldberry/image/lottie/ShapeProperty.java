package dev.goldberry.image.lottie;

import java.util.List;

/// A path that may change over time: Lottie's `ks` on a path shape and `pt` on a
/// mask.
///
/// Between two keyframes with the same number of vertices, every vertex and
/// tangent moves along the eased progress. Two keyframes that disagree about
/// the count cannot be blended and the first holds until the second, which is
/// what After Effects does with a path that gained a point.
sealed interface ShapeProperty permits ShapeProperty.Fixed, ShapeProperty.Keyed {

    /// The path at `frame`.
    Bezier at(double frame);

    /// One path for all time.
    record Fixed(Bezier path) implements ShapeProperty {

        @Override
        public Bezier at(double frame) {
            return path;
        }
    }

    /// One keyframe of a path.
    ///
    /// @param time   the frame it starts at
    /// @param start  the path at `time`
    /// @param end    the path at the next keyframe's time
    /// @param hold   whether the path jumps rather than moves
    /// @param easing the curve between the two
    record Key(double time, Bezier start, Bezier end, boolean hold, Easing easing) {}

    /// Paths at moments.
    ///
    /// @param keys at least one, in ascending time
    record Keyed(List<Key> keys) implements ShapeProperty {

        public Keyed {
            if (keys.isEmpty()) {
                throw new IllegalArgumentException("an animated path needs a keyframe");
            }
            keys = List.copyOf(keys);
        }

        @Override
        public Bezier at(double frame) {
            var first = keys.getFirst();
            if (frame <= first.time()) {
                return first.start();
            }
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
            if (key.hold() || !key.start().matches(key.end())) {
                return key.start();
            }
            var span = next.time() - key.time();
            var x = span <= 0 ? 1 : (frame - key.time()) / span;
            return key.start().towards(key.end(), key.easing().apply(x));
        }
    }
}
