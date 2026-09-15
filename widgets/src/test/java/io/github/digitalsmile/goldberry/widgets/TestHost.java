package io.github.digitalsmile.goldberry.widgets;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import io.github.digitalsmile.goldberry.ContextMenuHandler;
import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Overlay;
import io.github.digitalsmile.goldberry.Placement;
import io.github.digitalsmile.goldberry.Popup;
import io.github.digitalsmile.goldberry.Window;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.input.key.Shortcut;
import io.github.digitalsmile.goldberry.input.tap.ModifierKey;
import io.github.digitalsmile.goldberry.render.Clipboard;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessFileDialogs;
import io.github.digitalsmile.goldberry.render.desktop.SystemTheme;
import io.github.digitalsmile.goldberry.render.event.EventLoop;
import io.github.digitalsmile.goldberry.render.model.LogicalPoint;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.render.window.BackendWindow;
import io.github.digitalsmile.goldberry.stats.FrameStats;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Corner;

/// A [Host] for tests: it records what it was asked for and opens nothing.
///
/// **Opening nothing is not a crippled host.** `Backend.createPopup` returns an
/// `Optional` and empty is one of the two real answers — SDL's `dummy` driver,
/// which every headless test here runs under, has no popup windows
/// (ADR-0102).
/// A control that misbehaves against this misbehaves on a real machine whose
/// driver refuses, which is the branch CI exercises on every platform.
///
/// Subclass and override the one or two methods a test actually cares about.
/// Everything is either recorded here or answered with the harmless thing;
/// [#frames], [#fonts], [#window] and [#after] throw instead, because a test that
/// reaches them wants a real window and should say so rather than get a fake.
public class TestHost implements Host {

    /// The clock every widget under this host reads.
    ///
    /// Virtual, and that is the whole reason it is here: a `typeahead` measured
    /// against the real clock can only be tested by sleeping, so nothing tested
    /// it. `host.clock.advance(600)` and a keystroke is what asserting a timeout
    /// looks like instead (`docs/testing.md` §0.1).
    ///
    /// Public and mutable, like the other fields on this host: it is a test
    /// double, and a getter would be ceremony over a field every test writes to.
    public final io.github.digitalsmile.goldberry.motion.Clock.Virtual clock =
            io.github.digitalsmile.goldberry.motion.Clock.virtual();

    @Override
    public io.github.digitalsmile.goldberry.motion.Clock clock() {
        return clock;
    }

    /// One call to a `popup` overload, whichever one it was.
    public record Opened(Widget content, LogicalRect anchor, Placement placement, float minimumWidth) {}

    /// Every popup this was asked to open, in order. Read directly by the tests
    /// that were written against their own stub before this one existed.
    public final List<Opened> opened = new ArrayList<>();

    /// Every widget handed to [#fill].
    public final List<Widget> filled = new ArrayList<>();
    private final Map<String, LogicalRect> anchors = new LinkedHashMap<>();
    private final Map<Shortcut, Runnable> shortcuts = new LinkedHashMap<>();
    private final Map<Shortcut, Object> owners = new LinkedHashMap<>();
    private final Map<ModifierKey, Runnable> taps = new LinkedHashMap<>();
    private final Map<ModifierKey, Object> tapOwners = new LinkedHashMap<>();
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

    /// The modifier taps currently bound, in the order they were bound.
    ///
    /// The gesture itself — press, release, nothing in between — is
    /// [io.github.digitalsmile.goldberry.input.tap.ModifierTaps]'s and is tested
    /// there. What a widget owes is the *binding*, which is what this shows.
    public Map<ModifierKey, Runnable> modifierTaps() {
        return Map.copyOf(taps);
    }

    /// Fires a modifier tap as the window would, if anything is bound to it.
    ///
    /// @return whether anything was
    public boolean tap(ModifierKey modifier) {
        var action = taps.get(modifier);
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

    /// What this host says the desktop is set to — nothing, until a test says
    /// otherwise ([ADR-0322]).
    private SystemTheme systemTheme;

    private final List<Consumer<SystemTheme>> systemThemeListeners = new ArrayList<>();

    @Override
    public Optional<SystemTheme> systemTheme() {
        return Optional.ofNullable(systemTheme);
    }

    @Override
    public void onSystemThemeChanged(Consumer<SystemTheme> listener) {
        systemThemeListeners.add(listener);
    }

    /// Changes the setting and tells every listener, the way a desktop does at
    /// dusk.
    public void systemTheme(SystemTheme theme) {
        systemTheme = theme;
        for (var listener : List.copyOf(systemThemeListeners)) {
            listener.accept(theme);
        }
    }

    @Override
    public Optional<HitTest.Region> anchor(String id) {
        var rect = anchors.get(id);
        return rect == null
                ? Optional.empty()
                : Optional.of(HitTest.Region.of(
                        null,
                        rect.left(),
                        rect.top(),
                        rect.size().width(),
                        rect.size().height()));
    }

    @Override
    public Optional<Popup> popup(Widget content, LogicalRect anchor, Placement placement) {
        return popup(content, anchor, placement, 0);
    }

    @Override
    public Optional<Popup> popup(Widget content, LogicalRect anchor, Placement placement, float minimumWidth) {
        return popup(content, anchor, placement, minimumWidth, null);
    }

    @Override
    public Optional<Popup> popup(Widget content, LogicalRect anchor, Placement placement, float minimumWidth, Fit fit) {
        // The `Fit` is **consulted**, which is the whole reason a test double
        // bothers: the real facility answers it with a measurement, and a widget
        // that reacts to one has nothing to react to otherwise. What it is told
        // is [#measuring]'s, because nothing here lays anything out.
        var toOpen = fit == null || measured == null ? content : fit.fit(content, measured, placeableArea());
        opened.add(new Opened(toOpen, anchor, placement, minimumWidth));
        return Optional.empty();
    }

    /// What a real window is missing.
    private LogicalSize measured;

    /// Says what a popup's content will be told it measured.
    ///
    /// Unset by default, and while it is unset a [Host.Fit] is **not consulted at
    /// all** — so a test that does not care what fits sees exactly what it saw
    /// before this existed. A test that does care states a size and gets the
    /// widget the caller decided to open, which is the only observable a `Fit`
    /// has ([ADR-0179]).
    public TestHost measuring(float width, float height) {
        this.measured = new LogicalSize(width, height);
        return this;
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
        opened.add(new Opened(content, LogicalRect.of(at.x(), at.y(), 0, 0), Placement.BELOW, 0));
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
    public void restyle() {}

    @Override
    public void title(String title) {}

    @Override
    public void shortcut(Shortcut accelerator, Runnable action) {
        shortcut(accelerator, action, null);
    }

    /// The router's ownership, mirrored: what is bound, and who bound it
    /// (ADR-0220). Kept beside the actions rather than in them so that
    /// [#shortcuts()] can stay the map a test wants to read.
    @Override
    public void shortcut(Shortcut accelerator, Runnable action, Object owner) {
        shortcuts.put(accelerator, action);
        owners.put(accelerator, owner);
    }

    @Override
    public void shortcut(String accelerator, Runnable action) {
        shortcut(Shortcut.of(accelerator), action);
    }

    @Override
    public void removeShortcut(Shortcut accelerator) {
        shortcuts.remove(accelerator);
        owners.remove(accelerator);
    }

    @Override
    public void removeShortcut(Shortcut accelerator, Object owner) {
        if (shortcuts.containsKey(accelerator) && owners.get(accelerator) == owner) {
            removeShortcut(accelerator);
        }
    }

    @Override
    public void removeShortcut(String accelerator) {
        removeShortcut(Shortcut.of(accelerator));
    }

    @Override
    public void modifierTap(ModifierKey modifier, Runnable action, Object owner) {
        taps.put(modifier, action);
        tapOwners.put(modifier, owner);
    }

    /// Ownership, mirrored from the real registry: only the binder takes it back
    /// (ADR-0220's rule, ADR-0223's registry).
    @Override
    public void removeModifierTap(ModifierKey modifier, Object owner) {
        if (tapOwners.get(modifier) == owner) {
            taps.remove(modifier);
            tapOwners.remove(modifier);
        }
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

    private final java.util.List<String> focused = new ArrayList<>();

    /// Records the request and reports that it worked.
    ///
    /// There is no element tree behind this host, so there is nothing to focus
    /// and nothing to refuse: what a widget test wants to know is **that it
    /// asked**, and for which id. The real rule — refused for a node that cannot
    /// take focus or is outside an open modal — is `PointerRouter`'s and is tested
    /// against a real tree in `FocusTrapTest`.
    @Override
    public boolean focus(String id, boolean fromKeyboard) {
        focused.add(id);
        return true;
    }

    /// The ids something has asked to focus, in order.
    public java.util.List<String> focusRequests() {
        return java.util.List.copyOf(focused);
    }

    /// Forgets them, for a test that makes several moves and wants to assert
    /// about one at a time.
    ///
    /// A method rather than a mutable list out of [#focusRequests()], so the
    /// record stays the host's — a test that cleared the returned copy would
    /// assert against a list nothing was writing to, which is the failure mode
    /// this replaces.
    public void forgetFocusRequests() {
        focused.clear();
    }

    private final java.util.List<Runnable> scheduled = new ArrayList<>();
    private final java.util.List<Duration> delays = new ArrayList<>();
    private final java.util.List<EventLoop.Timer> timers = new ArrayList<>();

    /// Records the timer and hands back one that is never due.
    ///
    /// It used to throw, and that stopped being tenable when a **focused field
    /// blinks**: any test that gives focus to a `text-input` schedules one, so
    /// every test of anything containing a field would have had to know about
    /// carets. Never due because a test drives the action itself — a timer on a
    /// wall clock would make each of those tests wait out half a second.
    @Override
    public EventLoop.Timer after(Duration delay, Runnable action) {
        scheduled.add(action);
        delays.add(delay);
        var timer = io.github.digitalsmile.goldberry.render.event.TestTimers.pending();
        timers.add(timer);
        return timer;
    }

    /// Fires the most recently scheduled timer, as the loop would.
    public void tick() {
        if (!scheduled.isEmpty()) {
            scheduled.removeLast().run();
        }
    }

    /// Fires the **oldest** pending timer.
    ///
    /// [#tick()] fires the newest, which is right for a widget that has one timer
    /// at a time — a caret, a tooltip, a closing dialog. A `toast` stack has one
    /// per toast, and the one that goes off first is the one that was scheduled
    /// first, so a test of a queue needs this end of the list.
    public void tickFirst() {
        if (!scheduled.isEmpty()) {
            scheduled.removeFirst().run();
        }
    }

    /// Fires everything pending, oldest first, including anything scheduled while
    /// this is running — which is what a queue does to itself.
    ///
    /// Bounded, because a widget that reschedules on every fire would otherwise
    /// spin here for ever rather than failing.
    public void tickAll() {
        for (var i = 0; i < 100 && !scheduled.isEmpty(); i++) {
            scheduled.removeFirst().run();
        }
    }

    /// Whether anything is waiting to fire.
    public boolean hasPendingTimer() {
        return !scheduled.isEmpty();
    }

    /// The delays that were asked for, in order.
    public java.util.List<Duration> scheduledDelays() {
        return java.util.List.copyOf(delays);
    }

    /// Whether every timer this ever handed out has been cancelled — the leak a
    /// widget can cause.
    public boolean allTimersCancelled() {
        return timers.stream().noneMatch(EventLoop.Timer::isPending);
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

    /// Real scriptable file dialogs, for the reason the clipboard below is real:
    /// a test of an "Export…" button wants to say what the user picked, and a
    /// host that always refused would make every such test pass for the wrong
    /// reason.
    ///
    /// Reach it to script an answer — `host.fileDialogs().answerWith(...)` — and
    /// to read what was asked for.
    private final HeadlessFileDialogs fileDialogs = new HeadlessFileDialogs();

    @Override
    public HeadlessFileDialogs fileDialogs() {
        return fileDialogs;
    }

    /// A real headless backend, made on first use, so that a tray shown through
    /// this host is one a test can *choose a row of*.
    ///
    /// The same argument the clipboard above makes: a tray that refused would
    /// make every test of the rows pass for the wrong reason — and the rows are
    /// the only part of a tray Goldberry is responsible for, since the desktop
    /// draws them.
    private io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend trayBackend;

    @Override
    public java.util.Optional<io.github.digitalsmile.goldberry.render.tray.BackendTray> tray(
            io.github.digitalsmile.goldberry.render.tray.TraySpec spec) {
        if (trayBackend == null) {
            trayBackend = new io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend();
        }
        // `andThen(this::repaint)` is the launcher's, and it is here because a
        // test host that skipped it would pass the one thing the real one got
        // wrong: a tray row arrives with no event behind it, so nothing asks for
        // a frame unless the row does (ADR-0191).
        return trayBackend.createTray(spec.andThen(this::repaint));
    }

    @Override
    public void textInput(boolean active) {
        this.textInputActive = active;
    }

    /// Whether a field asked the platform to start delivering committed text —
    /// the contract
    /// [BackendWindow#textInput(boolean)]
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

    @Override
    public Optional<Popup> attachedPopup(
            Widget content, LogicalRect anchor, Placement placement, float minimumWidth, Fit fit) {
        return popup(content, anchor, placement, minimumWidth, fit);
    }
}
