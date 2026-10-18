package dev.goldberry.render.backend.sdl3;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.sdl.desktop.SdlTray;
import dev.goldberry.natives.sdl.desktop.SdlTrayIcon;
import dev.goldberry.natives.sdl.desktop.SdlTrayItem;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.tray.BackendTray;
import dev.goldberry.render.tray.TrayItem;
import dev.goldberry.render.tray.TraySpec;

/// The tray, on a real desktop.
///
/// Thin by design: the shape of a tray menu is the same on both sides of the
/// boundary, so this is a translation and nothing else — [TrayItem] to
/// [SdlTrayItem], row by row, handler and all. The duplication is deliberate and
/// is the rule every other platform type here follows (`PopupSpec` and
/// `SdlWindowFlag` are the same pair): `:core`'s SPI describes a *desktop*, and
/// the headless backend has no SDL to borrow a vocabulary from.
final class Sdl3Tray implements BackendTray {

    private final Sdl3Backend backend;
    private final SdlTray tray;

    private Sdl3Tray(Sdl3Backend backend, SdlTray tray) {
        this.backend = backend;
        this.tray = tray;
    }

    /// Opens one, or reports that this desktop has no tray.
    static Optional<BackendTray> open(Sdl3Backend backend, TraySpec spec) {
        return SdlTray.open(iconOf(spec.icon()), spec.tooltip(), itemsOf(backend, spec.items()))
                .<BackendTray>map(sdl -> new Sdl3Tray(backend, sdl));
    }

    private static List<SdlTrayItem> itemsOf(Sdl3Backend backend, List<TrayItem> items) {
        return items.stream().map(item -> itemOf(backend, item)).toList();
    }

    private static SdlTrayItem itemOf(Sdl3Backend backend, TrayItem item) {
        // On the UI thread, wherever the platform called back. SDL calls back
        // from inside its own pump on every platform it ships, which is the pump
        // this backend is in, so the handler runs at once; a click that arrives
        // on another thread waits for the next pump rather than racing it.
        var kind = switch (item.kind()) {
            case COMMAND -> SdlTrayItem.Kind.COMMAND;
            case CHECKBOX -> SdlTrayItem.Kind.CHECKBOX;
            case SUBMENU -> SdlTrayItem.Kind.SUBMENU;
            case SEPARATOR -> SdlTrayItem.Kind.SEPARATOR;
        };
        return new SdlTrayItem(
                kind,
                item.label(),
                item.enabled(),
                item.checked(),
                item.kind() == TrayItem.Kind.SUBMENU ? null : checked -> backend.onUiThread(() -> item.choose(checked)),
                itemsOf(backend, item.children()));
    }

    private static @Nullable SdlTrayIcon iconOf(@Nullable PixelBuffer icon) {
        if (icon == null) {
            return null;
        }
        return new SdlTrayIcon(icon.pixels(), icon.size().width(), icon.size().height(), icon.stride());
    }

    @Override
    public void icon(PixelBuffer value) {
        backend.requireUiThread();
        tray.icon(iconOf(value));
    }

    @Override
    public void tooltip(String value) {
        backend.requireUiThread();
        tray.tooltip(value);
    }

    @Override
    public void rows(List<TrayItem> rows) {
        backend.requireUiThread();
        tray.rows(itemsOf(backend, rows));
    }

    @Override
    public boolean isClosed() {
        return tray.isClosed();
    }

    @Override
    public void close() {
        if (tray.isClosed()) {
            return;
        }
        backend.requireUiThread();
        tray.close();
        backend.forget(this);
    }

    @Override
    public String toString() {
        return "Sdl3Tray[" + tray + "]";
    }
}
