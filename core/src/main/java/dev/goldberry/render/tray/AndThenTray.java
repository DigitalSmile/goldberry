package dev.goldberry.render.tray;

import java.util.List;
import java.util.Objects;

import dev.goldberry.render.PixelBuffer;

/// A tray whose rows all run one more thing after their own handler, the rows
/// it is given later as well as the ones it was opened with.
///
/// What a host hands an application: a tray row is chosen with no event
/// behind it, so nothing asks for a frame unless the row does, and
/// [TraySpec#andThen] only reaches the rows the tray opened with.
public final class AndThenTray implements BackendTray {

    private final BackendTray tray;
    private final Runnable after;

    private AndThenTray(BackendTray tray, Runnable after) {
        this.tray = tray;
        this.after = after;
    }

    /// `tray`, opened from a spec already given [TraySpec#andThen] `after`, so
    /// that the rows [#rows] is given later run it too.
    public static BackendTray of(BackendTray tray, Runnable after) {
        return new AndThenTray(Objects.requireNonNull(tray, "tray"), Objects.requireNonNull(after, "after"));
    }

    @Override
    public void rows(List<TrayItem> rows) {
        tray.rows(rows.stream().map(row -> row.andThen(after)).toList());
    }

    @Override
    public void icon(PixelBuffer icon) {
        tray.icon(icon);
    }

    @Override
    public void tooltip(String tooltip) {
        tray.tooltip(tooltip);
    }

    @Override
    public boolean isClosed() {
        return tray.isClosed();
    }

    @Override
    public void close() {
        tray.close();
    }

    @Override
    public String toString() {
        return tray.toString();
    }
}
