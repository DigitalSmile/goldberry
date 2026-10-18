package dev.goldberry.widgets.form.edit;

import static dev.goldberry.widgets.TestLoop.later;
import static dev.goldberry.widgets.TestLoop.popups;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.Application;
import dev.goldberry.Goldberry;
import dev.goldberry.GoldberryTestAccess;
import dev.goldberry.Host;
import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.input.key.Key;
import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.backend.headless.HeadlessPopup;
import dev.goldberry.render.backend.headless.HeadlessWindow;
import dev.goldberry.render.event.BackendEvent;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.form.textarea.TextArea;
import dev.goldberry.widgets.form.textinput.TextInput;

/// A text field's own menu, through the real launcher: a right-click opens it,
/// its rows run the field's commands, and Paste enables itself when something is
/// copied while it is open.
///
/// Driven by the keyboard once the menu is open, because a disabled row cannot
/// take focus: the menu focuses its first row that can run, and `Up` and `Down`
/// skip the ones that cannot. So which row a key lands on says which rows are
/// enabled.
class TextFieldMenuTest {

    private static final class TestApp implements Application {

        private final Widget root;
        private final Consumer<Host> onStart;

        TestApp(Widget root, Consumer<Host> onStart) {
            this.root = root;
            this.onStart = onStart;
        }

        @Override
        public Widget root() {
            return root;
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
        return backend.windows().stream()
                .filter(window -> !(window instanceof HeadlessPopup))
                .map(HeadlessWindow.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private void rightClick(float x, float y) {
        backend.post(new BackendEvent.PointerMoved(window(), x, y, 0));
        backend.post(new BackendEvent.PointerPressed(window(), x, y, 3, 1, 0));
        backend.post(new BackendEvent.PointerReleased(window(), x, y, 3, 1, 0));
    }

    private void press(Key key) {
        backend.post(new BackendEvent.KeyPressed(window(), key.sdlKeycode(), 0, false));
    }

    private long open() {
        return popups(backend).stream().filter(HeadlessPopup::isOpen).count();
    }

    @Test
    @Timeout(20)
    @DisplayName("Paste enables itself when something is copied while the menu is open, and pastes")
    void pasteFollowsTheClipboard() {
        var values = new ArrayList<String>();
        var openAfterRightClick = new long[1];
        var field = new TextInput("hello", values::add);

        Goldberry.launch(new TestApp(
                new Column(List.of(field), Attributes.NONE),
                host -> later(200, () -> {
                    rightClick(100, 16);
                    later(200, () -> {
                        openAfterRightClick[0] = open();
                        // Another application copies. Until now only Select all could
                        // run, so it holds the focus; Paste is the row above it.
                        backend.clipboard().text(" world");
                        later(150, () -> {
                            press(Key.UP);
                            press(Key.ENTER);
                            later(200, Goldberry::stop);
                        });
                    });
                })));

        assertAll(
                () -> assertEquals(1, openAfterRightClick[0], "the field's own menu opened"),
                () -> assertEquals("hello world", values.getLast(), "Paste ran on the field"));
    }

    @Test
    @Timeout(20)
    @DisplayName("Select all, then Cut from a second menu, empties the field onto the clipboard")
    void selectAllThenCut() {
        var values = new ArrayList<String>();
        var field = new TextInput("hello", values::add);

        Goldberry.launch(new TestApp(
                new Column(List.of(field), Attributes.NONE),
                host -> later(200, () -> {
                    rightClick(100, 16);
                    // No selection and an empty clipboard: Select all is the only row
                    // that can run, so it has the focus.
                    later(200, () -> {
                        press(Key.ENTER);
                        later(150, () -> {
                            rightClick(100, 16);
                            // Everything is selected now, so Cut is the first row that
                            // can run.
                            later(200, () -> {
                                press(Key.ENTER);
                                later(200, Goldberry::stop);
                            });
                        });
                    });
                })));

        assertAll(
                () -> assertEquals("", values.getLast()),
                () -> assertEquals("hello", backend.clipboard().text()));
    }

    @Test
    @Timeout(20)
    @DisplayName("a text-area has the same menu")
    void textArea() {
        var values = new ArrayList<String>();
        var area = new TextArea("one\ntwo", values::add);

        Goldberry.launch(new TestApp(
                new Column(List.of(area), Attributes.NONE),
                host -> later(200, () -> {
                    backend.clipboard().text("!");
                    rightClick(100, 16);
                    later(200, () -> {
                        // Paste and Select all can run; Paste comes first.
                        press(Key.ENTER);
                        later(200, Goldberry::stop);
                    });
                })));

        assertEquals(1, values.size(), "one paste");
        assertEquals(8, values.getLast().length(), "one\\ntwo with ! inserted at the caret");
    }

    @Test
    @Timeout(20)
    @DisplayName("a disabled field opens no menu")
    void disabledOpensNothing() {
        var count = new long[1];
        var field = new TextInput("hello", _ -> {}).disabled(true);

        Goldberry.launch(new TestApp(
                new Column(List.of(field), Attributes.NONE),
                host -> later(200, () -> {
                    rightClick(100, 16);
                    later(200, () -> {
                        count[0] = open();
                        Goldberry.stop();
                    });
                })));

        assertEquals(0, count[0]);
    }

    @Test
    @Timeout(20)
    @DisplayName("a field inside a panel that named a menu opens the field's own")
    void insideANamedPanel() {
        var asked = new ArrayList<String>();
        var count = new long[1];
        var panel = new Column(
                List.of(new TextInput("hello", _ -> {})), new Attributes("panel", Set.of(), "panel", null, "content"));

        Goldberry.launch(new TestApp(panel, host -> {
            host.onContextMenu((menu, at) -> asked.add(menu));
            later(200, () -> {
                rightClick(100, 16);
                later(200, () -> {
                    count[0] = open();
                    Goldberry.stop();
                });
            });
        }));

        assertAll(
                () -> assertEquals(List.of(), asked, "the panel's menu was not asked for"),
                () -> assertEquals(1, count[0], "the field's own opened"));
    }
}
