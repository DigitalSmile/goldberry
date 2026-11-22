package dev.goldberry.text.spell;

import java.util.List;
import java.util.Locale;

/// One language's dictionary, asked about one word at a time.
///
/// ```java
/// if (!checker.isCorrect("recieve")) {
///     var fixes = checker.suggest("recieve", 5); // [receive, relieve, ...]
/// }
/// ```
///
/// **Called from background threads,** possibly from several at once: the
/// fields check their lines away from the UI thread. An implementation that is
/// not safe for that locks inside.
///
/// A word is handed over as it was typed, without the punctuation around it.
/// The checker decides what case means: Hunspell dictionaries accept "Paris"
/// and "PARIS" and not "paris".
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#text-input).
public interface SpellChecker {

    /// The language this checker's dictionary is for.
    Locale locale();

    /// Whether `word` is spelled correctly.
    boolean isCorrect(String word);

    /// Up to `limit` spellings `word` may have meant, best first, and none for a
    /// word that is correct or that the dictionary has nothing near.
    ///
    /// May be slow: a suggestion searches the dictionary, and the fields ask
    /// only when a menu opens on the word.
    List<String> suggest(String word, int limit);
}
