package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

    /// What the property is set to and the delay it becomes — `null` in the
    /// second column for the property unset.
    ///
    /// A minute of a menu floating over the application the user switched to is
    /// not a tuning flag, it is a bug being configured in, so the ceiling is two
    /// seconds. A malformed flag falls back to the default rather than stopping
    /// an application starting, which is the rule `goldberry.frame.rate` already
    /// follows.
    static Stream<Arguments> settings() {
        return Stream.of(
                arguments("60 ms unless something says otherwise", null, Duration.ofMillis(60)),
                arguments("a driver that delivers the pair slowly can be given longer", "250", Duration.ofMillis(250)),
                arguments("zero is clamped, because a delay of none is the bug", "0", Duration.ofMillis(1)),
                arguments("and so is a negative", "-40", Duration.ofMillis(1)),
                arguments("two seconds is as far as this goes", "60000", Duration.ofSeconds(2)),
                arguments("nonsense is ignored rather than fatal", "soon", Duration.ofMillis(60)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("settings")
    @DisplayName("the delay is 60 ms unless told otherwise, clamped between 1 ms and two seconds")
    void focusSettle(String what, String property, Duration expected) {
        if (property == null) {
            System.clearProperty(Launcher.SETTLE_PROPERTY);
        } else {
            System.setProperty(Launcher.SETTLE_PROPERTY, property);
        }

        assertEquals(expected, Launcher.focusSettle(), what);
    }
}
