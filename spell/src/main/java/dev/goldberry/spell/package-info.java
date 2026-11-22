/// Hunspell dictionaries, read in Java: [dev.goldberry.spell.HunspellChecker]
/// and the service that hands it to `dev.goldberry.text.spell.SpellCheckers`.
///
/// ```java
/// SpellCheckers.load(Locale.US, Path.of("dict/en_US.aff"), Path.of("dict/en_US.dic"));
/// ```
///
/// Every type here is non-null unless it says `@Nullable`.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#text-input).
@NullMarked
package dev.goldberry.spell;

import org.jspecify.annotations.NullMarked;
