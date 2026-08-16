package io.github.digitalsmile.goldberry.natives.layout;

import io.github.digitalsmile.goldberry.natives.blend2d.BlendEnum;
import io.github.digitalsmile.goldberry.natives.harfbuzz.HarfBuzzEnum;
import io.github.digitalsmile.goldberry.natives.sdl.SdlEventType;
import io.github.digitalsmile.goldberry.natives.sdl.SdlPixelFormat;
import io.github.digitalsmile.goldberry.natives.sdl.SdlSystemCursor;
import io.github.digitalsmile.goldberry.natives.sdl.SdlWheelDirection;
import io.github.digitalsmile.goldberry.natives.sdl.SdlWindowFlag;
import io.github.digitalsmile.goldberry.natives.yoga.YogaEnum;
import java.util.ArrayList;
import java.util.List;

/// The registry of C constants the Java side hard-codes.
///
/// Every entry is checked against the compiled library by [LayoutVerifier], the
/// same way [Layouts] entries are. **A constant used in a binding belongs here,
/// and its C expression belongs in `goldberry_shim.c`.**
public final class NativeConstants {

    private NativeConstants() {
    }

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
        return List.copyOf(constants);
    }
}
