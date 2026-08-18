package io.github.digitalsmile.goldberry.backend;

import java.util.Objects;

/// What to open a popup as — [Backend#createPopup]'s argument.
///
/// The counterpart of [WindowSpec], and deliberately much smaller. A popup has no
/// title (nothing shows one), is never resizable by the user, and is never
/// decorated: it is a rectangle of the application's own drawing, placed relative
/// to the window that owns it.
///
/// **The position is in the owner window's logical coordinates**, with the origin
/// at the owner's top-left — the same coordinate space a hit test reports in, so
/// anchoring a menu under the button that opened it is the button's own rectangle
/// and no conversion. A popup may extend beyond the owner's bounds, which is the
/// entire reason it is a platform window rather than something in the in-window
/// overlay layer ([ADR-0100](../../../../../../book/src/adr/0100-a-window-has-a-layer-above-its-application.md)).
///
/// Nothing here decides *where a menu near a screen edge should flip to*. That is
/// placement policy — it needs the display's work area, the anchor rectangle and a
/// preference order — and it belongs with the widget that has all three. This
/// record is the platform request that policy ends in.
///
/// @param position where the popup's top-left sits, in the owner's coordinates
/// @param size     the popup's logical size
/// @param kind     what the platform should treat it as
public record PopupSpec(LogicalPoint position, LogicalSize size, PopupKind kind) {

    public PopupSpec {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(kind, "kind");
        if (size.width() <= 0 || size.height() <= 0) {
            throw new IllegalArgumentException(
                    "a popup needs a positive size, and " + size + " has none");
        }
    }

    /// A menu-kind popup at `position`.
    public static PopupSpec of(LogicalPoint position, LogicalSize size) {
        return new PopupSpec(position, size, PopupKind.MENU);
    }

    /// A tooltip-kind popup at `position`.
    public static PopupSpec tooltip(LogicalPoint position, LogicalSize size) {
        return new PopupSpec(position, size, PopupKind.TOOLTIP);
    }
}
