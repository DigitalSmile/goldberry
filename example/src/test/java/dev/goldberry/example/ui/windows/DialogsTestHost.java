package dev.goldberry.example.ui.windows;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.ContextMenuHandler;
import dev.goldberry.Host;
import dev.goldberry.Overlay;
import dev.goldberry.Placement;
import dev.goldberry.Popup;
import dev.goldberry.Window;
import dev.goldberry.bind.Subscription;
import dev.goldberry.input.hit.HitTest;
import dev.goldberry.input.key.Shortcut;
import dev.goldberry.input.tap.ModifierKey;
import dev.goldberry.render.backend.headless.HeadlessFileDialogs;
import dev.goldberry.render.clipboard.Clipboard;
import dev.goldberry.render.desktop.SystemTheme;
import dev.goldberry.render.event.EventLoop;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.tray.BackendTray;
import dev.goldberry.render.tray.TraySpec;
import dev.goldberry.render.web.BackendWebView;
import dev.goldberry.render.web.WebViewSpec;
import dev.goldberry.stats.FrameStats;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Corner;

/// A host with **real, scriptable file dialogs** and nothing else under it: a
/// card that asks the user for a path is only testable against a host that can
/// say what the user picked. `dialogs().answerWith(...)` says what the user
/// picked, and `dialogs().shown()` what was asked for.
///
/// No window, popups, tray or web view, which is what a session with no desktop
/// answers anyway.
///
/// @param dialogs the dialogs every `fileDialog` call goes to
record DialogsTestHost(HeadlessFileDialogs dialogs) implements Host {

    DialogsTestHost() {
        this(new HeadlessFileDialogs());
    }

    @Override
    public HeadlessFileDialogs fileDialogs() {
        return dialogs;
    }

    @Override
    public Optional<SystemTheme> systemTheme() {
        return Optional.empty();
    }

    @Override
    public Subscription onSystemThemeChanged(Consumer<SystemTheme> listener) {
        return () -> {};
    }

    @Override
    public Optional<HitTest.Region> anchor(String id) {
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
    public void shortcut(Shortcut accelerator, Runnable action) {}

    @Override
    public void shortcut(Shortcut accelerator, Runnable action, Object owner) {}

    @Override
    public void shortcut(String accelerator, Runnable action) {}

    @Override
    public void removeShortcut(Shortcut accelerator) {}

    @Override
    public void removeShortcut(Shortcut accelerator, Object owner) {}

    @Override
    public void removeShortcut(String accelerator) {}

    @Override
    public void modifierTap(ModifierKey modifier, Runnable action, Object owner) {}

    @Override
    public void removeModifierTap(ModifierKey modifier, Object owner) {}

    @Override
    public LogicalRect placeableArea() {
        return LogicalRect.of(0, 0, 900, 560);
    }

    @Override
    public Optional<Popup> popup(Widget content, LogicalRect anchor, Placement placement) {
        return Optional.empty();
    }

    @Override
    public Optional<Popup> popup(Widget content, LogicalRect anchor, Placement placement, float minimumWidth) {
        return Optional.empty();
    }

    @Override
    public Optional<Popup> popup(
            Widget content, LogicalRect anchor, Placement placement, float minimumWidth, @Nullable Fit fit) {
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
    public Optional<Popup> attachedPopup(
            Widget content, LogicalRect anchor, Placement placement, float minimumWidth, Fit fit) {
        return Optional.empty();
    }

    @Override
    public EventLoop.Timer after(Duration delay, Runnable action) {
        throw new UnsupportedOperationException("no event loop here");
    }

    @Override
    public void onContextMenu(ContextMenuHandler handler) {}

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

    @Override
    public Optional<BackendTray> tray(TraySpec spec) {
        return Optional.empty();
    }

    @Override
    public Optional<BackendWebView> webView(WebViewSpec spec) {
        return Optional.empty();
    }

    @Override
    public Optional<BackendWebView> embeddedWebView(WebViewSpec spec, LogicalRect bounds) {
        return Optional.empty();
    }

    @Override
    public void textInput(boolean active) {}

    @Override
    public Window window() {
        throw new UnsupportedOperationException("no window here");
    }
}
