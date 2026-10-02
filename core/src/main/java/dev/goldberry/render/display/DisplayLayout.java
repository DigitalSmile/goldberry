package dev.goldberry.render.display;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.render.model.LogicalSize;

/// The displays there are, and the rules for putting a window on them.
///
/// A window remembered on a display that is gone, or at a position that is now
/// off every screen, must still open somewhere the user can see it. That rule
/// lives here, once, for the position a window opens at and for every
/// [dev.goldberry.Window#move] after it:
///
/// - a window is **clamped** into the usable bounds of the display it is mostly
///   on, or of the nearest one when it is on none;
/// - one too big for that area keeps its top-left corner in it, so its title bar
///   can still be reached.
///
/// An empty layout — a platform that names no display — clamps nothing.
///
/// Read more: [Displays](https://goldberry.dev/docs/guide/windows.html#displays).
///
/// @param displays the displays, the primary one first where the platform says
public record DisplayLayout(List<Display> displays) {

    /// No displays at all: every position passes through as it is.
    public static final DisplayLayout NONE = new DisplayLayout(List.of());

    public DisplayLayout {
        displays = List.copyOf(Objects.requireNonNull(displays, "displays"));
    }

    /// Whether the platform named no display.
    public boolean isEmpty() {
        return displays.isEmpty();
    }

    /// The display marked primary, or the first when none is.
    public Optional<Display> primary() {
        for (var display : displays) {
            if (display.primary()) {
                return Optional.of(display);
            }
        }
        return displays.stream().findFirst();
    }

    /// The first display with this name.
    ///
    /// The name is what survives a restart; the id does not. Two identical
    /// monitors share a name, and then the first one wins.
    public Optional<Display> named(String name) {
        Objects.requireNonNull(name, "name");
        return displays.stream().filter(display -> display.name().equals(name)).findFirst();
    }

    /// The display with this id, for as long as it is connected.
    public Optional<Display> byId(long id) {
        return displays.stream().filter(display -> display.id() == id).findFirst();
    }

    /// The display most of `window` is on, or empty when it is on none.
    public Optional<Display> under(LogicalRect window) {
        Objects.requireNonNull(window, "window");
        Display best = null;
        var bestArea = 0f;
        for (var display : displays) {
            var area = overlap(window, display.bounds());
            if (area > bestArea) {
                best = display;
                bestArea = area;
            }
        }
        return Optional.ofNullable(best);
    }

    /// [#under], or — for a window on no display at all — the display whose
    /// edge is nearest its centre.
    public Optional<Display> nearest(LogicalRect window) {
        var under = under(window);
        if (under.isPresent()) {
            return under;
        }
        var cx = window.left() + window.width() / 2;
        var cy = window.top() + window.height() / 2;
        Display best = null;
        var bestDistance = Float.MAX_VALUE;
        for (var display : displays) {
            var bounds = display.bounds();
            var dx = Math.max(0, Math.max(bounds.left() - cx, cx - bounds.right()));
            var dy = Math.max(0, Math.max(bounds.top() - cy, cy - bounds.bottom()));
            var distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                best = display;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    /// Where `window`'s top-left has to be for all of it to be on a display.
    ///
    /// The display it is mostly on, or the nearest; inside that display's usable
    /// bounds. Unchanged when there are no displays to clamp against.
    public LogicalPoint clamp(LogicalRect window) {
        Objects.requireNonNull(window, "window");
        return nearest(window)
                .map(display -> fit(window.origin(), window.size(), display.usableBounds()))
                .orElse(window.origin());
    }

    /// Where a window of `size` opens, or empty to leave it to the platform.
    ///
    /// In order:
    ///
    /// 1. at `position`, clamped, when that is on a display that exists;
    /// 2. centred on the display called `display`, when there is one;
    /// 3. centred on the primary display, when a position was asked for and
    ///    is on no display — a window remembered on a monitor that is gone;
    /// 4. otherwise wherever the platform puts a new window.
    ///
    /// With no displays to check against, a position is taken as it is.
    ///
    /// @param size     the window's size
    /// @param position its top-left in desktop coordinates, or null
    /// @param display  the [Display#name] it should open on, or null
    public Optional<LogicalPoint> opening(LogicalSize size, @Nullable LogicalPoint position, @Nullable String display) {
        Objects.requireNonNull(size, "size");
        if (isEmpty()) {
            return Optional.ofNullable(position);
        }
        if (position != null) {
            var window = new LogicalRect(position, size);
            if (under(window).isPresent()) {
                return Optional.of(clamp(window));
            }
        }
        if (display != null) {
            var named = named(display);
            if (named.isPresent()) {
                return Optional.of(centred(size, named.get().usableBounds()));
            }
        }
        if (position != null) {
            return primary().map(primary -> centred(size, primary.usableBounds()));
        }
        return Optional.empty();
    }

    /// The top-left that centres `size` in `area`, kept inside it.
    public static LogicalPoint centred(LogicalSize size, LogicalRect area) {
        var at = new LogicalPoint(
                area.left() + (area.width() - size.width()) / 2, area.top() + (area.height() - size.height()) / 2);
        return fit(at, size, area);
    }

    /// `at`, moved the least distance that puts `size` inside `area` — or, when
    /// it cannot fit, its top-left corner at `area`'s.
    private static LogicalPoint fit(LogicalPoint at, LogicalSize size, LogicalRect area) {
        var x = Math.clamp(at.x(), area.left(), Math.max(area.left(), area.right() - size.width()));
        var y = Math.clamp(at.y(), area.top(), Math.max(area.top(), area.bottom() - size.height()));
        return new LogicalPoint(x, y);
    }

    private static float overlap(LogicalRect a, LogicalRect b) {
        var w = Math.min(a.right(), b.right()) - Math.max(a.left(), b.left());
        var h = Math.min(a.bottom(), b.bottom()) - Math.max(a.top(), b.top());
        return w > 0 && h > 0 ? w * h : 0;
    }
}
