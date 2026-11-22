/// Spell checking for text fields: Hunspell dictionaries read by Lucene's
/// Hunspell, in Java, provided to `dev.goldberry.text.spell` as a service.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#text-input).
module dev.goldberry.spell {
    requires dev.goldberry.common;

    requires transitive dev.goldberry.core;

    requires org.apache.lucene.analysis.common;

    requires org.apache.lucene.core;

    requires static org.jspecify;

    // `HunspellChecker`, for an application that loads a dictionary without the
    // registry, and holds the checker itself.
    exports dev.goldberry.spell;

    provides dev.goldberry.text.spell.SpellCheckerProvider with
            dev.goldberry.spell.HunspellProvider;
}
