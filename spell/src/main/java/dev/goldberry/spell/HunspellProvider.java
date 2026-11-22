package dev.goldberry.spell;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import dev.goldberry.text.spell.SpellChecker;
import dev.goldberry.text.spell.SpellCheckerProvider;

/// What `SpellCheckers.load` finds on the module path when this module is
/// there: a [HunspellChecker] over the dictionary it is handed.
///
/// Public because a service provider is constructed by `ServiceLoader`; an
/// application has no reason to name it.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#text-input).
public final class HunspellProvider implements SpellCheckerProvider {

    /// What `ServiceLoader` calls.
    public HunspellProvider() {}

    @Override
    public String name() {
        return "Lucene Hunspell";
    }

    @Override
    public SpellChecker open(Locale locale, InputStream affix, InputStream words) throws IOException {
        return HunspellChecker.load(locale, affix, words);
    }
}
