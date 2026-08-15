package io.github.digitalsmile.goldberry.natives.layout;

import io.github.digitalsmile.goldberry.natives.sdl.SdlEventType;
import io.github.digitalsmile.goldberry.natives.sdl.SdlPixelFormat;
import io.github.digitalsmile.goldberry.natives.sdl.SdlWindowFlag;
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
        return List.copyOf(constants);
    }
}
