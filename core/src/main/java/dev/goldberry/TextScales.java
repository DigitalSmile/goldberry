package dev.goldberry;

import dev.goldberry.widget.WidgetRenderer;

/// The one rule a text scale follows wherever it is set: finite, and clamped
/// to the design system's range rather than refused.
final class TextScales {

    private TextScales() {}

    /// `factor` within [WidgetRenderer#MINIMUM_TEXT_SCALE] and
    /// [WidgetRenderer#MAXIMUM_TEXT_SCALE]; 1 for a value that is not finite,
    /// because an application's starting value is read from a settings file and
    /// must not stop a window opening.
    static double clamp(double factor) {
        return Double.isFinite(factor)
                ? Math.clamp(factor, WidgetRenderer.MINIMUM_TEXT_SCALE, WidgetRenderer.MAXIMUM_TEXT_SCALE)
                : 1;
    }

    /// `factor` clamped, for a call made at run time, where a value that is not
    /// finite is a bug in the caller.
    ///
    /// @throws IllegalArgumentException if `factor` is not finite
    static double requireFinite(double factor) {
        if (!Double.isFinite(factor)) {
            throw new IllegalArgumentException("a text scale must be a finite factor, not " + factor);
        }
        return clamp(factor);
    }
}
