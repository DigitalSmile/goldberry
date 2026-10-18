package dev.goldberry.render.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.goldberry.natives.sdl.window.SdlWindowFlag;
import dev.goldberry.render.popup.PopupKind;

/// No popup takes the platform's keyboard, of any kind.
///
/// SDL moves the keyboard to a popup on show, and back to its parent on hide,
/// only when the popup is focusable, on every driver it ships. So this flag is
/// the whole of why a popup never needs to give the focus back and why opening
/// one sends the owner no focus-lost. The headless backend has no window flags,
/// which is why it is checked here, where they are chosen.
class PopupFlagsTest {

    @ParameterizedTest
    @EnumSource(PopupKind.class)
    void neverFocusable(PopupKind kind) {
        var flags = PopupFlags.of(kind);

        assertTrue(flags.contains(SdlWindowFlag.NOT_FOCUSABLE), kind + " could take the keyboard");
        assertTrue(flags.contains(SdlWindowFlag.HIDDEN), "shown only once its first frame is drawn");
    }

    @ParameterizedTest
    @EnumSource(PopupKind.class)
    void exactlyOneKindFlag(PopupKind kind) {
        var flags = PopupFlags.of(kind);
        var kinds = flags.stream()
                .filter(Set.of(SdlWindowFlag.POPUP_MENU, SdlWindowFlag.TOOLTIP)::contains)
                .toList();

        assertEquals(1, kinds.size(), "SDL refuses a popup that is both, or neither: " + kinds);
        assertEquals(kind == PopupKind.TOOLTIP ? SdlWindowFlag.TOOLTIP : SdlWindowFlag.POPUP_MENU, kinds.getFirst());
    }
}
