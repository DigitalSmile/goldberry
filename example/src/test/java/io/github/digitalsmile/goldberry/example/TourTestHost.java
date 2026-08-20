package io.github.digitalsmile.goldberry.example;

import io.github.digitalsmile.goldberry.FrameStats;
import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Overlay;
import io.github.digitalsmile.goldberry.Popup;
import io.github.digitalsmile.goldberry.Window;
import io.github.digitalsmile.goldberry.backend.LogicalRect;
import io.github.digitalsmile.goldberry.input.HitTest;
import io.github.digitalsmile.goldberry.text.Fonts;
import io.github.digitalsmile.goldberry.widget.Corner;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Optional;

/// A host that answers `anchor` from a captured frame — enough to draw a tour.
record TourTestHost(List<HitTest.Region> regions) implements Host {

    @Override
    public Optional<HitTest.Region> anchor(String id) {
        for (var region : regions) {
            if (region.owner() instanceof Element element && id.equals(element.id())) {
                return Optional.of(region);
            }
        }
        return Optional.empty();
    }

    @Override
    public Overlay fill(Widget widget) {
        return Overlay.filling(widget);
    }

    @Override
    public Overlay overlay(Widget widget, Corner corner) {
        return Overlay.of(widget, corner);
    }

    @Override
    public Overlay overlay(Widget widget, Corner corner, float margin) {
        return Overlay.of(widget, corner, margin);
    }

    @Override
    public void repaint() {
    }

    @Override
    public void restyle() {
    }

    @Override
    public void title(String title) {
    }

    @Override
    public void shortcut(io.github.digitalsmile.goldberry.input.Shortcut a, Runnable r) {
    }

    @Override
    public void shortcut(String accelerator, Runnable action) {
    }

    @Override
    public void removeShortcut(io.github.digitalsmile.goldberry.input.Shortcut accelerator) {
    }

    @Override
    public void removeShortcut(String accelerator) {
    }

    @Override
    public LogicalRect placeableArea() {
        return LogicalRect.of(0, 0, 900, 560);
    }

    @Override
    public Optional<Popup> popup(Widget content, String anchorId,
            io.github.digitalsmile.goldberry.Placement placement) {
        return Optional.empty();
    }

    @Override
    public Optional<Popup> popup(Widget content, LogicalRect anchor,
            io.github.digitalsmile.goldberry.Placement placement) {
        return Optional.empty();
    }

    @Override
    public Optional<Popup> popup(Widget content, LogicalRect anchor,
            io.github.digitalsmile.goldberry.Placement placement, float minimumWidth) {
        return Optional.empty();
    }

    @Override
    public Optional<Popup> popup(Widget content,
            io.github.digitalsmile.goldberry.backend.LogicalPoint at,
            io.github.digitalsmile.goldberry.backend.LogicalSize size) {
        return Optional.empty();
    }

    @Override
    public Optional<Popup> tooltip(Widget content,
            io.github.digitalsmile.goldberry.backend.LogicalPoint at,
            io.github.digitalsmile.goldberry.backend.LogicalSize size) {
        return Optional.empty();
    }

    @Override
    public io.github.digitalsmile.goldberry.backend.EventLoop.Timer after(
            java.time.Duration delay, Runnable action) {
        throw new UnsupportedOperationException("no event loop here");
    }

    @Override
    public void onContextMenu(io.github.digitalsmile.goldberry.ContextMenuHandler handler) {
    }

    @Override
    public FrameStats frames() {
        return FrameStats.none();
    }

    @Override
    public Fonts fonts() {
        throw new UnsupportedOperationException("no fonts here");
    }

    @Override
    public io.github.digitalsmile.goldberry.backend.Clipboard clipboard() {
        return io.github.digitalsmile.goldberry.backend.Clipboard.none();
    }

    @Override
    public void textInput(boolean active) {
    }

    @Override
    public Window window() {
        throw new UnsupportedOperationException("no window here");
    }
}
