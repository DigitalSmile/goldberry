package io.github.digitalsmile.goldberry.example;

import io.github.digitalsmile.goldberry.stats.FrameStats;
import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Overlay;
import io.github.digitalsmile.goldberry.Popup;
import io.github.digitalsmile.goldberry.Window;
import io.github.digitalsmile.goldberry.render.Clipboard;
import io.github.digitalsmile.goldberry.render.event.EventLoop;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.render.model.LogicalPoint;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.style.Corner;
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
    public void shortcut(io.github.digitalsmile.goldberry.input.key.Shortcut a, Runnable r) {
    }

    @Override
    public void shortcut(String accelerator, Runnable action) {
    }

    @Override
    public void removeShortcut(io.github.digitalsmile.goldberry.input.key.Shortcut accelerator) {
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
    public Optional<Popup> popup(Widget content, LogicalRect anchor,
            io.github.digitalsmile.goldberry.Placement placement, float minimumWidth,
            Fit fit) {
        return Optional.empty();
    }

    @Override
    public Optional<Popup> popup(Widget content,
            LogicalPoint at,
            LogicalSize size) {
        return Optional.empty();
    }

    @Override
    public Optional<Popup> tooltip(Widget content,
            LogicalPoint at,
            LogicalSize size) {
        return Optional.empty();
    }

    @Override
    public EventLoop.Timer after(
            java.time.Duration delay, Runnable action) {
        throw new UnsupportedOperationException("no event loop here");
    }

    @Override
    public void onContextMenu(io.github.digitalsmile.goldberry.ContextMenuHandler handler) {
    }

    /// No tree behind this host, so nothing to focus — a tour never asks.
    @Override
    public boolean focus(String id, boolean fromKeyboard) {
        return false;
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
    public Clipboard clipboard() {
        return Clipboard.none();
    }

    /// No tray: this host exists to drive a `tour` and has no desktop under it,
    /// which is the answer a session without a notification area gives anyway.
    @Override
    public java.util.Optional<io.github.digitalsmile.goldberry.render.tray.BackendTray> tray(
            io.github.digitalsmile.goldberry.render.tray.TraySpec spec) {
        return java.util.Optional.empty();
    }

    @Override
    public void textInput(boolean active) {
    }

    @Override
    public Window window() {
        throw new UnsupportedOperationException("no window here");
    }

    @Override
    public Optional<Popup> attachedPopup(Widget content, LogicalRect anchor,
            io.github.digitalsmile.goldberry.Placement placement, float minimumWidth,
            Fit fit) {
        return Optional.empty();
    }
}
