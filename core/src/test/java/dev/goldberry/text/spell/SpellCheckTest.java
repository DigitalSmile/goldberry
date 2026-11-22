package dev.goldberry.text.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// One field's checking: what it answers, when it asks, and what it forgets.
class SpellCheckTest {

    private WordListChecker checker;
    private PersonalDictionary personal;

    @BeforeEach
    void words() {
        checker = WordListChecker.english("the", "quick", "brown", "fox", "receive", "goldberry");
        personal = new PersonalDictionary(Locale.ENGLISH);
    }

    @Nested
    @DisplayName("on one thread")
    class Immediate {

        private SpellCheck check;

        @BeforeEach
        void open() {
            check = new SpellCheck(checker, personal, SpellCheck.Threads.IMMEDIATE);
        }

        @AfterEach
        void close() {
            check.close();
        }

        @Test
        @DisplayName("answers a line's misspelled words as soon as it is asked")
        void answersAtOnce() {
            var found = check.misspellings("the quikc brown fxo");

            assertEquals(List.of(new Misspelling(4, 9, "quikc"), new Misspelling(16, 19, "fxo")), found);
        }

        @Test
        @DisplayName("asks the checker about a line once, however often it is drawn")
        void cachesByText() {
            var first = check.misspellings("the quikc fox");
            var asked = checker.asked();

            var second = check.misspellings("the quikc fox");

            assertSame(first, second);
            assertEquals(asked, checker.asked(), "a line seen before is not checked again");
        }

        @Test
        @DisplayName("a word the user added, or ignored here, is not flagged")
        void personalAndIgnored() {
            check.addToDictionary("quikc");
            assertEquals(List.of(), check.misspellings("the quikc fox"));

            check.ignore("fxo");
            assertEquals(List.of(), check.misspellings("the fxo"));
            assertEquals(1, check.misspellings("the brwn").size(), "only the ignored word is let through");
        }

        @Test
        @DisplayName("a word added in small letters accepts it capitalised")
        void personalCase() {
            personal.add("tessera");

            assertEquals(List.of(), check.misspellings("Tessera tessera"));
            personal.add("Lantern");
            assertEquals(1, check.misspellings("lantern Lantern").size(), "a capitalised word is only itself");
        }

        @Test
        @DisplayName("a change to the dictionary tells the field and forgets every line")
        void dictionaryChangeRepaints() {
            var told = new AtomicInteger();
            check.onChecked(told::incrementAndGet);
            assertEquals(1, check.misspellings("the quikc").size());

            personal.add("quikc");

            assertEquals(1, told.get());
            assertEquals(List.of(), check.misspellings("the quikc"));
        }

        @Test
        @DisplayName("suggestions come from the checker")
        void suggestions() {
            var got = new ArrayList<List<String>>();
            check.suggest("recieve", 5, got::add);

            assertEquals(List.of(List.of("receive")), got);
        }

        @Test
        @DisplayName("a closed check answers nothing and asks nothing")
        void closed() {
            check.close();
            var asked = checker.asked();

            assertEquals(List.of(), check.misspellings("the quikc"));
            assertEquals(asked, checker.asked());
            check.close();
        }
    }

    /// The background and the UI thread as two queues a test drains by hand.
    @Nested
    @DisplayName("across threads")
    class Queued {

        private final Queue<Runnable> background = new ArrayDeque<>();
        private final Queue<Runnable> ui = new ArrayDeque<>();
        private SpellCheck check;

        @BeforeEach
        void open() {
            Executor toBackground = background::add;
            Executor toUi = ui::add;
            check = new SpellCheck(checker, personal, new SpellCheck.Threads(toBackground, toUi));
        }

        @Test
        @DisplayName("a new line is answered empty, checked away from the UI, and the field is told")
        void checkedInTheBackground() {
            var told = new AtomicInteger();
            check.onChecked(told::incrementAndGet);

            assertEquals(List.of(), check.misspellings("the quikc"));
            assertEquals(0, checker.asked(), "nothing is checked on the UI thread");

            run(background);
            run(ui);

            assertEquals(1, told.get());
            assertEquals(1, check.misspellings("the quikc").size());
        }

        @Test
        @DisplayName("lines asked for while a batch is out go in the next one, together")
        void batches() {
            check.misspellings("one");
            check.misspellings("two");
            assertEquals(1, background.size(), "the first line goes out at once, the second waits");
            check.misspellings("three");
            check.misspellings("four");
            assertEquals(1, background.size(), "one batch at a time");

            run(background);
            run(ui);

            assertEquals(1, background.size(), "the lines that waited went out as the next batch");
            run(background);
            run(ui);
            assertEquals(1, check.misspellings("four").size());
        }

        @Test
        @DisplayName("a batch that lands after the words changed is thrown away")
        void staleAnswers() {
            check.misspellings("the quikc");
            run(background);
            check.ignore("quikc");

            run(ui);

            assertEquals(List.of(), check.misspellings("the quikc"), "the line is asked about again");
            run(background);
            run(ui);
            assertEquals(List.of(), check.misspellings("the quikc"));
        }

        @Test
        @DisplayName("a check that lands after the field closed is dropped")
        void landsAfterClose() {
            var told = new AtomicInteger();
            check.onChecked(told::incrementAndGet);
            check.misspellings("the quikc");
            run(background);

            check.close();
            run(ui);

            assertEquals(0, told.get());
        }

        private static void run(Queue<Runnable> queue) {
            var pending = List.copyOf(queue);
            queue.clear();
            pending.forEach(Runnable::run);
        }
    }

    @Test
    @DisplayName("a word the caret is in or just after is not flagged")
    void caretRule() {
        var found = List.of(new Misspelling(4, 9, "quikc"), new Misspelling(16, 19, "fxo"));

        assertEquals(List.of(found.get(1)), SpellCheck.outside(found, 9), "just after the word");
        assertEquals(List.of(found.get(1)), SpellCheck.outside(found, 4), "at its start");
        assertEquals(found, SpellCheck.outside(found, 12), "between the two");
        assertSame(found, SpellCheck.outside(found, -1), "a line the caret is not on");
    }

    @Test
    @DisplayName("a misspelling is a range of its word")
    void misspellingIsARange() {
        var word = new Misspelling(4, 9, "quikc");

        assertTrue(word.covers(4) && word.covers(8) && !word.covers(9));
        assertThrows(IllegalArgumentException.class, () -> new Misspelling(4, 4, ""));
        assertThrows(IllegalArgumentException.class, () -> new Misspelling(4, 9, "quick!"));
    }
}
