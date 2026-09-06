package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// How long a focus-lost is disbelieved for, and why it can be told.
///
/// The platform reports focus **per window**: opening a popup sends a lost for
/// the owner and then a gained for the popup, in that order, so a menu that acted
/// on the first of the pair would close as it opened ([ADR-0144]). The way out is
/// to wait, and the wait is one number covering every driver — which is a
/// calibration nobody can derive, because the gap between the two events is the
/// compositor's own scheduling and nothing reports what it will be.
///
/// So it can be told instead. These are the rules around that: the default stands
/// when nothing says otherwise, a number is honoured, and neither nonsense nor
/// zero can turn the delay off — zero would act on the first of the pair every
/// driver sends, which is the exact bug the delay exists for.
class FocusSettleTest {

    @AfterEach
    void clearProperty() {
        System.clearProperty(Launcher.SETTLE_PROPERTY);
    }

    @Test
    @DisplayName("60 ms unless something says otherwise")
    void theDefault() {
        System.clearProperty(Launcher.SETTLE_PROPERTY);

        assertEquals(Duration.ofMillis(60), Launcher.focusSettle());
    }

    @Test
    @DisplayName("a driver that delivers the pair slowly can be given longer")
    void theOverride() {
        System.setProperty(Launcher.SETTLE_PROPERTY, "250");

        assertEquals(Duration.ofMillis(250), Launcher.focusSettle());
    }

    @Test
    @DisplayName("zero and negative are clamped, because a delay of none is the bug")
    void zeroIsClamped() {
        System.setProperty(Launcher.SETTLE_PROPERTY, "0");
        assertEquals(Duration.ofMillis(1), Launcher.focusSettle());

        System.setProperty(Launcher.SETTLE_PROPERTY, "-40");
        assertEquals(Duration.ofMillis(1), Launcher.focusSettle());
    }

    @Test
    @DisplayName("a menu that hangs about for two seconds is as far as this goes")
    void theCeiling() {
        System.setProperty(Launcher.SETTLE_PROPERTY, "60000");

        // A minute of a menu floating over the application the user switched to
        // is not a tuning flag, it is a bug being configured in.
        assertEquals(Duration.ofSeconds(2), Launcher.focusSettle());
    }

    @Test
    @DisplayName("nonsense is ignored rather than fatal")
    void nonsense() {
        System.setProperty(Launcher.SETTLE_PROPERTY, "soon");

        // A malformed tuning flag must not stop an application starting, which is
        // the rule `goldberry.frame.rate` already follows.
        assertEquals(Duration.ofMillis(60), Launcher.focusSettle());
    }
}
