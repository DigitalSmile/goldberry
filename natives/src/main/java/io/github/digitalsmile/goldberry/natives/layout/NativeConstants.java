package io.github.digitalsmile.goldberry.natives.layout;

import java.util.ArrayList;
import java.util.List;

import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendEnum;
import io.github.digitalsmile.goldberry.natives.harfbuzz.enums.HarfBuzzEnum;
import io.github.digitalsmile.goldberry.natives.md4c.enums.Md4cEnum;
import io.github.digitalsmile.goldberry.natives.platform.NativeCapability;
import io.github.digitalsmile.goldberry.natives.sdl.desktop.SdlSystemCursor;
import io.github.digitalsmile.goldberry.natives.sdl.desktop.SdlSystemTheme;
import io.github.digitalsmile.goldberry.natives.sdl.desktop.SdlTrayEntryFlag;
import io.github.digitalsmile.goldberry.natives.sdl.event.SdlEventType;
import io.github.digitalsmile.goldberry.natives.sdl.event.SdlWheelDirection;
import io.github.digitalsmile.goldberry.natives.sdl.window.SdlPixelFormat;
import io.github.digitalsmile.goldberry.natives.sdl.window.SdlWindowFlag;
import io.github.digitalsmile.goldberry.natives.yoga.style.YogaEnum;

/// The registry of C constants the Java side hard-codes.
///
/// Every entry is checked against the compiled library by [LayoutVerifier], the
/// same way [Layouts] entries are. **A constant used in a binding belongs here,
/// and its C expression belongs in `goldberry_shim.c`.**
public final class NativeConstants {

    private NativeConstants() {}

    /// Every constant that must agree with the compiled library.
    public static List<NativeConstant> registry() {
        var constants = new ArrayList<NativeConstant>();
        for (var event : SdlEventType.values()) {
            constants.add(new NativeConstant(event.nativeName(), event.value()));
        }
        for (var flag : SdlWindowFlag.values()) {
            constants.add(new NativeConstant(flag.nativeName(), flag.bit()));
        }
        for (var format : SdlPixelFormat.values()) {
            constants.add(new NativeConstant(format.nativeName(), format.value()));
        }
        for (var direction : SdlWheelDirection.values()) {
            constants.add(new NativeConstant(direction.nativeName(), direction.value()));
        }
        // Ordinals in an enum SDL has already inserted into the middle of once.
        for (var cursor : SdlSystemCursor.values()) {
            constants.add(new NativeConstant(cursor.nativeName(), cursor.value()));
        }
        // The desktop's light-or-dark setting, whose three values are ordinals in
        // a C enum and whose wrong reading starts an application in the wrong
        // theme with no error anywhere (ADR-0322).
        for (var theme : SdlSystemTheme.values()) {
            constants.add(new NativeConstant(theme.nativeName(), theme.value()));
        }
        // What this build of the platform layer can actually do. Bits of
        // libgoldberry's own, not an upstream's, and here for the same reason as
        // everything else: the Java enum hard-codes each one, and a bit that
        // disagrees reports the wrong capability rather than failing (ADR-0325,
        // `docs/gaps.md` G32).
        for (var capability : NativeCapability.values()) {
            constants.add(new NativeConstant(capability.nativeName(), capability.bit()));
        }
        // Tray entry kinds and their two optional bits, one of which is
        // 0x80000000 and is therefore the one a hand-copied `int` gets wrong.
        for (var flag : SdlTrayEntryFlag.values()) {
            constants.add(new NativeConstant(flag.nativeName(), flag.bit()));
        }
        // Every enumerator of every Yoga enum the bindings model. The list comes
        // from the sealed interface rather than from here, so an enum added to
        // the bindings is checked without this method being touched.
        for (var value : YogaEnum.all()) {
            constants.add(new NativeConstant(value.nativeName(), value.nativeValue()));
        }
        // Blend2D, by the same route and for the same reason.
        for (var value : BlendEnum.all()) {
            constants.add(new NativeConstant(value.nativeName(), value.nativeValue()));
        }
        // And HarfBuzz, whose direction values have gaps in them -- which is
        // exactly the sort of thing that is wrong until something checks.
        for (var value : HarfBuzzEnum.all()) {
            constants.add(new NativeConstant(value.nativeName(), value.nativeValue()));
        }
        // And md4c, whose enums have gained values in the middle before now -- a
        // block type off by one renders a heading as a block quote (ADR-0294).
        for (var value : Md4cEnum.all()) {
            constants.add(new NativeConstant(value.nativeName(), value.nativeValue()));
        }
        return List.copyOf(constants);
    }
}
