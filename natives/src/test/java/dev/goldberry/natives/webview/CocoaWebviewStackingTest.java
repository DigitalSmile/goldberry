package dev.goldberry.natives.webview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import java.util.EnumSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import dev.goldberry.natives.desktop.macos.ObjC;
import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.SdlVideo;
import dev.goldberry.natives.sdl.gpu.GpuDeviceRequirement;
import dev.goldberry.natives.sdl.gpu.GpuTestLauncher;
import dev.goldberry.natives.sdl.window.NativeWindowHandle;
import dev.goldberry.natives.sdl.window.SdlWindowFlag;

/// Exercises the native view order under the first-thread GPU launcher on macOS.
@EnabledOnOs(OS.MAC)
@Tag(GpuTestLauncher.TAG)
class CocoaWebviewStackingTest {

    @Test
    @DisplayName("a WKWebView stays above the Metal view after a claim and reclaim")
    void keepsThePageAboveMetal() {
        WebviewRequirement.enforce();
        var device = GpuDeviceRequirement.enforce();
        try (device) {
            assertEquals("metal", device.driver());
            var video = SdlVideo.get();
            var window = video.createWindow("Cocoa page stacking", 120, 80, EnumSet.of(SdlWindowFlag.HIDDEN));
            try {
                var handle = video.nativeHandle(window).orElseThrow();
                assertEquals(NativeWindowHandle.Kind.COCOA, handle.kind());
                var objc = ObjC.get().orElseThrow();
                var content = objc.send(MemorySegment.ofAddress(handle.value()), "contentView");
                try (var page = Webview.openEmbedded(false, handle.value(), handle.kind(), 10, 10, 64, 48)
                        .orElseThrow()) {
                    page.html("<p>Embedded above Metal</p>");
                    for (var attempt = 0; attempt < 2; attempt++) {
                        try (var claimed = device.claimWindow(window)) {
                            assertEquals(window, claimed.window());
                            assertTrue(
                                    objc.autoreleased(() -> {
                                        var subviews = objc.send(content, "subviews");
                                        var metal = objc.send(subviews, "firstObject");
                                        var web = objc.send(subviews, "lastObject");
                                        return objc.sendBoolean(
                                                        objc.send(metal, "layer"),
                                                        "isKindOfClass:",
                                                        objc.cls("CAMetalLayer"))
                                                && objc.sendBoolean(web, "isKindOfClass:", objc.cls("WKWebView"));
                                    }),
                                    "the first subview owns the Metal layer and the last is the page");
                        }
                    }
                }
            } finally {
                video.destroyWindow(window);
            }
        } finally {
            Sdl.get().quit();
        }
    }
}
