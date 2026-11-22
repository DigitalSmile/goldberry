package dev.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.junit.DrivenRuntime;
import dev.goldberry.paint.Box;
import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.backend.headless.HeadlessWindow;
import dev.goldberry.render.desktop.PowerState;
import dev.goldberry.render.event.BackendEvent;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// A window that animates pays for its frames only while they can be seen and
/// as often as its activity allows: none while occluded, a cap while saving
/// power or, where asked, while unfocused; and `:window-inactive`, the focus
/// and occlusion listeners and the power reading say what the activity is.
class WindowActivityTest {

    /// Always moving, as a spinner is, and records the colour it was styled in.
    private record Spinner(List<Integer> colours) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "spinner";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public boolean isAnimating() {
            return true;
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            colours.add(style.color());
            return Box.filled(style.color()).grow(1);
        }
    }

    private record TestApp(Widget root, Consumer<Host> onStart) implements Application {

        @Override
        public LogicalSize size() {
            return LogicalSize.of(200, 100);
        }

        @Override
        public List<Stylesheet> stylesheets() {
            return List.of(Stylesheet.parse(
                    CascadeLayer.APPLICATION, "spinner { color: #ff0000 } spinner:window-inactive { color: #00ff00 }"));
        }

        @Override
        public void start(Host host) {
            onStart.accept(host);
        }
    }

    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;

    private HeadlessBackend backend;
    private DrivenRuntime runtime;
    private final List<Integer> colours = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        backend = new HeadlessBackend();
        runtime = DrivenRuntime.install(backend);
    }

    @AfterEach
    void tearDown() {
        GoldberryRuntime.shutdown();
    }

    private HeadlessWindow window() {
        return (HeadlessWindow) backend.windows().getFirst();
    }

    /// Launches the spinner and runs `script` once its first frame is up.
    private void launch(Consumer<Host> script) {
        Goldberry.launch(
                new TestApp(new Spinner(colours), host -> runtime.afterTheFirstFrame(host, () -> script.accept(host))));
    }

    /// `steps` pumps, then `then`.
    private void pumps(int steps, Runnable then) {
        if (steps == 0) {
            then.run();
            return;
        }
        runtime.afterTheNextPump(() -> pumps(steps - 1, then));
    }

    @Test
    @Timeout(20)
    @DisplayName("an occluded window stops painting its animation, and starts again when it is seen")
    void occlusionPauses() {
        var counts = new int[3];
        var heard = new ArrayList<Boolean>();
        launch(host -> {
            host.window().onOcclusionChanged(heard::add);
            backend.post(new BackendEvent.OcclusionChanged(window(), true));
            // One frame may already have been asked for when the event lands.
            pumps(3, () -> {
                counts[0] = window().presentCount();
                pumps(6, () -> {
                    counts[1] = window().presentCount();
                    backend.post(new BackendEvent.OcclusionChanged(window(), false));
                    pumps(4, () -> {
                        counts[2] = window().presentCount();
                        Goldberry.stop();
                    });
                });
            });
        });

        assertEquals(counts[0], counts[1], "frames while nobody could see them");
        assertTrue(counts[2] > counts[1], "no frame once it could be seen again");
        assertEquals(List.of(true, false), heard);
    }

    @Test
    @Timeout(20)
    @DisplayName("an exposure that says nothing new tells nobody")
    void occlusionChangesOnly() {
        var heard = new ArrayList<Boolean>();
        launch(host -> {
            host.window().onOcclusionChanged(heard::add);
            backend.post(new BackendEvent.OcclusionChanged(window(), false));
            backend.post(new BackendEvent.OcclusionChanged(window(), true));
            backend.post(new BackendEvent.OcclusionChanged(window(), true));
            pumps(2, Goldberry::stop);
        });

        assertEquals(List.of(true), heard);
    }

    @Test
    @Timeout(20)
    @DisplayName("an unfocused window with an inactive cap holds its animation to it, and focus lets it go")
    void inactiveCap() {
        var counts = new int[3];
        var focus = new ArrayList<Boolean>();
        launch(host -> {
            var window = host.window();
            window.inactiveFrameCap(4);
            window.onFocusChanged(focus::add);
            backend.post(new BackendEvent.FocusChanged(window(), true));
            backend.post(new BackendEvent.FocusChanged(window(), false));
            pumps(3, () -> {
                counts[0] = window().presentCount();
                pumps(6, () -> {
                    counts[1] = window().presentCount();
                    // A quarter of a second on the loop's clock: the held frame.
                    runtime.elapsed(
                            Duration.ofMillis(260),
                            () -> pumps(2, () -> {
                                counts[2] = window().presentCount();
                                Goldberry.stop();
                            }));
                });
            });
        });

        assertEquals(counts[0], counts[1], "frames faster than the cap while unfocused");
        assertTrue(counts[2] > counts[1], "the held frame never came");
        assertEquals(List.of(true, false), focus);
    }

    @Test
    @Timeout(20)
    @DisplayName(":window-inactive matches while the focus is in another application")
    void windowInactive() {
        var seen = new int[2];
        launch(host -> {
            backend.post(new BackendEvent.FocusChanged(window(), true));
            backend.post(new BackendEvent.FocusChanged(window(), false));
            pumps(3, () -> {
                seen[0] = colours.getLast();
                backend.post(new BackendEvent.FocusChanged(window(), true));
                pumps(3, () -> {
                    seen[1] = colours.getLast();
                    Goldberry.stop();
                });
            });
        });

        assertEquals(RED, colours.getFirst(), "a window opens active");
        assertEquals(GREEN, seen[0]);
        assertEquals(RED, seen[1]);
    }

    @Test
    @Timeout(20)
    @DisplayName("the power is read again on a timer, a change is heard, and saving caps the animation")
    void power() {
        var heard = new ArrayList<PowerState>();
        var counts = new int[3];
        var saving = new PowerState(PowerState.Source.BATTERY, 40, true);
        launch(host -> {
            var window = host.window();
            assertEquals(PowerState.UNKNOWN, window.powerState());
            window.onPowerChanged(heard::add);
            backend.powerState(saving);
            runtime.elapsed(
                    Window.POWER_POLL,
                    () -> pumps(3, () -> {
                        counts[0] = window().presentCount();
                        pumps(6, () -> {
                            counts[1] = window().presentCount();
                            runtime.elapsed(
                                    Duration.ofMillis(40),
                                    () -> pumps(2, () -> {
                                        counts[2] = window().presentCount();
                                        Goldberry.stop();
                                    }));
                        });
                    }));
        });

        assertEquals(List.of(saving), heard);
        assertEquals(counts[0], counts[1], "faster than thirty a second while saving");
        assertTrue(counts[2] > counts[1], "the next frame of a thirtieth of a second never came");
        assertFalse(heard.getFirst().batteryPercent().isEmpty());
    }
}
