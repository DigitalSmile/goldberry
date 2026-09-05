package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Mod;
import io.github.digitalsmile.goldberry.input.tap.ModifierKey;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessWindow;
import io.github.digitalsmile.goldberry.render.event.BackendEvent;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// A modifier tap through the **real** launcher, the real event loop and the
/// real window ([ADR-0223]).
///
/// [io.github.digitalsmile.goldberry.input.tap.ModifierTapsTest] states the rule
/// against the detector on its own. What only this can say is that the rule is
/// *wired*: that the window feeds it raw keycodes before anything translates
/// them, and that the three things which are not keys — a press, a wheel, a focus
/// change — reach it as interruptions. Every one of those is a line in
/// `Window`'s handlers that nothing else would notice going missing.
class ModifierTapWindowTest {

    private static final int LEFT_ALT = 0x400000e2;

    /// Something for the window to paint, so the frame loop has work.
    private record Plate(Attributes attributes) implements Widget.Leaf, Styled, Paints {

        Plate() {
            this(new Attributes("content", Set.of(), "content"));
        }

        @Override
        public String cssType() {
            return "plate";
        }

        @Override
        public String id() {
            return attributes.id();
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
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
            return LogicalSize.of(400, 300);
        }

        @Override
        public List<Stylesheet> stylesheets() {
            return List.of(Stylesheet.parse(CascadeLayer.APPLICATION, "plate { background: #204060 }"));
        }

        @Override
        public void start(Host host) {
            onStart.accept(host);
        }
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

    private HeadlessWindow window() {
        return (HeadlessWindow) backend.windows().getFirst();
    }

    /// Runs a window, binds an `Alt` tap on it, posts `events`, and answers how
    /// many times the tap fired.
    private int run(Consumer<HeadlessWindow> events) {
        var fired = new AtomicInteger();
        Goldberry.launch(
                new TestApp(host -> {
                    host.modifierTap(ModifierKey.ALT, fired::incrementAndGet, this);
                    events.accept(window());
                }),
                new String[] {"--frames=3"});
        return fired.get();
    }

    @Test
    @Timeout(20)
    @DisplayName("Alt pressed and released fires the tap")
    void tap() {
        assertEquals(1, run(window -> {
            backend.post(new BackendEvent.KeyPressed(window, LEFT_ALT, Mod.ALT.bit(), false));
            backend.post(new BackendEvent.KeyReleased(window, LEFT_ALT, 0));
        }));
    }

    /// The wiring claim that matters most: `Key.fromSdl(LEFT_ALT)` is
    /// `Key.UNKNOWN`, so a window that fed the detector translated keys would see
    /// the same value here as for any unnamed key and could not fire correctly
    /// **or** refuse correctly. It fires, so it is reading the keycode.
    @Test
    @Timeout(20)
    @DisplayName("an unnamed key that is not a modifier fires nothing")
    void unnamedKeyIsNotAModifier() {
        assertEquals(0, run(window -> {
            // SDLK_F13 — a real key this toolkit does not name, so it
            // translates to exactly what `Alt` translates to.
            backend.post(new BackendEvent.KeyPressed(window, 0x40000068, 0, false));
            backend.post(new BackendEvent.KeyReleased(window, 0x40000068, 0));
        }));
    }

    @Test
    @Timeout(20)
    @DisplayName("Alt+F is a shortcut and fires nothing")
    void shortcut() {
        assertEquals(0, run(window -> {
            backend.post(new BackendEvent.KeyPressed(window, LEFT_ALT, Mod.ALT.bit(), false));
            backend.post(new BackendEvent.KeyPressed(window, Key.F.sdlKeycode(), Mod.ALT.bit(), false));
            backend.post(new BackendEvent.KeyReleased(window, Key.F.sdlKeycode(), Mod.ALT.bit()));
            backend.post(new BackendEvent.KeyReleased(window, LEFT_ALT, 0));
        }));
    }

    @Test
    @Timeout(20)
    @DisplayName("a press between the two halves spoils it")
    void pointerPress() {
        assertEquals(0, run(window -> {
            backend.post(new BackendEvent.KeyPressed(window, LEFT_ALT, Mod.ALT.bit(), false));
            backend.post(new BackendEvent.PointerPressed(window, 10, 10, 1, 1, Mod.ALT.bit()));
            backend.post(new BackendEvent.KeyReleased(window, LEFT_ALT, 0));
        }));
    }

    @Test
    @Timeout(20)
    @DisplayName("a wheel between the two halves spoils it")
    void wheel() {
        assertEquals(0, run(window -> {
            backend.post(new BackendEvent.KeyPressed(window, LEFT_ALT, Mod.ALT.bit(), false));
            backend.post(new BackendEvent.PointerWheel(window, 10, 10, 0, -1, 0, -1, Mod.ALT.bit()));
            backend.post(new BackendEvent.KeyReleased(window, LEFT_ALT, 0));
        }));
    }

    /// The compositor's window switcher: `Alt` goes down here, focus leaves, and
    /// the release either never arrives or arrives on the way back. Firing on it
    /// would open a menu the user was using to leave the application.
    @Test
    @Timeout(20)
    @DisplayName("losing focus between the two halves spoils it")
    void focusChange() {
        assertEquals(0, run(window -> {
            backend.post(new BackendEvent.KeyPressed(window, LEFT_ALT, Mod.ALT.bit(), false));
            backend.post(new BackendEvent.FocusChanged(window, false));
            backend.post(new BackendEvent.FocusChanged(window, true));
            backend.post(new BackendEvent.KeyReleased(window, LEFT_ALT, 0));
        }));
    }

    /// Auto-repeat is the platform saying the key is being *held*, which is
    /// somebody on their way to a second key.
    @Test
    @Timeout(20)
    @DisplayName("Alt held open fires nothing")
    void held() {
        assertEquals(0, run(window -> {
            backend.post(new BackendEvent.KeyPressed(window, LEFT_ALT, Mod.ALT.bit(), false));
            backend.post(new BackendEvent.KeyPressed(window, LEFT_ALT, Mod.ALT.bit(), true));
            backend.post(new BackendEvent.KeyReleased(window, LEFT_ALT, 0));
        }));
    }

    @Test
    @Timeout(20)
    @DisplayName("a tap unbound by its owner stops firing")
    void unbound() {
        var fired = new AtomicInteger();
        Goldberry.launch(
                new TestApp(host -> {
                    host.modifierTap(ModifierKey.ALT, fired::incrementAndGet, this);
                    host.removeModifierTap(ModifierKey.ALT, this);
                    backend.post(new BackendEvent.KeyPressed(window(), LEFT_ALT, Mod.ALT.bit(), false));
                    backend.post(new BackendEvent.KeyReleased(window(), LEFT_ALT, 0));
                }),
                new String[] {"--frames=3"});

        assertEquals(0, fired.get());
    }
}
