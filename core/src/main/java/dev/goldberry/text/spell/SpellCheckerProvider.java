package dev.goldberry.text.spell;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/// An engine that reads a Hunspell dictionary: the pair of files `xx_YY.aff`
/// (the affix rules) and `xx_YY.dic` (the words).
///
/// A service, provided by an engine module and found by [SpellCheckers#load].
/// `goldberry-spell` is the toolkit's own.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#text-input).
public interface SpellCheckerProvider {

    /// What the engine is called, for a log line.
    String name();

    /// A checker over the dictionary in `affix` and `words`, both read to their
    /// end. The caller closes the streams.
    ///
    /// @throws IOException if either cannot be read or is not a dictionary
    SpellChecker open(Locale locale, InputStream affix, InputStream words) throws IOException;
}
