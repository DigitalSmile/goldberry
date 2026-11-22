package dev.goldberry.text.spell;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import dev.goldberry.bind.Subscription;

/// The words a user has added to one language with *Add to dictionary*.
///
/// The application keeps them. The toolkit owns no settings directory, so it
/// reads nothing at start and writes nothing on change. An application that
/// wants the words to outlast the process listens and saves:
///
/// ```java
/// var personal = SpellCheckers.personal(Locale.US);
/// personal.addAll(Files.readAllLines(saved));
/// personal.onChange(() -> Files.write(saved, personal.words()));
/// ```
///
/// A word added in small letters also accepts its capitalised forms, so adding
/// "goldberry" accepts "Goldberry" at the start of a sentence. A word added
/// with a capital accepts only that spelling and its capitals.
///
/// Safe from any thread: the fields read it while checking off the UI thread.
/// A listener is called on the thread that changed the dictionary.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#text-input).
public final class PersonalDictionary {

    private final Locale locale;
    private final Set<String> words = ConcurrentHashMap.newKeySet();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong revision = new AtomicLong();

    PersonalDictionary(Locale locale) {
        this.locale = Objects.requireNonNull(locale, "locale");
    }

    /// The language these words are for.
    public Locale locale() {
        return locale;
    }

    /// Adds `word`. @return whether it was not there before
    public boolean add(String word) {
        var added = words.add(requireWord(word));
        if (added) {
            changed();
        }
        return added;
    }

    /// Adds every one of `all`, telling the listeners once.
    public void addAll(Collection<String> all) {
        var added = false;
        for (var word : all) {
            added |= words.add(requireWord(word));
        }
        if (added) {
            changed();
        }
    }

    /// Takes `word` out. @return whether it was there
    public boolean remove(String word) {
        var removed = words.remove(Objects.requireNonNull(word, "word"));
        if (removed) {
            changed();
        }
        return removed;
    }

    /// Whether `word` was added, or its small-letter form was.
    public boolean contains(String word) {
        Objects.requireNonNull(word, "word");
        return words.contains(word) || words.contains(word.toLowerCase(locale));
    }

    /// Every word, sorted, as a snapshot.
    public List<String> words() {
        return List.copyOf(new TreeSet<>(words));
    }

    /// How many times the words have changed, which a cached check compares.
    public long revision() {
        return revision.get();
    }

    /// Calls `listener` after every change. Close the answer to stop.
    public Subscription onChange(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private void changed() {
        revision.incrementAndGet();
        for (var listener : listeners) {
            listener.run();
        }
    }

    private static String requireWord(String word) {
        Objects.requireNonNull(word, "word");
        var stripped = word.strip();
        if (stripped.isEmpty() || !stripped.equals(word)) {
            throw new IllegalArgumentException(
                    "a dictionary word has no spaces around it and is not empty: \"" + word + "\"");
        }
        return word;
    }
}
