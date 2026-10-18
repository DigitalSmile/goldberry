package dev.goldberry;

import java.util.List;

import dev.goldberry.css.value.Shadow;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.render.model.LogicalSize;

/// The transparent room around a popup's panel that its shadow is drawn in, in
/// logical pixels on each side.
///
/// A popup window is its panel's size plus this, and the panel is painted inset
/// by it. Everything the toolkit tells an application about a popup, its offset,
/// its bounds and its size, is still the panel's: placement measures the panel
/// and not the margin, so a menu sits as close to its anchor with a shadow as
/// without one. A press in the margin is a press outside the popup.
///
/// Whole pixels, rounded up, so the panel lands on the pixel grid it would
/// have landed on without a margin and its edges do not blur.
record PopupMargin(int left, int top, int right, int bottom) {

    /// No margin: what every popup has where transparency is not honoured, and
    /// what a panel with no shadow has everywhere.
    static final PopupMargin NONE = new PopupMargin(0, 0, 0, 0);

    PopupMargin {
        if (left < 0 || top < 0 || right < 0 || bottom < 0) {
            throw new IllegalArgumentException(
                    "a margin is not negative, and " + left + "," + top + "," + right + "," + bottom + " is");
        }
    }

    /// The room `shadows` reach past the box that casts them: on each side, the
    /// farthest any outer shadow reaches, rounded up. An inset shadow reaches
    /// nothing outside, and neither does one with no ink.
    static PopupMargin of(List<Shadow> shadows) {
        double left = 0;
        double top = 0;
        double right = 0;
        double bottom = 0;
        for (var shadow : shadows) {
            if (shadow.inset() || !shadow.hasInk()) {
                continue;
            }
            left = Math.max(left, shadow.outsetLeft());
            top = Math.max(top, shadow.outsetTop());
            right = Math.max(right, shadow.outsetRight());
            bottom = Math.max(bottom, shadow.outsetBottom());
        }
        return new PopupMargin(
                (int) Math.ceil(left), (int) Math.ceil(top), (int) Math.ceil(right), (int) Math.ceil(bottom));
    }

    boolean isNone() {
        return left == 0 && top == 0 && right == 0 && bottom == 0;
    }

    /// The window's size for a panel of `panel`.
    LogicalSize around(LogicalSize panel) {
        return new LogicalSize(panel.width() + left + right, panel.height() + top + bottom);
    }

    /// The panel's size in a window of `window`.
    LogicalSize within(LogicalSize window) {
        return new LogicalSize(Math.max(0, window.width() - left - right), Math.max(0, window.height() - top - bottom));
    }

    /// Where the window goes for a panel whose top-left is `panel`.
    LogicalPoint windowAt(LogicalPoint panel) {
        return new LogicalPoint(panel.x() - left, panel.y() - top);
    }

    /// Where the panel is for a window whose top-left is `window`.
    LogicalPoint panelAt(LogicalPoint window) {
        return new LogicalPoint(window.x() + left, window.y() + top);
    }

    /// The panel's rectangle in a window of `window`, in the window's own
    /// coordinates.
    LogicalRect panelIn(LogicalSize window) {
        return new LogicalRect(new LogicalPoint(left, top), within(window));
    }
}
