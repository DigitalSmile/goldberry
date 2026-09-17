package io.github.digitalsmile.goldberry.image.anim;

import java.util.List;
import java.util.Objects;

import io.github.digitalsmile.goldberry.image.Image;

/// A picture with more than one frame, and how long each of them is shown.
///
/// ## What it is not
///
/// It is not a player. There is no thread here, no timer and no clock: the only
/// question it answers is [#at], "how does this look `n` milliseconds in", and
/// the `n` belongs to whoever is drawing — a `canvas` painter reading the frame
/// clock, an offscreen render stepping a virtual one, a test asking for exactly
/// 240ms ([ADR-0382]).
///
/// That is the same division the rest of the toolkit's motion is built on: a
/// transition is a function of a clock a caller injects, so a golden image of a
/// mid-animation frame is a call rather than a sleep.
///
/// ## Every image is one
///
/// A still image is an animation of one frame shown for ever, so a caller that
/// asks for the animation of a PNG gets something it can draw with the same two
/// lines. That keeps the branch out of the calling code, which is where it would
/// otherwise be written once per caller.
///
/// @param frames    the frames, in order — at least one
/// @param loopCount how many times to play, or **0 for ever**. A GIF's own
///                  NETSCAPE extension spells "for ever" as 0, and so does this
public record Animation(List<Frame> frames, int loopCount) {

    /// One frame, and how long it is shown for.
    ///
    /// @param image       what to draw
    /// @param delayMillis how long it stays, in milliseconds
    public record Frame(Image image, int delayMillis) {

        public Frame {
            Objects.requireNonNull(image, "image");
            if (delayMillis < 0) {
                throw new IllegalArgumentException("a frame is shown for " + delayMillis + "ms, which is not a time");
            }
        }
    }

    /// How long a still frame is shown: long enough that no arithmetic on it
    /// overflows, and long enough that nothing ever asks for the frame after it.
    private static final int FOR_EVER = Integer.MAX_VALUE;

    public Animation {
        frames = List.copyOf(frames);
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("an animation with no frames in it is not one");
        }
        if (loopCount < 0) {
            throw new IllegalArgumentException("an animation plays " + loopCount + " times, which is not a count");
        }
    }

    /// A still image, as an animation of one frame.
    public static Animation still(Image image) {
        return new Animation(List.of(new Frame(image, FOR_EVER)), 0);
    }

    /// Whether this is one frame — which every PNG, JPEG and QOI is, and most
    /// GIFs are.
    public boolean isStill() {
        return frames.size() == 1;
    }

    /// How long one pass through takes, in milliseconds.
    public long durationMillis() {
        var total = 0L;
        for (var frame : frames) {
            total += frame.delayMillis();
        }
        return total;
    }

    /// Whether this ever stops.
    public boolean isEndless() {
        return loopCount == 0;
    }

    /// How long the whole thing takes, in milliseconds, or -1 when it never
    /// finishes.
    ///
    /// What a caller asks to know whether to keep requesting frames.
    public long totalMillis() {
        return isEndless() ? -1 : durationMillis() * loopCount;
    }

    /// The frame to draw `elapsedMillis` after the animation started.
    ///
    /// Past the end of a finite animation this is the **last** frame, which is
    /// what a GIF that has played its three loops looks like: it stops on its
    /// last picture rather than disappearing or starting again.
    ///
    /// @param elapsedMillis milliseconds since the start; negative reads as 0,
    ///                      because a caller comparing two clocks should get the
    ///                      first frame rather than an exception
    public Frame at(long elapsedMillis) {
        var elapsed = Math.max(0, elapsedMillis);
        var duration = durationMillis();
        if (isStill() || duration <= 0) {
            return frames.getFirst();
        }
        if (!isEndless() && elapsed >= duration * loopCount) {
            return frames.getLast();
        }
        var into = elapsed % duration;
        for (var frame : frames) {
            if (into < frame.delayMillis()) {
                return frame;
            }
            into -= frame.delayMillis();
        }
        // Only reachable if the delays changed under us, which they cannot: the
        // list is copied and each frame is a record.
        return frames.getLast();
    }

    /// The image to draw `elapsedMillis` in — [#at]'s frame, without the delay.
    public Image imageAt(long elapsedMillis) {
        return at(elapsedMillis).image();
    }
}
