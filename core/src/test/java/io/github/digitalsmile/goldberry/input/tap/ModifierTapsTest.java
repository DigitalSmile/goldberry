package io.github.digitalsmile.goldberry.input.tap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Mod;

/// A tap of a bare modifier — §8's "`Alt`-style keyboard activation" ([ADR-0223]).
///
/// The claims worth the most are the **negative** ones. A detector that fires on
/// a tap is easy; one that does not fire on `Alt+F`, on `Alt` held open, on
/// `Alt+Shift`, or on the `Alt` the window switcher took is the whole of what
/// makes the gesture safe to bind. Each of those is a way a user loses a
/// keystroke they meant for something else.
class ModifierTapsTest {

    /// SDL's `SDLK_LALT`. Written out rather than read off [ModifierKey] in the
    /// press/release helpers, because a test that asks the subject for the number
    /// it is being tested against asserts nothing.
    private static final int LEFT_ALT = 0x400000e2;

    private static final int RIGHT_ALT = 0x400000e6;
    private static final int LEFT_SHIFT = 0x400000e1;

    private ModifierTaps taps;
    private AtomicInteger fired;

    @BeforeEach
    void setUp() {
        taps = new ModifierTaps();
        fired = new AtomicInteger();
        taps.bind(ModifierKey.ALT, fired::incrementAndGet, this);
    }

    private void press(int keycode) {
        taps.keyPressed(keycode, false);
    }

    private void release(int keycode) {
        taps.keyReleased(keycode);
    }

    @Nested
    @DisplayName("the gesture")
    class Gesture {

        @Test
        @DisplayName("pressed and released with nothing in between is a tap")
        void tap() {
            press(LEFT_ALT);
            assertTrue(taps.isArmed(), "the modifier is down and nothing has spoiled it");
            release(LEFT_ALT);

            assertEquals(1, fired.get());
            assertFalse(taps.isArmed(), "and the gesture is over");
        }

        /// The fold [io.github.digitalsmile.goldberry.input.key.Modifiers#fromSdl]
        /// already does, at the one other place a keycode is read.
        @Test
        @DisplayName("either Alt key is the same modifier")
        void eitherHand() {
            press(RIGHT_ALT);
            release(RIGHT_ALT);

            assertEquals(1, fired.get());
        }

        /// Not a rule anybody would write down, and the one a user produces by
        /// accident: `Alt` down, `Alt` down again on the other hand, one up.
        @Test
        @DisplayName("a second modifier on top of the first is not a tap of either")
        void secondModifier() {
            press(LEFT_ALT);
            press(LEFT_SHIFT);
            release(LEFT_SHIFT);
            release(LEFT_ALT);

            assertEquals(0, fired.get());
        }

        @Test
        @DisplayName("releasing a different key than the one armed fires nothing")
        void differentKey() {
            press(LEFT_ALT);
            release(LEFT_SHIFT);

            assertEquals(0, fired.get());
            assertFalse(taps.isArmed(), "and the gesture is spoiled rather than still pending");
        }

        @Test
        @DisplayName("two taps in a row fire twice")
        void repeatable() {
            press(LEFT_ALT);
            release(LEFT_ALT);
            press(LEFT_ALT);
            release(LEFT_ALT);

            assertEquals(2, fired.get());
        }

        /// A release with no press before it — which is what the window delivers
        /// after focus came back from somewhere else.
        @Test
        @DisplayName("a release out of nowhere fires nothing")
        void unpairedRelease() {
            assertFalse(taps.keyReleased(LEFT_ALT));
            assertEquals(0, fired.get());
        }
    }

    @Nested
    @DisplayName("what spoils it")
    class Spoiling {

        /// The important one. `Alt+F` is how a user reaches the File menu on
        /// Windows, and a detector that fired on the `Alt` afterwards would open
        /// the bar a second time under their fingers.
        @Test
        @DisplayName("another key going down makes it a shortcut, not a tap")
        void anotherKey() {
            press(LEFT_ALT);
            press(Key.F.sdlKeycode());
            release(Key.F.sdlKeycode());
            release(LEFT_ALT);

            assertEquals(0, fired.get());
        }

        @Test
        @DisplayName("holding the modifier open is not tapping it")
        void held() {
            press(LEFT_ALT);
            taps.keyPressed(LEFT_ALT, true);
            release(LEFT_ALT);

            assertEquals(0, fired.get());
        }

        @Test
        @DisplayName("a pointer press or a wheel spoils it")
        void pointer() {
            press(LEFT_ALT);
            taps.interrupted();
            release(LEFT_ALT);

            assertEquals(0, fired.get());
        }

        /// The gesture that is *not* spoiled, and the reason [ModifierTaps] takes
        /// an explicit [ModifierTaps#interrupted()] rather than watching
        /// everything: moving the mouse while tapping a key interrupts nothing,
        /// so the window never calls it for motion.
        @Test
        @DisplayName("nothing else reaches the detector, so nothing else can spoil it")
        void onlyWhatIsTold() {
            press(LEFT_ALT);
            release(LEFT_ALT);

            assertEquals(1, fired.get());
        }
    }

    @Nested
    @DisplayName("the registry")
    class Registry {

        @Test
        @DisplayName("an unbound modifier arms nothing")
        void unbound() {
            press(LEFT_SHIFT);

            assertFalse(taps.isArmed());
            assertEquals(0, fired.get());
        }

        /// ADR-0220's rule, applied to the second registry that has owners.
        @Test
        @DisplayName("only the binder takes a tap back")
        void ownership() {
            assertFalse(taps.unbind(ModifierKey.ALT, new Object()), "somebody else's unbind is a no-op");
            assertEquals(java.util.Set.of(ModifierKey.ALT), taps.bound());

            assertTrue(taps.unbind(ModifierKey.ALT, ModifierTapsTest.this));
            assertTrue(taps.bound().isEmpty());
        }

        @Test
        @DisplayName("unbinding whoever bound it also disarms a tap in progress")
        void unbindDisarms() {
            press(LEFT_ALT);
            taps.unbind(ModifierKey.ALT);
            release(LEFT_ALT);

            assertEquals(0, fired.get());
        }

        @Test
        @DisplayName("binding twice replaces rather than doubles")
        void rebind() {
            var second = new AtomicInteger();
            taps.bind(ModifierKey.ALT, second::incrementAndGet, this);

            press(LEFT_ALT);
            release(LEFT_ALT);

            assertEquals(0, fired.get());
            assertEquals(1, second.get());
        }

        @Test
        @DisplayName("nothing bound means nothing is even armed")
        void empty() {
            var idle = new ModifierTaps();
            idle.keyPressed(LEFT_ALT, false);

            assertFalse(idle.isArmed());
            assertFalse(idle.keyReleased(LEFT_ALT));
        }

        @Test
        @DisplayName("a null modifier or action is refused at the door")
        void nulls() {
            assertThrows(NullPointerException.class, () -> taps.bind(ModifierKey.ALT, null, this));
            assertThrows(NullPointerException.class, () -> taps.bind(null, () -> {}, this));
        }
    }

    @Nested
    @DisplayName("the vocabulary")
    class Vocabulary {

        /// The fact that makes this package necessary rather than a convenience:
        /// every modifier keycode translates to [Key#UNKNOWN], so a detector fed
        /// translated keys could not tell `Alt` from any other key it does not
        /// name.
        @Test
        @DisplayName("Key names no modifier, which is why a tap is read from the keycode")
        void keyNamesNoModifier() {
            for (var modifier : ModifierKey.values()) {
                assertEquals(Key.UNKNOWN, Key.fromSdl(modifier.leftKeycode()), modifier + " left");
                assertEquals(Key.UNKNOWN, Key.fromSdl(modifier.rightKeycode()), modifier + " right");
            }
        }

        @Test
        @DisplayName("each modifier key carries the bit it sets while it is held")
        void modifiers() {
            assertEquals(Mod.ALT, ModifierKey.ALT.modifier());
            assertEquals(Mod.CTRL, ModifierKey.CONTROL.modifier());
            assertEquals(Mod.SHIFT, ModifierKey.SHIFT.modifier());
            assertEquals(Mod.META, ModifierKey.META.modifier());
        }

        @Test
        @DisplayName("both hands of every modifier are recognised, and nothing else is")
        void lookup() {
            for (var modifier : ModifierKey.values()) {
                assertSame(modifier, ModifierKey.ofSdl(modifier.leftKeycode()).orElseThrow());
                assertSame(modifier, ModifierKey.ofSdl(modifier.rightKeycode()).orElseThrow());
                assertTrue(modifier.matches(modifier.leftKeycode()));
                assertTrue(modifier.matches(modifier.rightKeycode()));
            }
            assertEquals(Optional.empty(), ModifierKey.ofSdl(Key.F.sdlKeycode()));
            assertEquals(Optional.empty(), ModifierKey.ofSdl(0));
            assertFalse(ModifierKey.ALT.matches(LEFT_SHIFT));
        }
    }
}
