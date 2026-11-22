package dev.goldberry.natives.desktop.calls;

import java.util.Optional;

import dev.goldberry.natives.desktop.macos.ObjC;

/// Whether macOS's Low Power Mode is on:
/// `NSProcessInfo.processInfo.isLowPowerModeEnabled`, macOS 12 and later.
///
/// Through [ObjC], the runtime bridge the menu bar and notifications already
/// use. A system older than the property answers empty rather than false: it
/// has no such mode, and "off" would be a claim about one.
public final class MacLowPower {

    private MacLowPower() {}

    /// Whether Low Power Mode is on, or empty where the system cannot say.
    public static Optional<Boolean> read() {
        try {
            return ObjC.get()
                    .flatMap(objc -> objc.autoreleased(() -> {
                        var processInfo = objc.send(objc.cls("NSProcessInfo"), "processInfo");
                        if (processInfo.address() == 0
                                || !objc.sendBoolean(
                                        processInfo, "respondsToSelector:", objc.sel("isLowPowerModeEnabled"))) {
                            return Optional.<Boolean>empty();
                        }
                        return Optional.of(objc.sendBoolean(processInfo, "isLowPowerModeEnabled"));
                    }));
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            return Optional.empty();
        }
    }
}
