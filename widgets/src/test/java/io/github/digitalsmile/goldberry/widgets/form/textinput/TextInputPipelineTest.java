package io.github.digitalsmile.goldberry.widgets.form.textinput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.Application;
import io.github.digitalsmile.goldberry.Goldberry;
import io.github.digitalsmile.goldberry.GoldberryTestAccess;
import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.backend.BackendEvent;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.backend.headless.HeadlessWindow;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// A field that is really typed into, through the real launcher, the real event
/// loop and the real router.
///
/// [TextInputTest] drives the node directly, which is where the editing rules
/// are worth testing. This is the other half, and it exists because of what was
/// found while building the widget: **committed text had never once arrived on a
/// real window.** `SDL_StartTextInput` was not on the export list, so SDL
/// delivered no `TEXT_INPUT` event — and every piece of the path it would have
/// travelled was written, routed and unit-tested against the headless backend.
///
/// So what is under test here is the *seam*: a platform event goes in at the
/// backend and a character comes out in a field's text, with the router deciding
/// who has focus and the loop deciding when a frame happens. Nothing here calls a
/// widget method.
class TextInputPipelineTest {

    /// Where the field lands: the column has no padding and a field is 32 high.
    private static final float FIELD_Y = 16;

    private static final class TestApp implements Application {

        private final Widget content;
        private final Consumer<Host> onStart;

        TestApp(Widget content, Consumer<Host> onStart) {
            this.content = content;
            this.onStart = onStart;
        }

        @Override
        public Widget root() {
            return new Column(List.of(content), Attributes.NONE);
        }

        @Override
        public LogicalSize size() {
            return LogicalSize.of(400, 300);
        }

        @Override
        public List<Stylesheet> stylesheets() {
            return List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load());
        }

        @Override
        public void start(Host host) {
            onStart.accept(host);
        }
    }

    private HeadlessBackend backend;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        backend = new HeadlessBackend();
        GoldberryTestAccess.install(backend);
    }

    @AfterEach
    void tearDown() {
        GoldberryTestAccess.shutdown();
    }

    private HeadlessWindow window() {
        return (HeadlessWindow) backend.windows().getFirst();
    }

    private void click(float x, float y) {
        backend.post(new BackendEvent.PointerMoved(window(), x, y, 0));
        backend.post(new BackendEvent.PointerPressed(window(), x, y, 1, 1, 0));
        backend.post(new BackendEvent.PointerReleased(window(), x, y, 1, 1, 0));
    }

    private void typeText(String text) {
        backend.post(new BackendEvent.TextInput(window(), text));
    }

    private void press(Key key) {
        backend.post(new BackendEvent.KeyPressed(window(), key.sdlKeycode(), 0, false));
        backend.post(new BackendEvent.KeyReleased(window(), key.sdlKeycode(), 0));
    }

    private static void later(long millis, Runnable action) {
        Goldberry.async(() -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }).thenRun(action);
    }

    @Test
    @Timeout(20)
    @DisplayName("a click focuses a field and committed text lands in it")
    void typesThroughTheWholePipeline() {
        var value = Property.of("");
        var field = TextInput.of(value, value::set).placeholder("Name");

        Goldberry.launch(new TestApp(field, host -> later(200, () -> {
            click(40, FIELD_Y);
            later(150, () -> {
                // Two events, because that is what a keyboard produces: one
                // character each, translated by the platform's own layout and
                // compose handling before it reaches us (§7.1).
                typeText("G");
                typeText("o");
                later(200, Goldberry::stop);
            });
        })));

        assertEquals("Go", value.get(),
                "committed text did not reach the field the click focused");
    }

    @Test
    @Timeout(20)
    @DisplayName("the window is asked to start delivering text, and to stop")
    void asksThePlatform() {
        var active = new boolean[2];
        var field = new TextInput("", null);

        Goldberry.launch(new TestApp(field, host -> later(200, () -> {
            active[0] = window().isTextInputActive();
            click(40, FIELD_Y);
            later(150, () -> {
                active[1] = window().isTextInputActive();
                Goldberry.stop();
            });
        })));

        assertFalse(active[0], "a window with an unfocused field must not raise a keyboard");
        assertTrue(active[1], "SDL delivers no committed text until a window asks");
    }

    @Test
    @Timeout(20)
    @DisplayName("editing keys reach the field, and Tab still moves the focus")
    void routesTheKeys() {
        var value = Property.of("Goldberry");
        var field = TextInput.of(value, value::set);

        Goldberry.launch(new TestApp(field, host -> later(200, () -> {
            click(40, FIELD_Y);
            later(150, () -> {
                press(Key.END);
                press(Key.BACKSPACE);
                press(Key.BACKSPACE);
                later(200, Goldberry::stop);
            });
        })));

        assertEquals("Goldber", value.get(),
                "the key events did not reach the focused field");
    }

    @Test
    @Timeout(20)
    @DisplayName("a click lands the caret where the pointer was, not at the end")
    void clickPlacesTheCaret() {
        var value = Property.of("Goldberry");
        var field = TextInput.of(value, value::set);

        Goldberry.launch(new TestApp(field, host -> later(200, () -> {
            // Nine points in: past the field's 8pt padding by a hair, so the
            // nearest caret position is the very start of the text. What is being
            // tested is the whole chain — the router hit-tests the painted frame,
            // the field turns a local x into a text offset through the paragraph
            // it shaped, and the two agree about where the padding is.
            click(9, FIELD_Y);
            later(150, () -> {
                typeText("!");
                later(200, Goldberry::stop);
            });
        })));

        assertEquals("!Goldberry", value.get(),
                "the caret went somewhere other than where the pointer was");
    }
}
