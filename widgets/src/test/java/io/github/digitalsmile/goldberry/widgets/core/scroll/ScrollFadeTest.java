package io.github.digitalsmile.goldberry.widgets.core.scroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// §2.4's auto-hiding scrollbars, and — more to the point — **whether they ask
/// for the frames they need to hide in** ([ADR-0226]).
///
/// The opacity half is drawable and therefore checkable by a golden. The
/// `isAnimating` half is not: a golden drives `render` by hand and never asks
/// whether the frame loop would have come back, so a fade that answered `false`
/// would paint the bars once at whatever opacity the last frame caught and leave
/// them there forever — and every image in the corpus would still be right
/// ([ADR-0176]). Nothing but an assertion on `isAnimating` can see it, and this
/// is where they live for this package.
class ScrollFadeTest {

    /// A time in the middle of nothing. The absolute value never matters — every
    /// reading here is a difference — and starting away from zero is what stops a
    /// test from passing on an uninitialised `NaN` that happens to compare false.
    private static final double T0 = 10_000;

    @Nested
    @DisplayName("what it draws")
    class Opacity {

        /// A window that opens on a scrollable document shows no bar until the
        /// user does something. §2.4 calls these *overlay* scrollbars, and an
        /// overlay that greets you is a reserved gutter with extra steps.
        @Test
        @DisplayName("nothing has moved, so there is nothing to see")
        void invisibleUntilSomethingHappens() {
            var fade = new ScrollFade();
            fade.stamp(T0);

            assertEquals(0, fade.opacity());
        }

        @Test
        @DisplayName("it is at full strength for the whole idle period")
        void fullWhileIdle() {
            var fade = new ScrollFade();
            fade.woken();
            fade.stamp(T0);

            assertEquals(1, fade.opacity());
            fade.stamp(T0 + ScrollFade.IDLE_MILLIS);
            assertEquals(1, fade.opacity(), "it started fading before §2.4's 800ms were up");
        }

        @Test
        @DisplayName("it fades across the next 160ms and then it is gone")
        void fadesOut() {
            var fade = new ScrollFade();
            fade.woken();
            fade.stamp(T0);

            fade.stamp(T0 + ScrollFade.IDLE_MILLIS + ScrollFade.FADE_MILLIS / 2);
            assertEquals(0.5, fade.opacity(), 0.01);

            fade.stamp(T0 + ScrollFade.IDLE_MILLIS + ScrollFade.FADE_MILLIS);
            assertEquals(0, fade.opacity());
        }

        /// §2.4 widens the bars on hover, and a bar that faded out from under a
        /// pointer resting on it would be absurd.
        @Test
        @DisplayName("a pointer on the viewport holds them open however long it has been")
        void heldOpen() {
            var fade = new ScrollFade();
            fade.woken();
            fade.stamp(T0);
            fade.hold(true);

            fade.stamp(T0 + 10 * ScrollFade.IDLE_MILLIS);

            assertTrue(fade.isHeld());
            assertEquals(1, fade.opacity());
        }
    }

    @Nested
    @DisplayName("what it asks the frame loop for")
    class Frames {

        @Test
        @DisplayName("a fade nothing has woken owes no frames")
        void idleAsksForNothing() {
            assertFalse(new ScrollFade().isAnimating());
        }

        /// The wake and the clock arrive at different moments — a wheel event
        /// knows something happened and has no time, and `render` has a time and
        /// does not know what happened. A pending wake owes a frame *before* it
        /// has a time, or the frame that would give it one never comes.
        @Test
        @DisplayName("a wake owes a frame before it has been given a time")
        void pendingOwesAFrame() {
            var fade = new ScrollFade();
            fade.woken();

            assertTrue(fade.isAnimating(), "the frame that would stamp this will never be asked for");
        }

        @Test
        @DisplayName("it keeps asking through the idle period and the fade")
        void asksUntilItIsGone() {
            var fade = new ScrollFade();
            fade.woken();
            fade.stamp(T0);
            assertTrue(fade.isAnimating(), "it stopped asking while the bars were still at full strength");

            fade.stamp(T0 + ScrollFade.IDLE_MILLIS + ScrollFade.FADE_MILLIS / 2);
            assertTrue(fade.isAnimating(), "it stopped asking half way through the fade, so the bars froze there");
        }

        /// The other end, and the one that matters as much: a fade that never
        /// stopped asking would keep a window awake for ever, which is the
        /// opposite failure and just as real (§1.7's idle loop).
        @Test
        @DisplayName("it stops once the bars are gone")
        void stopsWhenThereIsNothingLeftToDraw() {
            var fade = new ScrollFade();
            fade.woken();
            fade.stamp(T0);

            fade.stamp(T0 + ScrollFade.IDLE_MILLIS + ScrollFade.FADE_MILLIS);

            assertFalse(fade.isAnimating(), "a settled scroll view is asking for frames it will not draw");
            assertEquals(0, fade.opacity());
        }

        /// Held bars are **not** animating: they are standing still at full
        /// strength, and asking for frames to redraw an unchanging picture is the
        /// waste ADR-0081's idle loop exists to avoid.
        @Test
        @DisplayName("bars held open by the pointer are still, not animating")
        void heldIsNotMoving() {
            var fade = new ScrollFade();
            fade.woken();
            fade.stamp(T0);
            fade.hold(true);
            fade.stamp(T0 + 1);

            assertFalse(fade.isAnimating());
        }

        /// Letting go starts the clock again from wherever it was, which is the
        /// case a `hold` that swallowed the pending wake would break.
        @Test
        @DisplayName("letting go starts them fading again")
        void releasingResumes() {
            var fade = new ScrollFade();
            fade.woken();
            fade.stamp(T0);
            fade.hold(true);
            fade.stamp(T0 + 1);
            fade.hold(false);

            assertTrue(fade.isAnimating());
        }
    }
}
