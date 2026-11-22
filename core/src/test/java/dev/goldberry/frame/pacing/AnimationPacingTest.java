package dev.goldberry.frame.pacing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.render.desktop.PowerState;

/// How often an animating window asks for its next frame.
class AnimationPacingTest {

    private static final WindowActivity HIDDEN = new WindowActivity(true, true, false);
    private static final WindowActivity BEHIND = new WindowActivity(false, false, false);
    private static final WindowActivity SAVING = new WindowActivity(false, true, true);

    private final AnimationPacing pacing = new AnimationPacing();

    @Test
    @DisplayName("a window in front animates as fast as it presents")
    void foreground() {
        assertInstanceOf(AnimationPacing.Next.Now.class, pacing.next(WindowActivity.FOREGROUND, Duration.ZERO));
        assertEquals(0, pacing.rate(WindowActivity.FOREGROUND));
    }

    @Test
    @DisplayName("a window nobody can see asks for no animation frame at all")
    void occluded() {
        assertInstanceOf(AnimationPacing.Next.Paused.class, pacing.next(HIDDEN, Duration.ofSeconds(5)));
    }

    @Test
    @DisplayName("an unfocused window is not slowed unless the application asked")
    void inactiveIsOptIn() {
        assertInstanceOf(AnimationPacing.Next.Now.class, pacing.next(BEHIND, Duration.ZERO));

        pacing.inactiveRate(4);
        var next = assertInstanceOf(AnimationPacing.Next.After.class, pacing.next(BEHIND, Duration.ofMillis(50)));
        assertEquals(Duration.ofMillis(200), next.delay());
        assertInstanceOf(AnimationPacing.Next.Now.class, pacing.next(BEHIND, Duration.ofMillis(250)));
        assertInstanceOf(
                AnimationPacing.Next.Now.class,
                pacing.next(WindowActivity.FOREGROUND, Duration.ZERO),
                "and a focused one is not held by it");
    }

    @Test
    @DisplayName("a desktop saving power holds animation to thirty frames a second")
    void saving() {
        assertEquals(PowerState.SAVING_FRAME_RATE, pacing.rate(SAVING));
        var next = assertInstanceOf(AnimationPacing.Next.After.class, pacing.next(SAVING, Duration.ZERO));
        assertEquals(Duration.ofNanos(1_000_000_000L / 30), next.delay());
    }

    @Test
    @DisplayName("two caps take the lower, and a higher inactive cap does not lift the saving one")
    void lowerWins() {
        pacing.inactiveRate(4);
        assertEquals(4, pacing.rate(new WindowActivity(false, false, true)));
        pacing.inactiveRate(60);
        assertEquals(30, pacing.rate(new WindowActivity(false, false, true)));
    }

    @Test
    @DisplayName("a negative or infinite cap is refused, and zero lifts it")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> pacing.inactiveRate(-1));
        assertThrows(IllegalArgumentException.class, () -> pacing.inactiveRate(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> pacing.inactiveRate(Double.NaN));
        pacing.inactiveRate(4);
        pacing.inactiveRate(0);
        assertEquals(0, pacing.inactiveRate());
    }
}
