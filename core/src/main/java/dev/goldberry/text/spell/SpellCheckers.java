package dev.goldberry.text.spell;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.bind.Subscription;
import dev.goldberry.log.Logs;

/// The application's spell checkers, one per language, and each language's
/// [PersonalDictionary].
///
/// ```java
/// SpellCheckers.load(Locale.US, Path.of("dict/en_US.aff"), Path.of("dict/en_US.dic"));
/// SpellCheckers.load(Locale.of("ru"), Path.of("dict/ru_RU.aff"), Path.of("dict/ru_RU.dic"));
/// ```
///
/// A field that checks its spelling finds its checker here by its `lang`, or by
/// the default locale when it has none. A checker for `en_US` also answers a
/// field that asks for `en`, and a checker for `en` answers `en_GB`. A field
/// whose language has no checker checks nothing, and that is logged once per
/// language.
///
/// One registry per process, safe from any thread. A field already showing is
/// told when a checker comes or goes, and checks again.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#text-input).
public final class SpellCheckers {

    private static final Logger LOG = Logs.of(SpellCheckers.class);

    private static final Map<String, SpellChecker> CHECKERS = new ConcurrentHashMap<>();
    private static final Map<String, PersonalDictionary> PERSONAL = new ConcurrentHashMap<>();
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static final AtomicLong REVISION = new AtomicLong();

    private SpellCheckers() {}

    /// Makes `checker` the one for its locale, in place of any before it.
    public static void install(SpellChecker checker) {
        Objects.requireNonNull(checker, "checker");
        CHECKERS.put(checker.locale().toLanguageTag(), checker);
        REPORTED.clear();
        changed();
    }

    /// Takes away the checker for exactly `locale`. @return whether there was one
    public static boolean uninstall(Locale locale) {
        var removed = CHECKERS.remove(locale.toLanguageTag()) != null;
        if (removed) {
            changed();
        }
        return removed;
    }

    /// Takes away every checker and every personal dictionary: for a test, and
    /// for an application that loads its dictionaries again.
    public static void clear() {
        CHECKERS.clear();
        PERSONAL.clear();
        REPORTED.clear();
        changed();
    }

    /// Reads a Hunspell dictionary through the engine on the module path and
    /// installs it.
    ///
    /// @throws IOException          if the files cannot be read or are not a
    ///                              dictionary
    /// @throws IllegalStateException if no engine is on the module path:
    ///                              `goldberry-spell` is the toolkit's own
    public static SpellChecker load(Locale locale, Path affix, Path words) throws IOException {
        try (var aff = Files.newInputStream(affix);
                var dic = Files.newInputStream(words)) {
            return load(locale, aff, dic);
        }
    }

    /// The same, from two streams the caller closes: a dictionary packed in the
    /// application's own resources.
    public static SpellChecker load(Locale locale, InputStream affix, InputStream words) throws IOException {
        Objects.requireNonNull(locale, "locale");
        var provider = ServiceLoader.load(SpellCheckerProvider.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "no spell checking engine is on the module path; add goldberry-spell"));
        var checker = provider.open(locale, affix, words);
        install(checker);
        LOG.debug("{} reads the {} dictionary", provider.name(), locale.toLanguageTag());
        return checker;
    }

    /// The checker for `locale`: an exact one, else one for its language alone,
    /// else one for the same language in another country.
    public static Optional<SpellChecker> find(Locale locale) {
        Objects.requireNonNull(locale, "locale");
        var exact = CHECKERS.get(locale.toLanguageTag());
        if (exact != null) {
            return Optional.of(exact);
        }
        var language = CHECKERS.get(Locale.of(locale.getLanguage()).toLanguageTag());
        if (language != null) {
            return Optional.of(language);
        }
        return CHECKERS.values().stream()
                .filter(checker -> checker.locale().getLanguage().equals(locale.getLanguage()))
                .findFirst();
    }

    /// The checker a field in `lang` uses, or the default locale's for a field
    /// that names none. Logs once per language that has none.
    public static Optional<SpellChecker> forField(@Nullable Locale lang) {
        var locale = lang == null ? Locale.getDefault() : lang;
        var found = find(locale);
        if (found.isEmpty() && REPORTED.add(locale.toLanguageTag())) {
            LOG.info(
                    "no spell checker for {}; a field in it checks nothing until one is installed",
                    locale.toLanguageTag());
        }
        return found;
    }

    /// The personal dictionary for `locale`'s language, made empty the first
    /// time it is asked for.
    public static PersonalDictionary personal(Locale locale) {
        var language = Locale.of(locale.getLanguage());
        return PERSONAL.computeIfAbsent(language.toLanguageTag(), _ -> new PersonalDictionary(language));
    }

    /// How many times the checkers have changed.
    public static long revision() {
        return REVISION.get();
    }

    /// Calls `listener` whenever a checker is installed or taken away, on the
    /// thread that did it. Close the answer to stop.
    public static Subscription onChange(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        LISTENERS.add(listener);
        return () -> LISTENERS.remove(listener);
    }

    private static void changed() {
        REVISION.incrementAndGet();
        for (var listener : LISTENERS) {
            listener.run();
        }
    }
}
