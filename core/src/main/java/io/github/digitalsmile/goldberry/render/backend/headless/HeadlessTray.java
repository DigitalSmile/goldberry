package io.github.digitalsmile.goldberry.render.backend.headless;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.render.PixelBuffer;
import io.github.digitalsmile.goldberry.render.tray.BackendTray;
import io.github.digitalsmile.goldberry.render.tray.TrayItem;
import io.github.digitalsmile.goldberry.render.tray.TraySpec;

/// A tray with no desktop under it.
///
/// It exists for the reason [HeadlessPopup] does: the SPI's rules need somewhere
/// to be checked without a platform. But it earns its place twice over here,
/// because the real one is **unobservable** — the menu is drawn by a shell
/// Goldberry cannot query, and there is no golden image of a GTK popup. So this
/// is the only place a tray menu's *behaviour* can be tested at all: [#choose]
/// is the click the shell would have delivered, applied by the same rules SDL
/// applies — a checkbox toggles first and the handler is told the new state.
///
/// What a test asserts against is therefore the description plus what choosing a
/// row did, which is the whole of what the widget layer above is responsible for.
public final class HeadlessTray implements BackendTray {

    private final HeadlessBackend backend;
    private final List<TrayItem> items;

    /// The checkbox states, keyed by the path that reaches the row. Held here
    /// rather than in the item because a `TrayItem` is a value that a caller may
    /// hold and re-describe, and the *live* state belongs to the tray.
    private final List<String> checked = new ArrayList<>();

    private PixelBuffer icon;
    private String tooltip;
    private boolean closed;

    HeadlessTray(HeadlessBackend backend, TraySpec spec) {
        this.backend = backend;
        this.items = spec.items();
        this.icon = spec.icon();
        this.tooltip = spec.tooltip();
        markInitiallyChecked("", spec.items());
    }

    private void markInitiallyChecked(String prefix, List<TrayItem> rows) {
        for (var row : rows) {
            var path = row.kind() == TrayItem.Kind.SEPARATOR ? prefix : prefix + "/" + row.label();
            if (row.kind() == TrayItem.Kind.CHECKBOX && row.checked()) {
                checked.add(path);
            }
            markInitiallyChecked(path, row.children());
        }
    }

    /// The menu, as it was described.
    public List<TrayItem> items() {
        return items;
    }

    /// The icon currently set, or empty if the platform's default was asked for.
    public Optional<PixelBuffer> icon() {
        return Optional.ofNullable(icon);
    }

    /// The hover text currently set.
    public Optional<String> tooltip() {
        return Optional.ofNullable(tooltip);
    }

    /// Whether the checkbox at `path` is currently checked.
    ///
    /// @param path the labels from the top of the menu down, e.g.
    ///        `"Notifications"` or `"Recent/report.pdf"`
    public boolean isChecked(String path) {
        return checked.contains("/" + path);
    }

    /// Chooses a row, the way the desktop's shell would.
    ///
    /// A checkbox is toggled **before** its handler runs, which is the platform's
    /// order and not a convenience: SDL applies the click itself and the handler
    /// reads the result, so a test that toggled afterwards would be testing an
    /// order no platform uses.
    ///
    /// @param path the labels from the top of the menu down, separated by `/`
    /// @throws IllegalArgumentException if no row is reached by that path
    /// @throws IllegalStateException if the row is disabled — the shell would not
    ///         have offered it, so choosing it in a test is a test asserting
    ///         something that cannot happen
    public void choose(String path) {
        Objects.requireNonNull(path, "path");
        if (closed) {
            throw new IllegalStateException("this tray has been closed");
        }
        backend.requireUiThread();

        var row = find(items, "", path);
        if (row == null) {
            throw new IllegalArgumentException("no tray row at " + path + "; the menu has " + paths(items, ""));
        }
        if (!row.enabled()) {
            throw new IllegalStateException(
                    "the tray row at " + path + " is disabled, so the shell would never offer it");
        }
        var full = "/" + path;
        var nowChecked = false;
        if (row.kind() == TrayItem.Kind.CHECKBOX) {
            nowChecked = !checked.remove(full);
            if (nowChecked) {
                checked.add(full);
            }
        }
        row.choose(nowChecked);
    }

    private static @Nullable TrayItem find(List<TrayItem> rows, String prefix, String path) {
        for (var row : rows) {
            if (row.kind() == TrayItem.Kind.SEPARATOR) {
                continue;
            }
            var here = prefix.isEmpty() ? row.label() : prefix + "/" + row.label();
            if (here.equals(path)) {
                return row;
            }
            var deeper = find(row.children(), here, path);
            if (deeper != null) {
                return deeper;
            }
        }
        return null;
    }

    private static List<String> paths(List<TrayItem> rows, String prefix) {
        var all = new ArrayList<String>();
        for (var row : rows) {
            if (row.kind() == TrayItem.Kind.SEPARATOR) {
                continue;
            }
            var here = prefix.isEmpty() ? row.label() : prefix + "/" + row.label();
            all.add(here);
            all.addAll(paths(row.children(), here));
        }
        return all;
    }

    @Override
    public void icon(PixelBuffer value) {
        backend.requireUiThread();
        requireOpen();
        this.icon = value;
    }

    @Override
    public void tooltip(String value) {
        backend.requireUiThread();
        requireOpen();
        this.tooltip = value;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        backend.requireUiThread();
        closed = true;
        backend.forget(this);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("this tray has been closed");
        }
    }

    @Override
    public String toString() {
        return "HeadlessTray[" + items.size() + " rows" + (closed ? ", closed" : "") + "]";
    }
}
