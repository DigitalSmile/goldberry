package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.desktop.SystemTheme;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The desktop's light-or-dark setting — `docs/gaps.md` G26, ADR-0322.
///
/// Driven through the **real** launcher on the backend that needs no display,
/// because what is under test is the whole path: a backend answer, an event
/// translated per window, a handler on [Window], and the two methods an
/// application actually calls. The `sdl3` half of it — `SDL_GetSystemTheme` and
/// `SDL_EVENT_SYSTEM_THEME_CHANGED` — is one `switch` and one branch, and the
/// numbers in it are checked against the compiled SDL by the layout probe.
///
/// The invariant worth naming: **"the desktop says light" and "the desktop does
/// not say" are different answers**, and an application can tell them apart.
class SystemThemeTest {

    /// Something to fill a window with.
    private record Plate() implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "plate";
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).grow(1);
        }
    }

    private static final class TestApp implements Application {

        private final Consumer<Host> onStart;

        TestApp(Consumer<Host> onStart) {
            this.onStart = onStart;
        }

        @Override
        public Widget root() {
            return new Plate();
        }

        @Override
        public LogicalSize size() {
            return LogicalSize.of(320, 240);
        }

        @Override
        public List<Stylesheet> stylesheets() {
            return List.of(Stylesheet.parse(CascadeLayer.APPLICATION, "plate { background: #204060 }"));
        }

        @Override
        public void start(Host host) {
            onStart.accept(host);
        }

        @Override
        public void stop() {}
    }

    private HeadlessBackend backend;

    @BeforeEach
    void installBackend() {
        RendererRequirement.enforce();
        backend = new HeadlessBackend();
        GoldberryRuntime.install(backend);
    }

    @AfterEach
    void shutDown() {
        GoldberryRuntime.shutdown();
    }

    private static void afterTurns(Host host, int turns, Runnable action) {
        if (turns <= 0) {
            action.run();
            return;
        }
        host.after(Duration.ZERO, () -> afterTurns(host, turns - 1, action));
    }

    @Test
    @DisplayName("a backend with no desktop under it does not say")
    void headlessSaysNothing() {
        assertEquals(Optional.empty(), backend.systemTheme(), "a headless backend has no appearance to report");
    }

    @Test
    @Timeout(20)
    @DisplayName("an application can ask what the desktop is set to")
    void theHostAnswers() {
        var asked = new ArrayList<Optional<SystemTheme>>();
        backend.systemTheme(SystemTheme.DARK);

        Goldberry.launch(
                new TestApp(host -> {
                    asked.add(host.systemTheme());
                    Goldberry.stop();
                }),
                new String[] {"--frames=400"});

        assertEquals(List.of(Optional.of(SystemTheme.DARK)), asked);
    }

    /// The reason the answer is an `Optional` rather than a third enum constant: an
    /// application has to be able to tell a *setting* from the absence of one, and
    /// a `switch` over three values makes that easy to forget.
    @Test
    @Timeout(20)
    @DisplayName("a desktop that does not say is empty rather than light")
    void nothingIsNotLight() {
        var asked = new ArrayList<Optional<SystemTheme>>();

        Goldberry.launch(
                new TestApp(host -> {
                    asked.add(host.systemTheme());
                    Goldberry.stop();
                }),
                new String[] {"--frames=400"});

        assertEquals(List.of(Optional.empty()), asked);
    }

    /// The half that matters more than the first question: on every platform with a
    /// sunset schedule this happens once a day, while the application is running.
    @Test
    @Timeout(20)
    @DisplayName("a change reaches the application, on the UI thread")
    void aChangeIsReported() {
        var told = new ArrayList<SystemTheme>();
        var seenAfter = new ArrayList<Optional<SystemTheme>>();
        backend.systemTheme(SystemTheme.LIGHT);

        Goldberry.launch(
                new TestApp(host -> {
                    host.onSystemThemeChanged(told::add);
                    afterTurns(host, 2, () -> {
                        backend.systemTheme(SystemTheme.DARK);
                        afterTurns(host, 3, () -> {
                            seenAfter.add(host.systemTheme());
                            Goldberry.stop();
                        });
                    });
                }),
                new String[] {"--frames=400"});

        assertEquals(List.of(SystemTheme.DARK), told, "dusk arrived and nobody was told");
        assertEquals(List.of(Optional.of(SystemTheme.DARK)), seenAfter, "and asking again gives the new answer");
    }

    @Test
    @Timeout(20)
    @DisplayName("every listener is told, and one added by another does not break the walk")
    void everyListenerIsTold() {
        var first = new ArrayList<SystemTheme>();
        var second = new ArrayList<SystemTheme>();
        var late = new ArrayList<SystemTheme>();

        Goldberry.launch(
                new TestApp(host -> {
                    host.onSystemThemeChanged(theme -> {
                        first.add(theme);
                        // A settings screen that appears *because* the theme changed
                        // is the plausible case, and a walk over the live list would
                        // be a ConcurrentModificationException.
                        host.onSystemThemeChanged(late::add);
                    });
                    host.onSystemThemeChanged(second::add);
                    afterTurns(host, 2, () -> {
                        backend.systemTheme(SystemTheme.DARK);
                        afterTurns(host, 3, Goldberry::stop);
                    });
                }),
                new String[] {"--frames=400"});

        assertEquals(List.of(SystemTheme.DARK), first);
        assertEquals(List.of(SystemTheme.DARK), second, "the second listener was skipped");
        assertEquals(List.of(), late, "a listener registered during the notification hears the *next* change");
    }

    @Test
    @Timeout(20)
    @DisplayName("setting it to what it already is reports nothing")
    void noNewsIsNoEvent() {
        var told = new ArrayList<SystemTheme>();
        backend.systemTheme(SystemTheme.DARK);

        Goldberry.launch(
                new TestApp(host -> {
                    host.onSystemThemeChanged(told::add);
                    afterTurns(host, 2, () -> {
                        backend.systemTheme(SystemTheme.DARK);
                        afterTurns(host, 3, Goldberry::stop);
                    });
                }),
                new String[] {"--frames=400"});

        assertEquals(List.of(), told, "a notification that reports no news is a rebuild nobody asked for");
    }

    /// A window with no [Host] over it is the other supported shape, and it can ask
    /// too — a `hud`, a still picture, a test.
    @Test
    @Timeout(20)
    @DisplayName("a bare window can ask and be told as well")
    void aBareWindowIsToldToo() {
        var told = new ArrayList<SystemTheme>();
        backend.systemTheme(SystemTheme.LIGHT);

        var window = Window.open("bare", 200, 150);
        window.onPaint(frame -> frame.fill(0xFF204060));
        var seen = new ArrayList<Optional<SystemTheme>>();
        window.onSystemThemeChanged(theme -> {
            told.add(theme);
            seen.add(window.systemTheme());
            // Nothing else would end the loop: it runs until every window has
            // closed, and there is no application here to stop it.
            window.close();
        });
        assertTrue(window.systemTheme().isPresent(), "the window could not reach the backend's answer");

        backend.systemTheme(SystemTheme.DARK);
        Goldberry.run();

        assertEquals(List.of(SystemTheme.DARK), told);
        assertEquals(List.of(Optional.of(SystemTheme.DARK)), seen);
    }
}
