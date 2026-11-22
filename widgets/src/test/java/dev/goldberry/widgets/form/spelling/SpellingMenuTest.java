package dev.goldberry.widgets.form.spelling;

import static dev.goldberry.widgets.TestLoop.later;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import dev.goldberry.text.spell.SpellCheck;
import dev.goldberry.text.spell.SpellCheckers;
import dev.goldberry.text.spell.WordListChecker;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.form.edit.TextCommands;
import dev.goldberry.widgets.form.edit.TextMenu;
import dev.goldberry.widgets.form.textarea.TextArea;
import dev.goldberry.widgets.menu.Item;
import dev.goldberry.widgets.menu.Separator;

/// The rows a field's menu shows on a misspelled word, alone and through the
/// real launcher.
class SpellingMenuTest {

    private WordListChecker checker;

    @BeforeEach
    void words() {
        checker = WordListChecker.english("the", "quick", "brown", "fox");
    }

    @AfterEach
    void clear() {
        SpellCheckers.clear();
    }

    @Nested
    @DisplayName("alone")
    class Alone {

        private SpellCheck check;
        private final List<int[]> replaced = new ArrayList<>();
        private final List<String> with = new ArrayList<>();

        @BeforeEach
        void open() {
            check = new SpellCheck(checker, SpellCheckers.personal(Locale.US), SpellCheck.Threads.IMMEDIATE);
        }

        @AfterEach
        void close() {
            check.close();
        }

        private boolean replace(int start, int end, String word) {
            replaced.add(new int[] {start, end});
            with.add(word);
            return true;
        }

        @Test
        @DisplayName("on a misspelled word: its suggestions, then Add to dictionary and Ignore")
        void rows() {
            var menu = new SpellingMenu(() -> {});
            menu.aim(check, "the fox\nthe quikc", 17);

            var rows = menu.rows(this::replace);
            assertEquals(List.of("quick", "Add to dictionary", "Ignore"), labels(rows));

            choose(rows.getFirst());
            assertEquals(List.of("quick"), with);
            assertEquals(
                    List.of(12, 17),
                    List.of(replaced.getFirst()[0], replaced.getFirst()[1]),
                    "offsets into the whole text, the second line's word");
        }

        @Test
        @DisplayName("nothing when the caret is on a known word, or the field does not check")
        void nothing() {
            var menu = new SpellingMenu(() -> {});

            menu.aim(check, "the quick fox", 6);
            assertEquals(List.of(), menu.rows(this::replace));
            menu.aim(null, "the quikc", 6);
            assertEquals(List.of(), menu.rows(this::replace));
        }

        @Test
        @DisplayName("no suggestions is a row that says so, and cannot be chosen")
        void noSuggestions() {
            var menu = new SpellingMenu(() -> {});
            menu.aim(check, "zzzzzzzz", 3);

            var rows = menu.rows(this::replace);
            assertEquals("No suggestions", labels(rows).getFirst());
            assertTrue(((Item) rows.getFirst()).disabled());
        }

        @Test
        @DisplayName("Add to dictionary and Ignore act on the check")
        void addAndIgnore() {
            var personal = SpellCheckers.personal(Locale.US);
            var menu = new SpellingMenu(() -> {});
            menu.aim(check, "the quikc", 9);
            ((Item) menu.rows(this::replace).get(1)).onPress().run();
            assertTrue(personal.contains("quikc"));

            menu.aim(check, "the brwon", 9);
            ((Item) menu.rows(this::replace).get(2)).onPress().run();
            assertEquals(List.of(), check.misspellings("the brwon"));
        }

        @Test
        @DisplayName("while suggestions are being found the menu says so, and is redrawn when they come")
        void pending() {
            Queue<Runnable> background = new ArrayDeque<>();
            Queue<Runnable> ui = new ArrayDeque<>();
            var queued = new SpellCheck(
                    checker, SpellCheckers.personal(Locale.of("xx")), new SpellCheck.Threads(background::add, ui::add));
            queued.misspellings("the quikc");
            drain(background);
            drain(ui);

            var redrawn = new AtomicInteger();
            var menu = new SpellingMenu(redrawn::incrementAndGet);
            menu.aim(queued, "the quikc", 9);
            assertEquals(
                    "Finding suggestions…", labels(menu.rows(this::replace)).getFirst());

            drain(background);
            drain(ui);
            assertEquals(1, redrawn.get());
            assertEquals("quick", labels(menu.rows(this::replace)).getFirst());
        }

        @Test
        @DisplayName("the stock menu puts a field's rows above its own, under a separator")
        void order() {
            var menu = TextMenu.menu(new NoCommands(), List.of(new Item("quick")), List.of(new Item("Format")));
            var rows = menu.children();

            assertEquals("quick", ((Item) rows.getFirst()).label());
            assertInstanceOf(Separator.class, rows.get(1));
            assertEquals("Cut", ((Item) rows.get(2)).label());
            assertEquals("Format", ((Item) rows.getLast()).label());
        }
    }

    /// Through the launcher: a right-click on a misspelled word in a
    /// `text-area` opens the menu on it, and its suggestion replaces the word.
    @Nested
    @DisplayName("in a window")
    class InAWindow {

        private HeadlessBackend backend;

        @BeforeEach
        void setUp() {
            RendererRequirement.enforce();
            backend = new HeadlessBackend();
            GoldberryTestAccess.install(backend);
            SpellCheckers.install(checker);
        }

        @AfterEach
        void tearDown() {
            GoldberryTestAccess.shutdown();
        }

        @Test
        @Timeout(20)
        @DisplayName("a right-click on the word, then Enter on its suggestion: the word is replaced")
        void replaceFromTheMenu() {
            var values = new ArrayList<String>();
            var area = new TextArea("the quikc fox", values::add).spellcheck(true);

            Goldberry.launch(new TestApp(
                    new Column(area),
                    () -> later(200, () -> {
                        rightClick(50, 16);
                        // The menu opens saying it is finding suggestions. When the
                        // suggestion comes it takes the first row's place, and the menu,
                        // drawn again, puts the focus on it: the first row that can run.
                        later(300, () -> {
                            press(Key.ENTER);
                            later(200, Goldberry::stop);
                        });
                    })));

            assertEquals("the quick fox", values.getLast());
        }

        @Test
        @Timeout(20)
        @DisplayName("Add to dictionary from the menu adds the word for every field")
        void addFromTheMenu() {
            var area = new TextArea("the quikc fox", value -> {}).spellcheck(true);

            Goldberry.launch(new TestApp(
                    new Column(area),
                    () -> later(200, () -> {
                        rightClick(50, 16);
                        // The suggestion, then Add to dictionary.
                        later(300, () -> {
                            press(Key.DOWN);
                            press(Key.ENTER);
                            later(200, Goldberry::stop);
                        });
                    })));

            assertTrue(SpellCheckers.personal(Locale.US).contains("quikc"));
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
    }

    // --- helpers --------------------------------------------------------------

    private record TestApp(Widget root, Runnable started) implements Application {

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
            started.run();
        }
    }

    private static final class NoCommands implements TextCommands {

        @Override
        public boolean canCut() {
            return false;
        }

        @Override
        public boolean canCopy() {
            return false;
        }

        @Override
        public boolean canPaste() {
            return false;
        }

        @Override
        public boolean canSelectAll() {
            return false;
        }

        @Override
        public boolean cut() {
            return false;
        }

        @Override
        public boolean copy() {
            return false;
        }

        @Override
        public boolean paste() {
            return false;
        }

        @Override
        public boolean selectAll() {
            return false;
        }
    }

    private static void choose(Widget row) {
        var action = ((Item) row).onPress();
        assertNotNull(action, "a row that can be chosen");
        action.run();
    }

    private static List<String> labels(List<Widget> rows) {
        return rows.stream().map(row -> ((Item) row).label()).toList();
    }

    private static void drain(Queue<Runnable> queue) {
        var pending = List.copyOf(queue);
        queue.clear();
        pending.forEach(Runnable::run);
    }
}
