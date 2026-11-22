package dev.goldberry.text.spell;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.slf4j.Logger;

import dev.goldberry.Goldberry;
import dev.goldberry.bind.Subscription;
import dev.goldberry.log.Logs;

/// One field's spell checking: which words of each line are misspelled,
/// worked out away from the UI thread and remembered per line.
///
/// ```java
/// var check = new SpellCheck(checker, SpellCheckers.personal(checker.locale()), SpellCheck.Threads.application());
/// check.onChecked(this::repaint);
/// // while painting line k:
/// var marks = SpellCheck.outside(check.misspellings(lineText), caretInLine);
/// ```
///
/// [#misspellings] answers from what is known and asks for the rest. A line
/// seen before with the same text is answered at once. A new one is answered
/// empty and checked on a background thread with any other new lines, after
/// which [#onChecked]'s listeners are told on the UI thread and the field
/// paints again. A field asks only for the lines it draws, so the lines in view
/// are the ones checked first.
///
/// The answers are kept by the line's text, so an edit costs one line's check
/// and moving text around costs none. *Ignore* and *Add to dictionary* throw
/// every answer away, because either can change any line.
///
/// Used from the UI thread. The checker and the personal dictionary are read
/// from the background.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#text-input).
public final class SpellCheck implements AutoCloseable {

    private static final Logger LOG = Logs.of(SpellCheck.class);

    /// How many lines' answers are kept, the least recently asked for going
    /// first.
    static final int REMEMBERED_LINES = 2048;

    /// Where the checking runs and where its answers land.
    ///
    /// @param background where lines are checked and suggestions are made
    /// @param ui         where the answers are handed back: the UI thread
    public record Threads(Executor background, Executor ui) {

        /// Everything on the caller's thread, at once: an answer is ready when
        /// [SpellCheck#misspellings] returns. For tests, and for a render with
        /// no window, which has one frame to get the picture right.
        public static final Threads IMMEDIATE = new Threads(Runnable::run, Runnable::run);

        public Threads {
            Objects.requireNonNull(background, "background");
            Objects.requireNonNull(ui, "ui");
        }

        /// A virtual thread per batch and the application's UI thread, when
        /// called on it; [#IMMEDIATE] anywhere else.
        public static Threads application() {
            if (!Goldberry.isUiThread()) {
                return IMMEDIATE;
            }
            return new Threads(
                    task -> Thread.ofVirtual().name("goldberry-spell").start(task), Goldberry.ui());
        }
    }

    private final SpellChecker checker;
    private final PersonalDictionary personal;
    private final Threads threads;
    private final Subscription watching;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private final Map<String, List<Misspelling>> known = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Misspelling>> eldest) {
            return size() > REMEMBERED_LINES;
        }
    };
    private final Set<String> waiting = new LinkedHashSet<>();
    private Set<String> ignored = Set.of();
    private long revision;
    private boolean checking;
    private boolean asking;
    private boolean closed;
    private volatile boolean reported;

    /// @param checker  the language's checker
    /// @param personal the words the user added to that language
    /// @param threads  where to check and where to answer
    public SpellCheck(SpellChecker checker, PersonalDictionary personal, Threads threads) {
        this.checker = Objects.requireNonNull(checker, "checker");
        this.personal = Objects.requireNonNull(personal, "personal");
        this.threads = Objects.requireNonNull(threads, "threads");
        this.watching = personal.onChange(() -> threads.ui().execute(this::forgetAll));
    }

    /// The checker this field checks with.
    public SpellChecker checker() {
        return checker;
    }

    /// The misspelled words of `line`, as far as they are known, and a check of
    /// it asked for when they are not.
    public List<Misspelling> misspellings(String line) {
        Objects.requireNonNull(line, "line");
        if (closed) {
            return List.of();
        }
        var answer = known.get(line);
        if (answer != null) {
            return answer;
        }
        waiting.add(line);
        asking = true;
        try {
            checkWaiting();
        } finally {
            asking = false;
        }
        return known.getOrDefault(line, List.of());
    }

    /// `found` without the word a caret at `caret` is in or just after: a word
    /// still being typed is not flagged. `caret` is an offset into the same
    /// line, or negative for a line the caret is not on.
    public static List<Misspelling> outside(List<Misspelling> found, int caret) {
        if (caret < 0 || found.isEmpty()) {
            return found;
        }
        var kept = new ArrayList<Misspelling>(found.size());
        for (var word : found) {
            if (!word.holds(caret)) {
                kept.add(word);
            }
        }
        return kept.size() == found.size() ? found : List.copyOf(kept);
    }

    /// Suggestions for `word`, worked out on the background and handed to
    /// `then` on the UI thread.
    public void suggest(String word, int limit, Consumer<List<String>> then) {
        Objects.requireNonNull(word, "word");
        Objects.requireNonNull(then, "then");
        threads.background().execute(() -> {
            List<String> suggestions;
            try {
                suggestions = List.copyOf(checker.suggest(word, limit));
            } catch (RuntimeException e) {
                LOG.warn("the {} checker failed to suggest for \"{}\": {}", checker.locale(), word, e.toString());
                suggestions = List.of();
            }
            var answer = suggestions;
            threads.ui().execute(() -> {
                if (!closed) {
                    then.accept(answer);
                }
            });
        });
    }

    /// Stops flagging `word` in this field, for as long as the field lasts.
    public void ignore(String word) {
        Objects.requireNonNull(word, "word");
        var more = new HashSet<>(ignored);
        if (more.add(word)) {
            ignored = Set.copyOf(more);
            forgetAll();
        }
    }

    /// Adds `word` to the language's personal dictionary, which every field in
    /// the language hears.
    public void addToDictionary(String word) {
        personal.add(word);
    }

    /// Calls `listener` on the UI thread when lines have been checked, so the
    /// field paints them. Close the answer to stop.
    public Subscription onChecked(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /// Stops listening to the dictionary and drops every answer. Idempotent; a
    /// check still running is thrown away when it lands.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        watching.close();
        listeners.clear();
        known.clear();
        waiting.clear();
    }

    private void forgetAll() {
        if (closed) {
            return;
        }
        revision++;
        known.clear();
        tell();
    }

    /// Hands every waiting line to the background as one batch, unless a batch
    /// is out already: the lines asked for meanwhile go in the next one.
    private void checkWaiting() {
        if (checking || waiting.isEmpty() || closed) {
            return;
        }
        checking = true;
        var batch = List.copyOf(waiting);
        waiting.clear();
        var asked = revision;
        var skip = ignored;
        threads.background().execute(() -> {
            var answers = new LinkedHashMap<String, List<Misspelling>>();
            for (var line : batch) {
                answers.put(line, check(line, skip));
            }
            threads.ui().execute(() -> landed(asked, answers));
        });
    }

    private void landed(long asked, Map<String, List<Misspelling>> answers) {
        checking = false;
        if (closed) {
            return;
        }
        if (asked == revision) {
            known.putAll(answers);
        }
        if (!asking) {
            tell();
        }
        checkWaiting();
    }

    private List<Misspelling> check(String line, Set<String> skip) {
        var found = new ArrayList<Misspelling>();
        for (var word : WordScanner.words(line)) {
            var text = word.text();
            if (skip.contains(text) || personal.contains(text)) {
                continue;
            }
            try {
                if (!checker.isCorrect(text)) {
                    found.add(new Misspelling(word.start(), word.end(), text));
                }
            } catch (RuntimeException e) {
                if (!reported) {
                    reported = true;
                    LOG.warn(
                            "the {} checker failed on \"{}\"; its words are not flagged: {}",
                            checker.locale(),
                            text,
                            e.toString());
                }
            }
        }
        return List.copyOf(found);
    }

    private void tell() {
        for (var listener : listeners) {
            listener.run();
        }
    }
}
