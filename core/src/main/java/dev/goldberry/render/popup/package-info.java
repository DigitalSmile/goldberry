/// The backend SPI's popup: a platform window owned by another, placed relative
/// to it and free of its bounds, which is what a menu, a dropdown and a tooltip
/// open in.
///
/// `PopupSpec` says what to open and `PopupKind` what the platform should treat
/// it as; `BackendPopup` is the open popup. Exported to every module, because a
/// widget that opens a popup states its request in these.
///
/// Null-marked: a parameter or return is non-null unless it says `@Nullable`.
///
/// Read more: [Windows, popups and the host](https://goldberry.dev/docs/guide/windows.html#in-a-window-of-its-own).
@NullMarked
package dev.goldberry.render.popup;

import org.jspecify.annotations.NullMarked;
