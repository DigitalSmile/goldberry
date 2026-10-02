package dev.goldberry.offscreen;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.ContextMenuHandler;
import dev.goldberry.Host;
import dev.goldberry.Overlay;
import dev.goldberry.OverlayLayer;
import dev.goldberry.Placement;
import dev.goldberry.Popup;
import dev.goldberry.Window;
import dev.goldberry.bind.Subscription;
import dev.goldberry.input.PointerRouter;
import dev.goldberry.input.hit.HitTest;
import dev.goldberry.input.key.Shortcut;
import dev.goldberry.input.tap.ModifierKey;
import dev.goldberry.motion.Clock;
import dev.goldberry.render.clipboard.Clipboard;
import dev.goldberry.render.desktop.SystemTheme;
import dev.goldberry.render.dialog.FileDialogs;
import dev.goldberry.render.event.EventLoop;
import dev.goldberry.render.event.TimerQueue;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.tray.BackendTray;
import dev.goldberry.render.tray.TraySpec;
import dev.goldberry.render.web.BackendWebView;
import dev.goldberry.render.web.WebViewSpec;
import dev.goldberry.stats.FrameStats;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Corner;

/// The window a [Session]'s tree believes it is in.
///
/// Real where a test can see the difference, and the answer SDL's `dummy`
/// driver gives everywhere else:
///
/// - **Overlays are real.** `fill` and `overlay` put the widget on the layer
///   the session's [dev.goldberry.widget.root.WindowRoot] draws, so a dialog
///   is painted over the content, takes the pointer, and is taken off again by
///   its own handle.
/// - **Time is the session's virtual clock**, and [#after] is a timer on it,
///   fired when the session moves the clock past it. A dialog's closing
///   animation ends, and its handler runs, on the frame it would in a window.
/// - **Focus, accelerators and modality are the router's**, which is the
///   router the session's input goes through.
/// - **No popup windows**, which is a real answer — the `dummy` driver has
///   none — and the one every control already falls back from. No tray, no
///   web view, no file dialogs, no clipboard, and no desktop theme to report.
///
/// [#window()] throws: there is no window, and a widget that reaches for one
/// would get a fake whose answers mean nothing.
final class SessionHost implements Host {

    private final PointerRouter router;

    private final Clock.Virtual clock;

    private final TimerQueue timers;

    private final OverlayLayer layer;

    private final Fonts fonts;

    private final LogicalSize size;

    private final double scale;

    /// What the last pass captured, which is what [#anchor] answers from — a
    /// window answers from the frame it painted, and so does this.
    private List<HitTest.Region> regions = List.of();

    /// Bare-modifier taps by owner. The gesture is the window's, and a session
    /// has no window, so they are kept and never fired.
    private final Map<ModifierKey, Object> taps = new EnumMap<>(ModifierKey.class);

    SessionHost(
            PointerRouter router,
            Clock.Virtual clock,
            TimerQueue timers,
            OverlayLayer layer,
            Fonts fonts,
            LogicalSize size,
            double scale) {
        this.router = router;
        this.clock = clock;
        this.timers = timers;
        this.layer = layer;
        this.fonts = fonts;
        this.size = size;
        this.scale = scale;
    }

    void regions(List<HitTest.Region> captured) {
        this.regions = captured;
    }

    @Override
    public Clock clock() {
        return clock;
    }

    @Override
    public void repaint() {
        // The session renders when it is told to, so a repaint asks for
        // nothing: the next pass builds whatever changed.
    }

    @Override
    public void restyle() {
        // The stylesheets are the session's, fixed when it opened.
    }

    @Override
    public void title(String title) {
        Objects.requireNonNull(title, "title");
    }

    @Override
    public void shortcut(Shortcut accelerator, Runnable action) {
        router.shortcut(accelerator, action);
    }

    @Override
    public void shortcut(Shortcut accelerator, Runnable action, Object owner) {
        router.shortcut(accelerator, action, owner);
    }

    @Override
    public void shortcut(String accelerator, Runnable action) {
        router.shortcut(accelerator, action);
    }

    @Override
    public void removeShortcut(Shortcut accelerator) {
        router.removeShortcut(accelerator);
    }

    @Override
    public void removeShortcut(Shortcut accelerator, Object owner) {
        router.removeShortcut(accelerator, owner);
    }

    @Override
    public void removeShortcut(String accelerator) {
        router.removeShortcut(Shortcut.of(accelerator));
    }

    @Override
    public void modifierTap(ModifierKey modifier, Runnable action, Object owner) {
        Objects.requireNonNull(action, "action");
        taps.put(modifier, owner);
    }

    @Override
    public void removeModifierTap(ModifierKey modifier, Object owner) {
        taps.remove(modifier, owner);
    }

    @Override
    public Overlay overlay(Widget widget, Corner corner) {
        return overlay(widget, corner, Overlay.WINDOW_MARGIN);
    }

    @Override
    public Overlay overlay(Widget widget, Corner corner, float margin) {
        return layer.overlay(widget, corner, margin);
    }

    @Override
    public Overlay fill(Widget widget) {
        return layer.fill(widget);
    }

    @Override
    public Optional<HitTest.Region> anchor(String id) {
        Objects.requireNonNull(id, "id");
        for (var region : regions) {
            if (region.owner() instanceof Element element && id.equals(element.id())) {
                return Optional.of(region);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<SystemTheme> systemTheme() {
        return Optional.empty();
    }

    @Override
    public Subscription onSystemThemeChanged(Consumer<SystemTheme> listener) {
        Objects.requireNonNull(listener, "listener");
        return () -> {};
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
    public Optional<Popup> attachedPopup(
            Widget content, LogicalRect anchor, Placement placement, float minimumWidth, Fit fit) {
        return Optional.empty();
    }

    @Override
    public LogicalRect placeableArea() {
        return LogicalRect.of(0, 0, size.width(), size.height());
    }

    @Override
    public void onContextMenu(ContextMenuHandler handler) {
        // A context menu is a popup, and a session has no popup windows to
        // open one in.
        Objects.requireNonNull(handler, "handler");
    }

    @Override
    public boolean focus(String id, boolean fromKeyboard) {
        return router.focusById(id, fromKeyboard);
    }

    @Override
    public EventLoop.Timer after(Duration delay, Runnable action) {
        return timers.after(delay, action);
    }

    @Override
    public FrameStats frames() {
        return FrameStats.none();
    }

    @Override
    public Clipboard clipboard() {
        return Clipboard.none();
    }

    @Override
    public FileDialogs fileDialogs() {
        return FileDialogs.none();
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
    public void textInput(boolean active) {
        // Nothing to switch on: text arrives through `Session.type`, which
        // hands it to the router whether or not a field asked.
    }

    @Override
    public Fonts fonts() {
        return fonts;
    }

    @Override
    public double displayScale() {
        return scale;
    }

    @Override
    public boolean isModal() {
        return router.isModal();
    }

    @Override
    public Window window() {
        throw new UnsupportedOperationException("an offscreen session has no window; a widget that needs one"
                + " is asking for something only a launcher can answer");
    }
}
