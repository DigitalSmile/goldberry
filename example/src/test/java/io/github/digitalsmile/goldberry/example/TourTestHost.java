package io.github.digitalsmile.goldberry.example;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Overlay;
import io.github.digitalsmile.goldberry.Popup;
import io.github.digitalsmile.goldberry.Window;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.render.Clipboard;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessFileDialogs;
import io.github.digitalsmile.goldberry.render.desktop.SystemTheme;
import io.github.digitalsmile.goldberry.render.event.EventLoop;
import io.github.digitalsmile.goldberry.render.model.LogicalPoint;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.stats.FrameStats;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Corner;

/// A host that answers `anchor` from a captured frame — enough to draw a tour.
///
/// It also carries **real, scriptable file dialogs**, for the reason its
/// clipboard is a real one: a card that asks the user for a path is only testable
/// against a host that can say what the user picked (ADR-0287).
record TourTestHost(List<HitTest.Region> regions, Clipboard board, HeadlessFileDialogs dialogs) implements Host {

    TourTestHost(List<HitTest.Region> regions) {
        this(regions, Clipboard.none());
    }

    TourTestHost(List<HitTest.Region> regions, Clipboard board) {
        this(regions, board, new HeadlessFileDialogs());
    }

    /// No desktop under a test, so no setting — which is a real answer and the one
    /// an application must have a default for ([ADR-0322]).
    @Override
    public Optional<SystemTheme> systemTheme() {
        return Optional.empty();
    }

    @Override
    public void onSystemThemeChanged(Consumer<SystemTheme> listener) {
        // Nothing ever changes it here.
    }

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
    public void repaint() {}

    @Override
    public void restyle() {}

    @Override
    public void title(String title) {}

    @Override
    public void shortcut(io.github.digitalsmile.goldberry.input.key.Shortcut a, Runnable r) {}

    @Override
    public void shortcut(io.github.digitalsmile.goldberry.input.key.Shortcut a, Runnable r, Object owner) {}

    @Override
    public void shortcut(String accelerator, Runnable action) {}

    @Override
    public void removeShortcut(io.github.digitalsmile.goldberry.input.key.Shortcut accelerator) {}

    @Override
    public void removeShortcut(io.github.digitalsmile.goldberry.input.key.Shortcut accelerator, Object owner) {}

    @Override
    public void removeShortcut(String accelerator) {}

    @Override
    public void modifierTap(
            io.github.digitalsmile.goldberry.input.tap.ModifierKey modifier, Runnable action, Object owner) {}

    @Override
    public void removeModifierTap(io.github.digitalsmile.goldberry.input.tap.ModifierKey modifier, Object owner) {}

    @Override
    public LogicalRect placeableArea() {
        return LogicalRect.of(0, 0, 900, 560);
    }

    @Override
    public Optional<Popup> popup(
            Widget content, String anchorId, io.github.digitalsmile.goldberry.Placement placement) {
        return Optional.empty();
    }

    @Override
    public Optional<Popup> popup(
            Widget content, LogicalRect anchor, io.github.digitalsmile.goldberry.Placement placement) {
        return Optional.empty();
    }

    @Override
    public Optional<Popup> popup(
            Widget content,
            LogicalRect anchor,
            io.github.digitalsmile.goldberry.Placement placement,
            float minimumWidth) {
        return Optional.empty();
    }

    @Override
    public Optional<Popup> popup(
            Widget content,
            LogicalRect anchor,
            io.github.digitalsmile.goldberry.Placement placement,
            float minimumWidth,
            Fit fit) {
        return Optional.empty();
    }

    @Override
    public Optional<Popup> popup(Widget content, LogicalPoint at, LogicalSize size) {
        return Optional.empty();
    }

    @Override
    public Optional<Popup> tooltip(Widget content, LogicalPoint at, LogicalSize size) {
        return Optional.empty();
    }

    @Override
    public EventLoop.Timer after(java.time.Duration delay, Runnable action) {
        throw new UnsupportedOperationException("no event loop here");
    }

    @Override
    public void onContextMenu(io.github.digitalsmile.goldberry.ContextMenuHandler handler) {}

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
        // Whatever the test handed over, which for a test about copying and
        // pasting is a real in-memory one (ADR-0286).
        return board;
    }

    /// Real ones, scripted — `dialogs().answerWith(...)` says what the user
    /// picked, and `dialogs().shown()` says what was asked for.
    @Override
    public HeadlessFileDialogs fileDialogs() {
        return dialogs;
    }

    /// No tray: this host exists to drive a `tour` and has no desktop under it,
    /// which is the answer a session without a notification area gives anyway.
    @Override
    public java.util.Optional<io.github.digitalsmile.goldberry.render.tray.BackendTray> tray(
            io.github.digitalsmile.goldberry.render.tray.TraySpec spec) {
        return java.util.Optional.empty();
    }

    /// No page, which is what a test host must answer: opening one would put a
    /// real WebKit window on the desktop of whoever ran the suite.
    @Override
    public java.util.Optional<io.github.digitalsmile.goldberry.render.web.BackendWebView> webView(
            io.github.digitalsmile.goldberry.render.web.WebViewSpec spec) {
        return java.util.Optional.empty();
    }

    /// No page inside the window either, for the reason above.
    @Override
    public java.util.Optional<io.github.digitalsmile.goldberry.render.web.BackendWebView> embeddedWebView(
            io.github.digitalsmile.goldberry.render.web.WebViewSpec spec,
            io.github.digitalsmile.goldberry.render.model.LogicalRect bounds) {
        return java.util.Optional.empty();
    }

    @Override
    public void textInput(boolean active) {}

    @Override
    public Window window() {
        throw new UnsupportedOperationException("no window here");
    }

    @Override
    public Optional<Popup> attachedPopup(
            Widget content,
            LogicalRect anchor,
            io.github.digitalsmile.goldberry.Placement placement,
            float minimumWidth,
            Fit fit) {
        return Optional.empty();
    }
}
