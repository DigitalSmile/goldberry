package dev.goldberry.natives.desktop.shortcuts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// A listener hears each press once and each release once, after its press.
@DisplayName("a hotkey's state")
class HotkeyStateTest {

    private final List<String> heard = new ArrayList<>();
    private final HotkeyState state = new HotkeyState(new HotkeyListener() {
        @Override
        public void pressed(String id) {
            heard.add("+" + id);
        }

        @Override
        public void released(String id) {
            heard.add("-" + id);
        }
    });

    @Test
    @DisplayName("drops a repeated press and a release without a press")
    void dedupes() {
        state.released("talk");
        state.pressed("talk");
        state.pressed("talk");
        state.released("talk");
        state.released("talk");

        assertEquals(List.of("+talk", "-talk"), heard);
    }

    @Test
    @DisplayName("releases everything held, in the order it went down")
    void releasesAll() {
        state.pressed("b");
        state.pressed("a");

        state.releaseAll();

        assertEquals(List.of("+b", "+a", "-b", "-a"), heard);
        assertEquals(List.of(), state.held());
    }

    @Test
    @DisplayName("knows what is held")
    void held() {
        state.pressed("talk");

        assertTrue(state.isHeld("talk"));
        assertFalse(state.isHeld("mute"));
    }

    @Test
    @DisplayName("a listener that throws does not stop the next event")
    void survivesAThrowingListener() {
        var calls = new ArrayList<String>();
        var throwing = new HotkeyState(new HotkeyListener() {
            @Override
            public void pressed(String id) {
                calls.add(id);
                throw new IllegalStateException("the application's bug");
            }

            @Override
            public void released(String id) {
                calls.add("-" + id);
            }
        });

        throwing.pressed("talk");
        throwing.released("talk");

        assertEquals(List.of("talk", "-talk"), calls);
    }
}
