package dev.goldberry.example.nativeimage;

import dev.goldberry.media.MediaCapabilities;
import dev.goldberry.render.web.WebViewEngine;
import dev.goldberry.vector.Svg;

/// Checks the native libraries that the Showcase's startup screen can live without.
public final class NativeLibraryCheck {

    private NativeLibraryCheck() {}

    /// Loads the libraries through the same entry points as the Showcase screens.
    public static void verify() {
        if (!MediaCapabilities.nativesAvailable()) {
            throw new IllegalStateException("libgoldberry-media is unavailable: "
                    + MediaCapabilities.nativesUnavailableReason().orElse("unknown reason"));
        }
        if (!WebViewEngine.isAvailable()) {
            throw new IllegalStateException("libgoldberry-webview is unavailable");
        }
        try (var svg = Svg.parse("""
                <svg xmlns="http://www.w3.org/2000/svg" width="1" height="1">
                  <rect width="1" height="1"/>
                </svg>
                """)) {
            if (svg.naturalSize().isEmpty()) {
                throw new IllegalStateException("libgoldberry-vector could not size the test SVG");
            }
        }
    }
}
