package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.ContextMenuHandler;
import io.github.digitalsmile.goldberry.FrameStats;
import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Overlay;
import io.github.digitalsmile.goldberry.Placement;
import io.github.digitalsmile.goldberry.Popup;
import io.github.digitalsmile.goldberry.Window;
import io.github.digitalsmile.goldberry.backend.Clipboard;
import io.github.digitalsmile.goldberry.backend.EventLoop;
import io.github.digitalsmile.goldberry.backend.LogicalPoint;
import io.github.digitalsmile.goldberry.backend.LogicalRect;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.input.HitTest;
import io.github.digitalsmile.goldberry.input.Shortcut;
import io.github.digitalsmile.goldberry.text.Fonts;
import io.github.digitalsmile.goldberry.widget.Corner;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// A [Host] for tests: it records what it was asked for and opens nothing.
///
/// **Opening nothing is not a crippled host.** `Backend.createPopup` returns an
/// `Optional` and empty is one of the two real answers — SDL's `dummy` driver,
/// which every headless test here runs under, has no popup windows
/// ([ADR-0102](../../../../../../book/src/adr/0102-a-popup-is-a-window-the-platform-may-refuse.md)).
/// A control that misbehaves against this misbehaves on a real machine whose
/// driver refuses, which is the branch CI exercises on every platform.
///
/// Subclass and override the one or two methods a test actually cares about.
/// Everything is either recorded here or answered with the harmless thing;
/// [#frames], [#fonts], [#window] and [#after] throw instead, because a test that
/// reaches them wants a real window and should say so rather than get a fake.
public class TestHost implements Host {

    /// One call to a `popup` overload, whichever one it was.
    public record Opened(Widget content, LogicalRect anchor, Placement placement,
            float minimumWidth) {
    }

    /// Every popup this was asked to open, in order. Read directly by the tests
    /// that were written against their own stub before this one existed.
    public final List<Opened> opened = new ArrayList<>();

    /// Every widget handed to [#fill].
    public final List<Widget> filled = new ArrayList<>();
    private final Map<String, LogicalRect> anchors = new LinkedHashMap<>();
    private final Map<Shortcut, Runnable> shortcuts = new LinkedHashMap<>();
    private ContextMenuHandler contextMenus;
    private int repaints;

    /// Says that `id` was painted at this rectangle last frame.
    public TestHost anchoring(String id, float x, float y, float width, float height) {
        anchors.put(id, LogicalRect.of(x, y, width, height));
        return this;
    }

    /// The accelerators currently bound, in the order they were bound — the same
    /// order and the same map semantics as the real router's.
    public Map<Shortcut, Runnable> shortcuts() {
        return Map.copyOf(shortcuts);
    }

    /// Fires an accelerator as the router would, if anything is bound to it.
    ///
    /// @return whether anything was
    public boolean press(String accelerator) {
        var action = shortcuts.get(Shortcut.of(accelerator));
        if (action == null) {
            return false;
        }
        action.run();
        return true;
    }

    /// The registered context-menu handler, if a test wants to fire one.
    public Optional<ContextMenuHandler> contextMenus() {
        return Optional.ofNullable(contextMenus);
    }

    public int repaints() {
        return repaints;
    }

    @Override
    public Optional<HitTest.Region> anchor(String id) {
        var rect = anchors.get(id);
        return rect == null
                ? Optional.empty()
                : Optional.of(HitTest.Region.of(null, rect.left(), rect.top(),
                        rect.size().width(), rect.size().height()));
    }

    @Override
    public Optional<Popup> popup(Widget content, LogicalRect anchor, Placement placement) {
        return popup(content, anchor, placement, 0);
    }

    @Override
    public Optional<Popup> popup(Widget content, LogicalRect anchor, Placement placement,
            float minimumWidth) {
        opened.add(new Opened(content, anchor, placement, minimumWidth));
        return Optional.empty();
    }

    @Override
    public Optional<Popup> popup(Widget content, String anchorId, Placement placement) {
        // Through the same recording path as the rectangle form, so a test can
        // assert on what was opened whichever overload the widget reached for.
        return anchor(anchorId)
                .map(region -> popup(content, region.bounds(), placement))
                .orElseGet(Optional::empty);
    }

    @Override
    public Optional<Popup> popup(Widget content, LogicalPoint at, LogicalSize size) {
        opened.add(new Opened(content, LogicalRect.of(at.x(), at.y(), 0, 0),
                Placement.BELOW, 0));
        return Optional.empty();
    }

    @Override
    public Optional<Popup> tooltip(Widget content, LogicalPoint at, LogicalSize size) {
        return popup(content, at, size);
    }

    @Override
    public LogicalRect placeableArea() {
        return LogicalRect.of(0, 0, 800, 600);
    }

    @Override
    public void repaint() {
        repaints++;
    }

    @Override
    public void restyle() {
    }

    @Override
    public void title(String title) {
    }

    @Override
    public void shortcut(Shortcut accelerator, Runnable action) {
        shortcuts.put(accelerator, action);
    }

    @Override
    public void shortcut(String accelerator, Runnable action) {
        shortcut(Shortcut.of(accelerator), action);
    }

    @Override
    public void removeShortcut(Shortcut accelerator) {
        shortcuts.remove(accelerator);
    }

    @Override
    public void removeShortcut(String accelerator) {
        removeShortcut(Shortcut.of(accelerator));
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
    public Overlay fill(Widget widget) {
        filled.add(widget);
        return Overlay.filling(widget);
    }

    @Override
    public void onContextMenu(ContextMenuHandler handler) {
        contextMenus = handler;
    }

    @Override
    public EventLoop.Timer after(Duration delay, Runnable action) {
        throw new UnsupportedOperationException("no event loop in this stub");
    }

    @Override
    public FrameStats frames() {
        throw new UnsupportedOperationException("no frame loop in this stub");
    }

    @Override
    public Fonts fonts() {
        throw new UnsupportedOperationException("no fonts in this stub");
    }

    /// A real in-memory clipboard, not a stub that refuses.
    ///
    /// A `text-input` test that copies and pastes is testing the widget's
    /// editing model; against a clipboard that accepted nothing every one of
    /// those tests would pass for the wrong reason.
    private final StringBuilder clipboardText = new StringBuilder();

    private final Clipboard clipboard = new Clipboard() {

        @Override
        public boolean hasText() {
            return !clipboardText.isEmpty();
        }

        @Override
        public String text() {
            return clipboardText.toString();
        }

        @Override
        public boolean text(String text) {
            clipboardText.setLength(0);
            clipboardText.append(text == null ? "" : text);
            return true;
        }
    };

    private boolean textInputActive;

    @Override
    public Clipboard clipboard() {
        return clipboard;
    }

    @Override
    public void textInput(boolean active) {
        this.textInputActive = active;
    }

    /// Whether a field asked the platform to start delivering committed text —
    /// the contract
    /// [io.github.digitalsmile.goldberry.backend.BackendWindow#textInput(boolean)]
    /// puts on anything editable.
    public boolean isTextInputActive() {
        return textInputActive;
    }

    /// Puts `text` on this host's clipboard, as another application would have.
    public TestHost clipboardText(String text) {
        clipboard.text(text);
        return this;
    }

    @Override
    public Window window() {
        throw new UnsupportedOperationException("no window in this stub");
    }
}
