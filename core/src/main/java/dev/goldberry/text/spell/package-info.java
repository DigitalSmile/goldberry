/// Spell checking for text fields: the checker an engine provides, the
/// registry an application installs checkers in, and the per-field service that
/// checks a document's lines away from the UI thread.
///
/// The toolkit ships no engine and no dictionary here. An engine module
/// provides [dev.goldberry.text.spell.SpellCheckerProvider] as a service, and
/// the application brings the dictionaries, whose licences differ by language.
///
/// ```java
/// SpellCheckers.load(Locale.US, Path.of("en_US.aff"), Path.of("en_US.dic"));
/// ```
///
/// Every type here is non-null unless it says `@Nullable`.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#text-input).
@NullMarked
package dev.goldberry.text.spell;

import org.jspecify.annotations.NullMarked;
